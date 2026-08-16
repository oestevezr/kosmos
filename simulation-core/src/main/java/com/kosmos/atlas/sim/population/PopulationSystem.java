package com.kosmos.atlas.sim.population;

import com.kosmos.atlas.sim.city.CityRegistry;
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
    private static final int GROWTH_STEP = 4;
    private static final int SATISFACTION_RECOVERY_STEP = 5;
    private static final int SATISFACTION_DECAY_STEP = 8;

    private static final int REQUIRED_SERVICE_MASK =
        WorldConstants.SERVICE_ROAD_ACCESS | WorldConstants.SERVICE_POWERED | WorldConstants.SERVICE_WATERED;

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

    public void tick(ChunkStore store, BuildingRegistry buildings, CityRegistry cities) {
        ensureCapacity(cities.highWaterMark());
        recomputeCityTotals(buildings);
        growExistingBuildings(store, buildings);
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

    private void growExistingBuildings(ChunkStore store, BuildingRegistry buildings) {
        buildings.forEachActive(id -> {
            byte type = buildings.type(id);
            if (type != BuildingType.RESIDENTIAL && type != BuildingType.COMMERCIAL && type != BuildingType.INDUSTRIAL) {
                return;
            }
            boolean serviced = isServiced(store, buildings.tileX(id), buildings.tileY(id));
            if (!serviced) {
                buildings.setSatisfactionPercent(id, buildings.satisfactionPercent(id) - SATISFACTION_DECAY_STEP);
                return;
            }
            buildings.setSatisfactionPercent(id, buildings.satisfactionPercent(id) + SATISFACTION_RECOVERY_STEP);

            int cityId = buildings.cityId(id);
            if (type == BuildingType.RESIDENTIAL) {
                growResidential(buildings, id, cityId);
            } else {
                growWorkplace(buildings, id, cityId);
            }
        });
    }

    private void growResidential(BuildingRegistry buildings, int id, int cityId) {
        int current = buildings.population(id);
        if (current >= RESIDENTIAL_CAPACITY) {
            return;
        }
        long totalJobs = totalCommercialJobs(cityId) + totalIndustrialJobs(cityId);
        double demandFactor = clamp01((double) totalJobs / Math.max(1, totalResidentialPopulation(cityId) + 1));
        int growth = (int) Math.round(GROWTH_STEP * demandFactor);
        if (growth <= 0) {
            return;
        }
        buildings.setPopulation(id, Math.min(RESIDENTIAL_CAPACITY, current + growth));
    }

    private void growWorkplace(BuildingRegistry buildings, int id, int cityId) {
        int current = buildings.jobs(id);
        if (current >= JOB_CAPACITY) {
            return;
        }
        long totalJobs = totalCommercialJobs(cityId) + totalIndustrialJobs(cityId);
        double workforceFactor = clamp01((double) totalResidentialPopulation(cityId) / Math.max(1, totalJobs + 1));
        int growth = (int) Math.round(GROWTH_STEP * workforceFactor);
        if (growth <= 0) {
            return;
        }
        buildings.setJobs(id, Math.min(JOB_CAPACITY, current + growth));
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
                    int flags = chunk.serviceFlags[idx] & 0xFF;
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
                        buildings.setPopulation(id, SEED_POPULATION);
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

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static boolean isServiced(ChunkStore store, int worldTileX, int worldTileY) {
        Chunk chunk = WorldTileAccess.chunkAt(store, worldTileX, worldTileY);
        if (chunk == null) {
            return false; // building's chunk streamed out — treat as unserviced until it's back
        }
        int idx = WorldTileAccess.localIndexAt(worldTileX, worldTileY);
        int flags = chunk.serviceFlags[idx] & 0xFF;
        return (flags & REQUIRED_SERVICE_MASK) == REQUIRED_SERVICE_MASK;
    }
}
