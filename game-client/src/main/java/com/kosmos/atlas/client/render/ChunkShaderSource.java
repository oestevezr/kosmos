package com.kosmos.atlas.client.render;

/**
 * GLSL source for the shader {@link WorldRenderer} uses to draw every {@link ChunkMesh} — almost
 * identical to {@code SpriteBatch.createDefaultShader()}'s own source, plus one added uniform,
 * {@code u_chunkAlpha}, multiplied into the fragment's alpha channel. That uniform drives
 * {@link ChunkMesh#currentAlpha()}'s streaming fade-in (see its javadoc) without touching per-vertex
 * color data or forcing a mesh rebuild every frame during the fade.
 *
 * <p>Inline source, not an external {@code .glsl}/asset file — same "everything code-generated, no
 * art pipeline" convention {@link PlaceholderAtlasGenerator} already established.
 */
final class ChunkShaderSource {

    static final String VERTEX = """
        attribute vec4 a_position;
        attribute vec4 a_color;
        attribute vec2 a_texCoord0;
        uniform mat4 u_projTrans;
        varying vec4 v_color;
        varying vec2 v_texCoords;
        void main() {
            v_color = a_color;
            v_texCoords = a_texCoord0;
            gl_Position = u_projTrans * a_position;
        }
        """;

    static final String FRAGMENT = """
        #ifdef GL_ES
        #define LOWP lowp
        precision mediump float;
        #else
        #define LOWP
        #endif
        varying LOWP vec4 v_color;
        varying vec2 v_texCoords;
        uniform sampler2D u_texture;
        uniform float u_chunkAlpha;
        void main() {
            vec4 texColor = texture2D(u_texture, v_texCoords);
            gl_FragColor = vec4(v_color.rgb * texColor.rgb, v_color.a * texColor.a * u_chunkAlpha);
        }
        """;

    private ChunkShaderSource() {
    }
}
