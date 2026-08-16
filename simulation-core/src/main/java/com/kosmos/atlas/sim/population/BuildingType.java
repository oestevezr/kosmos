package com.kosmos.atlas.sim.population;

/** Byte-coded building categories (spec §33 MVP 0.2/0.3, §21 industry chains, §24 utilities). */
public final class BuildingType {

    public static final byte RESIDENTIAL = 0;
    public static final byte COMMERCIAL = 1;
    public static final byte INDUSTRIAL = 2;
    public static final byte POWER_PLANT = 3;
    public static final byte WATER_TOWER = 4;

    // --- MVP 0.3 production chain (spec §7, §21) ---
    /** Extracts Food; requires a fertile tile. */
    public static final byte FARM = 5;
    /** Extracts Timber; requires a forested tile with the timber resource flag. */
    public static final byte LUMBER_CAMP = 6;
    /** Extracts Ore or Fuel depending on the tile's mineral resource flags. */
    public static final byte MINE = 7;
    /** Extracts ConstructionMaterials directly from a stone-flagged tile. */
    public static final byte QUARRY = 8;
    /** Consumes Ore, produces Steel. */
    public static final byte STEEL_MILL = 9;
    /** The player-placed external-market gateway (spec §29) — imports/exports goods up to a capacity. */
    public static final byte TRADE_DEPOT = 10;

    private BuildingType() {
    }
}
