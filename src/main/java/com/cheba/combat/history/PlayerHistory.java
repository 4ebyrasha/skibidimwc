package com.cheba.combat.history;

import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bounded chronological history of one player's authoritative snapshots.
 *
 * <p>Instances are owned by CombatHistoryManager and accessed exclusively
 * from the logical server thread.</p>
 *
 * <p>Historical queries never extrapolate. Interpolation is only permitted
 * between consecutive snapshots when their time gap is sufficiently small.</p>
 */
public final class PlayerHistory {

    private static final int MAX_SNAPSHOTS = 64;

    private static final long MAX_AGE_NANOS = 2_000_000_000L;

    static final long MAX_INTERPOLATION_GAP_NANOS = 250_000_000L;

    private final Deque<PlayerSnapshot> snapshots = new ArrayDeque<>();

    /**
     * Adds a snapshot if its timestamp is newer than the latest entry.
     *
     * @param snapshot authoritative player snapshot
     * @return true if accepted
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
     * Resolves a player's state at a server-monotonic timestamp.
     *
     * @param timestampNanos requested server timestamp
     * @return historical state, or null when unavailable
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

        // Extrapolation beyond the newest snapshot is intentionally forbidden.
        return null;
    }

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
                snapshot.getEyeHeight(),
                snapshot.getBoundingBox()
        );
    }

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

        long timeGap = toTimestamp - fromTimestamp;

        if (timeGap > MAX_INTERPOLATION_GAP_NANOS) {
            return null;
        }

        double progress = (double) (timestampNanos - fromTimestamp)
                / (double) timeGap;

        double x = lerp(from.getX(), to.getX(), progress);
        double y = lerp(from.getY(), to.getY(), progress);
        double z = lerp(from.getZ(), to.getZ(), progress);

        float yaw = interpolateAngle(
                from.getYaw(),
                to.getYaw(),
                progress
        );

        float pitch = (float) lerp(
                from.getPitch(),
                to.getPitch(),
                progress
        );

        float eyeHeight = (float) lerp(
                from.getEyeHeight(),
                to.getEyeHeight(),
                progress
        );

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
                eyeHeight,
                boundingBox
        );
    }

    private static double lerp(
            double from,
            double to,
            double progress
    ) {
        return from + (to - from) * progress;
    }

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

    public PlayerSnapshot getLatest() {
        return snapshots.peekLast();
    }

    public int size() {
        return snapshots.size();
    }

    public void clear() {
        snapshots.clear();
    }
}
