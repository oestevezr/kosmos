package com.kosmos.atlas.sim.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusRouteRegistryTest {

    @Test
    void createIsRetrievableAndActive() {
        BusRouteRegistry routes = new BusRouteRegistry();
        int id = routes.create(1, 1, new int[] {10, 11, 12});

        assertTrue(routes.isActive(id));
        assertEquals(1, routes.depotBuildingId(id));
        assertEquals(1, routes.cityId(id));
        assertEquals(3, routes.stopCount(id));
        assertEquals(10, routes.stopBuildingIdAt(id, 0));
        assertEquals(11, routes.stopBuildingIdAt(id, 1));
        assertEquals(12, routes.stopBuildingIdAt(id, 2));
    }

    @Test
    void countRoutesForDepotOnlyCountsThatDepot() {
        BusRouteRegistry routes = new BusRouteRegistry();
        routes.create(1, 1, new int[] {10, 11});
        routes.create(1, 1, new int[] {12, 13});
        routes.create(2, 1, new int[] {14, 15});

        assertEquals(2, routes.countRoutesForDepot(1));
        assertEquals(1, routes.countRoutesForDepot(2));
        assertEquals(0, routes.countRoutesForDepot(999));
    }

    @Test
    void isStopInAnyActiveRouteFindsStopsAcrossMultipleRoutes() {
        BusRouteRegistry routes = new BusRouteRegistry();
        routes.create(1, 1, new int[] {10, 11});
        routes.create(2, 1, new int[] {12, 13});

        assertTrue(routes.isStopInAnyActiveRoute(10));
        assertTrue(routes.isStopInAnyActiveRoute(13));
        assertFalse(routes.isStopInAnyActiveRoute(999), "a building id that's never a stop must not match");
    }

    @Test
    void demolishingARouteRemovesItFromCoverageAndFreesItsId() {
        BusRouteRegistry routes = new BusRouteRegistry();
        int id = routes.create(1, 1, new int[] {10, 11});
        assertTrue(routes.isStopInAnyActiveRoute(10));

        routes.demolishRoute(id);

        assertFalse(routes.isActive(id));
        assertFalse(routes.isStopInAnyActiveRoute(10), "a demolished route's stops must stop counting as covered");
        assertEquals(0, routes.countRoutesForDepot(1));
    }
}
