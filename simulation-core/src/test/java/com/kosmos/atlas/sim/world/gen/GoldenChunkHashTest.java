package com.kosmos.atlas.sim.world.gen;

import com.kosmos.atlas.sim.world.Chunk;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the exact output of the world generator for a fixed seed + chunk coordinate.
 *
 * <p>This is the single most important regression test in the project: spec §32 requires that
 * {@code seed + generatorVersion + chunkCoord} always reproduces the same virgin chunk. If this
 * test ever fails after a change to {@link ProceduralGenerator}, {@link DeterministicNoise} or
 * {@link SplitMix64}, that change silently invalidated every existing save — bump
 * {@link GeneratorVersion#CURRENT} and update the golden value deliberately, never accidentally.
 */
class GoldenChunkHashTest {

    private static final long SEED = 424242L;
    private static final int CHUNK_X = 3;
    private static final int CHUNK_Y = -2;

    // Golden value captured from the reference implementation at GeneratorVersion.CURRENT = 1.
    private static final long EXPECTED_HASH = 0xF1D6AEC94120929EL;

    @Test
    void goldenChunkMatchesPinnedHash() {
        long hash = generateAndHash();
        assertEquals(EXPECTED_HASH, hash,
            "Generator output changed for seed=" + SEED + " chunk=(" + CHUNK_X + "," + CHUNK_Y + "). "
                + "If intentional, bump GeneratorVersion.CURRENT and update EXPECTED_HASH deliberately.");
    }

    @Test
    void sameSeedAndCoordinateAlwaysProducesSameChunk() {
        long first = generateAndHash();
        long second = generateAndHash();
        assertEquals(first, second, "Generation must be deterministic across repeated calls");
    }

    private static long generateAndHash() {
        WorldGenSettings settings = WorldGenSettings.balanced(SEED, 4096);
        ProceduralGenerator generator = new ProceduralGenerator(settings);
        Chunk chunk = new Chunk();
        chunk.reset(CHUNK_X, CHUNK_Y);
        generator.generate(chunk);
        return fnv1a64(chunk);
    }

    /** FNV-1a 64-bit over every layer's bytes, in a fixed field order. */
    static long fnv1a64(Chunk chunk) {
        long hash = 0xCBF29CE484222325L;
        final long prime = 0x100000001B3L;
        hash = mixBytes(hash, prime, chunk.terrainType);
        hash = mixBytes(hash, prime, chunk.biome);
        hash = mixBytes(hash, prime, chunk.fertility);
        hash = mixBytes(hash, prime, chunk.moisture);
        hash = mixBytes(hash, prime, chunk.temperature);
        ByteBuffer shortBuf = ByteBuffer.allocate(chunk.elevation.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : chunk.elevation) {
            shortBuf.putShort(s);
        }
        hash = mixBytes(hash, prime, shortBuf.array());
        ByteBuffer intBuf = ByteBuffer.allocate(chunk.resourceFlags.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int f : chunk.resourceFlags) {
            intBuf.putInt(f);
        }
        hash = mixBytes(hash, prime, intBuf.array());
        return hash;
    }

    private static long mixBytes(long hash, long prime, byte[] data) {
        for (byte b : data) {
            hash ^= (b & 0xFF);
            hash *= prime;
        }
        return hash;
    }
}
