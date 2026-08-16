package com.kosmos.atlas.sim.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoanSystemTest {

    @Test
    void tickAccruesInterestOnEveryActiveLoan() {
        LoanRegistry loans = new LoanRegistry();
        int a = loans.create(LoanLenderType.EXTERNAL_MARKET, 1, 0, 1000.0, 0.10, 0);
        int b = loans.create(LoanLenderType.CITY, 2, 3, 200.0, 0.05, 0);

        new LoanSystem().tick(loans);

        assertEquals(1100.0, loans.balance(a), 1e-9);
        assertEquals(210.0, loans.balance(b), 1e-9);
    }

    @Test
    void tickSkipsRepaidLoans() {
        LoanRegistry loans = new LoanRegistry();
        int a = loans.create(LoanLenderType.EXTERNAL_MARKET, 1, 0, 1000.0, 0.10, 0);
        loans.applyRepayment(a, 1000.0);

        new LoanSystem().tick(loans); // must not throw or resurrect the tombstoned slot

        assertEquals(0, loans.activeCount());
    }

    @Test
    void interestCompoundsAcrossMultipleTicks() {
        LoanRegistry loans = new LoanRegistry();
        int id = loans.create(LoanLenderType.EXTERNAL_MARKET, 1, 0, 1000.0, 0.10, 0);
        LoanSystem system = new LoanSystem();

        system.tick(loans);
        system.tick(loans);

        assertEquals(1210.0, loans.balance(id), 1e-9);
    }
}
