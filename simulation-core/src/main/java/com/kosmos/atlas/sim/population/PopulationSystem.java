package com.kosmos.atlas.sim.population;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.economy.GoodType;
import com.kosmos.atlas.sim.economy.GoodsLedger;
import com.kosmos.atlas.sim.utility.UtilitySystem;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import com.kosmos.atlas.sim.world.WorldTileAccess;

import java.util.Arrays;

/**
 * Grows population and jobs at building granularity from the conditions the player has created —
 * never by scripted placement (spec §9 "population only appears after the minimum conditions for
 * habitation exist, such as access, shelter, water and basic employment"; spec §23 "city
 * attractiveness can depend on jobs, housing, services, transport").
 *
 * <p>Two things happen every time this runs, at the cadence {@code WorldManager} registers it:
 * <ol>
 *   <li><b>Settlement</b>: an empty zoned tile with road access, power and water spawns a new
 *       building, owned by the nearest founded {@code City} (spec §52's "habitation becomes
 *       viable -> first population arrives"; spec §9 on multiple player-founded cities). A tile
 *       with no founded city anywhere near it simply never settles.</li>
 *   <li><b>Growth</b>: existing serviced buildings grow toward a capacity, throttled by a simple
 *       jobs&lt;-&gt;housing balance scoped to their <em>own</em> city — a booming city's job
 *       market doesn't pull residents into a different city's houses. This is the feedback loop
 *       spec §23 describes, kept deliberately simple per spec §20 ("MVP economy should be
 *       understandable rather than hyper-realistic").</li>
 * </ol>
 * Unserviced buildings neither grow nor spawn; their satisfaction decays instead, giving the
 * player a visible signal (spec §23 lists satisfaction/services as growth inputs) without yet
 * implementing outright emigration (a documented follow-up once districts/city-level aggregates
 * exist, per spec §42.3's aggregation hierarchy).
 */
public final class PopulationSystem {

    public static final int RESIDENTIAL_CAPACITY = 60;
    public static final int JOB_CAPACITY = 40;
    private static final int SEED_POPULATION = 6;
    private static final int SEED_JOBS = 6;

    // Migration (spec §29 "migration pressure", §16's Passenger Model precursor — MVP 0.6's first
    // slice, docs/roadmap.md): a newly-settling residential tile's seed population scales with how
    // attractive its city currently is, instead of always seeding a flat SEED_POPULATION. Two
    // independent signals, deliberately not an off-map "external city" entity (spec §29: "should be
    // simulated statistically rather than represented as physical off-map cities") —
    // jobSurplusRatio (local: "Industry affects jobs, jobs affect migration") and tradeActivityRatio
    // (external: the city's own MarketSystem export volume, standing in for "the outside world
    // wants what this city produces"). Only residential seeding scales — commercial/industrial jobs
    // keep the flat SEED_JOBS, since migrants are residents, not employers.
    private static final double MIGRATION_BASE_MULTIPLIER = 0.5;
    private static final double MIGRATION_JOB_SURPLUS_WEIGHT = 1.0;
    private static final double MIGRATION_TRADE_ACTIVITY_WEIGHT = 1.0;
    private static final double MIGRATION_TRADE_REFERENCE_VOLUME = 50.0;
    private static final int GROWTH_STEP = 4;
    private static final int SATISFACTION_RECOVERY_STEP = 5;
    private static final int SATISFACTION_DECAY_STEP = 8;

    private static final int REQUIRED_SERVICE_MASK =
        WorldConstants.SERVICE_ROAD_ACCESS | WorldConstants.SERVICE_POWERED | WorldConstants.SERVICE_WATERED;

    // Satisfaction ceilings by service tier (Fase 2's "prosperity/luxury" civic services — spec
    // §23's attractiveness factors, finally consumed instead of just displayed). A tile always
    // meets REQUIRED_SERVICE_MASK to get here (see growExistingBuildings's early return), so 60 is
    // the floor once the essentials exist, not the floor overall.
    private static final int PROSPERITY_MASK = WorldConstants.SERVICE_HEALTHCARE | WorldConstants.SERVICE_FIRE
        | WorldConstants.SERVICE_SANITATION | WorldConstants.SERVICE_CEMETERY
        | WorldConstants.SERVICE_POLICE | WorldConstants.SERVICE_EDUCATION | WorldConstants.SERVICE_RELIGION
        | WorldConstants.SERVICE_TRANSIT;
    private static final int LUXURY_MASK = WorldConstants.SERVICE_PARK | WorldConstants.SERVICE_MUSEUM;
    private static final int SATISFACTION_CEILING_BASE = 60;
    private static final int SATISFACTION_CEILING_PROSPERITY = 85;
    private static final int SATISFACTION_CEILING_LUXURY = 100;

