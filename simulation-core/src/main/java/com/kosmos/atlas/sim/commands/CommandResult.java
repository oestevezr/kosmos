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
    REJECTED_TOO_CLOSE_TO_ANOTHER_CITY
}
