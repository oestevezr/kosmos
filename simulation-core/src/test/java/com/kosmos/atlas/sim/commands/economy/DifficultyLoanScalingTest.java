package com.kosmos.atlas.sim.commands.economy;

import com.kosmos.atlas.sim.Difficulty;
import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.economy.LoanRegistry;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.world.ChunkStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the user's requested "créditos" difficulty knob: loan rates scale with world difficulty. */
class DifficultyLoanScalingTest {

    private SimulationContext ctxFor(CityRegistry cities, BuildingRegistry buildings, LoanRegistry loans) {
        return new SimulationContext(new ChunkStore(4), buildings, cities, null, loans, 4096, 0);
    }

    @Test
    void externalLoanRateScalesWithDifficulty() {
        CityRegistry easy = new CityRegistry(Difficulty.EASY);
        int easyCity = easy.create("Easyville", 0, 0, 0);
        LoanRegistry easyLoans = new LoanRegistry();
        new RequestExternalLoanCommand(easyCity, 1000.0).apply(ctxFor(easy, new BuildingRegistry(), easyLoans));

        CityRegistry hard = new CityRegistry(Difficulty.HARD);
        int hardCity = hard.create("Hardville", 0, 0, 0);
        LoanRegistry hardLoans = new LoanRegistry();
        new RequestExternalLoanCommand(hardCity, 1000.0).apply(ctxFor(hard, new BuildingRegistry(), hardLoans));

        double easyRate = easyLoans.interestRatePerAccrual(1);
        double hardRate = hardLoans.interestRatePerAccrual(1);

        assertEquals(RequestExternalLoanCommand.INTEREST_RATE_PER_ACCRUAL * Difficulty.EASY.loanInterestRateMultiplier, easyRate, 1e-9);
        assertEquals(RequestExternalLoanCommand.INTEREST_RATE_PER_ACCRUAL * Difficulty.HARD.loanInterestRateMultiplier, hardRate, 1e-9);
        assertTrue(hardRate > easyRate, "credit should be more expensive on Hard than on Easy");
    }

    @Test
    void cityLoanRateScalesWithDifficultyToo() {
        CityRegistry easy = new CityRegistry(Difficulty.EASY);
        int easyBorrower = easy.create("EasyBorrower", 0, 0, 0);
        int easyLender = easy.create("EasyLender", 100, 100, 0);
        BuildingRegistry easyBuildings = new BuildingRegistry();
        easyBuildings.create(BuildingType.CENTRAL_BANK, 100, 100, easyLender);
        LoanRegistry easyLoans = new LoanRegistry();
        new RequestCityLoanCommand(easyBorrower, easyLender, 1000.0).apply(ctxFor(easy, easyBuildings, easyLoans));

        CityRegistry hard = new CityRegistry(Difficulty.HARD);
        int hardBorrower = hard.create("HardBorrower", 0, 0, 0);
        int hardLender = hard.create("HardLender", 100, 100, 0);
        BuildingRegistry hardBuildings = new BuildingRegistry();
        hardBuildings.create(BuildingType.CENTRAL_BANK, 100, 100, hardLender);
        LoanRegistry hardLoans = new LoanRegistry();
        new RequestCityLoanCommand(hardBorrower, hardLender, 1000.0).apply(ctxFor(hard, hardBuildings, hardLoans));

        assertTrue(hardLoans.interestRatePerAccrual(1) > easyLoans.interestRatePerAccrual(1),
            "inter-city credit should also be costlier on Hard than on Easy");
    }
}
