package com.kosmos.atlas.sim.commands.economy;

/** Stable, journal-facing type ids for economy commands (spec §33 MVP 0.3). Never renumber. */
public final class EconomyCommandTypeIds {
    public static final int BUILD_PRODUCTION_BUILDING = 20;
    public static final int REQUEST_EXTERNAL_LOAN = 21;
    public static final int REQUEST_CITY_LOAN = 22;
    public static final int REPAY_LOAN = 23;
    public static final int BUILD_PORT = 24;
    public static final int BUILD_CIVIC_BUILDING = 25;

    private EconomyCommandTypeIds() {
    }
}
