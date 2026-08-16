package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.trade.PortRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trips {@code ports.dat}, including building ids with no port row. */
class PortRegistryRoundTripTest {

    @Test
    void portRowsRoundTrip(@TempDir Path tmp) throws IOException {
        PortRegistry original = new PortRegistry();
        original.set(2, 6, 75, 50);
        original.set(5, 4, 40, 20);

        Path file = tmp.resolve("ports.dat");
        PortRegistryIO.write(file, original);
        PortRegistry loaded = PortRegistryIO.read(file);

        assertEquals(original.highWaterMark(), loaded.highWaterMark());

        assertTrue(loaded.hasPort(2));
        assertEquals(6, loaded.berths(2));
        assertEquals(75, loaded.cargoCapacityPerTick(2));
        assertEquals(50, loaded.customsEfficiencyPercent(2));

        assertTrue(loaded.hasPort(5));
        assertEquals(4, loaded.berths(5));

        assertFalse(loaded.hasPort(1), "a building id with no port row must load back as no port");
        assertFalse(loaded.hasPort(3));

        // Still usable for new ports after loading.
        loaded.set(10, 5, 50, 30);
        assertTrue(loaded.hasPort(10));
    }

    @Test
    void emptyRegistryRoundTrips(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("ports.dat");
        PortRegistryIO.write(file, new PortRegistry());
        PortRegistry loaded = PortRegistryIO.read(file);
        assertEquals(1, loaded.highWaterMark());
    }
}
