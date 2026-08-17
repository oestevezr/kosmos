package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.Command;
import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.UTFDataFormatException;

/**
 * "Choose where civilization begins" (spec §8) — the very first command a fresh world can accept.
 * Every other building-creating command requires an existing city to attribute itself to (see
 * {@code AbstractPlaceUtilityBuildingCommand}, {@code BuildProductionBuildingCommand},
 * {@code PopulationSystem}), so founding the first city is the mandatory opening move, not one
 * option among many (spec §9's "player's first meaningful action is not 'manage a city'; it is:
 * choose where civilization begins").
 *
 * <p>Also places a {@code BuildingType.CITY_HALL} at the founding tile, free of charge — City Hall
 * was conceptually redundant as a player-purchasable building (this command already *is* the
 * founding act), so it's the physical marker this command leaves behind instead of a separate,
 * decorative construction command.
 */
public final class FoundCityCommand extends Command {

    private static final int MAX_NAME_LENGTH = 32;

    private final int tileX;
    private final int tileY;
    private final String name;

    public FoundCityCommand(int tileX, int tileY, String name) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.name = name;
    }

    @Override
    public int typeId() {
        return CityCommandTypeIds.FOUND_CITY;
    }

    @Override
    public CommandResult apply(SimulationContext ctx) {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return CommandResult.REJECTED_INVALID_TERRAIN;
        }
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
        int nearest = cities.nearestCity(tileX, tileY);
        if (nearest >= 0 && cities.distanceTiles(nearest, tileX, tileY) < CityRegistry.MIN_FOUNDING_DISTANCE_TILES) {
            return CommandResult.REJECTED_TOO_CLOSE_TO_ANOTHER_CITY;
        }

        int newCityId = cities.create(name, tileX, tileY, ctx.currentTick());

        // A founded city gets a City Hall at its founding tile for free — it marks the city on the
        // map and gives City Hall a real role instead of being purely decorative (spec's civic
        // service system); never built via BuildCivicBuildingCommand directly (see BuildingType).
        BuildingRegistry buildings = ctx.requireBuildings();
        int cityHallId = buildings.create(BuildingType.CITY_HALL, tileX, tileY, newCityId);
        chunk.buildingId[idx] = cityHallId;
        chunk.markDirty();

        return CommandResult.ACCEPTED;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(tileX);
        out.writeInt(tileY);
        out.writeUTF(name);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public FoundCityCommand decode(DataInput in) throws IOException {
            int x = in.readInt();
            int y = in.readInt();
            String decodedName;
            try {
                decodedName = in.readUTF();
            } catch (UTFDataFormatException e) {
                throw new IOException("FoundCityCommand: malformed city name in journal", e);
            }
            return new FoundCityCommand(x, y, decodedName);
        }
    };
}
