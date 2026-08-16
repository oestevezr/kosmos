package com.kosmos.atlas.sim.util;

import java.util.Arrays;

/**
 * Open-addressing {@code long -> int} hash map with linear probing.
 *
 * <p>Used on the hot chunk-lookup path ({@code chunkKey -> slot index}) instead of
 * {@code HashMap<Long, Integer>} to avoid boxing every key and value (spec §42.2, §42.4:
 * "integer IDs instead of object references", "avoid boxing" in hot loops). Backed by two
 * primitive arrays; grows by doubling and rehashing, which never happens in steady state once
 * the map has warmed up to its working set size.
 */
public final class LongIntHashMap {

    private static final long EMPTY_KEY = Long.MIN_VALUE;
    private static final int NOT_FOUND = -1;

    private long[] keys;
    private int[] values;
    private int size;
    private int threshold;
    private final float loadFactor;

    public LongIntHashMap() {
        this(16, 0.6f);
    }

    public LongIntHashMap(int initialCapacity, float loadFactor) {
        int capacity = Integer.highestOneBit(Math.max(4, initialCapacity - 1) * 2);
        this.loadFactor = loadFactor;
        this.keys = new long[capacity];
        this.values = new int[capacity];
        Arrays.fill(this.keys, EMPTY_KEY);
        this.threshold = (int) (capacity * loadFactor);
    }

    public int size() {
        return size;
    }

    public int get(long key, int defaultValue) {
        int idx = indexOf(key);
        return idx < 0 ? defaultValue : values[idx];
    }

    public boolean containsKey(long key) {
        return indexOf(key) >= 0;
    }

    /** Returns the previous value associated with {@code key}, or {@link #NOT_FOUND} if absent. */
    public int put(long key, int value) {
        if (size >= threshold) {
            grow();
        }
        int mask = keys.length - 1;
        int i = spread(key) & mask;
        while (keys[i] != EMPTY_KEY) {
            if (keys[i] == key) {
                int prev = values[i];
                values[i] = value;
                return prev;
            }
            i = (i + 1) & mask;
        }
        keys[i] = key;
        values[i] = value;
        size++;
        return NOT_FOUND;
    }

    /** Removes {@code key} using backward-shift deletion so linear probing stays correct. */
    public int remove(long key) {
        int mask = keys.length - 1;
        int i = spread(key) & mask;
        while (keys[i] != EMPTY_KEY) {
            if (keys[i] == key) {
                int removed = values[i];
                int hole = i;
                int j = (i + 1) & mask;
                while (keys[j] != EMPTY_KEY) {
                    int idealJ = spread(keys[j]) & mask;
                    // Move entry j into the hole iff the hole lies on j's probe path from its
                    // ideal slot, i.e. the circular distance idealJ->hole is <= idealJ->j.
                    int distToHole = (hole - idealJ) & mask;
                    int distToJ = (j - idealJ) & mask;
                    if (distToHole <= distToJ) {
                        keys[hole] = keys[j];
                        values[hole] = values[j];
                        hole = j;
                    }
                    j = (j + 1) & mask;
                }
                keys[hole] = EMPTY_KEY;
                size--;
                return removed;
            }
            i = (i + 1) & mask;
        }
        return NOT_FOUND;
    }

    public void clear() {
        Arrays.fill(keys, EMPTY_KEY);
        size = 0;
    }

    private int indexOf(long key) {
        int mask = keys.length - 1;
        int i = spread(key) & mask;
        while (keys[i] != EMPTY_KEY) {
            if (keys[i] == key) {
                return i;
            }
            i = (i + 1) & mask;
        }
        return -1;
    }

    private void grow() {
        long[] oldKeys = keys;
        int[] oldValues = values;
        int newCapacity = oldKeys.length * 2;
        keys = new long[newCapacity];
        values = new int[newCapacity];
        Arrays.fill(keys, EMPTY_KEY);
        threshold = (int) (newCapacity * loadFactor);
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY_KEY) {
                put(oldKeys[i], oldValues[i]);
            }
        }
    }

    private static int spread(long key) {
        long h = key ^ (key >>> 32);
        h *= 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 29));
    }
}
