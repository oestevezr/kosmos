package com.kosmos.atlas.sim.commands.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.economy.BuildingEconomics;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.AirportRegistry;
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildAirportCommandTest {

    private ChunkStore store;
    private BuildingRegistry buildings;
    private CityRegistry cities;
    private RegionalGraph graph;
    private AirportRegistry airports;
    private int cityId;

    @BeforeEach
    void setUp() {
        store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        // (1,1) is water — an inland-only building must still reject it.
        chunk.terrainType[Chunk.tileIndex(1, 1)] = WorldConstants.TERRAIN_DEEP_WATER;
        store.put(chunk);
        buildings = new BuildingRegistry();
        cities = new CityRegistry();
        cityId = cities.create("Airfield", 5, 5, 0);
        graph = new RegionalGraph();
        airports = new AirportRegistry();
    }

    private SimulationContext ctx() {
        return new SimulationContext(store, buildings, cities, graph, null, null, airports, 4096, 0);
    }

    private void unlockAirportPopulation() {
        int home = buildings.create(BuildingType.RESIDENTIAL, 20, 20, cityId);
        buildings.setPopulation(home, (int) BuildingEconomics.unlockPopulation(BuildingType.AIRPORT));
    }

    @Test
    void rejectedUntilPopulationUnlocksItThenAccepted() {
        assertEquals(CommandResult.REJECTED_SERVICE_TIER_LOCKED, new BuildAirportCommand(5, 5).apply(ctx()));

        unlockAirportPopulation();
        assertEquals(CommandResult.ACCEPTED, new BuildAirportCommand(5, 5).apply(ctx()));

        int id = store.get(0, 0).buildingId[Chunk.tileIndex(5, 5)];
        assertNotEquals(WorldConstants.NO_BUILDING, id);
        assertEquals(BuildingType.AIRPORT, buildings.type(id));
        assertTrue(airports.hasAirport(id));
        assertEquals(BuildAirportCommand.DEFAULT_GATES, airports.gates(id));
        assertEquals(BuildAirportCommand.DEFAULT_CARGO_CAPACITY_PER_TICK, airports.cargoCapacityPerTick(id));
        assertEquals(BuildAirportCommand.DEFAULT_CUSTOMS_EFFICIENCY_PERCENT, airports.customsEfficiencyPercent(id));
    }

    @Test
    void airportRegistersAnAirportNodeInTheRegionalGraph() {
        unlockAirportPopulation();
        new BuildAirportCommand(5, 5).apply(ctx());

        int nearest = graph.nearestNodeOfType(5, 5, NodeType.AIRPORT);
        assertTrue(nearest >= 0);
        assertEquals(5, graph.nodeTileX(nearest));
        assertEquals(5, graph.nodeTileY(nearest));
    }

    @Test
    void inlandTileDoesNotNeedToBeCoastalUnlikeAPort() {
        unlockAirportPopulation();
        assertEquals(CommandResult.ACCEPTED, new BuildAirportCommand(10, 5).apply(ctx()),
            "an Airport, unlike a Port, has no adjacent-water requirement");
    }

    @Test
    void waterTileItselfIsRejected() {
        unlockAirportPopulation();
        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN, new BuildAirportCommand(1, 1).apply(ctx()));
    }

    @Test
    void occupiedTileIsRejected() {
        unlockAirportPopulation();
        assertEquals(CommandResult.ACCEPTED, new BuildAirportCommand(5, 5).apply(ctx()));
        assertEquals(CommandResult.REJECTED_TILE_OCCUPIED, new BuildAirportCommand(5, 5).apply(ctx()));
    }

    @Test
    void noCityFoundedNearbyIsRejected() {
        SimulationContext noCityCtx = new SimulationContext(
            store, buildings, new CityRegistry(), graph, null, null, airports, 4096, 0);
        assertEquals(CommandResult.REJECTED_NO_CITY_FOUNDED, new BuildAirportCommand(5, 5).apply(noCityCtx));
    }

    @Test
    void rejectsWithoutEnoughFunds() {
        unlockAirportPopulation();
        cities.finance(cityId).adjustTreasury(-cities.finance(cityId).treasuryBalance());
        assertEquals(CommandResult.REJECTED_INSUFFICIENT_FUNDS, new BuildAirportCommand(5, 5).apply(ctx()));
    }
}
