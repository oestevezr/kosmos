package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.trade.PortRegistry;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code ports.dat}: the {@link PortRegistry} authoritative state — every {@code BuildingType.PORT}
 * building's berths/cargo-capacity/customs-efficiency, indexed by {@code buildingId} (spec §17,
 * MVP 0.5). A building id with no port row simply never gets a {@code true} entry — same
 * "only what changed" shape as every other registry format here.
 */
public final class PortRegistryIO {

    private static final int MAGIC = 0x4154504F; // "ATPO" (Atlas Ports)
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_BUILDINGS = 1 << 22; // matches BuildingRegistryIO's bound
    private static final int BYTES_PER_ROW = 4 + 4 + 4;

    public static void write(Path file, PortRegistry registry) throws IOException {
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
                body.writeBoolean(registry.hasPort(id));
                if (!registry.hasPort(id)) {
                    continue;
                }
                body.writeInt(registry.berths(id));
                body.writeInt(registry.cargoCapacityPerTick(id));
                body.writeInt(registry.customsEfficiencyPercent(id));
            }
            BinaryBlockIO.writeBlock(out, buf.toByteArray());
        });
    }

    public static PortRegistry read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new SaveCorruptedException("ports.dat: bad magic");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new SaveCorruptedException("ports.dat: unsupported format version " + version);
            }
            byte[] body = BinaryBlockIO.readBlock(in, MAX_BUILDINGS * BYTES_PER_ROW + 8);
            DataInputStream bodyIn = new DataInputStream(new java.io.ByteArrayInputStream(body));
            int highWaterMark = bodyIn.readInt();
            if (highWaterMark < 1 || highWaterMark > MAX_BUILDINGS) {
                throw new SaveCorruptedException("ports.dat: implausible highWaterMark " + highWaterMark);
            }
            PortRegistry registry = PortRegistry.createForRestore(highWaterMark);
            for (int id = 1; id < highWaterMark; id++) {
                boolean hasPort = bodyIn.readBoolean();
                if (!hasPort) {
                    continue;
                }
                int berths = bodyIn.readInt();
                int cargoCapacityPerTick = bodyIn.readInt();
                int customsEfficiencyPercent = bodyIn.readInt();
                registry.set(id, berths, cargoCapacityPerTick, customsEfficiencyPercent);
            }
            return registry;
        }
    }

    private PortRegistryIO() {
    }
}
