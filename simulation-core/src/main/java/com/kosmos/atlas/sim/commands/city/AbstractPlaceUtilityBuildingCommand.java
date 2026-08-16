package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.Command;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataOutput;
import java.io.IOException;

/**
 * Shared placement logic for the two Fase 2 utility-source buildings (spec §24: electricity and
 * water as graph-based networks). Placing one of these creates a {@code BuildingRegistry} entry
 * of the corresponding type; {@code UtilitySystem} discovers power/water sources by scanning for
 * these building types rather than the command hard-wiring any network update itself — commands
 * only ever change authoritative state, never directly drive derived systems (spec §38, §41).
 */
abstract class AbstractPlaceUtilityBuildingCommand extends Command {

    final int tileX;
    final int tileY;

    AbstractPlaceUtilityBuildingCommand(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    abstract byte buildingType();

    @Override
    public final CommandResult apply(SimulationContext ctx) {
        if (!ctx.inBounds(tileX, tileY)) {
            return CommandResult.REJECTED_OUT_OF_BOUNDS;
        }
        int chunkX = Math.floorDiv(tileX, WorldConstants.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, WorldConstants.CHUNK_SIZE);
        Chunk chunk = ctx.chunkStore().get(chunkX, chunkY);
        if (chunk == null) {
            return CommandResult.REJECTED_UNKNOWN_CHUNK;
        }
        int idx = Chunk.tileIndex(Math.floorMod(tileX, WorldConstants.CHUNK_SIZE), Math.floorMod(tileY, WorldConstants.CHUNK_SIZE));
        if (!TileBuildability.isLand(chunk.terrainType[idx])) {
            return CommandResult.REJECTED_INVALID_TERRAIN;
        }
        if (chunk.buildingId[idx] != WorldConstants.NO_BUILDING) {
            return CommandResult.REJECTED_TILE_OCCUPIED;
        }

        CityRegistry cities = ctx.requireCities();
        int cityId = cities.nearestCity(tileX, tileY);
        if (cityId < 0) {
            return CommandResult.REJECTED_NO_CITY_FOUNDED;
        }

        BuildingRegistry buildings = ctx.requireBuildings();
        int id = buildings.create(buildingType(), tileX, tileY, cityId);
        chunk.buildingId[idx] = id;
        chunk.zoneType[idx] = WorldConstants.ZONE_NONE; // infrastructure is never a zoned lot
        chunk.markDirty();
        return CommandResult.ACCEPTED;
    }

    @Override
    public final void writePayload(DataOutput out) throws IOException {
        out.writeInt(tileX);
        out.writeInt(tileY);
    }
}
