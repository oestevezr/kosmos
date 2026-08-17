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
import com.kosmos.atlas.sim.trade.AirportRegistry;
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Places an Airport (spec §19, MVP 0.6's first slice — {@code docs/roadmap.md}): a cargo-only
 * gateway this pass, same role as {@code BuildPortCommand}'s Port but landlocked (no coastal
 * suitability check) and gated behind city population instead — spec §19's "a small town should
 * not automatically support an international airport", the same tier-unlock pattern
 * {@code BuildCivicBuildingCommand} already uses. Registers a {@link NodeType#AIRPORT} node and an
 * {@link AirportRegistry} row carrying its gates/cargo-capacity/customs-efficiency — {@code
 * MarketSystem} reads this row instead of the flat Trade Depot constants when trading through an
 * Airport. Passenger capacity is deliberately not modeled yet (see {@code docs/roadmap.md}).
 */
public final class BuildAirportCommand extends Command {

    // MVP defaults — spec doesn't call for player-configurable airport capacity yet.
    public static final int DEFAULT_GATES = 4;
    public static final int DEFAULT_CARGO_CAPACITY_PER_TICK = 50;
    public static final int DEFAULT_CUSTOMS_EFFICIENCY_PERCENT = 60;

    private final int tileX;
    private final int tileY;

    public BuildAirportCommand(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public int typeId() {
        return EconomyCommandTypeIds.BUILD_AIRPORT;
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

        BuildingRegistry buildings = ctx.requireBuildings();
        long unlockPopulation = BuildingEconomics.unlockPopulation(BuildingType.AIRPORT);
        if (buildings.residentialPopulationOfCity(cityId) < unlockPopulation) {
            return CommandResult.REJECTED_SERVICE_TIER_LOCKED;
        }

        double cost = BuildingEconomics.constructionCost(BuildingType.AIRPORT);
        if (cities.finance(cityId).treasuryBalance() < cost) {
            return CommandResult.REJECTED_INSUFFICIENT_FUNDS;
        }
        cities.finance(cityId).adjustTreasury(-cost);

        int id = buildings.create(BuildingType.AIRPORT, tileX, tileY, cityId, GoodType.NONE, 0, GoodType.NONE, 0);
        chunk.buildingId[idx] = id;
        chunk.zoneType[idx] = WorldConstants.ZONE_NONE;
        chunk.markDirty();

        ctx.requireAirports().set(id, DEFAULT_GATES, DEFAULT_CARGO_CAPACITY_PER_TICK, DEFAULT_CUSTOMS_EFFICIENCY_PERCENT);

        RegionalGraph graph = ctx.requireRegionalGraph();
        graph.addNode(NodeType.AIRPORT, tileX, tileY);

        return CommandResult.ACCEPTED;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(tileX);
        out.writeInt(tileY);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public BuildAirportCommand decode(DataInput in) throws IOException {
            return new BuildAirportCommand(in.readInt(), in.readInt());
        }
    };
}
