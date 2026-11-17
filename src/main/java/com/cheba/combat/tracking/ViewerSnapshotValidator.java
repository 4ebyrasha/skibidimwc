package com.cheba.combat.tracking;

import com.cheba.combat.time.CombatClockSyncManager;
import com.paneedah.mwc.network.messages.TryFireMessage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public final class ViewerSnapshotValidator {

    private static final long MAX_CLOCK_MAPPING_ERROR_NANOS =
            50_000_000L;

    private static final long MAX_RENDER_AFTER_SEND_NANOS =
            200_000_000L;

    private static final long MAX_REWIND_NANOS = 200_000_000L;

    /*
     * EntityOtherPlayerMP renders an interpolated position.
     * Allow the last three movement transitions preceding
     * the acknowledged sequence.
     */
    private static final int MAX_INTERPOLATION_STATES = 3;

    private static final double POSITION_TOLERANCE = 0.20D;
    private static final double SIZE_TOLERANCE = 0.05D;

    /*
     * A large displacement is treated as a discontinuity.
     * Do not accept arbitrary points along a teleport-like segment.
     */
    private static final double MAX_INTERPOLATION_DISTANCE = 2.5D;

    private ViewerSnapshotValidator() {
    }

    public static List<ValidatedTarget> validate(
            EntityPlayerMP observer,
            List<TryFireMessage.TargetSnapshots> submitted,
            long clientShotTimestampNanos,
            long serverShotTimestampNanos
    ) {
        if (observer == null
                || observer.world == null
                || observer.world.isRemote
                || submitted == null
                || submitted.isEmpty()
                || serverShotTimestampNanos
                == CombatClockSyncManager.NO_ESTIMATE) {
            return Collections.emptyList();
        }

        List<ValidatedTarget> result = new ArrayList<>();
        java.util.Set<Integer> processedEntityIds = new java.util.HashSet<>();

        for (TryFireMessage.TargetSnapshots targetData : submitted) {
            if (targetData == null
                    || targetData.getSnapshots() == null
                    || targetData.getSnapshots().isEmpty()
                    || targetData.getSnapshots().size()
                    > TryFireMessage.MAX_SNAPSHOTS_PER_TARGET
                    || !processedEntityIds.add(targetData.getEntityId())) {
                continue;
            }

            Entity entity = observer.world.getEntityByID(
                    targetData.getEntityId()
            );

            if (!(entity instanceof EntityPlayerMP)) {
                continue;
            }

            EntityPlayerMP target = (EntityPlayerMP) entity;

            if (target == observer
                    || target.isDead
                    || target.dimension != observer.dimension) {
                continue;
            }

            ViewerTrackingManager.ViewerTrackingState tracking =
                    ViewerTrackingManager.getState(
                            observer.getUniqueID(),
                            target.getUniqueID()
                    );

            if (tracking == null
                    || tracking.getEntityId() != target.getEntityId()
                    || tracking.getDimension() != target.dimension
                    || tracking.getEpoch() != targetData.getEpoch()) {
                continue;
            }

            Deque<ViewerTrackingManager.SentState> sentStates =
                    tracking.getSentStates();

            if (sentStates.isEmpty()) {
                continue;
            }

            List<ViewerTrackingManager.SentState> states =
                    new ArrayList<>(sentStates);

            List<ValidatedSnapshot> accepted = new ArrayList<>();

            long previousClientTimestamp = 0L;
            int previousSequence = -1;
            int previousStateIndex = -1;
            int latestStateIndex = -1;

            boolean hasPreviousTimestamp = false;
            boolean invalidSequence = false;

            for (TryFireMessage.Snapshot snapshot
                    : targetData.getSnapshots()) {

                if (snapshot == null
                        || snapshot.getBoundingBox() == null) {
                    invalidSequence = true;
                    break;
                }

                long clientTimestamp = snapshot.getTimestampNanos();
                int sequence = snapshot.getSequence();

                if ((hasPreviousTimestamp
                        && clientTimestamp <= previousClientTimestamp)
                        || sequence <= previousSequence) {
                    invalidSequence = true;
                    break;
                }

                long snapshotAge;

                try {
                    snapshotAge = Math.subtractExact(
                            clientShotTimestampNanos,
                            clientTimestamp
                    );
                } catch (ArithmeticException exception) {
                    invalidSequence = true;
                    break;
                }

                if (snapshotAge < 0L
                        || snapshotAge > MAX_REWIND_NANOS) {
                    invalidSequence = true;
                    break;
                }

                int stateIndex = findStateIndex(states, sequence);

                if (stateIndex < 0
                        || (previousStateIndex >= 0
                        && hasTeleportBetween(
                        states,
                        previousStateIndex,
                        stateIndex
                ))) {
                    invalidSequence = true;
                    break;
                }

                previousStateIndex = stateIndex;
                latestStateIndex = stateIndex;

                ViewerTrackingManager.SentState sentState =
                        states.get(stateIndex);

                long mappedClientTimestamp =
                        CombatClockSyncManager.getEstimatedServerTime(
                                observer.getUniqueID(),
                                clientTimestamp
                        );

                if (mappedClientTimestamp
                        == CombatClockSyncManager.NO_ESTIMATE) {
                    invalidSequence = true;
                    break;
                }

                AxisAlignedBB box = snapshot.getBoundingBox();

                if (!hasValidDimensions(
                        box,
                        target.getEntityBoundingBox()
                )) {
                    invalidSequence = true;
                    break;
                }

                if (!matchesSentTrajectory(
                        box,
                        states,
                        stateIndex
                )) {
                    invalidSequence = true;
                    break;
                }

                long serverTimestamp;

                try {
                    serverTimestamp = Math.subtractExact(
                            serverShotTimestampNanos,
                            snapshotAge
                    );
                } catch (ArithmeticException exception) {
                    invalidSequence = true;
                    break;
                }

                accepted.add(new ValidatedSnapshot(
                        sequence,
                        serverTimestamp,
                        box
                ));

                long clockMappingError;
                long renderAfterSend;

                try {
                    clockMappingError = Math.subtractExact(
                            mappedClientTimestamp,
                            serverTimestamp
                    );

                    renderAfterSend = Math.subtractExact(
                            mappedClientTimestamp,
                            sentState.getTimestampNanos()
                    );
                } catch (ArithmeticException exception) {
                    invalidSequence = true;
                    break;
                }

                if (clockMappingError < -MAX_CLOCK_MAPPING_ERROR_NANOS
                        || clockMappingError > MAX_CLOCK_MAPPING_ERROR_NANOS
                        || renderAfterSend < -MAX_CLOCK_MAPPING_ERROR_NANOS
                        || renderAfterSend > MAX_RENDER_AFTER_SEND_NANOS) {
                    invalidSequence = true;
                    break;
                }

                previousClientTimestamp = clientTimestamp;
                previousSequence = sequence;
                hasPreviousTimestamp = true;
            }

            /*
             * Reject viewer history if the target was teleported after
             * the latest rendered snapshot but before the shot.
             */
            if (!invalidSequence
                    && latestStateIndex >= 0
                    && hasTeleportBetween(
                    states,
                    latestStateIndex,
                    states.size() - 1
            )) {
                invalidSequence = true;
            }

            if (invalidSequence || accepted.size() != targetData.getSnapshots().size()) {
                continue;
            }

            result.add(new ValidatedTarget(
                    target.getUniqueID(),
                    target.getEntityId(),
                    tracking.getEpoch(),
                    accepted
            ));
        }

        return result;
    }

    private static boolean hasTeleportBetween(
            List<ViewerTrackingManager.SentState> states,
            int afterExclusive,
            int throughInclusive
    ) {
        for (int i = Math.max(0, afterExclusive + 1);
             i <= throughInclusive;
             i++) {

            if (states.get(i).isTeleport()) {
                return true;
            }
        }

        return false;
    }

    private static int findStateIndex(
            List<ViewerTrackingManager.SentState> states,
            int sequence
    ) {
        for (int i = states.size() - 1; i >= 0; i--) {
            if (states.get(i).getSequence() == sequence) {
                return i;
            }
        }

        return -1;
    }

    private static boolean hasValidDimensions(
            AxisAlignedBB submitted,
            AxisAlignedBB expected
    ) {
        return Math.abs(
                (submitted.maxX - submitted.minX)
                        - (expected.maxX - expected.minX)
        ) <= SIZE_TOLERANCE
                && Math.abs(
                (submitted.maxY - submitted.minY)
                        - (expected.maxY - expected.minY)
        ) <= SIZE_TOLERANCE
                && Math.abs(
                (submitted.maxZ - submitted.minZ)
                        - (expected.maxZ - expected.minZ)
        ) <= SIZE_TOLERANCE;
    }

    private static boolean matchesSentTrajectory(
            AxisAlignedBB box,
            List<ViewerTrackingManager.SentState> states,
            int stateIndex
    ) {
        double x = (box.minX + box.maxX) * 0.5D;
        double y = box.minY;
        double z = (box.minZ + box.maxZ) * 0.5D;

        double toleranceSquared =
                POSITION_TOLERANCE * POSITION_TOLERANCE;

        int firstIndex = Math.max(
                0,
                stateIndex - MAX_INTERPOLATION_STATES
        );

        /*
         * Ignore every movement segment before the latest teleport.
         */
        for (int i = firstIndex + 1; i <= stateIndex; i++) {
            if (states.get(i).isTeleport()) {
                firstIndex = i;
            }
        }

        ViewerTrackingManager.SentState current =
                states.get(stateIndex);

        if (stateIndex == firstIndex) {
            return distanceSquared(
                    x, y, z,
                    current.getX(),
                    current.getY(),
                    current.getZ()
            ) <= toleranceSquared;
        }

        for (int i = firstIndex + 1; i <= stateIndex; i++) {
            ViewerTrackingManager.SentState previous =
                    states.get(i - 1);

            ViewerTrackingManager.SentState next =
                    states.get(i);

            double dx = next.getX() - previous.getX();
            double dy = next.getY() - previous.getY();
            double dz = next.getZ() - previous.getZ();

            double lengthSquared =
                    dx * dx + dy * dy + dz * dz;

            if (lengthSquared
                    > MAX_INTERPOLATION_DISTANCE
                    * MAX_INTERPOLATION_DISTANCE) {
                continue;
            }

            if (distanceToSegmentSquared(
                    x, y, z,
                    previous.getX(),
                    previous.getY(),
                    previous.getZ(),
                    next.getX(),
                    next.getY(),
                    next.getZ()
            ) <= toleranceSquared) {
                return true;
            }
        }

        return false;
    }

    private static double distanceToSegmentSquared(
            double px, double py, double pz,
            double ax, double ay, double az,
            double bx, double by, double bz
    ) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;

        double lengthSquared =
                dx * dx + dy * dy + dz * dz;

        if (lengthSquared <= 1.0E-12D) {
            return distanceSquared(
                    px, py, pz,
                    ax, ay, az
            );
        }

        double progress = (
                (px - ax) * dx
                        + (py - ay) * dy
                        + (pz - az) * dz
        ) / lengthSquared;

        progress = Math.max(0.0D, Math.min(1.0D, progress));

        return distanceSquared(
                px, py, pz,
                ax + dx * progress,
                ay + dy * progress,
                az + dz * progress
        );
    }

    private static double distanceSquared(
            double ax, double ay, double az,
            double bx, double by, double bz
    ) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;

        return dx * dx + dy * dy + dz * dz;
    }

    public static final class ValidatedTarget {

        private final java.util.UUID targetId;
        private final int entityId;
        private final long epoch;
        private final List<ValidatedSnapshot> snapshots;

        private ValidatedTarget(
                java.util.UUID targetId,
                int entityId,
                long epoch,
                List<ValidatedSnapshot> snapshots
        ) {
            this.targetId = targetId;
            this.entityId = entityId;
            this.epoch = epoch;
            this.snapshots = Collections.unmodifiableList(
                    new ArrayList<>(snapshots)
            );
        }

        public java.util.UUID getTargetId() {
            return targetId;
        }

        public int getEntityId() {
            return entityId;
        }

        public long getEpoch() {
            return epoch;
        }

        public List<ValidatedSnapshot> getSnapshots() {
            return snapshots;
        }
    }

    public static final class ValidatedSnapshot {

        private final int sequence;
        private final long timestampNanos;
        private final AxisAlignedBB boundingBox;

        private ValidatedSnapshot(
                int sequence,
                long timestampNanos,
                AxisAlignedBB boundingBox
        ) {
            this.sequence = sequence;
            this.timestampNanos = timestampNanos;
            this.boundingBox = boundingBox;
        }

        public int getSequence() {
            return sequence;
        }

        public long getTimestampNanos() {
            return timestampNanos;
        }

        public AxisAlignedBB getBoundingBox() {
            return boundingBox;
        }
    }
}
