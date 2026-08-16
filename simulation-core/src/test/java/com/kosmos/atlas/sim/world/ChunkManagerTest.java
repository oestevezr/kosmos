package com.kosmos.atlas.sim.world;

import com.kosmos.atlas.sim.world.gen.ProceduralGenerator;
import com.kosmos.atlas.sim.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link ChunkManager}'s streaming/eviction loop, including the allocation-avoidance
 * rewrite of {@link ChunkManager#updateFocus(int, int)}: repeated calls with an unchanged focus
 * must be safe no-ops, and a real focus change must still load and evict the right chunks.
 */
class ChunkManagerTest {

    private static ChunkManager newManager(HardwareProfile profile) {
        WorldGenSettings settings = WorldGenSettings.balanced(1L, 4096);
        return new ChunkManager(new ProceduralGenerator(settings), profile, settings.worldSizeTiles);
    }

    private static void pumpUntil(ChunkManager manager, java.util.function.BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            manager.integrateReadyChunks(64);
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test
    void updateFocusLoadsChunksWithinPreloadRadius() {
        try (ChunkManager manager = newManager(HardwareProfile.LOW)) {
            manager.updateFocus(0, 0);
            // Several chunks race to load concurrently across worker threads (priority order is
            // respected for dequeue, but not for completion) — wait for the specific focus chunk,
            // not just "any chunk," to avoid a flaky race against a same-priority neighbor.
            pumpUntil(manager, () -> manager.store().contains(0, 0), 5000);
            assertTrue(manager.store().contains(0, 0), "the focus chunk itself should load");
        }
    }

    @Test
    void repeatedIdenticalFocusCallsAreNoOpsAndDoNotBreakStreaming() {
        try (ChunkManager manager = newManager(HardwareProfile.LOW)) {
            manager.updateFocus(0, 0);
            for (int i = 0; i < 50; i++) {
                manager.updateFocus(0, 0); // must be a cheap no-op, not re-trigger the eviction scan
            }
            pumpUntil(manager, () -> manager.store().contains(0, 0), 5000);
            assertTrue(manager.store().contains(0, 0));
        }
    }

    @Test
    void movingFocusFarAwayEvictsChunksOutsideUnloadRadius() {
        try (ChunkManager manager = newManager(HardwareProfile.LOW)) {
            manager.updateFocus(0, 0);
            pumpUntil(manager, () -> manager.store().contains(0, 0), 5000);
            assertTrue(manager.store().contains(0, 0));

            int farChunks = HardwareProfile.LOW.unloadRadius * 10;
            manager.updateFocus(farChunks, farChunks);
            // Eviction happens synchronously inside updateFocus, before any new generation.
            assertEquals(false, manager.store().contains(0, 0), "chunk (0,0) must be evicted once far outside the unload radius");
        }
    }
}
