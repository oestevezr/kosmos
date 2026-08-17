package com.kosmos.atlas.sim.population;

import com.kosmos.atlas.sim.Difficulty;
import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.transport.RoadNetwork;
import com.kosmos.atlas.sim.utility.UtilitySystem;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the settlement + growth loop end to end at the system level (spec §9, §23), using the
 * same RoadNetwork/UtilitySystem a real WorldManager would run before PopulationSystem each tick.
 */
class PopulationSystemTest {

    private static CityRegistry oneCity() {
        CityRegistry cities = new CityRegistry();
        cities.create("Testville", 10, 10, 0);
        return cities;
    }

    private ChunkStore buildServicedChunk() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        // Road spine down column x=10 so every zoned tile beside it has road access.
        for (int y = 0; y < WorldConstants.CHUNK_SIZE; y++) {
            chunk.roadType[Chunk.tileIndex(10, y)] = WorldConstants.ROAD_DIRT;
        }
        store.put(chunk);
        return store;
    }

    private void refreshServices(ChunkStore store, BuildingRegistry buildings, CityRegistry cities, UtilitySystem utility) {
        new RoadNetwork().update(store);
        utility.update(store, buildings, cities);
    }

    @Test
    void unservicedZonedTileNeverSettles() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        chunk.zoneType[Chunk.tileIndex(5, 5)] = WorldConstants.ZONE_RESIDENTIAL;
        store.put(chunk);

        CityRegistry cities = oneCity();
        BuildingRegistry buildings = new BuildingRegistry();
        UtilitySystem utility = new UtilitySystem();
        PopulationSystem system = new PopulationSystem();
        for (int i = 0; i < 5; i++) {
            refreshServices(store, buildings, cities, utility);
            system.tick(store, buildings, cities, utility);
        }

        assertEquals(WorldConstants.NO_BUILDING, chunk.buildingId[Chunk.tileIndex(5, 5)]);
        assertEquals(0, system.totalResidentialPopulation(1));
    }

    @Test
    void servicedZonedTileSettlesOnceRoadPowerAndWaterExist() {
        ChunkStore store = buildServicedChunk();
        Chunk chunk = store.get(0, 0);
        chunk.zoneType[Chunk.tileIndex(11, 5)] = WorldConstants.ZONE_RESIDENTIAL;

        CityRegistry cities = oneCity();
        BuildingRegistry buildings = new BuildingRegistry();
        int plantId = buildings.create(BuildingType.POWER_PLANT, 10, 20, 1);
        chunk.buildingId[Chunk.tileIndex(10, 20)] = plantId;
        int towerId = buildings.create(BuildingType.WATER_TOWER, 10, 21, 1);
        chunk.buildingId[Chunk.tileIndex(10, 21)] = towerId;

        UtilitySystem utility = new UtilitySystem();
        PopulationSystem system = new PopulationSystem();
        refreshServices(store, buildings, cities, utility);
        system.tick(store, buildings, cities, utility);

        int builtId = chunk.buildingId[Chunk.tileIndex(11, 5)];
        assertTrue(builtId != WorldConstants.NO_BUILDING, "a serviced residential zone must settle");
        assertEquals(BuildingType.RESIDENTIAL, buildings.type(builtId));
        assertTrue(buildings.population(builtId) > 0, "a freshly settled building has non-zero seed population");
    }

    @Test
    void residentialAndWorkplaceGrowthArePulledByEachOther() {
        ChunkStore store = buildServicedChunk();
        Chunk chunk = store.get(0, 0);
        chunk.zoneType[Chunk.tileIndex(11, 5)] = WorldConstants.ZONE_RESIDENTIAL;
        chunk.zoneType[Chunk.tileIndex(9, 5)] = WorldConstants.ZONE_COMMERCIAL;

        CityRegistry cities = oneCity();
        BuildingRegistry buildings = new BuildingRegistry();
        int plantId = buildings.create(BuildingType.POWER_PLANT, 10, 20, 1);
        chunk.buildingId[Chunk.tileIndex(10, 20)] = plantId;
        int towerId = buildings.create(BuildingType.WATER_TOWER, 10, 21, 1);
        chunk.buildingId[Chunk.tileIndex(10, 21)] = towerId;

        UtilitySystem utility = new UtilitySystem();
        PopulationSystem system = new PopulationSystem();
        for (int i = 0; i < 20; i++) {
            refreshServices(store, buildings, cities, utility);
            system.tick(store, buildings, cities, utility);
        }

        assertTrue(system.totalResidentialPopulation(1) > 6, "population should grow past its seed value given jobs demand");
        assertTrue(system.totalCommercialJobs(1) > 6, "jobs should grow past seed value given workforce demand");
    }

    @Test
    void difficultyScalesGrowthRate() {
        long easyPopulation = populationAfterFiveTicks(Difficulty.EASY);
        long hardPopulation = populationAfterFiveTicks(Difficulty.HARD);

        assertTrue(easyPopulation > hardPopulation,
            "Easy's faster growth multiplier should out-pace Hard's slower one given identical conditions");
    }

    private long populationAfterFiveTicks(Difficulty difficulty) {
        ChunkStore store = buildServicedChunk();
        Chunk chunk = store.get(0, 0);
        chunk.zoneType[Chunk.tileIndex(11, 5)] = WorldConstants.ZONE_RESIDENTIAL;
        chunk.zoneType[Chunk.tileIndex(9, 5)] = WorldConstants.ZONE_COMMERCIAL;

        CityRegistry cities = new CityRegistry(difficulty);
        cities.create("Testville", 10, 10, 0);
        BuildingRegistry buildings = new BuildingRegistry();
        int plantId = buildings.create(BuildingType.POWER_PLANT, 10, 20, 1);
        chunk.buildingId[Chunk.tileIndex(10, 20)] = plantId;
        int towerId = buildings.create(BuildingType.WATER_TOWER, 10, 21, 1);
        chunk.buildingId[Chunk.tileIndex(10, 21)] = towerId;

        UtilitySystem utility = new UtilitySystem();
        PopulationSystem system = new PopulationSystem();
        for (int i = 0; i < 5; i++) {
            refreshServices(store, buildings, cities, utility);
            system.tick(store, buildings, cities, utility);
        }
        return system.totalResidentialPopulation(1);
    }

    @Test
    void demolishingUtilitiesStopsFurtherGrowthButDoesNotShrinkExistingBuildings() {
        ChunkStore store = buildServicedChunk();
        Chunk chunk = store.get(0, 0);
        chunk.zoneType[Chunk.tileIndex(11, 5)] = WorldConstants.ZONE_RESIDENTIAL;
        chunk.zoneType[Chunk.tileIndex(9, 5)] = WorldConstants.ZONE_COMMERCIAL;

        CityRegistry cities = oneCity();
        BuildingRegistry buildings = new BuildingRegistry();
        int plantId = buildings.create(BuildingType.POWER_PLANT, 10, 20, 1);
        chunk.buildingId[Chunk.tileIndex(10, 20)] = plantId;
        int towerId = buildings.create(BuildingType.WATER_TOWER, 10, 21, 1);
        chunk.buildingId[Chunk.tileIndex(10, 21)] = towerId;

        UtilitySystem utility = new UtilitySystem();
        PopulationSystem system = new PopulationSystem();
        refreshServices(store, buildings, cities, utility);
        system.tick(store, buildings, cities, utility); // settle both zones

        // Remove utilities entirely.
        buildings.demolish(plantId);
        buildings.demolish(towerId);
        chunk.buildingId[Chunk.tileIndex(10, 20)] = WorldConstants.NO_BUILDING;
        chunk.buildingId[Chunk.tileIndex(10, 21)] = WorldConstants.NO_BUILDING;

        long before = system.totalResidentialPopulation(1);
        for (int i = 0; i < 10; i++) {
            refreshServices(store, buildings, cities, utility);
            system.tick(store, buildings, cities, utility);
        }
        assertEquals(before, system.totalResidentialPopulation(1), "unserviced buildings must not keep growing");
    }

    @Test
    void satisfactionRisesToTheProsperityCeilingWhenAClinicIsInRangeButNotWithoutOne() {
        long satisfactionWithClinic = residentialSatisfactionAfterTicks(true, 10);
        long satisfactionWithoutClinic = residentialSatisfactionAfterTicks(false, 10);

        assertTrue(satisfactionWithClinic > satisfactionWithoutClinic,
            "prosperity coverage should raise the satisfaction ceiling above the base 60");
        assertEquals(60, satisfactionWithoutClinic, "no prosperity/luxury coverage caps satisfaction at the base ceiling");
    }

    @Test
    void growsFasterWithProsperityCoverageThanWithoutGivenIdenticalConditionsOtherwise() {
        long populationWithClinic = residentialPopulationAfterTicksWithOrWithoutClinic(true, 15);
        long populationWithoutClinic = residentialPopulationAfterTicksWithOrWithoutClinic(false, 15);

        assertTrue(populationWithClinic > populationWithoutClinic,
            "a higher satisfaction ceiling should translate into faster growth, not just a bigger displayed number");
    }

    private long residentialSatisfactionAfterTicks(boolean withClinic, int ticks) {
        ChunkStore store = buildServicedChunk();
        Chunk chunk = store.get(0, 0);
        chunk.zoneType[Chunk.tileIndex(11, 5)] = WorldConstants.ZONE_RESIDENTIAL;

        CityRegistry cities = oneCity();
        BuildingRegistry buildings = new BuildingRegistry();
        int plantId = buildings.create(BuildingType.POWER_PLANT, 10, 20, 1);
        chunk.buildingId[Chunk.tileIndex(10, 20)] = plantId;
        int towerId = buildings.create(BuildingType.WATER_TOWER, 10, 21, 1);
        chunk.buildingId[Chunk.tileIndex(10, 21)] = towerId;
        if (withClinic) {
            int clinicId = buildings.create(BuildingType.CLINIC, 10, 22, 1);
            chunk.buildingId[Chunk.tileIndex(10, 22)] = clinicId;
        }

        UtilitySystem utility = new UtilitySystem();
        PopulationSystem system = new PopulationSystem();
        int homeId = -1;
        for (int i = 0; i < ticks; i++) {
            refreshServices(store, buildings, cities, utility);
            system.tick(store, buildings, cities, utility);
            homeId = chunk.buildingId[Chunk.tileIndex(11, 5)];
        }
        return buildings.satisfactionPercent(homeId);
    }

    private long residentialPopulationAfterTicksWithOrWithoutClinic(boolean withClinic, int ticks) {
        ChunkStore store = buildServicedChunk();
        Chunk chunk = store.get(0, 0);
        chunk.zoneType[Chunk.tileIndex(11, 5)] = WorldConstants.ZONE_RESIDENTIAL;
        chunk.zoneType[Chunk.tileIndex(9, 5)] = WorldConstants.ZONE_COMMERCIAL;

        CityRegistry cities = oneCity();
        BuildingRegistry buildings = new BuildingRegistry();
        int plantId = buildings.create(BuildingType.POWER_PLANT, 10, 20, 1);
        chunk.buildingId[Chunk.tileIndex(10, 20)] = plantId;
        int towerId = buildings.create(BuildingType.WATER_TOWER, 10, 21, 1);
        chunk.buildingId[Chunk.tileIndex(10, 21)] = towerId;
        if (withClinic) {
            int clinicId = buildings.create(BuildingType.CLINIC, 10, 22, 1);
            chunk.buildingId[Chunk.tileIndex(10, 22)] = clinicId;
        }

        UtilitySystem utility = new UtilitySystem();
        PopulationSystem system = new PopulationSystem();
        for (int i = 0; i < ticks; i++) {
            refreshServices(store, buildings, cities, utility);
            system.tick(store, buildings, cities, utility);
        }
        return system.totalResidentialPopulation(1);
    }
}
