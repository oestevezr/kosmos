package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.economy.GoodType;
import com.kosmos.atlas.sim.economy.GovernmentFinance;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code cities.dat}: the {@link CityRegistry} authoritative state — every player-founded city's
 * identity, treasury/tax rates ({@link GovernmentFinance}) and goods ledger inventory (spec §9's
 * multi-city model). This file replaces the old singleton {@code economy.dat}
 * ({@link GoodsLedgerIO}) now that the goods ledger and treasury are owned per-city rather than
 * once per world — {@link GoodsLedgerIO#writeInto}/{@link GoodsLedgerIO#readInto} are reused here
 * to encode each city's ledger without duplicating that logic.
 */
public final class CityRegistryIO {

    private static final int MAGIC = 0x41544349; // "ATCI" (Atlas Cities)
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_CITIES = 1 << 16; // generous, still bounded
    private static final int MAX_NAME_LENGTH = 32;
    private static final int BYTES_PER_ACTIVE_CITY_UPPER_BOUND =
        2 + MAX_NAME_LENGTH * 3 + 4 + 4 + 8 + 8 * 4 + (GoodType.COUNT * 16 + 4);

    public static void write(Path file, CityRegistry registry) throws IOException {
        AtomicFileWriter.write(file, (OutputStream sink) -> {
            DataOutputStream out = new DataOutputStream(sink);
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);

            int highWaterMark = registry.highWaterMark();
            java.io.ByteArrayOutputStream buf =
                new java.io.ByteArrayOutputStream(highWaterMark * BYTES_PER_ACTIVE_CITY_UPPER_BOUND + 8);
            DataOutputStream body = new DataOutputStream(buf);
            body.writeInt(highWaterMark);
            for (int id = 1; id < highWaterMark; id++) {
                body.writeBoolean(registry.isActive(id));
                if (!registry.isActive(id)) {
                    continue;
                }
                body.writeUTF(registry.name(id));
                body.writeInt(registry.tileX(id));
                body.writeInt(registry.tileY(id));
                body.writeLong(registry.foundedTick(id));
                GovernmentFinance finance = registry.finance(id);
                body.writeDouble(finance.treasuryBalance());
                body.writeDouble(finance.taxRate(WorldConstants.ZONE_RESIDENTIAL));
                body.writeDouble(finance.taxRate(WorldConstants.ZONE_COMMERCIAL));
                body.writeDouble(finance.taxRate(WorldConstants.ZONE_INDUSTRIAL));
                GoodsLedgerIO.writeInto(body, registry.ledger(id));
            }
            BinaryBlockIO.writeBlock(out, buf.toByteArray());
        });
    }

    public static CityRegistry read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new SaveCorruptedException("cities.dat: bad magic");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new SaveCorruptedException("cities.dat: unsupported format version " + version);
            }
            byte[] body = BinaryBlockIO.readBlock(in, MAX_CITIES * BYTES_PER_ACTIVE_CITY_UPPER_BOUND + 8);
            DataInputStream bodyIn = new DataInputStream(new java.io.ByteArrayInputStream(body));
            int highWaterMark = bodyIn.readInt();
            if (highWaterMark < 1 || highWaterMark > MAX_CITIES) {
                throw new SaveCorruptedException("cities.dat: implausible highWaterMark " + highWaterMark);
            }
            CityRegistry registry = CityRegistry.createForRestore(highWaterMark);
            for (int id = 1; id < highWaterMark; id++) {
                boolean isActive = bodyIn.readBoolean();
                if (!isActive) {
                    registry.restoreTombstone(id);
                    continue;
                }
                String name;
                try {
                    name = bodyIn.readUTF();
                } catch (java.io.UTFDataFormatException e) {
                    throw new SaveCorruptedException("cities.dat: malformed city name");
                }
                int tileX = bodyIn.readInt();
                int tileY = bodyIn.readInt();
                long foundedTick = bodyIn.readLong();
                double treasuryBalance = bodyIn.readDouble();
                double residentialTaxRate = bodyIn.readDouble();
                double commercialTaxRate = bodyIn.readDouble();
                double industrialTaxRate = bodyIn.readDouble();
                registry.restoreActive(id, name, tileX, tileY, foundedTick,
                    treasuryBalance, residentialTaxRate, commercialTaxRate, industrialTaxRate);
                GoodsLedgerIO.readInto(bodyIn, "cities.dat", registry.ledger(id));
            }
            return registry;
        }
    }

    private CityRegistryIO() {
    }
}
