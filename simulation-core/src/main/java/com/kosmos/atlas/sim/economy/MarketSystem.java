package com.kosmos.atlas.sim.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.AirportRegistry;
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.PortRegistry;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.trade.ShipmentKind;
import com.kosmos.atlas.sim.trade.ShipmentRegistry;
import com.kosmos.atlas.sim.trade.StationRegistry;

/**
 * Drives the {@code Production -> Logistics -> Consumption} loop spec §20 asks for, once per
 * active city (spec §9: multiple player-founded cities each have their own
 * {@code Production[Good]/Demand[Good]/Inventory[Good]/Price[Good]}, per spec §20's model applied
 * per city rather than per world). Runs on the {@code WorldManager} scheduler at a low cadence,
 * same tier as {@link GovernmentFinanceSystem}.
 *
 * <p>Per city, one tick, in order:
 * <ol>
 *   <li>that city's production buildings turn input goods (if any) into output goods, in
 *       {@code BuildingRegistry} id order — a building fed by another one created earlier (a
 *       lower id) sees that tick's fresh output; one created later sees only what was already in
 *       inventory before this tick. This is a deliberate simplification rather than trying to
 *       model true same-tick delivery ordering independent of creation order (spec §20's
 *       "understandable rather than hyper-realistic") — see {@code MarketSystemTest} for the
 *       exact id-order semantics;</li>
 *   <li>that city's residential/commercial/industrial buildings consume goods proportional to
 *       population/jobs;</li>
 *   <li>every active {@code TradeDepot} belonging to that city that's running short on a good or
 *       sitting on a surplus creates a {@code Shipment} (spec §14) rather than trading instantly —
 *       see {@code ShipmentSystem} for when the goods/money actually move — up to
 *       {@link #MAX_CONCURRENT_SHIPMENTS_PER_DEPOT} shipments in flight per depot at once
 *       (spec §17's bottleneck example: demand beyond capacity delays cargo rather than being
 *       silently unlimited);</li>
 *   <li>transport cost per good is re-derived from the average distance of that city's producers
 *       to the nearest external-market node (spec §20: "transport cost contributes to final
 *       price"), and prices are recomputed from the resulting inventory levels.</li>
 * </ol>
 */
public final class MarketSystem {

    private static final int TRADE_DEPOT_CAPACITY_PER_TICK = 25;
    private static final double TRANSPORT_COST_PER_TILE = 0.01;
    /** Ticks between a shipment departing a depot and arriving (spec §14's departure_time/ETA). */
    private static final long SHIPMENT_TRAVEL_TICKS = 20;
    private static final int MAX_CONCURRENT_SHIPMENTS_PER_DEPOT = 3;
    /** A Port's customs efficiency (0-100%) scales up to this fractional discount/premium on trades. */
    private static final double PORT_CUSTOMS_MAX_BONUS = 0.10;

    // Household/business consumption rates (units per tick per resident/job) — spec §20 simplification.
    private static final double FOOD_PER_RESIDENT = 0.05;
    private static final double CONSUMER_GOODS_PER_RESIDENT = 0.02;
    private static final double CONSUMER_GOODS_PER_COMMERCIAL_JOB = 0.08;
    private static final double STEEL_PER_INDUSTRIAL_JOB = 0.03;
    private static final double CONSTRUCTION_MATERIALS_PER_INDUSTRIAL_JOB = 0.03;

    public void tick(BuildingRegistry buildings, CityRegistry cities, RegionalGraph graph,
                      ShipmentRegistry shipments, PortRegistry ports, AirportRegistry airports,
                      StationRegistry stations, long currentTick) {
        cities.forEachActive(cityId -> tickOneCity(buildings, cities, cityId, graph, shipments, ports, airports, stations, currentTick));
    }

