package com.kosmos.atlas.sim.persistence;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code world.meta}: the seed and settings needed to regenerate virgin terrain, plus enough
 * bookkeeping to reject saves from an incompatible generator (spec §31, §32).
 *
 * <p>Untouched world tiles are never saved individually (spec §10) — this file, together with
 * per-region chunk delta packs, is the entire footprint of a save independent of world size.
 */
public final class WorldMeta {

    private static final int MAGIC = 0x41544C53; // "ATLS"
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_SETTINGS_BLOCK = 4096;

    public final long seed;
    public final int generatorVersion;
    public final int worldSizeTiles;
    public final double seaLevel;
    public final double resourceDensity;
    public final long creationTimeMillis;

    public WorldMeta(long seed, int generatorVersion, int worldSizeTiles,
                      double seaLevel, double resourceDensity, long creationTimeMillis) {
        this.seed = seed;
        this.generatorVersion = generatorVersion;
        this.worldSizeTiles = worldSizeTiles;
        this.seaLevel = seaLevel;
        this.resourceDensity = resourceDensity;
        this.creationTimeMillis = creationTimeMillis;
    }

    public void writeTo(Path worldMetaFile) throws IOException {
        AtomicFileWriter.write(worldMetaFile, this::write);
    }

    private void write(OutputStream sink) throws IOException {
        DataOutputStream out = new DataOutputStream(sink);
        out.writeInt(MAGIC);
        out.writeInt(FORMAT_VERSION);

        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream(64);
        DataOutputStream body = new DataOutputStream(buf);
        body.writeLong(seed);
        body.writeInt(generatorVersion);
        body.writeInt(worldSizeTiles);
        body.writeDouble(seaLevel);
        body.writeDouble(resourceDensity);
        body.writeLong(creationTimeMillis);

        BinaryBlockIO.writeBlock(out, buf.toByteArray());
    }

    public static WorldMeta readFrom(Path worldMetaFile) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(worldMetaFile)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new SaveCorruptedException("world.meta: bad magic");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new SaveCorruptedException("world.meta: unsupported format version " + version);
            }
            byte[] body = BinaryBlockIO.readBlock(in, MAX_SETTINGS_BLOCK);
            DataInputStream bodyIn = new DataInputStream(new java.io.ByteArrayInputStream(body));
            long seed = bodyIn.readLong();
            int generatorVersion = bodyIn.readInt();
            int worldSizeTiles = bodyIn.readInt();
            double seaLevel = bodyIn.readDouble();
            double resourceDensity = bodyIn.readDouble();
            long creationTimeMillis = bodyIn.readLong();
            if (worldSizeTiles <= 0 || worldSizeTiles > (1 << 20)) {
                throw new SaveCorruptedException("world.meta: implausible worldSizeTiles " + worldSizeTiles);
            }
            return new WorldMeta(seed, generatorVersion, worldSizeTiles, seaLevel, resourceDensity, creationTimeMillis);
        }
    }
}
