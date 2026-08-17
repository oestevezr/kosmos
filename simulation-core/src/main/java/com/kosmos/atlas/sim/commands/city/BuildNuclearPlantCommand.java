package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.population.BuildingType;

import java.io.DataInput;
import java.io.IOException;

/** Electricity tier 3 — the highest-capacity, most expensive, latest-unlocked power source. */
public final class BuildNuclearPlantCommand extends AbstractPlaceUtilityBuildingCommand {

    public BuildNuclearPlantCommand(int tileX, int tileY) {
        super(tileX, tileY);
    }

    @Override
    public int typeId() {
        return CityCommandTypeIds.BUILD_NUCLEAR_PLANT;
    }

    @Override
    byte buildingType() {
        return BuildingType.POWER_PLANT_NUCLEAR;
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public BuildNuclearPlantCommand decode(DataInput in) throws IOException {
            return new BuildNuclearPlantCommand(in.readInt(), in.readInt());
        }
    };
}
