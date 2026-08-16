package com.kosmos.atlas.sim.economy;

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
        int home = buildings.create(BuildingType.RESIDENTIAL, 0, 0);
        buildings.setPopulation(home, 100);
        int shop = buildings.create(BuildingType.COMMERCIAL, 1, 0);
        buildings.setJobs(shop, 20);
        int factory = buildings.create(BuildingType.INDUSTRIAL, 2, 0);
        buildings.setJobs(factory, 10);
        // A power plant must not be taxed as if it were a workplace.
        buildings.create(BuildingType.POWER_PLANT, 3, 0);

        GovernmentFinance finance = new GovernmentFinance();
        double revenue = new GovernmentFinanceSystem().tick(buildings, finance);

        double expected = 100 * 20.0 * GovernmentFinance.DEFAULT_TAX_RATE
            + 20 * 30.0 * GovernmentFinance.DEFAULT_TAX_RATE
            + 10 * 25.0 * GovernmentFinance.DEFAULT_TAX_RATE;
        assertEquals(expected, revenue, 1e-9);
        assertEquals(expected, finance.treasuryBalance(), 1e-9);
    }

    @Test
    void treasuryAccumulatesAcrossMultipleTicks() {
        BuildingRegistry buildings = new BuildingRegistry();
        int home = buildings.create(BuildingType.RESIDENTIAL, 0, 0);
        buildings.setPopulation(home, 50);

        GovernmentFinance finance = new GovernmentFinance();
        GovernmentFinanceSystem system = new GovernmentFinanceSystem();
        double first = system.tick(buildings, finance);
        double second = system.tick(buildings, finance);

        assertEquals(first + second, finance.treasuryBalance(), 1e-9);
        assertTrue(second > 0);
    }

    @Test
    void raisingTaxRateIncreasesFutureRevenue() {
        BuildingRegistry buildings = new BuildingRegistry();
        int home = buildings.create(BuildingType.RESIDENTIAL, 0, 0);
        buildings.setPopulation(home, 50);

        GovernmentFinance finance = new GovernmentFinance();
        GovernmentFinanceSystem system = new GovernmentFinanceSystem();
        double atDefaultRate = system.tick(buildings, finance);

        finance.setTaxRate(com.kosmos.atlas.sim.world.WorldConstants.ZONE_RESIDENTIAL, 0.5);
        double atHigherRate = system.tick(buildings, finance);

        assertTrue(atHigherRate > atDefaultRate);
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
