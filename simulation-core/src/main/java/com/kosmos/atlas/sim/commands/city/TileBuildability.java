package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.world.WorldConstants;

/** Shared "can I build here" terrain check for every city command (spec §2.1: terrain constrains development). */
final class TileBuildability {

    private TileBuildability() {
    }

    static boolean isLand(byte terrainType) {
        return terrainType != WorldConstants.TERRAIN_DEEP_WATER && terrainType != WorldConstants.TERRAIN_SHALLOW_WATER;
    }
}
