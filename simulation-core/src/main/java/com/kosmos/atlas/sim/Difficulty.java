package com.kosmos.atlas.sim;

/**
 * World-level difficulty, chosen once at world creation (like {@code HardwareProfile}, but the
 * opposite axis — {@code HardwareProfile} explicitly never changes simulation/economic outcomes;
 * {@code Difficulty} changes nothing else. Every city founded in a world shares its difficulty
 * (spec doesn't model per-city difficulty; a single knob per world keeps this "understandable
 * rather than hyper-realistic" per spec §20's MVP economy philosophy).
 *
 * <p>Starting treasury values follow the user's requested TheOtown-inspired tiers (50K/25K/10K).
 * {@link #growthRateMultiplier} scales {@code PopulationSystem}'s residential/workplace growth
 * step; {@link #loanInterestRateMultiplier} scales every loan rate in
 * {@code RequestExternalLoanCommand}/{@code RequestCityLoanCommand} — a harder world makes credit
 * more expensive on top of already starting with less money, the same way TheOtown's difficulty
 * affects more than just the starting cash. Deliberately does *not* touch the prosperity gate
 * thresholds (spec's loan system) — a lower starting treasury on Hard already makes reaching those
 * thresholds harder without needing a second knob for the same effect.
 */
public enum Difficulty {

    EASY(50_000.0, 1.25, 0.75),
    MEDIUM(25_000.0, 1.0, 1.0),
    HARD(10_000.0, 0.75, 1.5);

    /** Treasury balance every newly founded city starts with (spec's loan system already exists
     *  to cover the gap when this isn't enough — see {@code RequestExternalLoanCommand}). */
    public final double startingTreasury;
    /** Multiplies {@code PopulationSystem}'s per-tick residential/workplace growth step. */
    public final double growthRateMultiplier;
    /** Multiplies every loan's interest rate (external market and inter-city alike). */
    public final double loanInterestRateMultiplier;

    Difficulty(double startingTreasury, double growthRateMultiplier, double loanInterestRateMultiplier) {
        this.startingTreasury = startingTreasury;
        this.growthRateMultiplier = growthRateMultiplier;
        this.loanInterestRateMultiplier = loanInterestRateMultiplier;
    }
}
