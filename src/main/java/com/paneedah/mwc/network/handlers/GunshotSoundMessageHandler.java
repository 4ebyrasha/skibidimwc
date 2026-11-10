package com.paneedah.mwc.network.handlers;

import com.paneedah.mwc.network.messages.GunshotSoundMessage;
import dev.redstudio.redcore.utils.NetworkUtil;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.paneedah.mwc.ProjectConstants.LOGGER;

public final class GunshotSoundMessageHandler
        implements IMessageHandler<GunshotSoundMessage, IMessage> {

    private static final String CLIENT_HANDLER_CLASS =
            "com.paneedah.mwc.client.handlers.ClientGunshotSoundHandler";

    private static volatile Method enqueueMethod;

    @Override
    public IMessage onMessage(
            GunshotSoundMessage msg,
            MessageContext ctx
    ) {

        if (ctx.side != Side.CLIENT) {
            return null;
        }

        long packetReceiveNanos = System.nanoTime();

        NetworkUtil.processMessage(
                ctx,
                () -> dispatchToClient(msg, packetReceiveNanos)
        );

        return null;
    }

    private static void dispatchToClient(
            GunshotSoundMessage msg,
            long packetReceiveNanos
    ) {

        try {

            Method method = enqueueMethod;

            if (method == null) {

                synchronized (GunshotSoundMessageHandler.class) {

                    method = enqueueMethod;

                    if (method == null) {

                        Class<?> clientHandlerClass = Class.forName(
                                CLIENT_HANDLER_CLASS,
                                true,
                                GunshotSoundMessageHandler.class.getClassLoader()
                        );

                        method = clientHandlerClass.getMethod(
                                "enqueue",
                                GunshotSoundMessage.class,
                                long.class
                        );

                        enqueueMethod = method;
                    }
                }
            }

            method.invoke(null, msg, packetReceiveNanos);

        } catch (InvocationTargetException e) {

            LOGGER.error(
                    "Error while processing client gunshot event",
                    e.getCause()
            );

        } catch (ReflectiveOperationException e) {

            LOGGER.error(
                    "Could not load ClientGunshotSoundHandler",
                    e
            );
        }
    }
}
