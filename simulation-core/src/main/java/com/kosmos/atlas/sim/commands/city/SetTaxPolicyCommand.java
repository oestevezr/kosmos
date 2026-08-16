package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.Command;
import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Sets the tax rate for one zone sector, for one specific city (spec §38 lists
 * {@code SetTaxPolicyCommand} explicitly; spec §9's multiple player-founded cities means tax
 * policy is a per-city choice, not a world-wide one — a boom-town and a struggling mining town
 * can set very different rates).
 */
public final class SetTaxPolicyCommand extends Command {

    private final int cityId;
    private final byte zoneType;
    private final double rate;

    public SetTaxPolicyCommand(int cityId, byte zoneType, double rate) {
        this.cityId = cityId;
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
        CityRegistry cities = ctx.requireCities();
        if (!cities.isActive(cityId)) {
            return CommandResult.REJECTED_NO_CITY_FOUNDED;
        }
        cities.finance(cityId).setTaxRate(zoneType, rate);
        return CommandResult.ACCEPTED;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(cityId);
        out.writeByte(zoneType);
        out.writeDouble(rate);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public SetTaxPolicyCommand decode(DataInput in) throws IOException {
            return new SetTaxPolicyCommand(in.readInt(), in.readByte(), in.readDouble());
        }
    };
}