    // Pollution/noise mechanic: accumulated intensity (UtilitySystem.pollutionLevel, 0-100 once
    // clamped here) subtracts 1:1 from whatever ceiling coverage already granted. Floored, not
    // zeroed, so a polluted zone stagnates instead of hard-bricking. Industrial buildings are
    // immune to their own pollution — without that, every industrial zone would throttle itself to
    // the floor the moment it appeared, which would break industrial growth entirely.
    private static final int SATISFACTION_CEILING_FLOOR = 10;

    // Per-city totals, indexed by cityId — grown lazily to match CityRegistry.highWaterMark().
    // A handful of cities (spec §42.3's CITY aggregation tier), so plain arrays sized to city
    // count are trivial; this is not the per-building/per-tile hot path §42.4 warns about.
    private long[] totalResidentialPopulationByCity = new long[4];
    private long[] totalCommercialJobsByCity = new long[4];
    private long[] totalIndustrialJobsByCity = new long[4];

    public long totalResidentialPopulation(int cityId) {
        return cityId < totalResidentialPopulationByCity.length ? totalResidentialPopulationByCity[cityId] : 0;
    }

    public long totalCommercialJobs(int cityId) {
        return cityId < totalCommercialJobsByCity.length ? totalCommercialJobsByCity[cityId] : 0;
    }

    public long totalIndustrialJobs(int cityId) {
        return cityId < totalIndustrialJobsByCity.length ? totalIndustrialJobsByCity[cityId] : 0;
    }

    public void tick(ChunkStore store, BuildingRegistry buildings, CityRegistry cities, UtilitySystem utility) {
        ensureCapacity(cities.highWaterMark());
        recomputeCityTotals(buildings);
        growExistingBuildings(store, buildings, cities, utility);
        settleEmptyZonedTiles(store, buildings, cities);
        recomputeCityTotals(buildings); // reflect any spawns from this same tick in the public totals
    }

    private void ensureCapacity(int cityHighWaterMark) {
        if (cityHighWaterMark <= totalResidentialPopulationByCity.length) {
            return;
        }
        int newCapacity = Math.max(cityHighWaterMark, totalResidentialPopulationByCity.length * 2);
        totalResidentialPopulationByCity = Arrays.copyOf(totalResidentialPopulationByCity, newCapacity);
        totalCommercialJobsByCity = Arrays.copyOf(totalCommercialJobsByCity, newCapacity);
        totalIndustrialJobsByCity = Arrays.copyOf(totalIndustrialJobsByCity, newCapacity);
    }

