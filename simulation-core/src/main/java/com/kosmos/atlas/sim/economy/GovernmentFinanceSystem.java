package com.kosmos.atlas.sim.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.population.BuildingDensity;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;

/**
 * Collects tax revenue from each city's own population/jobs totals into that city's
 * {@link GovernmentFinance} each time it runs (spec §20, §26). Every player-founded city (spec
 * §9) taxes only its own buildings — a boom-town's revenue never funds a different city's budget.
 * Stateless itself — all authoritative state lives in {@link GovernmentFinance}, consistent with
 * commands being the only thing allowed to change tax *policy* while this system only changes the
 * *balance* it funds (spec §38's separation of concerns extends to systems, not just commands).
 *
 * <p>The same per-city scan also nets every active building's
 * {@link BuildingEconomics#maintenancePerAccrual} (recurring upkeep) against
 * {@link BuildingEconomics#revenuePerAccrual} (currently only the Museum's tourism income) —
 * charged/collected on the same cadence as tax collection (spec's cost system: construction is
 * one-time, maintenance/revenue are ongoing).
 */
public final class GovernmentFinanceSystem {

    /** Runs one tick of revenue collection for every active city. */
    public void tick(BuildingRegistry buildings, CityRegistry cities) {
        cities.forEachActive(cityId -> collectForOneCity(buildings, cities, cityId));
    }

    private void collectForOneCity(BuildingRegistry buildings, CityRegistry cities, int cityId) {
        // Indexed loop rather than forEachActive(id -> ...) here: accumulating into locals from
        // inside a lambda would force the single-element-array boxing trick to work around Java's
        // "effectively final" capture rule. This runs every finance tick per city (spec §41,
        // §42.4), and city counts are small enough that an O(buildings) scan per city is cheap —
        // see docs/roadmap.md's multi-city notes for why this wasn't optimized further.
        double residentialPop = 0;
        double commercialJobs = 0;
        double industrialJobs = 0;
        double maintenance = 0;
        double civicRevenue = 0;
        int highWaterMark = buildings.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!buildings.isActive(id) || buildings.cityId(id) != cityId) {
                continue;
            }
            byte type = buildings.type(id);
            // Taller buildings house higher-value residents/businesses, so they're weighted as
            // more "taxable heads" per actual occupant (BuildingDensity's wageMultiplier) — a
            // skyscraper's population pays more tax than the same headcount in starter buildings.
            double wageMultiplier = BuildingDensity.wageMultiplier(buildings.densityLevel(id));
            switch (type) {
                case BuildingType.RESIDENTIAL -> residentialPop += buildings.population(id) * wageMultiplier;
                case BuildingType.COMMERCIAL -> commercialJobs += buildings.jobs(id) * wageMultiplier;
                case BuildingType.INDUSTRIAL -> industrialJobs += buildings.jobs(id) * wageMultiplier;
                default -> { /* utility/production buildings pay no tax */ }
            }
            maintenance += BuildingEconomics.maintenancePerAccrual(type);
            civicRevenue += BuildingEconomics.revenuePerAccrual(type);
        }
        cities.finance(cityId).collectRevenue(Math.round(residentialPop), Math.round(commercialJobs), Math.round(industrialJobs));
        double net = civicRevenue - maintenance;
        if (net != 0) {
            cities.finance(cityId).adjustTreasury(net);
        }
    }
}
