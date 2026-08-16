package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.world.Chunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A save file is untrusted input from the moment it can be corrupted by a crash or hand-edited —
 * this test fuzzes byte-level corruption of a chunk delta and a hand-crafted "implausible length"
 * header, asserting every case either loads correctly or fails cleanly with
 * {@link SaveCorruptedException}, and — critically — never hangs or attempts an unbounded
 * allocation (spec §8 security notes: no OOM/allocation bomb from a manipulated save).
 */
class SaveCorruptionFuzzTest {

    @Test
    void randomByteFlipsNeverHangOrCrashTheJvm(@TempDir Path tmp) throws IOException {
        Chunk source = new Chunk();
        source.reset(2, 2);
        source.terrainType[0] = 3;
        source.elevation[10] = 1234;
        source.markDirty();

        Path chunkFile = tmp.resolve(ChunkDeltaIO.fileName(2, 2));
        ChunkDeltaIO.write(chunkFile, source);
        byte[] good = Files.readAllBytes(chunkFile);

        Random rng = new Random(1337); // fixed seed: deterministic, reproducible fuzz run
        for (int trial = 0; trial < 200; trial++) {
            byte[] corrupted = good.clone();
            int flips = 1 + rng.nextInt(4);
            for (int f = 0; f < flips; f++) {
                int pos = rng.nextInt(corrupted.length);
                corrupted[pos] ^= (byte) (1 << rng.nextInt(8));
            }
            Path corruptFile = tmp.resolve("corrupt_" + trial + ".delta");
            Files.write(corruptFile, corrupted);

            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                Chunk target = new Chunk();
                target.reset(2, 2);
                try {
                    ChunkDeltaIO.readInto(corruptFile, target);
                    // A flip that happened to leave magic/version/length/CRC all consistent is
                    // astronomically unlikely but not impossible to rule out by construction —
                    // if it loads, that's acceptable as long as it didn't hang or throw something
                    // other than a controlled IOException.
                } catch (SaveCorruptedException expected) {
                    // The expected outcome for the overwhelming majority of flips.
                } catch (java.io.EOFException expectedTruncation) {
                    // Also acceptable: a flip inside the length field can under-read the stream.
                }
            });
        }
    }

    @Test
    void implausibleDeclaredLengthIsRejectedBeforeAllocating(@TempDir Path tmp) throws IOException {
        Path malformed = tmp.resolve("0_0.delta");
        try (var out = new java.io.DataOutputStream(Files.newOutputStream(malformed))) {
            out.writeInt(0x41544344); // correct magic
            out.writeInt(1);          // correct format version
            out.writeInt(Integer.MAX_VALUE - 8); // declared block length: an allocation bomb if trusted
            // Deliberately do not write that many bytes — a naive reader would try to allocate
            // ~2 GB and then hang reading bytes that don't exist.
        }

        Chunk target = new Chunk();
        target.reset(0, 0);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
            assertThrows(SaveCorruptedException.class, () -> ChunkDeltaIO.readInto(malformed, target)));
    }
}
