package com.cheba.combat.handlers;

import com.cheba.combat.fire.ServerFireRateLimiter;
import com.cheba.combat.history.CombatHistoryManager;
import com.cheba.combat.logging.CombatLog;
import com.cheba.combat.time.CombatClockSyncManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Handles combat history recording and lifecycle cleanup.
 */
public final class CombatHistoryEventHandler {

    private CombatHistoryEventHandler() {
    }

    /**
     * Records the final authoritative player state for the current tick.
     */
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

    /**
     * Removes player-specific combat data when the player disconnects.
     */
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

        CombatLog.debug(
                "CombatHistoryEventHandler",
                "onPlayerLogout",
                "Player combat state removed | Player=" + player.getUniqueID()
        );
    }

    /**
     * Removes movement history when a player dies.
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
     * Invalidates movement history after a player changes dimension.
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

}
