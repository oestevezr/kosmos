package com.kosmos.atlas.sim.world;

/**
 * Byte-coded terrain/biome vocabulary and world partitioning constants (spec §4, §7).
 *
 * <p>Terrain and biome are stored as {@code byte} codes rather than enum references so that a
 * chunk's per-tile layers stay flat primitive arrays (spec §42.1/§42.2) — an enum reference
 * array would be an array of pointers to heap objects, defeating the point.
 */
public final class WorldConstants {

    private WorldConstants() {
    }

    /** Tiles per chunk edge (spec §4: "Recommended initial chunk size: 32 x 32 tiles"). */
    public static final int CHUNK_SIZE = 32;
    public static final int TILES_PER_CHUNK = CHUNK_SIZE * CHUNK_SIZE;

    /** 1 tile = 16 meters (spec §3). */
    public static final double METERS_PER_TILE = 16.0;

    // --- Terrain types ---
    public static final byte TERRAIN_DEEP_WATER = 0;
    public static final byte TERRAIN_SHALLOW_WATER = 1;
    public static final byte TERRAIN_BEACH = 2;
    public static final byte TERRAIN_PLAIN = 3;
    public static final byte TERRAIN_HILL = 4;
    public static final byte TERRAIN_MOUNTAIN = 5;

    // --- Biome types ---
    public static final byte BIOME_OCEAN = 0;
    public static final byte BIOME_BEACH = 1;
    public static final byte BIOME_GRASSLAND = 2;
    public static final byte BIOME_FOREST = 3;
    public static final byte BIOME_HILLS = 4;
    public static final byte BIOME_MOUNTAIN = 5;
    public static final byte BIOME_DESERT = 6;
    public static final byte BIOME_TUNDRA = 7;

    // --- Resource flag bits (packed into Chunk.resourceFlags[tile]) ---
    public static final int RESOURCE_TIMBER = 1;
    public static final int RESOURCE_STONE = 1 << 1;
    public static final int RESOURCE_IRON = 1 << 2;
    public static final int RESOURCE_COAL = 1 << 3;
    public static final int RESOURCE_FISHING = 1 << 4;
    public static final int RESOURCE_FERTILE_BONUS = 1 << 5;

    // --- Zone types (spec §33 MVP 0.2: residential/commercial/industrial) ---
    public static final byte ZONE_NONE = 0;
    public static final byte ZONE_RESIDENTIAL = 1;
    public static final byte ZONE_COMMERCIAL = 2;
    public static final byte ZONE_INDUSTRIAL = 3;

    // --- Road types. Only the first rung of §12's progression exists in Fase 2. ---
    public static final byte ROAD_NONE = 0;
    public static final byte ROAD_DIRT = 1;

    /** Building occupying a tile, or {@link #NO_BUILDING} if the tile is empty (spec §22, §42.3). */
    public static final int NO_BUILDING = 0;

    // --- Per-tile service flag bits (packed into Chunk.serviceFlags[tile]) ---
    /** Set by UtilitySystem when the tile is graph-reachable from a power source (spec §24). */
    public static final int SERVICE_POWERED = 1;
    /** Set by UtilitySystem when the tile is graph-reachable from a water source (spec §24). */
    public static final int SERVICE_WATERED = 1 << 1;
    /** Set by RoadNetwork when the tile is adjacent to a connected road (spec §12, §23 "transport"). */
    public static final int SERVICE_ROAD_ACCESS = 1 << 2;
}
