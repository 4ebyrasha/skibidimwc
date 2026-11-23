package com.cheba.combat.handlers;

import com.cheba.combat.fire.ServerFireRateLimiter;
import com.cheba.combat.history.CombatHistoryManager;
import com.cheba.combat.logging.CombatLog;
import com.cheba.combat.time.CombatClockSyncManager;
import com.cheba.combat.tracking.ViewerTrackingManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.UUID;

/**
 * Handles combat history recording and player lifecycle cleanup.
 */
public final class CombatHistoryEventHandler {

    private CombatHistoryEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof EntityPlayerMP)) {
            return;
        }

        CombatHistoryManager.recordSnapshot(
                (EntityPlayerMP) event.player
        );
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event
    ) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;

        CombatHistoryManager.remove(player.getUniqueID());
        CombatClockSyncManager.remove(player.getUniqueID());
        ServerFireRateLimiter.remove(player.getUniqueID());

        ViewerTrackingManager.stopTrackingObserver(
                player.getUniqueID()
        );
        ViewerTrackingManager.stopTrackingTarget(
                player.getUniqueID()
        );

        CombatLog.debug(
                "CombatHistoryEventHandler",
                "onPlayerLogout",
                "Player combat state removed"
                        + " | Player=" + player.getUniqueID()
        );
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();

        UUID playerId = player.getUniqueID();

        boolean firstDeath = CombatHistoryManager.markDead(playerId);

        if (!firstDeath) {
            CombatLog.debug(
                    "CombatHistoryEventHandler",
                    "onPlayerDeath",
                    "Duplicate death event ignored"
                            + " | Player=" + playerId
                            + " | EntityId=" + player.getEntityId()
            );
            return;
        }

        ViewerTrackingManager.stopTrackingObserver(playerId);
        ViewerTrackingManager.stopTrackingTarget(playerId);

        CombatLog.info(
                "CombatHistoryEventHandler",
                "onPlayerDeath",
                "Player death registered"
                        + " | Player=" + playerId
                        + " | EntityId=" + player.getEntityId()
                        + " | Dimension=" + player.dimension
        );
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event
    ) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        CombatHistoryManager.clearDeathMarker(
                player.getUniqueID()
        );

        /*
         * Death normally removes this state already.
         * Repeat the cleanup here to guarantee that the new player entity
         * cannot inherit stale tracking pairs or historical snapshots.
         *
         * EntityTrackerEntryMixin will create fresh tracking pairs when
         * the respawned entity starts being tracked by other players.
         */
        CombatHistoryManager.invalidate(
                player.getUniqueID(),
                "player_respawn"
        );

        ViewerTrackingManager.stopTrackingObserver(
                player.getUniqueID()
        );
        ViewerTrackingManager.stopTrackingTarget(
                player.getUniqueID()
        );

        CombatLog.info(
                "CombatHistoryEventHandler",
                "onPlayerRespawn",
                "Respawn lifecycle cleanup completed"
                        + " | Player=" + player.getUniqueID()
                        + " | EntityId=" + player.getEntityId()
                        + " | Dimension=" + player.dimension
        );
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event
    ) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        CombatHistoryManager.clearDeathMarker(
                player.getUniqueID()
        );

        CombatHistoryManager.invalidate(
                player.getUniqueID(),
                "dimension_change"
        );

        ViewerTrackingManager.stopTrackingObserver(
                player.getUniqueID()
        );
        ViewerTrackingManager.stopTrackingTarget(
                player.getUniqueID()
        );

        CombatLog.debug(
                "CombatHistoryEventHandler",
                "onPlayerChangedDimension",
                "Player history and tracking invalidated"
                        + " | Player=" + player.getUniqueID()
                        + " | Dimension=" + player.dimension
        );
    }
}
