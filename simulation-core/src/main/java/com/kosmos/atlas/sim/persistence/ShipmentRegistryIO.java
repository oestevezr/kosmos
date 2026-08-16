package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.trade.ShipmentRegistry;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code routes.dat}: in-flight {@link ShipmentRegistry} entries (spec §31 lists {@code routes.dat}
 * in the save directory layout). A shipment referencing a depot that no longer exists (demolished
 * while the game was closed) is not an error at load time — it simply completes as normal on its
 * next {@code ShipmentSystem} tick; nothing here needs the depot to still be there, since the
 * money side of the trade was already settled at departure (see {@code ShipmentSystem}'s javadoc).
 * The owning city id ({@code cityId}), on the other hand, is exactly what settlement needs and
 * always persists correctly regardless of what happens to the depot building.
 */
public final class ShipmentRegistryIO {

    private static final int MAGIC = 0x41545253; // "ATRS" (Atlas Routes/Shipments)
    /** Bumped to 2 in the multi-city refactor to add the owning cityId field (spec §32). */
    private static final int FORMAT_VERSION = 2;
    private static final int MAX_SHIPMENTS = 1 << 20; // ~1M in-flight shipments — generous, still bounded
    private static final int BYTES_PER_ACTIVE_SHIPMENT = 1 + 1 + 4 + 4 + 4 + 8 + 8;

    public static void write(Path file, ShipmentRegistry registry) throws IOException {
        AtomicFileWriter.write(file, (OutputStream sink) -> {
            DataOutputStream out = new DataOutputStream(sink);
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);

            int highWaterMark = registry.highWaterMark();
            java.io.ByteArrayOutputStream buf =
                new java.io.ByteArrayOutputStream(highWaterMark * BYTES_PER_ACTIVE_SHIPMENT + 8);
            DataOutputStream body = new DataOutputStream(buf);
            body.writeInt(highWaterMark);
            for (int id = 1; id < highWaterMark; id++) {
                body.writeBoolean(registry.isActive(id));
                if (!registry.isActive(id)) {
                    continue;
                }
                body.writeByte(registry.kind(id));
                body.writeByte(registry.commodity(id));
                body.writeInt(registry.quantity(id));
                body.writeInt(registry.depotBuildingId(id));
                body.writeInt(registry.cityId(id));
                body.writeLong(registry.departureTick(id));
                body.writeLong(registry.etaTick(id));
            }
            BinaryBlockIO.writeBlock(out, buf.toByteArray());
        });
    }

    public static ShipmentRegistry read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new SaveCorruptedException("routes.dat: bad magic");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new SaveCorruptedException("routes.dat: unsupported format version " + version);
            }
            byte[] body = BinaryBlockIO.readBlock(in, MAX_SHIPMENTS * BYTES_PER_ACTIVE_SHIPMENT + 8);
            DataInputStream bodyIn = new DataInputStream(new java.io.ByteArrayInputStream(body));
            int highWaterMark = bodyIn.readInt();
            if (highWaterMark < 1 || highWaterMark > MAX_SHIPMENTS) {
                throw new SaveCorruptedException("routes.dat: implausible highWaterMark " + highWaterMark);
            }
            ShipmentRegistry registry = ShipmentRegistry.createForRestore(highWaterMark);
            for (int id = 1; id < highWaterMark; id++) {
                boolean isActive = bodyIn.readBoolean();
                if (!isActive) {
                    registry.restoreTombstone(id);
                    continue;
                }
                byte kind = bodyIn.readByte();
                byte commodity = bodyIn.readByte();
                int quantity = bodyIn.readInt();
                int depotBuildingId = bodyIn.readInt();
                int cityId = bodyIn.readInt();
                long departureTick = bodyIn.readLong();
                long etaTick = bodyIn.readLong();
                registry.restoreActive(id, kind, commodity, quantity, depotBuildingId, cityId, departureTick, etaTick);
            }
            return registry;
        }
    }

    private ShipmentRegistryIO() {
    }
}
