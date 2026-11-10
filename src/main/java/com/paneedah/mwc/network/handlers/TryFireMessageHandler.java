package com.paneedah.mwc.network.handlers;

import com.paneedah.mwc.network.messages.TryFireMessage;
import com.paneedah.weaponlib.WeaponFireAspect;
import dev.redstudio.redcore.utils.NetworkUtil;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@NoArgsConstructor
@AllArgsConstructor
public final class TryFireMessageHandler
        implements IMessageHandler<TryFireMessage, IMessage> {

    private WeaponFireAspect fireManager;

    private static final long RESET_AFTER_IDLE_NANOS =
            1_000_000_000L;

    private static final long MAX_TIMESTAMP_DELTA_DEVIATION_NANOS =
            2_000_000_000L;

    private static final long DEFAULT_ESTIMATED_INTERVAL_NANOS =
            50_000_000L;

    private static final ConcurrentMap<UUID, TimestampState>
            TIMESTAMP_STATES = new ConcurrentHashMap<>();

    private static final class TimestampState {

        private long lastRawClientTimestamp;
        private long lastForwardedTimestamp;
        private long lastServerReceiveNanos;

        private long lastForwardedIntervalNanos =
                DEFAULT_ESTIMATED_INTERVAL_NANOS;

        private TimestampState(
                long clientTimestamp,
                long serverReceiveNanos
        ) {
            this.lastRawClientTimestamp = clientTimestamp;
            this.lastForwardedTimestamp = clientTimestamp;
            this.lastServerReceiveNanos = serverReceiveNanos;
        }
    }

    @Override
    public IMessage onMessage(
            final TryFireMessage message,
            final MessageContext context
    ) {
        NetworkUtil.processMessage(context, () -> {

            EntityPlayerMP player =
                    context.getServerHandler().player;

            long serverReceiveNanos = System.nanoTime();

            long shotTimestampNanos;

            if (message.hasClientShotTimestamp()) {
                shotTimestampNanos = validateClientTimestamp(
                        player.getUniqueID(),
                        message.getClientShotTimestampNanos(),
                        serverReceiveNanos
                );
            } else {
                TIMESTAMP_STATES.remove(player.getUniqueID());
                shotTimestampNanos = serverReceiveNanos;
            }

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

            fireManager.serverFire(
                    player,
                    message.isBurst(),
                    message.isAimed(),
                    shotTimestampNanos,
                    shotYaw,
                    shotPitch
            );
        });

        return null;
    }

    private static long validateClientTimestamp(
            UUID playerId,
            long clientTimestamp,
            long serverReceiveNanos
    ) {
        TimestampState state =
                TIMESTAMP_STATES.get(playerId);

        if (state == null) {
            TimestampState newState =
                    new TimestampState(
                            clientTimestamp,
                            serverReceiveNanos
                    );

            TIMESTAMP_STATES.put(playerId, newState);
            return clientTimestamp;
        }

        long serverDelta =
                serverReceiveNanos - state.lastServerReceiveNanos;

        if (serverDelta < 0L
                || serverDelta > RESET_AFTER_IDLE_NANOS) {

            TimestampState newState =
                    new TimestampState(
                            clientTimestamp,
                            serverReceiveNanos
                    );

            TIMESTAMP_STATES.put(playerId, newState);
            return clientTimestamp;
        }

        long clientDelta =
                clientTimestamp - state.lastRawClientTimestamp;

        boolean validTimestamp = false;

        if (clientDelta > 0L) {
            long difference = clientDelta >= serverDelta
                    ? clientDelta - serverDelta
                    : serverDelta - clientDelta;

            validTimestamp =
                    difference <= MAX_TIMESTAMP_DELTA_DEVIATION_NANOS;
        }

        long forwardedInterval;

        if (validTimestamp) {
            forwardedInterval = clientDelta;
        } else {
            if (serverDelta > 0L) {
                forwardedInterval = serverDelta;
            } else {
                forwardedInterval =
                        state.lastForwardedIntervalNanos;

                if (forwardedInterval <= 0L) {
                    forwardedInterval =
                            DEFAULT_ESTIMATED_INTERVAL_NANOS;
                }
            }
        }

        state.lastRawClientTimestamp = clientTimestamp;
        state.lastForwardedTimestamp += forwardedInterval;
        state.lastForwardedIntervalNanos = forwardedInterval;
        state.lastServerReceiveNanos = serverReceiveNanos;

        return state.lastForwardedTimestamp;
    }
}
