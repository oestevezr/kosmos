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

    @Test
    void policeAndEducationTier2CostMoreCoverFartherAndUnlockLaterThanTier1() {
        assertTrue(BuildingEconomics.constructionCost(BuildingType.POLICE_STATION)
            > BuildingEconomics.constructionCost(BuildingType.POLICE_OUTPOST));
        assertEquals(0, BuildingEconomics.unlockPopulation(BuildingType.POLICE_OUTPOST));
        assertTrue(BuildingEconomics.unlockPopulation(BuildingType.POLICE_STATION) > 0);

        assertTrue(BuildingEconomics.constructionCost(BuildingType.UNIVERSITY) > BuildingEconomics.constructionCost(BuildingType.SCHOOL));
        assertEquals(0, BuildingEconomics.unlockPopulation(BuildingType.SCHOOL));
        assertTrue(BuildingEconomics.unlockPopulation(BuildingType.UNIVERSITY) > 0);

        assertTrue(BuildingEconomics.constructionCost(BuildingType.CHURCH) > 0);
        assertEquals(0, BuildingEconomics.unlockPopulation(BuildingType.CHURCH), "no tier 2, always available like Cemetery");
    }

    @Test
    void pollutersHavePositiveIntensityAndParkIsTheOnlyReducer() {
        assertTrue(BuildingEconomics.pollutionIntensity(BuildingType.INDUSTRIAL) > 0);
        assertTrue(BuildingEconomics.pollutionIntensity(BuildingType.STEEL_MILL) > 0);
        assertTrue(BuildingEconomics.pollutionIntensity(BuildingType.MINE) > 0);
        assertTrue(BuildingEconomics.pollutionIntensity(BuildingType.QUARRY) > 0);
        assertTrue(BuildingEconomics.pollutionIntensity(BuildingType.POWER_PLANT) > 0);
        assertTrue(BuildingEconomics.pollutionIntensity(BuildingType.INCINERATOR) > 0);

        assertTrue(BuildingEconomics.pollutionIntensity(BuildingType.PARK) < 0, "Park is the only reducer");

        assertEquals(0, BuildingEconomics.pollutionIntensity(BuildingType.POWER_PLANT_HYDRO), "only the tier-1 plant pollutes");
        assertEquals(0, BuildingEconomics.pollutionIntensity(BuildingType.POWER_PLANT_NUCLEAR), "only the tier-1 plant pollutes");
        assertEquals(0, BuildingEconomics.pollutionIntensity(BuildingType.RESIDENTIAL));
        assertEquals(0, BuildingEconomics.pollutionIntensity(BuildingType.HOSPITAL));
    }

    @Test
    void everyPollutionSourceHasAPositiveRadius() {
        byte[] pollutionSources = {
            BuildingType.INDUSTRIAL, BuildingType.STEEL_MILL, BuildingType.MINE, BuildingType.QUARRY,
            BuildingType.POWER_PLANT, BuildingType.INCINERATOR, BuildingType.PARK,
        };
        for (byte type : pollutionSources) {
            assertTrue(BuildingEconomics.pollutionRadiusTiles(type) > 0,
                "type " + type + " has nonzero pollution intensity so it needs a radius to spread within");
        }
    }

    @Test
    void centralBankHasNoCoverageButGatesLendingAndCostsMore() {
        assertEquals(0, BuildingEconomics.capacity(BuildingType.CENTRAL_BANK));
        assertEquals(0, BuildingEconomics.coverageRadiusTiles(BuildingType.CENTRAL_BANK), "not a UtilitySystem coverage source");
        assertTrue(BuildingEconomics.unlockPopulation(BuildingType.CENTRAL_BANK) > 0);
        assertTrue(BuildingEconomics.constructionCost(BuildingType.CENTRAL_BANK) > 0);
    }

    @Test
    void cityHallHasNoConstructionCostSinceItsNeverPurchased() {
        assertEquals(0, BuildingEconomics.constructionCost(BuildingType.CITY_HALL),
            "FoundCityCommand places it for free — there's no purchase to charge for");
        assertEquals(0, BuildingEconomics.coverageRadiusTiles(BuildingType.CITY_HALL));
        assertTrue(BuildingEconomics.maintenancePerAccrual(BuildingType.CITY_HALL) > 0, "still has upkeep");
    }
}