    private void tickOneCity(BuildingRegistry buildings, CityRegistry cities, int cityId, RegionalGraph graph,
                              ShipmentRegistry shipments, PortRegistry ports, AirportRegistry airports,
                              StationRegistry stations, long currentTick) {
        GoodsLedger ledger = cities.ledger(cityId);
        ledger.beginTick();
        runProduction(buildings, cityId, ledger);
        runConsumption(buildings, cityId, ledger);
        runGateways(buildings, cityId, ledger, cities.finance(cityId), shipments, ports, airports, stations, currentTick);
        updateTransportCosts(buildings, cityId, ledger, graph);
        ledger.repriceAll();
    }

    private void runProduction(BuildingRegistry buildings, int cityId, GoodsLedger ledger) {
        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id) || buildings.cityId(id) != cityId) {
                continue;
            }
            byte output = buildings.outputGood(id);
            if (output == GoodType.NONE) {
                continue;
            }
            byte input = buildings.inputGood(id);
            int outputRate = buildings.outputRatePerTick(id);
            if (input == GoodType.NONE) {
                ledger.produce(output, outputRate);
                continue;
            }
            int inputRate = buildings.inputRatePerTick(id);
            int actualInput = ledger.consume(input, inputRate);
            int actualOutput = inputRate > 0 ? (int) ((long) outputRate * actualInput / inputRate) : 0;
            ledger.produce(output, actualOutput);
        }
    }

    private void runConsumption(BuildingRegistry buildings, int cityId, GoodsLedger ledger) {
        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id) || buildings.cityId(id) != cityId) {
                continue;
            }
            switch (buildings.type(id)) {
                case BuildingType.RESIDENTIAL -> {
                    int population = buildings.population(id);
                    ledger.consume(GoodType.FOOD, (int) Math.round(population * FOOD_PER_RESIDENT));
                    ledger.consume(GoodType.CONSUMER_GOODS, (int) Math.round(population * CONSUMER_GOODS_PER_RESIDENT));
                }
                case BuildingType.COMMERCIAL -> {
                    int jobs = buildings.jobs(id);
                    ledger.consume(GoodType.CONSUMER_GOODS, (int) Math.round(jobs * CONSUMER_GOODS_PER_COMMERCIAL_JOB));
                }
                case BuildingType.INDUSTRIAL -> {
                    int jobs = buildings.jobs(id);
                    ledger.consume(GoodType.STEEL, (int) Math.round(jobs * STEEL_PER_INDUSTRIAL_JOB));
                    ledger.consume(GoodType.CONSTRUCTION_MATERIALS, (int) Math.round(jobs * CONSTRUCTION_MATERIALS_PER_INDUSTRIAL_JOB));
                }
                default -> { /* utility/production buildings don't consume household goods */ }
            }
        }
    }

    /** Trade Depot, Port, Airport and Rail Terminal buildings all trade through this loop — a Port,
     *  Airport or Rail Terminal simply carries its own per-tick capacity/concurrency cap/customs
     *  bonus via its own registry instead of the flat Trade Depot constants (spec §17's
     *  higher-capacity coastal gateway; §19's landlocked, population-gated one; §18's domestic bulk
     *  freight one, which has no customs bonus at all — see the Rail branch below). */
    private void runGateways(BuildingRegistry buildings, int cityId, GoodsLedger ledger, GovernmentFinance finance,
                              ShipmentRegistry shipments, PortRegistry ports, AirportRegistry airports,
                              StationRegistry stations, long currentTick) {
        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id) || buildings.cityId(id) != cityId) {
                continue;
            }
            byte type = buildings.type(id);
            if (type != BuildingType.TRADE_DEPOT && type != BuildingType.PORT
                && type != BuildingType.AIRPORT && type != BuildingType.RAIL_TERMINAL) {
                continue;
            }
            boolean isPort = type == BuildingType.PORT && ports != null && ports.hasPort(id);
            boolean isAirport = type == BuildingType.AIRPORT && airports != null && airports.hasAirport(id);
            boolean isStation = type == BuildingType.RAIL_TERMINAL && stations != null && stations.hasTerminal(id);
            int concurrentCap = isPort ? ports.berths(id) : isAirport ? airports.gates(id)
                : isStation ? stations.platforms(id) : MAX_CONCURRENT_SHIPMENTS_PER_DEPOT;
            int capacityPerTick = isPort ? ports.cargoCapacityPerTick(id)
                : isAirport ? airports.cargoCapacityPerTick(id)
                : isStation ? stations.cargoCapacityPerTick(id) : TRADE_DEPOT_CAPACITY_PER_TICK;
            // Rail is domestic trade between player-founded cities, not across a world border —
            // no customs bonus applies, unlike Port/Airport.
            double customsBonus = isPort ? (ports.customsEfficiencyPercent(id) / 100.0) * PORT_CUSTOMS_MAX_BONUS
                : isAirport ? (airports.customsEfficiencyPercent(id) / 100.0) * PORT_CUSTOMS_MAX_BONUS : 0.0;

            if (shipments.countActiveForDepot(id) >= concurrentCap) {
                continue; // gateway is at capacity this tick — every good it needs to trade waits
            }
            for (byte good = 0; good < GoodType.COUNT; good++) {
                if (shipments.countActiveForDepot(id) >= concurrentCap) {
                    break; // filled the gateway's remaining slots partway through the good list
                }
                tradeOneGood(ledger, finance, shipments, id, cityId, currentTick, good, capacityPerTick, customsBonus);
            }
        }
    }

    /**
     * Departs (but does not yet settle) a shipment for one good at one gateway, spec §14/§15's
     * origin/destination/commodity/quantity/departure_time/ETA. An import is paid for now but the
     * goods only land in inventory when {@code ShipmentSystem} completes it at its ETA; an export
     * leaves inventory now but the treasury is only paid on arrival — see {@code ShipmentSystem}'s
     * javadoc for why the two sides of a trade are split that way. {@code customsBonus} is a
     * Port-only fractional discount on import cost / premium on export revenue (0 for Trade Depots).
     */
    private void tradeOneGood(GoodsLedger ledger, GovernmentFinance finance, ShipmentRegistry shipments,
                               int gatewayId, int cityId, long currentTick, byte good, int capacityPerTick, double customsBonus) {
        int inventory = ledger.inventory(good);
        int target = ledger.targetInventory(good);
        long eta = currentTick + SHIPMENT_TRAVEL_TICKS;
        if (inventory < target / 2) {
            int imported = Math.min(target / 2 - inventory, capacityPerTick);
            if (imported <= 0) {
                return;
            }
            finance.adjustTreasury(-imported * ledger.price(good) * (1.0 - customsBonus));
            shipments.create(ShipmentKind.IMPORT, good, imported, gatewayId, cityId, currentTick, eta);
        } else if (inventory > target + target / 2) {
            int exported = ledger.exportGood(good, Math.min(inventory - (target + target / 2), capacityPerTick));
            if (exported <= 0) {
                return;
            }
            shipments.create(ShipmentKind.EXPORT, good, exported, gatewayId, cityId, currentTick, eta);
        }
    }

    private void updateTransportCosts(BuildingRegistry buildings, int cityId, GoodsLedger ledger, RegionalGraph graph) {
        long[] distanceSum = new long[GoodType.COUNT];
        int[] producerCount = new int[GoodType.COUNT];

        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id) || buildings.cityId(id) != cityId) {
                continue;
            }
            byte output = buildings.outputGood(id);
            if (output == GoodType.NONE) {
                continue;
            }
            int marketNode = graph.nearestNodeOfType(buildings.tileX(id), buildings.tileY(id), NodeType.EXTERNAL_MARKET);
            if (marketNode < 0) {
                continue; // no gateway built yet — no priced friction to model
            }
            double distance = graph.distanceTiles(marketNode, buildings.tileX(id), buildings.tileY(id));
            distanceSum[output] += Math.round(distance);
            producerCount[output]++;
        }

        for (byte good = 0; good < GoodType.COUNT; good++) {
            double avgDistance = producerCount[good] > 0 ? (double) distanceSum[good] / producerCount[good] : 0.0;
            ledger.setTransportCostPerUnit(good, avgDistance * TRANSPORT_COST_PER_TILE);
        }
    }
}
