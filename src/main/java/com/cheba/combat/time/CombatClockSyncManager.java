package com.cheba.combat.time;

import com.cheba.combat.logging.CombatLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Maintains server-side estimates that translate a client's monotonic clock
 * into the server's monotonic clock domain.
 *
 * <p>The manager never treats client timestamps as authoritative. Every
 * estimate is built from a request whose server timestamps are retained
 * locally, and a matching client report. Consumers must still clamp any
 * translated timestamp to their own rewind policy.</p>
 *
 * <p>All methods are safe to call from network threads.</p>
 */
public final class CombatClockSyncManager {

    /**
     * Returned when no valid server-time estimate is available.
     *
     * <p>Zero and negative values are valid System.nanoTime() timestamps.</p>
     */
    public static final long NO_ESTIMATE = Long.MIN_VALUE;

    private static final long PENDING_TIMEOUT_NANOS = 5_000_000_000L;
    private static final long MAX_CLIENT_RTT_NANOS = 2_000_000_000L;
    private static final long MAX_SERVER_PROCESSING_NANOS = 1_000_000_000L;
    private static final long MAX_REPORT_AGE_NANOS = 2_000_000_000L;
    private static final long MAX_NEGATIVE_REPORT_AGE_NANOS = 250_000_000L;
    private static final long ESTIMATE_MAX_AGE_NANOS = 15_000_000_000L;

    private static final int MAX_PENDING_REQUESTS = 8;
    private static final int MAX_SAMPLES = 12;
    private static final int BEST_SAMPLE_COUNT = 4;

    private static final ConcurrentMap<UUID, PlayerClockState> STATES =
            new ConcurrentHashMap<>();

    private CombatClockSyncManager() {
    }

    /**
     * Stores the server-side timestamps associated with a synchronization
     * request. The client cannot provide or overwrite these timestamps later.
     *
     * @param playerId player UUID
     * @param nonce unique request identifier
     * @param clientSendNanos client timestamp captured before sending
     * @param serverReceiveNanos server timestamp captured on request arrival
     * @param serverSendNanos server timestamp captured before response creation
     * @return true when the request was stored
     */
    public static boolean registerRequest(
            UUID playerId,
            long nonce,
            long clientSendNanos,
            long serverReceiveNanos,
            long serverSendNanos
    ) {
        if (playerId == null
                || nonce == 0L
                || serverSendNanos < serverReceiveNanos) {
            CombatLog.warn(
                    "CombatClockSyncManager",
                    "registerRequest",
                    "Rejected invalid synchronization request"
            );
            return false;
        }

        PlayerClockState state = STATES.computeIfAbsent(
                playerId,
                ignored -> new PlayerClockState()
        );

        synchronized (state) {
            cleanupPending(state, serverReceiveNanos);

            if (state.pending.containsKey(nonce)) {
                CombatLog.warn(
                        "CombatClockSyncManager",
                        "registerRequest",
                        "Rejected duplicate nonce for Player=" + playerId
                );
                return false;
            }

            while (state.pending.size() >= MAX_PENDING_REQUESTS) {
                Iterator<Long> iterator = state.pending.keySet().iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }

            state.pending.put(
                    nonce,
                    new PendingRequest(
                            clientSendNanos,
                            serverReceiveNanos,
                            serverSendNanos
                    )
            );

            CombatLog.trace(
                    "CombatClockSyncManager",
                    "registerRequest",
                    "Player=" + playerId
                            + " | Nonce=" + nonce
                            + " | Pending=" + state.pending.size()
            );
        }

        return true;
    }

