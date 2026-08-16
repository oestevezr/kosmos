package com.kosmos.atlas.sim.commands;

import com.kosmos.atlas.sim.commands.terrain.LowerTerrainCommand;
import com.kosmos.atlas.sim.commands.terrain.PaintTerrainCommand;
import com.kosmos.atlas.sim.commands.terrain.RaiseTerrainCommand;
import com.kosmos.atlas.sim.commands.terrain.TerrainCommandTypeIds;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves {@code seed + journal} reproduces the same world state as the live session that produced
 * it (spec §35: "reproduce bugs from a seed + command log"). Applies a sequence of commands live
 * against a chunk, journals them, replays the journal against a freshly-generated chunk with the
 * same starting state, and asserts both end up identical.
 */
class CommandJournalReplayTest {

    @Test
    void replayingJournalReproducesLiveState() throws IOException {
        ChunkStore liveStore = new ChunkStore(4);
        Chunk liveChunk = new Chunk();
        liveChunk.reset(0, 0);
        liveStore.put(liveChunk);

        List<Command> applied = List.of(
            new RaiseTerrainCommand(1, 1, 50),
            new RaiseTerrainCommand(1, 1, 20),
            new LowerTerrainCommand(1, 1, 10),
            new PaintTerrainCommand(2, 2, WorldConstants.TERRAIN_MOUNTAIN)
        );

        ByteArrayOutputStream journalBytes = new ByteArrayOutputStream();
        long tick = 0;
        try (CommandJournal journal = new CommandJournal(journalBytes)) {
            for (Command command : applied) {
                SimulationContext ctx = new SimulationContext(liveStore, 4096, tick);
                CommandResult result = command.apply(ctx);
                assertEquals(CommandResult.ACCEPTED, result);
                journal.append(tick, command);
                tick++;
            }
            journal.flush();
        }

        // Fresh chunk store representing "regenerate from seed, then replay the journal".
        ChunkStore replayStore = new ChunkStore(4);
        Chunk replayChunk = new Chunk();
        replayChunk.reset(0, 0);
        replayStore.put(replayChunk);

        Map<Integer, CommandDecoder> decoders = CommandJournal.newDecoderRegistry();
        decoders.put(TerrainCommandTypeIds.RAISE_TERRAIN, RaiseTerrainCommand.DECODER);
        decoders.put(TerrainCommandTypeIds.LOWER_TERRAIN, LowerTerrainCommand.DECODER);
        decoders.put(TerrainCommandTypeIds.PAINT_TERRAIN, PaintTerrainCommand.DECODER);

        List<CommandResult> replayResults = new ArrayList<>();
        CommandJournal.replay(new ByteArrayInputStream(journalBytes.toByteArray()), decoders, (replayedTick, command) -> {
            SimulationContext ctx = new SimulationContext(replayStore, 4096, replayedTick);
            replayResults.add(command.apply(ctx));
        });

        assertEquals(applied.size(), replayResults.size());
        for (CommandResult r : replayResults) {
            assertEquals(CommandResult.ACCEPTED, r);
        }

        int idx = Chunk.tileIndex(1, 1);
        assertEquals(liveChunk.elevation[idx], replayChunk.elevation[idx]);
        int paintedIdx = Chunk.tileIndex(2, 2);
        assertEquals(liveChunk.terrainType[paintedIdx], replayChunk.terrainType[paintedIdx]);
        assertEquals(WorldConstants.TERRAIN_MOUNTAIN, replayChunk.terrainType[paintedIdx]);
    }
}
