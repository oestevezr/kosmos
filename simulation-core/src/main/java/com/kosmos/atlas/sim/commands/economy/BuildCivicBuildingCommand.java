package com.kosmos.atlas.sim.commands.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.Command;
import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.economy.BuildingEconomics;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Places one of Fase 2's prosperity/luxury civic services — Clinic/Hospital, Volunteer Fire
 * Brigade/Fire Station, Waste Collection/Incinerator, Cemetery, Park, or Museum
 * ({@code docs/roadmap.md}'s "Servicios cívicos por tiers") — as a single generic command instead
 * of nine near-identical classes, the same data-driven pattern
 * {@code BuildProductionBuildingCommand} already established for Fase 3's production chain.
 *
 * <p>Unlike {@code BuildProductionBuildingCommand}, these need no natural-resource flag — any
 * buildable land tile works. Unlike {@code AbstractPlaceUtilityBuildingCommand}'s Electricity/
 * Water sources, they never feed a capacity/demand ratio ({@code UtilitySystem} only sets their
 * coverage bit) — their effect is entirely through {@code PopulationSystem}'s satisfaction
 * ceiling. They still share the same tier-unlock and funds checks every other Fase-2 construction
 * command uses.
 */
public final class BuildCivicBuildingCommand extends Command {

    private final int tileX;
    private final int tileY;
    private final byte buildingType;

    public BuildCivicBuildingCommand(int tileX, int tileY, byte buildingType) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.buildingType = buildingType;
    }

    @Override
    public int typeId() {
        return EconomyCommandTypeIds.BUILD_CIVIC_BUILDING;
    }

    @Override
    public CommandResult apply(SimulationContext ctx) {
        if (!isKnownCivicType(buildingType)) {
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

        BuildingRegistry buildings = ctx.requireBuildings();
        long unlockPopulation = BuildingEconomics.unlockPopulation(buildingType);
        if (unlockPopulation > 0 && buildings.residentialPopulationOfCity(cityId) < unlockPopulation) {
            return CommandResult.REJECTED_SERVICE_TIER_LOCKED;
        }
        double cost = BuildingEconomics.constructionCost(buildingType);
        if (cities.finance(cityId).treasuryBalance() < cost) {
            return CommandResult.REJECTED_INSUFFICIENT_FUNDS;
        }

        cities.finance(cityId).adjustTreasury(-cost);
        int id = buildings.create(buildingType, tileX, tileY, cityId);
        chunk.buildingId[idx] = id;
        chunk.zoneType[idx] = WorldConstants.ZONE_NONE; // infrastructure is never a zoned lot
        chunk.markDirty();

        return CommandResult.ACCEPTED;
    }

    private static boolean isKnownCivicType(byte type) {
        return type == BuildingType.CLINIC || type == BuildingType.HOSPITAL
            || type == BuildingType.VOLUNTEER_FIRE_BRIGADE || type == BuildingType.FIRE_STATION
            || type == BuildingType.WASTE_COLLECTION || type == BuildingType.INCINERATOR
            || type == BuildingType.CEMETERY || type == BuildingType.PARK || type == BuildingType.MUSEUM;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(tileX);
        out.writeInt(tileY);
        out.writeByte(buildingType);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public BuildCivicBuildingCommand decode(DataInput in) throws IOException {
            return new BuildCivicBuildingCommand(in.readInt(), in.readInt(), in.readByte());
        }
    };
}
