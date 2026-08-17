package com.kosmos.atlas.sim.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernmentFinanceSystemTest {

    @Test
    void revenueMatchesFlatWageFormulaAtDefaultRates() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = new CityRegistry();
        int cityId = cities.create("Testville", 0, 0, 0);
        int home = buildings.create(BuildingType.RESIDENTIAL, 0, 0, cityId);
        buildings.setPopulation(home, 100);
        int shop = buildings.create(BuildingType.COMMERCIAL, 1, 0, cityId);
        buildings.setJobs(shop, 20);
        int factory = buildings.create(BuildingType.INDUSTRIAL, 2, 0, cityId);
        buildings.setJobs(factory, 10);
        // A power plant must not be taxed as if it were a workplace — but it does cost upkeep.
        buildings.create(BuildingType.POWER_PLANT, 3, 0, cityId);

        double before = cities.finance(cityId).treasuryBalance();
        new GovernmentFinanceSystem().tick(buildings, cities);

        double expected = 100 * 20.0 * GovernmentFinance.DEFAULT_TAX_RATE
            + 20 * 30.0 * GovernmentFinance.DEFAULT_TAX_RATE
            + 10 * 25.0 * GovernmentFinance.DEFAULT_TAX_RATE
            - BuildingEconomics.maintenancePerAccrual(BuildingType.POWER_PLANT);
        assertEquals(expected, cities.finance(cityId).treasuryBalance() - before, 1e-9);
    }

    @Test
    void treasuryAccumulatesAcrossMultipleTicks() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = new CityRegistry();
        int cityId = cities.create("Testville", 0, 0, 0);
        int home = buildings.create(BuildingType.RESIDENTIAL, 0, 0, cityId);
        buildings.setPopulation(home, 50);

        GovernmentFinanceSystem system = new GovernmentFinanceSystem();
        system.tick(buildings, cities);
        double first = cities.finance(cityId).treasuryBalance();
        system.tick(buildings, cities);
        double second = cities.finance(cityId).treasuryBalance() - first;

        assertEquals(first + second, cities.finance(cityId).treasuryBalance(), 1e-9);
        assertTrue(second > 0);
    }

    @Test
    void raisingTaxRateIncreasesFutureRevenue() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = new CityRegistry();
        int cityId = cities.create("Testville", 0, 0, 0);
        int home = buildings.create(BuildingType.RESIDENTIAL, 0, 0, cityId);
        buildings.setPopulation(home, 50);

        GovernmentFinanceSystem system = new GovernmentFinanceSystem();
        double beforeFirstTick = cities.finance(cityId).treasuryBalance();
        system.tick(buildings, cities);
        double atDefaultRate = cities.finance(cityId).treasuryBalance() - beforeFirstTick;

        double beforeSecondTick = cities.finance(cityId).treasuryBalance();
        cities.finance(cityId).setTaxRate(com.kosmos.atlas.sim.world.WorldConstants.ZONE_RESIDENTIAL, 0.5);
        system.tick(buildings, cities);
        double atHigherRate = cities.finance(cityId).treasuryBalance() - beforeSecondTick;

        assertTrue(atHigherRate > atDefaultRate);
    }

    @Test
    void maintenanceIsDeductedForEveryActiveUtilityBuildingRegardlessOfTax() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = new CityRegistry();
        int cityId = cities.create("Testville", 0, 0, 0);
        buildings.create(BuildingType.POWER_PLANT, 0, 0, cityId);
        buildings.create(BuildingType.WATER_TOWER, 1, 0, cityId);

        double before = cities.finance(cityId).treasuryBalance();
        new GovernmentFinanceSystem().tick(buildings, cities);

        double expectedMaintenance = BuildingEconomics.maintenancePerAccrual(BuildingType.POWER_PLANT)
            + BuildingEconomics.maintenancePerAccrual(BuildingType.WATER_TOWER);
        assertEquals(-expectedMaintenance, cities.finance(cityId).treasuryBalance() - before, 1e-9,
            "no tax-generating buildings exist, so the only change should be upkeep");
    }

    @Test
    void museumNetsItsRevenueAgainstItsOwnMaintenance() {
        BuildingRegistry buildings = new BuildingRegistry();
        CityRegistry cities = new CityRegistry();
        int cityId = cities.create("Testville", 0, 0, 0);
        buildings.create(BuildingType.MUSEUM, 0, 0, cityId);

        double before = cities.finance(cityId).treasuryBalance();
        new GovernmentFinanceSystem().tick(buildings, cities);

        double expectedNet = BuildingEconomics.revenuePerAccrual(BuildingType.MUSEUM)
            - BuildingEconomics.maintenancePerAccrual(BuildingType.MUSEUM);
        assertTrue(expectedNet > 0, "the Museum should be net-positive after its own upkeep");
        assertEquals(expectedNet, cities.finance(cityId).treasuryBalance() - before, 1e-9);
    }

    @Test
    void taxRateValidatesRangeAndSector() {
        GovernmentFinance finance = new GovernmentFinance();
        assertThrows(IllegalArgumentException.class,
            () -> finance.setTaxRate(com.kosmos.atlas.sim.world.WorldConstants.ZONE_RESIDENTIAL, 1.5));
        assertThrows(IllegalArgumentException.class,
            () -> finance.setTaxRate(com.kosmos.atlas.sim.world.WorldConstants.ZONE_NONE, 0.1));
    }
}
