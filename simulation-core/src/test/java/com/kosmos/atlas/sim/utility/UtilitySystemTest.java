package com.kosmos.atlas.sim.utility;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.economy.BuildingEconomics;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.BusRouteRegistry;
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

    @Test
    void healthcareCoverageSpreadsFromAClinicAndStopsBeyondItsRadius() {
        ChunkStore store = new ChunkStore(9);
        for (int cx = -2; cx <= 2; cx++) {
            Chunk chunk = new Chunk();
            chunk.reset(cx, 0);
            java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
            store.put(chunk);
        }
        BuildingRegistry buildings = new BuildingRegistry();
        int clinicId = buildings.create(BuildingType.CLINIC, 0, 0, 1);
        store.get(0, 0).buildingId[Chunk.tileIndex(0, 0)] = clinicId;

        new UtilitySystem().update(store, buildings, new CityRegistry());

        Chunk sourceChunk = store.get(0, 0);
        assertTrue((sourceChunk.serviceFlags[Chunk.tileIndex(0, 0)] & WorldConstants.SERVICE_HEALTHCARE) != 0,
            "the source tile itself is covered");

        // 90 tiles east exceeds a Clinic's 25-tile flood-fill radius.
        Chunk farChunk = store.get(2, 0);
        assertFalse((farChunk.serviceFlags[Chunk.tileIndex(26, 0)] & WorldConstants.SERVICE_HEALTHCARE) != 0,
            "coverage must not extend past the service radius");
    }

    @Test
    void policeCoverageSpreadsFromAPoliceOutpostAndStopsBeyondItsRadius() {
        ChunkStore store = new ChunkStore(9);
        for (int cx = -2; cx <= 2; cx++) {
            Chunk chunk = new Chunk();
            chunk.reset(cx, 0);
            java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
            store.put(chunk);
        }
        BuildingRegistry buildings = new BuildingRegistry();
        int outpostId = buildings.create(BuildingType.POLICE_OUTPOST, 0, 0, 1);
        store.get(0, 0).buildingId[Chunk.tileIndex(0, 0)] = outpostId;

        new UtilitySystem().update(store, buildings, new CityRegistry());

        Chunk sourceChunk = store.get(0, 0);
        assertTrue((sourceChunk.serviceFlags[Chunk.tileIndex(0, 0)] & WorldConstants.SERVICE_POLICE) != 0,
            "the source tile itself is covered");

        Chunk farChunk = store.get(2, 0);
        assertFalse((farChunk.serviceFlags[Chunk.tileIndex(26, 0)] & WorldConstants.SERVICE_POLICE) != 0,
            "coverage must not extend past the service radius");
    }

    @Test
    void centralBankAndCityHallAreNeverCoverageSources() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);

        BuildingRegistry buildings = new BuildingRegistry();
        int bankId = buildings.create(BuildingType.CENTRAL_BANK, 5, 5, 1);
        chunk.buildingId[Chunk.tileIndex(5, 5)] = bankId;
        int cityHallId = buildings.create(BuildingType.CITY_HALL, 6, 5, 1);
        chunk.buildingId[Chunk.tileIndex(6, 5)] = cityHallId;

        new UtilitySystem().update(store, buildings, new CityRegistry());

        assertEquals(0, chunk.serviceFlags[Chunk.tileIndex(5, 5)], "Central Bank sets no service bit");
        assertEquals(0, chunk.serviceFlags[Chunk.tileIndex(6, 5)], "City Hall sets no service bit");
    }

    @Test
    void steelMillPollutesWithinItsRadiusAndNotBeyondIt() {
        ChunkStore store = new ChunkStore(9);
        for (int cx = -2; cx <= 2; cx++) {
            Chunk chunk = new Chunk();
            chunk.reset(cx, 0);
            java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
            store.put(chunk);
        }
        BuildingRegistry buildings = new BuildingRegistry();
        int millId = buildings.create(BuildingType.STEEL_MILL, 0, 0, 1);
        store.get(0, 0).buildingId[Chunk.tileIndex(0, 0)] = millId;

        new UtilitySystem().update(store, buildings, new CityRegistry());

        Chunk sourceChunk = store.get(0, 0);
        assertTrue(sourceChunk.pollutionLevel[Chunk.tileIndex(0, 0)] > 0, "the source tile itself is polluted");

        // 90 tiles east exceeds a Steel Mill's 12-tile pollution radius.
        Chunk farChunk = store.get(2, 0);
        assertEquals(0, farChunk.pollutionLevel[Chunk.tileIndex(26, 0)], "pollution must not extend past its radius");
    }

    @Test
    void overlappingPollutionSourcesAccumulateAdditively() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);

        BuildingRegistry buildings = new BuildingRegistry();
        int mineId = buildings.create(BuildingType.MINE, 5, 5, 1);
        chunk.buildingId[Chunk.tileIndex(5, 5)] = mineId;
        int quarryId = buildings.create(BuildingType.QUARRY, 6, 5, 1);
        chunk.buildingId[Chunk.tileIndex(6, 5)] = quarryId;

        new UtilitySystem().update(store, buildings, new CityRegistry());

        int midpoint = chunk.pollutionLevel[Chunk.tileIndex(6, 5)];
        assertTrue(midpoint >= BuildingEconomics.pollutionIntensity(BuildingType.MINE) + BuildingEconomics.pollutionIntensity(BuildingType.QUARRY),
            "a tile in range of both sources should sum both intensities");
    }

    @Test
    void parkReducesPollutionFromANearbyPolluter() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);

        BuildingRegistry buildings = new BuildingRegistry();
        int industrialId = buildings.create(BuildingType.INDUSTRIAL, 5, 5, 1);
        chunk.buildingId[Chunk.tileIndex(5, 5)] = industrialId;

        UtilitySystem system = new UtilitySystem();
        system.update(store, buildings, new CityRegistry());
        int withoutPark = chunk.pollutionLevel[Chunk.tileIndex(5, 5)];

        int parkId = buildings.create(BuildingType.PARK, 6, 5, 1);
        chunk.buildingId[Chunk.tileIndex(6, 5)] = parkId;
        system.update(store, buildings, new CityRegistry());
        int withPark = chunk.pollutionLevel[Chunk.tileIndex(5, 5)];

        assertTrue(withPark < withoutPark, "a nearby park should lower pollution compared to no park at all");
    }

    @Test
    void hydroAndNuclearPowerPlantsDoNotPollute() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);

        BuildingRegistry buildings = new BuildingRegistry();
        int hydroId = buildings.create(BuildingType.POWER_PLANT_HYDRO, 5, 5, 1);
        chunk.buildingId[Chunk.tileIndex(5, 5)] = hydroId;

        new UtilitySystem().update(store, buildings, new CityRegistry());

        assertEquals(0, chunk.pollutionLevel[Chunk.tileIndex(5, 5)], "only the small tier-1 plant pollutes, not Hydro/Nuclear");
    }

    @Test
    void demolishingAPollutingSourceClearsItsPollutionOnNextUpdate() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);

        BuildingRegistry buildings = new BuildingRegistry();
        int incineratorId = buildings.create(BuildingType.INCINERATOR, 5, 5, 1);
        chunk.buildingId[Chunk.tileIndex(5, 5)] = incineratorId;

        UtilitySystem system = new UtilitySystem();
        system.update(store, buildings, new CityRegistry());
        assertTrue(chunk.pollutionLevel[Chunk.tileIndex(5, 5)] > 0);

        buildings.demolish(incineratorId);
        chunk.buildingId[Chunk.tileIndex(5, 5)] = WorldConstants.NO_BUILDING;
        system.update(store, buildings, new CityRegistry());
        assertEquals(0, chunk.pollutionLevel[Chunk.tileIndex(5, 5)]);
    }

    private static boolean transit(Chunk chunk, int lx, int ly) {
        return (chunk.serviceFlags[Chunk.tileIndex(lx, ly)] & WorldConstants.SERVICE_TRANSIT) != 0;
    }

    @Test
    void aBusStopWithNoRouteGivesNoTransitCoverage() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);

        BuildingRegistry buildings = new BuildingRegistry();
        int stopId = buildings.create(BuildingType.BUS_STOP, 5, 5, 1);
        chunk.buildingId[Chunk.tileIndex(5, 5)] = stopId;

        new UtilitySystem().update(store, buildings, new CityRegistry(), new BusRouteRegistry());

        assertFalse(transit(chunk, 5, 5), "an isolated bus stop with no active route must give no coverage");
    }

    @Test
    void aBusStopOnAnActiveRouteGivesTransitCoverage() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);

        BuildingRegistry buildings = new BuildingRegistry();
        int stopA = buildings.create(BuildingType.BUS_STOP, 5, 5, 1);
        chunk.buildingId[Chunk.tileIndex(5, 5)] = stopA;
        int stopB = buildings.create(BuildingType.BUS_STOP, 6, 5, 1);
        chunk.buildingId[Chunk.tileIndex(6, 5)] = stopB;

        BusRouteRegistry busRoutes = new BusRouteRegistry();
        busRoutes.create(1, 1, new int[] {stopA, stopB});

        new UtilitySystem().update(store, buildings, new CityRegistry(), busRoutes);

        assertTrue(transit(chunk, 5, 5), "a stop that's part of an active route must give coverage");
        assertTrue(transit(chunk, 6, 5));
    }

    @Test
    void losingItsOnlyRouteRemovesABusStopsTransitCoverageOnNextUpdate() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);

        BuildingRegistry buildings = new BuildingRegistry();
        int stopA = buildings.create(BuildingType.BUS_STOP, 5, 5, 1);
        chunk.buildingId[Chunk.tileIndex(5, 5)] = stopA;
        int stopB = buildings.create(BuildingType.BUS_STOP, 6, 5, 1);
        chunk.buildingId[Chunk.tileIndex(6, 5)] = stopB;

        BusRouteRegistry busRoutes = new BusRouteRegistry();
        int routeId = busRoutes.create(1, 1, new int[] {stopA, stopB});

        UtilitySystem system = new UtilitySystem();
        system.update(store, buildings, new CityRegistry(), busRoutes);
        assertTrue(transit(chunk, 5, 5));

        busRoutes.demolishRoute(routeId);
        system.update(store, buildings, new CityRegistry(), busRoutes);
        assertFalse(transit(chunk, 5, 5), "removing the route must clear coverage on the next update");
    }
}
