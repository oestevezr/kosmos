package com.kosmos.atlas.client.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.util.HashSet;
import java.util.Set;

/**
 * Draws only the chunks the camera can currently see (spec §44.4 "Camera Culling"), at a single
 * visual LOD ("STREET" — one quad per tile). Coarser LODs (§44.5: DISTRICT/CITY/REGIONAL) are a
 * declared extension point for a later phase once zoom controls and simplified chunk textures
 * exist; wiring the LOD switch in here now would be premature before there's a second LOD to
 * switch to.
 *
 * <p>Each visible chunk's terrain is a static {@link ChunkMesh} rebuilt only when its version
 * changes (spec §44.3), rather than 1024 fresh {@code SpriteBatch.draw} calls submitted every
 * single frame regardless of whether anything changed. The shader and texture bind happen once
 * per frame, outside the chunk loop, matching spec §44.2's "minimize draw calls and GPU state
 * changes" — every chunk mesh shares the same bound atlas texture, so nothing here ever flushes
 * a batch mid-scene.
 */
public final class WorldRenderer implements com.badlogic.gdx.utils.Disposable {

    /** Chunks beyond this radius (in world tiles) around the visible rect are not even considered. */
    private static final int CULL_MARGIN_TILES = WorldConstants.CHUNK_SIZE * 2;

    private final PlaceholderAtlasGenerator.Atlas atlas;
    private final ChunkRenderCache renderCache;
    private final ShaderProgram shader;

    private int lastVisibleChunkCount;
    private int lastDrawnTileCount;

    public WorldRenderer() {
        this.atlas = PlaceholderAtlasGenerator.generate();
        this.renderCache = new ChunkRenderCache(atlas);
        this.shader = SpriteBatch.createDefaultShader();
    }

    public int lastVisibleChunkCount() {
        return lastVisibleChunkCount;
    }

    public int lastDrawnTileCount() {
        return lastDrawnTileCount;
    }

    public void render(OrthographicCamera camera, ChunkStore chunkStore) {
        renderCache.beginFrame();

        int[] range = visibleChunkRange(camera);
        int minCx = range[0];
        int minCy = range[1];
        int maxCx = range[2];
        int maxCy = range[3];

        Set<Long> stillVisible = new HashSet<>();
        int visibleChunks = 0;
        int drawnTiles = 0;

        shader.bind();
        shader.setUniformMatrix("u_projTrans", camera.combined);
        atlas.texture.bind(0);
        shader.setUniformi("u_texture", 0);

        for (int cy = minCy; cy <= maxCy; cy++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                Chunk chunk = chunkStore.get(cx, cy);
                if (chunk == null) {
                    continue; // not generated/loaded yet — nothing to draw, not an error
                }
                visibleChunks++;
                stillVisible.add((((long) cx) << 32) | (cy & 0xFFFFFFFFL));

                ChunkMesh mesh = renderCache.getOrBuild(chunk);
                if (mesh == null) {
                    continue; // rebuild-budget exhausted this frame; draw it next frame instead
                }
                mesh.render(shader);
                drawnTiles += WorldConstants.TILES_PER_CHUNK;
            }
        }
        renderCache.evictExcept(stillVisible);

        lastVisibleChunkCount = visibleChunks;
        lastDrawnTileCount = drawnTiles;
    }

    /** Projects the camera's visible rectangle back into chunk-coordinate space (spec §44.4). */
    private int[] visibleChunkRange(OrthographicCamera camera) {
        float halfWidth = camera.viewportWidth * camera.zoom * 0.5f + CULL_MARGIN_TILES;
        float halfHeight = camera.viewportHeight * camera.zoom * 0.5f + CULL_MARGIN_TILES;

        float left = camera.position.x - halfWidth;
        float right = camera.position.x + halfWidth;
        float top = camera.position.y + halfHeight;
        float bottom = camera.position.y - halfHeight;

        float minTx = Float.MAX_VALUE, maxTx = -Float.MAX_VALUE;
        float minTy = Float.MAX_VALUE, maxTy = -Float.MAX_VALUE;
        float[][] corners = {{left, top}, {right, top}, {left, bottom}, {right, bottom}};
        for (float[] corner : corners) {
            float tx = IsoProjection.worldTileXApprox(corner[0], corner[1]);
            float ty = IsoProjection.worldTileYApprox(corner[0], corner[1]);
            minTx = Math.min(minTx, tx);
            maxTx = Math.max(maxTx, tx);
            minTy = Math.min(minTy, ty);
            maxTy = Math.max(maxTy, ty);
        }

        int minCx = Math.floorDiv((int) Math.floor(minTx), WorldConstants.CHUNK_SIZE) - 1;
        int maxCx = Math.floorDiv((int) Math.ceil(maxTx), WorldConstants.CHUNK_SIZE) + 1;
        int minCy = Math.floorDiv((int) Math.floor(minTy), WorldConstants.CHUNK_SIZE) - 1;
        int maxCy = Math.floorDiv((int) Math.ceil(maxTy), WorldConstants.CHUNK_SIZE) + 1;
        return new int[] {minCx, minCy, maxCx, maxCy};
    }

    @Override
    public void dispose() {
        shader.dispose();
        renderCache.dispose();
        atlas.dispose();
    }
}
