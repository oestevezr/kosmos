package com.kosmos.atlas.sim;

/**
 * Converts wall-clock frame time into a whole number of fixed-size simulation ticks (spec §25,
 * §26). Time compression (0/1/2/4/8/16x) multiplies how many ticks are run per frame, never the
 * tick's own {@code dt} — determinism must hold at any speed (spec §26: "Simulation must remain
 * deterministic enough for saves and debugging").
 *
 * <p>An accumulator with a hard cap on ticks-per-frame prevents the classic "spiral of death":
 * a long stall (GC pause, breakpoint, alt-tab) does not cause the simulation to try to catch up
 * by running thousands of ticks in one frame.
 */
public final class TimeManager {

    /** Simulated milliseconds per tick (spec §41 uses game-hour/day/etc.; 100ms is the base tick). */
    public static final long TICK_DURATION_MILLIS = 100;

    private static final int[] ALLOWED_SPEEDS = {0, 1, 2, 4, 8, 16};

    private final int maxTicksPerFrame;
    private double accumulatorSeconds;
    private int speedMultiplier = 1;
    private long totalTicks;

    public TimeManager(int maxTicksPerFrame) {
        this.maxTicksPerFrame = maxTicksPerFrame;
    }

    public void setSpeedMultiplier(int multiplier) {
        for (int allowed : ALLOWED_SPEEDS) {
            if (allowed == multiplier) {
                this.speedMultiplier = multiplier;
                return;
            }
        }
        throw new IllegalArgumentException("Unsupported speed multiplier: " + multiplier);
    }

    public int speedMultiplier() {
        return speedMultiplier;
    }

    public long totalTicks() {
        return totalTicks;
    }

    /**
     * Feeds real elapsed frame time in; returns how many ticks the caller should advance the
     * simulation by right now (0 if paused or not enough time has accumulated yet).
     */
    public int consumeFrame(double realDeltaSeconds) {
        if (speedMultiplier == 0) {
            return 0;
        }
        accumulatorSeconds += realDeltaSeconds * speedMultiplier;
        double tickSeconds = TICK_DURATION_MILLIS / 1000.0;
        int ticks = (int) (accumulatorSeconds / tickSeconds);
        ticks = Math.min(ticks, maxTicksPerFrame);
        accumulatorSeconds -= ticks * tickSeconds;
        totalTicks += ticks;
        return ticks;
    }
}
