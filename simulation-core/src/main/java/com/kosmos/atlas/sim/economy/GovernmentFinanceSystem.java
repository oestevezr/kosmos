package com.kosmos.atlas.sim.economy;

import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;

/**
 * Collects tax revenue from city-wide population/jobs totals into {@link GovernmentFinance} each
 * time it runs (spec §20, §26). Stateless itself — all authoritative state lives in
 * {@link GovernmentFinance}, consistent with commands being the only thing allowed to change tax
 * *policy* while this system only changes the *balance* it funds (spec §38's separation of
 * concerns extends to systems, not just commands).
 */
public final class GovernmentFinanceSystem {

    /** Runs one tick of revenue collection; returns the amount collected for reporting/tests. */
    public double tick(BuildingRegistry buildings, GovernmentFinance finance) {
        // Indexed loop rather than forEachActive(id -> ...): see PopulationSystem.recomputeCityTotals
        // for why this avoids the single-element-array boxing trick in a system that runs on a
        // recurring cadence (spec §41, §42.4).
        long residentialPop = 0;
        long commercialJobs = 0;
        long industrialJobs = 0;
        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id)) {
                continue;
            }
            switch (buildings.type(id)) {
                case BuildingType.RESIDENTIAL -> residentialPop += buildings.population(id);
                case BuildingType.COMMERCIAL -> commercialJobs += buildings.jobs(id);
                case BuildingType.INDUSTRIAL -> industrialJobs += buildings.jobs(id);
                default -> { /* utility buildings pay no tax in Fase 2 */ }
            }
        }
        return finance.collectRevenue(residentialPop, commercialJobs, industrialJobs);
    }
}
