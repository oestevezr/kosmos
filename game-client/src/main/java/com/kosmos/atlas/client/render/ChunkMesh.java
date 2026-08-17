package com.kosmos.atlas.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.TimeUtils;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

/**
 * A chunk's terrain baked into one static GPU-resident {@link Mesh} — the actual "Chunk Render
 * Cache" spec §44.3 describes, as opposed to {@code ChunkRenderData}'s predecessor, which only
 * cached the CPU-side screen positions and still re-submitted a fresh {@code SpriteBatch.draw}
 * call (and fresh vertex data) for all 1024 tiles every single frame regardless of whether
 * anything changed. Now a chunk's vertex data is written to the GPU once, on
 * {@link #rebuild(Chunk, PlaceholderAtlasGenerator.Atlas, BuildingRegistry)}, and reused by {@link #render(ShaderProgram)}
 * every subsequent frame until the chunk's version changes again.
 *
 * <p>Vertex layout matches {@code SpriteBatch}'s own default (Position2, ColorPacked, TexCoords2).
 * {@link WorldRenderer} used to reuse {@code SpriteBatch.createDefaultShader()} verbatim; it now
 * builds an almost-identical shader ({@link ChunkShaderSource}) with one added uniform
 * ({@code u_chunkAlpha}) for the fade-in described below — still hand-written to a minimum, not a
 * general-purpose shader (spec §44.2: minimize state changes, not reinvent the pipeline).
 *
 * <p>Each tile always contributes a terrain quad, plus at most one overlay quad on top — a road,
 * or a building (colored by category), or an empty zoned lot's semi-transparent tint, in that
 * priority order (see {@link #overlayRegionFor}) — never more than one, since in practice a tile is
 * exactly one of those three (spec's zoning model keeps roads/lots on separate tiles). Quad count
 * per chunk is therefore variable, between {@code TILES_PER_CHUNK} (all bare terrain) and
 * {@code TILES_PER_CHUNK * 2} (every tile has an overlay) — {@link #MAX_QUADS_PER_CHUNK} sizes the
 * scratch buffer/GPU mesh for the worst case, and {@link #quadCount} tracks the real count per
 * rebuild so {@link #render(ShaderProgram)} only submits the indices actually written.
 *
 * <p>Overlay presence follows only {@code Chunk.zoneType}/{@code roadType}/{@code buildingId} —
 * all three bump {@code Chunk.version()} on every mutating command, the same version this class
 * already keys rebuilds on, so no extra dirty-tracking is needed. A building's color depends only
 * on its (immutable-once-built) {@code BuildingType}, never on density/population, so this stays
 * true even though {@code BuildingRegistry} mutations don't themselves touch {@code Chunk}.
 *
 * <p>{@link #currentAlpha()} drives a short fade-in the very first time this chunk becomes visible
 * (streaming a new chunk in should be a soft transition, not an instant pop) — {@link #spawnedAtMillis}
 * is stamped once, in the constructor, and never touched by {@link #rebuild}, so editing an
 * already-loaded chunk's content (a road, a new building) never re-triggers the fade.
 */
final class ChunkMesh implements com.badlogic.gdx.utils.Disposable {

    private static final int FLOATS_PER_VERTEX = 5; // x, y, packedColor, u, v
    private static final int VERTICES_PER_TILE = 4;
    private static final int INDICES_PER_TILE = 6;
    private static final int MAX_QUADS_PER_CHUNK = WorldConstants.TILES_PER_CHUNK * 2;
    private static final float WHITE_BITS = Color.WHITE.toFloatBits();
    private static final long FADE_DURATION_MILLIS = 300;

    private final Mesh mesh;
    private final float[] vertexScratch = new float[MAX_QUADS_PER_CHUNK * VERTICES_PER_TILE * FLOATS_PER_VERTEX];
    private final long spawnedAtMillis = TimeUtils.millis();

    int builtVersion = -1;
    private int quadCount;

