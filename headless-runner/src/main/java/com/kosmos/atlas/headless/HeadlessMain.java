package com.kosmos.atlas.headless;

import com.kosmos.atlas.sim.Difficulty;
import com.kosmos.atlas.sim.Metrics;
import com.kosmos.atlas.sim.WorldManager;
import com.kosmos.atlas.sim.commands.city.BuildPowerPlantCommand;
import com.kosmos.atlas.sim.commands.city.BuildRoadCommand;
import com.kosmos.atlas.sim.commands.city.BuildWaterTowerCommand;
import com.kosmos.atlas.sim.commands.city.FoundCityCommand;
import com.kosmos.atlas.sim.commands.city.ZoneCommand;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkManager;
import com.kosmos.atlas.sim.world.HardwareProfile;
import com.kosmos.atlas.sim.world.WorldConstants;
import com.kosmos.atlas.sim.world.gen.WorldGenSettings;

/**
 * First-class headless simulation/benchmark executable (spec §48). Runs the exact same
 * {@link WorldManager} the game client drives, with zero graphics/audio/libGDX on the classpath —
 * proving {@code simulation-core} really is independent of rendering (spec §35, §54).
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :headless-runner:run --args="--seed 819234 --size medium --difficulty hard --bench chunkgen --chunks 2000"
 * </pre>
 */
public final class HeadlessMain {

    public static void main(String[] args) {
        Args parsed = Args.parse(args);
        System.out.println("Atlas City headless runner");
        System.out.println("  seed=" + parsed.seed + " size=" + parsed.worldSizeTiles
            + " profile=" + parsed.profile + " difficulty=" + parsed.difficulty + " bench=" + parsed.bench);

        WorldGenSettings genSettings = WorldGenSettings.balanced(parsed.seed, parsed.worldSizeTiles);
        try (WorldManager world = new WorldManager(genSettings, parsed.profile, parsed.difficulty)) {
            switch (parsed.bench) {
                case "chunkgen" -> runChunkGenBenchmark(world, parsed.chunkCount);
                case "city", "growth" -> runCityGrowthScenario(world, parsed.years);
                default -> runExploreSmoke(world);
            }
        }
    }

