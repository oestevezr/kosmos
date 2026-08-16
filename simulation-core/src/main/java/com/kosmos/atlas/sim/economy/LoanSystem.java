package com.kosmos.atlas.sim.economy;

/**
 * Accrues interest on every outstanding {@link LoanRegistry} entry (spec's MVP economy
 * simplification, same pattern as {@link GovernmentFinanceSystem}/{@link MarketSystem}: a plain
 * indexed loop over {@code highWaterMark}, no boxing, no per-tick allocation — spec §42.4).
 *
 * <p>Interest is compounded onto {@link LoanRegistry#balance} only; it never touches a city's
 * treasury directly. Money only moves at loan origination ({@code RequestExternalLoanCommand}/
 * {@code RequestCityLoanCommand}) and repayment ({@code RepayLoanCommand}) — the borrower must
 * actively repay, there is no auto-debit or bankruptcy rule, matching
 * {@link GovernmentFinance#adjustTreasury}'s existing "balance is allowed to go negative" MVP
 * simplification.
 */
public final class LoanSystem {

    public void tick(LoanRegistry loans) {
        int highWaterMark = loans.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (loans.isActive(id)) {
                loans.accrueInterest(id);
            }
        }
    }
}
