package com.kosmos.atlas.sim.commands;

/**
 * Base type for every player intent (spec §38). A command is a request, not a mutation: the
 * simulation core evaluates terrain validity, required materials, ownership and policy rules
 * before {@link #apply} is allowed to change anything, and can reject the command outright.
 *
 * <p>Concrete commands must be cheap, reusable value carriers so they can flow through
 * {@link CommandBus}'s preallocated ring buffer without becoming a hot-loop allocation source
 * (spec §42.4).
 */
public abstract class Command {

    /** Stable identifier used by {@link CommandJournal} — must never change once shipped. */
    public abstract int typeId();

    /** Validates and, if valid, applies this command against {@code ctx}. */
    public abstract CommandResult apply(SimulationContext ctx);

    /** Serializes this command's payload (not the type id) for the journal. */
    public abstract void writePayload(java.io.DataOutput out) throws java.io.IOException;
}
