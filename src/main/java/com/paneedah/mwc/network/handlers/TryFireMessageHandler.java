
package com.paneedah.mwc.network.handlers;

import com.paneedah.mwc.network.messages.TryFireMessage;
import com.paneedah.weaponlib.WeaponFireAspect;
import dev.redstudio.redcore.utils.NetworkUtil;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.minecraft.entity.player.EntityPlayerMP;
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

    /*
     * Reset validation state after one second without shots.
     *
     * This matches the client-side timeline idle reset.
     */
    private static final long RESET_AFTER_IDLE_NANOS =
            1_000_000_000L;

    /*
     * Maximum allowed difference between:
     *
     * - the interval measured by the shooter's client;
     * - the interval measured by the server.
     *
     * A large tolerance is intentional because network delivery
     * can be bursty and server packet processing is tick-based
     */
    private static final long MAX_TIMESTAMP_DELTA_DEVIATION_NANOS =
            2_000_000_000L;

    /*
     * Used when an invalid timestamp arrives while the server
     * receives multiple packets within the same timestamp interval
     */
    private static final long DEFAULT_ESTIMATED_INTERVAL_NANOS =
            50_000_000L;

    /*
     * Per-player timestamp validation state
     */
    private static final ConcurrentMap<UUID, TimestampState>
            TIMESTAMP_STATES = new ConcurrentHashMap<>();

    private static final class TimestampState {

        /*
         * Last raw timestamp received from the player's client
         */
        private long lastRawClientTimestamp;

        /*
         * Last monotonic timestamp forwarded to WeaponFireAspect
         */
        private long lastForwardedTimestamp;

        /*
         * Last time the server processed a shot from this player
         */
        private long lastServerReceiveNanos;

        /*
         * Last interval used to construct a forwarded timestamp
         */
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

                /*
                 * Legacy packet without a client timestamp
                 */
                TIMESTAMP_STATES.remove(player.getUniqueID());

                shotTimestampNanos = serverReceiveNanos;
            }

            /*
             * Gameplay firing logic remains unchanged.
             * Only the timestamp used for remote audio is sanitized
             */
            fireManager.serverFire(
                    player,
                    message.isBurst(),
                    message.isAimed(),
                    shotTimestampNanos
            );
        });

        return null;
    }

    /*
     * Validate and normalize a client timestamp.
     *
     * Absolute nanoTime values cannot be compared between
     * different machines because each JVM has its own origin.
     *
     * Therefore, only intervals between consecutive timestamps
     * are compared
     */
    private static long validateClientTimestamp(
            UUID playerId,
            long clientTimestamp,
            long serverReceiveNanos
    ) {

        TimestampState state =
                TIMESTAMP_STATES.get(playerId);

        /*
         * First shot or a long idle period:
         * accept a new timestamp origin
         */
        if (state == null) {

            TimestampState newState =
                    new TimestampState(
                            clientTimestamp,
                            serverReceiveNanos
                    );

            TIMESTAMP_STATES.put(
                    playerId,
                    newState
            );

            return clientTimestamp;
        }

        long serverDelta =
                serverReceiveNanos - state.lastServerReceiveNanos;

        /*
         * Start a new validation period after idle time.
         * Also recover if the server's monotonic interval
         * unexpectedly becomes negative
         */
        if (serverDelta < 0L
                || serverDelta > RESET_AFTER_IDLE_NANOS) {

            TimestampState newState =
                    new TimestampState(
                            clientTimestamp,
                            serverReceiveNanos
                    );

            TIMESTAMP_STATES.put(
                    playerId,
                    newState
            );

            return clientTimestamp;
        }

        long clientDelta =
                clientTimestamp - state.lastRawClientTimestamp;

        /*
         * The client interval is valid only when:
         *
         * 1. It is strictly positive.
         * 2. It is reasonably close to the server receive interval
         */
        boolean validTimestamp = false;

        if (clientDelta > 0L) {

            long difference;

            if (clientDelta >= serverDelta) {
                difference = clientDelta - serverDelta;
            } else {
                difference = serverDelta - clientDelta;
            }

            validTimestamp =
                    difference <= MAX_TIMESTAMP_DELTA_DEVIATION_NANOS;
        }

        long forwardedInterval;

        if (validTimestamp) {

            /*
             * Preserve the original client-side shot interval
             */
            forwardedInterval = clientDelta;

        } else {

            /*
             * The received timestamp is invalid.
             *
             * Do not forward it, because that could move the
             * remote client's timeline backwards or far forwards.
             *
             * Instead, construct a monotonic replacement using
             * the server receive interval
             */
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

        /*
         * Update the raw timestamp even when it was invalid.
         *
         * This allows the validator to recover on the next packet
         * instead of remaining permanently offset from the client
         */
        state.lastRawClientTimestamp = clientTimestamp;

        /*
         * Build a monotonic timestamp for forwarding
         */
        state.lastForwardedTimestamp += forwardedInterval;

        state.lastForwardedIntervalNanos = forwardedInterval;

        state.lastServerReceiveNanos = serverReceiveNanos;

        return state.lastForwardedTimestamp;
    }
}
