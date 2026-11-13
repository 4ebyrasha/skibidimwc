package com.cheba.combat.history;

import net.minecraft.util.math.AxisAlignedBB;

import java.util.UUID;

/**
 * Immutable representation of a player's historical state.
 *
 * <p>Unlike {@link PlayerSnapshot}, this class does not represent a state
 * captured at a specific packet-processing instant. It may also represent
 * an interpolated state between two snapshots.</p>
 *
 * <p>All timestamps use the server's monotonic {@link System#nanoTime()}
 * time domain. They must never be compared with timestamps obtained
 * from a different machine.</p>
 *
 * <p>The state is detached from the live player entity. Reading or using
 * this object cannot move, rotate, or otherwise modify that entity.</p>
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

    private final AxisAlignedBB boundingBox;

    /**
     * Creates a historical player state.
     *
     * @param playerId stable player UUID
     * @param entityId entity ID at the time represented by this state
     * @param dimension dimension containing the player
     * @param timestampNanos requested server-monotonic timestamp
     * @param x interpolated or captured X coordinate
     * @param y interpolated or captured Y coordinate
     * @param z interpolated or captured Z coordinate
     * @param yaw interpolated or captured yaw
     * @param pitch interpolated or captured pitch
     * @param boundingBox historical entity bounding box
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
            AxisAlignedBB boundingBox
    ) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId cannot be null");
        }

        if (boundingBox == null) {
            throw new IllegalArgumentException("boundingBox cannot be null");
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

        this.boundingBox = new AxisAlignedBB(
                boundingBox.minX,
                boundingBox.minY,
                boundingBox.minZ,
                boundingBox.maxX,
                boundingBox.maxY,
                boundingBox.maxZ
        );
    }

    /** @return UUID of the represented player */
    public UUID getPlayerId() {
        return playerId;
    }

    /** @return entity ID represented by this state */
    public int getEntityId() {
        return entityId;
    }

    /** @return dimension containing the represented state */
    public int getDimension() {
        return dimension;
    }

    /** @return requested server-monotonic timestamp */
    public long getTimestampNanos() {
        return timestampNanos;
    }

    /** @return historical X coordinate */
    public double getX() {
        return x;
    }

    /** @return historical Y coordinate */
    public double getY() {
        return y;
    }

    /** @return historical Z coordinate */
    public double getZ() {
        return z;
    }

    /** @return historical yaw in degrees */
    public float getYaw() {
        return yaw;
    }

    /** @return historical pitch in degrees */
    public float getPitch() {
        return pitch;
    }

    /**
     * Returns the historical bounding box.
     *
     * <p>{@link AxisAlignedBB} is immutable in Minecraft 1.12.2, so
     * returning this instance cannot mutate the stored state.</p>
     *
     * @return historical bounding box
     */
    public AxisAlignedBB getBoundingBox() {
        return boundingBox;
    }
}
