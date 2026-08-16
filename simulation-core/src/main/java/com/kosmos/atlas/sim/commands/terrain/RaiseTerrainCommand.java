package com.kosmos.atlas.sim.commands.terrain;

import com.kosmos.atlas.sim.commands.CommandDecoder;

import java.io.DataInput;
import java.io.IOException;

/** Raises a single tile's elevation by {@code deltaDecimeters} (must be positive). */
public final class RaiseTerrainCommand extends AbstractTerrainHeightCommand {

    public RaiseTerrainCommand(int tileX, int tileY, int deltaDecimeters) {
        super(tileX, tileY, Math.abs(deltaDecimeters));
    }

    @Override
    public int typeId() {
        return TerrainCommandTypeIds.RAISE_TERRAIN;
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public RaiseTerrainCommand decode(DataInput in) throws IOException {
            return new RaiseTerrainCommand(in.readInt(), in.readInt(), in.readInt());
        }
    };
}
