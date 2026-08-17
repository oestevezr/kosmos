package com.kosmos.atlas.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.world.WorldConstants;

/**
 * Builds a tiny code-generated sprite atlas (one solid diamond per terrain type, zone, road and
 * building category) so the project compiles and runs with zero external art assets (spec §44.1:
 * "Use sprite atlases aggressively"). A single {@link Texture} backs every region, so drawing a
 * whole visible chunk never switches GL texture state (spec §44.2: minimize draw calls / batch
 * flushes) — this stays true as more layers are added, since they all live on the same page.
 *
 * <p>Replace this with a real {@code TexturePacker}-built atlas once art exists; every renderer
 * downstream only depends on the returned {@link TextureRegion} arrays, not on how they were built.
 *
 * <p>Zone regions ({@link Atlas#byZoneType}) are baked with alpha ≈0.5 directly into the Pixmap —
 * an empty zoned lot should read as a tint over the terrain beneath it, not an opaque tile.
 * Everything else (terrain, roads, buildings) is opaque. Baking alpha into the texture rather than
 * the vertex color means {@link ChunkMesh} never needs a per-quad color, only per-quad UVs — it can
 * keep using a single hardcoded white vertex color everywhere.
 */
public final class PlaceholderAtlasGenerator {

    private static final int CELL_SIZE = 32;
    private static final float ZONE_ALPHA = 0.5f;

    // --- Building category indices (com.kosmos.atlas.client only — no BuildingEconomics-style
    // parallel table needed, this is display grouping, not simulation data). Residential/
    // Commercial/Industrial reuse the same hues as their zone tint, just opaque. ---
    public static final int CATEGORY_RESIDENTIAL = 0;
    public static final int CATEGORY_COMMERCIAL = 1;
    public static final int CATEGORY_INDUSTRIAL = 2;
    public static final int CATEGORY_UTILITY = 3;
    public static final int CATEGORY_CIVIC = 4;
    public static final int CATEGORY_LUXURY = 5;
    public static final int CATEGORY_TRANSPORT = 6;
    public static final int CATEGORY_INSTITUTIONAL = 7;
    private static final int BUILDING_CATEGORY_COUNT = 8;

    private PlaceholderAtlasGenerator() {
    }

    public static final class Atlas implements com.badlogic.gdx.utils.Disposable {
        public final Texture texture;
        public final TextureRegion[] byTerrainType;
        /** Index 0 ({@code ZONE_NONE}) is {@code null} — never looked up. */
        public final TextureRegion[] byZoneType;
        /** Index 0 ({@code ROAD_NONE}) is {@code null} — never looked up. */
        public final TextureRegion[] byRoadType;
        public final TextureRegion[] byBuildingCategory;

        Atlas(Texture texture, TextureRegion[] byTerrainType, TextureRegion[] byZoneType,
              TextureRegion[] byRoadType, TextureRegion[] byBuildingCategory) {
            this.texture = texture;
            this.byTerrainType = byTerrainType;
            this.byZoneType = byZoneType;
            this.byRoadType = byRoadType;
            this.byBuildingCategory = byBuildingCategory;
        }

        @Override
        public void dispose() {
            texture.dispose();
        }
    }

    /** Groups the 36 {@code BuildingType} constants into the small set of display categories above
     *  — simulation code never sees this, it exists purely for picking a placeholder color. */
    public static int buildingCategoryIndex(byte buildingType) {
        return switch (buildingType) {
            case BuildingType.RESIDENTIAL -> CATEGORY_RESIDENTIAL;
            case BuildingType.COMMERCIAL -> CATEGORY_COMMERCIAL;
            case BuildingType.INDUSTRIAL, BuildingType.FARM, BuildingType.LUMBER_CAMP,
                 BuildingType.MINE, BuildingType.QUARRY, BuildingType.STEEL_MILL -> CATEGORY_INDUSTRIAL;
            case BuildingType.POWER_PLANT, BuildingType.WATER_TOWER, BuildingType.POWER_PLANT_HYDRO,
                 BuildingType.POWER_PLANT_NUCLEAR, BuildingType.WATER_TREATMENT_PLANT,
                 BuildingType.DESALINATION_PLANT, BuildingType.WASTE_COLLECTION,
                 BuildingType.INCINERATOR -> CATEGORY_UTILITY;
            case BuildingType.CLINIC, BuildingType.HOSPITAL, BuildingType.VOLUNTEER_FIRE_BRIGADE,
                 BuildingType.FIRE_STATION, BuildingType.POLICE_OUTPOST, BuildingType.POLICE_STATION,
                 BuildingType.SCHOOL, BuildingType.UNIVERSITY, BuildingType.CHURCH,
                 BuildingType.CEMETERY -> CATEGORY_CIVIC;
            case BuildingType.PARK, BuildingType.MUSEUM -> CATEGORY_LUXURY;
            case BuildingType.TRADE_DEPOT, BuildingType.PORT, BuildingType.AIRPORT,
                 BuildingType.RAIL_TERMINAL, BuildingType.BUS_DEPOT, BuildingType.BUS_STOP -> CATEGORY_TRANSPORT;
            case BuildingType.CENTRAL_BANK, BuildingType.CITY_HALL -> CATEGORY_INSTITUTIONAL;
            default -> CATEGORY_CIVIC; // unreachable for any known BuildingType; a safe fallback otherwise
        };
    }

