package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.commands.Command;
import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Places a dirt road on one tile — the first rung of the infrastructure progression ladder
 * (spec §12: "Trail/footpath -> Dirt road -> ..."). Roads are what makes a zoned tile eligible to
 * grow a building (spec §9: population only appears once access/shelter/water/employment exist).
 */
public final class BuildRoadCommand extends Command {

    private final int tileX;
    private final int tileY;

    public BuildRoadCommand(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public int typeId() {
        return CityCommandTypeIds.BUILD_ROAD;
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
        if (!TileBuildability.isLand(chunk.terrainType[idx])) {
            return CommandResult.REJECTED_INVALID_TERRAIN;
        }
        if (chunk.buildingId[idx] != WorldConstants.NO_BUILDING) {
            return CommandResult.REJECTED_TILE_OCCUPIED;
        }
        if (chunk.roadType[idx] != WorldConstants.ROAD_NONE) {
            return CommandResult.REJECTED_TILE_OCCUPIED;
        }
        chunk.roadType[idx] = WorldConstants.ROAD_DIRT;
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
        public BuildRoadCommand decode(DataInput in) throws IOException {
            return new BuildRoadCommand(in.readInt(), in.readInt());
        }
    };
}
