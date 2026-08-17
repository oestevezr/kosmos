package com.kosmos.atlas.sim.commands;

/**
 * Outcome of applying a {@link Command} against the authoritative simulation state (spec §38).
 * The renderer/UI never mutates state directly — it submits a command and reacts to this result.
 */
public enum CommandResult {
    ACCEPTED,
    REJECTED_OUT_OF_BOUNDS,
    REJECTED_INVALID_TERRAIN,
    REJECTED_INSUFFICIENT_RESOURCES,
    REJECTED_UNKNOWN_CHUNK,
    /** A building, road or zone already occupies the target tile in a way that conflicts with this command. */
    REJECTED_TILE_OCCUPIED,
    /** The target tile has no building/road/zone to remove. */
    REJECTED_NOTHING_TO_DEMOLISH,
    /** No city has been founded close enough (or at all) to attribute this building/policy to (spec §9). */
    REJECTED_NO_CITY_FOUNDED,
    /** A new city can't be founded this close to an existing one — territory attribution would be meaningless. */
    REJECTED_TOO_CLOSE_TO_ANOTHER_CITY,
    /** A requested/repaid loan amount was zero, negative, or above the type's cap. */
    REJECTED_INVALID_LOAN_AMOUNT,
    /** The lending city isn't prosperous enough to extend credit right now (spec's loan system). */
    REJECTED_LENDER_NOT_PROSPEROUS,
    /** A city cannot lend to itself. */
    REJECTED_SAME_CITY_LOAN,
    /** The referenced loan id doesn't exist or has already been repaid in full. */
    REJECTED_LOAN_NOT_FOUND,
    /** The owning city's treasury can't cover this construction/zoning's cost. */
    REJECTED_INSUFFICIENT_FUNDS,
    /** This building's tier requires more city population than the city currently has (spec's tiered-service system). */
    REJECTED_SERVICE_TIER_LOCKED,
    /** The lending city hasn't built a Central Bank yet — the physical prerequisite for offering inter-city credit. */
    REJECTED_LENDER_HAS_NO_CENTRAL_BANK
}
