package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.trade.StationRegistry;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code stations.dat}: the {@link StationRegistry} authoritative state — every
 * {@code BuildingType.RAIL_TERMINAL} building's platforms/cargo-capacity, indexed by
 * {@code buildingId} (spec §18, MVP 0.6). Same "only what changed" shape as {@code ports.dat}/
 * {@code airports.dat}.
 */
public final class StationRegistryIO {

    private static final int MAGIC = 0x41545354; // "ATST" (Atlas Stations)
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_BUILDINGS = 1 << 22; // matches BuildingRegistryIO's bound
    private static final int BYTES_PER_ROW = 4 + 4;

    public static void write(Path file, StationRegistry registry) throws IOException {
        AtomicFileWriter.write(file, (OutputStream sink) -> {
            DataOutputStream out = new DataOutputStream(sink);
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);

            int highWaterMark = registry.highWaterMark();
            java.io.ByteArrayOutputStream buf =
                new java.io.ByteArrayOutputStream(highWaterMark * BYTES_PER_ROW + 8);
            DataOutputStream body = new DataOutputStream(buf);
            body.writeInt(highWaterMark);
            for (int id = 1; id < highWaterMark; id++) {
                body.writeBoolean(registry.hasTerminal(id));
                if (!registry.hasTerminal(id)) {
                    continue;
                }
                body.writeInt(registry.platforms(id));
                body.writeInt(registry.cargoCapacityPerTick(id));
            }
            BinaryBlockIO.writeBlock(out, buf.toByteArray());
        });
    }

    public static StationRegistry read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new SaveCorruptedException("stations.dat: bad magic");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new SaveCorruptedException("stations.dat: unsupported format version " + version);
            }
            byte[] body = BinaryBlockIO.readBlock(in, MAX_BUILDINGS * BYTES_PER_ROW + 8);
            DataInputStream bodyIn = new DataInputStream(new java.io.ByteArrayInputStream(body));
            int highWaterMark = bodyIn.readInt();
            if (highWaterMark < 1 || highWaterMark > MAX_BUILDINGS) {
                throw new SaveCorruptedException("stations.dat: implausible highWaterMark " + highWaterMark);
            }
            StationRegistry registry = StationRegistry.createForRestore(highWaterMark);
            for (int id = 1; id < highWaterMark; id++) {
                boolean hasTerminal = bodyIn.readBoolean();
                if (!hasTerminal) {
                    continue;
                }
                int platforms = bodyIn.readInt();
                int cargoCapacityPerTick = bodyIn.readInt();
                registry.set(id, platforms, cargoCapacityPerTick);
            }
            return registry;
        }
    }

    private StationRegistryIO() {
    }
}
