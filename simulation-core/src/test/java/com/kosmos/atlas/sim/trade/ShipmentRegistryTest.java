package com.kosmos.atlas.sim.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code commodity} is passed as a raw byte here — {@code sim.trade} has no dependency on {@code GoodType}. */
class ShipmentRegistryTest {

    private static final byte SOME_GOOD = 2;

    @Test
    void createIsRetrievableAndActive() {
        ShipmentRegistry registry = new ShipmentRegistry();
        int id = registry.create(ShipmentKind.IMPORT, SOME_GOOD, 50, 7, 100, 120);

        assertTrue(registry.isActive(id));
        assertEquals(ShipmentKind.IMPORT, registry.kind(id));
        assertEquals(SOME_GOOD, registry.commodity(id));
        assertEquals(50, registry.quantity(id));
        assertEquals(7, registry.depotBuildingId(id));
        assertEquals(100, registry.departureTick(id));
        assertEquals(120, registry.etaTick(id));
    }

    @Test
    void completeFreesIdForReuse() {
        ShipmentRegistry registry = new ShipmentRegistry();
        int id = registry.create(ShipmentKind.EXPORT, SOME_GOOD, 10, 1, 0, 10);
        registry.complete(id);

        assertFalse(registry.isActive(id));
        int reused = registry.create(ShipmentKind.IMPORT, SOME_GOOD, 5, 2, 1, 11);
        assertEquals(id, reused);
    }

    @Test
    void countActiveForDepotOnlyCountsThatDepot() {
        ShipmentRegistry registry = new ShipmentRegistry();
        registry.create(ShipmentKind.IMPORT, SOME_GOOD, 1, 1, 0, 10);
        registry.create(ShipmentKind.IMPORT, SOME_GOOD, 1, 1, 0, 10);
        registry.create(ShipmentKind.IMPORT, SOME_GOOD, 1, 2, 0, 10);

        assertEquals(2, registry.countActiveForDepot(1));
        assertEquals(1, registry.countActiveForDepot(2));
        assertEquals(0, registry.countActiveForDepot(3));
    }

    @Test
    void forEachActiveSkipsCompletedShipments() {
        ShipmentRegistry registry = new ShipmentRegistry();
        int a = registry.create(ShipmentKind.IMPORT, SOME_GOOD, 1, 1, 0, 10);
        registry.create(ShipmentKind.IMPORT, SOME_GOOD, 1, 1, 0, 10);
        registry.complete(a);

        int[] count = {0};
        registry.forEachActive(id -> count[0]++);
        assertEquals(1, count[0]);
    }
}
