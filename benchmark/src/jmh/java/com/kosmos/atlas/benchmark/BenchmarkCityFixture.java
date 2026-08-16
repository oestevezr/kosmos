package com.kosmos.atlas.benchmark;

import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.population.PopulationSystem;
import com.kosmos.atlas.sim.transport.RoadNetwork;
import com.kosmos.atlas.sim.utility.UtilitySystem;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;

/**
 * Builds a representative small city — a grid of loaded chunks, a road lattice, zoned tiles, and
 * a handful of power/water sources, then runs it forward until buildings have settled and grown —
 * so the Fase 2 system benchmarks measure realistic steady-state cost rather than an empty world
 * or a cold-start burst (spec §49: "based on profiles... representative hardware").
 */
final class BenchmarkCityFixture {

    final ChunkStore store;
    final BuildingRegistry buildings;

    /** {@code chunkSpan} x {@code chunkSpan} chunks of fully-buildable land, roaded every {@code roadSpacing} tiles. */
    BenchmarkCityFixture(int chunkSpan, int roadSpacing) {
        int capacity = chunkSpan * chunkSpan + 4;
        store = new ChunkStore(capacity);
        buildings = new BuildingRegistry();

        int half = chunkSpan / 2;
        for (int cx = -half; cx <= half; cx++) {
            for (int cy = -half; cy <= half; cy++) {
                Chunk chunk = new Chunk();
                chunk.reset(cx, cy);
                java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
                store.put(chunk);
            }
        }

        int worldMinTile = -half * WorldConstants.CHUNK_SIZE;
        int worldMaxTile = (half + 1) * WorldConstants.CHUNK_SIZE;

        // Road lattice + alternating residential/commercial/industrial zoning in each cell.
        for (int y = worldMinTile; y < worldMaxTile; y++) {
            for (int x = worldMinTile; x < worldMaxTile; x++) {
                Chunk chunk = store.get(Math.floorDiv(x, WorldConstants.CHUNK_SIZE), Math.floorDiv(y, WorldConstants.CHUNK_SIZE));
                int idx = Chunk.tileIndex(Math.floorMod(x, WorldConstants.CHUNK_SIZE), Math.floorMod(y, WorldConstants.CHUNK_SIZE));
                boolean onRoadLine = (x % roadSpacing == 0) || (y % roadSpacing == 0);
                if (onRoadLine) {
                    chunk.roadType[idx] = WorldConstants.ROAD_DIRT;
                } else if (Math.floorMod(x, roadSpacing) == 1) {
                    chunk.zoneType[idx] = WorldConstants.ZONE_RESIDENTIAL;
                } else if (Math.floorMod(x, roadSpacing) == 2) {
                    chunk.zoneType[idx] = WorldConstants.ZONE_COMMERCIAL;
                } else if (Math.floorMod(x, roadSpacing) == 3) {
                    chunk.zoneType[idx] = WorldConstants.ZONE_INDUSTRIAL;
                }
            }
        }

        // A power plant + water tower every few blocks so most zoned tiles end up serviced.
        int sourceSpacing = roadSpacing * 4;
        for (int y = worldMinTile; y < worldMaxTile; y += sourceSpacing) {
            for (int x = worldMinTile; x < worldMaxTile; x += sourceSpacing) {
                Chunk chunk = store.get(Math.floorDiv(x, WorldConstants.CHUNK_SIZE), Math.floorDiv(y, WorldConstants.CHUNK_SIZE));
                int idx = Chunk.tileIndex(Math.floorMod(x, WorldConstants.CHUNK_SIZE), Math.floorMod(y, WorldConstants.CHUNK_SIZE));
                int plantId = buildings.create(BuildingType.POWER_PLANT, x, y);
                chunk.buildingId[idx] = plantId;
                int towerX = Math.min(x + 1, worldMaxTile - 1);
                Chunk towerChunk = store.get(Math.floorDiv(towerX, WorldConstants.CHUNK_SIZE), Math.floorDiv(y, WorldConstants.CHUNK_SIZE));
                int towerIdx = Chunk.tileIndex(Math.floorMod(towerX, WorldConstants.CHUNK_SIZE), Math.floorMod(y, WorldConstants.CHUNK_SIZE));
                int towerId = buildings.create(BuildingType.WATER_TOWER, towerX, y);
                towerChunk.buildingId[towerIdx] = towerId;
            }
        }
    }

    /** Runs road/utility/population systems forward until the city has settled and grown. */
    void settle(int iterations) {
        RoadNetwork roadNetwork = new RoadNetwork();
        UtilitySystem utilitySystem = new UtilitySystem();
        PopulationSystem populationSystem = new PopulationSystem();
        for (int i = 0; i < iterations; i++) {
            roadNetwork.update(store);
            utilitySystem.update(store, buildings);
            populationSystem.tick(store, buildings);
        }
    }
}
