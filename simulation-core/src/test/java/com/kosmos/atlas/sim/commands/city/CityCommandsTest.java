package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Validates every Fase 2 city command's accept/reject rules (spec §38). */
class CityCommandsTest {

    private ChunkStore store;
    private BuildingRegistry buildings;
    private CityRegistry cities;
    private int cityId;

    @BeforeEach
    void setUp() {
        store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        // Tile (5,5) is land by default (byte 0 arrays => TERRAIN_DEEP_WATER=0!). Force it to plain.
        chunk.terrainType[Chunk.tileIndex(5, 5)] = WorldConstants.TERRAIN_PLAIN;
        chunk.terrainType[Chunk.tileIndex(6, 5)] = WorldConstants.TERRAIN_PLAIN;
        chunk.terrainType[Chunk.tileIndex(1, 1)] = WorldConstants.TERRAIN_DEEP_WATER;
        store.put(chunk);
        buildings = new BuildingRegistry();
        cities = new CityRegistry();
        cityId = cities.create("Testville", 5, 5, 0);
    }

    private SimulationContext ctx() {
        return new SimulationContext(store, buildings, cities, 4096, 0);
    }

    @Test
    void buildRoadOnLandSucceeds() {
        assertEquals(CommandResult.ACCEPTED, new BuildRoadCommand(5, 5).apply(ctx()));
        Chunk chunk = store.get(0, 0);
        assertEquals(WorldConstants.ROAD_DIRT, chunk.roadType[Chunk.tileIndex(5, 5)]);
    }

    @Test
    void buildRoadOnWaterFails() {
        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN, new BuildRoadCommand(1, 1).apply(ctx()));
    }

    @Test
    void buildRoadTwiceOnSameTileFails() {
        assertEquals(CommandResult.ACCEPTED, new BuildRoadCommand(5, 5).apply(ctx()));
        assertEquals(CommandResult.REJECTED_TILE_OCCUPIED, new BuildRoadCommand(5, 5).apply(ctx()));
    }

    @Test
    void zoneCommandSetsZoneType() {
        assertEquals(CommandResult.ACCEPTED, new ZoneCommand(5, 5, WorldConstants.ZONE_RESIDENTIAL).apply(ctx()));
        assertEquals(WorldConstants.ZONE_RESIDENTIAL, store.get(0, 0).zoneType[Chunk.tileIndex(5, 5)]);
    }

    @Test
    void zoneCommandRejectsInvalidZoneByte() {
        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN, new ZoneCommand(5, 5, (byte) 99).apply(ctx()));
    }

    @Test
    void buildPowerPlantCreatesActiveBuilding() {
        assertEquals(CommandResult.ACCEPTED, new BuildPowerPlantCommand(5, 5).apply(ctx()));
        int id = store.get(0, 0).buildingId[Chunk.tileIndex(5, 5)];
        assertNotEquals(WorldConstants.NO_BUILDING, id);
        assertEquals(BuildingType.POWER_PLANT, buildings.type(id));
    }

    @Test
    void buildWaterTowerOnOccupiedTileFails() {
        assertEquals(CommandResult.ACCEPTED, new BuildPowerPlantCommand(5, 5).apply(ctx()));
        assertEquals(CommandResult.REJECTED_TILE_OCCUPIED, new BuildWaterTowerCommand(5, 5).apply(ctx()));
    }

    @Test
    void demolishRemovesBuildingRoadAndZone() {
        new ZoneCommand(5, 5, WorldConstants.ZONE_RESIDENTIAL).apply(ctx());
        new BuildPowerPlantCommand(6, 5).apply(ctx()); // separate tile, since zoned tile has no building yet
        new BuildRoadCommand(5, 5).apply(ctx()); // wait: tile 5,5 already zoned but not occupied by a building

        assertEquals(CommandResult.ACCEPTED, new DemolishCommand(5, 5).apply(ctx()));
        Chunk chunk = store.get(0, 0);
        assertEquals(WorldConstants.ZONE_NONE, chunk.zoneType[Chunk.tileIndex(5, 5)]);
        assertEquals(WorldConstants.ROAD_NONE, chunk.roadType[Chunk.tileIndex(5, 5)]);

        int powerPlantId = chunk.buildingId[Chunk.tileIndex(6, 5)];
        assertEquals(CommandResult.ACCEPTED, new DemolishCommand(6, 5).apply(ctx()));
        assertEquals(WorldConstants.NO_BUILDING, chunk.buildingId[Chunk.tileIndex(6, 5)]);
        assertEquals(false, buildings.isActive(powerPlantId));
    }

    @Test
    void demolishOnEmptyTileRejected() {
        assertEquals(CommandResult.REJECTED_NOTHING_TO_DEMOLISH, new DemolishCommand(20, 20).apply(ctx()));
    }

    @Test
    void setTaxPolicyUpdatesRateAndRejectsOutOfRange() {
        assertEquals(CommandResult.ACCEPTED, new SetTaxPolicyCommand(cityId, WorldConstants.ZONE_RESIDENTIAL, 0.25).apply(ctx()));
        assertEquals(0.25, cities.finance(cityId).taxRate(WorldConstants.ZONE_RESIDENTIAL));
        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN,
            new SetTaxPolicyCommand(cityId, WorldConstants.ZONE_RESIDENTIAL, 1.5).apply(ctx()));
    }

    @Test
    void commandsOutsideWorldBoundsAreRejected() {
        SimulationContext tinyWorld = new SimulationContext(store, buildings, cities, 4, 0);
        assertEquals(CommandResult.REJECTED_OUT_OF_BOUNDS, new BuildRoadCommand(999, 999).apply(tinyWorld));
    }

    @Test
    void commandsOnUnknownChunkAreRejected() {
        assertEquals(CommandResult.REJECTED_UNKNOWN_CHUNK, new BuildRoadCommand(500, 500).apply(ctx()));
    }
}
