package com.kosmos.atlas.sim.economy;

/**
 * The 8 initial tradeable goods (spec §14). Byte-coded so they can index flat SoA arrays
 * (production/consumption/inventory/price per good) instead of a {@code Map<GoodType, ...>}.
 *
 * <p>{@link #NONE} is a distinct sentinel from {@link #FOOD} (which is byte {@code 0}) — a
 * building's "no input good" marker must never collide with a real good index.
 */
public final class GoodType {

    public static final byte FOOD = 0;
    public static final byte TIMBER = 1;
    public static final byte ORE = 2;
    public static final byte STEEL = 3;
    public static final byte FUEL = 4;
    public static final byte CONSUMER_GOODS = 5;
    public static final byte MACHINERY = 6;
    public static final byte CONSTRUCTION_MATERIALS = 7;

    public static final int COUNT = 8;

    /** Sentinel for "this building has no input/output good" — never a valid array index. */
    public static final byte NONE = -1;

    private GoodType() {
    }
}
