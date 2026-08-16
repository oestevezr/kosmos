package com.kosmos.atlas.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

/**
 * A chunk's terrain baked into one static GPU-resident {@link Mesh} — the actual "Chunk Render
 * Cache" spec §44.3 describes, as opposed to {@code ChunkRenderData}'s predecessor, which only
 * cached the CPU-side screen positions and still re-submitted a fresh {@code SpriteBatch.draw}
 * call (and fresh vertex data) for all 1024 tiles every single frame regardless of whether
 * anything changed. Now a chunk's vertex data is written to the GPU once, on
 * {@link #rebuild(Chunk, PlaceholderAtlasGenerator.Atlas)}, and reused by {@link #render(ShaderProgram)}
 * every subsequent frame until the chunk's version changes again.
 *
 * <p>Vertex layout matches {@code SpriteBatch}'s own default (Position2, ColorPacked, TexCoords2)
 * so rendering can reuse {@link com.badlogic.gdx.graphics.g2d.SpriteBatch#createDefaultShader()}
 * instead of hand-writing a GLSL program — one fewer thing to get wrong by hand (spec §44.2:
 * minimize state changes, not reinvent the pipeline).
 */
final class ChunkMesh implements com.badlogic.gdx.utils.Disposable {

    private static final int FLOATS_PER_VERTEX = 5; // x, y, packedColor, u, v
    private static final int VERTICES_PER_TILE = 4;
    private static final int INDICES_PER_TILE = 6;
    private static final float WHITE_BITS = Color.WHITE.toFloatBits();

    private final Mesh mesh;
    private final float[] vertexScratch = new float[WorldConstants.TILES_PER_CHUNK * VERTICES_PER_TILE * FLOATS_PER_VERTEX];

    int builtVersion = -1;

    ChunkMesh() {
        int maxVertices = WorldConstants.TILES_PER_CHUNK * VERTICES_PER_TILE;
        int maxIndices = WorldConstants.TILES_PER_CHUNK * INDICES_PER_TILE;
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

    void rebuild(Chunk chunk, PlaceholderAtlasGenerator.Atlas atlas) {
        int baseX = chunk.chunkX() * WorldConstants.CHUNK_SIZE;
        int baseY = chunk.chunkY() * WorldConstants.CHUNK_SIZE;
        float halfW = IsoProjection.TILE_WIDTH_PX * 0.5f;
        float halfH = IsoProjection.TILE_HEIGHT_PX * 0.5f;

        int v = 0;
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

                TextureRegion region = atlas.byTerrainType[chunk.terrainType[idx]];
                float u = region.getU();
                float v0 = region.getV();
                float u2 = region.getU2();
                float v2 = region.getV2();

                // Same vertex order/UV assignment as SpriteBatch.draw(region, x, y, w, h):
                // bottom-left, top-left, top-right, bottom-right.
                v = putVertex(vertexScratch, v, x0, y0, u, v2);
                v = putVertex(vertexScratch, v, x0, y1, u, v0);
                v = putVertex(vertexScratch, v, x1, y1, u2, v0);
                v = putVertex(vertexScratch, v, x1, y0, u2, v2);
            }
        }
        mesh.setVertices(vertexScratch);
        builtVersion = chunk.version();
    }

    private static int putVertex(float[] out, int offset, float x, float y, float u, float v) {
        out[offset++] = x;
        out[offset++] = y;
        out[offset++] = WHITE_BITS;
        out[offset++] = u;
        out[offset++] = v;
        return offset;
    }

    void render(ShaderProgram shader) {
        mesh.render(shader, GL20.GL_TRIANGLES);
    }

    @Override
    public void dispose() {
        mesh.dispose();
    }
}
