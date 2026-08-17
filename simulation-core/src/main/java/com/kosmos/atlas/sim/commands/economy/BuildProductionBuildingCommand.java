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
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Places one of the MVP 0.3 production-chain buildings — Farm, Lumber Camp, Mine, Quarry, Steel
 * Mill, or Trade Depot (spec §7, §21, §29) — as a single generic command instead of six
 * near-identical classes, following the shared-placement pattern already established by
 * {@code AbstractPlaceUtilityBuildingCommand} in Fase 2, just data-driven by building type here
 * since the output/input good and resource requirement differ per type rather than being fixed.
 *
 * <p>Extraction buildings (Farm/Lumber Camp/Quarry) require the matching natural resource flag
 * already on the target tile from world generation (spec §7: "geographic resource access
 * influences how quickly and cheaply the player's civilization can expand") — you cannot farm a
 * tile with no fertility bonus, or quarry one with no stone. {@code Mine} is the one type whose
 * output depends on *which* mineral flag the tile has (iron -> Ore, coal -> Fuel); processing
 * buildings (Steel Mill) and the Trade Depot need only buildable land.
 */
public final class BuildProductionBuildingCommand extends Command {

    // Units per tick at full capacity — spec §20 asks for an understandable economy, not
    // realistic tonnages, so these are round numbers picked for visible, testable dynamics.
    private static final int FARM_OUTPUT_RATE = 10;
    private static final int LUMBER_CAMP_OUTPUT_RATE = 10;
    private static final int MINE_OUTPUT_RATE = 8;
    private static final int QUARRY_OUTPUT_RATE = 8;
    private static final int STEEL_MILL_OUTPUT_RATE = 6;
    private static final int STEEL_MILL_INPUT_RATE = 8; // more ore in than steel out — refining loss

    private final int tileX;
    private final int tileY;
    private final byte buildingType;

    public BuildProductionBuildingCommand(int tileX, int tileY, byte buildingType) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.buildingType = buildingType;
    }

    @Override
    public int typeId() {
        return EconomyCommandTypeIds.BUILD_PRODUCTION_BUILDING;
    }

    @Override
    public CommandResult apply(SimulationContext ctx) {
        if (!isKnownProductionType(buildingType)) {
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

        int resourceFlags = chunk.resourceFlags[idx];
        byte outputGood;
        byte inputGood = GoodType.NONE;
        int outputRate;
        int inputRate = 0;

        switch (buildingType) {
            case BuildingType.FARM -> {
                if ((resourceFlags & WorldConstants.RESOURCE_FERTILE_BONUS) == 0) {
                    return CommandResult.REJECTED_INVALID_TERRAIN;
                }
                outputGood = GoodType.FOOD;
                outputRate = FARM_OUTPUT_RATE;
            }
            case BuildingType.LUMBER_CAMP -> {
                if ((resourceFlags & WorldConstants.RESOURCE_TIMBER) == 0) {
                    return CommandResult.REJECTED_INVALID_TERRAIN;
                }
                outputGood = GoodType.TIMBER;
                outputRate = LUMBER_CAMP_OUTPUT_RATE;
            }
            case BuildingType.MINE -> {
                if ((resourceFlags & WorldConstants.RESOURCE_IRON) != 0) {
                    outputGood = GoodType.ORE;
                } else if ((resourceFlags & WorldConstants.RESOURCE_COAL) != 0) {
                    outputGood = GoodType.FUEL;
                } else {
                    return CommandResult.REJECTED_INVALID_TERRAIN;
                }
                outputRate = MINE_OUTPUT_RATE;
            }
            case BuildingType.QUARRY -> {
                if ((resourceFlags & WorldConstants.RESOURCE_STONE) == 0) {
                    return CommandResult.REJECTED_INVALID_TERRAIN;
                }
                outputGood = GoodType.CONSTRUCTION_MATERIALS;
                outputRate = QUARRY_OUTPUT_RATE;
            }
            case BuildingType.STEEL_MILL -> {
                outputGood = GoodType.STEEL;
                outputRate = STEEL_MILL_OUTPUT_RATE;
                inputGood = GoodType.ORE;
                inputRate = STEEL_MILL_INPUT_RATE;
            }
            case BuildingType.TRADE_DEPOT -> {
                outputGood = GoodType.NONE;
                outputRate = 0;
            }
            default -> throw new IllegalStateException("Unreachable: " + buildingType);
        }

        double cost = BuildingEconomics.constructionCost(buildingType);
        if (cities.finance(cityId).treasuryBalance() < cost) {
            return CommandResult.REJECTED_INSUFFICIENT_FUNDS;
        }
        cities.finance(cityId).adjustTreasury(-cost);

        BuildingRegistry buildings = ctx.requireBuildings();
        int id = buildings.create(buildingType, tileX, tileY, cityId, outputGood, outputRate, inputGood, inputRate);
        chunk.buildingId[idx] = id;
        chunk.zoneType[idx] = WorldConstants.ZONE_NONE;
        chunk.markDirty();

        if (buildingType == BuildingType.TRADE_DEPOT) {
            RegionalGraph graph = ctx.requireRegionalGraph();
            graph.addNode(NodeType.EXTERNAL_MARKET, tileX, tileY);
        }

        return CommandResult.ACCEPTED;
    }

    private static boolean isKnownProductionType(byte type) {
        return type == BuildingType.FARM || type == BuildingType.LUMBER_CAMP || type == BuildingType.MINE
            || type == BuildingType.QUARRY || type == BuildingType.STEEL_MILL || type == BuildingType.TRADE_DEPOT;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(tileX);
        out.writeInt(tileY);
        out.writeByte(buildingType);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public BuildProductionBuildingCommand decode(DataInput in) throws IOException {
            return new BuildProductionBuildingCommand(in.readInt(), in.readInt(), in.readByte());
        }
    };
}
