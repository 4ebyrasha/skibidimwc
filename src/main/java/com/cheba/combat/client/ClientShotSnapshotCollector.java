package com.cheba.combat.client;

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
            return targets;
        }

        for (int entityId : ClientRenderedPlayerHistory.getEntityIds()) {
            if (entityId == shooter.getEntityId()) {
                continue;
            }

            List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot> history =
                    ClientRenderedPlayerHistory.getSnapshots(entityId);

            if (history.isEmpty()) {
                continue;
            }

            List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot> recent =
                    new ArrayList<>();

            long oldestAllowed = shotTimestampNanos - MAX_AGE_NANOS;

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

        if (targets.size() > MAX_TARGETS) {
            return new ArrayList<>(targets.subList(0, MAX_TARGETS));
        }

        return targets;
    }

    private static List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot>
    selectSnapshots(
            List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot> source
    ) {
        int count = source.size();

        if (count <= MAX_SNAPSHOTS_PER_TARGET) {
            return source;
        }

        List<ClientRenderedPlayerHistory.RenderedPlayerSnapshot> result =
                new ArrayList<>(MAX_SNAPSHOTS_PER_TARGET);

        for (int i = 0; i < MAX_SNAPSHOTS_PER_TARGET; i++) {
            int index = i * (count - 1)
                    / (MAX_SNAPSHOTS_PER_TARGET - 1);

            result.add(source.get(index));
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
