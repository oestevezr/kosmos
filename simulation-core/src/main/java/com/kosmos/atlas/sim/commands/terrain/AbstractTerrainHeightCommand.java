package com.kosmos.atlas.sim.commands.terrain;

import com.kosmos.atlas.sim.commands.Command;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataOutput;
import java.io.IOException;

/**
 * Shared validation/application logic for the two Fase 1 sandbox terrain-editing commands
 * (spec §38 example commands). Player modifications must override procedural terrain (spec §9)
 * — this is the first command family that does so, and it establishes the pattern later
 * BuildRoadCommand/BuildBridgeCommand/etc. will follow: validate against {@link SimulationContext},
 * mutate only through the {@link Chunk} it owns, and mark the chunk dirty for persistence and
 * render-cache invalidation.
 */
abstract class AbstractTerrainHeightCommand extends Command {

    private static final short MAX_ELEVATION_DM = 4000; // 400 m, generous sandbox ceiling
    private static final short MIN_ELEVATION_DM = -500; // -50 m, below sea level basins

    final int tileX;
    final int tileY;
    final int deltaDecimeters;

    AbstractTerrainHeightCommand(int tileX, int tileY, int deltaDecimeters) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.deltaDecimeters = deltaDecimeters;
    }

    @Override
    public final CommandResult apply(SimulationContext ctx) {
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
        int idx = Chunk.tileIndex(localX, localY);

        int newElevation = chunk.elevation[idx] + deltaDecimeters;
        if (newElevation > MAX_ELEVATION_DM || newElevation < MIN_ELEVATION_DM) {
            return CommandResult.REJECTED_INVALID_TERRAIN;
        }
        chunk.elevation[idx] = (short) newElevation;
        chunk.markDirty();
        return CommandResult.ACCEPTED;
    }

    @Override
    public final void writePayload(DataOutput out) throws IOException {
        out.writeInt(tileX);
        out.writeInt(tileY);
        out.writeInt(deltaDecimeters);
    }
}
