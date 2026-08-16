package com.kosmos.atlas.benchmark;

import com.kosmos.atlas.sim.economy.LoanLenderType;
import com.kosmos.atlas.sim.economy.LoanRegistry;
import com.kosmos.atlas.sim.economy.LoanSystem;
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
 * Benchmarks {@link LoanSystem#tick}, measured in the same change that introduces the loan system
 * (spec §49 and the explicit lesson from Fase 2's {@code UtilitySystem} rework — see
 * {@code docs/roadmap.md}). 500 active loans is a generous steady-state for a multi-city world
 * where every city carries a handful of external and inter-city loans at once.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5)
@Fork(1)
public class LoanSystemBenchmark {

    private static final int ACTIVE_LOANS = 500;

    private LoanRegistry loans;
    private LoanSystem system;

    @Setup(Level.Trial)
    public void setup() {
        loans = new LoanRegistry(ACTIVE_LOANS);
        for (int i = 0; i < ACTIVE_LOANS; i++) {
            byte lenderType = (i % 3 == 0) ? LoanLenderType.EXTERNAL_MARKET : LoanLenderType.CITY;
            int lenderCityId = lenderType == LoanLenderType.CITY ? (i % 20) + 1 : 0;
            loans.create(lenderType, (i % 20) + 1, lenderCityId, 1000.0, 0.005, 0);
        }
        system = new LoanSystem();
    }

    @Benchmark
    public void tick() {
        system.tick(loans);
    }
}
