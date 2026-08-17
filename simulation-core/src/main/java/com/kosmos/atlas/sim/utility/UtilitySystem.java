package com.kosmos.atlas.sim.utility;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.economy.BuildingEconomics;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.util.LongIntHashMap;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import com.kosmos.atlas.sim.world.WorldTileAccess;

import java.util.Arrays;

/**
 * Computes electricity and water coverage as a graph-reachability problem (spec §24: "Utility
 * networks should use graph-based calculations where possible"): a multi-source breadth-first
 * flood fill from every active source of a given tier, through orthogonally-connected land tiles,
 * capped at that tier's own {@link BuildingEconomics#coverageRadiusTiles} so a small plant doesn't
 * light up an entire continent and a Nuclear plant reaches further than a small one (spec's tiered
 * service system — {@code docs/roadmap.md}'s "Servicios cívicos por tiers").
 *
 * <p>Reachability is still binary per tile (in range of some source of the category, or not) —
 * but a tile being reachable doesn't mean it's actually served if the city outgrew its installed
 * capacity. {@link #powerCoverageRatio}/{@link #waterCoverageRatio} express that: the ratio of
 * total {@link BuildingEconomics#capacity} across every active source of a category, owned by a
 * city, to that city's demand (population + jobs — same totals {@code GovernmentFinanceSystem}
 * already scans for independently). {@code PopulationSystem} multiplies growth by these ratios —
 * a city with only a small plant sees growth throttle once demand outpaces that plant's capacity,
 * even for buildings well within its flood-fill radius. This is the "population served vs.
 * generated" capacity model the tiered-service system asked for, kept to a single extra per-city
 * scan rather than a full load-flow simulation (spec §20: understandable, not hyper-realistic).
 *
 * <p>Unlike {@link com.kosmos.atlas.sim.transport.RoadNetwork}, this cannot be made incremental
 * per-chunk with a simple version check — adding one new power plant can relight tiles anywhere
 * within range, in chunks whose own content never changed. Fase 2 accepts a full recompute of the
 * loaded area at a low cadence (see {@code WorldManager}'s scheduler registration) rather than
 * building a full incremental dependency graph; this is a documented candidate for optimization
 * once profiling on a real city shows it matters (spec §49: "based on profiles, not intuition").
 *
 * <p>Like {@link com.kosmos.atlas.sim.transport.RoadNetwork}, coverage flags are derived state:
 * recomputing them never marks a chunk dirty or bumps its render version.
 *
 * <p>The BFS frontier and visited-set are {@link LongQueue} and {@link LongIntHashMap} — the same
 * boxing-free primitive tools {@code ChunkStore} already uses for its own key index — reused as
 * instance fields across calls rather than a fresh {@code ArrayDeque<Long>}/{@code HashMap<Long,
 * Integer>} every recompute (spec §42.4). They only grow (never shrink), so allocation happens a
 * handful of times while the city's coverage area is still small and then never again.
 */
