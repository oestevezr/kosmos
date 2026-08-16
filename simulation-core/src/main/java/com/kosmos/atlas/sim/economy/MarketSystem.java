package com.kosmos.atlas.sim.economy;

import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.RegionalGraph;

/**
 * Drives the {@code Production -> Logistics -> Consumption} loop spec §20 asks for, at
 * city-wide granularity (spec §22 "primarily at building or district level" applied to goods,
 * not just population). Runs on the {@code WorldManager} scheduler at a low cadence, same tier as
 * {@link GovernmentFinanceSystem}.
 *
 * <p>One tick, in order:
 * <ol>
 *   <li>production buildings turn input goods (if any) into output goods, in {@code BuildingRegistry}
 *       id order — a building fed by another one created earlier (a lower id) sees that tick's
 *       fresh output; one created later sees only what was already in inventory before this tick.
 *       This is a deliberate simplification rather than trying to model true same-tick delivery
 *       ordering independent of creation order (spec §20's "understandable rather than
 *       hyper-realistic") — see {@code MarketSystemTest} for the exact id-order semantics;</li>
 *   <li>residential/commercial/industrial buildings consume goods proportional to
 *       population/jobs;</li>
 *   <li>every active {@code TradeDepot} imports goods running short and exports goods in surplus,
 *       up to its own capacity, moving currency through {@link GovernmentFinance#adjustTreasury}
 *       (spec §29: the external market has its own commodity prices);</li>
 *   <li>transport cost per good is re-derived from the average distance of that good's producers
 *       to the nearest external-market node (spec §20: "transport cost contributes to final
 *       price"), and prices are recomputed from the resulting inventory levels.</li>
 * </ol>
 */
public final class MarketSystem {

    private static final int TRADE_DEPOT_CAPACITY_PER_TICK = 25;
    private static final double TRANSPORT_COST_PER_TILE = 0.01;

    // Household/business consumption rates (units per tick per resident/job) — spec §20 simplification.
    private static final double FOOD_PER_RESIDENT = 0.05;
    private static final double CONSUMER_GOODS_PER_RESIDENT = 0.02;
    private static final double CONSUMER_GOODS_PER_COMMERCIAL_JOB = 0.08;
    private static final double STEEL_PER_INDUSTRIAL_JOB = 0.03;
    private static final double CONSTRUCTION_MATERIALS_PER_INDUSTRIAL_JOB = 0.03;

    public void tick(BuildingRegistry buildings, GoodsLedger ledger, GovernmentFinance finance, RegionalGraph graph) {
        ledger.beginTick();
        runProduction(buildings, ledger);
        runConsumption(buildings, ledger);
        runTradeDepots(buildings, ledger, finance);
        updateTransportCosts(buildings, ledger, graph);
        ledger.repriceAll();
    }

    private void runProduction(BuildingRegistry buildings, GoodsLedger ledger) {
        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id)) {
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

    private void runConsumption(BuildingRegistry buildings, GoodsLedger ledger) {
        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id)) {
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

    private void runTradeDepots(BuildingRegistry buildings, GoodsLedger ledger, GovernmentFinance finance) {
        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id) || buildings.type(id) != BuildingType.TRADE_DEPOT) {
                continue;
            }
            for (byte good = 0; good < GoodType.COUNT; good++) {
                tradeOneGood(ledger, finance, good);
            }
        }
    }

    private void tradeOneGood(GoodsLedger ledger, GovernmentFinance finance, byte good) {
        int inventory = ledger.inventory(good);
        int target = ledger.targetInventory(good);
        if (inventory < target / 2) {
            int need = target / 2 - inventory;
            int imported = ledger.importGood(good, Math.min(need, TRADE_DEPOT_CAPACITY_PER_TICK));
            finance.adjustTreasury(-imported * ledger.price(good));
        } else if (inventory > target + target / 2) {
            int surplus = inventory - (target + target / 2);
            int exported = ledger.exportGood(good, Math.min(surplus, TRADE_DEPOT_CAPACITY_PER_TICK));
            finance.adjustTreasury(exported * ledger.price(good) * 0.9); // sell at a small discount to base price
        }
    }

    private void updateTransportCosts(BuildingRegistry buildings, GoodsLedger ledger, RegionalGraph graph) {
        long[] distanceSum = new long[GoodType.COUNT];
        int[] producerCount = new int[GoodType.COUNT];

        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id)) {
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
