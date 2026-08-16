package com.kosmos.atlas.sim.population;

import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import com.kosmos.atlas.sim.world.WorldTileAccess;

/**
 * Grows population and jobs at building granularity from the conditions the player has created —
 * never by scripted placement (spec §9 "population only appears after the minimum conditions for
 * habitation exist, such as access, shelter, water and basic employment"; spec §23 "city
 * attractiveness can depend on jobs, housing, services, transport").
 *
 * <p>Two things happen every time this runs, at the cadence {@code WorldManager} registers it:
 * <ol>
 *   <li><b>Settlement</b>: an empty zoned tile with road access, power and water spawns a new
 *       building (spec §52's "habitation becomes viable -> first population arrives").</li>
 *   <li><b>Growth</b>: existing serviced buildings grow toward a capacity, throttled by a simple
 *       jobs<->housing balance — residential growth needs jobs to exist citywide, commercial/
 *       industrial job growth needs residents to fill them. This is the feedback loop spec §23
 *       describes, kept deliberately simple per spec §20 ("MVP economy should be understandable
 *       rather than hyper-realistic").</li>
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

    private long totalResidentialPopulation;
    private long totalCommercialJobs;
    private long totalIndustrialJobs;

    public long totalResidentialPopulation() {
        return totalResidentialPopulation;
    }

    public long totalCommercialJobs() {
        return totalCommercialJobs;
    }

    public long totalIndustrialJobs() {
        return totalIndustrialJobs;
    }

    public void tick(ChunkStore store, BuildingRegistry buildings) {
        recomputeCityTotals(buildings);
        growExistingBuildings(store, buildings);
        settleEmptyZonedTiles(store, buildings);
        recomputeCityTotals(buildings); // reflect any spawns from this same tick in the public totals
    }

    private void recomputeCityTotals(BuildingRegistry buildings) {
        // A plain indexed loop instead of forEachActive(id -> ...) here: accumulating into local
        // totals from inside a lambda would otherwise force the classic single-element-array
        // boxing trick (three `long[] x = {0}` per call) just to work around Java's "effectively
        // final" capture rule. This runs every population tick (spec §41 cadence), so it's worth
        // keeping allocation-free the same way the sim's other hot loops are (spec §42.4).
        long residential = 0;
        long commercial = 0;
        long industrial = 0;
        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id)) {
                continue;
            }
            switch (buildings.type(id)) {
                case BuildingType.RESIDENTIAL -> residential += buildings.population(id);
                case BuildingType.COMMERCIAL -> commercial += buildings.jobs(id);
                case BuildingType.INDUSTRIAL -> industrial += buildings.jobs(id);
                default -> { /* power plants / water towers don't contribute population or jobs */ }
            }
        }
        totalResidentialPopulation = residential;
        totalCommercialJobs = commercial;
        totalIndustrialJobs = industrial;
    }

    private void growExistingBuildings(ChunkStore store, BuildingRegistry buildings) {
        long totalJobs = totalCommercialJobs + totalIndustrialJobs;
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

            if (type == BuildingType.RESIDENTIAL) {
                growResidential(buildings, id, totalJobs);
            } else {
                growWorkplace(buildings, id);
            }
        });
    }

    private void growResidential(BuildingRegistry buildings, int id, long totalJobs) {
        int current = buildings.population(id);
        if (current >= RESIDENTIAL_CAPACITY) {
            return;
        }
        double demandFactor = clamp01((double) totalJobs / Math.max(1, totalResidentialPopulation + 1));
        int growth = (int) Math.round(GROWTH_STEP * demandFactor);
        if (growth <= 0) {
            return;
        }
        buildings.setPopulation(id, Math.min(RESIDENTIAL_CAPACITY, current + growth));
    }

    private void growWorkplace(BuildingRegistry buildings, int id) {
        int current = buildings.jobs(id);
        if (current >= JOB_CAPACITY) {
            return;
        }
        long totalJobs = totalCommercialJobs + totalIndustrialJobs;
        double workforceFactor = clamp01((double) totalResidentialPopulation / Math.max(1, totalJobs + 1));
        int growth = (int) Math.round(GROWTH_STEP * workforceFactor);
        if (growth <= 0) {
            return;
        }
        buildings.setJobs(id, Math.min(JOB_CAPACITY, current + growth));
    }

    private void settleEmptyZonedTiles(ChunkStore store, BuildingRegistry buildings) {
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
                    int id = buildings.create(zoneToBuildingType(chunk.zoneType[idx]), baseX + lx, baseY + ly);
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
