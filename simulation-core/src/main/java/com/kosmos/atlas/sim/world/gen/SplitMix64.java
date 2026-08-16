package com.kosmos.atlas.sim.world.gen;

/**
 * Stateless SplitMix64 mixing functions.
 *
 * <p>Deliberately exposes only pure {@code static} hash functions instead of a stateful
 * generator object: procedural generation must be reproducible regardless of call order or
 * thread (spec §32, §5.1). Given the same {@code state} input, {@link #mix(long)} always
 * returns the same value, on any thread, in any order — there is no shared mutable RNG to
 * accidentally serialize generation around.
 */
public final class SplitMix64 {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private SplitMix64() {
    }

    /** The classic SplitMix64 output mixer (Steele, Lea, Flood 2014). */
    public static long mix(long z) {
        z += GOLDEN_GAMMA;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Combines an arbitrary number of longs into a single deterministic seed. */
    public static long combine(long... parts) {
        long h = 0L;
        for (long p : parts) {
            h = mix(h ^ mix(p));
        }
        return h;
    }

    /** Maps a mixed long into [0, 1) as a double, using the top 53 bits for full mantissa precision. */
    public static double toUnitDouble(long mixed) {
        return (mixed >>> 11) * 0x1.0p-53;
    }
}
