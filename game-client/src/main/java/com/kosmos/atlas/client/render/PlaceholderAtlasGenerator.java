package com.kosmos.atlas.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kosmos.atlas.sim.world.WorldConstants;

/**
 * Builds a tiny code-generated sprite atlas (one solid diamond per terrain type) so the project
 * compiles and runs with zero external art assets (spec §44.1: "Use sprite atlases aggressively").
 * A single {@link Texture} backs every terrain-type region, so drawing a whole visible chunk never
 * switches GL texture state (spec §44.2: minimize draw calls / batch flushes).
 *
 * <p>Replace this with a real {@code TexturePacker}-built atlas once art exists; every renderer
 * downstream only depends on the returned {@link TextureRegion} array, not on how it was built.
 */
public final class PlaceholderAtlasGenerator {

    private static final int CELL_SIZE = 32;

    private PlaceholderAtlasGenerator() {
    }

    public static final class Atlas implements com.badlogic.gdx.utils.Disposable {
        public final Texture texture;
        public final TextureRegion[] byTerrainType;

        Atlas(Texture texture, TextureRegion[] byTerrainType) {
            this.texture = texture;
            this.byTerrainType = byTerrainType;
        }

        @Override
        public void dispose() {
            texture.dispose();
        }
    }

    public static Atlas generate() {
        Color[] colors = {
            new Color(0.10f, 0.20f, 0.55f, 1f), // TERRAIN_DEEP_WATER
            new Color(0.20f, 0.45f, 0.80f, 1f), // TERRAIN_SHALLOW_WATER
            new Color(0.85f, 0.78f, 0.55f, 1f), // TERRAIN_BEACH
            new Color(0.35f, 0.65f, 0.30f, 1f), // TERRAIN_PLAIN
            new Color(0.45f, 0.55f, 0.30f, 1f), // TERRAIN_HILL
            new Color(0.55f, 0.53f, 0.52f, 1f), // TERRAIN_MOUNTAIN
        };

        int cells = colors.length;
        Pixmap pixmap = new Pixmap(cells * CELL_SIZE, CELL_SIZE, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        for (int i = 0; i < cells; i++) {
            pixmap.setColor(colors[i]);
            pixmap.fillRectangle(i * CELL_SIZE, 0, CELL_SIZE, CELL_SIZE);
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();

        TextureRegion[] regions = new TextureRegion[cells];
        for (int i = 0; i < cells; i++) {
            regions[i] = new TextureRegion(texture, i * CELL_SIZE, 0, CELL_SIZE, CELL_SIZE);
        }
        assert regions.length > WorldConstants.TERRAIN_MOUNTAIN;
        return new Atlas(texture, regions);
    }
}
