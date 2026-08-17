package com.kosmos.atlas.sim.trade;

import java.util.Arrays;

/**
 * The extra fields a {@code BuildingType.RAIL_TERMINAL} building needs beyond what
 * {@code BuildingRegistry} already tracks (spec §18: platforms/cargo capacity) — same
 * secondary-registry-indexed-by-{@code buildingId} shape as {@link PortRegistry}/
 * {@link AirportRegistry} (see {@code PortRegistry}'s javadoc for the full rationale).
 *
 * <p>Unlike {@link PortRegistry}/{@link AirportRegistry}, there is no
 * {@code customsEfficiencyPercent} column here — a Rail Terminal trades domestically between
 * player-founded cities in the same world, not across an international border, so a customs bonus
 * doesn't apply (see {@code MarketSystem.runGateways}, which fixes the Rail branch's customs bonus
 * at 0 instead of reading a third column).
 */
public final class StationRegistry {

    private int[] platforms;
    private int[] cargoCapacityPerTick;
    private boolean[] hasTerminal;

    private int highWaterMark = 1; // buildingId 0 reserved as "no building"

    public StationRegistry() {
        this(8);
    }

    public StationRegistry(int initialCapacity) {
        int capacity = Math.max(2, initialCapacity) + 1;
        platforms = new int[capacity];
        cargoCapacityPerTick = new int[capacity];
        hasTerminal = new boolean[capacity];
    }

    public int highWaterMark() {
        return highWaterMark;
    }

    public void set(int buildingId, int platformsValue, int cargoCapacityPerTickValue) {
        ensureCapacity(buildingId);
        platforms[buildingId] = platformsValue;
        cargoCapacityPerTick[buildingId] = cargoCapacityPerTickValue;
        hasTerminal[buildingId] = true;
        if (buildingId >= highWaterMark) {
            highWaterMark = buildingId + 1;
        }
    }

    public boolean hasTerminal(int buildingId) {
        return buildingId > 0 && buildingId < hasTerminal.length && hasTerminal[buildingId];
    }

    public int platforms(int buildingId) {
        return platforms[buildingId];
    }

    public int cargoCapacityPerTick(int buildingId) {
        return cargoCapacityPerTick[buildingId];
    }

    public static StationRegistry createForRestore(int highWaterMarkValue) {
        StationRegistry registry = new StationRegistry(Math.max(2, highWaterMarkValue));
        registry.highWaterMark = Math.max(1, highWaterMarkValue);
        return registry;
    }

    private void ensureCapacity(int buildingId) {
        if (buildingId < platforms.length) {
            return;
        }
        int newCapacity = platforms.length;
        while (buildingId >= newCapacity) {
            newCapacity *= 2;
        }
        platforms = Arrays.copyOf(platforms, newCapacity);
        cargoCapacityPerTick = Arrays.copyOf(cargoCapacityPerTick, newCapacity);
        hasTerminal = Arrays.copyOf(hasTerminal, newCapacity);
    }
}
