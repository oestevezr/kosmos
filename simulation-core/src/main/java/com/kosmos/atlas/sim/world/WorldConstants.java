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

    // --- Per-tile service flag bits (packed into Chunk.serviceFlags[tile], an int since Fase 2's
    // civic-service tiers pushed past a byte's 8 bits). ---
    /** Set by UtilitySystem when the tile is graph-reachable from a power source (spec §24). */
    public static final int SERVICE_POWERED = 1;
    /** Set by UtilitySystem when the tile is graph-reachable from a water source (spec §24). */
    public static final int SERVICE_WATERED = 1 << 1;
    /** Set by RoadNetwork when the tile is adjacent to a connected road (spec §12, §23 "transport"). */
    public static final int SERVICE_ROAD_ACCESS = 1 << 2;

    // --- Prosperity/luxury civic-service coverage bits (Fase 2 — see PopulationSystem's
    // satisfaction-ceiling logic; docs/roadmap.md's "Servicios cívicos por tiers"). ---
    /** Set when in range of a Clinic or Hospital (either tier of the Healthcare category). */
    public static final int SERVICE_HEALTHCARE = 1 << 3;
    /** Set when in range of a Volunteer Fire Brigade or Fire Station. */
    public static final int SERVICE_FIRE = 1 << 4;
    /** Set when in range of Waste Collection or an Incinerator. */
    public static final int SERVICE_SANITATION = 1 << 5;
    public static final int SERVICE_CEMETERY = 1 << 6;
    public static final int SERVICE_PARK = 1 << 7;
    public static final int SERVICE_MUSEUM = 1 << 8;
    /** Set when in range of a Police Outpost or Police Station. */
    public static final int SERVICE_POLICE = 1 << 9;
    /** Set when in range of a School or University. */
    public static final int SERVICE_EDUCATION = 1 << 10;
    /** Set when in range of a Church. */
    public static final int SERVICE_RELIGION = 1 << 11;
    /** Set when in range of a Bus Stop that is a stop on at least one active bus route — an
     *  isolated, route-less stop gives no coverage (see UtilitySystem/docs/roadmap.md's bus-route
     *  mechanic, MVP 0.6). */
    public static final int SERVICE_TRANSIT = 1 << 12;
    // Central Bank, City Hall and Bus Depot are not coverage sources — no bit here.
}
