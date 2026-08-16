package com.kosmos.atlas.benchmark;

import com.kosmos.atlas.sim.util.LongIntHashMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks {@link LongIntHashMap} put/get against java.util.HashMap<Long,Integer> to quantify
 * the boxing/allocation cost it was written to avoid on the chunk-lookup hot path (spec §42.2).
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Fork(1)
public class LongIntHashMapBenchmark {

    private static final int ENTRIES = 512; // ~ HardwareProfile.HIGH.maxLoadedChunks() order of magnitude

    private LongIntHashMap primitiveMap;
    private java.util.HashMap<Long, Integer> boxedMap;

    @Setup(Level.Trial)
    public void setup() {
        primitiveMap = new LongIntHashMap(ENTRIES * 2, 0.6f);
        boxedMap = new java.util.HashMap<>(ENTRIES * 2);
        for (int i = 0; i < ENTRIES; i++) {
            primitiveMap.put(i, i);
            boxedMap.put((long) i, i);
        }
    }

    @Benchmark
    public void primitiveGet(Blackhole bh) {
        for (int i = 0; i < ENTRIES; i++) {
            bh.consume(primitiveMap.get(i, -1));
        }
    }

    @Benchmark
    public void boxedHashMapGet(Blackhole bh) {
        for (int i = 0; i < ENTRIES; i++) {
            bh.consume(boxedMap.get((long) i));
        }
    }
}