    /**
     * Validates a client report and adds a synchronization sample.
     *
     * @param playerId player UUID
     * @param nonce request identifier
     * @param clientSendNanos original client send timestamp
     * @param clientReceiveNanos client response receive timestamp
     * @param serverReportReceiveNanos server timestamp when the report arrived
     * @return true when a sample was accepted
     */
    public static boolean acceptReport(
            UUID playerId,
            long nonce,
            long clientSendNanos,
            long clientReceiveNanos,
            long serverReportReceiveNanos
    ) {
        if (playerId == null || nonce == 0L) {
            return false;
        }

        PlayerClockState state = STATES.get(playerId);
        if (state == null) {
            CombatLog.warn(
                    "CombatClockSyncManager",
                    "acceptReport",
                    "Report without pending state | Player=" + playerId
            );
            return false;
        }

        synchronized (state) {
            PendingRequest request = state.pending.remove(nonce);

            if (request == null) {
                CombatLog.warn(
                        "CombatClockSyncManager",
                        "acceptReport",
                        "Unknown, expired or reused nonce | Player=" + playerId
                                + " | Nonce=" + nonce
                );
                return false;
            }

            if (request.clientSendNanos != clientSendNanos
                    || clientReceiveNanos <= clientSendNanos
                    || serverReportReceiveNanos < request.serverSendNanos) {
                CombatLog.warn(
                        "CombatClockSyncManager",
                        "acceptReport",
                        "Rejected inconsistent timestamps | Player=" + playerId
                                + " | Nonce=" + nonce
                );
                return false;
            }

            long clientElapsed = clientReceiveNanos - clientSendNanos;
            long serverProcessing = request.serverSendNanos
                    - request.serverReceiveNanos;

            if (clientElapsed <= 0L
                    || clientElapsed > MAX_CLIENT_RTT_NANOS
                    || serverProcessing < 0L
                    || serverProcessing > MAX_SERVER_PROCESSING_NANOS) {
                CombatLog.warn(
                        "CombatClockSyncManager",
                        "acceptReport",
                        "Rejected out-of-range timing sample | Player=" + playerId
                                + " | ClientElapsed=" + clientElapsed
                                + " | ServerProcessing=" + serverProcessing
                );
                return false;
            }

            long rtt = clientElapsed - serverProcessing;

            if (rtt < 0L) {
                CombatLog.warn(
                        "CombatClockSyncManager",
                        "acceptReport",
                        "Rejected invalid RTT | Player=" + playerId
                                + " | RTT=" + rtt
                );
                return false;
            }

            /*
             * NTP-style offset:
             * serverClock - clientClock =
             * ((s1 - c0) + (s2 - c3)) / 2
             *
             * Double intermediates avoid overflow when subtracting
             * monotonic timestamps from different processes.
             */
            double offsetValue = (
                    ((double) request.serverReceiveNanos - clientSendNanos)
                            + ((double) request.serverSendNanos - clientReceiveNanos)
            ) / 2.0D;

            if (Double.isNaN(offsetValue)
                    || Double.isInfinite(offsetValue)
                    || offsetValue > Long.MAX_VALUE
                    || offsetValue < Long.MIN_VALUE) {
                CombatLog.warn(
                        "CombatClockSyncManager",
                        "acceptReport",
                        "Rejected invalid clock offset | Player=" + playerId
                );
                return false;
            }

            long offsetNanos = Math.round(offsetValue);

            /*
             * The report itself travels back to the server. This cross-check
             * rejects grossly inconsistent client clock values. A small
             * negative margin is allowed because network path asymmetry and
             * offset estimation error can produce it.
             */
            double estimatedServerAtClientReceive =
                    (double) clientReceiveNanos + offsetValue;
            double reportAgeValue =
                    (double) serverReportReceiveNanos - estimatedServerAtClientReceive;

            if (reportAgeValue < -MAX_NEGATIVE_REPORT_AGE_NANOS
                    || reportAgeValue > MAX_REPORT_AGE_NANOS) {
                CombatLog.warn(
                        "CombatClockSyncManager",
                        "acceptReport",
                        "Rejected implausible report age | Player=" + playerId
                                + " | ReportAgeNanos=" + Math.round(reportAgeValue)
                );
                return false;
            }

            ClockSample sample = new ClockSample(offsetNanos, rtt);
            state.samples.addLast(sample);

            while (state.samples.size() > MAX_SAMPLES) {
                state.samples.removeFirst();
            }

            updateEstimate(state);
            state.lastUpdateNanos = serverReportReceiveNanos;

            if (!state.synchronizedClock) {
                state.synchronizedClock = true;

                CombatLog.info(
                        "CombatClockSyncManager",
                        "acceptReport",
                        "Clock synchronized | Player=" + playerId
                                + " | Samples=" + state.samples.size()
                                + " | OffsetNanos=" + state.offsetNanos
                                + " | RTTNanos=" + state.rttNanos
                );
            } else {
                CombatLog.debug(
                        "CombatClockSyncManager",
                        "acceptReport",
                        "Sample accepted | Player=" + playerId
                                + " | Samples=" + state.samples.size()
                                + " | OffsetNanos=" + state.offsetNanos
                                + " | RTTNanos=" + state.rttNanos
                                + " | JitterNanos=" + state.jitterNanos
                );
            }

            return true;
        }
    }

