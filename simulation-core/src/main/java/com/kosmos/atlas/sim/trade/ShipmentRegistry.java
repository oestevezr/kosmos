package com.kosmos.atlas.sim.trade;

import java.util.Arrays;

/**
 * The authoritative "one record per in-flight shipment" store (spec §14's {@code Shipment
 * { origin, destination, commodity, quantity, route, departure_time, ETA }}), backed by growable
 * parallel primitive arrays — the same Structure-of-Arrays shape as
 * {@link com.kosmos.atlas.sim.population.BuildingRegistry}, for the same reason: a shipment id is
 * just an index, ids are stable across unrelated completions, and nothing here is a permanent
 * per-shipment Java object (spec §11: shipments are the textbook "AGGREGATED" entity — spec §15's
 * far-away shipments have "no physical train"; only near the player's active view would one ever
 * need a visual counterpart, and {@code game-client} doesn't render freight yet — see
 * {@code docs/roadmap.md}'s MVP 0.4 notes for why the streaming/LOD half of this is deferred).
 */
public final class ShipmentRegistry {

    private byte[] kind;
    private byte[] commodity;
    private int[] quantity;
    private int[] depotBuildingId;
    private int[] cityId;
    private long[] departureTick;
    private long[] etaTick;
    private boolean[] active;

    private int highWaterMark = 1; // id 0 reserved as "no shipment"
    private int activeCount;
    private int[] freeIds;
    private int freeTop;

    public ShipmentRegistry() {
        this(32);
    }

    public ShipmentRegistry(int initialCapacity) {
        int capacity = Math.max(2, initialCapacity) + 1;
        kind = new byte[capacity];
        commodity = new byte[capacity];
        quantity = new int[capacity];
        depotBuildingId = new int[capacity];
        cityId = new int[capacity];
        departureTick = new long[capacity];
        etaTick = new long[capacity];
        active = new boolean[capacity];
        freeIds = new int[capacity];
    }

    public int highWaterMark() {
        return highWaterMark;
    }

    public int activeCount() {
        return activeCount;
    }

    public int create(byte shipmentKind, byte commodityGood, int shipmentQuantity, int originDepotBuildingId,
                       int ownerCityId, long departure, long eta) {
        int id = freeTop > 0 ? freeIds[--freeTop] : allocateFreshId();
        kind[id] = shipmentKind;
        commodity[id] = commodityGood;
        quantity[id] = shipmentQuantity;
        depotBuildingId[id] = originDepotBuildingId;
        cityId[id] = ownerCityId;
        departureTick[id] = departure;
        etaTick[id] = eta;
        active[id] = true;
        activeCount++;
        return id;
    }

    /** Marks a shipment as arrived/complete, freeing its id for reuse. */
    public void complete(int id) {
        active[id] = false;
        activeCount--;
        freeIds[freeTop++] = id;
    }

    public boolean isActive(int id) {
        return id > 0 && id < highWaterMark && active[id];
    }

    public byte kind(int id) {
        return kind[id];
    }

    public byte commodity(int id) {
        return commodity[id];
    }

    public int quantity(int id) {
        return quantity[id];
    }

    public int depotBuildingId(int id) {
        return depotBuildingId[id];
    }

    /**
     * The city this shipment was created for — captured at departure, not re-derived from the
     * depot building afterward. A depot can be demolished (and its id slot reused for a different
     * building, even a different city) before an in-flight shipment arrives; settlement must still
     * land in the original city's books, not whatever happens to own that id later.
     */
    public int cityId(int id) {
        return cityId[id];
    }

    public long departureTick(int id) {
        return departureTick[id];
    }

    public long etaTick(int id) {
        return etaTick[id];
    }

    /**
     * Counts active shipments whose {@link #depotBuildingId} matches — how {@code MarketSystem}
     * enforces a per-depot concurrent-shipment cap (spec §17's bottleneck example: demand
     * exceeding capacity causes delayed cargo, not a hard rejection).
     */
    public int countActiveForDepot(int depotId) {
        int count = 0;
        for (int id = 1; id < highWaterMark; id++) {
            if (active[id] && depotBuildingId[id] == depotId) {
                count++;
            }
        }
        return count;
    }

    /** Visits every currently-active shipment without allocating an iterator. */
    public void forEachActive(ShipmentVisitor visitor) {
        for (int id = 1; id < highWaterMark; id++) {
            if (active[id]) {
                visitor.visit(id);
            }
        }
    }

    public static ShipmentRegistry createForRestore(int highWaterMarkValue) {
        ShipmentRegistry registry = new ShipmentRegistry(Math.max(2, highWaterMarkValue));
        registry.highWaterMark = Math.max(1, highWaterMarkValue);
        return registry;
    }

    public void restoreActive(int id, byte shipmentKind, byte commodityGood, int shipmentQuantity,
                               int originDepotBuildingId, int ownerCityId, long departure, long eta) {
        kind[id] = shipmentKind;
        commodity[id] = commodityGood;
        quantity[id] = shipmentQuantity;
        depotBuildingId[id] = originDepotBuildingId;
        cityId[id] = ownerCityId;
        departureTick[id] = departure;
        etaTick[id] = eta;
        active[id] = true;
        activeCount++;
    }

    public void restoreTombstone(int id) {
        active[id] = false;
        freeIds[freeTop++] = id;
    }

    private int allocateFreshId() {
        if (highWaterMark >= kind.length) {
            grow();
        }
        return highWaterMark++;
    }

    private void grow() {
        int newCapacity = kind.length * 2;
        kind = Arrays.copyOf(kind, newCapacity);
        commodity = Arrays.copyOf(commodity, newCapacity);
        quantity = Arrays.copyOf(quantity, newCapacity);
        depotBuildingId = Arrays.copyOf(depotBuildingId, newCapacity);
        cityId = Arrays.copyOf(cityId, newCapacity);
        departureTick = Arrays.copyOf(departureTick, newCapacity);
        etaTick = Arrays.copyOf(etaTick, newCapacity);
        active = Arrays.copyOf(active, newCapacity);
        freeIds = Arrays.copyOf(freeIds, newCapacity);
    }

    @FunctionalInterface
    public interface ShipmentVisitor {
        void visit(int shipmentId);
    }
}
