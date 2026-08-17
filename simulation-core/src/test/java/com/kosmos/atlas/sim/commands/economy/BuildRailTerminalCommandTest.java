package com.kosmos.atlas.sim.commands.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.trade.StationRegistry;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildRailTerminalCommandTest {

    private ChunkStore store;
    private BuildingRegistry buildings;
    private CityRegistry cities;
    private RegionalGraph graph;
    private StationRegistry stations;

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
        cities.create("Railtown", 5, 5, 0);
        graph = new RegionalGraph();
        stations = new StationRegistry();
    }

    private SimulationContext ctx() {
        return new SimulationContext(store, buildings, cities, graph, null, null, null, stations, null, 4096, 0);
    }

    @Test
    void inlandLandTileAcceptsARailTerminalWithNoCoastalOrPopulationGate() {
        assertEquals(CommandResult.ACCEPTED, new BuildRailTerminalCommand(10, 5).apply(ctx()),
            "unlike a Port, a Rail Terminal needs neither adjacent water nor a population threshold");

        int id = store.get(0, 0).buildingId[Chunk.tileIndex(10, 5)];
        assertNotEquals(WorldConstants.NO_BUILDING, id);
        assertEquals(BuildingType.RAIL_TERMINAL, buildings.type(id));
        assertTrue(stations.hasTerminal(id));
        assertEquals(BuildRailTerminalCommand.DEFAULT_PLATFORMS, stations.platforms(id));
        assertEquals(BuildRailTerminalCommand.DEFAULT_CARGO_CAPACITY_PER_TICK, stations.cargoCapacityPerTick(id));
    }

    @Test
    void railTerminalRegistersAStationNodeInTheRegionalGraph() {
        new BuildRailTerminalCommand(5, 5).apply(ctx());

        int nearest = graph.nearestNodeOfType(5, 5, NodeType.STATION);
        assertTrue(nearest >= 0);
        assertEquals(5, graph.nodeTileX(nearest));
        assertEquals(5, graph.nodeTileY(nearest));
    }

    @Test
    void waterTileItselfIsRejected() {
        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN, new BuildRailTerminalCommand(1, 1).apply(ctx()));
    }

    @Test
    void occupiedTileIsRejected() {
        assertEquals(CommandResult.ACCEPTED, new BuildRailTerminalCommand(5, 5).apply(ctx()));
        assertEquals(CommandResult.REJECTED_TILE_OCCUPIED, new BuildRailTerminalCommand(5, 5).apply(ctx()));
    }

    @Test
    void noCityFoundedNearbyIsRejected() {
        SimulationContext noCityCtx = new SimulationContext(
            store, buildings, new CityRegistry(), graph, null, null, null, stations, null, 4096, 0);
        assertEquals(CommandResult.REJECTED_NO_CITY_FOUNDED, new BuildRailTerminalCommand(5, 5).apply(noCityCtx));
    }

    @Test
    void rejectsWithoutEnoughFunds() {
        int cityId = cities.nearestCity(5, 5);
        cities.finance(cityId).adjustTreasury(-cities.finance(cityId).treasuryBalance());
        assertEquals(CommandResult.REJECTED_INSUFFICIENT_FUNDS, new BuildRailTerminalCommand(5, 5).apply(ctx()));
    }
}
