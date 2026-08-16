package com.kosmos.atlas.sim.utility;

import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.util.LongIntHashMap;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import com.kosmos.atlas.sim.world.WorldTileAccess;

/**
 * Computes electricity and water coverage as a graph-reachability problem (spec §24: "Utility
 * networks should use graph-based calculations where possible"): a multi-source breadth-first
 * flood fill from every active power plant / water tower, through orthogonally-connected land
 * tiles, capped at {@link #MAX_RANGE_TILES} hops so one plant doesn't light up an entire
 * continent.
 *
 * <p>Unlike {@link com.kosmos.atlas.sim.transport.RoadNetwork}, this cannot be made incremental
 * per-chunk with a simple version check — adding one new power plant can relight tiles anywhere
 * within range, in chunks whose own content never changed. Fase 2 accepts a full recompute of the
 * loaded area at a low cadence (see {@code WorldManager}'s scheduler registration) rather than
 * building a full incremental dependency graph; this is a documented candidate for optimization
 * once profiling on a real city shows it matters (spec §49: "based on profiles, not intuition").
 *
 * <p>Like {@link com.kosmos.atlas.sim.transport.RoadNetwork}, coverage flags are derived state:
 * recomputing them never marks a chunk dirty or bumps its render version.
 *
 * <p>The BFS frontier and visited-set are {@link LongQueue} and {@link LongIntHashMap} — the same
 * boxing-free primitive tools {@code ChunkStore} already uses for its own key index — reused as
 * instance fields across calls rather than a fresh {@code ArrayDeque<Long>}/{@code HashMap<Long,
 * Integer>} every recompute (spec §42.4). They only grow (never shrink), so allocation happens a
 * handful of times while the city's coverage area is still small and then never again.
 */
public final class UtilitySystem {

    private static final int MAX_RANGE_TILES = 48;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};
    private static final int NOT_VISITED = Integer.MIN_VALUE;

    private final LongQueue frontier = new LongQueue(1024);
    private final LongIntHashMap depthOf = new LongIntHashMap(2048, 0.6f);

    public void update(ChunkStore store, BuildingRegistry buildings) {
        clearBits(store, WorldConstants.SERVICE_POWERED | WorldConstants.SERVICE_WATERED);
        floodFillFromSources(store, buildings, BuildingType.POWER_PLANT, WorldConstants.SERVICE_POWERED);
        floodFillFromSources(store, buildings, BuildingType.WATER_TOWER, WorldConstants.SERVICE_WATERED);
    }

    private void clearBits(ChunkStore store, int bits) {
        store.forEach(chunk -> {
            byte[] flags = chunk.serviceFlags;
            for (int i = 0; i < flags.length; i++) {
                flags[i] = (byte) ((flags[i] & 0xFF) & ~bits);
            }
        });
    }

    private void floodFillFromSources(ChunkStore store, BuildingRegistry buildings, byte sourceType, int serviceBit) {
        frontier.clear();
        depthOf.clear();

        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id) || buildings.type(id) != sourceType) {
                continue;
            }
            long key = packTile(buildings.tileX(id), buildings.tileY(id));
            if (depthOf.get(key, NOT_VISITED) == NOT_VISITED) {
                depthOf.put(key, 0);
                frontier.offer(key);
            }
        }

        while (!frontier.isEmpty()) {
            long key = frontier.poll();
            int wx = unpackX(key);
            int wy = unpackY(key);
            int depth = depthOf.get(key, 0);

            Chunk chunk = WorldTileAccess.chunkAt(store, wx, wy);
            if (chunk == null) {
                continue; // fell outside the currently-loaded window
            }
            int idx = WorldTileAccess.localIndexAt(wx, wy);
            if (!isLand(chunk.terrainType[idx])) {
                continue; // utilities don't spread across open water in Fase 2
            }
            chunk.serviceFlags[idx] = (byte) ((chunk.serviceFlags[idx] & 0xFF) | serviceBit);

            if (depth >= MAX_RANGE_TILES) {
                continue;
            }
            for (int d = 0; d < 4; d++) {
                int nx = wx + DX[d];
                int ny = wy + DY[d];
                long neighborKey = packTile(nx, ny);
                if (depthOf.get(neighborKey, NOT_VISITED) == NOT_VISITED) {
                    depthOf.put(neighborKey, depth + 1);
                    frontier.offer(neighborKey);
                }
            }
        }
    }

    private static boolean isLand(byte terrainType) {
        return terrainType != WorldConstants.TERRAIN_DEEP_WATER && terrainType != WorldConstants.TERRAIN_SHALLOW_WATER;
    }

    private static long packTile(int x, int y) {
        return (((long) x) << 32) | (y & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackY(long key) {
        return (int) key;
    }

    /**
     * Growable {@code long}-valued FIFO ring buffer. Purpose-built instead of reusing
     * {@code ArrayDeque<Long>} so the BFS frontier never boxes a tile key (spec §42.2).
     */
    private static final class LongQueue {
        private long[] buffer;
        private int head;
        private int tail;
        private int size;

        LongQueue(int initialCapacity) {
            buffer = new long[Integer.highestOneBit(Math.max(2, initialCapacity - 1) * 2)];
        }

        void offer(long value) {
            if (size == buffer.length) {
                grow();
            }
            buffer[tail] = value;
            tail = (tail + 1) & (buffer.length - 1);
            size++;
        }

        long poll() {
            long value = buffer[head];
            head = (head + 1) & (buffer.length - 1);
            size--;
            return value;
        }

        boolean isEmpty() {
            return size == 0;
        }

        void clear() {
            head = 0;
            tail = 0;
            size = 0;
        }

        private void grow() {
            long[] newBuffer = new long[buffer.length * 2];
            for (int i = 0; i < size; i++) {
                newBuffer[i] = buffer[(head + i) & (buffer.length - 1)];
            }
            buffer = newBuffer;
            head = 0;
            tail = size;
        }
    }
}
