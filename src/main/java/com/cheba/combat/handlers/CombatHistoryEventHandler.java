package com.cheba.combat.handlers;

import com.cheba.combat.history.CombatHistoryManager;
import com.cheba.combat.logging.CombatLog;
import com.cheba.combat.time.CombatClockSyncManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Handles lifecycle cleanup for combat history and clock synchronization.
 */
public final class CombatHistoryEventHandler {

    private CombatHistoryEventHandler() {
    }

    /**
     * Removes player-specific combat data when the player disconnects.
     *
     * @param event player logout event
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;

        CombatHistoryManager.remove(player.getUniqueID());
        CombatClockSyncManager.remove(player.getUniqueID());

        CombatLog.debug(
                "CombatHistoryEventHandler",
                "onPlayerLogout",
                "Player combat state removed | Player=" + player.getUniqueID()
        );
    }

    /**
     * Removes movement history when a player dies.
     *
     * @param event living entity death event
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();

        CombatHistoryManager.remove(player.getUniqueID());
    }

    /**
     * Invalidates movement history after a dimension change.
     *
     * @param event player dimension-change event
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event
    ) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;

        CombatHistoryManager.invalidate(
                player.getUniqueID(),
                "dimension_change"
        );
    }

    /**
     * Clears all combat state when the server stops.
     *
     * @param event server stopped event
     */
    @SubscribeEvent
    public static void onServerStopped(FMLServerStoppedEvent event) {
        CombatHistoryManager.clear();
        CombatClockSyncManager.clear();

        CombatLog.info(
                "CombatHistoryEventHandler",
                "onServerStopped",
                "Combat history and clock synchronization cleanup completed"
        );
    }
}
