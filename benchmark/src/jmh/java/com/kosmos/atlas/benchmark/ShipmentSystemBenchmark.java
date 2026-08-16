package com.kosmos.atlas.benchmark;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.trade.ShipmentKind;
import com.kosmos.atlas.sim.trade.ShipmentRegistry;
import com.kosmos.atlas.sim.trade.ShipmentSystem;
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
 * Benchmarks {@link ShipmentSystem#tick}, per spec §49 and the explicit lesson from Fase 2's
 * {@code UtilitySystem} rework: measure a new full-recompute-shaped system's cost in the same
 * change that introduces it, not after (see {@code docs/roadmap.md}'s MVP 0.4 notes). 500 active
 * shipments is a deliberately generous steady-state — a single depot is capped at 3 concurrent
 * shipments (spec §17 bottleneck), so 500 represents dozens of depots trading simultaneously.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5)
@Fork(1)
public class ShipmentSystemBenchmark {

    private static final int ACTIVE_SHIPMENTS = 500;

    private ShipmentRegistry shipments;
    private CityRegistry cities;
    private ShipmentSystem system;

    @Setup(Level.Invocation)
    public void setup() {
        // Fresh per invocation: tick() completes arrived shipments (mutates the registry), so a
        // Level.Trial fixture would shrink across iterations and stop measuring steady-state cost.
        shipments = new ShipmentRegistry(ACTIVE_SHIPMENTS);
        cities = new CityRegistry();
        int cityId = cities.create("Benchmark City", 0, 0, 0);
        for (int i = 0; i < ACTIVE_SHIPMENTS; i++) {
            byte kind = (i % 2 == 0) ? ShipmentKind.IMPORT : ShipmentKind.EXPORT;
            shipments.create(kind, (byte) (i % 8), 10, i % 20, cityId, 0, 100); // all arrive at tick 100
        }
        system = new ShipmentSystem();
    }

    @Benchmark
    public void tickWithNoArrivalsYet() {
        system.tick(50, shipments, cities); // well before any ETA — pure scan cost
    }

    @Benchmark
    public void tickSettlingAllArrivals() {
        system.tick(100, shipments, cities); // every shipment's ETA — full settlement cost
    }
}
