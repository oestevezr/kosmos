package com.kosmos.atlas.benchmark;

import com.kosmos.atlas.sim.economy.GoodType;
import com.kosmos.atlas.sim.economy.MarketSystem;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.trade.ShipmentRegistry;
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
 * Benchmarks {@link MarketSystem#tick} over a settled representative city (spec §49) with a full
 * production chain (Farm/Mine/Steel Mill) and a Trade Depot — closes the gap
 * {@code docs/roadmap.md}'s MVP 0.3 section called for ("un benchmark JMH desde el primer commit")
 * but that didn't land until MVP 0.4 touched this system again.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5)
@Fork(1)
public class MarketSystemBenchmark {

    private BenchmarkCityFixture city;
    private MarketSystem marketSystem;
    private RegionalGraph graph;
    private ShipmentRegistry shipments;
    private long tick;

    @Setup(Level.Trial)
    public void setup() {
        city = new BenchmarkCityFixture(5, 8);
        city.settle(30);

        int farm = city.buildings.create(BuildingType.FARM, -100, -100, city.cityId, GoodType.FOOD, 10, GoodType.NONE, 0);
        int mine = city.buildings.create(BuildingType.MINE, -99, -100, city.cityId, GoodType.ORE, 8, GoodType.NONE, 0);
        int mill = city.buildings.create(BuildingType.STEEL_MILL, -98, -100, city.cityId, GoodType.STEEL, 6, GoodType.ORE, 8);
        int depot = city.buildings.create(BuildingType.TRADE_DEPOT, -97, -100, city.cityId, GoodType.NONE, 0, GoodType.NONE, 0);
        assert farm > 0 && mine > 0 && mill > 0 && depot > 0;

        graph = new RegionalGraph();
        graph.addNode(NodeType.EXTERNAL_MARKET, -97, -100);

        shipments = new ShipmentRegistry();
        marketSystem = new MarketSystem();
        tick = 0;
    }

    @Benchmark
    public void tick() {
        marketSystem.tick(city.buildings, city.cities, graph, shipments, null, null, null, tick++);
    }
}
