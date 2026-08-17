package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.population.BuildingType;

import java.io.DataInput;
import java.io.IOException;

/** Water tier 2 — unlocked once the city is populous enough (see {@code BuildingEconomics}). */
public final class BuildWaterTreatmentPlantCommand extends AbstractPlaceUtilityBuildingCommand {

    public BuildWaterTreatmentPlantCommand(int tileX, int tileY) {
        super(tileX, tileY);
    }

    @Override
    public int typeId() {
        return CityCommandTypeIds.BUILD_WATER_TREATMENT_PLANT;
    }

    @Override
    byte buildingType() {
        return BuildingType.WATER_TREATMENT_PLANT;
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public BuildWaterTreatmentPlantCommand decode(DataInput in) throws IOException {
            return new BuildWaterTreatmentPlantCommand(in.readInt(), in.readInt());
        }
    };
}
