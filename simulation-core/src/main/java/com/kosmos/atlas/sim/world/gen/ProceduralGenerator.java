package com.kosmos.atlas.sim.world.gen;

import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

/**
 * Generates a virgin chunk's natural layers from {@code (worldSeed, generatorVersion, chunkX,
 * chunkY)} alone (spec §4: "chunk = Generate(world_seed, chunk_x, chunk_y)").
 *
 * <p>Pipeline order follows spec §5.2's subset for MVP 0.1: continentalness -> elevation ->
 * water/coastline -> temperature -> humidity -> biome -> fertility -> resource flags. Rivers and
 * natural harbors are declared extension points (spec §6) left for a later phase.
 *
 * <p>Stateless and thread-safe: every call only reads {@link WorldGenSettings} and writes into
 * the caller-supplied {@link Chunk}, so world workers can generate many chunks concurrently
 * without coordination (spec §40 "WORLD WORKERS").
 */
public final class ProceduralGenerator {

    private static final int LAYER_CONTINENT = 1;
    private static final int LAYER_ELEVATION_DETAIL = 2;
    private static final int LAYER_TEMPERATURE = 3;
    private static final int LAYER_HUMIDITY = 4;
    private static final int LAYER_FERTILITY = 5;
    /** Base layer id for resource-flag coin flips; each resource offsets from here (see below). */
    private static final int LAYER_MINERAL = 6;

    private final WorldGenSettings settings;
    private final DeterministicNoise continentNoise;
    private final DeterministicNoise elevationDetailNoise;
    private final DeterministicNoise temperatureNoise;
    private final DeterministicNoise humidityNoise;
    private final DeterministicNoise fertilityNoise;

    public ProceduralGenerator(WorldGenSettings settings) {
        this.settings = settings;
        long seed = settings.worldSeed;
        int gv = settings.generatorVersion;
        this.continentNoise = new DeterministicNoise(seed, gv, LAYER_CONTINENT);
        this.elevationDetailNoise = new DeterministicNoise(seed, gv, LAYER_ELEVATION_DETAIL);
        this.temperatureNoise = new DeterministicNoise(seed, gv, LAYER_TEMPERATURE);
        this.humidityNoise = new DeterministicNoise(seed, gv, LAYER_HUMIDITY);
        this.fertilityNoise = new DeterministicNoise(seed, gv, LAYER_FERTILITY);
    }

