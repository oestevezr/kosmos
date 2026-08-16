package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.trade.ShipmentKind;
import com.kosmos.atlas.sim.trade.ShipmentRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trips {@code routes.dat}, including a completed (tombstoned) shipment id, per spec §31. */
class ShipmentRegistryRoundTripTest {

    @Test
    void activeAndCompletedShipmentsRoundTrip(@TempDir Path tmp) throws IOException {
        ShipmentRegistry original = new ShipmentRegistry();
        int inFlight = original.create(ShipmentKind.IMPORT, (byte) 2, 75, 3, 1, 100, 130);
        int completed = original.create(ShipmentKind.EXPORT, (byte) 1, 40, 3, 1, 50, 70);
        original.complete(completed);

        Path file = tmp.resolve("routes.dat");
        ShipmentRegistryIO.write(file, original);
        ShipmentRegistry loaded = ShipmentRegistryIO.read(file);

        assertTrue(loaded.isActive(inFlight));
        assertEquals(ShipmentKind.IMPORT, loaded.kind(inFlight));
        assertEquals(2, loaded.commodity(inFlight));
        assertEquals(75, loaded.quantity(inFlight));
        assertEquals(3, loaded.depotBuildingId(inFlight));
        assertEquals(1, loaded.cityId(inFlight));
        assertEquals(100, loaded.departureTick(inFlight));
        assertEquals(130, loaded.etaTick(inFlight));

        assertFalse(loaded.isActive(completed), "a completed shipment must load back as inactive");

        // The restored free list must still be usable for new shipments.
        int reused = loaded.create(ShipmentKind.IMPORT, (byte) 0, 10, 9, 1, 200, 220);
        assertTrue(loaded.isActive(reused));
    }

    @Test
    void emptyRegistryRoundTrips(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("routes.dat");
        ShipmentRegistryIO.write(file, new ShipmentRegistry());
        ShipmentRegistry loaded = ShipmentRegistryIO.read(file);
        assertEquals(0, loaded.activeCount());
    }
}
