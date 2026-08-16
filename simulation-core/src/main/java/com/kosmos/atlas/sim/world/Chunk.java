package com.kosmos.atlas.sim.world;

import java.util.Arrays;

/**
 * A single 32x32 tile chunk stored as parallel primitive arrays — a Structure of Arrays, not an
 * array of {@code Tile} objects (spec §42.1/§42.2). At ~11 KB per chunk, a 7x7 loaded window
 * (spec §27) costs well under 1 MB, independent of total world size (spec §43).
 *
 * <p>Instances are recycled by {@link ChunkPool}; a chunk's arrays are never reallocated across
 * its lifetime, only their contents overwritten, so loading/unloading chunks steady-state is
 * allocation-free (spec §42.4).
 */
public final class Chunk {

    public final byte[] terrainType = new byte[WorldConstants.TILES_PER_CHUNK];
    public final byte[] biome = new byte[WorldConstants.TILES_PER_CHUNK];
    public final byte[] fertility = new byte[WorldConstants.TILES_PER_CHUNK];
    public final byte[] moisture = new byte[WorldConstants.TILES_PER_CHUNK];
    public final byte[] temperature = new byte[WorldConstants.TILES_PER_CHUNK];
    public final short[] elevation = new short[WorldConstants.TILES_PER_CHUNK];
    public final int[] resourceFlags = new int[WorldConstants.TILES_PER_CHUNK];

    /** Player-placed layers (spec §33 MVP 0.2). Unlike the natural layers above, nothing
     *  regenerates these from the seed, so {@link #reset(int, int)} must zero them explicitly
     *  when a pooled instance is recycled for a different coordinate (spec §42.4). */
    public final byte[] zoneType = new byte[WorldConstants.TILES_PER_CHUNK];
    public final byte[] roadType = new byte[WorldConstants.TILES_PER_CHUNK];
    public final int[] buildingId = new int[WorldConstants.TILES_PER_CHUNK];
    public final byte[] serviceFlags = new byte[WorldConstants.TILES_PER_CHUNK];

    private int chunkX;
    private int chunkY;

    /** Incremented on every mutation; the render cache treats a version bump as "dirty". */
    private int version;

    /** True once modified by the player — governs delta persistence (spec §10). */
    private boolean dirty;

    /**
     * Re-initializes this instance for reuse (spec §42.4 pooling). Public so {@link ChunkPool}
     * and tests can construct/recycle chunks directly; ordinary game code should go through
     * {@code ChunkPool.acquire} rather than calling this on an already-loaded chunk.
     */
    public void reset(int chunkX, int chunkY) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.version = 0;
        this.dirty = false;
        // Natural layers are unconditionally overwritten by ProceduralGenerator.generate() for
        // every tile, so they don't need clearing here. Player-placed layers are not — a pooled
        // chunk being recycled for a new coordinate must not leak the previous occupant's roads,
        // zones or buildings into virgin terrain.
        Arrays.fill(zoneType, WorldConstants.ZONE_NONE);
        Arrays.fill(roadType, WorldConstants.ROAD_NONE);
        Arrays.fill(buildingId, WorldConstants.NO_BUILDING);
        Arrays.fill(serviceFlags, (byte) 0);
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkY() {
        return chunkY;
    }

    public int version() {
        return version;
    }

    public boolean isDirty() {
        return dirty;
    }

    public static int tileIndex(int localX, int localY) {
        return localY * WorldConstants.CHUNK_SIZE + localX;
    }

    /** Marks the chunk as mutated by generation or a player command; bumps the render-dirty version. */
    public void markMutated() {
        version++;
    }

    /** Marks the chunk as containing player modifications that must be persisted as a delta. */
    public void markDirty() {
        dirty = true;
        markMutated();
    }
}
