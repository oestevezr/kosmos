package com.kosmos.atlas.sim.commands.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.PortRegistry;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPortCommandTest {

    private ChunkStore store;
    private BuildingRegistry buildings;
    private CityRegistry cities;
    private RegionalGraph graph;
    private PortRegistry ports;

    @BeforeEach
    void setUp() {
        store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        // (5,5) is land with a water neighbor at (6,5) -> coastal.
        chunk.terrainType[Chunk.tileIndex(6, 5)] = WorldConstants.TERRAIN_DEEP_WATER;
        // (1,1) is itself water -> can never host a port regardless of adjacency.
        chunk.terrainType[Chunk.tileIndex(1, 1)] = WorldConstants.TERRAIN_DEEP_WATER;
        store.put(chunk);
        buildings = new BuildingRegistry();
        cities = new CityRegistry();
        cities.create("Portville", 5, 5, 0);
        graph = new RegionalGraph();
        ports = new PortRegistry();
    }

    private SimulationContext ctx() {
        return new SimulationContext(store, buildings, cities, graph, null, ports, 4096, 0);
    }

    @Test
    void coastalLandTileAcceptsAPort() {
        assertEquals(CommandResult.ACCEPTED, new BuildPortCommand(5, 5).apply(ctx()));

        int id = store.get(0, 0).buildingId[Chunk.tileIndex(5, 5)];
        assertNotEquals(WorldConstants.NO_BUILDING, id);
        assertEquals(BuildingType.PORT, buildings.type(id));
        assertTrue(ports.hasPort(id));
        assertEquals(BuildPortCommand.DEFAULT_BERTHS, ports.berths(id));
        assertEquals(BuildPortCommand.DEFAULT_CARGO_CAPACITY_PER_TICK, ports.cargoCapacityPerTick(id));
        assertEquals(BuildPortCommand.DEFAULT_CUSTOMS_EFFICIENCY_PERCENT, ports.customsEfficiencyPercent(id));
    }

    @Test
    void portRegistersAPortNodeInTheRegionalGraph() {
        new BuildPortCommand(5, 5).apply(ctx());

        int nearest = graph.nearestNodeOfType(5, 5, NodeType.PORT);
        assertTrue(nearest >= 0);
        assertEquals(5, graph.nodeTileX(nearest));
        assertEquals(5, graph.nodeTileY(nearest));
    }

    @Test
    void inlandTileWithNoAdjacentWaterIsRejected() {
        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN, new BuildPortCommand(10, 5).apply(ctx()));
    }

    @Test
    void waterTileItselfIsRejected() {
        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN, new BuildPortCommand(1, 1).apply(ctx()));
    }

    @Test
    void occupiedTileIsRejected() {
        assertEquals(CommandResult.ACCEPTED, new BuildPortCommand(5, 5).apply(ctx()));
        assertEquals(CommandResult.REJECTED_TILE_OCCUPIED, new BuildPortCommand(5, 5).apply(ctx()));
    }

    @Test
    void noCityFoundedNearbyIsRejected() {
        SimulationContext noCityCtx = new SimulationContext(
            store, buildings, new CityRegistry(), graph, null, ports, 4096, 0);
        assertEquals(CommandResult.REJECTED_NO_CITY_FOUNDED, new BuildPortCommand(5, 5).apply(noCityCtx));
    }

    @Test
    void portRejectsWithoutEnoughFunds() {
        int cityId = cities.nearestCity(5, 5);
        cities.finance(cityId).adjustTreasury(-cities.finance(cityId).treasuryBalance());
        assertEquals(CommandResult.REJECTED_INSUFFICIENT_FUNDS, new BuildPortCommand(5, 5).apply(ctx()));
    }
}
