package com.kosmos.atlas.sim.economy;

import com.kosmos.atlas.sim.population.BuildingType;

/**
 * Per-{@link BuildingType} economics — construction cost, recurring maintenance, and (for
 * utility-tier buildings) capacity/coverage-radius/unlock-population. Same shape as
 * {@link GoodsLedger#basePrice}'s default table: static arrays indexed by the type's byte
 * constant, not a {@code Map}, with each row's value justified in a comment rather than derived
 * from a formula (spec §20's "understandable rather than hyper-realistic").
 *
 * <p>"Per accrual" (not "per tick") matches {@link LoanRegistry#interestRatePerAccrual}'s
 * terminology — maintenance is deducted once per {@code GovernmentFinanceSystem.tick} call, which
 * runs at a scheduler cadence slower than the raw simulation tick, exactly like tax collection
 * and loan interest already do.
 *
 * <p>Residential/Commercial/Industrial buildings never go through a construction command (they
 * spawn organically in {@code PopulationSystem.settleEmptyZonedTiles}) — their construction cost
 * lives in {@code ZoneCommand} instead (delimiting the zone is the "infrastructure" cost, not the
 * building appearing later), so their rows here are all zero.
 *
 * <p>{@link #capacity}/{@link #coverageRadiusTiles}/{@link #unlockPopulation} are all zero for
 * non-utility types (production buildings, Trade Depot, Port, R/C/I) — {@code UtilitySystem} only
 * ever reads them for the Power/Water category types.
 */
public final class BuildingEconomics {

    private static final double[] CONSTRUCTION_COST = new double[BuildingType.COUNT];
    private static final double[] MAINTENANCE_PER_ACCRUAL = new double[BuildingType.COUNT];
    private static final double[] REVENUE_PER_ACCRUAL = new double[BuildingType.COUNT];
    private static final int[] CAPACITY = new int[BuildingType.COUNT];
    private static final int[] COVERAGE_RADIUS_TILES = new int[BuildingType.COUNT];
    private static final long[] UNLOCK_POPULATION = new long[BuildingType.COUNT];
    /** Signed — positive for polluters, negative for reducers (Park). Zero for every other type,
     *  including non-polluting building categories and Hydro/Nuclear power (deliberately excluded
     *  from the polluter roster, see docs/roadmap.md). */
    private static final int[] POLLUTION_INTENSITY = new int[BuildingType.COUNT];
    /** Own radius, independent of {@link #COVERAGE_RADIUS_TILES} — e.g. a small Power Plant's
     *  30-tile electricity reach has nothing to do with its smoke radius. */
    private static final int[] POLLUTION_RADIUS_TILES = new int[BuildingType.COUNT];