    public static Atlas generate() {
        Color[] terrainColors = {
            new Color(0.10f, 0.20f, 0.55f, 1f), // TERRAIN_DEEP_WATER
            new Color(0.20f, 0.45f, 0.80f, 1f), // TERRAIN_SHALLOW_WATER
            new Color(0.85f, 0.78f, 0.55f, 1f), // TERRAIN_BEACH
            new Color(0.35f, 0.65f, 0.30f, 1f), // TERRAIN_PLAIN
            new Color(0.45f, 0.55f, 0.30f, 1f), // TERRAIN_HILL
            new Color(0.55f, 0.53f, 0.52f, 1f), // TERRAIN_MOUNTAIN
        };
        // Index 0 unused (ZONE_NONE); tints, not opaque fills — see ZONE_ALPHA.
        Color[] zoneColors = {
            null,
            new Color(0.30f, 0.80f, 0.30f, ZONE_ALPHA), // ZONE_RESIDENTIAL
            new Color(0.30f, 0.50f, 0.90f, ZONE_ALPHA), // ZONE_COMMERCIAL
            new Color(0.85f, 0.75f, 0.20f, ZONE_ALPHA), // ZONE_INDUSTRIAL
        };
        // Index 0 unused (ROAD_NONE).
        Color[] roadColors = {
            null,
            new Color(0.25f, 0.25f, 0.28f, 1f), // ROAD_DIRT
        };
        Color[] buildingCategoryColors = new Color[BUILDING_CATEGORY_COUNT];
        buildingCategoryColors[CATEGORY_RESIDENTIAL] = new Color(0.20f, 0.60f, 0.25f, 1f);
        buildingCategoryColors[CATEGORY_COMMERCIAL] = new Color(0.20f, 0.40f, 0.75f, 1f);
        buildingCategoryColors[CATEGORY_INDUSTRIAL] = new Color(0.65f, 0.55f, 0.15f, 1f);
        buildingCategoryColors[CATEGORY_UTILITY] = new Color(0.60f, 0.60f, 0.35f, 1f);
        buildingCategoryColors[CATEGORY_CIVIC] = new Color(0.75f, 0.85f, 0.95f, 1f);
        buildingCategoryColors[CATEGORY_LUXURY] = new Color(0.25f, 0.75f, 0.60f, 1f);
        buildingCategoryColors[CATEGORY_TRANSPORT] = new Color(0.75f, 0.40f, 0.15f, 1f);
        buildingCategoryColors[CATEGORY_INSTITUTIONAL] = new Color(0.85f, 0.70f, 0.20f, 1f);

        int cells = terrainColors.length + zoneColors.length + roadColors.length + buildingCategoryColors.length;
        Pixmap pixmap = new Pixmap(cells * CELL_SIZE, CELL_SIZE, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None); // write raw RGBA including alpha, no premultiply

        int cursor = 0;
        cursor = fillCells(pixmap, cursor, terrainColors);
        cursor = fillCells(pixmap, cursor, zoneColors);
        cursor = fillCells(pixmap, cursor, roadColors);
        cursor = fillCells(pixmap, cursor, buildingCategoryColors);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();

        int offset = 0;
        TextureRegion[] byTerrainType = sliceRegions(texture, offset, terrainColors.length);
        offset += terrainColors.length;
        TextureRegion[] byZoneType = sliceRegions(texture, offset, zoneColors.length);
        offset += zoneColors.length;
        TextureRegion[] byRoadType = sliceRegions(texture, offset, roadColors.length);
        offset += roadColors.length;
        TextureRegion[] byBuildingCategory = sliceRegions(texture, offset, buildingCategoryColors.length);

        // byZoneType[0]/byRoadType[0] stay null (ZONE_NONE/ROAD_NONE are never looked up).
        byZoneType[0] = null;
        byRoadType[0] = null;

        assert byTerrainType.length > WorldConstants.TERRAIN_MOUNTAIN;
        return new Atlas(texture, byTerrainType, byZoneType, byRoadType, byBuildingCategory);
    }

    /** Fills {@code colors.length} consecutive cells starting at {@code startCell}; a {@code null}
     *  entry leaves that cell untouched (transparent black) since nothing ever reads it. */
    private static int fillCells(Pixmap pixmap, int startCell, Color[] colors) {
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == null) {
                continue;
            }
            pixmap.setColor(colors[i]);
            pixmap.fillRectangle((startCell + i) * CELL_SIZE, 0, CELL_SIZE, CELL_SIZE);
        }
        return startCell + colors.length;
    }

    private static TextureRegion[] sliceRegions(Texture texture, int startCell, int count) {
        TextureRegion[] regions = new TextureRegion[count];
        for (int i = 0; i < count; i++) {
            regions[i] = new TextureRegion(texture, (startCell + i) * CELL_SIZE, 0, CELL_SIZE, CELL_SIZE);
        }
        return regions;
    }
}
