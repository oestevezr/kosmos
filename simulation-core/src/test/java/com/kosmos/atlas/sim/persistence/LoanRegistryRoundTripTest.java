package com.kosmos.atlas.sim.persistence;

import com.kosmos.atlas.sim.economy.LoanLenderType;
import com.kosmos.atlas.sim.economy.LoanRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trips {@code loans.dat}, including a fully-repaid (tombstoned) loan id. */
class LoanRegistryRoundTripTest {

    @Test
    void activeAndRepaidLoansRoundTrip(@TempDir Path tmp) throws IOException {
        LoanRegistry original = new LoanRegistry();
        int external = original.create(LoanLenderType.EXTERNAL_MARKET, 1, 0, 1000.0, 0.02, 10);
        original.accrueInterest(external);
        int cityLoan = original.create(LoanLenderType.CITY, 2, 3, 500.0, 0.005, 20);
        int repaid = original.create(LoanLenderType.EXTERNAL_MARKET, 4, 0, 250.0, 0.02, 30);
        original.applyRepayment(repaid, 250.0);

        Path file = tmp.resolve("loans.dat");
        LoanRegistryIO.write(file, original);
        LoanRegistry loaded = LoanRegistryIO.read(file);

        assertEquals(original.highWaterMark(), loaded.highWaterMark());

        assertTrue(loaded.isActive(external));
        assertEquals(LoanLenderType.EXTERNAL_MARKET, loaded.lenderType(external));
        assertEquals(1, loaded.borrowerCityId(external));
        assertEquals(0, loaded.lenderCityId(external));
        assertEquals(1000.0, loaded.principal(external), 1e-9);
        assertEquals(1020.0, loaded.balance(external), 1e-9);
        assertEquals(0.02, loaded.interestRatePerAccrual(external), 1e-9);
        assertEquals(10, loaded.originationTick(external));

        assertTrue(loaded.isActive(cityLoan));
        assertEquals(LoanLenderType.CITY, loaded.lenderType(cityLoan));
        assertEquals(3, loaded.lenderCityId(cityLoan));

        assertFalse(loaded.isActive(repaid), "a fully repaid loan must load back as inactive");

        // The restored free list must still be usable for new loans.
        int reused = loaded.create(LoanLenderType.EXTERNAL_MARKET, 5, 0, 10.0, 0.02, 40);
        assertTrue(loaded.isActive(reused));
    }

    @Test
    void emptyRegistryRoundTrips(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("loans.dat");
        LoanRegistryIO.write(file, new LoanRegistry());
        LoanRegistry loaded = LoanRegistryIO.read(file);
        assertEquals(0, loaded.activeCount());
    }
}
