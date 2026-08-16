package com.kosmos.atlas.sim.economy;

/**
 * City-wide {@code Production[Good]/Demand[Good]/Inventory[Good]/Price[Good]} (spec §20). Fase 2
 * never introduced districts or multiple cities, so this ledger is a single city-wide instance —
 * one flat array per field, sized {@link GoodType#COUNT}, indexed directly by the good's byte
 * constant. No per-building bookkeeping lives here; {@code MarketSystem} aggregates
 * {@code BuildingRegistry}'s per-building production/consumption into these totals each tick.
 *
 * <p>Price follows the simplified rule spec §20 asks for — supply relative to a per-good target
 * inventory, not a real supply/demand curve: inventory at or above target settles near
 * {@link #basePrice}, inventory near zero rises toward roughly double base price.
 */
public final class GoodsLedger {

    private static final double MIN_PRICE_MULTIPLIER = 1.0;
    private static final double MAX_PRICE_MULTIPLIER = 2.0;

    /**
     * Starting {@link #basePrice} per good, indexed by {@link GoodType}'s byte constants — round
     * numbers reflecting production-chain depth rather than a real commodity market (spec §20's
     * "understandable rather than hyper-realistic"): extracted raw goods (Food/Timber/Ore/Fuel/
     * ConstructionMaterials — {@code BuildProductionBuildingCommand}'s Farm/Lumber Camp/Mine/
     * Quarry) sit low; Steel costs more because refining it from Ore is lossy (8 Ore in -> 6 Steel
     * out, {@code MarketSystem}'s Steel Mill ratio); ConsumerGoods/Machinery have no domestic
     * producer at all yet (only importable through a Trade Depot/Port), priced highest to reflect
     * that scarcity until a later phase adds a factory building for them.
     */
    private static final double[] DEFAULT_BASE_PRICE_BY_GOOD = {
        8.0,  // FOOD
        9.0,  // TIMBER
        14.0, // ORE
        28.0, // STEEL
        16.0, // FUEL
        32.0, // CONSUMER_GOODS
        45.0, // MACHINERY
        12.0, // CONSTRUCTION_MATERIALS
    };

    private final int[] inventory = new int[GoodType.COUNT];
    private final int[] producedLastTick = new int[GoodType.COUNT];
    private final int[] consumedLastTick = new int[GoodType.COUNT];
    private final int[] shortageLastTick = new int[GoodType.COUNT]; // demand that inventory couldn't satisfy
    private final int[] importedLastTick = new int[GoodType.COUNT];
    private final int[] exportedLastTick = new int[GoodType.COUNT];
    private final double[] price = new double[GoodType.COUNT];
    private final double[] basePrice = new double[GoodType.COUNT];
    private final int[] targetInventory = new int[GoodType.COUNT];
    /** Set by {@code MarketSystem} from average producer-to-market distance (spec §20: "Transport
     *  cost contributes to final price"); subtracted from price in {@link #repriceAll()}. */
    private final double[] transportCostPerUnit = new double[GoodType.COUNT];

    public GoodsLedger() {
        for (int g = 0; g < GoodType.COUNT; g++) {
            basePrice[g] = DEFAULT_BASE_PRICE_BY_GOOD[g];
            price[g] = basePrice[g];
            targetInventory[g] = 200;
        }
    }

    public int inventory(byte good) {
        return inventory[good];
    }

    public double price(byte good) {
        return price[good];
    }

    public int producedLastTick(byte good) {
        return producedLastTick[good];
    }

    public int consumedLastTick(byte good) {
        return consumedLastTick[good];
    }

    public int shortageLastTick(byte good) {
        return shortageLastTick[good];
    }

    public double basePrice(byte good) {
        return basePrice[good];
    }

    public void setBasePrice(byte good, double value) {
        basePrice[good] = value;
    }

    /**
     * Directly sets inventory during a save load — {@code GoodsLedgerIO} restores the
     * authoritative stock level this way, then calls {@link #repriceAll()} once to derive prices
     * rather than persisting {@link #price(byte)} itself (it's fully recomputable, same reasoning
     * as {@code Chunk.serviceFlags} not needing to be authoritative state).
     */
    public void setInventory(byte good, int value) {
        inventory[good] = Math.max(0, value);
    }

    public int targetInventory(byte good) {
        return targetInventory[good];
    }

    public void setTargetInventory(byte good, int value) {
        targetInventory[good] = Math.max(1, value);
    }

    public void setTransportCostPerUnit(byte good, double costPerUnit) {
        transportCostPerUnit[good] = Math.max(0.0, costPerUnit);
    }

    public double transportCostPerUnit(byte good) {
        return transportCostPerUnit[good];
    }

    /** Adds freshly produced units to inventory and this tick's production tally. */
    public void produce(byte good, int amount) {
        inventory[good] += amount;
        producedLastTick[good] += amount;
    }

    /**
     * Consumes up to {@code amount} units from inventory; returns how much was actually consumed
     * (less than requested if inventory ran short — the shortfall is tracked for the price signal
     * and for callers, e.g. a mill that can't run at full rate without enough input stock).
     */
    public int consume(byte good, int amount) {
        int available = Math.min(amount, inventory[good]);
        inventory[good] -= available;
        consumedLastTick[good] += available;
        shortageLastTick[good] += amount - available;
        return available;
    }

    /**
     * Adds imported units to inventory (spec §29 external market), tracked separately from
     * domestic {@link #produce}. Returns the amount actually imported (capped by {@code amount}
     * itself — no inventory ceiling on imports; that's the {@code TradeDepot}'s own capacity's job).
     */
    public int importGood(byte good, int amount) {
        inventory[good] += amount;
        importedLastTick[good] += amount;
        return amount;
    }

    /** Removes up to {@code amount} exportable units from inventory; returns how much was actually exported. */
    public int exportGood(byte good, int amount) {
        int available = Math.min(amount, inventory[good]);
        inventory[good] -= available;
        exportedLastTick[good] += available;
        return available;
    }

    public int importedLastTick(byte good) {
        return importedLastTick[good];
    }

    public int exportedLastTick(byte good) {
        return exportedLastTick[good];
    }

    /** Zeroes the per-tick tallies; call once at the start of each {@code MarketSystem.tick}. */
    public void beginTick() {
        java.util.Arrays.fill(producedLastTick, 0);
        java.util.Arrays.fill(consumedLastTick, 0);
        java.util.Arrays.fill(shortageLastTick, 0);
        java.util.Arrays.fill(importedLastTick, 0);
        java.util.Arrays.fill(exportedLastTick, 0);
    }

    /** Recomputes {@link #price(byte)} for every good from current inventory vs. its target. */
    public void repriceAll() {
        for (byte g = 0; g < GoodType.COUNT; g++) {
            double ratio = (double) inventory[g] / targetInventory[g];
            double clamped = Math.max(0.0, Math.min(1.0, ratio));
            // ratio 0 (empty) -> MAX multiplier; ratio >= 1 (at/above target) -> MIN multiplier.
            double multiplier = MAX_PRICE_MULTIPLIER - clamped * (MAX_PRICE_MULTIPLIER - MIN_PRICE_MULTIPLIER);
            price[g] = Math.max(0.0, basePrice[g] * multiplier - transportCostPerUnit[g]);
        }
    }
}
