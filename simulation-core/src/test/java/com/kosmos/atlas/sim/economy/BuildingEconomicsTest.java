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

    @Test
    void healthcareFireAndSanitationTier2CostMoreCoverFartherAndUnlockLaterThanTier1() {
        assertTrue(BuildingEconomics.constructionCost(BuildingType.HOSPITAL) > BuildingEconomics.constructionCost(BuildingType.CLINIC));
        assertTrue(BuildingEconomics.coverageRadiusTiles(BuildingType.HOSPITAL) > BuildingEconomics.coverageRadiusTiles(BuildingType.CLINIC));
        assertEquals(0, BuildingEconomics.unlockPopulation(BuildingType.CLINIC), "tier 1 is always available");
        assertTrue(BuildingEconomics.unlockPopulation(BuildingType.HOSPITAL) > 0);

        assertTrue(BuildingEconomics.constructionCost(BuildingType.FIRE_STATION)
            > BuildingEconomics.constructionCost(BuildingType.VOLUNTEER_FIRE_BRIGADE));
        assertEquals(0, BuildingEconomics.unlockPopulation(BuildingType.VOLUNTEER_FIRE_BRIGADE));

        assertTrue(BuildingEconomics.constructionCost(BuildingType.INCINERATOR) > BuildingEconomics.constructionCost(BuildingType.WASTE_COLLECTION));
        assertEquals(0, BuildingEconomics.unlockPopulation(BuildingType.WASTE_COLLECTION));
    }

    @Test
    void cemeteryParkAndMuseumHaveNoCapacityLikeElectricityWaterDo() {
        assertEquals(0, BuildingEconomics.capacity(BuildingType.CEMETERY));
        assertEquals(0, BuildingEconomics.capacity(BuildingType.PARK));
        assertEquals(0, BuildingEconomics.capacity(BuildingType.MUSEUM));
        assertTrue(BuildingEconomics.coverageRadiusTiles(BuildingType.PARK) > 0, "still a coverage source, just no capacity");
    }

    @Test
    void onlyMuseumGeneratesItsOwnRevenue() {
        assertTrue(BuildingEconomics.revenuePerAccrual(BuildingType.MUSEUM) > 0);
        assertEquals(0, BuildingEconomics.revenuePerAccrual(BuildingType.PARK));
        assertEquals(0, BuildingEconomics.revenuePerAccrual(BuildingType.HOSPITAL));
        assertEquals(0, BuildingEconomics.revenuePerAccrual(BuildingType.CLINIC));
    }
}
