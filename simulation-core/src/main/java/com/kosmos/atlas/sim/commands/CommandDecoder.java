package com.kosmos.atlas.sim.commands;

import java.io.DataInput;
import java.io.IOException;

/** Rebuilds a concrete {@link Command} from its journaled payload bytes. */
@FunctionalInterface
public interface CommandDecoder {
    Command decode(DataInput in) throws IOException;
}
