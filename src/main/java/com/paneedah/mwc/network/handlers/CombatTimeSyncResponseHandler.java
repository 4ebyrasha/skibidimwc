package com.paneedah.mwc.network.handlers;

import com.cheba.combat.logging.CombatLog;
import com.paneedah.mwc.MWC;
import com.paneedah.mwc.network.messages.CombatTimeSyncReport;
import com.paneedah.mwc.network.messages.CombatTimeSyncResponse;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Receives server clock synchronization responses on the client.
 *
 * <p>Captures the local monotonic timestamp immediately when the response
 * reaches the network handler, then reports it back to the server.</p>
 */
public final class CombatTimeSyncResponseHandler
        implements IMessageHandler<CombatTimeSyncResponse, IMessage> {

    @Override
    public IMessage onMessage(
            CombatTimeSyncResponse message,
            MessageContext context
    ) {
        if (message == null) {
            return null;
        }

        // Capture response arrival before doing any other work.
        final long clientReceiveNanos = System.nanoTime();

        MWC.CHANNEL.sendToServer(
                new CombatTimeSyncReport(
                        message.getNonce(),
                        message.getClientSendNanos(),
                        clientReceiveNanos
                )
        );

        CombatLog.trace(
                "CombatTimeSyncResponseHandler",
                "onMessage",
                "Report sent | Nonce=" + message.getNonce()
                        + " | ClientReceiveNanos=" + clientReceiveNanos
        );

        return null;
    }
}
