package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.economy.GoodType;
import com.kosmos.atlas.sim.economy.GoodsLedger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Round-trips {@code economy.dat}, per spec §31. */
class GoodsLedgerRoundTripTest {

    @Test
    void inventoryBasePriceAndTargetInventoryRoundTrip(@TempDir Path tmp) throws IOException {
        GoodsLedger original = new GoodsLedger();
        original.produce(GoodType.STEEL, 137);
        original.setBasePrice(GoodType.STEEL, 42.5);
        original.setTargetInventory(GoodType.STEEL, 300);
        original.produce(GoodType.FOOD, 20);

        Path file = tmp.resolve("economy.dat");
        GoodsLedgerIO.write(file, original);
        GoodsLedger loaded = GoodsLedgerIO.read(file);

        assertEquals(137, loaded.inventory(GoodType.STEEL));
        assertEquals(42.5, loaded.basePrice(GoodType.STEEL), 1e-9);
        assertEquals(300, loaded.targetInventory(GoodType.STEEL));
        assertEquals(20, loaded.inventory(GoodType.FOOD));
    }

    @Test
    void loadedLedgerIsAlreadyRepriced(@TempDir Path tmp) throws IOException {
        GoodsLedger original = new GoodsLedger();
        original.setBasePrice(GoodType.ORE, 10.0);
        original.setTargetInventory(GoodType.ORE, 100);
        // Inventory left at 0 -> price should settle near double base price once repriced.

        Path file = tmp.resolve("economy.dat");
        GoodsLedgerIO.write(file, original);
        GoodsLedger loaded = GoodsLedgerIO.read(file);

        assertEquals(20.0, loaded.price(GoodType.ORE), 0.01, "read() must reprice, not leave stale/default prices");
    }

    @Test
    void perTickTalliesDoNotSurviveARoundTrip(@TempDir Path tmp) throws IOException {
        GoodsLedger original = new GoodsLedger();
        original.beginTick();
        original.produce(GoodType.TIMBER, 50);
        original.consume(GoodType.TIMBER, 10);

        Path file = tmp.resolve("economy.dat");
        GoodsLedgerIO.write(file, original);
        GoodsLedger loaded = GoodsLedgerIO.read(file);

        assertEquals(40, loaded.inventory(GoodType.TIMBER), "inventory (authoritative) must survive");
        assertEquals(0, loaded.producedLastTick(GoodType.TIMBER), "per-tick tallies are ephemeral, not persisted");
        assertEquals(0, loaded.consumedLastTick(GoodType.TIMBER));
    }
}
