package com.cheba.combat.mixin;

import com.cheba.combat.tracking.ViewerTrackingManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketEntity;
import net.minecraft.network.play.server.SPacketEntityTeleport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(EntityTrackerEntry.class)
public abstract class EntityTrackerEntryMixin {

    @Shadow
    @Final
    private Entity trackedEntity;

    @Shadow
    @Final
    public Set<EntityPlayerMP> trackingPlayers;

    @Inject(
            method = "updatePlayerEntity",
            at = @At("RETURN")
    )
    private void mwc$onUpdatePlayerEntity(
            EntityPlayerMP observer,
            CallbackInfo ci
    ) {
        if (!(trackedEntity instanceof EntityPlayerMP)) {
            return;
        }

        if (trackingPlayers.contains(observer)) {
            ViewerTrackingManager.startTracking(
                    observer,
                    (EntityPlayerMP) trackedEntity
            );
        } else {
            ViewerTrackingManager.stopTracking(
                    observer.getUniqueID(),
                    trackedEntity.getUniqueID()
            );
        }
    }

    @Inject(
            method = "sendPacketToTrackedPlayers",
            at = @At("RETURN")
    )
    private void mwc$afterMovementPacket(
            Packet<?> packet,
            CallbackInfo ci
    ) {
        if (!(trackedEntity instanceof EntityPlayerMP)) {
            return;
        }

        if (!(packet instanceof SPacketEntity.S15PacketEntityRelMove)
                && !(packet instanceof SPacketEntity.S16PacketEntityLook)
                && !(packet instanceof SPacketEntity.S17PacketEntityLookMove)
                && !(packet instanceof SPacketEntityTeleport)) {
            return;
        }

        EntityPlayerMP target = (EntityPlayerMP) trackedEntity;

        for (EntityPlayerMP observer : trackingPlayers) {
            ViewerTrackingManager.recordMovement(
                    observer,
                    target,
                    packet
            );
        }
    }

    @Inject(
            method = "removeFromTrackedPlayers",
            at = @At("RETURN")
    )
    private void mwc$onRemoveFromTrackedPlayers(
            EntityPlayerMP observer,
            CallbackInfo ci
    ) {
        ViewerTrackingManager.stopTracking(
                observer.getUniqueID(),
                trackedEntity.getUniqueID()
        );
    }

    @Inject(
            method = "removeTrackedPlayerSymmetric",
            at = @At("RETURN")
    )
    private void mwc$onRemoveTrackedPlayerSymmetric(
            EntityPlayerMP observer,
            CallbackInfo ci
    ) {
        ViewerTrackingManager.stopTracking(
                observer.getUniqueID(),
                trackedEntity.getUniqueID()
        );
    }

    @Inject(
            method = "sendDestroyEntityPacketToTrackedPlayers",
            at = @At("RETURN")
    )
    private void mwc$onDestroyTrackedEntity(CallbackInfo ci) {
        if (trackedEntity instanceof EntityPlayerMP) {
            ViewerTrackingManager.stopTrackingTarget(
                    trackedEntity.getUniqueID()
            );
        }
    }
}
