package com.cheba.combat.history;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.UUID;

/**
 * Immutable snapshot of a player's authoritative server-side state.
 *
 * <p>The snapshot captures position, rotation, eye height, dimension and
 * bounding box from the server-side player entity.</p>
 *
 * <p>The timestamp belongs to the server's System.nanoTime() time domain.</p>
 */
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
    private final float eyeHeight;

    private final AxisAlignedBB boundingBox;

    /**
     * Captures the current authoritative state of a server-side player.
     *
     * @param player server-side player
     * @param timestampNanos timestamp captured by the server
     * @throws IllegalArgumentException if the player state is invalid
     */
    public PlayerSnapshot(EntityPlayerMP player, long timestampNanos) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }

        this.playerId = player.getUniqueID();
        this.entityId = player.getEntityId();
        this.dimension = player.dimension;
        this.timestampNanos = timestampNanos;

        this.x = player.posX;
        this.y = player.posY;
        this.z = player.posZ;

        this.yaw = player.rotationYaw;
        this.pitch = player.rotationPitch;
        this.eyeHeight = player.getEyeHeight();

        AxisAlignedBB box = player.getEntityBoundingBox();

        if (!isFinite(x) || !isFinite(y) || !isFinite(z)
                || !isFinite(yaw) || !isFinite(pitch)
                || !isFinite(eyeHeight) || eyeHeight < 0.0F
                || !isValidBoundingBox(box)) {
            throw new IllegalArgumentException(
                    "Player snapshot contains invalid position, rotation, eye height or bounding box"
            );
        }

        this.boundingBox = new AxisAlignedBB(
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ
        );
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean isValidBoundingBox(AxisAlignedBB box) {
        if (box == null) {
            return false;
        }

        return isFinite(box.minX)
                && isFinite(box.minY)
                && isFinite(box.minZ)
                && isFinite(box.maxX)
                && isFinite(box.maxY)
                && isFinite(box.maxZ)
                && box.minX <= box.maxX
                && box.minY <= box.maxY
                && box.minZ <= box.maxZ;
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

    /**
     * Returns the player's eye height at the captured moment.
     *
     * @return historical eye height
     */
    public float getEyeHeight() {
        return eyeHeight;
    }

    /**
     * Returns the captured bounding box.
     *
     * @return captured bounding box
     */
    public AxisAlignedBB getBoundingBox() {
        return boundingBox;
    }
}