    static {
        // --- Electricity: small plant (tier 1, always available) -> hydroelectric -> nuclear ---
        set(BuildingType.POWER_PLANT, 800, 8, 400, 30, 0);
        set(BuildingType.POWER_PLANT_HYDRO, 4000, 30, 2000, 48, 500);
        set(BuildingType.POWER_PLANT_NUCLEAR, 12000, 60, 8000, 60, 2000);

        // --- Water: small tower (tier 1) -> treatment plant -> desalination plant ---
        set(BuildingType.WATER_TOWER, 600, 6, 400, 30, 0);
        set(BuildingType.WATER_TREATMENT_PLANT, 3000, 25, 2000, 48, 500);
        set(BuildingType.DESALINATION_PLANT, 9000, 50, 8000, 60, 2000);

        // --- Fase 2: prosperity/luxury civic services (spec §23's attractiveness factors,
        // finally wired up — see PopulationSystem's satisfaction-ceiling logic). No CAPACITY: a
        // hospital doesn't "serve" a population count the way a power plant does — coverage alone
        // feeds the satisfaction ceiling, not a capacity/demand ratio. ---
        setCoverageOnly(BuildingType.CLINIC, 2000, 15, 25, 0);
        setCoverageOnly(BuildingType.HOSPITAL, 6000, 35, 45, 1000);
        setCoverageOnly(BuildingType.VOLUNTEER_FIRE_BRIGADE, 1200, 10, 25, 0);
        setCoverageOnly(BuildingType.FIRE_STATION, 3500, 25, 45, 1000);
        setCoverageOnly(BuildingType.WASTE_COLLECTION, 1000, 8, 25, 0);
        setCoverageOnly(BuildingType.INCINERATOR, 3000, 20, 45, 800);
        setCoverageOnly(BuildingType.CEMETERY, 800, 4, 20, 0);
        setCoverageOnly(BuildingType.PARK, 400, 3, 15, 200);
        // Museum is the one civic building with its own revenue (tourism) — net +6/accrual after
        // its own upkeep, confirmed with the user during Fase 1's scoping questions.
        setCoverageOnly(BuildingType.MUSEUM, 4500, 12, 20, 1000);
        REVENUE_PER_ACCRUAL[BuildingType.MUSEUM] = 18;

        // --- Remaining civic services: Police and Education follow the same 2-tier prosperity
        // pattern as Healthcare/Fire/Sanitation; Church has no tier 2, same as Cemetery. ---
        setCoverageOnly(BuildingType.POLICE_OUTPOST, 1200, 10, 25, 0);
        setCoverageOnly(BuildingType.POLICE_STATION, 3500, 25, 45, 1000);
        setCoverageOnly(BuildingType.SCHOOL, 1800, 15, 25, 0);
        setCoverageOnly(BuildingType.UNIVERSITY, 5500, 30, 45, 1200);
        setCoverageOnly(BuildingType.CHURCH, 600, 5, 20, 0);

        // Central Bank: not a UtilitySystem coverage source (radius 0, never flood-filled) — its
        // only effect is gating RequestCityLoanCommand (see BuildingRegistry.hasActiveBuildingOfType).
        set(BuildingType.CENTRAL_BANK, 8000, 20, 0, 0, 1500);

        // City Hall: never built via BuildCivicBuildingCommand — FoundCityCommand places it for
        // free at the founding tile, so construction cost is 0 (there's no purchase to charge for).
        // Still has upkeep, and is not a coverage source either.
        set(BuildingType.CITY_HALL, 0, 5, 0, 0, 0);

        // Airport (MVP 0.6, first slice): costs more than a Port (bigger infrastructure), no
        // CAPACITY/COVERAGE_RADIUS (not a UtilitySystem coverage source — MarketSystem reads its
        // own AirportRegistry row instead), gated behind a higher population than Central Bank
        // (spec §19's "a small town should not automatically support an international airport").
        set(BuildingType.AIRPORT, 15000, 60, 0, 0, 3000);

        // --- MVP 0.3 production chain + Trade Depot/Port: one-time construction cost only,
        // no capacity/radius/unlock (they aren't UtilitySystem coverage sources). ---
        setCostOnly(BuildingType.FARM, 600);
        setCostOnly(BuildingType.LUMBER_CAMP, 600);
        setCostOnly(BuildingType.MINE, 1000);
        setCostOnly(BuildingType.QUARRY, 700);
        setCostOnly(BuildingType.STEEL_MILL, 2200);
        setCostOnly(BuildingType.TRADE_DEPOT, 3000);
        setCostOnly(BuildingType.PORT, 8000);

        // RESIDENTIAL/COMMERCIAL/INDUSTRIAL: left at zero — their cost lives in ZoneCommand.

        // --- Pollution/noise mechanic: only these polluters and only Park as a reducer (locked
        // in with the user — narrower buildings-only scope, no natural-forest reduction). Small
        // Power Plant pollutes; Hydro/Nuclear deliberately don't. See UtilitySystem/PopulationSystem
        // for how this accumulates and lowers the satisfaction ceiling. ---
        setPollution(BuildingType.INDUSTRIAL, 25, 8);
        setPollution(BuildingType.STEEL_MILL, 40, 12);
        setPollution(BuildingType.MINE, 30, 10);
        setPollution(BuildingType.QUARRY, 20, 8);
        setPollution(BuildingType.POWER_PLANT, 35, 10);
        setPollution(BuildingType.INCINERATOR, 30, 10);
        setPollution(BuildingType.PARK, -30, 12);

        // --- MVP 0.6, second slice: Rail (fourth cargo gateway) + bus-route mechanic. ---
        // Rail Terminal: domestic gateway, no coastal/population gate like Port/Trade Depot (only
        // Airport has spec §19's "small town" restriction). Costs between Port (8000) and Airport
        // (15000) — bigger infrastructure than a dock, smaller than an international airport.
        set(BuildingType.RAIL_TERMINAL, 10000, 45, 0, 0, 0);
        // Bus Depot: CAPACITY here means "max simultaneous routes it can dispatch" (CreateBusRouteCommand
        // reads it), not a UtilitySystem coverage source — radius 0.
        set(BuildingType.BUS_DEPOT, 2000, 15, 3, 0, 0);
        // Bus Stop: cheap coverage-only point, same shape as Fase 2's civic services, except
        // UtilitySystem only actually floods from one that's part of an active route (see its javadoc).
        setCoverageOnly(BuildingType.BUS_STOP, 300, 3, 20, 0);
    }

