package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.trade.AirportRegistry;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code airports.dat}: the {@link AirportRegistry} authoritative state — every
 * {@code BuildingType.AIRPORT} building's gates/cargo-capacity/customs-efficiency, indexed by
 * {@code buildingId} (spec §19, MVP 0.6). Same "only what changed" shape as {@code ports.dat}
 * ({@link PortRegistryIO}) — a building id with no airport row simply never gets a {@code true}
 * entry.
 */
public final class AirportRegistryIO {

    private static final int MAGIC = 0x41544150; // "ATAP" (Atlas Airports)
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_BUILDINGS = 1 << 22; // matches BuildingRegistryIO's bound
    private static final int BYTES_PER_ROW = 4 + 4 + 4;

    public static void write(Path file, AirportRegistry registry) throws IOException {
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
                body.writeBoolean(registry.hasAirport(id));
                if (!registry.hasAirport(id)) {
                    continue;
                }
                body.writeInt(registry.gates(id));
                body.writeInt(registry.cargoCapacityPerTick(id));
                body.writeInt(registry.customsEfficiencyPercent(id));
            }
            BinaryBlockIO.writeBlock(out, buf.toByteArray());
        });
    }

    public static AirportRegistry read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new SaveCorruptedException("airports.dat: bad magic");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new SaveCorruptedException("airports.dat: unsupported format version " + version);
            }
            byte[] body = BinaryBlockIO.readBlock(in, MAX_BUILDINGS * BYTES_PER_ROW + 8);
            DataInputStream bodyIn = new DataInputStream(new java.io.ByteArrayInputStream(body));
            int highWaterMark = bodyIn.readInt();
            if (highWaterMark < 1 || highWaterMark > MAX_BUILDINGS) {
                throw new SaveCorruptedException("airports.dat: implausible highWaterMark " + highWaterMark);
            }
            AirportRegistry registry = AirportRegistry.createForRestore(highWaterMark);
            for (int id = 1; id < highWaterMark; id++) {
                boolean hasAirport = bodyIn.readBoolean();
                if (!hasAirport) {
                    continue;
                }
                int gates = bodyIn.readInt();
                int cargoCapacityPerTick = bodyIn.readInt();
                int customsEfficiencyPercent = bodyIn.readInt();
                registry.set(id, gates, cargoCapacityPerTick, customsEfficiencyPercent);
            }
            return registry;
        }
    }

    private AirportRegistryIO() {
    }
}
