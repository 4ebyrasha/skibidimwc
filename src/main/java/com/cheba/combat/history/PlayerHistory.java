package com.cheba.combat.history;

import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bounded chronological history of one player's authoritative snapshots.
 *
 * <p>Instances are owned by {@link CombatHistoryManager} and are expected
 * to be accessed exclusively from the logical server thread.</p>
 *
 * <p>Snapshots must have strictly increasing server-monotonic timestamps.
 * The history rejects out-of-order or duplicate timestamps and removes
 * entries exceeding its configured age or capacity limits.</p>
 *
 * <p>Historical queries never extrapolate. A query can only be resolved
 * when its timestamp is covered by the retained history.</p>
 */
public final class PlayerHistory {

    /** Maximum number of snapshots retained for one player. */
    private static final int MAX_SNAPSHOTS = 64;

    /** Maximum age of retained snapshots: two seconds. */
    private static final long MAX_AGE_NANOS = 2_000_000_000L;

    private final Deque<PlayerSnapshot> snapshots = new ArrayDeque<>();

    /**
     * Adds a snapshot if its timestamp is newer than the latest entry.
     *
     * @param snapshot authoritative player snapshot
     * @return {@code true} if the snapshot was accepted
     */
    public boolean add(PlayerSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }

        PlayerSnapshot latest = snapshots.peekLast();

        if (latest != null
                && snapshot.getTimestampNanos() <= latest.getTimestampNanos()) {
            return false;
        }

        snapshots.addLast(snapshot);
        trim(snapshot.getTimestampNanos());

