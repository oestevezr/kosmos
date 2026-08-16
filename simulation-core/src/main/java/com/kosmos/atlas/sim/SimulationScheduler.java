package com.kosmos.atlas.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs registered {@link SimSystem}s at their own declared cadence, in ticks (spec §41's cadence
 * table: local traffic every tick, district economy every ~50 ticks, regional logistics/day
 * scale much less often, etc.). A cadence of 1 runs every tick; a cadence of N runs on ticks
 * {@code tickIndex % N == 0}.
 *
 * <p>The scheduler itself never allocates once systems are registered — {@link #advance(int)}
 * only iterates a preallocated list and calls into already-existing {@link SimSystem} instances.
 */
public final class SimulationScheduler {

    private static final class Registration {
        final SimSystem system;
        final int cadenceTicks;
        final String name;

        Registration(SimSystem system, int cadenceTicks, String name) {
            this.system = system;
            this.cadenceTicks = cadenceTicks;
            this.name = name;
        }
    }

    private final List<Registration> registrations = new ArrayList<>();
    private long tickIndex;

    public void register(String name, int cadenceTicks, SimSystem system) {
        if (cadenceTicks < 1) {
            throw new IllegalArgumentException("cadenceTicks must be >= 1, was " + cadenceTicks);
        }
        registrations.add(new Registration(system, cadenceTicks, name));
    }

    public long currentTick() {
        return tickIndex;
    }

    /** Names of every registered system, in registration order — for debug/diagnostic display only. */
    public List<String> registeredSystemNames() {
        List<String> names = new ArrayList<>(registrations.size());
        for (Registration reg : registrations) {
            names.add(reg.name);
        }
        return names;
    }

    /** Advances the simulation by {@code ticks} whole ticks, running each system on its due ticks. */
    public void advance(int ticks) {
        for (int i = 0; i < ticks; i++) {
            for (int r = 0; r < registrations.size(); r++) {
                Registration reg = registrations.get(r);
                if (tickIndex % reg.cadenceTicks == 0) {
                    reg.system.tick(tickIndex);
                }
            }
            tickIndex++;
        }
    }
}
