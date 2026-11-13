package com.cheba.combat.history;

import com.cheba.combat.logging.CombatLog;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatHistoryManager {

    private static final Map<UUID, PlayerHistory> HISTORIES = new HashMap<>();

    private CombatHistoryManager() {
    }

    public static void recordSnapshot(EntityPlayerMP player) {
        if (player == null || player.world == null || player.world.isRemote) {
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

        int previousSize = history.size();
        history.add(snapshot);

        if (history.size() > previousSize) {
            CombatLog.debug(
                    "CombatHistoryManager",
                    "recordSnapshot",
                    "Player=" + playerId
                            + " | Snapshot saved"
                            + " | Dimension=" + snapshot.getDimension()
                            + " | Position=" + snapshot.getX()
                            + ", " + snapshot.getY()
                            + ", " + snapshot.getZ()
                            + " | HistorySize=" + history.size()
            );
        }
    }

    public static PlayerHistory getHistory(UUID playerId) {
        return HISTORIES.get(playerId);
    }

    public static void invalidate(UUID playerId, String reason) {
        PlayerHistory history = HISTORIES.remove(playerId);

        if (history != null) {
            CombatLog.info(
                    "CombatHistoryManager",
                    "invalidate",
                    "Player=" + playerId + " | History invalidated | Reason=" + reason
            );
        }
    }

    public static void remove(UUID playerId) {
        PlayerHistory history = HISTORIES.remove(playerId);

        if (history != null) {
            CombatLog.info(
                    "CombatHistoryManager",
                    "remove",
                    "Player=" + playerId + " | History removed"
            );
        }
    }

    public static void clear() {
        HISTORIES.clear();

        CombatLog.info(
                "CombatHistoryManager",
                "clear",
                "All player histories cleared"
        );
    }
}
