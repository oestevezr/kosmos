package com.kosmos.atlas.sim;

import com.kosmos.atlas.sim.util.Histogram;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces spec §42.4: "Hot rendering and simulation loops should approach zero temporary heap
 * allocations" after warm-up. Uses {@code com.sun.management.ThreadMXBean#getThreadAllocatedBytes}
 * (HotSpot-specific but present on every mainstream desktop/CI JVM this project targets) to
 * measure exactly how many bytes the current thread allocates while driving the scheduler's hot
 * loop, with no chunk generation or I/O involved.
 */
class AllocationBudgetTest {

    private static final long MAX_BYTES_PER_TICK = 512; // generous slack over autoboxing-free code

    @Test
    void schedulerAdvanceIsEffectivelyAllocationFree() {
        com.sun.management.ThreadMXBean bean =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported(), "Test JVM must support per-thread allocation counters");
        bean.setThreadAllocatedMemoryEnabled(true);

        SimulationScheduler scheduler = new SimulationScheduler();
        Histogram histogram = new Histogram();
        long[] counter = new long[1];
        scheduler.register("counter", 1, tick -> {
            counter[0]++;
            histogram.record(tick);
        });
        scheduler.register("every-5", 5, tick -> counter[0]++);

        long threadId = Thread.currentThread().getId();

        // Warm-up: let the JIT compile the hot path before measuring.
        scheduler.advance(20_000);

        long before = bean.getThreadAllocatedBytes(threadId);
        int measuredTicks = 100_000;
        scheduler.advance(measuredTicks);
        long after = bean.getThreadAllocatedBytes(threadId);

        long bytesPerTick = (after - before) / measuredTicks;
        assertTrue(bytesPerTick <= MAX_BYTES_PER_TICK,
            "Steady-state scheduler.advance() allocated " + bytesPerTick + " bytes/tick, budget is "
                + MAX_BYTES_PER_TICK);
    }
}
