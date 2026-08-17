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
    private static final int[] CAPACITY = new int[BuildingType.COUNT];
    private static final int[] COVERAGE_RADIUS_TILES = new int[BuildingType.COUNT];
    private static final long[] UNLOCK_POPULATION = new long[BuildingType.COUNT];

    static {
        // --- Electricity: small plant (tier 1, always available) -> hydroelectric -> nuclear ---
        set(BuildingType.POWER_PLANT, 800, 8, 400, 30, 0);
        set(BuildingType.POWER_PLANT_HYDRO, 4000, 30, 2000, 48, 500);
        set(BuildingType.POWER_PLANT_NUCLEAR, 12000, 60, 8000, 60, 2000);

        // --- Water: small tower (tier 1) -> treatment plant -> desalination plant ---
        set(BuildingType.WATER_TOWER, 600, 6, 400, 30, 0);
        set(BuildingType.WATER_TREATMENT_PLANT, 3000, 25, 2000, 48, 500);
        set(BuildingType.DESALINATION_PLANT, 9000, 50, 8000, 60, 2000);

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
    }

    public static double constructionCost(byte buildingType) {
        return CONSTRUCTION_COST[buildingType];
    }

    public static double maintenancePerAccrual(byte buildingType) {
        return MAINTENANCE_PER_ACCRUAL[buildingType];
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

    private static void set(byte type, double constructionCost, double maintenancePerAccrual,
                             int capacity, int coverageRadiusTiles, long unlockPopulation) {
        CONSTRUCTION_COST[type] = constructionCost;
        MAINTENANCE_PER_ACCRUAL[type] = maintenancePerAccrual;
        CAPACITY[type] = capacity;
        COVERAGE_RADIUS_TILES[type] = coverageRadiusTiles;
        UNLOCK_POPULATION[type] = unlockPopulation;
    }

    private static void setCostOnly(byte type, double constructionCost) {
        CONSTRUCTION_COST[type] = constructionCost;
    }

    private BuildingEconomics() {
    }
}
