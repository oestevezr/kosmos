package com.kosmos.atlas.sim.commands.economy;

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

class BuildBusDepotCommandTest {

    private ChunkStore store;
    private BuildingRegistry buildings;
    private CityRegistry cities;

    @BeforeEach
    void setUp() {
        store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        chunk.terrainType[Chunk.tileIndex(1, 1)] = WorldConstants.TERRAIN_DEEP_WATER;
        store.put(chunk);
        buildings = new BuildingRegistry();
        cities = new CityRegistry();
        cities.create("Bustown", 5, 5, 0);
    }

    private SimulationContext ctx() {
        return new SimulationContext(store, buildings, cities, 4096, 0);
    }

    @Test
    void landTileAcceptsABusDepot() {
        assertEquals(CommandResult.ACCEPTED, new BuildBusDepotCommand(5, 5).apply(ctx()));

        int id = store.get(0, 0).buildingId[Chunk.tileIndex(5, 5)];
        assertNotEquals(WorldConstants.NO_BUILDING, id);
        assertEquals(BuildingType.BUS_DEPOT, buildings.type(id));
    }

    @Test
    void waterTileItselfIsRejected() {
        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN, new BuildBusDepotCommand(1, 1).apply(ctx()));
    }

    @Test
    void occupiedTileIsRejected() {
        assertEquals(CommandResult.ACCEPTED, new BuildBusDepotCommand(5, 5).apply(ctx()));
        assertEquals(CommandResult.REJECTED_TILE_OCCUPIED, new BuildBusDepotCommand(5, 5).apply(ctx()));
    }

    @Test
    void noCityFoundedNearbyIsRejected() {
        SimulationContext noCityCtx = new SimulationContext(store, buildings, new CityRegistry(), 4096, 0);
        assertEquals(CommandResult.REJECTED_NO_CITY_FOUNDED, new BuildBusDepotCommand(5, 5).apply(noCityCtx));
    }

    @Test
    void rejectsWithoutEnoughFunds() {
        int cityId = cities.nearestCity(5, 5);
        cities.finance(cityId).adjustTreasury(-cities.finance(cityId).treasuryBalance());
        assertEquals(CommandResult.REJECTED_INSUFFICIENT_FUNDS, new BuildBusDepotCommand(5, 5).apply(ctx()));
    }
}
