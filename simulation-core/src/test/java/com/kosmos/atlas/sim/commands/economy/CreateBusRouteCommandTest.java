package com.kosmos.atlas.sim.commands.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.BusRouteRegistry;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateBusRouteCommandTest {

    private ChunkStore store;
    private BuildingRegistry buildings;
    private CityRegistry cities;
    private RegionalGraph graph;
    private BusRouteRegistry busRoutes;
    private int cityId;
    private int depot;

    @BeforeEach
    void setUp() {
        store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);
        buildings = new BuildingRegistry();
        cities = new CityRegistry();
        cityId = cities.create("Bustown", 5, 5, 0);
        graph = new RegionalGraph();
        busRoutes = new BusRouteRegistry();

        depot = buildings.create(BuildingType.BUS_DEPOT, 0, 0, cityId);
    }

    private SimulationContext ctx() {
        return new SimulationContext(store, buildings, cities, graph, null, null, null, null, busRoutes, 4096, 0);
    }

    private int stopAt(int x, int y) {
        int id = buildings.create(BuildingType.BUS_STOP, x, y, cityId);
        graph.addNode(com.kosmos.atlas.sim.trade.NodeType.BUS_STOP, x, y);
        return id;
    }

    @Test
    void validRouteCreatesEdgesAndARegistryEntry() {
        int stopA = stopAt(1, 0);
        int stopB = stopAt(2, 0);
        int stopC = stopAt(3, 0);

        int edgesBefore = graph.edgeCount();
        CommandResult result = new CreateBusRouteCommand(depot, new int[] {stopA, stopB, stopC}).apply(ctx());

        assertEquals(CommandResult.ACCEPTED, result);
        assertEquals(edgesBefore + 2, graph.edgeCount(), "3 stops means 2 consecutive-pair edges");
        assertEquals(1, busRoutes.countRoutesForDepot(depot));
        assertTrue(busRoutes.isStopInAnyActiveRoute(stopA));
        assertTrue(busRoutes.isStopInAnyActiveRoute(stopC));
    }

    @Test
    void tooFewStopsIsRejected() {
        int stopA = stopAt(1, 0);
        assertEquals(CommandResult.REJECTED_INVALID_ROUTE,
            new CreateBusRouteCommand(depot, new int[] {stopA}).apply(ctx()));
    }

    @Test
    void tooManyStopsIsRejected() {
        int[] stops = new int[BusRouteRegistry.MAX_STOPS_PER_ROUTE + 1];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = stopAt(i + 1, 0);
        }
        assertEquals(CommandResult.REJECTED_INVALID_ROUTE, new CreateBusRouteCommand(depot, stops).apply(ctx()));
    }

    @Test
    void nonBusStopBuildingInTheListIsRejected() {
        int stopA = stopAt(1, 0);
        int notAStop = buildings.create(BuildingType.RESIDENTIAL, 2, 0, cityId);
        assertEquals(CommandResult.REJECTED_INVALID_ROUTE,
            new CreateBusRouteCommand(depot, new int[] {stopA, notAStop}).apply(ctx()));
    }

    @Test
    void depotFromADifferentCityThanItsStopsIsRejected() {
        int otherCityId = cities.create("Otherville", 50, 50, 0);
        int otherDepot = buildings.create(BuildingType.BUS_DEPOT, 51, 51, otherCityId);
        int stopA = stopAt(1, 0);
        int stopB = stopAt(2, 0);

        assertEquals(CommandResult.REJECTED_INVALID_ROUTE,
            new CreateBusRouteCommand(otherDepot, new int[] {stopA, stopB}).apply(ctx()));
    }

    @Test
    void depotAtCapacityIsRejected() {
        int maxRoutes = com.kosmos.atlas.sim.economy.BuildingEconomics.capacity(BuildingType.BUS_DEPOT);
        for (int i = 0; i < maxRoutes; i++) {
            int a = stopAt(i * 2 + 1, 0);
            int b = stopAt(i * 2 + 2, 0);
            assertEquals(CommandResult.ACCEPTED, new CreateBusRouteCommand(depot, new int[] {a, b}).apply(ctx()));
        }

        int extraA = stopAt(90, 0);
        int extraB = stopAt(91, 0);
        assertEquals(CommandResult.REJECTED_DEPOT_AT_CAPACITY,
            new CreateBusRouteCommand(depot, new int[] {extraA, extraB}).apply(ctx()));
    }
}
