package com.kosmos.atlas.sim.utility;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        new UtilitySystem().update(store, buildings, new CityRegistry());

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

        new UtilitySystem().update(store, buildings, new CityRegistry());

        // 90 tiles east of the source exceeds a small Water Tower's 30-tile flood-fill radius.
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
        CityRegistry cities = new CityRegistry();
        utilitySystem.update(store, buildings, cities);
        assertTrue(powered(chunk, 5, 5));

        buildings.demolish(plantId);
        chunk.buildingId[Chunk.tileIndex(5, 5)] = WorldConstants.NO_BUILDING;
        utilitySystem.update(store, buildings, cities);
        assertFalse(powered(chunk, 5, 5));
    }

    @Test
    void coverageRatioIsFullWhenCapacityMeetsOrExceedsDemand() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);

        CityRegistry cities = new CityRegistry();
        int cityId = cities.create("Testville", 5, 5, 0);
        BuildingRegistry buildings = new BuildingRegistry();
        int home = buildings.create(BuildingType.RESIDENTIAL, 5, 5, cityId);
        buildings.setPopulation(home, 100); // well under a small plant's 400 capacity
        int plantId = buildings.create(BuildingType.POWER_PLANT, 6, 5, cityId);
        chunk.buildingId[Chunk.tileIndex(6, 5)] = plantId;
        int towerId = buildings.create(BuildingType.WATER_TOWER, 6, 6, cityId);
        chunk.buildingId[Chunk.tileIndex(6, 6)] = towerId;

        UtilitySystem system = new UtilitySystem();
        system.update(store, buildings, cities);

        assertEquals(1.0, system.powerCoverageRatio(cityId), 1e-9);
        assertEquals(1.0, system.waterCoverageRatio(cityId), 1e-9);
    }

    @Test
    void coverageRatioDropsBelowOneWhenDemandOutgrowsInstalledCapacity() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);

        CityRegistry cities = new CityRegistry();
        int cityId = cities.create("Testville", 5, 5, 0);
        BuildingRegistry buildings = new BuildingRegistry();
        int home = buildings.create(BuildingType.RESIDENTIAL, 5, 5, cityId);
        buildings.setPopulation(home, 5000); // far past a single small plant's 400 capacity
        int plantId = buildings.create(BuildingType.POWER_PLANT, 6, 5, cityId);
        chunk.buildingId[Chunk.tileIndex(6, 5)] = plantId;

        UtilitySystem system = new UtilitySystem();
        system.update(store, buildings, cities);

        assertTrue(system.powerCoverageRatio(cityId) < 1.0, "one small plant shouldn't cover a 5000-population city");
    }

    @Test
    void freshlyFoundedCityWithNoPopulationYetHasFullRatioByDefault() {
        CityRegistry cities = new CityRegistry();
        int cityId = cities.create("Testville", 5, 5, 0);

        UtilitySystem system = new UtilitySystem();
        system.update(new ChunkStore(1), new BuildingRegistry(), cities);

        assertEquals(1.0, system.powerCoverageRatio(cityId), 1e-9, "no demand yet means nothing to throttle");
        assertEquals(1.0, system.waterCoverageRatio(cityId), 1e-9);
    }
}