    ChunkMesh() {
        int maxVertices = MAX_QUADS_PER_CHUNK * VERTICES_PER_TILE;
        int maxIndices = MAX_QUADS_PER_CHUNK * INDICES_PER_TILE;
        mesh = new Mesh(true, maxVertices, maxIndices,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"));

        short[] indices = new short[maxIndices];
        short vertex = 0;
        for (int i = 0; i < maxIndices; i += INDICES_PER_TILE, vertex += VERTICES_PER_TILE) {
            indices[i] = vertex;
            indices[i + 1] = (short) (vertex + 1);
            indices[i + 2] = (short) (vertex + 2);
            indices[i + 3] = (short) (vertex + 2);
            indices[i + 4] = (short) (vertex + 3);
            indices[i + 5] = vertex;
        }
        mesh.setIndices(indices); // fixed forever — only setVertices() changes on rebuild
    }

    void rebuild(Chunk chunk, PlaceholderAtlasGenerator.Atlas atlas, BuildingRegistry buildings) {
        int baseX = chunk.chunkX() * WorldConstants.CHUNK_SIZE;
        int baseY = chunk.chunkY() * WorldConstants.CHUNK_SIZE;
        float halfW = IsoProjection.TILE_WIDTH_PX * 0.5f;
        float halfH = IsoProjection.TILE_HEIGHT_PX * 0.5f;

        int v = 0;
        int quads = 0;
        for (int ly = 0; ly < WorldConstants.CHUNK_SIZE; ly++) {
            for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
                int idx = Chunk.tileIndex(lx, ly);
                int wx = baseX + lx;
                int wy = baseY + ly;
                float sx = IsoProjection.screenX(wx, wy);
                float sy = IsoProjection.screenY(wx, wy, chunk.elevation[idx]);

                float x0 = sx - halfW;
                float y0 = sy - halfH;
                float x1 = sx + halfW;
                float y1 = sy + halfH;

                TextureRegion terrainRegion = atlas.byTerrainType[chunk.terrainType[idx]];
                v = putQuad(vertexScratch, v, x0, y0, x1, y1, terrainRegion);
                quads++;

                TextureRegion overlayRegion = overlayRegionFor(chunk, idx, atlas, buildings);
                if (overlayRegion != null) {
                    v = putQuad(vertexScratch, v, x0, y0, x1, y1, overlayRegion);
                    quads++;
                }
            }
        }
        mesh.setVertices(vertexScratch, 0, v);
        quadCount = quads;
        builtVersion = chunk.version();
    }

    /** Road beats building beats empty-zoned-lot — see class javadoc for why only one ever applies
     *  in practice. Returns {@code null} for a bare tile (no overlay quad emitted). */
    private static TextureRegion overlayRegionFor(Chunk chunk, int idx, PlaceholderAtlasGenerator.Atlas atlas,
                                                    BuildingRegistry buildings) {
        byte road = chunk.roadType[idx];
        if (road != WorldConstants.ROAD_NONE) {
            return atlas.byRoadType[road];
        }
        int buildingId = chunk.buildingId[idx];
        if (buildingId != WorldConstants.NO_BUILDING && buildings.isActive(buildingId)) {
            int category = PlaceholderAtlasGenerator.buildingCategoryIndex(buildings.type(buildingId));
            return atlas.byBuildingCategory[category];
        }
        byte zone = chunk.zoneType[idx];
        if (zone != WorldConstants.ZONE_NONE) {
            return atlas.byZoneType[zone];
        }
        return null;
    }

    private static int putQuad(float[] out, int offset, float x0, float y0, float x1, float y1, TextureRegion region) {
        float u = region.getU();
        float v0 = region.getV();
        float u2 = region.getU2();
        float v2 = region.getV2();
        // Same vertex order/UV assignment as SpriteBatch.draw(region, x, y, w, h):
        // bottom-left, top-left, top-right, bottom-right.
        offset = putVertex(out, offset, x0, y0, u, v2);
        offset = putVertex(out, offset, x0, y1, u, v0);
        offset = putVertex(out, offset, x1, y1, u2, v0);
        offset = putVertex(out, offset, x1, y0, u2, v2);
        return offset;
    }

    private static int putVertex(float[] out, int offset, float x, float y, float u, float v) {
        out[offset++] = x;
        out[offset++] = y;
        out[offset++] = WHITE_BITS;
        out[offset++] = u;
        out[offset++] = v;
        return offset;
    }

    /** Linear fade from 0 to 1 over {@link #FADE_DURATION_MILLIS} since this mesh was first
     *  created — see class javadoc for why this only ever fires once per chunk's lifetime. */
    float currentAlpha() {
        float t = TimeUtils.timeSinceMillis(spawnedAtMillis) / (float) FADE_DURATION_MILLIS;
        return Math.max(0f, Math.min(1f, t));
    }

    void render(ShaderProgram shader) {
        mesh.render(shader, GL20.GL_TRIANGLES, 0, quadCount * INDICES_PER_TILE);
    }

    @Override
    public void dispose() {
        mesh.dispose();
    }
}
