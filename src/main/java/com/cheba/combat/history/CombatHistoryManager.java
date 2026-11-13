package com.cheba.combat.history;

import com.cheba.combat.logging.CombatLog;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central registry for server-authoritative player combat histories.
 *
 * <p>Each connected player has an independent bounded history identified
 * by UUID. Histories contain immutable snapshots captured from accepted
 * server-side movement states.</p>
 *
 * <p>All methods that access the internal map or its histories must be
 * called from the logical server thread. Minecraft entity state and
 * movement history are both owned by that thread.</p>
 *
 * <p>All timestamps belong to the server's {@link System#nanoTime()}
 * time domain. Client timestamps must never be passed directly to
 * historical queries.</p>
 */
public final class CombatHistoryManager {

    private static final Map<UUID, PlayerHistory> HISTORIES = new HashMap<>();

    private CombatHistoryManager() {
    }

    /**
     * Captures the current authoritative state of a player.
     *
     * <p>The caller must invoke this method after the server has accepted
     * and applied the corresponding movement update.</p>
     *
     * <p>The interval between consecutive snapshots is measured for
     * diagnostics. Gaps exceeding the interpolation limit are reported
     * because historical queries cannot safely interpolate across them.</p>
     *
     * @param player server-side player whose state should be recorded
     */
    public static void recordSnapshot(EntityPlayerMP player) {
        if (player == null || player.world == null || player.world.isRemote
                || player.isDead) {
            return;
        }

        UUID playerId = player.getUniqueID();

        PlayerHistory history = HISTORIES.computeIfAbsent(
                playerId,
                id -> new PlayerHistory()
        );

        PlayerSnapshot previousSnapshot = history.getLatest();
        PlayerSnapshot snapshot;

        try {
            snapshot = new PlayerSnapshot(player, System.nanoTime());
        } catch (IllegalArgumentException exception) {
            CombatLog.warn(
                    "CombatHistoryManager",
                    "recordSnapshot",
                    "Player=" + playerId
                            + " | Snapshot rejected"
                            + " | Reason=invalid_player_state"
                            + " | Details=" + exception.getMessage()
            );
            return;
        }

        long intervalNanos = previousSnapshot == null
                ? -1L
                : snapshot.getTimestampNanos()
                - previousSnapshot.getTimestampNanos();

        if (!history.add(snapshot)) {
            CombatLog.warn(
                    "CombatHistoryManager",
                    "recordSnapshot",
                    "Player=" + playerId
                            + " | Snapshot rejected"
                            + " | Reason=non_monotonic_timestamp"
                            + " | Timestamp=" + snapshot.getTimestampNanos()
            );
            return;
        }

        if (previousSnapshot == null) {
            CombatLog.debug(
                    "CombatHistoryManager",
                    "recordSnapshot",
                    "Player=" + playerId
                            + " | First snapshot saved"
                            + " | Dimension=" + snapshot.getDimension()
                            + " | HistorySize=" + history.size()
            );
            return;
        }

        double intervalMillis = intervalNanos / 1_000_000.0D;
        boolean interpolationGap =
                intervalNanos > PlayerHistory.MAX_INTERPOLATION_GAP_NANOS;

        if (interpolationGap) {
            CombatLog.warn(
                    "CombatHistoryManager",
                    "recordSnapshot",
                    "Player=" + playerId
                            + " | Snapshot saved after timeline gap"
                            + " | GapMs=" + intervalMillis
                            + " | InterpolationLimitMs="
                            + PlayerHistory.MAX_INTERPOLATION_GAP_NANOS
                            / 1_000_000.0D
                            + " | HistoricalInterpolation=Unavailable"
                            + " | HistorySize=" + history.size()
            );
        } else {
            CombatLog.debug(
                    "CombatHistoryManager",
                    "recordSnapshot",
                    "Player=" + playerId
                            + " | Snapshot saved"
                            + " | IntervalMs=" + intervalMillis
                            + " | Dimension=" + snapshot.getDimension()
                            + " | Position=" + snapshot.getX()
                            + ", " + snapshot.getY()
                            + ", " + snapshot.getZ()
                            + " | Rotation=" + snapshot.getYaw()
                            + ", " + snapshot.getPitch()
                            + " | HistorySize=" + history.size()
            );
        }
    }

    /**
     * Returns the retained history for a player.
     *
     * <p>The returned history is mutable and must only be accessed from
     * the logical server thread. Prefer {@link #getHistoricalState(UUID,
     * long)} for ordinary historical queries.</p>
     *
     * @param playerId UUID of the player
     * @return player's history, or null if no history exists
     */
    public static PlayerHistory getHistory(UUID playerId) {
        if (playerId == null) {
            return null;
        }

        return HISTORIES.get(playerId);
    }

    /**
     * Resolves a player's historical state at the requested server time.
     *
     * @param playerId UUID of the target player
     * @param timestampNanos requested timestamp in the server time domain
     * @return historical state, or null if unavailable
     */
    public static HistoricalPlayerState getHistoricalState(
            UUID playerId,
            long timestampNanos
    ) {
        if (playerId == null) {
            return null;
        }

        PlayerHistory history = HISTORIES.get(playerId);

        if (history == null) {
            return null;
        }

        return history.getStateAt(timestampNanos);
    }

    /**
     * Invalidates a player's history after a movement timeline discontinuity.
     *
     * @param playerId UUID of the player
     * @param reason diagnostic reason for invalidation
     */
    public static void invalidate(UUID playerId, String reason) {
        if (playerId == null) {
            return;
        }

        PlayerHistory history = HISTORIES.remove(playerId);

        if (history != null) {
            CombatLog.info(
                    "CombatHistoryManager",
                    "invalidate",
                    "Player=" + playerId
                            + " | History invalidated"
                            + " | Reason=" + reason
            );
        }
    }

    /**
     * Removes a player's history from the registry.
     *
     * @param playerId UUID of the player
     */
    public static void remove(UUID playerId) {
        if (playerId == null) {
            return;
        }

        PlayerHistory history = HISTORIES.remove(playerId);

        if (history != null) {
            CombatLog.info(
                    "CombatHistoryManager",
                    "remove",
                    "Player=" + playerId + " | History removed"
            );
        }
    }

    /**
     * Removes every player history during server shutdown.
     */
    public static void clear() {
        int removedHistories = HISTORIES.size();

        HISTORIES.clear();

        CombatLog.info(
                "CombatHistoryManager",
                "clear",
                "All player histories cleared"
                        + " | RemovedPlayers=" + removedHistories
        );
    }
}
