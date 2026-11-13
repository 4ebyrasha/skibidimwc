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
 * called from the logical server thread. This class intentionally does
 * not synchronize its data structures: Minecraft entity state and the
 * movement history are both owned by the server thread.</p>
 *
 * <p>All timestamps passed to or returned from this manager belong to the
 * server's {@link System#nanoTime()} time domain. Client timestamps must
 * never be passed directly to historical queries.</p>
 */
public final class CombatHistoryManager {

    private static final Map<UUID, PlayerHistory> HISTORIES = new HashMap<>();

    private CombatHistoryManager() {
    }

    /**
     * Captures the current authoritative state of a player.
     *
     * <p>This method must be called after the server has accepted and
     * applied the corresponding movement update. It must not be called
     * with coordinates supplied directly by an untrusted client.</p>
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

        PlayerSnapshot snapshot = new PlayerSnapshot(
                player,
                System.nanoTime()
        );

        if (history.add(snapshot)) {
            CombatLog.debug(
                    "CombatHistoryManager",
                    "recordSnapshot",
                    "Player=" + playerId
                            + " | Snapshot saved"
                            + " | Dimension=" + snapshot.getDimension()
                            + " | Position=" + snapshot.getX()
                            + ", " + snapshot.getY()
                            + ", " + snapshot.getZ()
                            + " | Rotation=" + snapshot.getYaw()
                            + ", " + snapshot.getPitch()
                            + " | HistorySize=" + history.size()
            );
        } else {
            CombatLog.warn(
                    "CombatHistoryManager",
                    "recordSnapshot",
                    "Player=" + playerId
                            + " | Snapshot rejected"
                            + " | Reason=non_monotonic_timestamp"
                            + " | Timestamp=" + snapshot.getTimestampNanos()
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
     * @return player's history, or {@code null} if no history exists
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
     * <p>The state is interpolated between neighboring snapshots when
     * necessary. Requests outside the retained time range return
     * {@code null}; no extrapolation is performed.</p>
     *
     * @param playerId UUID of the target player
     * @param timestampNanos requested timestamp from the server's
     *                       monotonic time domain
     * @return historical state, or {@code null} if unavailable
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
     * Invalidates a player's history after a discontinuity in its movement
     * timeline.
     *
     * <p>The next accepted movement snapshot creates a new history.
     * Typical reasons include server position corrections and dimension
     * changes.</p>
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
     * Removes a player's history permanently from the registry.
     *
     * <p>Used when the player disconnects or dies. A later login or respawn
     * starts with a new history.</p>
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
     * Removes every player history.
     *
     * <p>Called during server shutdown to release all retained snapshots.</p>
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
