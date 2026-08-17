package com.kosmos.atlas.sim.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirportRegistryTest {

    @Test
    void setIsRetrievableAndFlagsHasAirport() {
        AirportRegistry airports = new AirportRegistry();
        airports.set(5, 4, 50, 60);

        assertTrue(airports.hasAirport(5));
        assertEquals(4, airports.gates(5));
        assertEquals(50, airports.cargoCapacityPerTick(5));
        assertEquals(60, airports.customsEfficiencyPercent(5));
    }

    @Test
    void unknownBuildingIdHasNoAirport() {
        AirportRegistry airports = new AirportRegistry();
        airports.set(5, 4, 50, 60);

        assertFalse(airports.hasAirport(1));
        assertFalse(airports.hasAirport(999));
        assertFalse(airports.hasAirport(0));
    }

    @Test
    void setGrowsBackingArraysForLargeBuildingIds() {
        AirportRegistry airports = new AirportRegistry(2);
        airports.set(500, 4, 50, 60);

        assertTrue(airports.hasAirport(500));
        assertEquals(4, airports.gates(500));
        assertEquals(501, airports.highWaterMark());
    }

    @Test
    void highWaterMarkTracksTheLargestBuildingIdSet() {
        AirportRegistry airports = new AirportRegistry();
        airports.set(3, 4, 50, 60);
        airports.set(10, 4, 50, 60);
        airports.set(7, 4, 50, 60);

        assertEquals(11, airports.highWaterMark());
    }
}
