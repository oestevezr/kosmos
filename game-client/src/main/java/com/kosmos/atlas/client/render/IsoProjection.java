package com.kosmos.atlas.client.render;

/**
 * World-tile <-> screen-pixel isometric projection math (spec §44).
 *
 * <p>Pure, stateless, allocation-free — every renderer call goes through here instead of
 * duplicating the projection formula, and it has zero dependency on libGDX types so it can be
 * unit-tested without a graphics context if needed later.
 */
public final class IsoProjection {

    public static final float TILE_WIDTH_PX = 64f;
    public static final float TILE_HEIGHT_PX = 32f;
    /** Screen pixels of vertical rise per meter of elevation. */
    public static final float HEIGHT_PX_PER_METER = 0.6f;

    private IsoProjection() {
    }

    public static float screenX(int tileX, int tileY) {
        return (tileX - tileY) * (TILE_WIDTH_PX * 0.5f);
    }

    public static float screenY(int tileX, int tileY, short elevationDecimeters) {
        float elevationMeters = elevationDecimeters * 0.1f;
        return (tileX + tileY) * (TILE_HEIGHT_PX * 0.5f) * -1f + elevationMeters * HEIGHT_PX_PER_METER;
    }

    /** Inverse projection at elevation 0 — good enough for camera-focus/culling purposes (spec §44.4). */
    public static float worldTileXApprox(float screenX, float screenY) {
        float a = screenX / (TILE_WIDTH_PX * 0.5f);
        float b = -screenY / (TILE_HEIGHT_PX * 0.5f);
        return (a + b) * 0.5f;
    }

    public static float worldTileYApprox(float screenX, float screenY) {
        float a = screenX / (TILE_WIDTH_PX * 0.5f);
        float b = -screenY / (TILE_HEIGHT_PX * 0.5f);
        return (b - a) * 0.5f;
    }
}
