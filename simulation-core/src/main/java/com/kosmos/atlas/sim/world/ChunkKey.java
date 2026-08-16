package com.kosmos.atlas.sim.world;

/**
 * Packs a {@code (chunkX, chunkY)} coordinate pair into a single {@code long} so it can be used
 * as a key in {@link com.kosmos.atlas.sim.util.LongIntHashMap} without allocating a wrapper
 * object per lookup (spec §42.2).
 */
public final class ChunkKey {

    private ChunkKey() {
    }

    public static long pack(int chunkX, int chunkY) {
        return (((long) chunkX) << 32) | (chunkY & 0xFFFFFFFFL);
    }

    public static int unpackX(long key) {
        return (int) (key >> 32);
    }

    public static int unpackY(long key) {
        return (int) key;
    }
}
