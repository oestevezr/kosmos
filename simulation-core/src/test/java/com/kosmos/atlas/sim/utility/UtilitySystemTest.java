package com.kosmos.atlas.sim.utility;

import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilitySystemTest {

    private static boolean powered(Chunk chunk, int lx, int ly) {
        return (chunk.serviceFlags[Chunk.tileIndex(lx, ly)] & WorldConstants.SERVICE_POWERED) != 0;
    }

    private static boolean watered(Chunk chunk, int lx, int ly) {
        return (chunk.serviceFlags[Chunk.tileIndex(lx, ly)] & WorldConstants.SERVICE_WATERED) != 0;
    }

    @Test
    void powerSpreadsFromPlantThroughLandAndStopsAtWater() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        // A water barrier along column x=15 blocks flood-fill from crossing east of it.
        for (int y = 0; y < WorldConstants.CHUNK_SIZE; y++) {
            chunk.terrainType[Chunk.tileIndex(15, y)] = WorldConstants.TERRAIN_DEEP_WATER;
        }
        store.put(chunk);

        BuildingRegistry buildings = new BuildingRegistry();
        int plantId = buildings.create(BuildingType.POWER_PLANT, 5, 5, 1);
        chunk.buildingId[Chunk.tileIndex(5, 5)] = plantId;

        new UtilitySystem().update(store, buildings);

        assertTrue(powered(chunk, 5, 5), "the source tile itself is powered");
        assertTrue(powered(chunk, 8, 5), "nearby tiles on the same landmass are powered");
        assertFalse(powered(chunk, 20, 5), "tiles beyond the water barrier are not reachable");
        assertFalse(watered(chunk, 5, 5), "a power plant does not provide water");
    }

    @Test
    void coverageStopsBeyondMaxRange() {
        ChunkStore store = new ChunkStore(9);
        for (int cx = -2; cx <= 2; cx++) {
            Chunk chunk = new Chunk();
            chunk.reset(cx, 0);
            java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
            store.put(chunk);
        }
        BuildingRegistry buildings = new BuildingRegistry();
        int towerId = buildings.create(BuildingType.WATER_TOWER, 0, 0, 1);
        store.get(0, 0).buildingId[Chunk.tileIndex(0, 0)] = towerId;

        new UtilitySystem().update(store, buildings);

        // 90 tiles east of the source exceeds the 48-tile flood-fill cap.
        Chunk farChunk = store.get(2, 0);
        assertFalse(watered(farChunk, 26, 0), "coverage must not extend past the service radius");
    }

    @Test
    void demolishingTheSourceRemovesCoverageOnNextUpdate() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);

        BuildingRegistry buildings = new BuildingRegistry();
        int plantId = buildings.create(BuildingType.POWER_PLANT, 5, 5, 1);
        chunk.buildingId[Chunk.tileIndex(5, 5)] = plantId;

        UtilitySystem utilitySystem = new UtilitySystem();
        utilitySystem.update(store, buildings);
        assertTrue(powered(chunk, 5, 5));

        buildings.demolish(plantId);
        chunk.buildingId[Chunk.tileIndex(5, 5)] = WorldConstants.NO_BUILDING;
        utilitySystem.update(store, buildings);
        assertFalse(powered(chunk, 5, 5));
    }
}
