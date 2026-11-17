package com.cheba.combat.client;

import java.util.HashMap;
import java.util.Map;

public final class ClientTrackingSequenceManager {

    private static final Map<Integer, TrackingSequence> SEQUENCES =
            new HashMap<>();

    private ClientTrackingSequenceManager() {
    }

    public static synchronized void accept(
            int entityId,
            long epoch,
            int sequence
    ) {
        if (entityId < 0 || epoch <= 0L || sequence < 0) {
            return;
        }

        TrackingSequence current = SEQUENCES.get(entityId);

        if (current != null) {
            if (current.epoch > epoch) {
                return;
            }

            if (current.epoch == epoch
                    && sequence <= current.sequence) {
                return;
            }

            if (current.epoch != epoch) {
                ClientRenderedPlayerHistory.remove(entityId);
            }
        }

        SEQUENCES.put(
                entityId,
                new TrackingSequence(epoch, sequence)
        );
    }

    public static synchronized TrackingSequence get(int entityId) {
        return SEQUENCES.get(entityId);
    }

    public static synchronized void remove(int entityId) {
        SEQUENCES.remove(entityId);
        ClientRenderedPlayerHistory.remove(entityId);
    }

    public static synchronized void clear() {
        SEQUENCES.clear();
        ClientRenderedPlayerHistory.clear();
    }

    public static final class TrackingSequence {

        private final long epoch;
        private final int sequence;

        private TrackingSequence(long epoch, int sequence) {
            this.epoch = epoch;
            this.sequence = sequence;
        }

        public long getEpoch() {
            return epoch;
        }

        public int getSequence() {
            return sequence;
        }
    }
}
