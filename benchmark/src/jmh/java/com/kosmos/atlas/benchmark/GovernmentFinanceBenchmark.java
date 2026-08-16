package com.kosmos.atlas.benchmark;

import com.kosmos.atlas.sim.economy.GovernmentFinanceSystem;
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

/** Benchmarks {@link GovernmentFinanceSystem#tick} over a settled representative city (spec §49). */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5)
@Fork(1)
public class GovernmentFinanceBenchmark {

    private BenchmarkCityFixture city;
    private GovernmentFinanceSystem financeSystem;

    @Setup(Level.Trial)
    public void setup() {
        city = new BenchmarkCityFixture(5, 8);
        city.settle(30);
        financeSystem = new GovernmentFinanceSystem();
    }

    @Benchmark
    public void tick() {
        financeSystem.tick(city.buildings, city.cities);
    }
}
