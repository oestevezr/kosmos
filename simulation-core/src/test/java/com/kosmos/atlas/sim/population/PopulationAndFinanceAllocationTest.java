package com.kosmos.atlas.sim.population;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.economy.GovernmentFinanceSystem;
import com.kosmos.atlas.sim.world.ChunkStore;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the fix that replaced {@code forEachActive(id -> total[0] += ...)} single-element-array
 * boxing in {@link PopulationSystem#recomputeCityTotals} and {@link GovernmentFinanceSystem#tick}
 * with plain indexed loops over {@link BuildingRegistry#highWaterMark()}/{@code isActive}. Both
 * run on a recurring scheduler cadence (spec §41), so per-call allocation there is exactly the
 * kind of steady-state cost spec §42.4 asks hot loops to avoid — this test exists so a future
 * change can't silently reintroduce the box-per-tick pattern.
 *
 * <p>The budget is generous enough to tolerate the couple of small capturing-lambda instances
 * {@code forEachActive}/{@code store.forEach} still allocate elsewhere in these systems (a
 * documented, much smaller residual cost — see the Fase 2 validation notes), while still catching
 * a regression back to per-building or per-array boxing.
 */
class PopulationAndFinanceAllocationTest {

    private static final long MAX_BYTES_PER_CALL = 4096;

    @Test
    void populationSystemTickIsCheapInSteadyState() {
        CityRegistry cities = oneCity();
        BuildingRegistry buildings = seedBuildings(cities);
        ChunkStore emptyStore = new ChunkStore(1); // no loaded chunks: isolates the totals/growth accounting cost
        PopulationSystem system = new PopulationSystem();

        measureAndAssert("PopulationSystem.tick", () -> system.tick(emptyStore, buildings, cities));
    }

    @Test
    void governmentFinanceSystemTickIsCheapInSteadyState() {
        CityRegistry cities = oneCity();
        BuildingRegistry buildings = seedBuildings(cities);
        GovernmentFinanceSystem system = new GovernmentFinanceSystem();

        measureAndAssert("GovernmentFinanceSystem.tick", () -> system.tick(buildings, cities));
    }

    private static CityRegistry oneCity() {
        CityRegistry cities = new CityRegistry();
        cities.create("Testville", 0, 0, 0);
        return cities;
    }

    private static BuildingRegistry seedBuildings(CityRegistry cities) {
        BuildingRegistry buildings = new BuildingRegistry();
        for (int i = 0; i < 200; i++) {
            int id = buildings.create(BuildingType.RESIDENTIAL, i, 0, 1);
            buildings.setPopulation(id, 30);
        }
        for (int i = 0; i < 100; i++) {
            int id = buildings.create(BuildingType.COMMERCIAL, i, 1, 1);
            buildings.setJobs(id, 15);
        }
        return buildings;
    }

    private static void measureAndAssert(String label, Runnable call) {
        com.sun.management.ThreadMXBean bean =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);

        for (int i = 0; i < 2000; i++) {
            call.run(); // warm-up
        }

        long threadId = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(threadId);
        int measuredCalls = 5000;
        for (int i = 0; i < measuredCalls; i++) {
            call.run();
        }
        long after = bean.getThreadAllocatedBytes(threadId);

        long bytesPerCall = (after - before) / measuredCalls;
        assertTrue(bytesPerCall <= MAX_BYTES_PER_CALL,
            label + " allocated " + bytesPerCall + " bytes/call, budget is " + MAX_BYTES_PER_CALL);
    }
}
