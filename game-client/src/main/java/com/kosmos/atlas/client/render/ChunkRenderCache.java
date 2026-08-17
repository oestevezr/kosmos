package com.kosmos.atlas.client.render;

import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.world.Chunk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Owns one {@link ChunkMesh} per currently-visible chunk (spec §44.3). The number of visible
 * chunks is small and bounded by the active/preload radius (spec §27), so a plain
 * {@code HashMap<Long, ...>} here is not the hot path {@code simulation-core} has to avoid —
 * unlike the world's own chunk index, this map holds at most a few dozen entries.
 *
 * <p>A per-frame rebuild budget ({@link #maxRebuildsPerFrame}) caps how many dirty chunks get a
 * new mesh built in a single frame, so a burst of newly-streamed-in chunks cannot itself cause a
 * frame-time spike (spec §45).
 */
public final class ChunkRenderCache implements com.badlogic.gdx.utils.Disposable {

    private final Map<Long, ChunkMesh> cache = new HashMap<>();
    private final PlaceholderAtlasGenerator.Atlas atlas;
    private int maxRebuildsPerFrame = 6;
    private int rebuildsThisFrame;

    public ChunkRenderCache(PlaceholderAtlasGenerator.Atlas atlas) {
        this.atlas = atlas;
    }

    public void beginFrame() {
        rebuildsThisFrame = 0;
    }

    public void setMaxRebuildsPerFrame(int max) {
        this.maxRebuildsPerFrame = max;
    }

    /**
     * Returns the mesh for {@code chunk}, rebuilding it if stale and budget remains. Returns
     * {@code null} only if the mesh is stale and this frame's rebuild budget is exhausted — the
     * caller should simply skip drawing that chunk this frame and try again next frame.
     */
    public ChunkMesh getOrBuild(Chunk chunk, BuildingRegistry buildings) {
        long key = (((long) chunk.chunkX()) << 32) | (chunk.chunkY() & 0xFFFFFFFFL);
        ChunkMesh mesh = cache.get(key);
        if (mesh != null && mesh.builtVersion == chunk.version()) {
            return mesh;
        }
        if (rebuildsThisFrame >= maxRebuildsPerFrame) {
            return mesh; // may be null (never built) or stale — caller decides what to do
        }
        if (mesh == null) {
            mesh = new ChunkMesh();
            cache.put(key, mesh);
        }
        mesh.rebuild(chunk, atlas, buildings);
        rebuildsThisFrame++;
        return mesh;
    }

    /**
     * Drops (and disposes the GPU mesh of) cached data for chunks that are no longer loaded, so
     * neither the cache nor GPU memory grows unbounded as the player explores.
     */
    public void evictExcept(java.util.Set<Long> stillLoadedKeys) {
        Iterator<Map.Entry<Long, ChunkMesh>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ChunkMesh> entry = it.next();
            if (!stillLoadedKeys.contains(entry.getKey())) {
                entry.getValue().dispose();
                it.remove();
            }
        }
    }

    @Override
    public void dispose() {
        for (ChunkMesh mesh : cache.values()) {
            mesh.dispose();
        }
        cache.clear();
    }
}
