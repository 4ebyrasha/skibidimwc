package com.paneedah.mwc.network.handlers;

import com.cheba.combat.logging.CombatLog;
import com.cheba.combat.time.CombatClockSyncManager;
import com.paneedah.mwc.network.messages.CombatTimeSyncReport;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Validates client clock reports and updates the server-side clock estimate.
 */
public final class CombatTimeSyncReportHandler
        implements IMessageHandler<CombatTimeSyncReport, IMessage> {

    @Override
    public IMessage onMessage(
            CombatTimeSyncReport message,
            MessageContext context
    ) {
        // Capture report arrival before any other processing.
        final long serverReportReceiveNanos = System.nanoTime();

        if (message == null || context.getServerHandler() == null) {
            return null;
        }

        EntityPlayerMP player = context.getServerHandler().player;
        if (player == null) {
            return null;
        }

        boolean accepted = CombatClockSyncManager.acceptReport(
                player.getUniqueID(),
                message.getNonce(),
                message.getClientSendNanos(),
                message.getClientReceiveNanos(),
                serverReportReceiveNanos
        );

        if (!accepted) {
            CombatLog.debug(
                    "CombatTimeSyncReportHandler",
                    "onMessage",
                    "Report rejected | Player=" + player.getUniqueID()
                            + " | Nonce=" + message.getNonce()
            );
        }

        return null;
    }
}
