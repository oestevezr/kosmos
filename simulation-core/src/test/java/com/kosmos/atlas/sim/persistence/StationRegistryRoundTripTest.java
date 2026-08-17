package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.trade.StationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trips {@code stations.dat}, including building ids with no terminal row. */
class StationRegistryRoundTripTest {

    @Test
    void stationRowsRoundTrip(@TempDir Path tmp) throws IOException {
        StationRegistry original = new StationRegistry();
        original.set(2, 5, 60);
        original.set(5, 8, 90);

        Path file = tmp.resolve("stations.dat");
        StationRegistryIO.write(file, original);
        StationRegistry loaded = StationRegistryIO.read(file);

        assertEquals(original.highWaterMark(), loaded.highWaterMark());

        assertTrue(loaded.hasTerminal(2));
        assertEquals(5, loaded.platforms(2));
        assertEquals(60, loaded.cargoCapacityPerTick(2));

        assertTrue(loaded.hasTerminal(5));
        assertEquals(8, loaded.platforms(5));

        assertFalse(loaded.hasTerminal(1), "a building id with no terminal row must load back as no terminal");
        assertFalse(loaded.hasTerminal(3));

        // Still usable for new terminals after loading.
        loaded.set(10, 4, 50);
        assertTrue(loaded.hasTerminal(10));
    }

    @Test
    void emptyRegistryRoundTrips(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("stations.dat");
        StationRegistryIO.write(file, new StationRegistry());
        StationRegistry loaded = StationRegistryIO.read(file);
        assertEquals(1, loaded.highWaterMark());
    }
}
