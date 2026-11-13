package com.cheba.combat.history;

import net.minecraft.util.math.AxisAlignedBB;

import java.util.UUID;

/**
 * Immutable representation of a player's historical state.
 *
 * <p>The state may represent either an exact snapshot or an interpolated
 * state between two consecutive snapshots.</p>
 *
 * <p>All timestamps belong to the server's System.nanoTime() time domain.</p>
 */
public final class HistoricalPlayerState {

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
     * Creates an immutable historical player state.
     *
     * @param playerId player UUID
     * @param entityId historical entity ID
     * @param dimension historical dimension
     * @param timestampNanos requested server timestamp
     * @param x historical X coordinate
     * @param y historical Y coordinate
     * @param z historical Z coordinate
     * @param yaw historical yaw
     * @param pitch historical pitch
     * @param eyeHeight historical eye height
     * @param boundingBox historical bounding box
     */
    public HistoricalPlayerState(
            UUID playerId,
            int entityId,
            int dimension,
            long timestampNanos,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            float eyeHeight,
            AxisAlignedBB boundingBox
    ) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId cannot be null");
        }

        if (boundingBox == null) {
            throw new IllegalArgumentException("boundingBox cannot be null");
        }

        if (Float.isNaN(eyeHeight)
                || Float.isInfinite(eyeHeight)
                || eyeHeight < 0.0F) {
            throw new IllegalArgumentException("Invalid eye height");
        }

        this.playerId = playerId;
        this.entityId = entityId;
        this.dimension = dimension;
        this.timestampNanos = timestampNanos;

        this.x = x;
        this.y = y;
        this.z = z;

        this.yaw = yaw;
        this.pitch = pitch;
        this.eyeHeight = eyeHeight;

        this.boundingBox = new AxisAlignedBB(
                boundingBox.minX,
                boundingBox.minY,
                boundingBox.minZ,
                boundingBox.maxX,
                boundingBox.maxY,
                boundingBox.maxZ
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

    /**
     * Returns the eye height at the represented moment.
     *
     * @return historical eye height
     */
    public float getEyeHeight() {
        return eyeHeight;
    }

    public AxisAlignedBB getBoundingBox() {
        return boundingBox;
    }
}
