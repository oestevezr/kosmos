package com.kosmos.atlas.sim.commands.city;

/** Stable, journal-facing type ids for city commands. Never renumber — see {@link com.kosmos.atlas.sim.commands.Command#typeId()}. */
public final class CityCommandTypeIds {
    public static final int BUILD_ROAD = 10;
    public static final int ZONE = 11;
    public static final int DEMOLISH = 12;
    public static final int BUILD_POWER_PLANT = 13;
    public static final int BUILD_WATER_TOWER = 14;
    public static final int SET_TAX_POLICY = 15;
    public static final int FOUND_CITY = 16;

    private CityCommandTypeIds() {
    }
}