        return true;
    }

    /**
     * Removes snapshots that exceed the retention age or capacity.
     *
     * @param currentTimestamp timestamp of the newest accepted snapshot
     */
    private void trim(long currentTimestamp) {
        while (!snapshots.isEmpty()) {
            PlayerSnapshot oldest = snapshots.peekFirst();

            boolean tooOld =
                    currentTimestamp - oldest.getTimestampNanos() > MAX_AGE_NANOS;

            boolean tooMany = snapshots.size() > MAX_SNAPSHOTS;

            if (!tooOld && !tooMany) {
                break;
            }

            snapshots.removeFirst();
        }
    }

    /**
     * Resolves the player's state at a server-monotonic timestamp.
     *
     * <p>If the requested timestamp exactly matches a retained snapshot,
     * that snapshot is returned as a historical state without interpolation.</p>
     *
     * <p>Otherwise, the method searches for two consecutive snapshots
     * surrounding the requested timestamp and interpolates between them.
     * It never extrapolates beyond the oldest or newest retained snapshot.</p>
     *
     * <p>Interpolation is rejected if the snapshots do not represent the
     * same player entity in the same dimension, or if their timestamps
     * are not strictly increasing.</p>
     *
     * @param timestampNanos requested timestamp in the server's
     *                       {@link System#nanoTime()} time domain
     * @return resolved historical state, or {@code null} if the timestamp
     *         is outside the retained history or cannot be interpolated
     */
    public HistoricalPlayerState getStateAt(long timestampNanos) {
        if (snapshots.isEmpty()) {
            return null;
        }

        PlayerSnapshot previous = null;

        for (PlayerSnapshot current : snapshots) {
            long currentTimestamp = current.getTimestampNanos();

            if (timestampNanos == currentTimestamp) {
                return fromSnapshot(current, timestampNanos);
            }

            if (timestampNanos < currentTimestamp) {
                if (previous == null) {
                    return null;
                }

                return interpolate(previous, current, timestampNanos);
            }

            previous = current;
        }

        // The requested time is newer than the latest retained snapshot.
        // Extrapolation is intentionally forbidden.
        return null;
    }

    /**
     * Converts a captured snapshot into a historical state.
     *
     * @param snapshot source snapshot
     * @param timestampNanos requested timestamp
     * @return immutable historical state
     */
    private HistoricalPlayerState fromSnapshot(
            PlayerSnapshot snapshot,
            long timestampNanos
    ) {
        return new HistoricalPlayerState(
                snapshot.getPlayerId(),
                snapshot.getEntityId(),
                snapshot.getDimension(),
                timestampNanos,
                snapshot.getX(),
                snapshot.getY(),
                snapshot.getZ(),
                snapshot.getYaw(),
                snapshot.getPitch(),
                snapshot.getBoundingBox()
        );
    }

    /**
     * Interpolates a state between two consecutive snapshots.
     *
     * @param from earlier snapshot
     * @param to later snapshot
     * @param timestampNanos requested timestamp
     * @return interpolated state, or {@code null} if the snapshots are
     *         incompatible
     */
    private HistoricalPlayerState interpolate(
            PlayerSnapshot from,
            PlayerSnapshot to,
            long timestampNanos
    ) {
        if (!from.getPlayerId().equals(to.getPlayerId())
                || from.getEntityId() != to.getEntityId()
                || from.getDimension() != to.getDimension()) {
            return null;
        }

        long fromTimestamp = from.getTimestampNanos();
        long toTimestamp = to.getTimestampNanos();

        if (timestampNanos <= fromTimestamp
                || timestampNanos >= toTimestamp
                || toTimestamp <= fromTimestamp) {
            return null;
        }

        double progress = (double) (timestampNanos - fromTimestamp)
                / (double) (toTimestamp - fromTimestamp);

        double x = lerp(from.getX(), to.getX(), progress);
        double y = lerp(from.getY(), to.getY(), progress);
        double z = lerp(from.getZ(), to.getZ(), progress);

        float yaw = interpolateAngle(from.getYaw(), to.getYaw(), progress);
        float pitch = (float) lerp(from.getPitch(), to.getPitch(), progress);

        AxisAlignedBB fromBox = from.getBoundingBox();
        AxisAlignedBB toBox = to.getBoundingBox();

        AxisAlignedBB boundingBox = new AxisAlignedBB(
                lerp(fromBox.minX, toBox.minX, progress),
                lerp(fromBox.minY, toBox.minY, progress),
                lerp(fromBox.minZ, toBox.minZ, progress),
                lerp(fromBox.maxX, toBox.maxX, progress),
                lerp(fromBox.maxY, toBox.maxY, progress),
                lerp(fromBox.maxZ, toBox.maxZ, progress)
        );

        return new HistoricalPlayerState(
                from.getPlayerId(),
                from.getEntityId(),
                from.getDimension(),
                timestampNanos,
                x,
                y,
                z,
                yaw,
                pitch,
                boundingBox
        );
    }

    /**
     * Performs linear interpolation between two scalar values.
     *
     * @param from starting value
     * @param to ending value
     * @param progress interpolation factor in the range (0, 1)
     * @return interpolated value
     */
    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    /**
     * Interpolates an angle using the shortest path around the 360-degree
     * circle.
     *
     * <p>This avoids interpolating from 179 degrees to -179 degrees through
     * zero, which would produce an incorrect 358-degree rotation.</p>
     *
     * @param from starting angle in degrees
     * @param to ending angle in degrees
     * @param progress interpolation factor
     * @return interpolated angle in degrees
     */
    private static float interpolateAngle(
            float from,
            float to,
            double progress
    ) {
        float difference = (to - from) % 360.0F;

        if (difference >= 180.0F) {
            difference -= 360.0F;
        } else if (difference < -180.0F) {
            difference += 360.0F;
        }

        return from + (float) (difference * progress);
    }

    /** @return newest retained snapshot, or {@code null} if empty */
    public PlayerSnapshot getLatest() {
        return snapshots.peekLast();
    }

    /** @return number of retained snapshots */
    public int size() {
        return snapshots.size();
    }

    /** Removes all snapshots from this history. */
    public void clear() {
        snapshots.clear();
    }
}
