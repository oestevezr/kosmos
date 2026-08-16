package com.kosmos.atlas.sim.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanRegistryTest {

    @Test
    void createIsRetrievableAndActive() {
        LoanRegistry loans = new LoanRegistry();
        int id = loans.create(LoanLenderType.EXTERNAL_MARKET, 1, 0, 1000.0, 0.02, 5);

        assertTrue(loans.isActive(id));
        assertEquals(LoanLenderType.EXTERNAL_MARKET, loans.lenderType(id));
        assertEquals(1, loans.borrowerCityId(id));
        assertEquals(0, loans.lenderCityId(id));
        assertEquals(1000.0, loans.principal(id));
        assertEquals(1000.0, loans.balance(id));
        assertEquals(0.02, loans.interestRatePerAccrual(id));
        assertEquals(5, loans.originationTick(id));
    }

    @Test
    void accrueInterestGrowsBalanceButNotPrincipal() {
        LoanRegistry loans = new LoanRegistry();
        int id = loans.create(LoanLenderType.EXTERNAL_MARKET, 1, 0, 1000.0, 0.10, 0);

        loans.accrueInterest(id);

        assertEquals(1100.0, loans.balance(id), 1e-9);
        assertEquals(1000.0, loans.principal(id), 1e-9, "principal never changes after origination");
    }

    @Test
    void partialRepaymentReducesBalanceWithoutClosingLoan() {
        LoanRegistry loans = new LoanRegistry();
        int id = loans.create(LoanLenderType.EXTERNAL_MARKET, 1, 0, 1000.0, 0.0, 0);

        loans.applyRepayment(id, 400.0);

        assertEquals(600.0, loans.balance(id), 1e-9);
        assertTrue(loans.isActive(id));
    }

    @Test
    void fullRepaymentClosesLoanAndFreesIdForReuse() {
        LoanRegistry loans = new LoanRegistry();
        int id = loans.create(LoanLenderType.CITY, 1, 2, 500.0, 0.0, 0);

        loans.applyRepayment(id, 500.0);

        assertFalse(loans.isActive(id));
        int reused = loans.create(LoanLenderType.EXTERNAL_MARKET, 3, 0, 10.0, 0.0, 1);
        assertEquals(id, reused);
    }

    @Test
    void forEachActiveSkipsRepaidLoans() {
        LoanRegistry loans = new LoanRegistry();
        int a = loans.create(LoanLenderType.EXTERNAL_MARKET, 1, 0, 100.0, 0.0, 0);
        loans.create(LoanLenderType.EXTERNAL_MARKET, 1, 0, 100.0, 0.0, 0);
        loans.applyRepayment(a, 100.0);

        int[] count = {0};
        loans.forEachActive(id -> count[0]++);
        assertEquals(1, count[0]);
        assertEquals(1, loans.activeCount());
    }
}
