package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.population.BuildingType;

import java.io.DataInput;
import java.io.IOException;

public final class BuildPowerPlantCommand extends AbstractPlaceUtilityBuildingCommand {

    public BuildPowerPlantCommand(int tileX, int tileY) {
        super(tileX, tileY);
    }

    @Override
    public int typeId() {
        return CityCommandTypeIds.BUILD_POWER_PLANT;
    }

    @Override
    byte buildingType() {
        return BuildingType.POWER_PLANT;
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public BuildPowerPlantCommand decode(DataInput in) throws IOException {
            return new BuildPowerPlantCommand(in.readInt(), in.readInt());
        }
    };
}
