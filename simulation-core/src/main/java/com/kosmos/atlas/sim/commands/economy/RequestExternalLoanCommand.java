package com.kosmos.atlas.sim.commands.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.Command;
import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.economy.LoanLenderType;
import com.kosmos.atlas.sim.economy.LoanRegistry;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Borrows from the simulated external market — always available regardless of the borrowing
 * city's prosperity, but at a fixed high interest rate (the user's requested loan design: "el
 * mercado externo simulado serían intereses altos"). No eligibility check beyond an active,
 * founded city and a sane amount — this is deliberately the lender of last resort.
 */
public final class RequestExternalLoanCommand extends Command {

    /** High and fixed — the external market doesn't care about the borrower's prosperity. */
    public static final double INTEREST_RATE_PER_ACCRUAL = 0.02;
    public static final double MAX_AMOUNT = 50_000.0;

    private final int borrowerCityId;
    private final double amount;

    public RequestExternalLoanCommand(int borrowerCityId, double amount) {
        this.borrowerCityId = borrowerCityId;
        this.amount = amount;
    }

    @Override
    public int typeId() {
        return EconomyCommandTypeIds.REQUEST_EXTERNAL_LOAN;
    }

    @Override
    public CommandResult apply(SimulationContext ctx) {
        CityRegistry cities = ctx.requireCities();
        if (!cities.isActive(borrowerCityId)) {
            return CommandResult.REJECTED_NO_CITY_FOUNDED;
        }
        if (!(amount > 0.0) || amount > MAX_AMOUNT) {
            return CommandResult.REJECTED_INVALID_LOAN_AMOUNT;
        }

        LoanRegistry loans = ctx.requireLoans();
        cities.finance(borrowerCityId).adjustTreasury(amount);
        loans.create(LoanLenderType.EXTERNAL_MARKET, borrowerCityId, 0, amount, INTEREST_RATE_PER_ACCRUAL, ctx.currentTick());
        return CommandResult.ACCEPTED;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(borrowerCityId);
        out.writeDouble(amount);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public RequestExternalLoanCommand decode(DataInput in) throws IOException {
            return new RequestExternalLoanCommand(in.readInt(), in.readDouble());
        }
    };
}
