package com.kosmos.atlas.sim.city;

import com.kosmos.atlas.sim.Difficulty;
import com.kosmos.atlas.sim.economy.GoodsLedger;
import com.kosmos.atlas.sim.economy.GovernmentFinance;

import java.util.Arrays;

/**
 * The authoritative directory of player-founded cities (spec §9: "additional player-founded
 * towns; multiple municipalities; regional governance; interconnected metropolitan areas").
 * Every city owns its own {@link GovernmentFinance} (treasury, tax rates) and {@link GoodsLedger}
 * (production/consumption/inventory/price) — this is what makes "which city does this money
 * belong to" a well-defined question once more than one city exists.
 *
 * <p>Unlike {@code BuildingRegistry}/{@code Chunk}, a handful of cities is exactly the kind of
 * small, bounded count spec §42.3's aggregation hierarchy (BUILDING -&gt; DISTRICT -&gt; CITY -&gt;
 * REGION) explicitly allows one object per unit at — so each city slot owns a real
 * {@link GovernmentFinance}/{@link GoodsLedger} instance rather than flattening their fields into
 * more primitive arrays the way {@code BuildingRegistry}'s per-building columns do. This reuses
 * both classes entirely unchanged, including their existing tests.
 *
 * <p>Same growable-SoA-with-tombstone-free-list id shape as {@code BuildingRegistry}/
 * {@code RegionalGraph}: a city id (stored in {@code Chunk}-adjacent state like
 * {@code BuildingRegistry.cityId}) must stay valid across unrelated changes elsewhere.
 */
public final class CityRegistry {

    /** Two founded cities closer than this (in tiles) would make "nearest city" territory attribution meaningless. */
    public static final int MIN_FOUNDING_DISTANCE_TILES = 20;

    private String[] name;
    private int[] tileX;
    private int[] tileY;
    private long[] foundedTick;
    private boolean[] active;
    private GovernmentFinance[] finance;
    private GoodsLedger[] ledger;
    private final Difficulty difficulty;

    private int highWaterMark = 1; // id 0 reserved as "no city"
    private int activeCount;
    private int[] freeIds;
    private int freeTop;

    public CityRegistry() {
        this(Difficulty.MEDIUM, 8);
    }

    public CityRegistry(Difficulty difficulty) {
        this(difficulty, 8);
    }

    public CityRegistry(int initialCapacity) {
        this(Difficulty.MEDIUM, initialCapacity);
    }

    public CityRegistry(Difficulty difficulty, int initialCapacity) {
        this.difficulty = difficulty;
        int capacity = Math.max(2, initialCapacity) + 1;
        name = new String[capacity];
        tileX = new int[capacity];
        tileY = new int[capacity];
        foundedTick = new long[capacity];
        active = new boolean[capacity];
        finance = new GovernmentFinance[capacity];
        ledger = new GoodsLedger[capacity];
        freeIds = new int[capacity];
    }

    public int highWaterMark() {
        return highWaterMark;
    }

    public int activeCount() {
        return activeCount;
    }

    public int create(String cityName, int worldTileX, int worldTileY, long tick) {
        int id = freeTop > 0 ? freeIds[--freeTop] : allocateFreshId();
        name[id] = cityName;
        tileX[id] = worldTileX;
        tileY[id] = worldTileY;
        foundedTick[id] = tick;
        GovernmentFinance newFinance = new GovernmentFinance();
        newFinance.adjustTreasury(difficulty.startingTreasury);
        finance[id] = newFinance;
        ledger[id] = new GoodsLedger();
        active[id] = true;
        activeCount++;
        return id;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public boolean isActive(int id) {
        return id > 0 && id < highWaterMark && active[id];
    }

    public String name(int id) {
        return name[id];
    }

    public int tileX(int id) {
        return tileX[id];
    }

    public int tileY(int id) {
        return tileY[id];
    }

    public long foundedTick(int id) {
        return foundedTick[id];
    }

    public GovernmentFinance finance(int id) {
        return finance[id];
    }

    public GoodsLedger ledger(int id) {
        return ledger[id];
    }

    public double distanceTiles(int id, int otherTileX, int otherTileY) {
        double dx = tileX[id] - otherTileX;
        double dy = tileY[id] - otherTileY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * The city whose founding tile is closest to {@code (tileX, tileY)}, or {@code -1} if no
     * city has been founded yet. Territory is never drawn explicitly (no border tool exists) —
     * every building simply belongs to whichever city is nearest, the same nearest-gateway
     * pattern {@code RegionalGraph.nearestNodeOfType} already uses for trade.
     */
    public int nearestCity(int tileX, int tileY) {
        int best = -1;
        long bestDistSq = Long.MAX_VALUE;
        for (int id = 1; id < highWaterMark; id++) {
            if (!active[id]) {
                continue;
            }
            long dx = this.tileX[id] - tileX;
            long dy = this.tileY[id] - tileY;
            long distSq = dx * dx + dy * dy;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = id;
            }
        }
        return best;
    }

    /** Visits every currently-active city without allocating an iterator. */
    public void forEachActive(CityVisitor visitor) {
        for (int id = 1; id < highWaterMark; id++) {
            if (active[id]) {
                visitor.visit(id);
            }
        }
    }

    public static CityRegistry createForRestore(int highWaterMarkValue) {
        return createForRestore(highWaterMarkValue, Difficulty.MEDIUM);
    }

    public static CityRegistry createForRestore(int highWaterMarkValue, Difficulty difficulty) {
        CityRegistry registry = new CityRegistry(difficulty, Math.max(2, highWaterMarkValue));
        registry.highWaterMark = Math.max(1, highWaterMarkValue);
        return registry;
    }

    public void restoreActive(int id, String cityName, int worldTileX, int worldTileY, long tick,
                               double treasuryBalance, double residentialTaxRate, double commercialTaxRate,
                               double industrialTaxRate) {
        name[id] = cityName;
        tileX[id] = worldTileX;
        tileY[id] = worldTileY;
        foundedTick[id] = tick;
        GovernmentFinance restoredFinance = new GovernmentFinance();
        restoredFinance.adjustTreasury(treasuryBalance);
        restoredFinance.setTaxRate(com.kosmos.atlas.sim.world.WorldConstants.ZONE_RESIDENTIAL, residentialTaxRate);
        restoredFinance.setTaxRate(com.kosmos.atlas.sim.world.WorldConstants.ZONE_COMMERCIAL, commercialTaxRate);
        restoredFinance.setTaxRate(com.kosmos.atlas.sim.world.WorldConstants.ZONE_INDUSTRIAL, industrialTaxRate);
        finance[id] = restoredFinance;
        ledger[id] = new GoodsLedger();
        active[id] = true;
        activeCount++;
    }

    public void restoreTombstone(int id) {
        active[id] = false;
        freeIds[freeTop++] = id;
    }

    private int allocateFreshId() {
        if (highWaterMark >= name.length) {
            grow();
        }
        return highWaterMark++;
    }

    private void grow() {
        int newCapacity = name.length * 2;
        name = Arrays.copyOf(name, newCapacity);
        tileX = Arrays.copyOf(tileX, newCapacity);
        tileY = Arrays.copyOf(tileY, newCapacity);
        foundedTick = Arrays.copyOf(foundedTick, newCapacity);
        active = Arrays.copyOf(active, newCapacity);
        finance = Arrays.copyOf(finance, newCapacity);
        ledger = Arrays.copyOf(ledger, newCapacity);
        freeIds = Arrays.copyOf(freeIds, newCapacity);
    }

    @FunctionalInterface
    public interface CityVisitor {
        void visit(int cityId);
    }
}
