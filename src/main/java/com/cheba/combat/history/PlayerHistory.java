package com.cheba.combat.history;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public final class PlayerHistory {

    private static final int MAX_SNAPSHOTS = 64;
    private static final long MAX_AGE_NANOS = 2_000_000_000L;

    private final Deque<PlayerSnapshot> snapshots = new ArrayDeque<>();

    public void add(PlayerSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        PlayerSnapshot latest = snapshots.peekLast();

        if (latest != null
                && snapshot.getTimestampNanos() <= latest.getTimestampNanos()) {
            return;
        }

        snapshots.addLast(snapshot);
        trim(snapshot.getTimestampNanos());
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

    public PlayerSnapshot getLatest() {
        return snapshots.peekLast();
    }

    public List<PlayerSnapshot> getSnapshots() {
        return Collections.unmodifiableList(
                new ArrayList<>(snapshots)
        );
    }

    public int size() {
        return snapshots.size();
    }

    public void clear() {
        snapshots.clear();
    }
}
