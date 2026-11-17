package com.cheba.combat.client;

import java.util.HashMap;
import java.util.Map;

public final class ClientTrackingSequenceManager {

    private static final Map<Integer, TrackingSequence> SEQUENCES =
            new HashMap<>();

    private ClientTrackingSequenceManager() {
    }

    public static void accept(
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
        }

        SEQUENCES.put(
                entityId,
                new TrackingSequence(epoch, sequence)
        );
    }

    public static TrackingSequence get(int entityId) {
        return SEQUENCES.get(entityId);
    }

    public static void remove(int entityId) {
        SEQUENCES.remove(entityId);
    }

    public static void clear() {
        SEQUENCES.clear();
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
