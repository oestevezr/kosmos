package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates a save directory (spec §31):
 * <pre>
 * save/&lt;world&gt;/
 * ├── world.meta
 * └── chunks/
 *     ├── 0_0.delta
 *     └── ...
 * </pre>
 * Only chunks flagged {@link Chunk#isDirty()} are written — this is the mechanism that keeps save
 * size proportional to player modifications, not world size (spec §10, §43).
 */
public final class SaveManager {

    private final Path savesRoot;

    public SaveManager(Path savesRoot) {
        this.savesRoot = savesRoot;
    }

    public Path resolveWorldDir(String worldName) throws IOException {
        return WorldNameValidator.resolveWorldDir(savesRoot, worldName);
    }

    /** Writes world.meta and any dirty chunks currently resident in {@code chunkStore}. */
    public void save(String worldName, WorldMeta meta, ChunkStore chunkStore) throws IOException {
        save(worldName, meta, chunkStore, null);
    }

    /**
     * Writes world.meta, any dirty chunks currently resident in {@code chunkStore}, and — if
     * non-null — {@code settlements.dat} (spec §31). Building ids referenced from
     * {@code Chunk.buildingId} are only meaningful together with the registry that owns them, so
     * a save with buildings on the map must always include it.
     */
    public void save(String worldName, WorldMeta meta, ChunkStore chunkStore, BuildingRegistry buildings) throws IOException {
        Path worldDir = resolveWorldDir(worldName);
        meta.writeTo(worldDir.resolve("world.meta"));

        Path chunksDir = worldDir.resolve("chunks");
        Files.createDirectories(chunksDir);

        List<IOException> failures = new ArrayList<>();
        chunkStore.forEach(chunk -> {
            if (!chunk.isDirty()) {
                return;
            }
            Path chunkFile = chunksDir.resolve(ChunkDeltaIO.fileName(chunk.chunkX(), chunk.chunkY()));
            try {
                ChunkDeltaIO.write(chunkFile, chunk);
            } catch (IOException e) {
                failures.add(e);
            }
        });
        if (!failures.isEmpty()) {
            throw failures.get(0);
        }

        if (buildings != null) {
            BuildingRegistryIO.write(worldDir.resolve("settlements.dat"), buildings);
        }
    }

    public WorldMeta loadMeta(String worldName) throws IOException {
        Path worldDir = resolveWorldDir(worldName);
        return WorldMeta.readFrom(worldDir.resolve("world.meta"));
    }

    public boolean hasBuildingRegistry(String worldName) throws IOException {
        return Files.isRegularFile(resolveWorldDir(worldName).resolve("settlements.dat"));
    }

    public BuildingRegistry loadBuildingRegistry(String worldName) throws IOException {
        return BuildingRegistryIO.read(resolveWorldDir(worldName).resolve("settlements.dat"));
    }

    /** Lists every persisted chunk-delta coordinate for a world, without loading their content yet. */
    public List<int[]> listDeltaChunkCoords(String worldName) throws IOException {
        Path chunksDir = resolveWorldDir(worldName).resolve("chunks");
        List<int[]> coords = new ArrayList<>();
        if (!Files.isDirectory(chunksDir)) {
            return coords;
        }
        try (var stream = Files.newDirectoryStream(chunksDir, "*.delta")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                String base = name.substring(0, name.length() - ".delta".length());
                int sep = base.indexOf('_');
                if (sep < 0) {
                    continue; // ignore unrecognized files rather than fail the whole load
                }
                try {
                    int cx = Integer.parseInt(base.substring(0, sep));
                    int cy = Integer.parseInt(base.substring(sep + 1));
                    coords.add(new int[] {cx, cy});
                } catch (NumberFormatException ignored) {
                    // ignore unrecognized files
                }
            }
        }
        return coords;
    }

    public boolean hasDelta(String worldName, int chunkX, int chunkY) throws IOException {
        Path file = resolveWorldDir(worldName).resolve("chunks").resolve(ChunkDeltaIO.fileName(chunkX, chunkY));
        return Files.isRegularFile(file);
    }

    /** Applies a persisted delta onto {@code target} (which must already carry chunkX/chunkY via generation). */
    public void loadChunkDelta(String worldName, Chunk target) throws IOException {
        Path file = resolveWorldDir(worldName).resolve("chunks")
            .resolve(ChunkDeltaIO.fileName(target.chunkX(), target.chunkY()));
        ChunkDeltaIO.readInto(file, target);
    }
}