public final class UtilitySystem {

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};
    private static final int NOT_VISITED = Integer.MIN_VALUE;

    /** Every BuildingType that contributes to electricity coverage/capacity, tier 1 to tier 3. */
    private static final byte[] POWER_SOURCE_TYPES =
        {BuildingType.POWER_PLANT, BuildingType.POWER_PLANT_HYDRO, BuildingType.POWER_PLANT_NUCLEAR};
    /** Every BuildingType that contributes to water coverage/capacity, tier 1 to tier 3. */
    private static final byte[] WATER_SOURCE_TYPES =
        {BuildingType.WATER_TOWER, BuildingType.WATER_TREATMENT_PLANT, BuildingType.DESALINATION_PLANT};

    // Fase 2 prosperity/luxury coverage sources — bit-only, no capacity/demand ratio (see class
    // javadoc). Paired with their service bit in COVERAGE_ONLY_CATEGORIES below.
    private static final byte[] HEALTHCARE_SOURCE_TYPES = {BuildingType.CLINIC, BuildingType.HOSPITAL};
    private static final byte[] FIRE_SOURCE_TYPES = {BuildingType.VOLUNTEER_FIRE_BRIGADE, BuildingType.FIRE_STATION};
    private static final byte[] SANITATION_SOURCE_TYPES = {BuildingType.WASTE_COLLECTION, BuildingType.INCINERATOR};
    private static final byte[] CEMETERY_SOURCE_TYPES = {BuildingType.CEMETERY};
    private static final byte[] PARK_SOURCE_TYPES = {BuildingType.PARK};
    private static final byte[] MUSEUM_SOURCE_TYPES = {BuildingType.MUSEUM};
    private static final byte[] POLICE_SOURCE_TYPES = {BuildingType.POLICE_OUTPOST, BuildingType.POLICE_STATION};
    private static final byte[] EDUCATION_SOURCE_TYPES = {BuildingType.SCHOOL, BuildingType.UNIVERSITY};
    private static final byte[] RELIGION_SOURCE_TYPES = {BuildingType.CHURCH};
    // Central Bank and City Hall are deliberately absent — not coverage sources, see BuildingType.

    /** Every BuildingType.BuildingEconomics row with a nonzero pollution intensity — polluters
     *  (positive) and reducers (Park, negative). Radius/intensity come from BuildingEconomics, not
     *  a parallel table here, so tuning the numbers never touches this class. */
    private static final byte[] POLLUTION_SOURCE_TYPES = {
        BuildingType.INDUSTRIAL, BuildingType.STEEL_MILL, BuildingType.MINE, BuildingType.QUARRY,
        BuildingType.POWER_PLANT, BuildingType.INCINERATOR, BuildingType.PARK,
    };
    private static final int POLLUTION_CLAMP_MIN = -30000;
    private static final int POLLUTION_CLAMP_MAX = 30000;

    private static final byte[][] COVERAGE_ONLY_SOURCE_TYPES = {
        HEALTHCARE_SOURCE_TYPES, FIRE_SOURCE_TYPES, SANITATION_SOURCE_TYPES,
        CEMETERY_SOURCE_TYPES, PARK_SOURCE_TYPES, MUSEUM_SOURCE_TYPES,
        POLICE_SOURCE_TYPES, EDUCATION_SOURCE_TYPES, RELIGION_SOURCE_TYPES,
    };
    private static final int[] COVERAGE_ONLY_SERVICE_BITS = {
        WorldConstants.SERVICE_HEALTHCARE, WorldConstants.SERVICE_FIRE, WorldConstants.SERVICE_SANITATION,
        WorldConstants.SERVICE_CEMETERY, WorldConstants.SERVICE_PARK, WorldConstants.SERVICE_MUSEUM,
        WorldConstants.SERVICE_POLICE, WorldConstants.SERVICE_EDUCATION, WorldConstants.SERVICE_RELIGION,
    };

    private final LongQueue frontier = new LongQueue(1024);
    private final LongIntHashMap depthOf = new LongIntHashMap(2048, 0.6f);

    // Per-city capacity/demand ratios, indexed by cityId — same growable-array shape as
    // PopulationSystem's totalXByCity fields (a handful of cities, spec §42.3's aggregation tier).
    private double[] powerCoverageRatioByCity = new double[4];
    private double[] waterCoverageRatioByCity = new double[4];

    public void update(ChunkStore store, BuildingRegistry buildings, CityRegistry cities) {
        int coverageOnlyBits = 0;
        for (int bit : COVERAGE_ONLY_SERVICE_BITS) {
            coverageOnlyBits |= bit;
        }
        clearBits(store, WorldConstants.SERVICE_POWERED | WorldConstants.SERVICE_WATERED | coverageOnlyBits);
        clearPollution(store);
        for (byte type : POWER_SOURCE_TYPES) {
            floodFillFromSources(store, buildings, type, WorldConstants.SERVICE_POWERED, BuildingEconomics.coverageRadiusTiles(type), 0);
        }
        for (byte type : WATER_SOURCE_TYPES) {
            floodFillFromSources(store, buildings, type, WorldConstants.SERVICE_WATERED, BuildingEconomics.coverageRadiusTiles(type), 0);
        }
        for (int category = 0; category < COVERAGE_ONLY_SOURCE_TYPES.length; category++) {
            int serviceBit = COVERAGE_ONLY_SERVICE_BITS[category];
            for (byte type : COVERAGE_ONLY_SOURCE_TYPES[category]) {
                floodFillFromSources(store, buildings, type, serviceBit, BuildingEconomics.coverageRadiusTiles(type), 0);
            }
        }
        for (byte type : POLLUTION_SOURCE_TYPES) {
            int intensity = BuildingEconomics.pollutionIntensity(type);
            if (intensity != 0) {
                floodFillFromSources(store, buildings, type, 0, BuildingEconomics.pollutionRadiusTiles(type), intensity);
            }
        }

        ensureCapacity(cities.highWaterMark());
        cities.forEachActive(cityId -> computeCoverageRatios(buildings, cityId));
    }

    /** In range of any active tier of the electricity category — see the class javadoc for why
     *  "in range" alone doesn't mean "actually served" once capacity is undersized. */
    public double powerCoverageRatio(int cityId) {
        return cityId >= 0 && cityId < powerCoverageRatioByCity.length ? powerCoverageRatioByCity[cityId] : 1.0;
    }

    public double waterCoverageRatio(int cityId) {
        return cityId >= 0 && cityId < waterCoverageRatioByCity.length ? waterCoverageRatioByCity[cityId] : 1.0;
    }

    private void ensureCapacity(int cityHighWaterMark) {
        if (cityHighWaterMark <= powerCoverageRatioByCity.length) {
            return;
        }
        int newCapacity = Math.max(cityHighWaterMark, powerCoverageRatioByCity.length * 2);
        powerCoverageRatioByCity = Arrays.copyOf(powerCoverageRatioByCity, newCapacity);
        waterCoverageRatioByCity = Arrays.copyOf(waterCoverageRatioByCity, newCapacity);
    }

    /**
     * One O(buildings) scan per city computing demand (population + jobs, same formula
     * {@code GovernmentFinanceSystem} independently totals for taxes) and installed capacity for
     * both categories at once, rather than three separate scans.
     */
    private void computeCoverageRatios(BuildingRegistry buildings, int cityId) {
        long demand = 0;
        long powerCapacity = 0;
        long waterCapacity = 0;
        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id) || buildings.cityId(id) != cityId) {
                continue;
            }
            byte type = buildings.type(id);
            switch (type) {
                case BuildingType.RESIDENTIAL -> demand += buildings.population(id);
                case BuildingType.COMMERCIAL, BuildingType.INDUSTRIAL -> demand += buildings.jobs(id);
                default -> { /* utility/production buildings don't add demand */ }
            }
            if (isOneOf(type, POWER_SOURCE_TYPES)) {
                powerCapacity += BuildingEconomics.capacity(type);
            }
            if (isOneOf(type, WATER_SOURCE_TYPES)) {
                waterCapacity += BuildingEconomics.capacity(type);
            }
        }
        powerCoverageRatioByCity[cityId] = coverageRatio(powerCapacity, demand);
        waterCoverageRatioByCity[cityId] = coverageRatio(waterCapacity, demand);
    }

    /** No demand yet (a freshly founded city) means nothing to throttle — full ratio either way. */
    private static double coverageRatio(long capacity, long demand) {
        return demand <= 0 ? 1.0 : Math.min(1.0, (double) capacity / demand);
    }

    private static boolean isOneOf(byte type, byte[] set) {
        for (byte candidate : set) {
            if (candidate == type) {
                return true;
            }
        }
        return false;
    }

    private void clearBits(ChunkStore store, int bits) {
        store.forEach(chunk -> {
            int[] flags = chunk.serviceFlags;
            for (int i = 0; i < flags.length; i++) {
                flags[i] = flags[i] & ~bits;
            }
        });
    }

    private void clearPollution(ChunkStore store) {
        store.forEach(chunk -> Arrays.fill(chunk.pollutionLevel, (short) 0));
    }

    /**
     * @param serviceBit OR-ed into {@code serviceFlags} at every reached tile, or {@code 0} to
     *                   skip (pure pollution sources aren't coverage sources).
     * @param pollutionDelta added (saturating) to {@code pollutionLevel} at every reached tile, or
     *                       {@code 0} to skip (every coverage-only category). Negative for Park.
     */
    private void floodFillFromSources(ChunkStore store, BuildingRegistry buildings, byte sourceType,
                                       int serviceBit, int radiusTiles, int pollutionDelta) {
        frontier.clear();
        depthOf.clear();

        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id) || buildings.type(id) != sourceType) {
                continue;
            }
            long key = packTile(buildings.tileX(id), buildings.tileY(id));
            if (depthOf.get(key, NOT_VISITED) == NOT_VISITED) {
                depthOf.put(key, 0);
                frontier.offer(key);
            }
        }

        while (!frontier.isEmpty()) {
            long key = frontier.poll();
            int wx = unpackX(key);
            int wy = unpackY(key);
            int depth = depthOf.get(key, 0);

            Chunk chunk = WorldTileAccess.chunkAt(store, wx, wy);
            if (chunk == null) {
                continue; // fell outside the currently-loaded window
            }
            int idx = WorldTileAccess.localIndexAt(wx, wy);
            if (!isLand(chunk.terrainType[idx])) {
                continue; // utilities don't spread across open water in Fase 2
            }
            if (serviceBit != 0) {
                chunk.serviceFlags[idx] = chunk.serviceFlags[idx] | serviceBit;
            }
            if (pollutionDelta != 0) {
                int saturated = clampPollution(chunk.pollutionLevel[idx] + pollutionDelta);
                chunk.pollutionLevel[idx] = (short) saturated;
            }

            if (depth >= radiusTiles) {
                continue;
            }
            for (int d = 0; d < 4; d++) {
                int nx = wx + DX[d];
                int ny = wy + DY[d];
                long neighborKey = packTile(nx, ny);
                if (depthOf.get(neighborKey, NOT_VISITED) == NOT_VISITED) {
                    depthOf.put(neighborKey, depth + 1);
                    frontier.offer(neighborKey);
                }
            }
        }
    }

    private static int clampPollution(int value) {
        if (value < POLLUTION_CLAMP_MIN) return POLLUTION_CLAMP_MIN;
        if (value > POLLUTION_CLAMP_MAX) return POLLUTION_CLAMP_MAX;
        return value;
    }

    private static boolean isLand(byte terrainType) {
        return terrainType != WorldConstants.TERRAIN_DEEP_WATER && terrainType != WorldConstants.TERRAIN_SHALLOW_WATER;
    }

    private static long packTile(int x, int y) {
        return (((long) x) << 32) | (y & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackY(long key) {
        return (int) key;
    }

    /**
     * Growable {@code long}-valued FIFO ring buffer. Purpose-built instead of reusing
     * {@code ArrayDeque<Long>} so the BFS frontier never boxes a tile key (spec §42.2).
     */
    private static final class LongQueue {
        private long[] buffer;
        private int head;
        private int tail;
        private int size;

        LongQueue(int initialCapacity) {
            buffer = new long[Integer.highestOneBit(Math.max(2, initialCapacity - 1) * 2)];
        }

        void offer(long value) {
            if (size == buffer.length) {
                grow();
            }
            buffer[tail] = value;
            tail = (tail + 1) & (buffer.length - 1);
            size++;
        }

        long poll() {
            long value = buffer[head];
            head = (head + 1) & (buffer.length - 1);
            size--;
            return value;
        }

        boolean isEmpty() {
            return size == 0;
        }

        void clear() {
            head = 0;
            tail = 0;
            size = 0;
        }

        private void grow() {
            long[] newBuffer = new long[buffer.length * 2];
            for (int i = 0; i < size; i++) {
                newBuffer[i] = buffer[(head + i) & (buffer.length - 1)];
            }
            buffer = newBuffer;
            head = 0;
            tail = size;
        }
    }
}