    public static double constructionCost(byte buildingType) {
        return CONSTRUCTION_COST[buildingType];
    }

    public static double maintenancePerAccrual(byte buildingType) {
        return MAINTENANCE_PER_ACCRUAL[buildingType];
    }

    public static double revenuePerAccrual(byte buildingType) {
        return REVENUE_PER_ACCRUAL[buildingType];
    }

    public static int capacity(byte buildingType) {
        return CAPACITY[buildingType];
    }

    public static int coverageRadiusTiles(byte buildingType) {
        return COVERAGE_RADIUS_TILES[buildingType];
    }

    public static long unlockPopulation(byte buildingType) {
        return UNLOCK_POPULATION[buildingType];
    }

    public static int pollutionIntensity(byte buildingType) {
        return POLLUTION_INTENSITY[buildingType];
    }

    public static int pollutionRadiusTiles(byte buildingType) {
        return POLLUTION_RADIUS_TILES[buildingType];
    }

    private static void set(byte type, double constructionCost, double maintenancePerAccrual,
                             int capacity, int coverageRadiusTiles, long unlockPopulation) {
        CONSTRUCTION_COST[type] = constructionCost;
        MAINTENANCE_PER_ACCRUAL[type] = maintenancePerAccrual;
        CAPACITY[type] = capacity;
        COVERAGE_RADIUS_TILES[type] = coverageRadiusTiles;
        UNLOCK_POPULATION[type] = unlockPopulation;
    }

    /** Coverage-only service (prosperity/luxury civic buildings): no CAPACITY, see the class javadoc. */
    private static void setCoverageOnly(byte type, double constructionCost, double maintenancePerAccrual,
                                         int coverageRadiusTiles, long unlockPopulation) {
        CONSTRUCTION_COST[type] = constructionCost;
        MAINTENANCE_PER_ACCRUAL[type] = maintenancePerAccrual;
        COVERAGE_RADIUS_TILES[type] = coverageRadiusTiles;
        UNLOCK_POPULATION[type] = unlockPopulation;
    }

    private static void setCostOnly(byte type, double constructionCost) {
        CONSTRUCTION_COST[type] = constructionCost;
    }

    /** Pollution/noise source or reducer — additive to whatever other row this type already has. */
    private static void setPollution(byte type, int pollutionIntensity, int pollutionRadiusTiles) {
        POLLUTION_INTENSITY[type] = pollutionIntensity;
        POLLUTION_RADIUS_TILES[type] = pollutionRadiusTiles;
    }

    private BuildingEconomics() {
    }
}
