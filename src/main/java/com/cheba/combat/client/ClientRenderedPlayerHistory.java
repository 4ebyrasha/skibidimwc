package com.cheba.combat.client;

import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class ClientRenderedPlayerHistory {

    private static final int MAX_SNAPSHOTS = 64;
    private static final long MAX_AGE_NANOS = 2_000_000_000L;

    private static final Map<Integer, Deque<RenderedPlayerSnapshot>> HISTORY =
            new HashMap<>();

    private ClientRenderedPlayerHistory() {
    }

    public static void record(
            int entityId,
            long epoch,
            int sequence,
            long timestampNanos,
            AxisAlignedBB boundingBox
    ) {
        if (entityId < 0
                || epoch <= 0L
                || sequence < 0
                || boundingBox == null) {
            return;
        }

        Deque<RenderedPlayerSnapshot> snapshots =
                HISTORY.computeIfAbsent(entityId, id -> new ArrayDeque<>());

        RenderedPlayerSnapshot latest = snapshots.peekLast();

        if (latest != null && latest.getEpoch() != epoch) {
            snapshots.clear();
            latest = null;
        }

        if (latest != null && sequence < latest.getSequence()) {
            return;
        }

        snapshots.addLast(new RenderedPlayerSnapshot(
                entityId,
                epoch,
                sequence,
                timestampNanos,
                boundingBox
        ));

        trim(snapshots, timestampNanos);

        if (snapshots.isEmpty()) {
            HISTORY.remove(entityId);
        }
    }

    public static List<RenderedPlayerSnapshot> getSnapshots(int entityId) {
        Deque<RenderedPlayerSnapshot> snapshots = HISTORY.get(entityId);

        if (snapshots == null || snapshots.isEmpty()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(snapshots);
    }

    public static void remove(int entityId) {
        HISTORY.remove(entityId);
    }

    public static void pruneStale(long nowNanos) {
        Iterator<Map.Entry<Integer, Deque<RenderedPlayerSnapshot>>> iterator =
                HISTORY.entrySet().iterator();

        while (iterator.hasNext()) {
            Deque<RenderedPlayerSnapshot> snapshots =
                    iterator.next().getValue();

            trim(snapshots, nowNanos);

            if (snapshots.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public static void clear() {
        HISTORY.clear();
    }

    private static void trim(
            Deque<RenderedPlayerSnapshot> snapshots,
            long nowNanos
    ) {
        while (!snapshots.isEmpty()) {
            RenderedPlayerSnapshot oldest = snapshots.peekFirst();

            boolean tooOld =
                    nowNanos - oldest.getTimestampNanos() > MAX_AGE_NANOS;

            boolean tooMany = snapshots.size() > MAX_SNAPSHOTS;

            if (!tooOld && !tooMany) {
                break;
            }

            snapshots.removeFirst();
        }
    }

    public static final class RenderedPlayerSnapshot {

        private final int entityId;
        private final long epoch;
        private final int sequence;
        private final long timestampNanos;
        private final AxisAlignedBB boundingBox;

        private RenderedPlayerSnapshot(
                int entityId,
                long epoch,
                int sequence,
                long timestampNanos,
                AxisAlignedBB boundingBox
        ) {
            this.entityId = entityId;
            this.epoch = epoch;
            this.sequence = sequence;
            this.timestampNanos = timestampNanos;
            this.boundingBox = boundingBox;
        }

        public int getEntityId() {
            return entityId;
        }

        public long getEpoch() {
            return epoch;
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
