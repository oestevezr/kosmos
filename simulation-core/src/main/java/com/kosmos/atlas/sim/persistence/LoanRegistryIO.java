package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.economy.LoanRegistry;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** {@code loans.dat}: the {@link LoanRegistry} authoritative state (spec §31-style save layout). */
public final class LoanRegistryIO {

    private static final int MAGIC = 0x41544C4E; // "ATLN" (Atlas Loans)
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_LOANS = 1 << 20; // generous, still bounded
    private static final int BYTES_PER_ACTIVE_LOAN = 1 + 4 + 4 + 8 + 8 + 8 + 8;

    public static void write(Path file, LoanRegistry registry) throws IOException {
        AtomicFileWriter.write(file, (OutputStream sink) -> {
            DataOutputStream out = new DataOutputStream(sink);
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);

            int highWaterMark = registry.highWaterMark();
            java.io.ByteArrayOutputStream buf =
                new java.io.ByteArrayOutputStream(highWaterMark * BYTES_PER_ACTIVE_LOAN + 8);
            DataOutputStream body = new DataOutputStream(buf);
            body.writeInt(highWaterMark);
            for (int id = 1; id < highWaterMark; id++) {
                body.writeBoolean(registry.isActive(id));
                if (!registry.isActive(id)) {
                    continue;
                }
                body.writeByte(registry.lenderType(id));
                body.writeInt(registry.borrowerCityId(id));
                body.writeInt(registry.lenderCityId(id));
                body.writeDouble(registry.principal(id));
                body.writeDouble(registry.balance(id));
                body.writeDouble(registry.interestRatePerAccrual(id));
                body.writeLong(registry.originationTick(id));
            }
            BinaryBlockIO.writeBlock(out, buf.toByteArray());
        });
    }

    public static LoanRegistry read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new SaveCorruptedException("loans.dat: bad magic");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new SaveCorruptedException("loans.dat: unsupported format version " + version);
            }
            byte[] body = BinaryBlockIO.readBlock(in, MAX_LOANS * BYTES_PER_ACTIVE_LOAN + 8);
            DataInputStream bodyIn = new DataInputStream(new java.io.ByteArrayInputStream(body));
            int highWaterMark = bodyIn.readInt();
            if (highWaterMark < 1 || highWaterMark > MAX_LOANS) {
                throw new SaveCorruptedException("loans.dat: implausible highWaterMark " + highWaterMark);
            }
            LoanRegistry registry = LoanRegistry.createForRestore(highWaterMark);
            for (int id = 1; id < highWaterMark; id++) {
                boolean isActive = bodyIn.readBoolean();
                if (!isActive) {
                    registry.restoreTombstone(id);
                    continue;
                }
                byte lenderType = bodyIn.readByte();
                int borrowerCityId = bodyIn.readInt();
                int lenderCityId = bodyIn.readInt();
                double principal = bodyIn.readDouble();
                double balance = bodyIn.readDouble();
                double interestRate = bodyIn.readDouble();
                long originationTick = bodyIn.readLong();
                registry.restoreActive(id, lenderType, borrowerCityId, lenderCityId, principal, balance, interestRate, originationTick);
            }
            return registry;
        }
    }

    private LoanRegistryIO() {
    }
}
