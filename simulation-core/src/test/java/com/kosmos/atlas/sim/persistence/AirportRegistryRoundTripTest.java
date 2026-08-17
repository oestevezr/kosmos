package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.trade.AirportRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trips {@code airports.dat}, including building ids with no airport row. */
class AirportRegistryRoundTripTest {

    @Test
    void airportRowsRoundTrip(@TempDir Path tmp) throws IOException {
        AirportRegistry original = new AirportRegistry();
        original.set(2, 4, 50, 60);
        original.set(5, 6, 80, 40);

        Path file = tmp.resolve("airports.dat");
        AirportRegistryIO.write(file, original);
        AirportRegistry loaded = AirportRegistryIO.read(file);

        assertEquals(original.highWaterMark(), loaded.highWaterMark());

        assertTrue(loaded.hasAirport(2));
        assertEquals(4, loaded.gates(2));
        assertEquals(50, loaded.cargoCapacityPerTick(2));
        assertEquals(60, loaded.customsEfficiencyPercent(2));

        assertTrue(loaded.hasAirport(5));
        assertEquals(6, loaded.gates(5));

        assertFalse(loaded.hasAirport(1), "a building id with no airport row must load back as no airport");
        assertFalse(loaded.hasAirport(3));

        // Still usable for new airports after loading.
        loaded.set(10, 3, 40, 30);
        assertTrue(loaded.hasAirport(10));
    }

    @Test
    void emptyRegistryRoundTrips(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("airports.dat");
        AirportRegistryIO.write(file, new AirportRegistry());
        AirportRegistry loaded = AirportRegistryIO.read(file);
        assertEquals(1, loaded.highWaterMark());
    }
}
