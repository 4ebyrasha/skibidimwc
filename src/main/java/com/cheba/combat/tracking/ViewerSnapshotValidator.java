package com.cheba.combat.tracking;

import com.cheba.combat.history.CombatHistoryManager;
import com.cheba.combat.history.HistoricalPlayerState;
import com.cheba.combat.history.PlayerSnapshot;
import com.cheba.combat.logging.CombatLog;
import com.cheba.combat.time.CombatClockSyncManager;
import com.paneedah.mwc.network.messages.TryFireMessage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ViewerSnapshotValidator {

    private static final long MAX_CLOCK_MAPPING_ERROR_NANOS =
            50_000_000L;

    private static final long MAX_RENDER_AFTER_SEND_NANOS =
            200_000_000L;

    private static final long MAX_REWIND_NANOS =
            200_000_000L;

    private static final int MAX_INTERPOLATION_STATES = 3;

    private static final double POSITION_TOLERANCE = 0.20D;
    private static final double SIZE_TOLERANCE = 0.05D;
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
        Set<Integer> processedEntityIds = new HashSet<>();

        int rejectedTargets = 0;

        for (TryFireMessage.TargetSnapshots targetData : submitted) {

            if (targetData == null) {
                reject(observer, null, "NULL_TARGET_DATA", "");
                rejectedTargets++;
                continue;
            }

            List<TryFireMessage.Snapshot> snapshots =
                    targetData.getSnapshots();

            if (snapshots == null || snapshots.isEmpty()) {
                reject(observer, targetData, "EMPTY_SNAPSHOTS", "");
                rejectedTargets++;
                continue;
            }

            if (snapshots.size()
                    > TryFireMessage.MAX_SNAPSHOTS_PER_TARGET) {
                reject(observer, targetData, "TOO_MANY_SNAPSHOTS",
                        "Count=" + snapshots.size());
                rejectedTargets++;
                continue;
            }

            if (!processedEntityIds.add(targetData.getEntityId())) {
                reject(observer, targetData, "DUPLICATE_TARGET", "");
                rejectedTargets++;
                continue;
            }

            Entity entity = observer.world.getEntityByID(
                    targetData.getEntityId()
            );

            if (!(entity instanceof EntityPlayerMP)) {
                reject(observer, targetData, "TARGET_NOT_FOUND_OR_NOT_PLAYER",
                        "");
                rejectedTargets++;
                continue;
            }

            EntityPlayerMP target = (EntityPlayerMP) entity;

            if (target == observer) {
                reject(observer, targetData, "TARGET_IS_OBSERVER", "");
                rejectedTargets++;
                continue;
            }

            if (target.isDead) {
                reject(observer, targetData, "TARGET_IS_DEAD", "");
                rejectedTargets++;
                continue;
            }

            if (target.dimension != observer.dimension) {
                reject(observer, targetData, "DIMENSION_MISMATCH",
                        "ObserverDimension=" + observer.dimension
                                + " | TargetDimension=" + target.dimension);
                rejectedTargets++;
                continue;
            }

            ViewerTrackingManager.ViewerTrackingState tracking =
                    ViewerTrackingManager.getState(
                            observer.getUniqueID(),
                            target.getUniqueID()
                    );

            if (tracking == null) {
                reject(observer, targetData, "TRACKING_STATE_MISSING", "");
                rejectedTargets++;
                continue;
            }

            if (tracking.getEntityId() != target.getEntityId()
                    || tracking.getDimension() != target.dimension) {
                reject(observer, targetData, "TRACKING_IDENTITY_MISMATCH",
                        "TrackingEntityId=" + tracking.getEntityId()
                                + " | TargetEntityId=" + target.getEntityId()
                                + " | TrackingDimension="
                                + tracking.getDimension()
                                + " | TargetDimension=" + target.dimension);
                rejectedTargets++;
                continue;
            }

            if (tracking.getEpoch() != targetData.getEpoch()) {
                reject(observer, targetData, "EPOCH_MISMATCH",
                        "Expected=" + tracking.getEpoch()
                                + " | Received=" + targetData.getEpoch());
                rejectedTargets++;
                continue;
            }

            Deque<ViewerTrackingManager.SentState> sentStates =
                    tracking.getSentStates();

            if (sentStates.isEmpty()) {
                reject(observer, targetData, "SENT_HISTORY_EMPTY", "");
                rejectedTargets++;
                continue;
            }

            /*
             * Capture the current authoritative target state once.
             * It may be used as a temporary interpolation endpoint when
             * the requested historical timestamp is newer than the last
             * persistent history snapshot.
             */
            PlayerSnapshot liveEndpoint;

            try {
                liveEndpoint = new PlayerSnapshot(
                        target,
                        System.nanoTime()
                );
            } catch (IllegalArgumentException exception) {
                reject(
                        observer,
                        targetData,
                        "TARGET_LIVE_STATE_INVALID",
                        exception.getMessage()
                );

                rejectedTargets++;
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
            String rejectionReason = null;
            String rejectionDetails = "";

            for (TryFireMessage.Snapshot snapshot : snapshots) {

                if (snapshot == null) {
                    rejectionReason = "NULL_SNAPSHOT";
                    break;
                }

                AxisAlignedBB box = snapshot.getBoundingBox();

                if (box == null) {
                    rejectionReason = "NULL_SNAPSHOT_AABB";
                    break;
                }

                long clientTimestamp = snapshot.getTimestampNanos();
                int sequence = snapshot.getSequence();

                if (hasPreviousTimestamp
                        && clientTimestamp <= previousClientTimestamp) {
                    rejectionReason = "TIMESTAMP_NOT_INCREASING";
                    rejectionDetails = "Sequence=" + sequence;
                    break;
                }

                if (sequence <= previousSequence) {
                    rejectionReason = "SEQUENCE_NOT_INCREASING";
                    rejectionDetails = "Previous=" + previousSequence
                            + " | Received=" + sequence;
                    break;
                }

                long snapshotAge;

                try {
                    snapshotAge = Math.subtractExact(
                            clientShotTimestampNanos,
                            clientTimestamp
                    );
                } catch (ArithmeticException exception) {
                    rejectionReason = "SNAPSHOT_AGE_OVERFLOW";
                    break;
                }

                if (snapshotAge < 0L) {
                    rejectionReason = "SNAPSHOT_FROM_FUTURE";
                    rejectionDetails = "AgeNanos=" + snapshotAge;
                    break;
                }

                if (snapshotAge > MAX_REWIND_NANOS) {
                    rejectionReason = "SNAPSHOT_TOO_OLD";
                    rejectionDetails = "AgeMs="
                            + snapshotAge / 1_000_000.0D;
                    break;
                }

                int stateIndex = findStateIndex(states, sequence);

                if (stateIndex < 0) {
                    rejectionReason = "SENT_SEQUENCE_NOT_FOUND";
                    rejectionDetails = "Sequence=" + sequence;
                    break;
                }

                if (previousStateIndex >= 0
                        && hasTeleportBetween(
                        states,
                        previousStateIndex,
                        stateIndex
                )) {
                    rejectionReason = "TELEPORT_BETWEEN_SNAPSHOTS";
                    rejectionDetails = "PreviousSequence="
                            + states.get(previousStateIndex).getSequence()
                            + " | CurrentSequence=" + sequence;
                    break;
                }

                ViewerTrackingManager.SentState sentState =
                        states.get(stateIndex);

                long mappedClientTimestamp =
                        CombatClockSyncManager.getEstimatedServerTime(
                                observer.getUniqueID(),
                                clientTimestamp
                        );

                if (mappedClientTimestamp
                        == CombatClockSyncManager.NO_ESTIMATE) {
                    rejectionReason = "CLOCK_SYNC_UNAVAILABLE";
                    rejectionDetails = "Sequence=" + sequence;
                    break;
                }

                long serverTimestamp;

                try {
                    serverTimestamp = Math.subtractExact(
                            serverShotTimestampNanos,
                            snapshotAge
                    );
                } catch (ArithmeticException exception) {
                    rejectionReason = "SERVER_TIMESTAMP_OVERFLOW";
                    break;
                }

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
                    rejectionReason = "CLOCK_CALCULATION_OVERFLOW";
                    break;
                }

                if (clockMappingError < -MAX_CLOCK_MAPPING_ERROR_NANOS
                        || clockMappingError > MAX_CLOCK_MAPPING_ERROR_NANOS) {
                    rejectionReason = "CLOCK_MAPPING_ERROR";
                    rejectionDetails = "Sequence=" + sequence
                            + " | ErrorMs="
                            + clockMappingError / 1_000_000.0D;
                    break;
                }

                if (renderAfterSend < -MAX_CLOCK_MAPPING_ERROR_NANOS
                        || renderAfterSend > MAX_RENDER_AFTER_SEND_NANOS) {
                    rejectionReason = "RENDER_AFTER_SEND_OUT_OF_RANGE";
                    rejectionDetails = "Sequence=" + sequence
                            + " | RenderAfterSendMs="
                            + renderAfterSend / 1_000_000.0D;
                    break;
                }

                /*
                 * Resolve the target's authoritative historical state
                 * at the exact timestamp that will be used by the
                 * validated snapshot.
                 */
                HistoricalPlayerState historicalState =
                        CombatHistoryManager.getHistoricalState(
                                target.getUniqueID(),
                                serverTimestamp,
                                liveEndpoint
                        );

                if (historicalState == null) {
                    String historyReason =
                            CombatHistoryManager.getHistoricalStateFailureReason(
                                    target.getUniqueID(),
                                    serverTimestamp,
                                    liveEndpoint
                            );

                    rejectionReason = "HISTORICAL_STATE_UNAVAILABLE";
                    rejectionDetails = "Sequence=" + sequence
                            + " | HistoryReason=" + historyReason;
                    break;
                }

                if (historicalState.getEntityId() != target.getEntityId()
                        || historicalState.getDimension() != target.dimension) {
                    rejectionReason = "HISTORICAL_IDENTITY_MISMATCH";
                    rejectionDetails = "Sequence=" + sequence
                            + " | HistoricalEntityId="
                            + historicalState.getEntityId()
                            + " | TargetEntityId=" + target.getEntityId()
                            + " | HistoricalDimension="
                            + historicalState.getDimension()
                            + " | TargetDimension=" + target.dimension;
                    break;
                }

                if (!hasValidDimensions(
                        box,
                        historicalState.getBoundingBox()
                )) {
                    rejectionReason = "INVALID_AABB_DIMENSIONS";
                    rejectionDetails = "Sequence=" + sequence;
                    break;
                }

                if (!matchesSentTrajectory(
                        box,
                        states,
                        stateIndex
                )) {
                    rejectionReason = "SENT_TRAJECTORY_MISMATCH";
                    rejectionDetails = "Sequence=" + sequence;
                    break;
                }

                accepted.add(new ValidatedSnapshot(
                        sequence,
                        serverTimestamp,
                        box
                ));

                CombatLog.trace(
                        "ViewerSnapshotValidator",
                        "validate",
                        "Accepted snapshot"
                                + " | Observer=" + observer.getUniqueID()
                                + " | Target=" + target.getUniqueID()
                                + " | Sequence=" + sequence
                                + " | AgeMs="
                                + snapshotAge / 1_000_000.0D
                                + " | ClockErrorMs="
                                + clockMappingError / 1_000_000.0D
                                + " | RenderAfterSendMs="
                                + renderAfterSend / 1_000_000.0D
                );

                previousClientTimestamp = clientTimestamp;
                previousSequence = sequence;
                previousStateIndex = stateIndex;
                latestStateIndex = stateIndex;
                hasPreviousTimestamp = true;
            }

            if (rejectionReason == null
                    && latestStateIndex >= 0
                    && hasTeleportBetween(
                    states,
                    latestStateIndex,
                    states.size() - 1
            )) {
                rejectionReason = "TELEPORT_AFTER_LATEST_SNAPSHOT";
                rejectionDetails = "LatestSequence="
                        + states.get(latestStateIndex).getSequence();
            }

            if (rejectionReason != null
                    || accepted.size() != snapshots.size()) {

                if (rejectionReason == null) {
                    rejectionReason = "SNAPSHOT_COUNT_MISMATCH";
                    rejectionDetails = "Accepted=" + accepted.size()
                            + " | Submitted=" + snapshots.size();
                }

                reject(
                        observer,
                        targetData,
                        rejectionReason,
                        rejectionDetails
                );

                rejectedTargets++;
                continue;
            }

            result.add(new ValidatedTarget(
                    target.getUniqueID(),
                    target.getEntityId(),
                    tracking.getEpoch(),
                    accepted
            ));

            CombatLog.debug(
                    "ViewerSnapshotValidator",
                    "validate",
                    "Accepted target"
                            + " | Observer=" + observer.getUniqueID()
                            + " | Target=" + target.getUniqueID()
                            + " | EntityId=" + target.getEntityId()
                            + " | Epoch=" + tracking.getEpoch()
                            + " | Snapshots=" + accepted.size()
            );
        }

        CombatLog.debug(
                "ViewerSnapshotValidator",
                "validate",
                "Validation completed"
                        + " | Observer=" + observer.getUniqueID()
                        + " | SubmittedTargets=" + submitted.size()
                        + " | AcceptedTargets=" + result.size()
                        + " | RejectedTargets=" + rejectedTargets
        );

        return result;
    }

    private static void reject(
            EntityPlayerMP observer,
            TryFireMessage.TargetSnapshots targetData,
            String reason,
            String details
    ) {
        CombatLog.debug(
                "ViewerSnapshotValidator",
                "validate",
                "Rejected target"
                        + " | Observer="
                        + (observer == null
                        ? "null"
                        : observer.getUniqueID())
                        + " | EntityId="
                        + (targetData == null
                        ? "unknown"
                        : targetData.getEntityId())
                        + " | Epoch="
                        + (targetData == null
                        ? "unknown"
                        : targetData.getEpoch())
                        + " | Reason=" + reason
                        + (details == null || details.isEmpty()
                        ? ""
                        : " | " + details)
        );
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
         * Do not compare snapshots across a teleport.
         * Preserve the existing behavior: only inspect the trajectory
         * after the latest teleport in the selected state window.
         */
        for (int i = firstIndex + 1; i <= stateIndex; i++) {
            if (states.get(i).isTeleport()) {
                firstIndex = i;
            }
        }

        ViewerTrackingManager.SentState current =
                states.get(stateIndex);

        /*
         * When no interpolation segment is available, compare directly
         * against the current authoritative sent state.
         */
        if (stateIndex == firstIndex) {
            double distanceSquared = distanceSquared(
                    x, y, z,
                    current.getX(),
                    current.getY(),
                    current.getZ()
            );

            boolean matches = distanceSquared <= toleranceSquared;

            if (!matches) {
                CombatLog.trace(
                        "ViewerSnapshotValidator",
                        "matchesSentTrajectory",
                        "Trajectory mismatch"
                                + " | Mode=SingleState"
                                + " | Sequence=" + current.getSequence()
                                + " | StateIndex=" + stateIndex
                                + " | ClientFoot=" + x + ", " + y + ", " + z
                                + " | ServerPosition="
                                + current.getX() + ", "
                                + current.getY() + ", "
                                + current.getZ()
                                + " | Distance="
                                + Math.sqrt(distanceSquared)
                                + " | Tolerance=" + POSITION_TOLERANCE
                );
            }

            return matches;
        }

        double minimumDistanceSquared = Double.POSITIVE_INFINITY;

        int nearestSegmentIndex = -1;
        double nearestSegmentLength = 0.0D;
        double nearestSegmentProgress = 0.0D;

        int candidateSegments = 0;
        int skippedLongSegments = 0;

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
                skippedLongSegments++;
                continue;
            }

            candidateSegments++;

            double progress;

            if (lengthSquared <= 1.0E-12D) {
                progress = 0.0D;
            } else {
                progress = (
                        (x - previous.getX()) * dx
                                + (y - previous.getY()) * dy
                                + (z - previous.getZ()) * dz
                ) / lengthSquared;

                progress = Math.max(
                        0.0D,
                        Math.min(1.0D, progress)
                );
            }

            double closestX = previous.getX() + dx * progress;
            double closestY = previous.getY() + dy * progress;
            double closestZ = previous.getZ() + dz * progress;

            double distanceSquared = distanceSquared(
                    x, y, z,
                    closestX, closestY, closestZ
            );

            if (distanceSquared < minimumDistanceSquared) {
                minimumDistanceSquared = distanceSquared;
                nearestSegmentIndex = i;
                nearestSegmentLength = Math.sqrt(lengthSquared);
                nearestSegmentProgress = progress;
            }
        }

        boolean matches =
                minimumDistanceSquared <= toleranceSquared;

        if (!matches) {
            String nearestSegment;

            if (nearestSegmentIndex < 0) {
                nearestSegment = "none";
            } else {
                ViewerTrackingManager.SentState previous =
                        states.get(nearestSegmentIndex - 1);

                ViewerTrackingManager.SentState next =
                        states.get(nearestSegmentIndex);

                nearestSegment =
                        previous.getSequence()
                                + "->"
                                + next.getSequence();
            }

            String minimumDistance =
                    Double.isInfinite(minimumDistanceSquared)
                            ? "unavailable"
                            : String.valueOf(
                            Math.sqrt(minimumDistanceSquared)
                    );

            CombatLog.trace(
                    "ViewerSnapshotValidator",
                    "matchesSentTrajectory",
                    "Trajectory mismatch"
                            + " | Mode=Interpolated"
                            + " | StateIndex=" + stateIndex
                            + " | FirstStateIndex=" + firstIndex
                            + " | CurrentSequence=" + current.getSequence()
                            + " | FirstSequence="
                            + states.get(firstIndex).getSequence()
                            + " | ClientFoot=" + x + ", " + y + ", " + z
                            + " | NearestSegment=" + nearestSegment
                            + " | NearestSegmentLength="
                            + nearestSegmentLength
                            + " | NearestSegmentProgress="
                            + nearestSegmentProgress
                            + " | MinimumDistance=" + minimumDistance
                            + " | PositionTolerance=" + POSITION_TOLERANCE
                            + " | CandidateSegments=" + candidateSegments
                            + " | SkippedLongSegments=" + skippedLongSegments
                            + " | MaxInterpolationDistance="
                            + MAX_INTERPOLATION_DISTANCE
                            + " | AvailableStates=" + states.size()
            );
        }

        return matches;
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

        private final UUID targetId;
        private final int entityId;
        private final long epoch;
        private final List<ValidatedSnapshot> snapshots;

        private ValidatedTarget(
                UUID targetId,
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

        public UUID getTargetId() {
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
