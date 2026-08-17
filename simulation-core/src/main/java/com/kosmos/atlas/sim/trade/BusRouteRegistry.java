package com.kosmos.atlas.sim.trade;

import java.util.Arrays;

/**
 * The authoritative "one record per bus route" store — a route is a {@code BuildingType.BUS_DEPOT}
 * plus {@link #MAX_STOPS_PER_ROUTE} or fewer, at least 2, ordered {@code BuildingType.BUS_STOP}
 * building ids ({@code CreateBusRouteCommand}, spec §16's passenger demand mechanic, MVP 0.6).
 *
 * <p>A route's stop list is a small, fixed-maximum-size category — the same shape
 * {@code CLAUDE.md} already documents for "several fields of a fixed small category": one flat
 * array indexed by {@code id * MAX_STOPS_PER_ROUTE + slot}, not a variable-length list structure,
 * which would break the Structure-of-Arrays convention every other registry in this codebase uses.
 * Unused slots (beyond a route's own {@link #stopCount}) are left at 0, which is never a valid
 * building id.
 *
 * <p>Same growable-SoA-with-tombstone-free-list shape as {@link ShipmentRegistry}. Deliberately
 * <b>not persisted</b> — its routes reference {@link RegionalGraph} edges, and that graph isn't
 * persisted either (see its class javadoc); persisting routes without their edges would leave
 * inconsistent state on load, so this is documented as shared, not fixed-half, debt.
 */
public final class BusRouteRegistry {

    public static final int MAX_STOPS_PER_ROUTE = 6;
    public static final int MIN_STOPS_PER_ROUTE = 2;

    private int[] depotBuildingId;
    private int[] cityId;
    private byte[] stopCount;
    private int[] stopBuildingIdsFlat; // id * MAX_STOPS_PER_ROUTE + slot
    private boolean[] active;

    private int highWaterMark = 1; // id 0 reserved as "no route"
    private int activeCount;
    private int[] freeIds;
    private int freeTop;

    public BusRouteRegistry() {
        this(16);
    }

    public BusRouteRegistry(int initialCapacity) {
        int capacity = Math.max(2, initialCapacity) + 1;
        depotBuildingId = new int[capacity];
        cityId = new int[capacity];
        stopCount = new byte[capacity];
        stopBuildingIdsFlat = new int[capacity * MAX_STOPS_PER_ROUTE];
        active = new boolean[capacity];
        freeIds = new int[capacity];
    }

    public int highWaterMark() {
        return highWaterMark;
    }

    public int activeCount() {
        return activeCount;
    }

    /**
     * @param stopBuildingIds ordered stop building ids, {@link #MIN_STOPS_PER_ROUTE} to
     *                        {@link #MAX_STOPS_PER_ROUTE} of them — the caller (
     *                        {@code CreateBusRouteCommand}) is responsible for validating types/
     *                        ownership/count before calling this.
     */
    public int create(int ownerDepotBuildingId, int ownerCityId, int[] stopBuildingIds) {
        int id = freeTop > 0 ? freeIds[--freeTop] : allocateFreshId();
        depotBuildingId[id] = ownerDepotBuildingId;
        cityId[id] = ownerCityId;
        stopCount[id] = (byte) stopBuildingIds.length;
        int base = id * MAX_STOPS_PER_ROUTE;
        for (int i = 0; i < stopBuildingIds.length; i++) {
            stopBuildingIdsFlat[base + i] = stopBuildingIds[i];
        }
        active[id] = true;
        activeCount++;
        return id;
    }

    public boolean isActive(int id) {
        return id > 0 && id < highWaterMark && active[id];
    }

    public int depotBuildingId(int id) {
        return depotBuildingId[id];
    }

    public int cityId(int id) {
        return cityId[id];
    }

    public int stopCount(int id) {
        return stopCount[id] & 0xFF;
    }

    public int stopBuildingIdAt(int id, int slot) {
        return stopBuildingIdsFlat[id * MAX_STOPS_PER_ROUTE + slot];
    }

    /** How many active routes originate from {@code depotId} — {@code CreateBusRouteCommand}'s
     *  concurrency cap, same O(routes) scan class as {@code ShipmentRegistry.countActiveForDepot}. */
    public int countRoutesForDepot(int depotId) {
        int count = 0;
        for (int id = 1; id < highWaterMark; id++) {
            if (active[id] && depotBuildingId[id] == depotId) {
                count++;
            }
        }
        return count;
    }

    /** Whether {@code buildingId} is a stop on at least one active route — {@code UtilitySystem}'s
     *  gate for whether a Bus Stop actually gives transit coverage (see its javadoc: an isolated,
     *  route-less stop gives none). O(active routes × MAX_STOPS_PER_ROUTE), same performance class
     *  as every other small registry scan in this codebase. */
    public boolean isStopInAnyActiveRoute(int buildingId) {
        for (int id = 1; id < highWaterMark; id++) {
            if (!active[id]) {
                continue;
            }
            int base = id * MAX_STOPS_PER_ROUTE;
            int count = stopCount[id] & 0xFF;
            for (int slot = 0; slot < count; slot++) {
                if (stopBuildingIdsFlat[base + slot] == buildingId) {
                    return true;
                }
            }
        }
        return false;
    }

    public void demolishRoute(int id) {
        active[id] = false;
        activeCount--;
        freeIds[freeTop++] = id;
    }

    /** Visits every currently-active route without allocating an iterator. */
    public void forEachActive(RouteVisitor visitor) {
        for (int id = 1; id < highWaterMark; id++) {
            if (active[id]) {
                visitor.visit(id);
            }
        }
    }

    private int allocateFreshId() {
        if (highWaterMark >= depotBuildingId.length) {
            grow();
        }
        return highWaterMark++;
    }

    private void grow() {
        int oldCapacity = depotBuildingId.length;
        int newCapacity = oldCapacity * 2;
        depotBuildingId = Arrays.copyOf(depotBuildingId, newCapacity);
        cityId = Arrays.copyOf(cityId, newCapacity);
        stopCount = Arrays.copyOf(stopCount, newCapacity);
        stopBuildingIdsFlat = Arrays.copyOf(stopBuildingIdsFlat, newCapacity * MAX_STOPS_PER_ROUTE);
        active = Arrays.copyOf(active, newCapacity);
        freeIds = Arrays.copyOf(freeIds, newCapacity);
    }

    @FunctionalInterface
    public interface RouteVisitor {
        void visit(int routeId);
    }
}
