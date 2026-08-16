package com.kosmos.atlas.sim.world;

import com.kosmos.atlas.sim.util.LongIntHashMap;

/**
 * Fixed-capacity table of currently-loaded chunks, indexed by packed {@link ChunkKey} through a
 * boxing-free {@link LongIntHashMap} (spec §42.2). Capacity is bounded by
 * {@link HardwareProfile#maxLoadedChunks()} — the whole point of chunk streaming is that this
 * never has to grow to world size (spec §43).
 */
public final class ChunkStore {

    private final Chunk[] slots;
    private final LongIntHashMap keyToSlot;
    private final int[] freeSlots;
    private int freeTop;

    public ChunkStore(int capacity) {
        slots = new Chunk[capacity];
        keyToSlot = new LongIntHashMap(capacity, 0.6f);
        freeSlots = new int[capacity];
        for (int i = 0; i < capacity; i++) {
            freeSlots[i] = capacity - 1 - i;
        }
        freeTop = capacity;
    }

    public int capacity() {
        return slots.length;
    }

    public int loadedCount() {
        return slots.length - freeTop;
    }

    public boolean hasFreeSlot() {
        return freeTop > 0;
    }

    public Chunk get(int chunkX, int chunkY) {
        int slot = keyToSlot.get(ChunkKey.pack(chunkX, chunkY), -1);
        return slot < 0 ? null : slots[slot];
    }

    /** Registers an already-generated chunk into the store. Caller must check {@link #hasFreeSlot()} first. */
    public void put(Chunk chunk) {
        if (freeTop == 0) {
            throw new IllegalStateException("ChunkStore is full (capacity=" + slots.length + ")");
        }
        int slot = freeSlots[--freeTop];
        slots[slot] = chunk;
        keyToSlot.put(ChunkKey.pack(chunk.chunkX(), chunk.chunkY()), slot);
    }

    /** Evicts a chunk, returning it so the caller can release it back to a {@link ChunkPool}. */
    public Chunk remove(int chunkX, int chunkY) {
        long key = ChunkKey.pack(chunkX, chunkY);
        int slot = keyToSlot.remove(key);
        if (slot < 0) {
            return null;
        }
        Chunk chunk = slots[slot];
        slots[slot] = null;
        freeSlots[freeTop++] = slot;
        return chunk;
    }

    public boolean contains(int chunkX, int chunkY) {
        return keyToSlot.containsKey(ChunkKey.pack(chunkX, chunkY));
    }

    /** Iterates all currently loaded chunks without allocating an iterator/collection. */
    public void forEach(ChunkVisitor visitor) {
        for (Chunk chunk : slots) {
            if (chunk != null) {
                visitor.visit(chunk);
            }
        }
    }

    @FunctionalInterface
    public interface ChunkVisitor {
        void visit(Chunk chunk);
    }
}
