package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.economy.GoodType;
import com.kosmos.atlas.sim.economy.GoodsLedger;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
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
 *
 * <p>{@link #writeInto}/{@link #readInto} expose the same per-good encoding without the file
 * envelope (magic/version/CRC block), so {@code CityRegistryIO} can embed one ledger per city
 * inside {@code cities.dat} instead of duplicating this logic — the standalone {@code write}/
 * {@code read} methods below remain for any single-ledger use.
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
            writeInto(body, ledger);
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
            return readInto(bodyIn, "economy.dat");
        }
    }

    /** Writes one {@link GoodsLedger}'s per-good state, with no envelope — for embedding in a larger block. */
    public static void writeInto(DataOutput out, GoodsLedger ledger) throws IOException {
        out.writeInt(GoodType.COUNT);
        for (byte g = 0; g < GoodType.COUNT; g++) {
            out.writeInt(ledger.inventory(g));
            out.writeDouble(ledger.basePrice(g));
            out.writeInt(ledger.targetInventory(g));
        }
    }

    /** Reads one {@link GoodsLedger}'s per-good state written by {@link #writeInto}, already repriced. */
    public static GoodsLedger readInto(DataInput in, String sourceLabel) throws IOException {
        GoodsLedger ledger = new GoodsLedger();
        readInto(in, sourceLabel, ledger);
        return ledger;
    }

    /** Reads into an existing {@link GoodsLedger} (e.g. one {@code CityRegistry} already owns), already repriced. */
    public static void readInto(DataInput in, String sourceLabel, GoodsLedger ledger) throws IOException {
        int goodCount = in.readInt();
        if (goodCount != GoodType.COUNT) {
            throw new SaveCorruptedException(
                sourceLabel + ": good count mismatch (file has " + goodCount + ", build expects " + GoodType.COUNT + ")");
        }
        for (byte g = 0; g < GoodType.COUNT; g++) {
            int inventory = in.readInt();
            double basePrice = in.readDouble();
            int targetInventory = in.readInt();
            ledger.setInventory(g, inventory);
            ledger.setBasePrice(g, basePrice);
            ledger.setTargetInventory(g, targetInventory);
        }
        ledger.repriceAll();
    }

    private GoodsLedgerIO() {
    }
}
