package com.paneedah.mwc.network.handlers;

import com.cheba.combat.logging.CombatLog;
import com.cheba.combat.time.CombatClockSyncManager;
import com.paneedah.mwc.network.messages.TryFireMessage;
import com.paneedah.weaponlib.Weapon;
import com.paneedah.weaponlib.WeaponFireAspect;
import dev.redstudio.redcore.utils.NetworkUtil;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/**
 * Handles client weapon fire requests.
 *
 * <p>Client timestamps are translated into the server monotonic
 * clock domain and clamped to the allowed rewind window.</p>
 */
@NoArgsConstructor
@AllArgsConstructor
public final class TryFireMessageHandler
        implements IMessageHandler<TryFireMessage, IMessage> {

    private static final long MAX_REWIND_NANOS = 200_000_000L;

    private WeaponFireAspect fireManager;

    @Override
    public IMessage onMessage(
            final TryFireMessage message,
            final MessageContext context
    ) {
        // Capture packet arrival before scheduling work on the server thread.
        final long serverPacketReceiveNanos = System.nanoTime();

        NetworkUtil.processMessage(context, () -> {
            if (message == null || context.getServerHandler() == null) {
                return;
            }

            EntityPlayerMP player = context.getServerHandler().player;

            if (player == null || player.isDead || fireManager == null) {
                return;
            }

            ItemStack heldStack = player.getHeldItemMainhand();

            if (heldStack.isEmpty()
                    || !(heldStack.getItem() instanceof Weapon)) {
                CombatLog.debug(
                        "TryFireMessageHandler",
                        "onMessage",
                        "Fire request rejected: no weapon in main hand"
                                + " | Player=" + player.getUniqueID()
                );
                return;
            }

            UUID playerId = player.getUniqueID();

            boolean hasClientTimestamp =
                    message.hasClientShotTimestamp();

            long clientShotTimestampNanos =
                    hasClientTimestamp
                            ? message.getClientShotTimestampNanos()
                            : serverPacketReceiveNanos;

            /*
             * A server receive timestamp must never be interpreted
             * as a timestamp from the client's clock domain.
             *
             * Older message formats without a client timestamp use
             * packet arrival time directly.
             */
            long estimatedServerShotNanos =
                    hasClientTimestamp
                            ? CombatClockSyncManager.getEstimatedServerTime(
                            playerId,
                            clientShotTimestampNanos
                    )
                            : -1L;

            boolean clockSynchronized = estimatedServerShotNanos > 0L;

            if (!clockSynchronized) {
                estimatedServerShotNanos = serverPacketReceiveNanos;
            }

            long oldestAllowedTimestamp = Math.max(
                    1L,
                    serverPacketReceiveNanos - MAX_REWIND_NANOS
            );

            long serverShotTimestampNanos = Math.max(
                    oldestAllowedTimestamp,
                    Math.min(
                            estimatedServerShotNanos,
                            serverPacketReceiveNanos
                    )
            );

            /*
             * No artificial angular restriction is applied here.
             * Legitimate fast flicks must not be clamped.
             */
            float shotYaw = player.rotationYaw;
            float shotPitch = player.rotationPitch;

            if (message.hasShotDirection()) {
                float clientYaw = message.getShotYaw();
                float clientPitch = message.getShotPitch();

                if (Float.isFinite(clientYaw)
                        && Float.isFinite(clientPitch)
                        && clientPitch >= -90.0F
                        && clientPitch <= 90.0F) {

                    shotYaw = MathHelper.wrapDegrees(clientYaw);
                    shotPitch = clientPitch;
                }
            }

            CombatLog.debug(
                    "TryFireMessageHandler",
                    "onMessage",
                    "Shot timestamp resolved"
                            + " | Player=" + playerId
                            + " | HasClientTimestamp=" + hasClientTimestamp
                            + " | ClockSynchronized=" + clockSynchronized
                            + " | ClientShotNanos=" + clientShotTimestampNanos
                            + " | ServerReceiveNanos=" + serverPacketReceiveNanos
                            + " | ServerShotNanos=" + serverShotTimestampNanos
                            + " | RewindMs="
                            + (serverPacketReceiveNanos - serverShotTimestampNanos)
                            / 1_000_000.0D
            );

            fireManager.serverFire(
                    player,
                    message.isBurst(),
                    message.isAimed(),
                    clientShotTimestampNanos,
                    shotYaw,
                    shotPitch,
                    serverShotTimestampNanos
            );
        });

        return null;
    }
}
