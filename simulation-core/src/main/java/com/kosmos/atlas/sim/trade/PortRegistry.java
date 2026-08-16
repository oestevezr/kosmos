package com.kosmos.atlas.sim.trade;

import java.util.Arrays;

/**
 * The extra fields a {@code BuildingType.PORT} building needs beyond what {@code BuildingRegistry}
 * already tracks (spec §17: berths/cargo capacity/customs efficiency) — a secondary registry
 * indexed directly by {@code buildingId} rather than either a full parallel id space (ports are a
 * small, sparse subset of buildings, like {@code BuildingType.TRADE_DEPOT}) or new optional
 * columns bolted onto {@code BuildingRegistry} that 99% of buildings would never use (the tradeoff
 * this class's javadoc-linked roadmap entry, {@code docs/roadmap.md}'s MVP 0.5 section, decided
 * ahead of time).
 *
 * <p>Unlike {@code CityRegistry}/{@code ShipmentRegistry}, there is no separate id space or
 * tombstone free list here — a port's "id" *is* its {@code buildingId}, so {@link #set} simply
 * grows the backing arrays to fit. A demolished port's row is never explicitly cleared (matching
 * {@code BuildingRegistry.demolish}'s existing "stale until slot reused" behavior) since every
 * caller only reads {@link #hasPort} for ids where {@code BuildingRegistry.isActive} is already
 * true and {@code BuildingRegistry.type} is already {@code PORT}.
 */
public final class PortRegistry {

    private int[] berths;
    private int[] cargoCapacityPerTick;
    private int[] customsEfficiencyPercent;
    private boolean[] hasPort;

    private int highWaterMark = 1; // buildingId 0 reserved as "no building"

    public PortRegistry() {
        this(8);
    }

    public PortRegistry(int initialCapacity) {
        int capacity = Math.max(2, initialCapacity) + 1;
        berths = new int[capacity];
        cargoCapacityPerTick = new int[capacity];
        customsEfficiencyPercent = new int[capacity];
        hasPort = new boolean[capacity];
    }

    public int highWaterMark() {
        return highWaterMark;
    }

    public void set(int buildingId, int berthsValue, int cargoCapacityPerTickValue, int customsEfficiencyPercentValue) {
        ensureCapacity(buildingId);
        berths[buildingId] = berthsValue;
        cargoCapacityPerTick[buildingId] = cargoCapacityPerTickValue;
        customsEfficiencyPercent[buildingId] = customsEfficiencyPercentValue;
        hasPort[buildingId] = true;
        if (buildingId >= highWaterMark) {
            highWaterMark = buildingId + 1;
        }
    }

    public boolean hasPort(int buildingId) {
        return buildingId > 0 && buildingId < hasPort.length && hasPort[buildingId];
    }

    public int berths(int buildingId) {
        return berths[buildingId];
    }

    public int cargoCapacityPerTick(int buildingId) {
        return cargoCapacityPerTick[buildingId];
    }

    public int customsEfficiencyPercent(int buildingId) {
        return customsEfficiencyPercent[buildingId];
    }

    public static PortRegistry createForRestore(int highWaterMarkValue) {
        PortRegistry registry = new PortRegistry(Math.max(2, highWaterMarkValue));
        registry.highWaterMark = Math.max(1, highWaterMarkValue);
        return registry;
    }

    private void ensureCapacity(int buildingId) {
        if (buildingId < berths.length) {
            return;
        }
        int newCapacity = berths.length;
        while (buildingId >= newCapacity) {
            newCapacity *= 2;
        }
        berths = Arrays.copyOf(berths, newCapacity);
        cargoCapacityPerTick = Arrays.copyOf(cargoCapacityPerTick, newCapacity);
        customsEfficiencyPercent = Arrays.copyOf(customsEfficiencyPercent, newCapacity);
        hasPort = Arrays.copyOf(hasPort, newCapacity);
    }
}
