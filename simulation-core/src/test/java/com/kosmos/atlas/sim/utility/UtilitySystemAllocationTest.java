package com.kosmos.atlas.sim.utility;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the rewrite of {@link UtilitySystem}'s flood fill from a fresh
 * {@code ArrayDeque<Long>}/{@code HashMap<Long,Integer>} per call to the reused
 * {@code LongQueue}/{@link com.kosmos.atlas.sim.util.LongIntHashMap} instance fields (spec
 * §42.4). Both grow on the first few calls and then stay allocation-free — this test warms up
 * past that growth phase before measuring, matching {@code AllocationBudgetTest}'s methodology.
 */
class UtilitySystemAllocationTest {

    private static final long MAX_BYTES_PER_CALL = 4096;

    @Test
    void updateIsCheapInSteadyState() {
        ChunkStore store = new ChunkStore(9);
        for (int cx = -1; cx <= 1; cx++) {
            for (int cy = -1; cy <= 1; cy++) {
                Chunk chunk = new Chunk();
                chunk.reset(cx, cy);
                java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
                store.put(chunk);
            }
        }
        BuildingRegistry buildings = new BuildingRegistry();
        int plantId = buildings.create(BuildingType.POWER_PLANT, 5, 5, 1);
        store.get(0, 0).buildingId[Chunk.tileIndex(5, 5)] = plantId;
        int towerId = buildings.create(BuildingType.WATER_TOWER, 10, 10, 1);
        store.get(0, 0).buildingId[Chunk.tileIndex(10, 10)] = towerId;

        UtilitySystem system = new UtilitySystem();
        CityRegistry cities = new CityRegistry();
        cities.create("Testville", 5, 5, 0);

        com.sun.management.ThreadMXBean bean =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);

        for (int i = 0; i < 50; i++) {
            system.update(store, buildings, cities); // warm-up: lets frontier/depthOf grow to steady size
        }

        long threadId = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(threadId);
        int measuredCalls = 200;
        for (int i = 0; i < measuredCalls; i++) {
            system.update(store, buildings, cities);
        }
        long after = bean.getThreadAllocatedBytes(threadId);

        long bytesPerCall = (after - before) / measuredCalls;
        assertTrue(bytesPerCall <= MAX_BYTES_PER_CALL,
            "UtilitySystem.update allocated " + bytesPerCall + " bytes/call, budget is " + MAX_BYTES_PER_CALL);
    }
}