    /**
     * Converts a client monotonic timestamp to the estimated server clock.
     *
     * @param playerId player UUID
     * @param clientTimestampNanos client timestamp
     * @return estimated server timestamp, or NO_ESTIMATE when no fresh estimate exists
     */
    public static long getEstimatedServerTime(
            UUID playerId,
            long clientTimestampNanos
    ) {
        if (playerId == null) {
            return NO_ESTIMATE;
        }

        PlayerClockState state = STATES.get(playerId);
        if (state == null) {
            return NO_ESTIMATE;
        }

        synchronized (state) {
            long now = System.nanoTime();

            if (!state.synchronizedClock
                    || now - state.lastUpdateNanos > ESTIMATE_MAX_AGE_NANOS) {
                return NO_ESTIMATE;
            }

            double estimated = (double) clientTimestampNanos + state.offsetNanos;

            /*
             * Long.MIN_VALUE is reserved as NO_ESTIMATE.
             * Negative and zero timestamps are otherwise valid.
             */
            if (Double.isNaN(estimated)
                    || Double.isInfinite(estimated)
                    || estimated <= (double) Long.MIN_VALUE
                    || estimated >= (double) Long.MAX_VALUE) {
                return NO_ESTIMATE;
            }

            return Math.round(estimated);
        }
    }

    /**
     * Returns whether a fresh clock estimate is available.
     *
     * @param playerId player UUID
     * @return true when the estimate is fresh
     */
    public static boolean isSynchronized(UUID playerId) {
        return getEstimatedServerTime(
                playerId,
                System.nanoTime()
        ) != NO_ESTIMATE;
    }

    /**
     * Removes all clock synchronization state for a player.
     *
     * @param playerId player UUID
     */
    public static void remove(UUID playerId) {
        if (playerId != null) {
            STATES.remove(playerId);
        }
    }

    /**
     * Clears all clock synchronization state.
     */
    public static void clear() {
        STATES.clear();
    }

    private static void cleanupPending(
            PlayerClockState state,
            long nowNanos
    ) {
        Iterator<Map.Entry<Long, PendingRequest>> iterator =
                state.pending.entrySet().iterator();

        while (iterator.hasNext()) {
            PendingRequest pending = iterator.next().getValue();

            if (nowNanos - pending.serverReceiveNanos > PENDING_TIMEOUT_NANOS) {
                iterator.remove();
            }
        }
    }

    private static void updateEstimate(PlayerClockState state) {
        List<ClockSample> sorted = new ArrayList<>(state.samples);

        sorted.sort(Comparator.comparingLong(sample -> sample.rttNanos));

        int selectedCount = Math.min(BEST_SAMPLE_COUNT, sorted.size());
        List<Long> offsets = new ArrayList<>(selectedCount);
        long bestRtt = Long.MAX_VALUE;

        for (int i = 0; i < selectedCount; i++) {
            ClockSample sample = sorted.get(i);
            offsets.add(sample.offsetNanos);
            bestRtt = Math.min(bestRtt, sample.rttNanos);
        }

        offsets.sort(Long::compareTo);

        long medianOffset;
        if ((offsets.size() & 1) == 1) {
            medianOffset = offsets.get(offsets.size() / 2);
        } else {
            long lower = offsets.get(offsets.size() / 2 - 1);
            long upper = offsets.get(offsets.size() / 2);
            medianOffset = lower / 2L + upper / 2L
                    + (lower % 2L + upper % 2L) / 2L;
        }

        long minOffset = offsets.get(0);
        long maxOffset = offsets.get(offsets.size() - 1);

        state.offsetNanos = medianOffset;
        state.rttNanos = bestRtt;
        state.jitterNanos = maxOffset - minOffset;
    }

    private static final class PendingRequest {

        private final long clientSendNanos;
        private final long serverReceiveNanos;
        private final long serverSendNanos;

        private PendingRequest(
                long clientSendNanos,
                long serverReceiveNanos,
                long serverSendNanos
        ) {
            this.clientSendNanos = clientSendNanos;
            this.serverReceiveNanos = serverReceiveNanos;
            this.serverSendNanos = serverSendNanos;
        }
    }

    private static final class ClockSample {

        private final long offsetNanos;
        private final long rttNanos;

        private ClockSample(long offsetNanos, long rttNanos) {
            this.offsetNanos = offsetNanos;
            this.rttNanos = rttNanos;
        }
    }

    private static final class PlayerClockState {

        private final LinkedHashMap<Long, PendingRequest> pending =
                new LinkedHashMap<>();

        private final ArrayDeque<ClockSample> samples = new ArrayDeque<>();

        private long offsetNanos;
        private long rttNanos;
        private long jitterNanos;
        private long lastUpdateNanos;
        private boolean synchronizedClock;
    }
}
