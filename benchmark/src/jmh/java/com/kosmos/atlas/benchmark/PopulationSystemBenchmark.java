package com.kosmos.atlas.benchmark;

import com.kosmos.atlas.sim.population.PopulationSystem;
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
 * Benchmarks {@link PopulationSystem#tick} once a representative city has already settled (spec
 * §49) — the realistic steady-state cost, not the one-time burst of every zoned tile spawning a
 * building on the very first tick.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5)
@Fork(1)
public class PopulationSystemBenchmark {

    private BenchmarkCityFixture city;
    private PopulationSystem populationSystem;

    @Setup(Level.Trial)
    public void setup() {
        city = new BenchmarkCityFixture(5, 8);
        city.settle(30); // run to steady state before measuring
        populationSystem = new PopulationSystem();
    }

    @Benchmark
    public void tick() {
        populationSystem.tick(city.store, city.buildings);
    }
}