    private void recomputeCityTotals(BuildingRegistry buildings) {
        // A plain indexed loop instead of forEachActive(id -> ...) here: accumulating into local
        // totals from inside a lambda would otherwise force the classic single-element-array
        // boxing trick just to work around Java's "effectively final" capture rule. This runs
        // every population tick (spec §41 cadence), so it's worth keeping allocation-free the
        // same way the sim's other hot loops are (spec §42.4).
        Arrays.fill(totalResidentialPopulationByCity, 0);
        Arrays.fill(totalCommercialJobsByCity, 0);
        Arrays.fill(totalIndustrialJobsByCity, 0);
        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id)) {
                continue;
            }
            int cityId = buildings.cityId(id);
            switch (buildings.type(id)) {
                case BuildingType.RESIDENTIAL -> totalResidentialPopulationByCity[cityId] += buildings.population(id);
                case BuildingType.COMMERCIAL -> totalCommercialJobsByCity[cityId] += buildings.jobs(id);
                case BuildingType.INDUSTRIAL -> totalIndustrialJobsByCity[cityId] += buildings.jobs(id);
                default -> { /* power plants / water towers don't contribute population or jobs */ }
            }
        }
    }

    private void growExistingBuildings(ChunkStore store, BuildingRegistry buildings, CityRegistry cities, UtilitySystem utility) {
        buildings.forEachActive(id -> {
            byte type = buildings.type(id);
            if (type != BuildingType.RESIDENTIAL && type != BuildingType.COMMERCIAL && type != BuildingType.INDUSTRIAL) {
                return;
            }
            int flags = serviceFlagsAt(store, buildings.tileX(id), buildings.tileY(id));
            if ((flags & REQUIRED_SERVICE_MASK) != REQUIRED_SERVICE_MASK) {
                buildings.setSatisfactionPercent(id, buildings.satisfactionPercent(id) - SATISFACTION_DECAY_STEP);
                return;
            }

            // Move satisfaction toward the ceiling its prosperity/luxury coverage allows, rather
            // than a flat +/- step — this is the same code whether the ceiling just rose (built a
            // hospital) or fell (demolished a park), no special case needed either way.
            int ceiling = satisfactionCeiling(flags);
            if (type != BuildingType.INDUSTRIAL) {
                int pollution = clamp(pollutionAt(store, buildings.tileX(id), buildings.tileY(id)), 0, 100);
                ceiling = Math.max(SATISFACTION_CEILING_FLOOR, ceiling - pollution);
            }
            int currentSatisfaction = buildings.satisfactionPercent(id);
            if (currentSatisfaction < ceiling) {
                buildings.setSatisfactionPercent(id, Math.min(ceiling, currentSatisfaction + SATISFACTION_RECOVERY_STEP));
            } else if (currentSatisfaction > ceiling) {
                buildings.setSatisfactionPercent(id, Math.max(ceiling, currentSatisfaction - SATISFACTION_DECAY_STEP));
            }

            updateDensityLevel(buildings, id, type);

            int cityId = buildings.cityId(id);
            // Being in flood-fill range isn't enough — the city's installed capacity must also
            // cover its demand (spec's tiered-service capacity model, see UtilitySystem's javadoc),
            // and satisfaction (just updated above) throttles growth the same way — a city with no
            // prosperity services stays capped near its base ceiling's multiplier forever.
            double growthRateMultiplier = cities.difficulty().growthRateMultiplier
                * utility.powerCoverageRatio(cityId) * utility.waterCoverageRatio(cityId)
                * (buildings.satisfactionPercent(id) / 100.0);
            if (type == BuildingType.RESIDENTIAL) {
                growResidential(buildings, id, cityId, growthRateMultiplier);
            } else {
                growWorkplace(buildings, id, cityId, growthRateMultiplier);
            }
        });
    }

    private static int satisfactionCeiling(int serviceFlags) {
        if ((serviceFlags & LUXURY_MASK) != 0) {
            return SATISFACTION_CEILING_LUXURY;
        }
        if ((serviceFlags & PROSPERITY_MASK) != 0) {
            return SATISFACTION_CEILING_PROSPERITY;
        }
        return SATISFACTION_CEILING_BASE;
    }

    private void growResidential(BuildingRegistry buildings, int id, int cityId, double growthRateMultiplier) {
        int capacity = BuildingDensity.residentialCapacity(buildings.densityLevel(id));
        int current = buildings.population(id);
        if (current >= capacity) {
            return;
        }
        long totalJobs = totalCommercialJobs(cityId) + totalIndustrialJobs(cityId);
        double demandFactor = clamp01((double) totalJobs / Math.max(1, totalResidentialPopulation(cityId) + 1));
        int growth = (int) Math.round(GROWTH_STEP * demandFactor * growthRateMultiplier);
        if (growth <= 0) {
            return;
        }
        buildings.setPopulation(id, Math.min(capacity, current + growth));
    }

    private void growWorkplace(BuildingRegistry buildings, int id, int cityId, double growthRateMultiplier) {
        int capacity = BuildingDensity.jobCapacity(buildings.densityLevel(id));
        int current = buildings.jobs(id);
        if (current >= capacity) {
            return;
        }
        long totalJobs = totalCommercialJobs(cityId) + totalIndustrialJobs(cityId);
        double workforceFactor = clamp01((double) totalResidentialPopulation(cityId) / Math.max(1, totalJobs + 1));
        int growth = (int) Math.round(GROWTH_STEP * workforceFactor * growthRateMultiplier);
        if (growth <= 0) {
            return;
        }
        buildings.setJobs(id, Math.min(capacity, current + growth));
    }

    /**
     * Promotes a full, well-serviced building one density level, or demotes an under-serviced one
     * — see {@link BuildingDensity}'s javadoc for the hysteresis rationale. At most one level per
     * tick either way; clamps population/jobs down to the new (smaller) capacity on demotion so a
     * shrunk building never reports more occupants than it can hold.
     */
    private void updateDensityLevel(BuildingRegistry buildings, int id, byte type) {
        int level = buildings.densityLevel(id);
        int satisfaction = buildings.satisfactionPercent(id);
        boolean isResidential = type == BuildingType.RESIDENTIAL;
        int occupancy = isResidential ? buildings.population(id) : buildings.jobs(id);
        int capacityAtCurrentLevel = isResidential
            ? BuildingDensity.residentialCapacity(level) : BuildingDensity.jobCapacity(level);

        if (level < BuildingDensity.MAX_LEVEL
            && occupancy >= capacityAtCurrentLevel
            && satisfaction >= BuildingDensity.promoteSatisfaction(level + 1)) {
            buildings.setDensityLevel(id, level + 1);
        } else if (level > 0 && satisfaction < BuildingDensity.demoteSatisfaction(level)) {
            int newLevel = level - 1;
            buildings.setDensityLevel(id, newLevel);
            int newCapacity = isResidential
                ? BuildingDensity.residentialCapacity(newLevel) : BuildingDensity.jobCapacity(newLevel);
            if (isResidential) {
                buildings.setPopulation(id, Math.min(occupancy, newCapacity));
            } else {
                buildings.setJobs(id, Math.min(occupancy, newCapacity));
            }
        }
    }

    /**
     * How attractive {@code cityId} currently is to new migrants, as a multiplier on
     * {@link #SEED_POPULATION} — see this class's migration fields javadoc for the rationale.
     * Ranges {@code [MIGRATION_BASE_MULTIPLIER, MIGRATION_BASE_MULTIPLIER + JOB_SURPLUS_WEIGHT +
     * TRADE_ACTIVITY_WEIGHT]} = {@code [0.5, 2.5]} with the current weights.
     */
    private double migrationMultiplier(CityRegistry cities, int cityId) {
        long totalJobs = totalCommercialJobs(cityId) + totalIndustrialJobs(cityId);
        double jobSurplusRatio = clamp01(
            (totalJobs - totalResidentialPopulation(cityId)) / (double) Math.max(1, totalJobs));

        GoodsLedger ledger = cities.ledger(cityId);
        int totalExported = 0;
        for (byte good = 0; good < GoodType.COUNT; good++) {
            totalExported += ledger.exportedLastTick(good);
        }
        double tradeActivityRatio = clamp01(totalExported / MIGRATION_TRADE_REFERENCE_VOLUME);

        return MIGRATION_BASE_MULTIPLIER + jobSurplusRatio * MIGRATION_JOB_SURPLUS_WEIGHT
            + tradeActivityRatio * MIGRATION_TRADE_ACTIVITY_WEIGHT;
    }

    private void settleEmptyZonedTiles(ChunkStore store, BuildingRegistry buildings, CityRegistry cities) {
        store.forEach(chunk -> {
            int baseX = chunk.chunkX() * WorldConstants.CHUNK_SIZE;
            int baseY = chunk.chunkY() * WorldConstants.CHUNK_SIZE;
            for (int ly = 0; ly < WorldConstants.CHUNK_SIZE; ly++) {
                for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
                    int idx = Chunk.tileIndex(lx, ly);
                    if (chunk.zoneType[idx] == WorldConstants.ZONE_NONE || chunk.buildingId[idx] != WorldConstants.NO_BUILDING) {
                        continue;
                    }
                    int flags = chunk.serviceFlags[idx];
                    if ((flags & REQUIRED_SERVICE_MASK) != REQUIRED_SERVICE_MASK) {
                        continue;
                    }
                    int worldTileX = baseX + lx;
                    int worldTileY = baseY + ly;
                    int cityId = cities.nearestCity(worldTileX, worldTileY);
                    if (cityId < 0) {
                        continue; // no city founded anywhere yet — nothing to attribute this building to
                    }
                    int id = buildings.create(zoneToBuildingType(chunk.zoneType[idx]), worldTileX, worldTileY, cityId);
                    if (chunk.zoneType[idx] == WorldConstants.ZONE_RESIDENTIAL) {
                        int seed = Math.max(1, (int) Math.round(SEED_POPULATION * migrationMultiplier(cities, cityId)));
                        buildings.setPopulation(id, seed);
                    } else {
                        buildings.setJobs(id, SEED_JOBS);
                    }
                    chunk.buildingId[idx] = id;
                    chunk.markDirty();
                }
            }
        });
    }

    private static byte zoneToBuildingType(byte zoneType) {
        return switch (zoneType) {
            case WorldConstants.ZONE_RESIDENTIAL -> BuildingType.RESIDENTIAL;
            case WorldConstants.ZONE_COMMERCIAL -> BuildingType.COMMERCIAL;
            case WorldConstants.ZONE_INDUSTRIAL -> BuildingType.INDUSTRIAL;
            default -> throw new IllegalArgumentException("Not a buildable zone type: " + zoneType);
        };
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    /** Returns 0 (nothing serviced) if the building's chunk streamed out — treat as unserviced until it's back. */
    private static int serviceFlagsAt(ChunkStore store, int worldTileX, int worldTileY) {
        Chunk chunk = WorldTileAccess.chunkAt(store, worldTileX, worldTileY);
        if (chunk == null) {
            return 0;
        }
        int idx = WorldTileAccess.localIndexAt(worldTileX, worldTileY);
        return chunk.serviceFlags[idx];
    }

    /** Returns 0 (no pollution) if the building's chunk streamed out — same convention as
     *  {@link #serviceFlagsAt}. */
    private static int pollutionAt(ChunkStore store, int worldTileX, int worldTileY) {
        Chunk chunk = WorldTileAccess.chunkAt(store, worldTileX, worldTileY);
        if (chunk == null) {
            return 0;
        }
        int idx = WorldTileAccess.localIndexAt(worldTileX, worldTileY);
        return chunk.pollutionLevel[idx];
    }
}
