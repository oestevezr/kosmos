package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.commands.Command;
import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.economy.GovernmentFinance;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Sets the tax rate for one zone sector (spec §38 lists {@code SetTaxPolicyCommand} explicitly;
 * spec §26 requires the simulation to stay deterministic under player policy changes, so this
 * goes through the same command/journal pipeline as every other mutation).
 */
public final class SetTaxPolicyCommand extends Command {

    private final byte zoneType;
    private final double rate;

    public SetTaxPolicyCommand(byte zoneType, double rate) {
        this.zoneType = zoneType;
        this.rate = rate;
    }

    @Override
    public int typeId() {
        return CityCommandTypeIds.SET_TAX_POLICY;
    }

    @Override
    public CommandResult apply(SimulationContext ctx) {
        if (zoneType < WorldConstants.ZONE_RESIDENTIAL || zoneType > WorldConstants.ZONE_INDUSTRIAL) {
            return CommandResult.REJECTED_INVALID_TERRAIN;
        }
        if (rate < 0.0 || rate > 1.0) {
            return CommandResult.REJECTED_INVALID_TERRAIN;
        }
        GovernmentFinance finance = ctx.requireFinance();
        finance.setTaxRate(zoneType, rate);
        return CommandResult.ACCEPTED;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeByte(zoneType);
        out.writeDouble(rate);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public SetTaxPolicyCommand decode(DataInput in) throws IOException {
            return new SetTaxPolicyCommand(in.readByte(), in.readDouble());
        }
    };
}
