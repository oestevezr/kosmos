package com.kosmos.atlas.client.presentation;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kosmos.atlas.client.camera.WorldCameraController;
import com.kosmos.atlas.client.render.WorldRenderer;
import com.kosmos.atlas.client.ui.DebugOverlay;
import com.kosmos.atlas.sim.WorldManager;
import com.kosmos.atlas.sim.world.HardwareProfile;
import com.kosmos.atlas.sim.world.gen.WorldGenSettings;

/**
 * Top-level libGDX application. Deliberately thin (spec §36.2: "libGDX as a thin
 * presentation/platform layer, not as the architecture of the simulation") — its only jobs are
 * wiring the {@link WorldManager}, driving the fixed-camera-focus streaming update, and handing
 * off drawing to {@link WorldRenderer}/{@link DebugOverlay}. No gameplay or simulation rule lives
 * in this class.
 */
public final class AtlasGame extends ApplicationAdapter {

    private static final long DEMO_SEED = 819234L;
    private static final int DEMO_WORLD_SIZE_TILES = 2048; // "Medium" preset (spec §3)

    private WorldManager world;
    private OrthographicCamera camera;
    private WorldCameraController cameraController;
    private WorldRenderer worldRenderer;
    private DebugOverlay debugOverlay;
    private boolean showDebugOverlay = true;

    @Override
    public void create() {
        WorldGenSettings genSettings = WorldGenSettings.balanced(DEMO_SEED, DEMO_WORLD_SIZE_TILES);
        world = new WorldManager(genSettings, HardwareProfile.MEDIUM);

        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cameraController = new WorldCameraController();
        worldRenderer = new WorldRenderer();
        debugOverlay = new DebugOverlay();
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.25f); // clamp against alt-tab spikes

        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            showDebugOverlay = !showDebugOverlay;
        }

        cameraController.update(camera, delta);

        int focusTileX = (int) (camera.position.x / com.kosmos.atlas.client.render.IsoProjection.TILE_WIDTH_PX);
        int focusTileY = (int) (camera.position.y / com.kosmos.atlas.client.render.IsoProjection.TILE_HEIGHT_PX);
        world.updateCameraFocus(focusTileX, focusTileY);
        world.update(delta);

        ScreenUtils.clear(0.06f, 0.07f, 0.09f, 1f, true);
        Gdx.gl.glEnable(GL20.GL_BLEND);

        worldRenderer.render(camera, world.chunkManager().store());
        debugOverlay.update(delta);
        if (showDebugOverlay) {
            debugOverlay.render(world, worldRenderer.lastVisibleChunkCount(), worldRenderer.lastDrawnTileCount());
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
    }

    @Override
    public void dispose() {
        worldRenderer.dispose();
        debugOverlay.dispose();
        world.close();
    }
}
