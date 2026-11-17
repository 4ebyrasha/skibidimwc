package com.cheba.combat.tracking;

import com.cheba.combat.logging.CombatLog;
import com.paneedah.mwc.MWC;
import com.cheba.combat.tracking.message.TrackingMarkerMessage;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class ViewerTrackingManager {

    private static final int MAX_STATES = 64;
    private static final long MAX_AGE_NANOS = 2_000_000_000L;

    private static final AtomicLong NEXT_EPOCH = new AtomicLong(1L);

    private static final Map<TrackingKey, ViewerTrackingState> TRACKING =
            new HashMap<>();

    private ViewerTrackingManager() {
    }

    public static void startTracking(
            EntityPlayerMP observer,
            EntityPlayerMP target
    ) {
        if (!isValidPair(observer, target)) {
            return;
        }

        TrackingKey key = new TrackingKey(
                observer.getUniqueID(),
                target.getUniqueID()
        );

        if (TRACKING.containsKey(key)) {
            return;
        }

        ViewerTrackingState state = new ViewerTrackingState(
                observer.getUniqueID(),
                target.getUniqueID(),
                target.getEntityId(),
                target.dimension,
                NEXT_EPOCH.getAndIncrement()
        );

        TRACKING.put(key, state);

        state.record(target, System.nanoTime());

        sendMarker(observer, state);

        CombatLog.debug(
                "ViewerTrackingManager",
                "startTracking",
                "Observer=" + observer.getUniqueID()
                        + " | Target=" + target.getUniqueID()
                        + " | EntityId=" + target.getEntityId()
                        + " | Epoch=" + state.getEpoch()
        );
    }

    public static void recordMovement(
            EntityPlayerMP observer,
            EntityPlayerMP target
    ) {
        if (!isValidPair(observer, target)) {
            return;
        }

        TrackingKey key = new TrackingKey(
                observer.getUniqueID(),
                target.getUniqueID()
        );

        ViewerTrackingState state = TRACKING.get(key);

        if (state == null
                || state.getEntityId() != target.getEntityId()
                || state.getDimension() != target.dimension) {
            stopTracking(observer.getUniqueID(), target.getUniqueID());
            startTracking(observer, target);
            return;
        }

        state.record(target, System.nanoTime());

        sendMarker(observer, state);
    }

    public static void stopTracking(
            UUID observerId,
            UUID targetId
    ) {
        if (observerId == null || targetId == null) {
            return;
        }

        ViewerTrackingState removed = TRACKING.remove(
                new TrackingKey(observerId, targetId)
        );

        if (removed != null) {
            CombatLog.debug(
                    "ViewerTrackingManager",
                    "stopTracking",
                    "Observer=" + observerId
                            + " | Target=" + targetId
                            + " | Epoch=" + removed.getEpoch()
            );
        }
    }

    public static void stopTrackingTarget(UUID targetId) {
        if (targetId == null) {
            return;
        }

        Iterator<Map.Entry<TrackingKey, ViewerTrackingState>> iterator =
                TRACKING.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<TrackingKey, ViewerTrackingState> entry =
                    iterator.next();

            if (targetId.equals(entry.getKey().targetId)) {
                iterator.remove();
            }
        }
    }

    public static void stopTrackingObserver(UUID observerId) {
        if (observerId == null) {
            return;
        }

        Iterator<Map.Entry<TrackingKey, ViewerTrackingState>> iterator =
                TRACKING.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<TrackingKey, ViewerTrackingState> entry =
                    iterator.next();

            if (observerId.equals(entry.getKey().observerId)) {
                iterator.remove();
            }
        }
    }

    public static ViewerTrackingState getState(
            UUID observerId,
            UUID targetId
    ) {
        if (observerId == null || targetId == null) {
            return null;
        }

        return TRACKING.get(new TrackingKey(observerId, targetId));
    }

    private static void sendMarker(
            EntityPlayerMP observer,
            ViewerTrackingState state
    ) {
        MWC.CHANNEL.sendTo(
                new TrackingMarkerMessage(
                        state.getEntityId(),
                        state.getEpoch(),
                        state.getSequence()
                ),
                observer
        );
    }

    private static boolean isValidPair(
            EntityPlayerMP observer,
            EntityPlayerMP target
    ) {
        return observer != null
                && target != null
                && observer != target
                && !observer.isDead
                && !target.isDead
                && observer.world == target.world
                && !observer.world.isRemote;
    }

    public static final class ViewerTrackingState {

        private final UUID observerId;
        private final UUID targetId;
        private final int entityId;
        private final int dimension;
        private final long epoch;

        private final Deque<SentState> sentStates = new ArrayDeque<>();

        private int sequence;

        private ViewerTrackingState(
                UUID observerId,
                UUID targetId,
                int entityId,
                int dimension,
                long epoch
        ) {
            this.observerId = observerId;
            this.targetId = targetId;
            this.entityId = entityId;
            this.dimension = dimension;
            this.epoch = epoch;
        }

        private void record(
                EntityPlayerMP target,
                long timestampNanos
        ) {
            if (sequence == Integer.MAX_VALUE) {
                throw new IllegalStateException(
                        "Tracking sequence exhausted"
                );
            }

            if (!sentStates.isEmpty()) {
                sequence++;
            }

            sentStates.addLast(new SentState(
                    sequence,
                    timestampNanos,
                    target.posX,
                    target.posY,
                    target.posZ
            ));

            trim(timestampNanos);
        }

        private void trim(long now) {
            while (!sentStates.isEmpty()) {
                SentState oldest = sentStates.peekFirst();

                boolean tooOld =
                        now - oldest.timestampNanos > MAX_AGE_NANOS;

                boolean tooMany = sentStates.size() > MAX_STATES;

                if (!tooOld && !tooMany) {
                    break;
                }

                sentStates.removeFirst();
            }
        }

        public UUID getObserverId() {
            return observerId;
        }

        public UUID getTargetId() {
            return targetId;
        }

        public int getEntityId() {
            return entityId;
        }

        public int getDimension() {
            return dimension;
        }

        public long getEpoch() {
            return epoch;
        }

        public int getSequence() {
            return sequence;
        }

        public Deque<SentState> getSentStates() {
            return new ArrayDeque<>(sentStates);
        }
    }

    public static final class SentState {

        private final int sequence;
        private final long timestampNanos;

        private final double x;
        private final double y;
        private final double z;

        private SentState(
                int sequence,
                long timestampNanos,
                double x,
                double y,
                double z
        ) {
            this.sequence = sequence;
            this.timestampNanos = timestampNanos;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getSequence() {
            return sequence;
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
    }

    private static final class TrackingKey {

        private final UUID observerId;
        private final UUID targetId;

        private TrackingKey(UUID observerId, UUID targetId) {
            this.observerId = observerId;
            this.targetId = targetId;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof TrackingKey)) {
                return false;
            }

            TrackingKey other = (TrackingKey) object;

            return observerId.equals(other.observerId)
                    && targetId.equals(other.targetId);
        }

        @Override
        public int hashCode() {
            return 31 * observerId.hashCode() + targetId.hashCode();
        }
    }
}
