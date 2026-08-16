package com.kosmos.atlas.sim.world;

import java.util.ArrayDeque;

/**
 * Recycles {@link Chunk} instances (and the primitive arrays inside them) instead of letting
 * unloaded chunks become garbage and loaded chunks allocate fresh (spec §42.4: "object pools for
 * transient... entities", zero hot-loop allocation after warm-up).
 *
 * <p>Not thread-safe by design — only the thread that owns a {@link ChunkManager}'s chunk store
 * should acquire/release from its pool. World workers generate into freshly-acquired chunks,
 * which are then handed off via the results queue.
 */
public final class ChunkPool {

    private final ArrayDeque<Chunk> free;

    public ChunkPool(int initialCapacity) {
        free = new ArrayDeque<>(initialCapacity);
        for (int i = 0; i < initialCapacity; i++) {
            free.push(new Chunk());
        }
    }

    public Chunk acquire(int chunkX, int chunkY) {
        Chunk chunk = free.poll();
        if (chunk == null) {
            chunk = new Chunk();
        }
        chunk.reset(chunkX, chunkY);
        return chunk;
    }

    public void release(Chunk chunk) {
        free.push(chunk);
    }

    public int pooledCount() {
        return free.size();
    }
}
