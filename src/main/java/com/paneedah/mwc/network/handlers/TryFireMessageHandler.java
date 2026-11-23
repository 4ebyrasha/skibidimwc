package com.paneedah.mwc.network.handlers;

import com.cheba.combat.history.CombatHistoryManager;
import com.cheba.combat.logging.CombatLog;
import com.cheba.combat.time.CombatClockSyncManager;
import com.cheba.combat.tracking.ViewerSnapshotValidator;
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
        final long serverPacketReceiveNanos = System.nanoTime();

        NetworkUtil.processMessage(context, () -> {
            if (message == null || context.getServerHandler() == null) {
                CombatLog.debug(
                        "TryFireMessageHandler",
                        "onMessage",
                        "Fire request ignored: missing message or server handler"
                );
                return;
            }

            EntityPlayerMP player = context.getServerHandler().player;

            if (player == null) {
                CombatLog.debug(
                        "TryFireMessageHandler",
                        "onMessage",
                        "Fire request ignored: server player is null"
                );
                return;
            }

            UUID playerId = player.getUniqueID();

            CombatLog.debug(
                    "TryFireMessageHandler",
                    "onMessage",
                    "Fire request received"
                            + " | Player=" + playerId
                            + " | EntityId=" + player.getEntityId()
                            + " | Dead=" + player.isDead
                            + " | Burst=" + message.isBurst()
                            + " | Aimed=" + message.isAimed()
                            + " | HasClientTimestamp="
                            + message.hasClientShotTimestamp()
                            + " | HasShotDirection="
                            + message.hasShotDirection()
                            + " | SubmittedTargets="
                            + (message.getTargetSnapshots() == null
                            ? 0
                            : message.getTargetSnapshots().size())
                            + " | ServerReceiveNanos="
                            + serverPacketReceiveNanos
            );

            if (!player.isEntityAlive()
                    || CombatHistoryManager.isMarkedDead(playerId)
                    || fireManager == null) {
                CombatLog.debug(
                        "TryFireMessageHandler",
                        "onMessage",
                        "Fire request rejected"
                                + " | Player=" + playerId
                                + " | Dead=" + !player.isEntityAlive()
                                + " | DeathMarker="
                                + CombatHistoryManager.isMarkedDead(playerId)
                                + " | FireManagerAvailable="
                                + (fireManager != null)
                );
                return;
            }

            ItemStack heldStack = player.getHeldItemMainhand();

            if (heldStack.isEmpty()
                    || !(heldStack.getItem() instanceof Weapon)) {
                CombatLog.debug(
                        "TryFireMessageHandler",
                        "onMessage",
                        "Fire request rejected: no weapon in main hand"
                                + " | Player=" + playerId
                );
                return;
            }

            boolean hasClientTimestamp =
                    message.hasClientShotTimestamp();

            long clientShotTimestampNanos =
                    hasClientTimestamp
                            ? message.getClientShotTimestampNanos()
                            : serverPacketReceiveNanos;

            long estimatedServerShotNanos =
                    hasClientTimestamp
                            ? CombatClockSyncManager.getEstimatedServerTime(
                            playerId,
                            clientShotTimestampNanos
                    )
                            : CombatClockSyncManager.NO_ESTIMATE;

            boolean clockSynchronized =
                    estimatedServerShotNanos
                            != CombatClockSyncManager.NO_ESTIMATE;

            if (!clockSynchronized) {
                estimatedServerShotNanos = serverPacketReceiveNanos;
            }

            long oldestAllowedTimestamp =
                    serverPacketReceiveNanos - MAX_REWIND_NANOS;

            if (oldestAllowedTimestamp > serverPacketReceiveNanos) {
                oldestAllowedTimestamp = Long.MIN_VALUE;
            }

            long serverShotTimestampNanos = Math.max(
                    oldestAllowedTimestamp,
                    Math.min(
                            estimatedServerShotNanos,
                            serverPacketReceiveNanos
                    )
            );

            float shotYaw = Float.NaN;
            float shotPitch = Float.NaN;
            boolean acceptedClientDirection = false;

            if (message.hasShotDirection()) {
                float clientYaw = message.getShotYaw();
                float clientPitch = message.getShotPitch();

                if (Float.isFinite(clientYaw)
                        && Float.isFinite(clientPitch)
                        && clientPitch >= -90.0F
                        && clientPitch <= 90.0F) {
                    shotYaw = MathHelper.wrapDegrees(clientYaw);
                    shotPitch = clientPitch;
                    acceptedClientDirection = true;
                }

                CombatLog.debug(
                        "TryFireMessageHandler",
                        "onMessage",
                        "Shot direction received"
                                + " | Player=" + playerId
                                + " | ClientYaw=" + clientYaw
                                + " | ClientPitch=" + clientPitch
                                + " | DirectionAccepted="
                                + acceptedClientDirection
                );
            }

            List<ViewerSnapshotValidator.ValidatedTarget> viewerSnapshots =
                    clockSynchronized
                            ? ViewerSnapshotValidator.validate(
                            player,
                            message.getTargetSnapshots(),
                            clientShotTimestampNanos,
                            serverShotTimestampNanos
                    )
                            : Collections.emptyList();

            CombatLog.debug(
                    "TryFireMessageHandler",
                    "onMessage",
                    "Shot timestamp resolved"
                            + " | Player=" + playerId
                            + " | HasClientTimestamp=" + hasClientTimestamp
                            + " | ClockSynchronized=" + clockSynchronized
                            + " | ClientShotNanos="
                            + clientShotTimestampNanos
                            + " | ServerReceiveNanos="
                            + serverPacketReceiveNanos
                            + " | ServerShotNanos="
                            + serverShotTimestampNanos
                            + " | RewindMs="
                            + (serverPacketReceiveNanos
                            - serverShotTimestampNanos)
                            / 1_000_000.0D
            );

            CombatLog.debug(
                    "TryFireMessageHandler",
                    "onMessage",
                    "Viewer snapshot validation completed"
                            + " | Player=" + playerId
                            + " | SubmittedTargets="
                            + (message.getTargetSnapshots() == null
                            ? 0
                            : message.getTargetSnapshots().size())
                            + " | AcceptedTargets="
                            + viewerSnapshots.size()
                            + " | ClockSynchronized="
                            + clockSynchronized
            );

            fireManager.serverFire(
                    player,
                    message.isBurst(),
                    message.isAimed(),
                    clientShotTimestampNanos,
                    shotYaw,
                    shotPitch,
                    serverShotTimestampNanos,
                    viewerSnapshots,
                    serverPacketReceiveNanos
            );
        });

        return null;
    }
}
