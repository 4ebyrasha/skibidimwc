package com.cheba.knockback;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;

import net.minecraft.util.ResourceLocation;

import static com.paneedah.mwc.ProjectConstants.ID;

public final class KnockbackCapabilityHandler {

    private static final ResourceLocation CAPABILITY_ID =
            new ResourceLocation(ID, "knockback_resistance");

    @SubscribeEvent
    public static void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof EntityPlayer)) {
            return;
        }

        event.addCapability(
                CAPABILITY_ID,
                new KnockbackCapability()
        );
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        KnockbackCapability.apply(event.player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerRespawnEvent event) {
        KnockbackCapability.apply(event.player);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        EntityPlayer original = event.getOriginal();
        EntityPlayer replacement = event.getEntityPlayer();

        if (replacement.world.isRemote) {
            return;
        }

        KnockbackCapability.copy(original, replacement);
        KnockbackCapability.apply(replacement);
    }
}
