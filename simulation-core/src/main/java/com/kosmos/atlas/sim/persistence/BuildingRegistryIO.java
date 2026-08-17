package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.population.BuildingRegistry;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code settlements.dat}: the {@link BuildingRegistry} aggregate state (spec §31 lists
 * {@code settlements.dat} in the save directory layout). Building ids referenced from
 * {@code Chunk.buildingId} are only meaningful together with this file — loading chunk deltas
 * without it would leave tile->building references dangling.
 */
public final class BuildingRegistryIO {

    private static final int MAGIC = 0x41544252; // "ATBR"
    /** Bumped to 4 by the density-evolution mechanic to add densityLevel (spec §32 applies to
     *  save formats too: a format change must be a deliberate version bump). */
    private static final int FORMAT_VERSION = 4;
    private static final int MAX_BUILDINGS = 1 << 22; // ~4M buildings — generous, still bounded

    public static void write(Path file, BuildingRegistry registry) throws IOException {
        AtomicFileWriter.write(file, (OutputStream sink) -> {
            DataOutputStream out = new DataOutputStream(sink);
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);

            int highWaterMark = registry.highWaterMark();
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream(highWaterMark * 32 + 8);
            DataOutputStream body = new DataOutputStream(buf);
            body.writeInt(highWaterMark);
            for (int id = 1; id < highWaterMark; id++) {
                body.writeBoolean(registry.isActive(id));
                if (!registry.isActive(id)) {
                    continue;
                }
                body.writeByte(registry.type(id));
                body.writeInt(registry.tileX(id));
                body.writeInt(registry.tileY(id));
                body.writeInt(registry.cityId(id));
                body.writeInt(registry.population(id));
                body.writeInt(registry.jobs(id));
                body.writeByte(registry.incomeLevel(id));
                body.writeByte(registry.employmentRatePercent(id));
                body.writeByte(registry.satisfactionPercent(id));
                body.writeByte(registry.densityLevel(id));
                body.writeByte(registry.outputGood(id));
                body.writeInt(registry.outputRatePerTick(id));
                body.writeByte(registry.inputGood(id));
                body.writeInt(registry.inputRatePerTick(id));
            }
            BinaryBlockIO.writeBlock(out, buf.toByteArray());
        });
    }

    public static BuildingRegistry read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new SaveCorruptedException("settlements.dat: bad magic");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new SaveCorruptedException("settlements.dat: unsupported format version " + version);
            }
            byte[] body = BinaryBlockIO.readBlock(in, MAX_BUILDINGS * 32 + 8);
            DataInputStream bodyIn = new DataInputStream(new java.io.ByteArrayInputStream(body));
            int highWaterMark = bodyIn.readInt();
            if (highWaterMark < 1 || highWaterMark > MAX_BUILDINGS) {
                throw new SaveCorruptedException("settlements.dat: implausible highWaterMark " + highWaterMark);
            }
            BuildingRegistry registry = BuildingRegistry.createForRestore(highWaterMark);
            for (int id = 1; id < highWaterMark; id++) {
                boolean isActive = bodyIn.readBoolean();
                if (!isActive) {
                    registry.restoreTombstone(id);
                    continue;
                }
                byte type = bodyIn.readByte();
                int tileX = bodyIn.readInt();
                int tileY = bodyIn.readInt();
                int cityId = bodyIn.readInt();
                int population = bodyIn.readInt();
                int jobs = bodyIn.readInt();
                byte incomeLevel = bodyIn.readByte();
                int employmentRate = bodyIn.readUnsignedByte();
                int satisfaction = bodyIn.readUnsignedByte();
                int densityLevel = bodyIn.readUnsignedByte();
                byte outputGood = bodyIn.readByte();
                int outputRate = bodyIn.readInt();
                byte inputGood = bodyIn.readByte();
                int inputRate = bodyIn.readInt();
                registry.restoreActive(id, type, tileX, tileY, cityId, population, jobs,
                    incomeLevel, employmentRate, satisfaction, densityLevel, outputGood, outputRate, inputGood, inputRate);
            }
            return registry;
        }
    }

    private BuildingRegistryIO() {
    }
}
