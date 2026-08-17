package com.kosmos.atlas.sim.commands.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.Command;
import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.economy.BuildingEconomics;
import com.kosmos.atlas.sim.economy.GoodType;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Places a Bus Depot ("central") — the administrative origin point of bus routes
 * ({@code docs/roadmap.md}'s bus-route mechanic, MVP 0.6). Unlike a Bus Stop, a depot is
 * <b>not</b> a {@code RegionalGraph} node — it never appears in a route's stop sequence, it only
 * owns a capacity of simultaneous routes ({@link BuildingEconomics#capacity}, read by
 * {@code CreateBusRouteCommand}). No coastal or population gate, like Trade Depot/Port.
 */
public final class BuildBusDepotCommand extends Command {

    private final int tileX;
    private final int tileY;

    public BuildBusDepotCommand(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public int typeId() {
        return EconomyCommandTypeIds.BUILD_BUS_DEPOT;
    }

    @Override
    public CommandResult apply(SimulationContext ctx) {
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
        byte terrain = chunk.terrainType[idx];
        if (terrain == WorldConstants.TERRAIN_DEEP_WATER || terrain == WorldConstants.TERRAIN_SHALLOW_WATER) {
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

        double cost = BuildingEconomics.constructionCost(BuildingType.BUS_DEPOT);
        if (cities.finance(cityId).treasuryBalance() < cost) {
            return CommandResult.REJECTED_INSUFFICIENT_FUNDS;
        }
        cities.finance(cityId).adjustTreasury(-cost);

        BuildingRegistry buildings = ctx.requireBuildings();
        int id = buildings.create(BuildingType.BUS_DEPOT, tileX, tileY, cityId, GoodType.NONE, 0, GoodType.NONE, 0);
        chunk.buildingId[idx] = id;
        chunk.zoneType[idx] = WorldConstants.ZONE_NONE;
        chunk.markDirty();

        return CommandResult.ACCEPTED;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(tileX);
        out.writeInt(tileY);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public BuildBusDepotCommand decode(DataInput in) throws IOException {
            return new BuildBusDepotCommand(in.readInt(), in.readInt());
        }
    };
}
