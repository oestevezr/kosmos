package com.kosmos.atlas.client.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.kosmos.atlas.sim.Metrics;
import com.kosmos.atlas.sim.WorldManager;

/**
 * F3-style perf/debug HUD (spec §45, §49): frame time percentiles, loaded/queued chunk counts,
 * command accept/reject counts. Uses libGDX's built-in default font so it needs no external asset
 * (mirrors {@code PlaceholderAtlasGenerator}'s "runs with zero shipped art" goal).
 *
 * <p>Scene2D is an acceptable choice for HUD text per spec §44.6 — this is exactly the kind of
 * "menus/HUD/dialogs/tooltips" use case it calls out, as opposed to one Actor per tile/entity.
 */
public final class DebugOverlay implements com.badlogic.gdx.utils.Disposable {

    private final BitmapFont font = new BitmapFont();
    private final SpriteBatch uiBatch = new SpriteBatch();
    private final StringBuilder line = new StringBuilder(128);

    private double smoothedFrameMillis;

    public void update(float deltaSeconds) {
        double frameMillis = deltaSeconds * 1000.0;
        smoothedFrameMillis = smoothedFrameMillis * 0.9 + frameMillis * 0.1;
    }

    public void render(WorldManager world, int visibleChunks, int drawnTiles) {
        Metrics metrics = world.metrics();
        uiBatch.begin();
        line.setLength(0);
        line.append("Atlas City — Fase 1 Terrain Sandbox\n")
            .append(String.format("frame: %.2f ms (%.0f fps)\n", smoothedFrameMillis, 1000.0 / Math.max(0.001, smoothedFrameMillis)))
            .append("chunks loaded: ").append(world.chunkManager().loadedChunkCount())
            .append(" / capacity ").append(world.chunkManager().store().capacity())
            .append("   generated total: ").append(world.chunkManager().generatedTotal()).append('\n')
            .append("visible chunks: ").append(visibleChunks).append("   drawn tiles: ").append(drawnTiles).append('\n')
            .append(String.format("chunk-gen p50/p95/p99: %.2f / %.2f / %.2f ms%n",
                metrics.chunkGenerationDuration.percentileNanos(0.50) / 1e6,
                metrics.chunkGenerationDuration.percentileNanos(0.95) / 1e6,
                metrics.chunkGenerationDuration.percentileNanos(0.99) / 1e6))
            .append("commands accepted/rejected: ").append(metrics.commandsAccepted())
            .append(" / ").append(metrics.commandsRejected()).append('\n')
            .append(String.format("heap used: %.1f MB%n", Metrics.usedHeapBytes() / 1e6))
            .append("WASD/arrows pan, Q/E zoom");
        font.draw(uiBatch, line.toString(), 12, com.badlogic.gdx.Gdx.graphics.getHeight() - 12);
        uiBatch.end();
    }

    @Override
    public void dispose() {
        font.dispose();
        uiBatch.dispose();
    }
}
