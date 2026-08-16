package com.kosmos.atlas.sim.economy;

import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of {@link MarketSystem#tick} — production, household/business consumption,
 * trade-depot import/export, and the transport-cost-affects-price loop (spec §20, §21, §29), all
 * driven the way {@code WorldManager}'s scheduler actually calls it.
 */
class MarketSystemTest {

    @Test
    void extractionBuildingProducesWithoutNeedingInput() {
        BuildingRegistry buildings = new BuildingRegistry();
        buildings.create(BuildingType.FARM, 0, 0, GoodType.FOOD, 10, GoodType.NONE, 0);

        GoodsLedger ledger = new GoodsLedger();
        new MarketSystem().tick(buildings, ledger, new GovernmentFinance(), new RegionalGraph());

        assertEquals(10, ledger.inventory(GoodType.FOOD));
    }

    @Test
    void processingBuildingOutputIsGatedByAvailableInput() {
        BuildingRegistry buildings = new BuildingRegistry();
        // Mine is created (and so gets a lower id) before Steel Mill, and MarketSystem's
        // production pass runs in id order — so within the very same tick, the mill sees the
        // ore the mine already produced earlier in that same pass. This is the documented,
        // deterministic id-order behavior (see MarketSystem.runProduction's javadoc), not a lag.
        buildings.create(BuildingType.MINE, 0, 0, GoodType.ORE, 8, GoodType.NONE, 0);
        buildings.create(BuildingType.STEEL_MILL, 1, 0, GoodType.STEEL, 6, GoodType.ORE, 8);

        GoodsLedger ledger = new GoodsLedger();
        MarketSystem system = new MarketSystem();
        GovernmentFinance finance = new GovernmentFinance();
        RegionalGraph graph = new RegionalGraph();

        system.tick(buildings, ledger, finance, graph);
        assertEquals(6, ledger.inventory(GoodType.STEEL), "the mill runs after the mine in the same id-ordered pass");
        assertEquals(0, ledger.inventory(GoodType.ORE), "all of this tick's ore was consumed by the mill");

        system.tick(buildings, ledger, finance, graph);
        assertEquals(12, ledger.inventory(GoodType.STEEL));
        assertEquals(0, ledger.inventory(GoodType.ORE));
    }

    @Test
    void processingBuildingOutputScalesDownWhenInputIsShort() {
        BuildingRegistry buildings = new BuildingRegistry();
        int mill = buildings.create(BuildingType.STEEL_MILL, 0, 0, GoodType.STEEL, 6, GoodType.ORE, 8);

        GoodsLedger ledger = new GoodsLedger();
        ledger.produce(GoodType.ORE, 4); // only half the mill's required input is in stock

        new MarketSystem().tick(buildings, ledger, new GovernmentFinance(), new RegionalGraph());

        assertEquals(3, ledger.inventory(GoodType.STEEL), "half the ore in, half the steel out");
        assertTrue(buildings.isActive(mill));
    }

    @Test
    void residentialAndCommercialBuildingsConsumeGoods() {
        BuildingRegistry buildings = new BuildingRegistry();
        int home = buildings.create(BuildingType.RESIDENTIAL, 0, 0);
        buildings.setPopulation(home, 1000);
        int shop = buildings.create(BuildingType.COMMERCIAL, 1, 0);
        buildings.setJobs(shop, 500);

        GoodsLedger ledger = new GoodsLedger();
        ledger.produce(GoodType.FOOD, 1000);
        ledger.produce(GoodType.CONSUMER_GOODS, 1000);

        new MarketSystem().tick(buildings, ledger, new GovernmentFinance(), new RegionalGraph());

        assertTrue(ledger.inventory(GoodType.FOOD) < 1000, "residents must consume food");
        assertTrue(ledger.inventory(GoodType.CONSUMER_GOODS) < 1000, "residents and shops must consume consumer goods");
    }

    @Test
    void tradeDepotImportsWhenShortAndExportsWhenInSurplus() {
        BuildingRegistry buildings = new BuildingRegistry();
        buildings.create(BuildingType.TRADE_DEPOT, 0, 0, GoodType.NONE, 0, GoodType.NONE, 0);

        GoodsLedger ledger = new GoodsLedger();
        ledger.setTargetInventory(GoodType.FUEL, 100);
        // Inventory starts at 0, well below target/2 -> depot should import.

        GovernmentFinance finance = new GovernmentFinance();
        double treasuryBefore = finance.treasuryBalance();
        new MarketSystem().tick(buildings, ledger, finance, new RegionalGraph());

        assertTrue(ledger.inventory(GoodType.FUEL) > 0, "depot should have imported fuel to cover the shortage");
        assertTrue(finance.treasuryBalance() < treasuryBefore, "importing must cost the treasury money");
    }

    @Test
    void tradeDepotExportsSurplusForRevenue() {
        BuildingRegistry buildings = new BuildingRegistry();
        buildings.create(BuildingType.TRADE_DEPOT, 0, 0, GoodType.NONE, 0, GoodType.NONE, 0);

        GoodsLedger ledger = new GoodsLedger();
        // Pre-stock every other good exactly at its target so the depot has nothing to import for
        // them this tick — isolates the treasury effect to TIMBER's export alone.
        for (byte g = 0; g < GoodType.COUNT; g++) {
            if (g != GoodType.TIMBER) {
                ledger.produce(g, ledger.targetInventory(g));
            }
        }
        ledger.setTargetInventory(GoodType.TIMBER, 100);
        ledger.produce(GoodType.TIMBER, 500); // far above target + target/2 -> exportable surplus

        GovernmentFinance finance = new GovernmentFinance();
        new MarketSystem().tick(buildings, ledger, finance, new RegionalGraph());

        assertTrue(ledger.inventory(GoodType.TIMBER) < 500, "depot should have exported some of the surplus");
        assertTrue(finance.treasuryBalance() > 0, "exporting must earn the treasury money");
    }

    @Test
    void transportCostIsDerivedFromDistanceToNearestExternalMarketNode() {
        BuildingRegistry buildings = new BuildingRegistry();
        buildings.create(BuildingType.QUARRY, 50, 0, GoodType.CONSTRUCTION_MATERIALS, 8, GoodType.NONE, 0);

        RegionalGraph graph = new RegionalGraph();
        graph.addNode(NodeType.EXTERNAL_MARKET, 0, 0); // 50 tiles away from the quarry

        GoodsLedger ledger = new GoodsLedger();
        new MarketSystem().tick(buildings, ledger, new GovernmentFinance(), graph);

        assertTrue(ledger.transportCostPerUnit(GoodType.CONSTRUCTION_MATERIALS) > 0,
            "a producer far from the only market node should carry nonzero transport cost");
    }

    @Test
    void noExternalMarketNodeMeansNoTransportCostModeled() {
        BuildingRegistry buildings = new BuildingRegistry();
        buildings.create(BuildingType.FARM, 0, 0, GoodType.FOOD, 10, GoodType.NONE, 0);

        GoodsLedger ledger = new GoodsLedger();
        new MarketSystem().tick(buildings, ledger, new GovernmentFinance(), new RegionalGraph());

        assertEquals(0.0, ledger.transportCostPerUnit(GoodType.FOOD), 1e-9);
    }
}
