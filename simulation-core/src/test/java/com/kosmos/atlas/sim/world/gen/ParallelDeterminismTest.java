package com.kosmos.atlas.sim.world.gen;

import com.kosmos.atlas.sim.world.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies generation is stateless with respect to thread and call order (spec §32, §40 "WORLD
 * WORKERS"). A batch of chunks generated sequentially must byte-for-byte match the same batch
 * generated concurrently across multiple worker threads, in an arbitrary/racy order.
 */
class ParallelDeterminismTest {

    private static final long SEED = 90210L;
    private static final int BATCH = 24;

    @Test
    void concurrentGenerationMatchesSequentialGeneration() throws Exception {
        WorldGenSettings settings = WorldGenSettings.balanced(SEED, 4096);

        long[] sequentialHashes = new long[BATCH];
        ProceduralGenerator sequentialGenerator = new ProceduralGenerator(settings);
        for (int i = 0; i < BATCH; i++) {
            Chunk chunk = new Chunk();
            chunk.reset(i, i * 3 - 5);
            sequentialGenerator.generate(chunk);
            sequentialHashes[i] = GoldenChunkHashTest.fnv1a64(chunk);
        }

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<long[]>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < BATCH; i++) {
                final int idx = i;
                tasks.add(() -> {
                    // Each task builds its own generator instance to also prove no shared mutable state
                    // is required across threads.
                    ProceduralGenerator generator = new ProceduralGenerator(settings);
                    Chunk chunk = new Chunk();
                    chunk.reset(idx, idx * 3 - 5);
                    generator.generate(chunk);
                    return new long[] {idx, GoldenChunkHashTest.fnv1a64(chunk)};
                });
            }
            List<Future<long[]>> futures = pool.invokeAll(tasks);
            for (Future<long[]> f : futures) {
                long[] result = f.get();
                int idx = (int) result[0];
                long hash = result[1];
                assertEquals(sequentialHashes[idx], hash,
                    "Chunk " + idx + " differs between sequential and concurrent generation");
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
