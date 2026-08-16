package com.kosmos.atlas.sim.world.gen;

/**
 * Deterministic, allocation-free 2D value noise with fractal (fBm) octaves.
 *
 * <p>Spec §5.3 explicitly allows "custom deterministic value noise" and warns against
 * expensive world-generation algorithms in the MVP. This implementation avoids
 * {@code Math.sin/cos/pow} (whose results are only guaranteed correctly-rounded, not bit-stable,
 * across JVMs) in favor of {@link StrictMath}, which the JLS requires to be bit-identical on
 * every platform — a hard requirement for §32's "same seed + version ⇒ same world" guarantee.
 *
 * <p>Grid corner values are derived directly from {@link RngStream}, not from a stateful
 * generator, so noise sampling is stateless and safe to call from any thread in any order.
 */
public final class DeterministicNoise {

    private final long worldSeed;
    private final int generatorVersion;
    private final int layerId;

    public DeterministicNoise(long worldSeed, int generatorVersion, int layerId) {
        this.worldSeed = worldSeed;
        this.generatorVersion = generatorVersion;
        this.layerId = layerId;
    }

    /** Single-octave value noise in [-1, 1] at the given world-tile-space coordinate. */
    public double sample(double x, double y) {
        int x0 = (int) StrictMath.floor(x);
        int y0 = (int) StrictMath.floor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        double sx = fade(x - x0);
        double sy = fade(y - y0);

        double n00 = corner(x0, y0);
        double n10 = corner(x1, y0);
        double n01 = corner(x0, y1);
        double n11 = corner(x1, y1);

        double ix0 = lerp(n00, n10, sx);
        double ix1 = lerp(n01, n11, sx);
        return lerp(ix0, ix1, sy);
    }

    /** Fractal Brownian motion: sum of octaves at increasing frequency / decreasing amplitude. */
    public double fbm(double x, double y, int octaves, double lacunarity, double persistence) {
        double amplitude = 1.0;
        double frequency = 1.0;
        double sum = 0.0;
        double maxAmplitude = 0.0;
        for (int o = 0; o < octaves; o++) {
            sum += sample(x * frequency, y * frequency) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return sum / maxAmplitude;
    }

    private double corner(int gx, int gy) {
        long mixed = RngStream.deriveTile(worldSeed, generatorVersion, gx, gy, layerId);
        return SplitMix64.toUnitDouble(mixed) * 2.0 - 1.0;
    }

    private static double fade(double t) {
        // Perlin's quintic ease curve: 6t^5 - 15t^4 + 10t^3
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
