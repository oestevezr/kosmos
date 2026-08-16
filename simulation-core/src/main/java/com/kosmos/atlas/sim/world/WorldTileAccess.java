package com.kosmos.atlas.sim.world;

/**
 * Resolves an absolute world-tile coordinate to its owning {@link Chunk} and local tile index.
 * Shared by any system that needs to walk across chunk boundaries (road access, utility
 * flood-fill) instead of each reimplementing the same {@code floorDiv}/{@code floorMod} pair.
 */
public final class WorldTileAccess {

    private WorldTileAccess() {
    }

    /** Returns the chunk owning {@code (worldTileX, worldTileY)}, or {@code null} if it isn't currently loaded. */
    public static Chunk chunkAt(ChunkStore store, int worldTileX, int worldTileY) {
        int chunkX = Math.floorDiv(worldTileX, WorldConstants.CHUNK_SIZE);
        int chunkY = Math.floorDiv(worldTileY, WorldConstants.CHUNK_SIZE);
        return store.get(chunkX, chunkY);
    }

    public static int localIndexAt(int worldTileX, int worldTileY) {
        int localX = Math.floorMod(worldTileX, WorldConstants.CHUNK_SIZE);
        int localY = Math.floorMod(worldTileY, WorldConstants.CHUNK_SIZE);
        return Chunk.tileIndex(localX, localY);
    }
}
