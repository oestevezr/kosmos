package com.kosmos.atlas.sim.commands.terrain;

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
 * Directly overwrites one tile's terrain type — a sandbox/debug tool for Fase 1 to prove the full
 * command pipeline end to end (spec §38) before real construction commands (BuildRoadCommand,
 * etc.) exist. Rejects any terrain byte outside the known {@link WorldConstants} vocabulary so a
 * malformed or malicious payload can never write an out-of-range terrain code (see also the
 * journal replay security notes: this validation runs identically whether the command came from
 * live input or from replaying a journal).
 */
public final class PaintTerrainCommand extends Command {

    private static final byte MIN_TERRAIN = WorldConstants.TERRAIN_DEEP_WATER;
    private static final byte MAX_TERRAIN = WorldConstants.TERRAIN_MOUNTAIN;

    private final int tileX;
    private final int tileY;
    private final byte terrainType;

    public PaintTerrainCommand(int tileX, int tileY, byte terrainType) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.terrainType = terrainType;
    }

    @Override
    public int typeId() {
        return TerrainCommandTypeIds.PAINT_TERRAIN;
    }

    @Override
    public CommandResult apply(SimulationContext ctx) {
        if (terrainType < MIN_TERRAIN || terrainType > MAX_TERRAIN) {
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
        int localX = Math.floorMod(tileX, WorldConstants.CHUNK_SIZE);
        int localY = Math.floorMod(tileY, WorldConstants.CHUNK_SIZE);
        chunk.terrainType[Chunk.tileIndex(localX, localY)] = terrainType;
        chunk.markDirty();
        return CommandResult.ACCEPTED;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(tileX);
        out.writeInt(tileY);
        out.writeByte(terrainType);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public PaintTerrainCommand decode(DataInput in) throws IOException {
            return new PaintTerrainCommand(in.readInt(), in.readInt(), in.readByte());
        }
    };
}
