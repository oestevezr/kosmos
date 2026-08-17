package com.kosmos.atlas.sim.economy;

import com.kosmos.atlas.sim.population.BuildingType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingEconomicsTest {

    @Test
    void higherElectricityTiersCostMoreButServeMoreCapacityFartherAndUnlockLater() {
        assertTrue(BuildingEconomics.constructionCost(BuildingType.POWER_PLANT_HYDRO)
            > BuildingEconomics.constructionCost(BuildingType.POWER_PLANT));
        assertTrue(BuildingEconomics.constructionCost(BuildingType.POWER_PLANT_NUCLEAR)
            > BuildingEconomics.constructionCost(BuildingType.POWER_PLANT_HYDRO));

        assertTrue(BuildingEconomics.capacity(BuildingType.POWER_PLANT_HYDRO) > BuildingEconomics.capacity(BuildingType.POWER_PLANT));
        assertTrue(BuildingEconomics.capacity(BuildingType.POWER_PLANT_NUCLEAR) > BuildingEconomics.capacity(BuildingType.POWER_PLANT_HYDRO));

        assertTrue(BuildingEconomics.coverageRadiusTiles(BuildingType.POWER_PLANT_NUCLEAR)
            > BuildingEconomics.coverageRadiusTiles(BuildingType.POWER_PLANT));

        assertEquals(0, BuildingEconomics.unlockPopulation(BuildingType.POWER_PLANT), "tier 1 is always available");
        assertTrue(BuildingEconomics.unlockPopulation(BuildingType.POWER_PLANT_HYDRO) > 0);
        assertTrue(BuildingEconomics.unlockPopulation(BuildingType.POWER_PLANT_NUCLEAR)
            > BuildingEconomics.unlockPopulation(BuildingType.POWER_PLANT_HYDRO));
    }

    @Test
    void waterTiersFollowTheSameProgressionAsElectricity() {
        assertTrue(BuildingEconomics.constructionCost(BuildingType.DESALINATION_PLANT)
            > BuildingEconomics.constructionCost(BuildingType.WATER_TREATMENT_PLANT));
        assertTrue(BuildingEconomics.capacity(BuildingType.WATER_TREATMENT_PLANT) > BuildingEconomics.capacity(BuildingType.WATER_TOWER));
        assertEquals(0, BuildingEconomics.unlockPopulation(BuildingType.WATER_TOWER));
    }

    @Test
    void nonUtilityBuildingsHaveNoCapacityRadiusOrUnlockRequirement() {
        assertEquals(0, BuildingEconomics.capacity(BuildingType.TRADE_DEPOT));
        assertEquals(0, BuildingEconomics.coverageRadiusTiles(BuildingType.TRADE_DEPOT));
        assertEquals(0, BuildingEconomics.unlockPopulation(BuildingType.TRADE_DEPOT));
        assertTrue(BuildingEconomics.constructionCost(BuildingType.TRADE_DEPOT) > 0);
    }

    @Test
    void residentialCommercialIndustrialHaveNoCommandConstructionCost() {
        // Their cost lives in ZoneCommand instead — see docs/roadmap.md.
        assertEquals(0, BuildingEconomics.constructionCost(BuildingType.RESIDENTIAL));
        assertEquals(0, BuildingEconomics.constructionCost(BuildingType.COMMERCIAL));
        assertEquals(0, BuildingEconomics.constructionCost(BuildingType.INDUSTRIAL));
    }
}
