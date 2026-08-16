package com.kosmos.atlas.sim.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-checks {@link LongIntHashMap} (put/get/remove/grow) against {@code HashMap<Long,Integer>}
 * as an oracle, since a bug in {@link ChunkStore}'s key index would silently corrupt which
 * physical chunk a coordinate resolves to.
 */
class LongIntHashMapTest {

    @Test
    void matchesReferenceHashMapUnderRandomOperations() {
        LongIntHashMap map = new LongIntHashMap(4, 0.6f);
        HashMap<Long, Integer> oracle = new HashMap<>();
        Random rng = new Random(2024);

        for (int i = 0; i < 20_000; i++) {
            long key = rng.nextInt(500); // small key space forces collisions and overwrites
            int op = rng.nextInt(3);
            switch (op) {
                case 0 -> {
                    int value = rng.nextInt();
                    map.put(key, value);
                    oracle.put(key, value);
                }
                case 1 -> {
                    map.remove(key);
                    oracle.remove(key);
                }
                default -> {
                    Integer expected = oracle.get(key);
                    int actual = map.get(key, Integer.MIN_VALUE);
                    if (expected == null) {
                        assertEquals(Integer.MIN_VALUE, actual);
                    } else {
                        assertEquals(expected.intValue(), actual);
                    }
                }
            }
        }

        assertEquals(oracle.size(), map.size());
        for (Long key : oracle.keySet()) {
            assertTrue(map.containsKey(key));
            assertEquals(oracle.get(key).intValue(), map.get(key, Integer.MIN_VALUE));
        }
    }

    @Test
    void clearRemovesEverything() {
        LongIntHashMap map = new LongIntHashMap();
        map.put(1L, 10);
        map.put(2L, 20);
        map.clear();
        assertEquals(0, map.size());
        assertFalse(map.containsKey(1L));
    }
}
