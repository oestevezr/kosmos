package com.kosmos.atlas.sim.transport;

import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkTest {

    private static void makeLand(Chunk chunk) {
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
    }

    private static boolean hasAccess(Chunk chunk, int lx, int ly) {
        int idx = Chunk.tileIndex(lx, ly);
        return (chunk.serviceFlags[idx] & WorldConstants.SERVICE_ROAD_ACCESS) != 0;
    }

    @Test
    void tileOnRoadAndOrthogonalNeighborsGetAccessButDiagonalDoesNot() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        makeLand(chunk);
        chunk.roadType[Chunk.tileIndex(10, 10)] = WorldConstants.ROAD_DIRT;
        store.put(chunk);

        new RoadNetwork().update(store);

        assertTrue(hasAccess(chunk, 10, 10), "the road tile itself has access");
        assertTrue(hasAccess(chunk, 11, 10));
        assertTrue(hasAccess(chunk, 9, 10));
        assertTrue(hasAccess(chunk, 10, 11));
        assertTrue(hasAccess(chunk, 10, 9));
        assertFalse(hasAccess(chunk, 11, 11), "diagonal neighbors are not orthogonally adjacent");
        assertFalse(hasAccess(chunk, 0, 0), "tiles far from the road have no access");
    }

    @Test
    void accessPropagatesAcrossChunkBoundary() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunkA = new Chunk();
        chunkA.reset(0, 0);
        makeLand(chunkA);
        // Road on the last column of chunk (0,0): local x=31.
        chunkA.roadType[Chunk.tileIndex(31, 5)] = WorldConstants.ROAD_DIRT;
        store.put(chunkA);

        Chunk chunkB = new Chunk();
        chunkB.reset(1, 0);
        makeLand(chunkB);
        store.put(chunkB);

        new RoadNetwork().update(store);

        // World tile (32,5) is chunk (1,0) local (0,5) — directly east of the road at world (31,5).
        assertTrue(hasAccess(chunkB, 0, 5), "road access must propagate across the chunk boundary");
    }

    @Test
    void removingTheRoadRemovesAccessOnNextUpdate() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        makeLand(chunk);
        chunk.roadType[Chunk.tileIndex(10, 10)] = WorldConstants.ROAD_DIRT;
        store.put(chunk);

        RoadNetwork roadNetwork = new RoadNetwork();
        roadNetwork.update(store);
        assertTrue(hasAccess(chunk, 11, 10));

        chunk.roadType[Chunk.tileIndex(10, 10)] = WorldConstants.ROAD_NONE;
        chunk.markDirty(); // bumps version so RoadNetwork's dirty-tracking reprocesses this chunk
        roadNetwork.update(store);
        assertFalse(hasAccess(chunk, 11, 10));
    }
}
