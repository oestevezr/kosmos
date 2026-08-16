package com.kosmos.atlas.sim.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortRegistryTest {

    @Test
    void setIsRetrievableAndFlagsHasPort() {
        PortRegistry ports = new PortRegistry();
        ports.set(5, 6, 75, 50);

        assertTrue(ports.hasPort(5));
        assertEquals(6, ports.berths(5));
        assertEquals(75, ports.cargoCapacityPerTick(5));
        assertEquals(50, ports.customsEfficiencyPercent(5));
    }

    @Test
    void unknownBuildingIdHasNoPort() {
        PortRegistry ports = new PortRegistry();
        ports.set(5, 6, 75, 50);

        assertFalse(ports.hasPort(1));
        assertFalse(ports.hasPort(999));
        assertFalse(ports.hasPort(0));
    }

    @Test
    void setGrowsBackingArraysForLargeBuildingIds() {
        PortRegistry ports = new PortRegistry(2);
        ports.set(500, 4, 40, 20);

        assertTrue(ports.hasPort(500));
        assertEquals(4, ports.berths(500));
        assertEquals(501, ports.highWaterMark());
    }

    @Test
    void highWaterMarkTracksTheLargestBuildingIdSet() {
        PortRegistry ports = new PortRegistry();
        ports.set(3, 6, 75, 50);
        ports.set(10, 6, 75, 50);
        ports.set(7, 6, 75, 50);

        assertEquals(11, ports.highWaterMark());
    }
}
