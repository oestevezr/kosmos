package com.kosmos.atlas.benchmark;

import com.kosmos.atlas.sim.world.gen.DeterministicNoise;
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

/** Benchmarks the deterministic noise primitive that every generation layer is built from (spec §5.3). */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Fork(1)
public class NoiseBenchmark {

    private DeterministicNoise noise;

    @Setup(Level.Trial)
    public void setup() {
        noise = new DeterministicNoise(42L, 1, 0);
    }

    @Benchmark
    public void singleOctaveSample(Blackhole bh) {
        bh.consume(noise.sample(123.456, 789.012));
    }

    @Benchmark
    public void fiveOctaveFbm(Blackhole bh) {
        bh.consume(noise.fbm(123.456, 789.012, 5, 2.0, 0.5));
    }
}
