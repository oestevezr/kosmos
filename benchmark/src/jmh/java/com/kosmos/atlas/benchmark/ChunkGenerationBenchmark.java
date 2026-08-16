package com.kosmos.atlas.benchmark;

import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.gen.ProceduralGenerator;
import com.kosmos.atlas.sim.world.gen.WorldGenSettings;
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
 * Microbenchmarks the single most performance-critical loop in Fase 1: generating one 32x32 chunk
 * (spec §48, §49 — "no major system should be considered complete without profiling"). Run with:
 * <pre>./gradlew :benchmark:jmh</pre>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3)
@Fork(1)
public class ChunkGenerationBenchmark {

    private ProceduralGenerator generator;
    private Chunk chunk;
    private int counter;

    @Setup(Level.Trial)
    public void setup() {
        WorldGenSettings settings = WorldGenSettings.balanced(42L, 4096);
        generator = new ProceduralGenerator(settings);
        chunk = new Chunk();
    }

    @Benchmark
    public void generateChunk(Blackhole bh) {
        chunk.reset(counter, -counter);
        generator.generate(chunk);
        counter++;
        bh.consume(chunk);
    }
}
