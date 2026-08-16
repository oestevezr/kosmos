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
 * Designates a tile as residential/commercial/industrial, or clears its zoning
 * ({@link WorldConstants#ZONE_NONE}). Zoning a tile does not build anything by itself — it only
 * makes the tile eligible for {@code PopulationSystem} to grow a building there once road access
 * and utility coverage exist (spec §9, §23). This mirrors the classic "zone tool" UX and keeps
 * population growth an emergent consequence of infrastructure, not a direct command effect.
 */
public final class ZoneCommand extends Command {

    private final int tileX;
    private final int tileY;
    private final byte zoneType;

    public ZoneCommand(int tileX, int tileY, byte zoneType) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.zoneType = zoneType;
    }

    @Override
    public int typeId() {
        return CityCommandTypeIds.ZONE;
    }

    @Override
    public CommandResult apply(SimulationContext ctx) {
        if (zoneType < WorldConstants.ZONE_NONE || zoneType > WorldConstants.ZONE_INDUSTRIAL) {
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
            return CommandResult.REJECTED_TILE_OCCUPIED; // re-zoning an occupied tile requires demolishing first
        }
        chunk.zoneType[idx] = zoneType;
        chunk.markDirty();
        return CommandResult.ACCEPTED;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(tileX);
        out.writeInt(tileY);
        out.writeByte(zoneType);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public ZoneCommand decode(DataInput in) throws IOException {
            return new ZoneCommand(in.readInt(), in.readInt(), in.readByte());
        }
    };
}
