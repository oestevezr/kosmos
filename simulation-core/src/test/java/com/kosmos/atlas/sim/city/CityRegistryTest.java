package com.kosmos.atlas.sim.city;

import com.kosmos.atlas.sim.Difficulty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Covers the difficulty-driven starting treasury (the user's requested TheOtown-inspired tiers). */
class CityRegistryTest {

    @Test
    void newlyFoundedCityStartsWithItsDifficultysTreasury() {
        CityRegistry easy = new CityRegistry(Difficulty.EASY);
        int easyCity = easy.create("Easyville", 0, 0, 0);
        assertEquals(Difficulty.EASY.startingTreasury, easy.finance(easyCity).treasuryBalance(), 1e-9);

        CityRegistry hard = new CityRegistry(Difficulty.HARD);
        int hardCity = hard.create("Hardville", 0, 0, 0);
        assertEquals(Difficulty.HARD.startingTreasury, hard.finance(hardCity).treasuryBalance(), 1e-9);
    }

    @Test
    void everyCityFoundedInTheSameWorldGetsTheSameStartingTreasury() {
        CityRegistry cities = new CityRegistry(Difficulty.MEDIUM);
        int first = cities.create("First", 0, 0, 0);
        int second = cities.create("Second", 100, 100, 0);

        assertEquals(cities.finance(first).treasuryBalance(), cities.finance(second).treasuryBalance(), 1e-9);
        assertEquals(Difficulty.MEDIUM.startingTreasury, cities.finance(second).treasuryBalance(), 1e-9);
    }

    @Test
    void defaultConstructorUsesMediumDifficulty() {
        CityRegistry cities = new CityRegistry();
        assertEquals(Difficulty.MEDIUM, cities.difficulty());
        int cityId = cities.create("Testville", 0, 0, 0);
        assertEquals(Difficulty.MEDIUM.startingTreasury, cities.finance(cityId).treasuryBalance(), 1e-9);
    }
}
