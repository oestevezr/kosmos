package com.kosmos.atlas.sim.economy;

import com.kosmos.atlas.sim.world.WorldConstants;

/**
 * Authoritative treasury state: per-sector tax rates and the accumulated balance (spec §20, §26:
 * "basic taxes"). Deliberately simple — flat per-capita/per-job revenue rather than a full price
 * model, matching spec §20's "MVP economy should be understandable rather than hyper-realistic."
 *
 * <p>Tax rates are indexed by the {@link WorldConstants} zone-type bytes so {@code SetTaxPolicyCommand}
 * and {@code GovernmentFinanceSystem} share one vocabulary with zoning itself.
 */
public final class GovernmentFinance {

    /** Simulated currency earned per resident / job per tick before tax (spec §20's "Production"). */
    private static final double WAGE_PER_RESIDENT = 20.0;
    private static final double WAGE_PER_COMMERCIAL_JOB = 30.0;
    private static final double WAGE_PER_INDUSTRIAL_JOB = 25.0;

    public static final double DEFAULT_TAX_RATE = 0.09;

    // Indexed by WorldConstants.ZONE_*; index 0 (ZONE_NONE) is unused.
    private final double[] taxRateByZone = {0, DEFAULT_TAX_RATE, DEFAULT_TAX_RATE, DEFAULT_TAX_RATE};

    private double treasuryBalance;

    public double taxRate(byte zoneType) {
        return taxRateByZone[zoneType];
    }

    public void setTaxRate(byte zoneType, double rate) {
        if (zoneType < WorldConstants.ZONE_RESIDENTIAL || zoneType > WorldConstants.ZONE_INDUSTRIAL) {
            throw new IllegalArgumentException("Not a taxable zone type: " + zoneType);
        }
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("Tax rate must be within [0,1], was " + rate);
        }
        taxRateByZone[zoneType] = rate;
    }

    public double treasuryBalance() {
        return treasuryBalance;
    }

    /** Collects one tick's revenue from city-wide totals and adds it to the balance; returns the amount collected. */
    public double collectRevenue(long totalResidentialPopulation, long totalCommercialJobs, long totalIndustrialJobs) {
        double revenue =
            totalResidentialPopulation * WAGE_PER_RESIDENT * taxRateByZone[WorldConstants.ZONE_RESIDENTIAL]
                + totalCommercialJobs * WAGE_PER_COMMERCIAL_JOB * taxRateByZone[WorldConstants.ZONE_COMMERCIAL]
                + totalIndustrialJobs * WAGE_PER_INDUSTRIAL_JOB * taxRateByZone[WorldConstants.ZONE_INDUSTRIAL];
        treasuryBalance += revenue;
        return revenue;
    }

    /**
     * Deposits or withdraws {@code delta} directly — used by {@code MarketSystem} for trade
     * revenue/cost (spec §29: the external market has "commodity prices" a {@code TradeDepot}
     * buys/sells at), which isn't a per-capita tax and so doesn't go through
     * {@link #collectRevenue}. Negative {@code delta} is a withdrawal (e.g. paying for imports);
     * the balance is allowed to go negative — spec's MVP economy has no bankruptcy rule yet.
     */
    public void adjustTreasury(double delta) {
        treasuryBalance += delta;
    }
}
