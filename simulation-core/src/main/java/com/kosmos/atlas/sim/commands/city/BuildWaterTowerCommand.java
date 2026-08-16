package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.population.BuildingType;

import java.io.DataInput;
import java.io.IOException;

public final class BuildWaterTowerCommand extends AbstractPlaceUtilityBuildingCommand {

    public BuildWaterTowerCommand(int tileX, int tileY) {
        super(tileX, tileY);
    }

    @Override
    public int typeId() {
        return CityCommandTypeIds.BUILD_WATER_TOWER;
    }

    @Override
    byte buildingType() {
        return BuildingType.WATER_TOWER;
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public BuildWaterTowerCommand decode(DataInput in) throws IOException {
            return new BuildWaterTowerCommand(in.readInt(), in.readInt());
        }
    };
}
