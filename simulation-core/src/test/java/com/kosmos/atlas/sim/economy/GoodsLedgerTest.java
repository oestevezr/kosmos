package com.kosmos.atlas.sim.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoodsLedgerTest {

    @Test
    void defaultBasePricesDifferByProductionChainDepthNotFlatAcrossAllGoods() {
        GoodsLedger ledger = new GoodsLedger();

        assertTrue(ledger.basePrice(GoodType.STEEL) > ledger.basePrice(GoodType.ORE),
            "refined Steel should cost more than the raw Ore it's made from");
        assertTrue(ledger.basePrice(GoodType.CONSUMER_GOODS) > ledger.basePrice(GoodType.FOOD),
            "a good with no domestic producer should be priced above a raw extracted staple");
        assertTrue(ledger.basePrice(GoodType.MACHINERY) > ledger.basePrice(GoodType.CONSTRUCTION_MATERIALS),
            "the most complex good should not be priced the same as quarried raw material");
    }

    @Test
    void produceAddsToInventoryAndTally() {
        GoodsLedger ledger = new GoodsLedger();
        ledger.beginTick();
        ledger.produce(GoodType.FOOD, 15);
        assertEquals(15, ledger.inventory(GoodType.FOOD));
        assertEquals(15, ledger.producedLastTick(GoodType.FOOD));
    }

    @Test
    void consumeIsCappedByAvailableInventoryAndTracksShortage() {
        GoodsLedger ledger = new GoodsLedger();
        ledger.beginTick();
        ledger.produce(GoodType.FOOD, 10);

        int consumed = ledger.consume(GoodType.FOOD, 15);

        assertEquals(10, consumed, "cannot consume more than is in stock");
        assertEquals(0, ledger.inventory(GoodType.FOOD));
        assertEquals(10, ledger.consumedLastTick(GoodType.FOOD));
        assertEquals(5, ledger.shortageLastTick(GoodType.FOOD));
    }

    @Test
    void beginTickResetsPerTickTalliesButNotInventory() {
        GoodsLedger ledger = new GoodsLedger();
        ledger.beginTick();
        ledger.produce(GoodType.TIMBER, 20);
        ledger.consume(GoodType.TIMBER, 5);

        ledger.beginTick(); // next tick

        assertEquals(15, ledger.inventory(GoodType.TIMBER), "inventory persists across ticks");
        assertEquals(0, ledger.producedLastTick(GoodType.TIMBER));
        assertEquals(0, ledger.consumedLastTick(GoodType.TIMBER));
    }

    @Test
    void priceRisesWhenInventoryIsBelowTargetAndSettlesAtBaseWhenAtTarget() {
        GoodsLedger ledger = new GoodsLedger();
        ledger.setBasePrice(GoodType.ORE, 10.0);
        ledger.setTargetInventory(GoodType.ORE, 100);

        ledger.beginTick();
        ledger.repriceAll(); // empty inventory
        double priceWhenEmpty = ledger.price(GoodType.ORE);

        ledger.produce(GoodType.ORE, 100); // exactly at target
        ledger.repriceAll();
        double priceAtTarget = ledger.price(GoodType.ORE);

        assertEquals(20.0, priceWhenEmpty, 0.01, "empty inventory should price near double base");
        assertEquals(10.0, priceAtTarget, 0.01, "inventory at target should settle at base price");
        assertTrue(priceWhenEmpty > priceAtTarget);
    }

    @Test
    void transportCostReducesPrice() {
        GoodsLedger ledger = new GoodsLedger();
        ledger.setBasePrice(GoodType.STEEL, 50.0);
        ledger.setTargetInventory(GoodType.STEEL, 100);
        ledger.produce(GoodType.STEEL, 100); // at target, would otherwise price at base

        ledger.setTransportCostPerUnit(GoodType.STEEL, 12.0);
        ledger.repriceAll();

        assertEquals(38.0, ledger.price(GoodType.STEEL), 0.01);
    }

    @Test
    void importAndExportAreTrackedSeparatelyFromProductionAndConsumption() {
        GoodsLedger ledger = new GoodsLedger();
        ledger.beginTick();

        ledger.importGood(GoodType.FUEL, 30);
        assertEquals(30, ledger.inventory(GoodType.FUEL));
        assertEquals(30, ledger.importedLastTick(GoodType.FUEL));
        assertEquals(0, ledger.producedLastTick(GoodType.FUEL), "imports must not count as domestic production");

        int exported = ledger.exportGood(GoodType.FUEL, 50);
        assertEquals(30, exported, "cannot export more than is in stock");
        assertEquals(0, ledger.inventory(GoodType.FUEL));
        assertEquals(30, ledger.exportedLastTick(GoodType.FUEL));
    }
}