    /** Generates virgin content directly into {@code chunk}, which must already carry its coordinates. */
    public void generate(Chunk chunk) {
        int baseX = chunk.chunkX() * WorldConstants.CHUNK_SIZE;
        int baseY = chunk.chunkY() * WorldConstants.CHUNK_SIZE;

        for (int ly = 0; ly < WorldConstants.CHUNK_SIZE; ly++) {
            for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
                int wx = baseX + lx;
                int wy = baseY + ly;
                int idx = Chunk.tileIndex(lx, ly);
                generateTile(chunk, idx, wx, wy);
            }
        }
        chunk.markMutated();
    }

    private void generateTile(Chunk chunk, int idx, int wx, int wy) {
        double freq = 1.0 / 256.0;

        double continent = continentNoise.fbm(wx * freq, wy * freq, 5, 2.0, 0.5);
        double detail = elevationDetailNoise.fbm(wx * freq * 4.0, wy * freq * 4.0, 3, 2.0, 0.5);
        double elevationUnit = clamp01((continent * 0.75 + detail * 0.25) * 0.5 + 0.5);

        double temperatureUnit = clamp01(
            temperatureNoise.fbm(wx * freq * 0.5, wy * freq * 0.5, 3, 2.0, 0.5) * 0.5 + 0.5
        );
        double humidityUnit = clamp01(
            humidityNoise.fbm(wx * freq * 0.7, wy * freq * 0.7, 3, 2.0, 0.5) * 0.5 + 0.5
        );

        byte terrain = classifyTerrain(elevationUnit);
        byte biome = classifyBiome(terrain, temperatureUnit, humidityUnit);

        double baseFertility = clamp01(
            fertilityNoise.fbm(wx * freq * 1.3, wy * freq * 1.3, 3, 2.0, 0.5) * 0.5 + 0.5
        );
        double fertility = terrain == WorldConstants.TERRAIN_PLAIN
            ? clamp01(baseFertility * 0.6 + humidityUnit * 0.4)
            : baseFertility * 0.2;

        int resourceFlags = deriveResourceFlags(terrain, biome, wx, wy, elevationUnit);

        chunk.terrainType[idx] = terrain;
        chunk.biome[idx] = biome;
        chunk.elevation[idx] = (short) Math.round(elevationUnit * 3000.0); // decimeters, 0..300m
        chunk.temperature[idx] = toByte255(temperatureUnit);
        chunk.moisture[idx] = toByte255(humidityUnit);
        chunk.fertility[idx] = toByte255(fertility);
        chunk.resourceFlags[idx] = resourceFlags;
    }

    private byte classifyTerrain(double elevationUnit) {
        if (elevationUnit < settings.seaLevel - 0.06) {
            return WorldConstants.TERRAIN_DEEP_WATER;
        }
        if (elevationUnit < settings.seaLevel) {
            return WorldConstants.TERRAIN_SHALLOW_WATER;
        }
        if (elevationUnit < settings.seaLevel + 0.02) {
            return WorldConstants.TERRAIN_BEACH;
        }
        if (elevationUnit < settings.seaLevel + 0.28) {
            return WorldConstants.TERRAIN_PLAIN;
        }
        if (elevationUnit < settings.seaLevel + 0.5) {
            return WorldConstants.TERRAIN_HILL;
        }
        return WorldConstants.TERRAIN_MOUNTAIN;
    }

    private byte classifyBiome(byte terrain, double temperatureUnit, double humidityUnit) {
        switch (terrain) {
            case WorldConstants.TERRAIN_DEEP_WATER:
            case WorldConstants.TERRAIN_SHALLOW_WATER:
                return WorldConstants.BIOME_OCEAN;
            case WorldConstants.TERRAIN_BEACH:
                return WorldConstants.BIOME_BEACH;
            case WorldConstants.TERRAIN_MOUNTAIN:
                return WorldConstants.BIOME_MOUNTAIN;
            case WorldConstants.TERRAIN_HILL:
                return WorldConstants.BIOME_HILLS;
            default:
                if (temperatureUnit < 0.25) {
                    return WorldConstants.BIOME_TUNDRA;
                }
                if (temperatureUnit > 0.72 && humidityUnit < 0.32) {
                    return WorldConstants.BIOME_DESERT;
                }
                return humidityUnit > 0.52 ? WorldConstants.BIOME_FOREST : WorldConstants.BIOME_GRASSLAND;
        }
    }

    private int deriveResourceFlags(byte terrain, byte biome, int wx, int wy, double elevationUnit) {
        int flags = 0;
        double density = settings.resourceDensity;
        long seed = settings.worldSeed;
        int gv = settings.generatorVersion;

        if (biome == WorldConstants.BIOME_FOREST
            && RngStream.unitDouble(seed, gv, wx, wy, LAYER_MINERAL) < 0.35 * density + 0.05) {
            flags |= WorldConstants.RESOURCE_TIMBER;
        }
        if (terrain == WorldConstants.TERRAIN_HILL || terrain == WorldConstants.TERRAIN_MOUNTAIN) {
            double stoneRoll = RngStream.unitDouble(seed, gv, wx, wy, LAYER_MINERAL + 100);
            double ironRoll = RngStream.unitDouble(seed, gv, wx, wy, LAYER_MINERAL + 200);
            double coalRoll = RngStream.unitDouble(seed, gv, wx, wy, LAYER_MINERAL + 300);
            if (stoneRoll < 0.30 * density + 0.05) {
                flags |= WorldConstants.RESOURCE_STONE;
            }
            if (ironRoll < 0.14 * density + 0.02) {
                flags |= WorldConstants.RESOURCE_IRON;
            }
            if (coalRoll < 0.12 * density + 0.02) {
                flags |= WorldConstants.RESOURCE_COAL;
            }
        }
        if (terrain == WorldConstants.TERRAIN_SHALLOW_WATER
            && RngStream.unitDouble(seed, gv, wx, wy, LAYER_MINERAL + 400) < 0.25 * density + 0.05) {
            flags |= WorldConstants.RESOURCE_FISHING;
        }
        if (terrain == WorldConstants.TERRAIN_PLAIN && elevationUnit < settings.seaLevel + 0.08) {
            flags |= WorldConstants.RESOURCE_FERTILE_BONUS;
        }
        return flags;
    }

    private static byte toByte255(double unit) {
        return (byte) Math.round(clamp01(unit) * 255.0);
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
