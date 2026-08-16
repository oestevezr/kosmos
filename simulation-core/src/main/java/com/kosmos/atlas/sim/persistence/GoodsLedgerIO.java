package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.economy.GoodType;
import com.kosmos.atlas.sim.economy.GoodsLedger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code economy.dat}: the {@link GoodsLedger} authoritative state (spec §31 lists
 * {@code economy.dat} in the save directory layout). Only inventory, base price and target
 * inventory are persisted per good — everything else on {@link GoodsLedger} is either a per-tick
 * tally (reset every {@code MarketSystem.tick} regardless) or fully derived from these three
 * (price via {@link GoodsLedger#repriceAll()}, transport cost from the current
 * {@code RegionalGraph}), the same "don't persist what you can recompute" rule
 * {@code Chunk.serviceFlags} already follows.
 */
public final class GoodsLedgerIO {

    private static final int MAGIC = 0x41544547; // "ATEG" (Atlas Economy Goods)
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_PAYLOAD = GoodType.COUNT * 16 + 8;

    public static void write(Path file, GoodsLedger ledger) throws IOException {
        AtomicFileWriter.write(file, (OutputStream sink) -> {
            DataOutputStream out = new DataOutputStream(sink);
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);

            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream(MAX_PAYLOAD);
            DataOutputStream body = new DataOutputStream(buf);
            body.writeInt(GoodType.COUNT);
            for (byte g = 0; g < GoodType.COUNT; g++) {
                body.writeInt(ledger.inventory(g));
                body.writeDouble(ledger.basePrice(g));
                body.writeInt(ledger.targetInventory(g));
            }
            BinaryBlockIO.writeBlock(out, buf.toByteArray());
        });
    }

    /** Reads {@code file} into a fresh {@link GoodsLedger}, already repriced and ready to use. */
    public static GoodsLedger read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new SaveCorruptedException("economy.dat: bad magic");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new SaveCorruptedException("economy.dat: unsupported format version " + version);
            }
            byte[] body = BinaryBlockIO.readBlock(in, MAX_PAYLOAD);
            DataInputStream bodyIn = new DataInputStream(new java.io.ByteArrayInputStream(body));
            int goodCount = bodyIn.readInt();
            if (goodCount != GoodType.COUNT) {
                throw new SaveCorruptedException(
                    "economy.dat: good count mismatch (file has " + goodCount + ", build expects " + GoodType.COUNT + ")");
            }
            GoodsLedger ledger = new GoodsLedger();
            for (byte g = 0; g < GoodType.COUNT; g++) {
                int inventory = bodyIn.readInt();
                double basePrice = bodyIn.readDouble();
                int targetInventory = bodyIn.readInt();
                ledger.setInventory(g, inventory);
                ledger.setBasePrice(g, basePrice);
                ledger.setTargetInventory(g, targetInventory);
            }
            ledger.repriceAll();
            return ledger;
        }
    }

    private GoodsLedgerIO() {
    }
}
