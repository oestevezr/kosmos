package com.kosmos.atlas.sim.population;

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

    private void refreshServices(ChunkStore store, BuildingRegistry buildings) {
        new RoadNetwork().update(store);
        new UtilitySystem().update(store, buildings);
    }

    @Test
    void unservicedZonedTileNeverSettles() {
        ChunkStore store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        chunk.zoneType[Chunk.tileIndex(5, 5)] = WorldConstants.ZONE_RESIDENTIAL;
        store.put(chunk);

        BuildingRegistry buildings = new BuildingRegistry();
        PopulationSystem system = new PopulationSystem();
        for (int i = 0; i < 5; i++) {
            refreshServices(store, buildings);
            system.tick(store, buildings);
        }

        assertEquals(WorldConstants.NO_BUILDING, chunk.buildingId[Chunk.tileIndex(5, 5)]);
        assertEquals(0, system.totalResidentialPopulation());
    }

    @Test
    void servicedZonedTileSettlesOnceRoadPowerAndWaterExist() {
        ChunkStore store = buildServicedChunk();
        Chunk chunk = store.get(0, 0);
        chunk.zoneType[Chunk.tileIndex(11, 5)] = WorldConstants.ZONE_RESIDENTIAL;

        BuildingRegistry buildings = new BuildingRegistry();
        int plantId = buildings.create(BuildingType.POWER_PLANT, 10, 20);
        chunk.buildingId[Chunk.tileIndex(10, 20)] = plantId;
        int towerId = buildings.create(BuildingType.WATER_TOWER, 10, 21);
        chunk.buildingId[Chunk.tileIndex(10, 21)] = towerId;

        PopulationSystem system = new PopulationSystem();
        refreshServices(store, buildings);
        system.tick(store, buildings);

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

        BuildingRegistry buildings = new BuildingRegistry();
        int plantId = buildings.create(BuildingType.POWER_PLANT, 10, 20);
        chunk.buildingId[Chunk.tileIndex(10, 20)] = plantId;
        int towerId = buildings.create(BuildingType.WATER_TOWER, 10, 21);
        chunk.buildingId[Chunk.tileIndex(10, 21)] = towerId;

        PopulationSystem system = new PopulationSystem();
        for (int i = 0; i < 20; i++) {
            refreshServices(store, buildings);
            system.tick(store, buildings);
        }

        assertTrue(system.totalResidentialPopulation() > 6, "population should grow past its seed value given jobs demand");
        assertTrue(system.totalCommercialJobs() > 6, "jobs should grow past seed value given workforce demand");
    }

    @Test
    void demolishingUtilitiesStopsFurtherGrowthButDoesNotShrinkExistingBuildings() {
        ChunkStore store = buildServicedChunk();
        Chunk chunk = store.get(0, 0);
        chunk.zoneType[Chunk.tileIndex(11, 5)] = WorldConstants.ZONE_RESIDENTIAL;
        chunk.zoneType[Chunk.tileIndex(9, 5)] = WorldConstants.ZONE_COMMERCIAL;

        BuildingRegistry buildings = new BuildingRegistry();
        int plantId = buildings.create(BuildingType.POWER_PLANT, 10, 20);
        chunk.buildingId[Chunk.tileIndex(10, 20)] = plantId;
        int towerId = buildings.create(BuildingType.WATER_TOWER, 10, 21);
        chunk.buildingId[Chunk.tileIndex(10, 21)] = towerId;

        PopulationSystem system = new PopulationSystem();
        refreshServices(store, buildings);
        system.tick(store, buildings); // settle both zones

        // Remove utilities entirely.
        buildings.demolish(plantId);
        buildings.demolish(towerId);
        chunk.buildingId[Chunk.tileIndex(10, 20)] = WorldConstants.NO_BUILDING;
        chunk.buildingId[Chunk.tileIndex(10, 21)] = WorldConstants.NO_BUILDING;

        long before = system.totalResidentialPopulation();
        for (int i = 0; i < 10; i++) {
            refreshServices(store, buildings);
            system.tick(store, buildings);
        }
        assertEquals(before, system.totalResidentialPopulation(), "unserviced buildings must not keep growing");
    }
}
