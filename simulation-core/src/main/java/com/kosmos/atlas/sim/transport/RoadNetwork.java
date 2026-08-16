package com.kosmos.atlas.sim.transport;

import com.kosmos.atlas.sim.util.LongIntHashMap;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkKey;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import com.kosmos.atlas.sim.world.WorldTileAccess;

/**
 * Maintains the {@link WorldConstants#SERVICE_ROAD_ACCESS} bit for every loaded tile: a tile has
 * road access if any of its four orthogonal neighbors is a road tile (spec §9: population only
 * appears once "access" exists; spec §23: transport is one of the inputs to city growth).
 *
 * <p>Fase 2 scope is deliberately local — this is direct-adjacency access, not the regional
 * routing graph described in spec §13.2 ({@code Node}/{@code Edge} with capacity, travel time,
 * congestion). That graph belongs to the freight/regional-transport phase (spec §13, §14); Fase 2
 * only needs "is this zoned lot reachable from a road at all" to decide whether it can grow.
 *
 * <p>Recomputed access flags are <strong>derived state</strong>, not authoritative player data:
 * updating them does not call {@link Chunk#markDirty()} or bump the chunk's render version. If it
 * did, this system would perpetually re-mark every chunk it just processed as needing
 * re-processing — a version bump would immediately invalidate the dirty-tracking cache below,
 * and would also force the render cache to rebuild chunks whose visuals never changed (spec §41:
 * "optimize work avoided").
 */
public final class RoadNetwork {

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};

    /** Chunk key -> version last processed, so an unchanged chunk is skipped entirely (spec §41 dirty flags). */
    private final LongIntHashMap lastProcessedVersion = new LongIntHashMap();

    public void update(ChunkStore store) {
        store.forEach(chunk -> {
            long key = ChunkKey.pack(chunk.chunkX(), chunk.chunkY());
            if (lastProcessedVersion.get(key, Integer.MIN_VALUE) == chunk.version()) {
                return; // no player edits since last pass — access can't have changed
            }
            recomputeChunkAccess(store, chunk);
            lastProcessedVersion.put(key, chunk.version());
        });
    }

    private void recomputeChunkAccess(ChunkStore store, Chunk chunk) {
        int baseX = chunk.chunkX() * WorldConstants.CHUNK_SIZE;
        int baseY = chunk.chunkY() * WorldConstants.CHUNK_SIZE;
        for (int ly = 0; ly < WorldConstants.CHUNK_SIZE; ly++) {
            for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
                int idx = Chunk.tileIndex(lx, ly);
                int wx = baseX + lx;
                int wy = baseY + ly;
                boolean access = chunk.roadType[idx] != WorldConstants.ROAD_NONE || hasAdjacentRoad(store, wx, wy);
                setBit(chunk, idx, WorldConstants.SERVICE_ROAD_ACCESS, access);
            }
        }
    }

    private boolean hasAdjacentRoad(ChunkStore store, int wx, int wy) {
        for (int d = 0; d < 4; d++) {
            Chunk neighborChunk = WorldTileAccess.chunkAt(store, wx + DX[d], wy + DY[d]);
            if (neighborChunk == null) {
                continue; // unloaded neighbor: treat as no road, self-corrects once it streams in
            }
            int neighborIdx = WorldTileAccess.localIndexAt(wx + DX[d], wy + DY[d]);
            if (neighborChunk.roadType[neighborIdx] != WorldConstants.ROAD_NONE) {
                return true;
            }
        }
        return false;
    }

    private static void setBit(Chunk chunk, int idx, int bit, boolean value) {
        int flags = chunk.serviceFlags[idx] & 0xFF;
        int updated = value ? (flags | bit) : (flags & ~bit);
        chunk.serviceFlags[idx] = (byte) updated;
    }
}
