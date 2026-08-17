package com.kosmos.atlas.client.presentation;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kosmos.atlas.client.camera.WorldCameraController;
import com.kosmos.atlas.client.render.IsoProjection;
import com.kosmos.atlas.client.render.WorldRenderer;
import com.kosmos.atlas.client.ui.DebugOverlay;
import com.kosmos.atlas.sim.WorldManager;
import com.kosmos.atlas.sim.commands.city.BuildPowerPlantCommand;
import com.kosmos.atlas.sim.commands.city.BuildRoadCommand;
import com.kosmos.atlas.sim.commands.city.BuildWaterTowerCommand;
import com.kosmos.atlas.sim.commands.city.FoundCityCommand;
import com.kosmos.atlas.sim.commands.city.ZoneCommand;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.HardwareProfile;
import com.kosmos.atlas.sim.world.WorldConstants;
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

    /** Set once {@link #seedDemoSettlement()} places something to look at; {@code resize()} needs
     *  it too, since {@code OrthographicCamera.setToOrtho} resets {@code camera.position} back to
     *  the viewport center every time it runs — including the initial resize event most LWJGL3
     *  backends fire right after {@code create()}, which would otherwise silently discard the
     *  centering done in {@code create()} before the very first frame is even drawn. -1 = unset. */
    private int demoCenterTileX = -1;
    private int demoCenterTileY;

    @Override
    public void create() {
        WorldGenSettings genSettings = WorldGenSettings.balanced(DEMO_SEED, DEMO_WORLD_SIZE_TILES);
        world = new WorldManager(genSettings, HardwareProfile.MEDIUM);

        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cameraController = new WorldCameraController();
        worldRenderer = new WorldRenderer();
        debugOverlay = new DebugOverlay();

        seedDemoSettlement();
    }

    /**
     * Founds a small demo settlement near the origin (road + zones + power/water, same recipe as
     * {@code headless-runner}'s {@code HeadlessMain.runCityGrowthScenario}) so the render change
     * that made zones/roads/buildings visible for the first time actually has something to show —
     * pure demo wiring, not a simulation rule (this class's own javadoc constraint). Also centers
     * the camera on it: {@code camera} otherwise starts at world tile (0,0), which rarely lines up
     * with wherever {@link #findLandRun} actually placed the settlement — leaving most of the
     * initial view pointed at unloaded chunks (nothing streamed there yet) until the player pans.
     */
    private void seedDemoSettlement() {
        world.updateCameraFocus(16, 16); // chunk (0,0)
        long deadlineNanos = System.nanoTime() + 10_000_000_000L;
        while (world.chunkManager().store().get(0, 0) == null && System.nanoTime() < deadlineNanos) {
            world.update(1.0 / 30.0);
        }
        Chunk chunk = world.chunkManager().store().get(0, 0);
        if (chunk == null) {
            return; // chunk never loaded in time — leave the world as plain terrain
        }

        int landStart = findLandRun(chunk.terrainType, 16, 9);
        if (landStart < 0) {
            return; // no suitable land run for this seed — skip the demo, terrain still renders fine
        }

        world.submitCommand(new FoundCityCommand(landStart, 16, "Atlas City"));
        world.update(1.0 / 30.0); // drain the found-city command before zoning/building depend on it

        int roadStart = landStart + 1;
        for (int x = roadStart; x < roadStart + 8; x++) {
            world.submitCommand(new BuildRoadCommand(x, 16));
        }
        world.submitCommand(new ZoneCommand(roadStart, 15, WorldConstants.ZONE_RESIDENTIAL));
        world.submitCommand(new ZoneCommand(roadStart + 1, 15, WorldConstants.ZONE_RESIDENTIAL));
        world.submitCommand(new ZoneCommand(roadStart + 2, 17, WorldConstants.ZONE_COMMERCIAL));
        world.submitCommand(new ZoneCommand(roadStart + 3, 17, WorldConstants.ZONE_INDUSTRIAL));
        world.submitCommand(new BuildPowerPlantCommand(roadStart + 5, 15));
        world.submitCommand(new BuildWaterTowerCommand(roadStart + 5, 17));

        demoCenterTileX = roadStart + 4;
        demoCenterTileY = 16;
        recenterCameraOnDemoSettlement();
        world.updateCameraFocus(demoCenterTileX, demoCenterTileY);
        // Give the streaming radius around the new focus a moment to catch up before the first
        // real frame renders, same spirit as the wait above for chunk (0,0).
        long focusDeadlineNanos = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < focusDeadlineNanos) {
            world.update(1.0 / 30.0);
        }
    }

    /** Re-applies the demo settlement's camera centering — needed after any
     *  {@code camera.setToOrtho} call, which resets {@code camera.position} to the viewport center
     *  (see {@link #demoCenterTileX}'s javadoc). No-op if {@link #seedDemoSettlement()} never found
     *  anywhere to center on. */
    private void recenterCameraOnDemoSettlement() {
        if (demoCenterTileX < 0) {
            return;
        }
        camera.position.set(IsoProjection.screenX(demoCenterTileX, demoCenterTileY),
            IsoProjection.screenY(demoCenterTileX, demoCenterTileY, (short) 0), 0);
        camera.update();
    }

    private static int findLandRun(byte[] terrainType, int y, int runLength) {
        int run = 0;
        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            byte t = terrainType[Chunk.tileIndex(x, y)];
            boolean land = t != WorldConstants.TERRAIN_DEEP_WATER && t != WorldConstants.TERRAIN_SHALLOW_WATER;
            run = land ? run + 1 : 0;
            if (run >= runLength) {
                return x - runLength + 1;
            }
        }
        return -1;
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
        // Needed now that zoned-but-unbuilt lots render as a semi-transparent tint (PlaceholderAtlasGenerator)
        // — glEnable alone was a no-op before this, since nothing used alpha < 1.
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        worldRenderer.render(camera, world.chunkManager().store(), world.buildings());
        debugOverlay.update(delta);
        if (showDebugOverlay) {
            debugOverlay.render(world, worldRenderer.lastVisibleChunkCount(), worldRenderer.lastDrawnTileCount());
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        recenterCameraOnDemoSettlement();
    }

    @Override
    public void dispose() {
        worldRenderer.dispose();
        debugOverlay.dispose();
        world.close();
    }
}
