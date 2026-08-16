package com.kosmos.atlas.sim.economy;

import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.trade.ShipmentKind;
import com.kosmos.atlas.sim.trade.ShipmentRegistry;
import com.kosmos.atlas.sim.trade.ShipmentSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of {@link MarketSystem#tick} — production, household/business consumption,
 * trade-depot shipments, and the transport-cost-affects-price loop (spec §14, §20, §21, §29), all
 * driven the way {@code WorldManager}'s scheduler actually calls it.
 */
class MarketSystemTest {

    @Test
    void extractionBuildingProducesWithoutNeedingInput() {
        BuildingRegistry buildings = new BuildingRegistry();
        buildings.create(BuildingType.FARM, 0, 0, GoodType.FOOD, 10, GoodType.NONE, 0);

        GoodsLedger ledger = new GoodsLedger();
        tick(buildings, ledger, new GovernmentFinance(), new RegionalGraph(), new ShipmentRegistry(), 0);

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
        ShipmentRegistry shipments = new ShipmentRegistry();

        system.tick(buildings, ledger, finance, graph, shipments, 0);
        assertEquals(6, ledger.inventory(GoodType.STEEL), "the mill runs after the mine in the same id-ordered pass");
        assertEquals(0, ledger.inventory(GoodType.ORE), "all of this tick's ore was consumed by the mill");

        system.tick(buildings, ledger, finance, graph, shipments, 1);
        assertEquals(12, ledger.inventory(GoodType.STEEL));
        assertEquals(0, ledger.inventory(GoodType.ORE));
    }

    @Test
    void processingBuildingOutputScalesDownWhenInputIsShort() {
        BuildingRegistry buildings = new BuildingRegistry();
        int mill = buildings.create(BuildingType.STEEL_MILL, 0, 0, GoodType.STEEL, 6, GoodType.ORE, 8);

        GoodsLedger ledger = new GoodsLedger();
        ledger.produce(GoodType.ORE, 4); // only half the mill's required input is in stock

        tick(buildings, ledger, new GovernmentFinance(), new RegionalGraph(), new ShipmentRegistry(), 0);

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

        tick(buildings, ledger, new GovernmentFinance(), new RegionalGraph(), new ShipmentRegistry(), 0);

        assertTrue(ledger.inventory(GoodType.FOOD) < 1000, "residents must consume food");
        assertTrue(ledger.inventory(GoodType.CONSUMER_GOODS) < 1000, "residents and shops must consume consumer goods");
    }

    @Test
    void tradeDepotShortageDepartsAnImportShipmentAndPaysImmediately() {
        BuildingRegistry buildings = new BuildingRegistry();
        int depot = buildings.create(BuildingType.TRADE_DEPOT, 0, 0, GoodType.NONE, 0, GoodType.NONE, 0);

        GoodsLedger ledger = new GoodsLedger();
        // Pre-stock every other good at its target so only FUEL triggers a shipment this tick —
        // isolates the "one shipment" assertion from the depot's per-tick concurrency cap.
        for (byte g = 0; g < GoodType.COUNT; g++) {
            if (g != GoodType.FUEL) {
                ledger.produce(g, ledger.targetInventory(g));
            }
        }
        ledger.setTargetInventory(GoodType.FUEL, 100);
        // Inventory starts at 0, well below target/2 -> depot should depart an import shipment.

        GovernmentFinance finance = new GovernmentFinance();
        ShipmentRegistry shipments = new ShipmentRegistry();
        new MarketSystem().tick(buildings, ledger, finance, new RegionalGraph(), shipments, 0);

        assertEquals(0, ledger.inventory(GoodType.FUEL), "goods aren't in inventory until the shipment arrives");
        assertTrue(finance.treasuryBalance() < 0, "importing is paid for at departure, not on arrival");
        assertEquals(1, shipments.countActiveForDepot(depot));
    }

    @Test
    void tradeDepotSurplusDepartsAnExportShipmentImmediatelyButPaysOnArrival() {
        BuildingRegistry buildings = new BuildingRegistry();
        int depot = buildings.create(BuildingType.TRADE_DEPOT, 0, 0, GoodType.NONE, 0, GoodType.NONE, 0);

        GoodsLedger ledger = new GoodsLedger();
        // Pre-stock every other good at its target so only TIMBER trades this tick.
        for (byte g = 0; g < GoodType.COUNT; g++) {
            if (g != GoodType.TIMBER) {
                ledger.produce(g, ledger.targetInventory(g));
            }
        }
        ledger.setTargetInventory(GoodType.TIMBER, 100);
        ledger.produce(GoodType.TIMBER, 500); // far above target + target/2 -> exportable surplus

        GovernmentFinance finance = new GovernmentFinance();
        ShipmentRegistry shipments = new ShipmentRegistry();
        new MarketSystem().tick(buildings, ledger, finance, new RegionalGraph(), shipments, 0);

        assertTrue(ledger.inventory(GoodType.TIMBER) < 500, "exported goods leave inventory immediately at departure");
        assertEquals(0.0, finance.treasuryBalance(), 1e-9, "export revenue isn't paid until the shipment arrives");
        assertEquals(1, shipments.countActiveForDepot(depot));
    }

    @Test
    void shipmentSystemSettlesImportAndExportOnArrival() {
        GoodsLedger ledger = new GoodsLedger();
        GovernmentFinance finance = new GovernmentFinance();
        ledger.setBasePrice(GoodType.STEEL, 20.0);
        ledger.repriceAll();
        ShipmentRegistry shipments = new ShipmentRegistry();

        shipments.create(ShipmentKind.IMPORT, GoodType.ORE, 50, 1, 0, 10);
        shipments.create(ShipmentKind.EXPORT, GoodType.STEEL, 30, 1, 0, 10);

        ShipmentSystem system = new ShipmentSystem();
        system.tick(5, shipments, ledger, finance); // before ETA — nothing should settle yet
        assertEquals(0, ledger.inventory(GoodType.ORE));
        assertEquals(0.0, finance.treasuryBalance(), 1e-9);
        assertEquals(2, shipments.activeCount());

        system.tick(10, shipments, ledger, finance); // at ETA — both settle
        assertEquals(50, ledger.inventory(GoodType.ORE), "import lands in inventory on arrival");
        assertTrue(finance.treasuryBalance() > 0, "export revenue is paid on arrival");
        assertEquals(0, shipments.activeCount());
    }

    @Test
    void depotConcurrentShipmentCapCreatesABottleneck() {
        BuildingRegistry buildings = new BuildingRegistry();
        int depot = buildings.create(BuildingType.TRADE_DEPOT, 0, 0, GoodType.NONE, 0, GoodType.NONE, 0);

        GoodsLedger ledger = new GoodsLedger();
        // Every good starts at 0, well under target/2 -> all 8 would want to import at once.
        GovernmentFinance finance = new GovernmentFinance();
        ShipmentRegistry shipments = new ShipmentRegistry();
        new MarketSystem().tick(buildings, ledger, finance, new RegionalGraph(), shipments, 0);

        assertTrue(shipments.countActiveForDepot(depot) <= 3,
            "a depot must not depart more than its concurrent-shipment cap in one tick (spec §17 bottleneck)");
    }

    @Test
    void transportCostIsDerivedFromDistanceToNearestExternalMarketNode() {
        BuildingRegistry buildings = new BuildingRegistry();
        buildings.create(BuildingType.QUARRY, 50, 0, GoodType.CONSTRUCTION_MATERIALS, 8, GoodType.NONE, 0);

        RegionalGraph graph = new RegionalGraph();
        graph.addNode(NodeType.EXTERNAL_MARKET, 0, 0); // 50 tiles away from the quarry

        GoodsLedger ledger = new GoodsLedger();
        tick(buildings, ledger, new GovernmentFinance(), graph, new ShipmentRegistry(), 0);

        assertTrue(ledger.transportCostPerUnit(GoodType.CONSTRUCTION_MATERIALS) > 0,
            "a producer far from the only market node should carry nonzero transport cost");
    }

    @Test
    void noExternalMarketNodeMeansNoTransportCostModeled() {
        BuildingRegistry buildings = new BuildingRegistry();
        buildings.create(BuildingType.FARM, 0, 0, GoodType.FOOD, 10, GoodType.NONE, 0);

        GoodsLedger ledger = new GoodsLedger();
        tick(buildings, ledger, new GovernmentFinance(), new RegionalGraph(), new ShipmentRegistry(), 0);

        assertEquals(0.0, ledger.transportCostPerUnit(GoodType.FOOD), 1e-9);
    }

    private static void tick(BuildingRegistry buildings, GoodsLedger ledger, GovernmentFinance finance,
                              RegionalGraph graph, ShipmentRegistry shipments, long currentTick) {
        new MarketSystem().tick(buildings, ledger, finance, graph, shipments, currentTick);
    }
}
