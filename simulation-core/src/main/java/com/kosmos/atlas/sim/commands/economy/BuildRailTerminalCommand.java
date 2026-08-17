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
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.trade.StationRegistry;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Places a Rail Terminal (spec §18, MVP 0.6's second slice — {@code docs/roadmap.md}): a fourth
 * cargo gateway, same {@code MarketSystem} treatment as Port/Airport, but domestic — no coastal
 * requirement (unlike Port) and no population gate (unlike Airport; only spec §19's "small town"
 * restriction applies to international airports). Registers a {@link NodeType#STATION} node and a
 * {@link StationRegistry} row carrying its platforms/cargo-capacity — {@code MarketSystem} reads
 * this row instead of the flat Trade Depot constants, with no customs bonus (domestic trade).
 */
public final class BuildRailTerminalCommand extends Command {

    public static final int DEFAULT_PLATFORMS = 5;
    public static final int DEFAULT_CARGO_CAPACITY_PER_TICK = 60;

    private final int tileX;
    private final int tileY;

    public BuildRailTerminalCommand(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public int typeId() {
        return EconomyCommandTypeIds.BUILD_RAIL_TERMINAL;
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

        double cost = BuildingEconomics.constructionCost(BuildingType.RAIL_TERMINAL);
        if (cities.finance(cityId).treasuryBalance() < cost) {
            return CommandResult.REJECTED_INSUFFICIENT_FUNDS;
        }
        cities.finance(cityId).adjustTreasury(-cost);

        BuildingRegistry buildings = ctx.requireBuildings();
        int id = buildings.create(BuildingType.RAIL_TERMINAL, tileX, tileY, cityId, GoodType.NONE, 0, GoodType.NONE, 0);
        chunk.buildingId[idx] = id;
        chunk.zoneType[idx] = WorldConstants.ZONE_NONE;
        chunk.markDirty();

        ctx.requireStations().set(id, DEFAULT_PLATFORMS, DEFAULT_CARGO_CAPACITY_PER_TICK);

        RegionalGraph graph = ctx.requireRegionalGraph();
        graph.addNode(NodeType.STATION, tileX, tileY);

        return CommandResult.ACCEPTED;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(tileX);
        out.writeInt(tileY);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public BuildRailTerminalCommand decode(DataInput in) throws IOException {
            return new BuildRailTerminalCommand(in.readInt(), in.readInt());
        }
    };
}
