package com.kosmos.atlas.sim.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.PortRegistry;
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

    private static CityRegistry oneCity() {
        CityRegistry cities = new CityRegistry();
        cities.create("Testville", 0, 0, 0);
        return cities;
    }

    @Test
    void extractionBuildingProducesWithoutNeedingInput() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = oneCity();
        buildings.create(BuildingType.FARM, 0, 0, 1, GoodType.FOOD, 10, GoodType.NONE, 0);

        tick(buildings, cities, new RegionalGraph(), new ShipmentRegistry(), 0);

        assertEquals(10, cities.ledger(1).inventory(GoodType.FOOD));
    }

    @Test
    void processingBuildingOutputIsGatedByAvailableInput() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = oneCity();
        // Mine is created (and so gets a lower id) before Steel Mill, and MarketSystem's
        // production pass runs in id order — so within the very same tick, the mill sees the
        // ore the mine already produced earlier in that same pass. This is the documented,
        // deterministic id-order behavior (see MarketSystem.runProduction's javadoc), not a lag.
        buildings.create(BuildingType.MINE, 0, 0, 1, GoodType.ORE, 8, GoodType.NONE, 0);
        buildings.create(BuildingType.STEEL_MILL, 1, 0, 1, GoodType.STEEL, 6, GoodType.ORE, 8);

        MarketSystem system = new MarketSystem();
        RegionalGraph graph = new RegionalGraph();
        ShipmentRegistry shipments = new ShipmentRegistry();

        system.tick(buildings, cities, graph, shipments, null, 0);
        GoodsLedger ledger = cities.ledger(1);
        assertEquals(6, ledger.inventory(GoodType.STEEL), "the mill runs after the mine in the same id-ordered pass");
        assertEquals(0, ledger.inventory(GoodType.ORE), "all of this tick's ore was consumed by the mill");

        system.tick(buildings, cities, graph, shipments, null, 1);
        assertEquals(12, ledger.inventory(GoodType.STEEL));
        assertEquals(0, ledger.inventory(GoodType.ORE));
    }

    @Test
    void processingBuildingOutputScalesDownWhenInputIsShort() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = oneCity();
        int mill = buildings.create(BuildingType.STEEL_MILL, 0, 0, 1, GoodType.STEEL, 6, GoodType.ORE, 8);

        cities.ledger(1).produce(GoodType.ORE, 4); // only half the mill's required input is in stock

        tick(buildings, cities, new RegionalGraph(), new ShipmentRegistry(), 0);

        assertEquals(3, cities.ledger(1).inventory(GoodType.STEEL), "half the ore in, half the steel out");
        assertTrue(buildings.isActive(mill));
    }

    @Test
    void residentialAndCommercialBuildingsConsumeGoods() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = oneCity();
        int home = buildings.create(BuildingType.RESIDENTIAL, 0, 0, 1);
        buildings.setPopulation(home, 1000);
        int shop = buildings.create(BuildingType.COMMERCIAL, 1, 0, 1);
        buildings.setJobs(shop, 500);

        GoodsLedger ledger = cities.ledger(1);
        ledger.produce(GoodType.FOOD, 1000);
        ledger.produce(GoodType.CONSUMER_GOODS, 1000);

        tick(buildings, cities, new RegionalGraph(), new ShipmentRegistry(), 0);

        assertTrue(ledger.inventory(GoodType.FOOD) < 1000, "residents must consume food");
        assertTrue(ledger.inventory(GoodType.CONSUMER_GOODS) < 1000, "residents and shops must consume consumer goods");
    }

    @Test
    void tradeDepotShortageDepartsAnImportShipmentAndPaysImmediately() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = oneCity();
        int depot = buildings.create(BuildingType.TRADE_DEPOT, 0, 0, 1, GoodType.NONE, 0, GoodType.NONE, 0);

        GoodsLedger ledger = cities.ledger(1);
        // Pre-stock every other good at its target so only FUEL triggers a shipment this tick —
        // isolates the "one shipment" assertion from the depot's per-tick concurrency cap.
        for (byte g = 0; g < GoodType.COUNT; g++) {
            if (g != GoodType.FUEL) {
                ledger.produce(g, ledger.targetInventory(g));
            }
        }
        ledger.setTargetInventory(GoodType.FUEL, 100);
        // Inventory starts at 0, well below target/2 -> depot should depart an import shipment.

        ShipmentRegistry shipments = new ShipmentRegistry();
        new MarketSystem().tick(buildings, cities, new RegionalGraph(), shipments, null, 0);

        assertEquals(0, ledger.inventory(GoodType.FUEL), "goods aren't in inventory until the shipment arrives");
        assertTrue(cities.finance(1).treasuryBalance() < 0, "importing is paid for at departure, not on arrival");
        assertEquals(1, shipments.countActiveForDepot(depot));
    }

    @Test
    void tradeDepotSurplusDepartsAnExportShipmentImmediatelyButPaysOnArrival() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = oneCity();
        int depot = buildings.create(BuildingType.TRADE_DEPOT, 0, 0, 1, GoodType.NONE, 0, GoodType.NONE, 0);

        GoodsLedger ledger = cities.ledger(1);
        // Pre-stock every other good at its target so only TIMBER trades this tick.
        for (byte g = 0; g < GoodType.COUNT; g++) {
            if (g != GoodType.TIMBER) {
                ledger.produce(g, ledger.targetInventory(g));
            }
        }
        ledger.setTargetInventory(GoodType.TIMBER, 100);
        ledger.produce(GoodType.TIMBER, 500); // far above target + target/2 -> exportable surplus

        ShipmentRegistry shipments = new ShipmentRegistry();
        new MarketSystem().tick(buildings, cities, new RegionalGraph(), shipments, null, 0);

        assertTrue(ledger.inventory(GoodType.TIMBER) < 500, "exported goods leave inventory immediately at departure");
        assertEquals(0.0, cities.finance(1).treasuryBalance(), 1e-9, "export revenue isn't paid until the shipment arrives");
        assertEquals(1, shipments.countActiveForDepot(depot));
    }

    @Test
    void shipmentSystemSettlesImportAndExportOnArrival() {
        CityRegistry cities = oneCity();
        GoodsLedger ledger = cities.ledger(1);
        ledger.setBasePrice(GoodType.STEEL, 20.0);
        ledger.repriceAll();
        ShipmentRegistry shipments = new ShipmentRegistry();

        shipments.create(ShipmentKind.IMPORT, GoodType.ORE, 50, 1, 1, 0, 10);
        shipments.create(ShipmentKind.EXPORT, GoodType.STEEL, 30, 1, 1, 0, 10);

        ShipmentSystem system = new ShipmentSystem();
        system.tick(5, shipments, cities); // before ETA — nothing should settle yet
        assertEquals(0, ledger.inventory(GoodType.ORE));
        assertEquals(0.0, cities.finance(1).treasuryBalance(), 1e-9);
        assertEquals(2, shipments.activeCount());

        system.tick(10, shipments, cities); // at ETA — both settle
        assertEquals(50, ledger.inventory(GoodType.ORE), "import lands in inventory on arrival");
        assertTrue(cities.finance(1).treasuryBalance() > 0, "export revenue is paid on arrival");
        assertEquals(0, shipments.activeCount());
    }

    @Test
    void depotConcurrentShipmentCapCreatesABottleneck() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = oneCity();
        int depot = buildings.create(BuildingType.TRADE_DEPOT, 0, 0, 1, GoodType.NONE, 0, GoodType.NONE, 0);

        // Every good starts at 0, well under target/2 -> all 8 would want to import at once.
        ShipmentRegistry shipments = new ShipmentRegistry();
        new MarketSystem().tick(buildings, cities, new RegionalGraph(), shipments, null, 0);

        assertTrue(shipments.countActiveForDepot(depot) <= 3,
            "a depot must not depart more than its concurrent-shipment cap in one tick (spec §17 bottleneck)");
    }

    @Test
    void transportCostIsDerivedFromDistanceToNearestExternalMarketNode() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = oneCity();
        buildings.create(BuildingType.QUARRY, 50, 0, 1, GoodType.CONSTRUCTION_MATERIALS, 8, GoodType.NONE, 0);

        RegionalGraph graph = new RegionalGraph();
        graph.addNode(NodeType.EXTERNAL_MARKET, 0, 0); // 50 tiles away from the quarry

        tick(buildings, cities, graph, new ShipmentRegistry(), 0);

        assertTrue(cities.ledger(1).transportCostPerUnit(GoodType.CONSTRUCTION_MATERIALS) > 0,
            "a producer far from the only market node should carry nonzero transport cost");
    }

    @Test
    void portTradesWithItsOwnCapacityAndConcurrencyCapInsteadOfTradeDepotConstants() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = oneCity();
        int port = buildings.create(BuildingType.PORT, 0, 0, 1, GoodType.NONE, 0, GoodType.NONE, 0);

        PortRegistry ports = new PortRegistry();
        ports.set(port, 6, 75, 50); // berths, cargo/tick, customs efficiency %

        // Every good starts at 0, well under target/2 -> a Trade Depot would cap at 3 concurrent
        // shipments; a Port with 6 berths should be able to depart more than that in one tick.
        ShipmentRegistry shipments = new ShipmentRegistry();
        new MarketSystem().tick(buildings, cities, new RegionalGraph(), shipments, ports, 0);

        assertTrue(shipments.countActiveForDepot(port) > 3,
            "a Port's berths, not the Trade Depot concurrency cap, should bound its shipments");
    }

    @Test
    void portCustomsEfficiencyDiscountsImportCostBelowTradeDepotPrice() {
        // A low target keeps the desired import amount (10) below both gateways' per-tick
        // capacity (25 for the depot, 75 for the port), so both import the same quantity —
        // isolating the customs discount as the only variable affecting cost.
        BuildingRegistry depotBuildings = new BuildingRegistry();
        CityRegistry depotCity = oneCity();
        depotBuildings.create(BuildingType.TRADE_DEPOT, 0, 0, 1, GoodType.NONE, 0, GoodType.NONE, 0);
        stockAllGoodsExcept(depotCity.ledger(1), GoodType.FUEL);
        depotCity.ledger(1).setTargetInventory(GoodType.FUEL, 20);
        new MarketSystem().tick(depotBuildings, depotCity, new RegionalGraph(), new ShipmentRegistry(), null, 0);
        double depotCost = -depotCity.finance(1).treasuryBalance();

        BuildingRegistry portBuildings = new BuildingRegistry();
        CityRegistry portCity = oneCity();
        int port = portBuildings.create(BuildingType.PORT, 0, 0, 1, GoodType.NONE, 0, GoodType.NONE, 0);
        PortRegistry ports = new PortRegistry();
        ports.set(port, 6, 75, 50);
        stockAllGoodsExcept(portCity.ledger(1), GoodType.FUEL);
        portCity.ledger(1).setTargetInventory(GoodType.FUEL, 20);
        new MarketSystem().tick(portBuildings, portCity, new RegionalGraph(), new ShipmentRegistry(), ports, 0);
        double portCost = -portCity.finance(1).treasuryBalance();

        assertTrue(portCost < depotCost, "a Port's customs efficiency should discount import cost below the Trade Depot's");
    }

    /** Pre-stocks every good except {@code excluded} to its target so only that good trades this tick. */
    private static void stockAllGoodsExcept(GoodsLedger ledger, byte excluded) {
        for (byte g = 0; g < GoodType.COUNT; g++) {
            if (g != excluded) {
                ledger.produce(g, ledger.targetInventory(g));
            }
        }
    }

    @Test
    void noExternalMarketNodeMeansNoTransportCostModeled() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = oneCity();
        buildings.create(BuildingType.FARM, 0, 0, 1, GoodType.FOOD, 10, GoodType.NONE, 0);

        tick(buildings, cities, new RegionalGraph(), new ShipmentRegistry(), 0);

        assertEquals(0.0, cities.ledger(1).transportCostPerUnit(GoodType.FOOD), 1e-9);
    }

    private static void tick(BuildingRegistry buildings, CityRegistry cities,
                              RegionalGraph graph, ShipmentRegistry shipments, long currentTick) {
        new MarketSystem().tick(buildings, cities, graph, shipments, null, currentTick);
    }
}
