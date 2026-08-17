package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.population.BuildingType;

import java.io.DataInput;
import java.io.IOException;

/** Electricity tier 2 — unlocked once the city is populous enough (see {@code BuildingEconomics}). */
public final class BuildHydroelectricPlantCommand extends AbstractPlaceUtilityBuildingCommand {

    public BuildHydroelectricPlantCommand(int tileX, int tileY) {
        super(tileX, tileY);
    }

    @Override
    public int typeId() {
        return CityCommandTypeIds.BUILD_HYDROELECTRIC_PLANT;
    }

    @Override
    byte buildingType() {
        return BuildingType.POWER_PLANT_HYDRO;
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public BuildHydroelectricPlantCommand decode(DataInput in) throws IOException {
            return new BuildHydroelectricPlantCommand(in.readInt(), in.readInt());
        }
    };
}
