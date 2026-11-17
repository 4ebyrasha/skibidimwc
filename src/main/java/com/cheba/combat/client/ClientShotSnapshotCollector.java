package com.cheba.combat.client;

import com.cheba.combat.logging.CombatLog;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class ClientShotSnapshotCollector {

    private static final long MAX_AGE_NANOS = 200_000_000L;

    private static final int MAX_TARGETS = 16;
    private static final int MAX_SNAPSHOTS_PER_TARGET = 4;

    private ClientShotSnapshotCollector() {
    }

    public static List<ClientTargetSnapshots> collect(
            EntityLivingBase shooter,
            long shotTimestampNanos
    ) {
        List<ClientTargetSnapshots> targets = new ArrayList<>();

        if (shooter == null || shooter.world == null
                || !shooter.world.isRemote) {
            CombatLog.debug(
                    "ClientShotSnapshotCollector",
                    "collect",
                    "Snapshot collection skipped: invalid client shooter"
            );
            return targets;
        }

        List<Integer> entityIds =
                ClientRenderedPlayerHistory.getEntityIds();

        int targetsWithHistory = 0;
        int targetsWithRecentSnapshots = 0;

        long oldestAllowed = shotTimestampNanos < Long.MIN_VALUE + MAX_AGE_NANOS
                ? Long.MIN_VALUE
                : shotTimestampNanos - MAX_AGE_NANOS;

        for (int entityId : entityIds) {
            if (entityId == shooter.getEntityId()) {
                continue;
            }

            List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot> history =
                    ClientRenderedPlayerHistory.getSnapshots(entityId);

            if (history.isEmpty()) {
                continue;
            }

            targetsWithHistory++;

            List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot> recent =
                    new ArrayList<>();

            for (ClientRenderedPlayerHistory.RenderedPlayerSnapshot snapshot
                    : history) {

                long timestamp = snapshot.getTimestampNanos();

                if (timestamp < oldestAllowed
                        || timestamp > shotTimestampNanos) {
                    continue;
                }

                recent.add(snapshot);
            }

            if (recent.isEmpty()) {
                continue;
            }

            targetsWithRecentSnapshots++;

            List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot> selected =
                    selectSnapshots(recent);

            if (selected.isEmpty()) {
                continue;
            }

            AxisAlignedBB box =
                    selected.get(selected.size() - 1).getBoundingBox();

            double dx = box.getCenter().x - shooter.posX;
            double dy = box.getCenter().y - shooter.posY;
            double dz = box.getCenter().z - shooter.posZ;

            targets.add(new ClientTargetSnapshots(
                    entityId,
                    selected,
                    dx * dx + dy * dy + dz * dz
            ));
        }

        targets.sort(Comparator.comparingDouble(
                target -> target.distanceSquared
        ));

        int eligibleTargets = targets.size();

        if (targets.size() > MAX_TARGETS) {
            targets = new ArrayList<>(
                    targets.subList(0, MAX_TARGETS)
            );
        }

        CombatLog.debug(
                "ClientShotSnapshotCollector",
                "collect",
                "Snapshot collection completed"
                        + " | ShooterEntityId=" + shooter.getEntityId()
                        + " | ShotTimestampNanos=" + shotTimestampNanos
                        + " | TrackedEntities=" + entityIds.size()
                        + " | TargetsWithHistory=" + targetsWithHistory
                        + " | TargetsWithRecentSnapshots="
                        + targetsWithRecentSnapshots
                        + " | EligibleTargets=" + eligibleTargets
                        + " | SelectedTargets=" + targets.size()
                        + " | OmittedTargets="
                        + (eligibleTargets - targets.size())
        );

        for (ClientTargetSnapshots target : targets) {
            List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot>
                    snapshots = target.getSnapshots();

            ClientRenderedPlayerHistory.RenderedPlayerSnapshot latest =
                    snapshots.get(snapshots.size() - 1);

            CombatLog.trace(
                    "ClientShotSnapshotCollector",
                    "collect",
                    "Selected target"
                            + " | EntityId=" + target.getEntityId()
                            + " | SnapshotCount=" + snapshots.size()
                            + " | LatestEpoch=" + latest.getEpoch()
                            + " | LatestSequence=" + latest.getSequence()
                            + " | LatestAgeMs="
                            + (shotTimestampNanos
                            - latest.getTimestampNanos()) / 1_000_000.0D
                            + " | Distance=" + Math.sqrt(target.distanceSquared)
            );

            for (ClientRenderedPlayerHistory.RenderedPlayerSnapshot snapshot
                    : snapshots) {

                AxisAlignedBB box = snapshot.getBoundingBox();

                CombatLog.trace(
                        "ClientShotSnapshotCollector",
                        "collect",
                        "Submitted snapshot"
                                + " | EntityId=" + snapshot.getEntityId()
                                + " | Epoch=" + snapshot.getEpoch()
                                + " | Sequence=" + snapshot.getSequence()
                                + " | TimestampNanos="
                                + snapshot.getTimestampNanos()
                                + " | AgeMs="
                                + (shotTimestampNanos
                                - snapshot.getTimestampNanos())
                                / 1_000_000.0D
                                + " | AABB="
                                + box.minX + "," + box.minY + "," + box.minZ
                                + " -> "
                                + box.maxX + "," + box.maxY + "," + box.maxZ
                );
            }
        }

        return targets;
    }

    private static List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot>
    selectSnapshots(
            List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot> source
    ) {
        List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot> distinct =
                new ArrayList<>(source.size());

        /*
         * Rendering can produce multiple snapshots for the same network
         * sequence. Keep only the latest rendered position for each sequence.
         */
        for (ClientRenderedPlayerHistory.RenderedPlayerSnapshot snapshot : source) {
            int lastIndex = distinct.size() - 1;

            if (lastIndex >= 0
                    && distinct.get(lastIndex).getSequence()
                    == snapshot.getSequence()) {
                distinct.set(lastIndex, snapshot);
            } else {
                distinct.add(snapshot);
            }
        }

        int count = distinct.size();

        if (count <= MAX_SNAPSHOTS_PER_TARGET) {
            return distinct;
        }

        List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot> result =
                new ArrayList<>(MAX_SNAPSHOTS_PER_TARGET);

        for (int i = 0; i < MAX_SNAPSHOTS_PER_TARGET; i++) {
            int index = i * (count - 1)
                    / (MAX_SNAPSHOTS_PER_TARGET - 1);

            result.add(distinct.get(index));
        }

        return result;
    }

    public static final class ClientTargetSnapshots {

        private final int entityId;

        private final List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot>
                snapshots;

        private final double distanceSquared;

        private ClientTargetSnapshots(
                int entityId,
                List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot> snapshots,
                double distanceSquared
        ) {
            this.entityId = entityId;
            this.snapshots = snapshots;
            this.distanceSquared = distanceSquared;
        }

        public int getEntityId() {
            return entityId;
        }

        public List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot>
        getSnapshots() {
            return snapshots;
        }
    }
}
