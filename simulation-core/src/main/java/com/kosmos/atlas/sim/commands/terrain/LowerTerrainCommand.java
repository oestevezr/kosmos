package com.kosmos.atlas.sim.commands.terrain;

import com.kosmos.atlas.sim.commands.CommandDecoder;

import java.io.DataInput;
import java.io.IOException;

/** Lowers a single tile's elevation by {@code deltaDecimeters} (must be positive). */
public final class LowerTerrainCommand extends AbstractTerrainHeightCommand {

    public LowerTerrainCommand(int tileX, int tileY, int deltaDecimeters) {
        super(tileX, tileY, -Math.abs(deltaDecimeters));
    }

    @Override
    public int typeId() {
        return TerrainCommandTypeIds.LOWER_TERRAIN;
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public LowerTerrainCommand decode(DataInput in) throws IOException {
            int tileX = in.readInt();
            int tileY = in.readInt();
            int delta = in.readInt(); // stored negative by writePayload
            return new LowerTerrainCommand(tileX, tileY, -delta);
        }
    };
}
