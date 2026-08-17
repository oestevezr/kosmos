package com.kosmos.atlas.sim.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationRegistryTest {

    @Test
    void setIsRetrievableAndFlagsHasTerminal() {
        StationRegistry stations = new StationRegistry();
        stations.set(5, 5, 60);

        assertTrue(stations.hasTerminal(5));
        assertEquals(5, stations.platforms(5));
        assertEquals(60, stations.cargoCapacityPerTick(5));
    }

    @Test
    void unknownBuildingIdHasNoTerminal() {
        StationRegistry stations = new StationRegistry();
        stations.set(5, 5, 60);

        assertFalse(stations.hasTerminal(1));
        assertFalse(stations.hasTerminal(999));
        assertFalse(stations.hasTerminal(0));
    }

    @Test
    void setGrowsBackingArraysForLargeBuildingIds() {
        StationRegistry stations = new StationRegistry(2);
        stations.set(500, 5, 60);

        assertTrue(stations.hasTerminal(500));
        assertEquals(5, stations.platforms(500));
        assertEquals(501, stations.highWaterMark());
    }

    @Test
    void highWaterMarkTracksTheLargestBuildingIdSet() {
        StationRegistry stations = new StationRegistry();
        stations.set(3, 5, 60);
        stations.set(10, 5, 60);
        stations.set(7, 5, 60);

        assertEquals(11, stations.highWaterMark());
    }
}
