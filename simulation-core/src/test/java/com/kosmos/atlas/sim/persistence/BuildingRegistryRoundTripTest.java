package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trips {@code settlements.dat}, including a tombstoned (demolished) id, per spec §31. */
class BuildingRegistryRoundTripTest {

    @Test
    void activeAndTombstonedBuildingsRoundTrip(@TempDir Path tmp) throws IOException {
        BuildingRegistry original = new BuildingRegistry();
        int home = original.create(BuildingType.RESIDENTIAL, 10, 20);
        original.setPopulation(home, 42);
        original.setIncomeLevel(home, (byte) 2);
        original.setEmploymentRatePercent(home, 77);
        original.setSatisfactionPercent(home, 60);

        int shop = original.create(BuildingType.COMMERCIAL, 11, 20);
        original.setJobs(shop, 15);

        int demolished = original.create(BuildingType.INDUSTRIAL, 12, 20);
        original.demolish(demolished);

        Path file = tmp.resolve("settlements.dat");
        BuildingRegistryIO.write(file, original);
        BuildingRegistry loaded = BuildingRegistryIO.read(file);

        assertEquals(original.highWaterMark(), loaded.highWaterMark());
        assertTrue(loaded.isActive(home));
        assertEquals(BuildingType.RESIDENTIAL, loaded.type(home));
        assertEquals(10, loaded.tileX(home));
        assertEquals(20, loaded.tileY(home));
        assertEquals(42, loaded.population(home));
        assertEquals(2, loaded.incomeLevel(home));
        assertEquals(77, loaded.employmentRatePercent(home));
        assertEquals(60, loaded.satisfactionPercent(home));

        assertTrue(loaded.isActive(shop));
        assertEquals(15, loaded.jobs(shop));

        assertFalse(loaded.isActive(demolished), "a demolished building must load back as inactive");

        // The restored free list must still be usable for new construction.
        int reused = loaded.create(BuildingType.RESIDENTIAL, 99, 99);
        assertTrue(loaded.isActive(reused));
    }

    @Test
    void emptyRegistryRoundTrips(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("settlements.dat");
        BuildingRegistryIO.write(file, new BuildingRegistry());
        BuildingRegistry loaded = BuildingRegistryIO.read(file);
        assertEquals(0, loaded.activeCount());
    }
}