    private static void runChunkGenBenchmark(WorldManager world, int chunkCount) {
        ChunkManager chunkManager = world.chunkManager();
        Metrics metrics = world.metrics();

        long startNanos = System.nanoTime();
        long heapBefore = Metrics.usedHeapBytes();

        // Sweep the camera focus outward in a spiral-ish pattern so the streaming system has to
        // continuously request, generate and evict chunks — this is the real workload, not a
        // synthetic direct call into the generator.
        int side = (int) Math.ceil(Math.sqrt(chunkCount));
        int requested = 0;
        outer:
        for (int ring = 0; ring < side; ring++) {
            for (int x = -ring; x <= ring; x++) {
                world.updateCameraFocus(x * WorldConstants.CHUNK_SIZE, ring * WorldConstants.CHUNK_SIZE);
                drainForTicks(world, 4);
                requested++;
                if (requested >= chunkCount) {
                    break outer;
                }
            }
        }
        // Let in-flight generation drain.
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (chunkManager.generatedTotal() < chunkCount && System.nanoTime() < deadline) {
            drainForTicks(world, 1);
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        long heapAfter = Metrics.usedHeapBytes();

        System.out.printf("chunks generated:      %d%n", chunkManager.generatedTotal());
        System.out.printf("elapsed:                %.2f s%n", elapsedNanos / 1e9);
        System.out.printf("chunks/sec:             %.1f%n", chunkManager.generatedTotal() / (elapsedNanos / 1e9));
        System.out.printf("chunk gen p50/p95/p99:  %.3f / %.3f / %.3f ms%n",
            metrics.chunkGenerationDuration.percentileNanos(0.50) / 1e6,
            metrics.chunkGenerationDuration.percentileNanos(0.95) / 1e6,
            metrics.chunkGenerationDuration.percentileNanos(0.99) / 1e6);
        System.out.printf("heap before/after:      %.1f MB / %.1f MB%n", heapBefore / 1e6, heapAfter / 1e6);
        System.out.printf("loaded chunks (final):  %d / capacity %d%n",
            chunkManager.loadedChunkCount(), chunkManager.store().capacity());
    }

    /**
     * Founds a small settlement (road + zones + power + water) and simulates it for
     * {@code years} of in-game time, printing population/jobs/treasury snapshots at the same
     * cadence spec §48's example output does (Year 0 / 10 / 25 / 50 / ...). This is the headless
     * proof that MVP 0.2 (spec §33) actually produces the "population starts at zero and only
     * appears once conditions exist" story end to end, without any client/rendering involved.
     */
    private static void runCityGrowthScenario(WorldManager world, int years) {
        world.updateCameraFocus(16, 16); // chunk (0,0)
        long loadDeadline = System.nanoTime() + 10_000_000_000L;
        while (world.chunkManager().store().get(0, 0) == null && System.nanoTime() < loadDeadline) {
            world.update(1.0 / 30.0);
        }
        Chunk chunk = world.chunkManager().store().get(0, 0);
        if (chunk == null) {
            System.err.println("City scenario: chunk (0,0) never loaded — aborting.");
            return;
        }

        int landStart = findLandRun(chunk.terrainType, 16, 8);
        if (landStart < 0) {
            System.err.println("City scenario: no 8-tile land run found at y=16 for this seed; try a different --seed.");
            return;
        }

        world.submitCommand(new FoundCityCommand(landStart, 16, "Atlas City"));
        world.update(1.0 / 30.0); // drain the found-city command so the city exists before zoning/building
        int cityId = world.cities().nearestCity(landStart, 16);

        for (int x = landStart; x < landStart + 8; x++) {
            world.submitCommand(new BuildRoadCommand(x, 16));
        }
        world.submitCommand(new ZoneCommand(landStart, 15, WorldConstants.ZONE_RESIDENTIAL));
        world.submitCommand(new ZoneCommand(landStart + 1, 15, WorldConstants.ZONE_RESIDENTIAL));
        world.submitCommand(new ZoneCommand(landStart + 2, 17, WorldConstants.ZONE_COMMERCIAL));
        world.submitCommand(new ZoneCommand(landStart + 3, 17, WorldConstants.ZONE_INDUSTRIAL));
        world.submitCommand(new BuildPowerPlantCommand(landStart + 5, 15));
        world.submitCommand(new BuildWaterTowerCommand(landStart + 5, 17));

        System.out.println("Founded settlement at chunk (0,0), road row y=16, x=[" + landStart + "," + (landStart + 7) + "]");
        System.out.println();

        long startNanos = System.nanoTime();
        double secondsPerYear = 8.0; // arbitrary until a calendar system exists
        double frameSeconds = 1.0 / 60.0;
        world.timeManager().setSpeedMultiplier(16);

        int[] reportYears = {0, 5, 10, 25, 50, 100};
        int nextReportIdx = 0;
        double simulatedSeconds = 0;
        double totalRealSeconds = years * secondsPerYear;
        while (simulatedSeconds <= totalRealSeconds) {
            int currentYear = (int) (simulatedSeconds / secondsPerYear);
            if (nextReportIdx < reportYears.length && reportYears[nextReportIdx] <= currentYear) {
                printYearSnapshot(world, cityId, reportYears[nextReportIdx]);
                nextReportIdx++;
            }
            world.update(frameSeconds);
            simulatedSeconds += frameSeconds;
        }
        if (nextReportIdx < reportYears.length) {
            printYearSnapshot(world, cityId, years);
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        System.out.println();
        System.out.printf("Simulation time: %.2f s wall time (ticks=%d)%n", elapsedNanos / 1e9, world.scheduler().currentTick());
        System.out.printf("Peak heap:       %.1f MB%n", Metrics.usedHeapBytes() / 1e6);
        System.out.printf("Commands accepted/rejected: %d / %d%n",
            world.metrics().commandsAccepted(), world.metrics().commandsRejected());
    }

    private static void printYearSnapshot(WorldManager world, int cityId, int year) {
        System.out.printf("Year %-4d Population: %-6d Commercial jobs: %-6d Industrial jobs: %-6d Treasury: %.1f%n",
            year,
            world.populationSystem().totalResidentialPopulation(cityId),
            world.populationSystem().totalCommercialJobs(cityId),
            world.populationSystem().totalIndustrialJobs(cityId),
            world.cities().finance(cityId).treasuryBalance());
    }

    private static int findLandRun(byte[] terrainType, int y, int runLength) {
        int run = 0;
        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            byte t = terrainType[Chunk.tileIndex(x, y)];
            boolean land = t != WorldConstants.TERRAIN_DEEP_WATER && t != WorldConstants.TERRAIN_SHALLOW_WATER;
            run = land ? run + 1 : 0;
            if (run >= runLength) {
                return x - runLength + 1;
            }
        }
        return -1;
    }

    private static void runExploreSmoke(WorldManager world) {
        world.updateCameraFocus(0, 0);
        for (int i = 0; i < 300; i++) {
            world.update(1.0 / 60.0);
        }
        System.out.println("Smoke run OK. Chunks generated: " + world.chunkManager().generatedTotal());
    }

    private static void drainForTicks(WorldManager world, int frames) {
        for (int i = 0; i < frames; i++) {
            world.update(1.0 / 30.0);
        }
    }

    private static final class Args {
        long seed = 819234L;
        int worldSizeTiles = 2048;
        HardwareProfile profile = HardwareProfile.MEDIUM;
        Difficulty difficulty = Difficulty.MEDIUM;
        String bench = "smoke";
        int chunkCount = 500;
        int years = 50;

        static Args parse(String[] argv) {
            Args a = new Args();
            for (int i = 0; i < argv.length; i++) {
                String arg = argv[i];
                String value = (i + 1 < argv.length) ? argv[i + 1] : null;
                switch (arg) {
                    case "--seed" -> { a.seed = Long.parseLong(value); i++; }
                    case "--size" -> { a.worldSizeTiles = parseSize(value); i++; }
                    case "--profile" -> { a.profile = HardwareProfile.valueOf(value.toUpperCase()); i++; }
                    case "--difficulty" -> { a.difficulty = Difficulty.valueOf(value.toUpperCase()); i++; }
                    case "--bench" -> { a.bench = value; i++; }
                    case "--chunks" -> { a.chunkCount = Integer.parseInt(value); i++; }
                    case "--years" -> { a.years = Integer.parseInt(value); i++; }
                    default -> System.err.println("Ignoring unknown argument: " + arg);
                }
            }
            return a;
        }

        static int parseSize(String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return switch (value.toLowerCase()) {
                    case "small" -> 1024;
                    case "medium" -> 2048;
                    case "large" -> 4096;
                    case "experimental" -> 8192;
                    default -> throw new IllegalArgumentException("Unknown world size: " + value);
                };
            }
        }
    }
}
