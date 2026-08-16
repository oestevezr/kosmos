package com.kosmos.atlas.sim.economy;

/**
 * Who a {@link LoanRegistry} entry borrowed from — the two lender kinds the user asked for: the
 * simulated external market (always available, high interest, spec's existing "external market"
 * concept from {@code MarketSystem}/{@code TradeDepot}) or another player-founded city whose
 * prosperity lets it extend credit (spec §9's inter-city economy).
 */
public final class LoanLenderType {

    /** Always available, no prosperity gate, fixed high rate — see {@code LoanSystem}. */
    public static final byte EXTERNAL_MARKET = 0;
    /** Another active city; gated by that city's treasury via {@code RequestCityLoanCommand}. */
    public static final byte CITY = 1;

    private LoanLenderType() {
    }
}
