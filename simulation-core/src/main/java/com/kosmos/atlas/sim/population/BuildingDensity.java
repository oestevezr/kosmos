package com.kosmos.atlas.sim.population;

/**
 * Per-density-level table for RESIDENTIAL/COMMERCIAL/INDUSTRIAL buildings — same shape as
 * {@link com.kosmos.atlas.sim.economy.BuildingEconomics}: static arrays indexed by level, values
 * justified in comments rather than a formula (spec §20's "understandable rather than
 * hyper-realistic").
 *
 * <p>A building evolves organically: {@code PopulationSystem} promotes it one level once it's both
 * full (at the current level's capacity) and has sustained satisfaction at or above
 * {@link #promoteSatisfaction}, and demotes it one level if satisfaction falls below
 * {@link #demoteSatisfaction} — a lower threshold than promotion (hysteresis), so a building
 * doesn't flap between levels as satisfaction settles near a boundary. Satisfaction is
 * {@code PopulationSystem}'s existing ceiling-driven value (prosperity/luxury coverage minus
 * pollution, see its class javadoc), so "needs better services to grow taller" falls out of the
 * existing model for free — no new coverage check is added here.
 */
public final class BuildingDensity {

    /** 0 = starter (house/storefront/workshop), 1 = mid-rise, 2 = high-rise/skyscraper. */
    public static final int MAX_LEVEL = 2;

    // Level 0 matches PopulationSystem's original flat RESIDENTIAL_CAPACITY/JOB_CAPACITY exactly —
    // a building that never evolves behaves identically to before this mechanic existed.
    private static final int[] RESIDENTIAL_CAPACITY_BY_LEVEL = {60, 180, 500};
    private static final int[] JOB_CAPACITY_BY_LEVEL = {40, 120, 320};

    // Index 0 (level 0) is unused — nothing demotes below or promotes into the starter level from
    // "below". Promotion is stricter than PopulationSystem's ceilings (85 prosperity / 100 luxury)
    // require reaching, not just approaching, the ceiling that unlocks the next level.
    private static final int[] PROMOTE_SATISFACTION_BY_LEVEL = {0, 75, 92};
    private static final int[] DEMOTE_SATISFACTION_BY_LEVEL = {0, 65, 88};

    // Taller buildings house higher-value residents/businesses -> more tax per capita, not just
    // more capacity (docs/roadmap.md's density-evolution section).
    private static final double[] WAGE_MULTIPLIER_BY_LEVEL = {1.0, 1.35, 1.8};

    public static int residentialCapacity(int level) {
        return RESIDENTIAL_CAPACITY_BY_LEVEL[level];
    }

    public static int jobCapacity(int level) {
        return JOB_CAPACITY_BY_LEVEL[level];
    }

    public static int promoteSatisfaction(int level) {
        return PROMOTE_SATISFACTION_BY_LEVEL[level];
    }

    public static int demoteSatisfaction(int level) {
        return DEMOTE_SATISFACTION_BY_LEVEL[level];
    }

    public static double wageMultiplier(int level) {
        return WAGE_MULTIPLIER_BY_LEVEL[level];
    }

    /**
     * Deterministic pseudo-random variant index in {@code [0, variantCount)} for the building at
     * {@code (tileX, tileY)} at {@code level} — purely a function of its inputs, not stored
     * anywhere (the whole world is already seed-deterministic, see {@code GoldenChunkHashTest}).
     * Changes when the building evolves, so a promoted building doesn't keep looking identical to
     * its pre-promotion self once {@code game-client} has a variant to draw.
     */
    public static int variantIndex(int tileX, int tileY, int level, int variantCount) {
        long h = tileX * 0x9E3779B97F4A7C15L ^ tileY * 0xC2B2AE3D27D4EB4FL ^ (level + 1) * 0x165667B19E3779F9L;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        return (int) Math.floorMod(h, (long) variantCount);
    }

    private BuildingDensity() {
    }
}
