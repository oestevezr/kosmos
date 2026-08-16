package com.kosmos.atlas.benchmark;

import com.kosmos.atlas.sim.transport.RoadNetwork;
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
 * Benchmarks {@link RoadNetwork#update} over a representative city footprint (spec §49): a cold
 * full per-tile scan (a fresh {@link RoadNetwork}, or one whose chunks all just changed) versus
 * the steady-state case where the dirty-tracking skip (spec §41) short-circuits every chunk.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3)
@Fork(1)
public class RoadNetworkBenchmark {

    /** Every invocation gets a fresh {@link RoadNetwork}, so every call pays the full scan. */
    @State(Scope.Thread)
    public static class ColdState {
        BenchmarkCityFixture city;

        @Setup(Level.Trial)
        public void setup() {
            city = new BenchmarkCityFixture(5, 8);
        }
    }

    /** One {@link RoadNetwork} reused across the whole trial: after the first call, every chunk is unchanged. */
    @State(Scope.Thread)
    public static class SteadyState {
        BenchmarkCityFixture city;
        RoadNetwork network;

        @Setup(Level.Trial)
        public void setup() {
            city = new BenchmarkCityFixture(5, 8);
            network = new RoadNetwork();
            network.update(city.store); // prime the dirty-tracking cache once, outside measurement
        }
    }

    @Benchmark
    public void coldFullScan(ColdState state) {
        new RoadNetwork().update(state.city.store);
    }

    @Benchmark
    public void steadyStateSkip(SteadyState state) {
        state.network.update(state.city.store);
    }
}
