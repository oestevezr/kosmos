package com.kosmos.atlas.sim.commands.terrain;

/** Stable, journal-facing type ids for terrain commands. Never renumber — see {@link com.kosmos.atlas.sim.commands.Command#typeId()}. */
public final class TerrainCommandTypeIds {
    public static final int RAISE_TERRAIN = 1;
    public static final int LOWER_TERRAIN = 2;
    public static final int PAINT_TERRAIN = 3;

    private TerrainCommandTypeIds() {
    }
}
