package com.kosmos.atlas.sim;

import com.kosmos.atlas.sim.commands.city.BuildPowerPlantCommand;
import com.kosmos.atlas.sim.commands.city.BuildRoadCommand;
import com.kosmos.atlas.sim.commands.city.BuildWaterTowerCommand;
import com.kosmos.atlas.sim.commands.city.FoundCityCommand;
import com.kosmos.atlas.sim.commands.city.ZoneCommand;
import com.kosmos.atlas.sim.world.HardwareProfile;
import com.kosmos.atlas.sim.world.WorldConstants;
import com.kosmos.atlas.sim.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test for Fase 2 (spec §33 MVP 0.2) driven entirely through the public
 * {@link WorldManager} API a real client/headless host would use: submit commands, call
 * {@link WorldManager#update(double)} repeatedly, observe population/jobs/tax revenue emerge —
 * without touching any {@code sim.world}/{@code sim.commands} internals directly.
 */
class WorldManagerCityGrowthTest {

    @Test
    void foundingASettlementProducesPopulationJobsAndTaxRevenue() {
        // A flat-ish seed/location is not guaranteed by any specific seed, so instead of hunting
        // for one, this test builds directly on chunk (0,0)'s tiles and simply requires them to
        // be land; if a future generator change makes (0,0) full ocean for this seed, the test
        // should pick a different anchor rather than silently pass on a no-op city.
        WorldGenSettings genSettings = WorldGenSettings.balanced(2024L, 1024);
        try (WorldManager world = new WorldManager(genSettings, HardwareProfile.LOW)) {
            world.updateCameraFocus(16, 16); // focus chunk (0,0)
            waitForChunkLoad(world);

            var chunk = world.chunkManager().store().get(0, 0);
            assertTrue(chunk != null, "chunk (0,0) should have streamed in by now");

            // Find a contiguous run of land tiles along y=16 to build the demo settlement on.
            int landRunStart = findLandRun(chunk.terrainType, 16, 6);
            assertTrue(landRunStart >= 0, "expected at least 6 contiguous land tiles at y=16 for this seed");

            assertTrue(world.submitCommand(new FoundCityCommand(landRunStart, 16, "Testville")));
            world.update(1.0 / 30.0); // drain the found-city command so subsequent commands attribute to it
            int cityId = world.cities().nearestCity(landRunStart, 16);

            for (int x = landRunStart; x < landRunStart + 6; x++) {
                assertTrue(world.submitCommand(new BuildRoadCommand(x, 16)));
            }
            assertTrue(world.submitCommand(new ZoneCommand(landRunStart, 15, WorldConstants.ZONE_RESIDENTIAL)));
            assertTrue(world.submitCommand(new ZoneCommand(landRunStart + 1, 17, WorldConstants.ZONE_COMMERCIAL)));
            assertTrue(world.submitCommand(new BuildPowerPlantCommand(landRunStart + 3, 15)));
            assertTrue(world.submitCommand(new BuildWaterTowerCommand(landRunStart + 3, 17)));

            // Advance enough simulated time for road/utility/population systems to run several times.
            world.timeManager().setSpeedMultiplier(16);
            for (int i = 0; i < 400; i++) {
                world.update(1.0 / 30.0);
            }

            assertTrue(world.populationSystem().totalResidentialPopulation(cityId) > 0,
                "founding a serviced settlement should produce residents within this many ticks");
            assertTrue(world.cities().finance(cityId).treasuryBalance() > 0,
                "a populated, taxed city should have collected some revenue");
            assertTrue(world.metrics().commandsAccepted() >= 10);
        }
    }

    private static void waitForChunkLoad(WorldManager world) {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (world.chunkManager().store().get(0, 0) == null && System.nanoTime() < deadline) {
            world.update(1.0 / 30.0);
        }
    }

    private static int findLandRun(byte[] terrainType, int y, int runLength) {
        int run = 0;
        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            byte t = terrainType[com.kosmos.atlas.sim.world.Chunk.tileIndex(x, y)];
            boolean land = t != WorldConstants.TERRAIN_DEEP_WATER && t != WorldConstants.TERRAIN_SHALLOW_WATER;
            run = land ? run + 1 : 0;
            if (run >= runLength) {
                return x - runLength + 1;
            }
        }
        return -1;
    }
}
