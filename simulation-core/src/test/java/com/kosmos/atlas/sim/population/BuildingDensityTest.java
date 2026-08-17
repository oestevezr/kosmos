package com.kosmos.atlas.sim.population;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingDensityTest {

    @Test
    void capacityGrowsWithEachLevel() {
        for (int level = 0; level < BuildingDensity.MAX_LEVEL; level++) {
            assertTrue(BuildingDensity.residentialCapacity(level + 1) > BuildingDensity.residentialCapacity(level));
            assertTrue(BuildingDensity.jobCapacity(level + 1) > BuildingDensity.jobCapacity(level));
        }
    }

    @Test
    void promotionThresholdsAreStrictlyAboveDemotionThresholds() {
        for (int level = 1; level <= BuildingDensity.MAX_LEVEL; level++) {
            assertTrue(BuildingDensity.promoteSatisfaction(level) > BuildingDensity.demoteSatisfaction(level),
                "level " + level + " needs hysteresis or a building would flap between levels");
        }
    }

    @Test
    void wageMultiplierGrowsWithEachLevel() {
        for (int level = 0; level < BuildingDensity.MAX_LEVEL; level++) {
            assertTrue(BuildingDensity.wageMultiplier(level + 1) > BuildingDensity.wageMultiplier(level));
        }
    }

    @Test
    void variantIndexIsDeterministicForTheSameInputs() {
        int first = BuildingDensity.variantIndex(12, 34, 1, 6);
        int second = BuildingDensity.variantIndex(12, 34, 1, 6);
        assertEquals(first, second);
    }

    @Test
    void variantIndexStaysWithinRange() {
        for (int x = -5; x <= 5; x++) {
            for (int level = 0; level <= BuildingDensity.MAX_LEVEL; level++) {
                int variant = BuildingDensity.variantIndex(x, 100, level, 4);
                assertTrue(variant >= 0 && variant < 4);
            }
        }
    }

    @Test
    void variantIndexChangesWhenTheLevelChanges() {
        int atLevel0 = BuildingDensity.variantIndex(7, 9, 0, 8);
        int atLevel1 = BuildingDensity.variantIndex(7, 9, 1, 8);
        int atLevel2 = BuildingDensity.variantIndex(7, 9, 2, 8);
        assertTrue(atLevel0 != atLevel1 || atLevel1 != atLevel2, "at least one promotion should change the visual variant");
    }
}
