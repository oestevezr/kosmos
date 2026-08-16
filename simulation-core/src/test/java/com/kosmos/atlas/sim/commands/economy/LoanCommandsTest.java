package com.kosmos.atlas.sim.commands.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.economy.LoanLenderType;
import com.kosmos.atlas.sim.economy.LoanRegistry;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.world.ChunkStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the loan system's accept/reject rules — external market vs. inter-city credit. */
class LoanCommandsTest {

    private ChunkStore store;
    private BuildingRegistry buildings;
    private CityRegistry cities;
    private LoanRegistry loans;
    private int borrower;
    private int prosperousLender;
    private int poorLender;

    @BeforeEach
    void setUp() {
        store = new ChunkStore(4);
        buildings = new BuildingRegistry();
        cities = new CityRegistry();
        loans = new LoanRegistry();
        // Cities start with Difficulty.MEDIUM's default treasury (25,000) — zeroed out here so
        // each test controls exact balances instead of reasoning about the starting amount.
        borrower = cities.create("Borrowtown", 0, 0, 0);
        zeroTreasury(borrower);
        prosperousLender = cities.create("Richfield", 100, 100, 0);
        zeroTreasury(prosperousLender);
        cities.finance(prosperousLender).adjustTreasury(20_000.0);
        poorLender = cities.create("Poorville", 200, 200, 0);
        zeroTreasury(poorLender);
        cities.finance(poorLender).adjustTreasury(1_000.0);
    }

    private void zeroTreasury(int cityId) {
        cities.finance(cityId).adjustTreasury(-cities.finance(cityId).treasuryBalance());
    }

    private SimulationContext ctx() {
        return new SimulationContext(store, buildings, cities, null, loans, 4096, 0);
    }

    @Test
    void externalLoanIsAlwaysAcceptedForAFoundedCity() {
        assertEquals(CommandResult.ACCEPTED, new RequestExternalLoanCommand(borrower, 1000.0).apply(ctx()));
        assertEquals(1000.0, cities.finance(borrower).treasuryBalance(), 1e-9);
        assertEquals(1, loans.activeCount());
        assertEquals(LoanLenderType.EXTERNAL_MARKET, loans.lenderType(1));
    }

    @Test
    void externalLoanRejectsInvalidAmountOrUnknownCity() {
        assertEquals(CommandResult.REJECTED_INVALID_LOAN_AMOUNT, new RequestExternalLoanCommand(borrower, 0.0).apply(ctx()));
        assertEquals(CommandResult.REJECTED_INVALID_LOAN_AMOUNT,
            new RequestExternalLoanCommand(borrower, RequestExternalLoanCommand.MAX_AMOUNT + 1).apply(ctx()));
        assertEquals(CommandResult.REJECTED_NO_CITY_FOUNDED, new RequestExternalLoanCommand(999, 100.0).apply(ctx()));
    }

    @Test
    void cityLoanFromAProsperousLenderSucceedsAndMovesMoney() {
        double lenderBefore = cities.finance(prosperousLender).treasuryBalance();
        assertEquals(CommandResult.ACCEPTED, new RequestCityLoanCommand(borrower, prosperousLender, 5000.0).apply(ctx()));

        assertEquals(5000.0, cities.finance(borrower).treasuryBalance(), 1e-9);
        assertEquals(lenderBefore - 5000.0, cities.finance(prosperousLender).treasuryBalance(), 1e-9);
        assertEquals(1, loans.activeCount());
        assertEquals(LoanLenderType.CITY, loans.lenderType(1));
        assertEquals(prosperousLender, loans.lenderCityId(1));
    }

    @Test
    void cityLoanRejectedWhenLenderNotProsperousEnough() {
        assertEquals(CommandResult.REJECTED_LENDER_NOT_PROSPEROUS,
            new RequestCityLoanCommand(borrower, poorLender, 500.0).apply(ctx()));
    }

    @Test
    void cityLoanRejectedWhenItWouldDrainLenderBelowReserve() {
        // Prosperous lender has 20,000; borrowing 19,000 would leave only 1,000, below the reserve floor.
        assertEquals(CommandResult.REJECTED_LENDER_NOT_PROSPEROUS,
            new RequestCityLoanCommand(borrower, prosperousLender, 19_000.0).apply(ctx()));
    }

    @Test
    void cityCannotLendToItself() {
        assertEquals(CommandResult.REJECTED_SAME_CITY_LOAN,
            new RequestCityLoanCommand(prosperousLender, prosperousLender, 100.0).apply(ctx()));
    }

    @Test
    void richerLenderChargesLowerInterestThanExternalMarket() {
        new RequestCityLoanCommand(borrower, prosperousLender, 1000.0).apply(ctx());
        assertTrue(loans.interestRatePerAccrual(1) < RequestExternalLoanCommand.INTEREST_RATE_PER_ACCRUAL,
            "a prosperous city should undercut the external market's fixed high rate");
    }

    @Test
    void repayPartiallyReducesBalanceAndMovesMoneyToLender() {
        new RequestCityLoanCommand(borrower, prosperousLender, 1000.0).apply(ctx());
        double lenderAfterLoan = cities.finance(prosperousLender).treasuryBalance();

        assertEquals(CommandResult.ACCEPTED, new RepayLoanCommand(1, 400.0).apply(ctx()));

        assertEquals(600.0, loans.balance(1), 1e-9);
        assertEquals(600.0, cities.finance(borrower).treasuryBalance(), 1e-9);
        assertEquals(lenderAfterLoan + 400.0, cities.finance(prosperousLender).treasuryBalance(), 1e-9);
        assertTrue(loans.isActive(1));
    }

    @Test
    void repayInFullClosesLoan() {
        new RequestExternalLoanCommand(borrower, 500.0).apply(ctx());

        assertEquals(CommandResult.ACCEPTED, new RepayLoanCommand(1, 500.0).apply(ctx()));

        assertEquals(0, loans.activeCount());
    }

    @Test
    void repayCapsAtOutstandingBalanceRatherThanOverpaying() {
        new RequestExternalLoanCommand(borrower, 500.0).apply(ctx());
        cities.finance(borrower).adjustTreasury(1000.0); // plenty of money to try to overpay with

        new RepayLoanCommand(1, 5000.0).apply(ctx());

        assertEquals(0, loans.activeCount(), "overpaying must simply close the loan, not go negative");
    }

    @Test
    void repayRejectsUnknownLoanOrInvalidAmount() {
        assertEquals(CommandResult.REJECTED_LOAN_NOT_FOUND, new RepayLoanCommand(999, 100.0).apply(ctx()));

        new RequestExternalLoanCommand(borrower, 500.0).apply(ctx());
        assertEquals(CommandResult.REJECTED_INVALID_LOAN_AMOUNT, new RepayLoanCommand(1, 0.0).apply(ctx()));
    }
}
