package com.kosmos.atlas.sim.world.gen;

/**
 * Generation inputs (spec §5.1). Immutable — a running world's generator is configured once at
 * creation and never mutated, since it participates in the determinism guarantee (spec §32).
 */
public final class WorldGenSettings {

    public final long worldSeed;
    public final int generatorVersion;
    public final int worldSizeTiles;
    public final double seaLevel;
    public final double resourceDensity;

    public WorldGenSettings(long worldSeed, int worldSizeTiles, double seaLevel, double resourceDensity) {
        this.worldSeed = worldSeed;
        this.generatorVersion = GeneratorVersion.CURRENT;
        this.worldSizeTiles = worldSizeTiles;
        this.seaLevel = seaLevel;
        this.resourceDensity = resourceDensity;
    }

    public static WorldGenSettings balanced(long worldSeed, int worldSizeTiles) {
        return new WorldGenSettings(worldSeed, worldSizeTiles, 0.42, 0.5);
    }
}
