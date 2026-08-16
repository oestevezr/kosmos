package com.kosmos.atlas.sim.persistence;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32C;

/**
 * Length-prefixed, CRC-checked binary block primitive used by every save file (spec §31, §47).
 *
 * <p>Two rules make loading a hostile or corrupted file safe:
 * <ol>
 *   <li>the declared length is validated against {@code maxLength} <em>before</em> the byte array
 *       is allocated, so a manipulated save cannot force an out-of-memory allocation bomb;</li>
 *   <li>every block carries a CRC32C checksum, checked on read, so silent bit-level corruption or
 *       tampering surfaces as a clean {@link SaveCorruptedException} instead of a garbled load.</li>
 * </ol>
 * Deliberately hand-rolled instead of {@code ObjectOutputStream}/{@code ObjectInputStream}
 * (CWE-502): this project never deserializes native Java object graphs from disk.
 */
public final class BinaryBlockIO {

    private BinaryBlockIO() {
    }

    public static void writeBlock(DataOutputStream out, byte[] payload) throws IOException {
        CRC32C crc = new CRC32C();
        crc.update(payload);
        out.writeInt(payload.length);
        out.write(payload);
        out.writeLong(crc.getValue());
    }

    public static byte[] readBlock(DataInputStream in, int maxLength) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > maxLength) {
            throw new SaveCorruptedException(
                "Implausible block length " + length + " (max " + maxLength + ")");
        }
        byte[] payload = new byte[length];
        in.readFully(payload);
        long expectedCrc = in.readLong();
        CRC32C crc = new CRC32C();
        crc.update(payload);
        if (crc.getValue() != expectedCrc) {
            throw new SaveCorruptedException("CRC mismatch: save block is corrupted");
        }
        return payload;
    }
}
