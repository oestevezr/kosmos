package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms save/load round-trips reproduce exactly what was written, and that only
 * player-modified chunks are ever persisted (spec §10, §31, §47).
 */
class SaveRoundTripTest {

    @TempDir
    Path savesRoot;

    @Test
    void worldMetaRoundTrips(@TempDir Path root) throws IOException {
        SaveManager saveManager = new SaveManager(root);
        Path worldDir = saveManager.resolveWorldDir("Riverlands");
        WorldMeta original = new WorldMeta(123456789L, 1, 2048, 0.42, 0.6, 1_700_000_000_000L);
        java.nio.file.Files.createDirectories(worldDir);
        original.writeTo(worldDir.resolve("world.meta"));

        WorldMeta loaded = WorldMeta.readFrom(worldDir.resolve("world.meta"));

        assertEquals(original.seed, loaded.seed);
        assertEquals(original.generatorVersion, loaded.generatorVersion);
        assertEquals(original.worldSizeTiles, loaded.worldSizeTiles);
        assertEquals(original.seaLevel, loaded.seaLevel);
        assertEquals(original.resourceDensity, loaded.resourceDensity);
        assertEquals(original.creationTimeMillis, loaded.creationTimeMillis);
    }

    @Test
    void onlyDirtyChunksAreSaved() throws IOException {
        SaveManager saveManager = new SaveManager(savesRoot);
        WorldMeta meta = new WorldMeta(1L, 1, 1024, 0.4, 0.5, 0L);

        ChunkStore store = new ChunkStore(8);
        Chunk untouched = new Chunk();
        untouched.reset(0, 0);
        store.put(untouched); // never marked dirty

        Chunk modified = new Chunk();
        modified.reset(1, 1);
        modified.terrainType[0] = WorldConstants.TERRAIN_MOUNTAIN;
        modified.elevation[0] = 2500;
        modified.markDirty();
        store.put(modified);

        saveManager.save("Alpha", meta, store);

        List<int[]> deltaCoords = saveManager.listDeltaChunkCoords("Alpha");
        assertEquals(1, deltaCoords.size(), "Only the dirty chunk should have been persisted");
        assertEquals(1, deltaCoords.get(0)[0]);
        assertEquals(1, deltaCoords.get(0)[1]);
        assertFalse(saveManager.hasDelta("Alpha", 0, 0));
        assertTrue(saveManager.hasDelta("Alpha", 1, 1));
    }

    @Test
    void chunkDeltaRoundTripsAllLayers() throws IOException {
        SaveManager saveManager = new SaveManager(savesRoot);
        saveManager.resolveWorldDir("Beta"); // ensures name validated
        java.nio.file.Files.createDirectories(saveManager.resolveWorldDir("Beta").resolve("chunks"));

        Chunk source = new Chunk();
        source.reset(5, -3);
        for (int i = 0; i < source.terrainType.length; i++) {
            source.terrainType[i] = (byte) (i % 6);
            source.biome[i] = (byte) (i % 8);
            source.fertility[i] = (byte) (i % 256 - 128);
            source.moisture[i] = (byte) ((i * 3) % 256 - 128);
            source.temperature[i] = (byte) ((i * 7) % 256 - 128);
            source.elevation[i] = (short) (i * 11 - 500);
            source.resourceFlags[i] = i * 17;
            source.zoneType[i] = (byte) (i % 4);
            source.roadType[i] = (byte) (i % 2);
            source.buildingId[i] = i * 31;
            source.serviceFlags[i] = (byte) (i % 8);
        }
        source.markDirty();

        Path chunkFile = saveManager.resolveWorldDir("Beta").resolve("chunks")
            .resolve(ChunkDeltaIO.fileName(5, -3));
        ChunkDeltaIO.write(chunkFile, source);

        Chunk target = new Chunk();
        target.reset(5, -3);
        ChunkDeltaIO.readInto(chunkFile, target);

        assertArrayEquals(source.terrainType, target.terrainType);
        assertArrayEquals(source.biome, target.biome);
        assertArrayEquals(source.fertility, target.fertility);
        assertArrayEquals(source.moisture, target.moisture);
        assertArrayEquals(source.temperature, target.temperature);
        assertArrayEquals(source.elevation, target.elevation);
        assertArrayEquals(source.resourceFlags, target.resourceFlags);
        assertArrayEquals(source.zoneType, target.zoneType);
        assertArrayEquals(source.roadType, target.roadType);
        assertArrayEquals(source.buildingId, target.buildingId);
        assertArrayEquals(source.serviceFlags, target.serviceFlags);
    }
}
