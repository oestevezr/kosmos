package com.kosmos.atlas.sim.world.gen;

/**
 * Derives a positionally-addressed deterministic value from
 * {@code (worldSeed, generatorVersion, chunkX, chunkY, layerId)} (spec §4, §32).
 *
 * <p>There is intentionally no sequential/stateful RNG shared across chunks or layers: every
 * value is derived directly from its coordinates, so generation is embarrassingly parallel and
 * independent of the order chunks happen to be requested in. Changing {@code generatorVersion}
 * is the only sanctioned way to change how a given seed maps to terrain — this is what makes old
 * saves able to pin themselves to their original generator (spec §32).
 */
public final class RngStream {

    private RngStream() {
    }

    /** Deterministic 64-bit value for a given chunk coordinate and named layer. */
    public static long derive(long worldSeed, int generatorVersion, int chunkX, int chunkY, int layerId) {
        return SplitMix64.combine(
            worldSeed,
            generatorVersion,
            (((long) chunkX) << 32) ^ (chunkY & 0xFFFFFFFFL),
            layerId
        );
    }

    /** Deterministic 64-bit value for a single absolute tile coordinate within a layer. */
    public static long deriveTile(long worldSeed, int generatorVersion, int worldTileX, int worldTileY, int layerId) {
        return SplitMix64.combine(
            worldSeed,
            generatorVersion,
            (((long) worldTileX) << 32) ^ (worldTileY & 0xFFFFFFFFL),
            layerId,
            0x5151L
        );
    }

    public static double unitDouble(long worldSeed, int generatorVersion, int worldTileX, int worldTileY, int layerId) {
        return SplitMix64.toUnitDouble(deriveTile(worldSeed, generatorVersion, worldTileX, worldTileY, layerId));
    }
}
