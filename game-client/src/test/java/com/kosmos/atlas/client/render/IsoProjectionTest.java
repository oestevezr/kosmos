package com.kosmos.atlas.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link IsoProjection} is pure math with zero libGDX/GPU dependency, so it is fully unit
 * testable without a graphics context — unlike {@link ChunkMesh} or {@link WorldRenderer}, which
 * need an OpenGL context to construct a {@code Mesh} and can only really be checked by launching
 * the app (see {@code platform-desktop}'s manual verification notes). This is the one piece of
 * the renderer's math previously covered by neither an automated test nor a visual check.
 */
class IsoProjectionTest {

    private static final float EPSILON = 0.01f;

    @Test
    void originMapsToScreenOrigin() {
        assertEquals(0f, IsoProjection.screenX(0, 0), EPSILON);
        assertEquals(0f, IsoProjection.screenY(0, 0, (short) 0), EPSILON);
    }

    @Test
    void movingEastIncreasesScreenXAndDecreasesScreenY() {
        float sx0 = IsoProjection.screenX(0, 0);
        float sy0 = IsoProjection.screenY(0, 0, (short) 0);
        float sxEast = IsoProjection.screenX(1, 0);
        float syEast = IsoProjection.screenY(1, 0, (short) 0);

        assertEquals(IsoProjection.TILE_WIDTH_PX * 0.5f, sxEast - sx0, EPSILON);
        assertEquals(-(IsoProjection.TILE_HEIGHT_PX * 0.5f), syEast - sy0, EPSILON);
    }

    @Test
    void movingSouthDecreasesScreenXAndDecreasesScreenY() {
        float sx0 = IsoProjection.screenX(0, 0);
        float sy0 = IsoProjection.screenY(0, 0, (short) 0);
        float sxSouth = IsoProjection.screenX(0, 1);
        float sySouth = IsoProjection.screenY(0, 1, (short) 0);

        assertEquals(-(IsoProjection.TILE_WIDTH_PX * 0.5f), sxSouth - sx0, EPSILON);
        assertEquals(-(IsoProjection.TILE_HEIGHT_PX * 0.5f), sySouth - sy0, EPSILON);
    }

    @Test
    void higherElevationRaisesScreenYWithoutMovingScreenX() {
        float sxLow = IsoProjection.screenX(3, 3);
        float syLow = IsoProjection.screenY(3, 3, (short) 0);
        float sxHigh = IsoProjection.screenX(3, 3);
        float syHigh = IsoProjection.screenY(3, 3, (short) 1000); // 100 meters

        assertEquals(sxLow, sxHigh, EPSILON, "elevation must not affect horizontal screen position");
        float expectedRise = 100f * IsoProjection.HEIGHT_PX_PER_METER;
        assertEquals(expectedRise, syHigh - syLow, EPSILON);
    }

    @Test
    void inverseProjectionRoundTripsAtZeroElevation() {
        for (int tx = -20; tx <= 20; tx += 3) {
            for (int ty = -20; ty <= 20; ty += 3) {
                float sx = IsoProjection.screenX(tx, ty);
                float sy = IsoProjection.screenY(tx, ty, (short) 0);
                float roundTrippedX = IsoProjection.worldTileXApprox(sx, sy);
                float roundTrippedY = IsoProjection.worldTileYApprox(sx, sy);
                assertEquals(tx, roundTrippedX, EPSILON, "x round-trip failed for (" + tx + "," + ty + ")");
                assertEquals(ty, roundTrippedY, EPSILON, "y round-trip failed for (" + tx + "," + ty + ")");
            }
        }
    }
}
