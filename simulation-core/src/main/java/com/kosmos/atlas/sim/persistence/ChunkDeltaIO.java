package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads/writes one {@code chunks/<cx>_<cy>.delta} file (spec §10, §31). Only chunks the player
 * actually modified are ever written — virgin chunks are cheap to regenerate from the seed and
 * are never persisted individually.
 *
 * <p>Fase 1 stores a full compact snapshot of the modified chunk's layers rather than a sparse
 * change list; spec §10 flags this as an acceptable strategy ("if a chunk becomes heavily
 * modified, save a compact chunk snapshot rather than thousands of individual deltas") and it
 * keeps the format trivial to validate. A sparse per-tile change list is a documented future
 * optimization once real gameplay produces much sparser edits.
 */
public final class ChunkDeltaIO {

    private static final int MAGIC = 0x41544344; // "ATCD"
    /** Bumped to 2 in Fase 2 to add zone/road/building/service layers (spec §32 applies to save
     *  formats too: a format change must be a deliberate version bump, not a silent reinterpretation). */
    private static final int FORMAT_VERSION = 2;
    private static final int EXPECTED_PAYLOAD_LENGTH =
        WorldConstants.TILES_PER_CHUNK * (5 /* natural byte layers */ + 2 /* elevation short */
            + 4 /* resourceFlags int */ + 2 /* zoneType + roadType */ + 4 /* buildingId int */
            + 1 /* serviceFlags */)
            + 8; // + chunkX/chunkY ints

    public static void write(Path chunkFile, Chunk chunk) throws IOException {
        AtomicFileWriter.write(chunkFile, (OutputStream sink) -> {
            DataOutputStream out = new DataOutputStream(sink);
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);

            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream(EXPECTED_PAYLOAD_LENGTH);
            DataOutputStream body = new DataOutputStream(buf);
            body.writeInt(chunk.chunkX());
            body.writeInt(chunk.chunkY());
            body.write(chunk.terrainType);
            body.write(chunk.biome);
            body.write(chunk.fertility);
            body.write(chunk.moisture);
            body.write(chunk.temperature);
            for (short s : chunk.elevation) {
                body.writeShort(s);
            }
            for (int flags : chunk.resourceFlags) {
                body.writeInt(flags);
            }
            body.write(chunk.zoneType);
            body.write(chunk.roadType);
            for (int id : chunk.buildingId) {
                body.writeInt(id);
            }
            body.write(chunk.serviceFlags);
            BinaryBlockIO.writeBlock(out, buf.toByteArray());
        });
    }

    /** Applies a persisted delta onto {@code target}, which must already be reset to the delta's chunk coordinates. */
    public static void readInto(Path chunkFile, Chunk target) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(chunkFile)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new SaveCorruptedException("chunk delta: bad magic in " + chunkFile);
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new SaveCorruptedException("chunk delta: unsupported format version " + version);
            }
            byte[] body = BinaryBlockIO.readBlock(in, EXPECTED_PAYLOAD_LENGTH);
            if (body.length != EXPECTED_PAYLOAD_LENGTH) {
                throw new SaveCorruptedException("chunk delta: unexpected payload length " + body.length);
            }
            DataInputStream bodyIn = new DataInputStream(new java.io.ByteArrayInputStream(body));
            int chunkX = bodyIn.readInt();
            int chunkY = bodyIn.readInt();
            if (chunkX != target.chunkX() || chunkY != target.chunkY()) {
                throw new SaveCorruptedException(
                    "chunk delta: coordinate mismatch (file says " + chunkX + "," + chunkY
                        + ", expected " + target.chunkX() + "," + target.chunkY() + ")");
            }
            bodyIn.readFully(target.terrainType);
            bodyIn.readFully(target.biome);
            bodyIn.readFully(target.fertility);
            bodyIn.readFully(target.moisture);
            bodyIn.readFully(target.temperature);
            for (int i = 0; i < target.elevation.length; i++) {
                target.elevation[i] = bodyIn.readShort();
            }
            for (int i = 0; i < target.resourceFlags.length; i++) {
                target.resourceFlags[i] = bodyIn.readInt();
            }
            bodyIn.readFully(target.zoneType);
            bodyIn.readFully(target.roadType);
            for (int i = 0; i < target.buildingId.length; i++) {
                target.buildingId[i] = bodyIn.readInt();
            }
            bodyIn.readFully(target.serviceFlags);
            target.markDirty();
        }
    }

    public static String fileName(int chunkX, int chunkY) {
        return chunkX + "_" + chunkY + ".delta";
    }

    private ChunkDeltaIO() {
    }
}
