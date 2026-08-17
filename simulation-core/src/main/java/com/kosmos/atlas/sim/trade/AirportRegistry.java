package com.kosmos.atlas.sim.trade;

import java.util.Arrays;

/**
 * The extra fields a {@code BuildingType.AIRPORT} building needs beyond what
 * {@code BuildingRegistry} already tracks (spec §19: gates/cargo capacity/customs efficiency) —
 * same secondary-registry-indexed-by-{@code buildingId} shape as {@link PortRegistry} (see its
 * javadoc for the full rationale: airports are a small, sparse subset of buildings, so a parallel
 * id space or columns bolted onto {@code BuildingRegistry} would both be wasteful).
 *
 * <p>Unlike {@link PortRegistry}, there is no {@code passengerCapacity} column here — MVP 0.6's
 * first slice (see {@code docs/roadmap.md}) makes the Airport a cargo-only gateway, same as a Port
 * but landlocked; passenger capacity has no reader yet, so it isn't added (the same discipline that
 * kept it off {@code PortRegistry} in MVP 0.5).
 */
public final class AirportRegistry {

    private int[] gates;
    private int[] cargoCapacityPerTick;
    private int[] customsEfficiencyPercent;
    private boolean[] hasAirport;

    private int highWaterMark = 1; // buildingId 0 reserved as "no building"

    public AirportRegistry() {
        this(8);
    }

    public AirportRegistry(int initialCapacity) {
        int capacity = Math.max(2, initialCapacity) + 1;
        gates = new int[capacity];
        cargoCapacityPerTick = new int[capacity];
        customsEfficiencyPercent = new int[capacity];
        hasAirport = new boolean[capacity];
    }

    public int highWaterMark() {
        return highWaterMark;
    }

    public void set(int buildingId, int gatesValue, int cargoCapacityPerTickValue, int customsEfficiencyPercentValue) {
        ensureCapacity(buildingId);
        gates[buildingId] = gatesValue;
        cargoCapacityPerTick[buildingId] = cargoCapacityPerTickValue;
        customsEfficiencyPercent[buildingId] = customsEfficiencyPercentValue;
        hasAirport[buildingId] = true;
        if (buildingId >= highWaterMark) {
            highWaterMark = buildingId + 1;
        }
    }

    public boolean hasAirport(int buildingId) {
        return buildingId > 0 && buildingId < hasAirport.length && hasAirport[buildingId];
    }

    public int gates(int buildingId) {
        return gates[buildingId];
    }

    public int cargoCapacityPerTick(int buildingId) {
        return cargoCapacityPerTick[buildingId];
    }

    public int customsEfficiencyPercent(int buildingId) {
        return customsEfficiencyPercent[buildingId];
    }

    public static AirportRegistry createForRestore(int highWaterMarkValue) {
        AirportRegistry registry = new AirportRegistry(Math.max(2, highWaterMarkValue));
        registry.highWaterMark = Math.max(1, highWaterMarkValue);
        return registry;
    }

    private void ensureCapacity(int buildingId) {
        if (buildingId < gates.length) {
            return;
        }
        int newCapacity = gates.length;
        while (buildingId >= newCapacity) {
            newCapacity *= 2;
        }
        gates = Arrays.copyOf(gates, newCapacity);
        cargoCapacityPerTick = Arrays.copyOf(cargoCapacityPerTick, newCapacity);
        customsEfficiencyPercent = Arrays.copyOf(customsEfficiencyPercent, newCapacity);
        hasAirport = Arrays.copyOf(hasAirport, newCapacity);
    }
}
