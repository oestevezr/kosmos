package com.kosmos.atlas.benchmark;

import com.kosmos.atlas.sim.utility.UtilitySystem;
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

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks {@link UtilitySystem#update}, the one Fase 2 system that always does a full
 * multi-source flood fill (spec §49) — it has no cheap "nothing changed" path the way
 * {@code RoadNetwork} does, so this number is the one to watch as a city's power/water source
 * count grows.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5)
@Fork(1)
public class UtilitySystemBenchmark {

    private BenchmarkCityFixture city;
    private UtilitySystem utilitySystem;

    @Setup(Level.Trial)
    public void setup() {
        city = new BenchmarkCityFixture(5, 8);
        utilitySystem = new UtilitySystem();
        utilitySystem.update(city.store, city.buildings); // let LongQueue/LongIntHashMap grow once, outside measurement
    }

    @Benchmark
    public void update() {
        utilitySystem.update(city.store, city.buildings);
    }
}
