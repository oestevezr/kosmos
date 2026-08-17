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
import com.kosmos.atlas.sim.trade.PortRegistry;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Places a Port (spec §17, MVP 0.5 — {@code docs/roadmap.md}): a higher-capacity, coastal-only
 * trade gateway. Unlike {@code BuildProductionBuildingCommand}'s {@code TRADE_DEPOT} case, a Port
 * requires "natural harbor suitability" — approximated, per the roadmap's deliberate scope
 * adjustment, as simple 4-neighbor adjacency to shallow/deep water rather than a dedicated world-gen
 * pass. It registers a {@link NodeType#PORT} node (not {@code EXTERNAL_MARKET}) so
 * {@code RegionalGraph.nearestNodeOfType} callers can still tell a Port apart from a Trade Depot,
 * and a {@link PortRegistry} row carrying its berths/cargo-capacity/customs-efficiency —
 * {@code MarketSystem} reads this row instead of the flat Trade Depot constants when trading
 * through a Port.
 */
public final class BuildPortCommand extends Command {

    // MVP defaults — spec doesn't call for player-configurable port capacity yet (docs/roadmap.md).
    public static final int DEFAULT_BERTHS = 6;
    public static final int DEFAULT_CARGO_CAPACITY_PER_TICK = 75;
    public static final int DEFAULT_CUSTOMS_EFFICIENCY_PERCENT = 50;

    private final int tileX;
    private final int tileY;

    public BuildPortCommand(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public int typeId() {
        return EconomyCommandTypeIds.BUILD_PORT;
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
        if (!isCoastal(ctx, tileX, tileY)) {
            return CommandResult.REJECTED_INVALID_TERRAIN;
        }

        CityRegistry cities = ctx.requireCities();
        int cityId = cities.nearestCity(tileX, tileY);
        if (cityId < 0) {
            return CommandResult.REJECTED_NO_CITY_FOUNDED;
        }

        double cost = BuildingEconomics.constructionCost(BuildingType.PORT);
        if (cities.finance(cityId).treasuryBalance() < cost) {
            return CommandResult.REJECTED_INSUFFICIENT_FUNDS;
        }
        cities.finance(cityId).adjustTreasury(-cost);

        BuildingRegistry buildings = ctx.requireBuildings();
        int id = buildings.create(BuildingType.PORT, tileX, tileY, cityId, GoodType.NONE, 0, GoodType.NONE, 0);
        chunk.buildingId[idx] = id;
        chunk.zoneType[idx] = WorldConstants.ZONE_NONE;
        chunk.markDirty();

        ctx.requirePorts().set(id, DEFAULT_BERTHS, DEFAULT_CARGO_CAPACITY_PER_TICK, DEFAULT_CUSTOMS_EFFICIENCY_PERCENT);

        RegionalGraph graph = ctx.requireRegionalGraph();
        graph.addNode(NodeType.PORT, tileX, tileY);

        return CommandResult.ACCEPTED;
    }

    /** A land tile is coastal if any of its 4 orthogonal neighbors is water — spec §5.2's "natural
     *  harbor suitability", approximated per {@code docs/roadmap.md}'s MVP 0.5 scope adjustment
     *  rather than a dedicated world-gen pass. An unloaded neighbor chunk is conservatively treated
     *  as non-water from that side. */
    private static boolean isCoastal(SimulationContext ctx, int tileX, int tileY) {
        int[][] neighbors = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : neighbors) {
            int nx = tileX + offset[0];
            int ny = tileY + offset[1];
            int nChunkX = Math.floorDiv(nx, WorldConstants.CHUNK_SIZE);
            int nChunkY = Math.floorDiv(ny, WorldConstants.CHUNK_SIZE);
            Chunk neighborChunk = ctx.chunkStore().get(nChunkX, nChunkY);
            if (neighborChunk == null) {
                continue;
            }
            int nIdx = Chunk.tileIndex(Math.floorMod(nx, WorldConstants.CHUNK_SIZE), Math.floorMod(ny, WorldConstants.CHUNK_SIZE));
            byte neighborTerrain = neighborChunk.terrainType[nIdx];
            if (neighborTerrain == WorldConstants.TERRAIN_DEEP_WATER || neighborTerrain == WorldConstants.TERRAIN_SHALLOW_WATER) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(tileX);
        out.writeInt(tileY);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public BuildPortCommand decode(DataInput in) throws IOException {
            return new BuildPortCommand(in.readInt(), in.readInt());
        }
    };
}
