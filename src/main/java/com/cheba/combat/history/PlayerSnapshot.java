package com.cheba.combat.history;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.UUID;

public final class PlayerSnapshot {

    private final UUID playerId;
    private final int entityId;
    private final int dimension;

    private final long timestampNanos;

    private final double x;
    private final double y;
    private final double z;

    private final float yaw;
    private final float pitch;

    private final AxisAlignedBB boundingBox;

    public PlayerSnapshot(EntityPlayerMP player, long timestampNanos) {
        this.playerId = player.getUniqueID();
        this.entityId = player.getEntityId();
        this.dimension = player.dimension;

        this.timestampNanos = timestampNanos;

        this.x = player.posX;
        this.y = player.posY;
        this.z = player.posZ;

        this.yaw = player.rotationYaw;
        this.pitch = player.rotationPitch;

        AxisAlignedBB box = player.getEntityBoundingBox();
        this.boundingBox = new AxisAlignedBB(
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ
        );
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getEntityId() {
        return entityId;
    }

    public int getDimension() {
        return dimension;
    }

    public long getTimestampNanos() {
        return timestampNanos;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public AxisAlignedBB getBoundingBox() {
        return boundingBox;
    }
}
