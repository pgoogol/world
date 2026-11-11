package com.pgoogol.npcworld.view;

import java.util.ArrayDeque;
import java.util.Deque;

/** Mean of the most recent values, for the life length chart. */
final class RollingMean {

    private final int size;
    private final Deque<Integer> values = new ArrayDeque<>();
    private long sum;

    RollingMean(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Size must be at least 1");
        }
        this.size = size;
    }

    /** Adds a value and returns the mean of the last {@code size} values. */
    double add(int value) {
        values.addLast(value);
        sum += value;
        if (values.size() > size) {
            sum -= values.removeFirst();
        }
        return (double) sum / values.size();
    }
}
