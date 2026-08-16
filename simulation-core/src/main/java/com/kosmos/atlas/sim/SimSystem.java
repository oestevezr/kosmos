package com.kosmos.atlas.sim;

/**
 * A schedulable simulation subsystem. Each system declares its own cadence when registered with
 * {@link SimulationScheduler} instead of running every tick — spec §41: "Not every subsystem
 * deserves the same update frequency," and "optimize work avoided, not merely make every
 * calculation faster."
 */
@FunctionalInterface
public interface SimSystem {
    void tick(long tickIndex);
}
