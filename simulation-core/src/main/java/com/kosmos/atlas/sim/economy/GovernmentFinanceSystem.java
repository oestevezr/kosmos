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
 * one-time, maintenance/revenue are ongoing). The same scan also derives a city-level
 * {@link #tourismRevenueFor tourism revenue} from population plus how many Museum/Park attractions
 * it has (spec §29's "tourism demand", MVP 0.6's third slice — {@code docs/roadmap.md}).
 */
public final class GovernmentFinanceSystem {

    /** Diminishing returns past this many active Museum/Park attractions in one city — spec §20's
     *  "understandable rather than hyper-realistic". */
    private static final int MAX_TOURISM_ATTRACTIONS = 5;
    /** Tourism revenue doesn't keep scaling forever with city size — capped at this population. */
    private static final double TOURISM_POPULATION_CAP = 5000;
    private static final double TOURISM_REVENUE_PER_RESIDENT = 0.02;

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
        long rawResidentialPop = 0;
        int attractionCount = 0;
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
                case BuildingType.RESIDENTIAL -> {
                    residentialPop += buildings.population(id) * wageMultiplier;
                    rawResidentialPop += buildings.population(id); // tourism scales with headcount, not tax weight
                }
                case BuildingType.COMMERCIAL -> commercialJobs += buildings.jobs(id) * wageMultiplier;
                case BuildingType.INDUSTRIAL -> industrialJobs += buildings.jobs(id) * wageMultiplier;
                case BuildingType.MUSEUM, BuildingType.PARK -> attractionCount++;
                default -> { /* utility/production buildings pay no tax */ }
            }
            maintenance += BuildingEconomics.maintenancePerAccrual(type);
            civicRevenue += BuildingEconomics.revenuePerAccrual(type);
        }
        cities.finance(cityId).collectRevenue(Math.round(residentialPop), Math.round(commercialJobs), Math.round(industrialJobs));
        double net = civicRevenue - maintenance + tourismRevenueFor(rawResidentialPop, attractionCount);
        if (net != 0) {
            cities.finance(cityId).adjustTreasury(net);
        }
    }

    /**
     * City-level tourism income (spec §29's "tourism demand") — proportional to how many active
     * Museum/Park attractions the city has (diminishing returns past {@link #MAX_TOURISM_ATTRACTIONS})
     * and to its population (capped at {@link #TOURISM_POPULATION_CAP}). Deliberately presence-based
     * rather than weighted by each attraction's coverage radius — that would need this system to
     * read {@code Chunk.serviceFlags} per resident, which isn't worth the signature churn for a
     * marginal precision gain (see {@code docs/roadmap.md}'s note on this simplification).
     */
    private static double tourismRevenueFor(long residentialPop, int attractionCount) {
        if (attractionCount <= 0 || residentialPop <= 0) {
            return 0;
        }
        int cappedAttractions = Math.min(attractionCount, MAX_TOURISM_ATTRACTIONS);
        double cappedPop = Math.min(residentialPop, TOURISM_POPULATION_CAP);
        return cappedAttractions * cappedPop * TOURISM_REVENUE_PER_RESIDENT;
    }
}
