package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.population.BuildingType;

import java.io.DataInput;
import java.io.IOException;

/** Water tier 3 — the highest-capacity, most expensive, latest-unlocked water source. */
public final class BuildDesalinationPlantCommand extends AbstractPlaceUtilityBuildingCommand {

    public BuildDesalinationPlantCommand(int tileX, int tileY) {
        super(tileX, tileY);
    }

    @Override
    public int typeId() {
        return CityCommandTypeIds.BUILD_DESALINATION_PLANT;
    }

    @Override
    byte buildingType() {
        return BuildingType.DESALINATION_PLANT;
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public BuildDesalinationPlantCommand decode(DataInput in) throws IOException {
            return new BuildDesalinationPlantCommand(in.readInt(), in.readInt());
        }
    };
}
