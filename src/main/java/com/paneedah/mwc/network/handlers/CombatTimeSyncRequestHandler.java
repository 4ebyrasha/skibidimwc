package com.paneedah.mwc.network.handlers;

import com.cheba.combat.logging.CombatLog;
import com.cheba.combat.time.CombatClockSyncManager;
import com.paneedah.mwc.network.messages.CombatTimeSyncRequest;
import com.paneedah.mwc.network.messages.CombatTimeSyncResponse;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Handles client clock synchronization requests on the server.
 */
public final class CombatTimeSyncRequestHandler
        implements IMessageHandler<CombatTimeSyncRequest, IMessage> {

    @Override
    public IMessage onMessage(
            CombatTimeSyncRequest message,
            MessageContext context
    ) {
        // Capture arrival before any scheduling or other work.
        final long serverReceiveNanos = System.nanoTime();

        if (message == null || context.getServerHandler() == null) {
            return null;
        }

        EntityPlayerMP player = context.getServerHandler().player;
        if (player == null) {
            return null;
        }

        final long nonce = message.getNonce();
        final long clientSendNanos = message.getClientSendNanos();
        final long serverSendNanos = System.nanoTime();

        boolean registered = CombatClockSyncManager.registerRequest(
                player.getUniqueID(),
                nonce,
                clientSendNanos,
                serverReceiveNanos,
                serverSendNanos
        );

        if (!registered) {
            CombatLog.debug(
                    "CombatTimeSyncRequestHandler",
                    "onMessage",
                    "Request rejected | Player=" + player.getUniqueID()
                            + " | Nonce=" + nonce
            );
            return null;
        }

        CombatLog.trace(
                "CombatTimeSyncRequestHandler",
                "onMessage",
                "Response prepared | Player=" + player.getUniqueID()
                        + " | Nonce=" + nonce
        );

        return new CombatTimeSyncResponse(
                nonce,
                clientSendNanos,
                serverReceiveNanos,
                serverSendNanos
        );
    }
}
