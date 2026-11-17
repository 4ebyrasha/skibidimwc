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

    public static synchronized void record(
            int entityId,
            long epoch,
            int sequence,
            long timestampNanos,
            AxisAlignedBB boundingBox
    ) {
        if (entityId < 0 || epoch <= 0L || sequence < 0
                || timestampNanos <= 0L || boundingBox == null) {
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

    public static synchronized List<RenderedPlayerSnapshot> getSnapshots(
            int entityId
    ) {
        Deque<RenderedPlayerSnapshot> snapshots = HISTORY.get(entityId);

        return snapshots == null
                ? new ArrayList<>()
                : new ArrayList<>(snapshots);
    }

    public static synchronized List<Integer> getEntityIds() {
        return new ArrayList<>(HISTORY.keySet());
    }

    public static synchronized void remove(int entityId) {
        HISTORY.remove(entityId);
    }

    public static synchronized void pruneStale(long nowNanos) {
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

    public static synchronized void clear() {
        HISTORY.clear();
    }

    private static void trim(
            Deque<RenderedPlayerSnapshot> snapshots,
            long nowNanos
    ) {
        while (!snapshots.isEmpty()) {
            RenderedPlayerSnapshot oldest = snapshots.peekFirst();

            if (nowNanos - oldest.getTimestampNanos() <= MAX_AGE_NANOS
                    && snapshots.size() <= MAX_SNAPSHOTS) {
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

        public RenderedPlayerSnapshot(
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
