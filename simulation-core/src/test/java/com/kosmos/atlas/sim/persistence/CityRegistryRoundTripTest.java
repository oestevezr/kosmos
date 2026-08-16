package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.Difficulty;
import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.economy.GoodType;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trips {@code cities.dat}, including the world's {@link Difficulty}. */
class CityRegistryRoundTripTest {

    @Test
    void difficultyAndCityStateRoundTrip(@TempDir Path tmp) throws IOException {
        CityRegistry original = new CityRegistry(Difficulty.HARD);
        int cityId = original.create("Hardville", 10, 20, 5);
        original.finance(cityId).adjustTreasury(1234.5);
        original.finance(cityId).setTaxRate(WorldConstants.ZONE_RESIDENTIAL, 0.2);
        original.ledger(cityId).setInventory(GoodType.FOOD, 50);

        Path file = tmp.resolve("cities.dat");
        CityRegistryIO.write(file, original);
        CityRegistry loaded = CityRegistryIO.read(file);

        assertEquals(Difficulty.HARD, loaded.difficulty());
        assertTrue(loaded.isActive(cityId));
        assertEquals(Difficulty.HARD.startingTreasury + 1234.5, loaded.finance(cityId).treasuryBalance(), 1e-9);
        assertEquals(0.2, loaded.finance(cityId).taxRate(WorldConstants.ZONE_RESIDENTIAL), 1e-9);
        assertEquals(50, loaded.ledger(cityId).inventory(GoodType.FOOD));

        // A city founded after loading still gets the persisted difficulty's starting treasury.
        int newCity = loaded.create("Newtown", 99, 99, 10);
        assertEquals(Difficulty.HARD.startingTreasury, loaded.finance(newCity).treasuryBalance(), 1e-9);
    }

    @Test
    void tombstonedCityLoadsBackAsInactive(@TempDir Path tmp) throws IOException {
        CityRegistry original = new CityRegistry(Difficulty.EASY);
        original.create("Alpha", 0, 0, 0);

        Path file = tmp.resolve("cities.dat");
        CityRegistryIO.write(file, original);
        CityRegistry loaded = CityRegistryIO.read(file);

        assertFalse(loaded.isActive(999));
        assertEquals(Difficulty.EASY, loaded.difficulty());
    }
}
