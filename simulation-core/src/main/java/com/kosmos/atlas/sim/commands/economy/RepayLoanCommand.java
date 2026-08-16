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
 * Repays part or all of an outstanding loan. The payment is always capped to the loan's remaining
 * {@link LoanRegistry#balance} — overpaying is not possible, the extra is simply not taken. Money
 * always leaves the borrower's treasury; it only lands in a lender's treasury for
 * {@link LoanLenderType#CITY} loans — an {@link LoanLenderType#EXTERNAL_MARKET} repayment leaves
 * the simulated economy entirely, same as {@code MarketSystem}'s import payments.
 */
public final class RepayLoanCommand extends Command {

    private final int loanId;
    private final double amount;

    public RepayLoanCommand(int loanId, double amount) {
        this.loanId = loanId;
        this.amount = amount;
    }

    @Override
    public int typeId() {
        return EconomyCommandTypeIds.REPAY_LOAN;
    }

    @Override
    public CommandResult apply(SimulationContext ctx) {
        LoanRegistry loans = ctx.requireLoans();
        if (!loans.isActive(loanId)) {
            return CommandResult.REJECTED_LOAN_NOT_FOUND;
        }
        if (!(amount > 0.0)) {
            return CommandResult.REJECTED_INVALID_LOAN_AMOUNT;
        }

        CityRegistry cities = ctx.requireCities();
        double payment = Math.min(amount, loans.balance(loanId));
        int borrowerCityId = loans.borrowerCityId(loanId);
        if (cities.isActive(borrowerCityId)) {
            cities.finance(borrowerCityId).adjustTreasury(-payment);
        }
        if (loans.lenderType(loanId) == LoanLenderType.CITY) {
            int lenderCityId = loans.lenderCityId(loanId);
            if (cities.isActive(lenderCityId)) {
                cities.finance(lenderCityId).adjustTreasury(payment);
            }
        }
        loans.applyRepayment(loanId, payment);
        return CommandResult.ACCEPTED;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(loanId);
        out.writeDouble(amount);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public RepayLoanCommand decode(DataInput in) throws IOException {
            return new RepayLoanCommand(in.readInt(), in.readDouble());
        }
    };
}
