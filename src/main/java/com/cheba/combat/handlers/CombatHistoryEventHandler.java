package com.cheba.combat.handlers;

import com.cheba.combat.history.CombatHistoryManager;
import com.cheba.combat.logging.CombatLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.FMLServerStoppedEvent;

/**
 * Handles player and server lifecycle events for combat history.
 *
 * <p>Movement snapshots are temporary server-side data. They must not
 * survive player disconnection, death, dimension changes, or server
 * shutdown.</p>
 *
 * <p>Player lifecycle events are received from the Forge event bus.
 * Server shutdown is received from the FML event bus.</p>
 */
public final class CombatHistoryEventHandler {

    private CombatHistoryEventHandler() {
    }

    /**
     * Removes all historical snapshots when a player disconnects.
     *
     * @param event Forge player logout event
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;

        CombatHistoryManager.remove(player.getUniqueID());
    }

    /**
     * Removes historical snapshots when a player dies.
     *
     * <p>A dead player's previous movement history must not be reused
     * after respawn. The respawned player starts with a fresh history.</p>
     *
     * @param event Forge living entity death event
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
     * Removes historical snapshots after a player changes dimension.
     *
     * <p>Coordinates from different dimensions do not belong to the same
     * spatial timeline. A new history will be created when the player
     * submits the next accepted movement packet.</p>
     *
     * @param event Forge player dimension-change event
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
     * Clears all combat histories when the dedicated or integrated server
     * has fully stopped.
     *
     * @param event FML server stopped event
     */
    @SubscribeEvent
    public static void onServerStopped(FMLServerStoppedEvent event) {
        CombatHistoryManager.clear();

        CombatLog.info(
                "CombatHistoryEventHandler",
                "onServerStopped",
                "Combat history cleanup completed"
        );
    }
}
