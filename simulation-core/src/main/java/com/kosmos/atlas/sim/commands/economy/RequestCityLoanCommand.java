package com.kosmos.atlas.sim.commands.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.Command;
import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.economy.GovernmentFinance;
import com.kosmos.atlas.sim.economy.LoanLenderType;
import com.kosmos.atlas.sim.economy.LoanRegistry;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Borrows from another player-founded city rather than the external market — the user's requested
 * "para ciudades/paises fundadas dependería de la simulación, ciudades con prosperidad pueden
 * ofrecer créditos" rule: a lending city must hold at least {@link #PROSPERITY_MIN_TREASURY} and
 * must still retain at least {@link #MIN_RESERVE_AFTER_LENDING} after the loan goes out, or the
 * request is rejected outright — a struggling city can never be forced into lending. The interest
 * rate the borrower gets scales down as the lender's treasury grows past the prosperity floor
 * (richer lenders can afford to undercut the external market's fixed high rate), computed by
 * {@link #interestRateFor(double)}.
 */
public final class RequestCityLoanCommand extends Command {

    /** A city below this treasury balance cannot lend at all, regardless of the requested amount. */
    public static final double PROSPERITY_MIN_TREASURY = 5_000.0;
    /** A lending city must retain at least this much after the loan goes out. */
    public static final double MIN_RESERVE_AFTER_LENDING = 2_000.0;
    /** Rate charged by a lender sitting right at {@link #PROSPERITY_MIN_TREASURY}. */
    public static final double BASE_INTEREST_RATE_PER_ACCRUAL = 0.008;
    /** Rate floor for the most prosperous lenders — still above zero; lending isn't free. */
    public static final double MIN_INTEREST_RATE_PER_ACCRUAL = 0.002;
    /** Treasury balance at which a lender's rate bottoms out at {@link #MIN_INTEREST_RATE_PER_ACCRUAL}. */
    public static final double PROSPERITY_RATE_FLOOR_TREASURY = 50_000.0;

    private final int borrowerCityId;
    private final int lenderCityId;
    private final double amount;

    public RequestCityLoanCommand(int borrowerCityId, int lenderCityId, double amount) {
        this.borrowerCityId = borrowerCityId;
        this.lenderCityId = lenderCityId;
        this.amount = amount;
    }

    /** Lower for a richer lender, floored at {@link #MIN_INTEREST_RATE_PER_ACCRUAL} once past {@link #PROSPERITY_RATE_FLOOR_TREASURY}. */
    public static double interestRateFor(double lenderTreasuryBalance) {
        double span = PROSPERITY_RATE_FLOOR_TREASURY - PROSPERITY_MIN_TREASURY;
        double progress = (lenderTreasuryBalance - PROSPERITY_MIN_TREASURY) / span;
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        return BASE_INTEREST_RATE_PER_ACCRUAL - clamped * (BASE_INTEREST_RATE_PER_ACCRUAL - MIN_INTEREST_RATE_PER_ACCRUAL);
    }

    @Override
    public int typeId() {
        return EconomyCommandTypeIds.REQUEST_CITY_LOAN;
    }

    @Override
    public CommandResult apply(SimulationContext ctx) {
        CityRegistry cities = ctx.requireCities();
        if (!cities.isActive(borrowerCityId) || !cities.isActive(lenderCityId)) {
            return CommandResult.REJECTED_NO_CITY_FOUNDED;
        }
        if (borrowerCityId == lenderCityId) {
            return CommandResult.REJECTED_SAME_CITY_LOAN;
        }
        if (!(amount > 0.0)) {
            return CommandResult.REJECTED_INVALID_LOAN_AMOUNT;
        }

        GovernmentFinance lenderFinance = cities.finance(lenderCityId);
        double lenderTreasury = lenderFinance.treasuryBalance();
        if (lenderTreasury < PROSPERITY_MIN_TREASURY || (lenderTreasury - amount) < MIN_RESERVE_AFTER_LENDING) {
            return CommandResult.REJECTED_LENDER_NOT_PROSPEROUS;
        }

        double rate = interestRateFor(lenderTreasury);
        lenderFinance.adjustTreasury(-amount);
        cities.finance(borrowerCityId).adjustTreasury(amount);

        LoanRegistry loans = ctx.requireLoans();
        loans.create(LoanLenderType.CITY, borrowerCityId, lenderCityId, amount, rate, ctx.currentTick());
        return CommandResult.ACCEPTED;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(borrowerCityId);
        out.writeInt(lenderCityId);
        out.writeDouble(amount);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public RequestCityLoanCommand decode(DataInput in) throws IOException {
            return new RequestCityLoanCommand(in.readInt(), in.readInt(), in.readDouble());
        }
    };
}
