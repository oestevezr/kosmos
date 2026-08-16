package com.kosmos.atlas.sim.commands;

import com.kosmos.atlas.sim.economy.GovernmentFinance;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.world.ChunkStore;

/**
 * The narrow surface a {@link Command} is allowed to touch when applying itself. Commands never
 * receive the whole {@code WorldManager} — only what they need — so it stays obvious, module by
 * module, exactly what authoritative state a command type can mutate (spec §38: terrain
 * validity, ownership/policy rules are evaluated here, not in the client).
 *
 * <p>{@link #buildings()}, {@link #finance()} and {@link #regionalGraph()} are {@code null} for
 * contexts that don't carry that state (e.g. terrain-only commands in a headless benchmark) —
 * commands that need them must call {@code requireX()} rather than assume presence.
 */
public final class SimulationContext {

    private final ChunkStore chunkStore;
    private final BuildingRegistry buildings;
    private final GovernmentFinance finance;
    private final RegionalGraph regionalGraph;
    private final int worldSizeTiles;
    private final long currentTick;

    public SimulationContext(ChunkStore chunkStore, int worldSizeTiles, long currentTick) {
        this(chunkStore, null, null, null, worldSizeTiles, currentTick);
    }

    public SimulationContext(ChunkStore chunkStore, BuildingRegistry buildings, int worldSizeTiles, long currentTick) {
        this(chunkStore, buildings, null, null, worldSizeTiles, currentTick);
    }

    public SimulationContext(ChunkStore chunkStore, BuildingRegistry buildings, GovernmentFinance finance,
                              int worldSizeTiles, long currentTick) {
        this(chunkStore, buildings, finance, null, worldSizeTiles, currentTick);
    }

    public SimulationContext(ChunkStore chunkStore, BuildingRegistry buildings, GovernmentFinance finance,
                              RegionalGraph regionalGraph, int worldSizeTiles, long currentTick) {
        this.chunkStore = chunkStore;
        this.buildings = buildings;
        this.finance = finance;
        this.regionalGraph = regionalGraph;
        this.worldSizeTiles = worldSizeTiles;
        this.currentTick = currentTick;
    }

    public ChunkStore chunkStore() {
        return chunkStore;
    }

    public BuildingRegistry buildings() {
        return buildings;
    }

    public BuildingRegistry requireBuildings() {
        if (buildings == null) {
            throw new IllegalStateException("This SimulationContext was not given a BuildingRegistry");
        }
        return buildings;
    }

    public GovernmentFinance finance() {
        return finance;
    }

    public GovernmentFinance requireFinance() {
        if (finance == null) {
            throw new IllegalStateException("This SimulationContext was not given a GovernmentFinance");
        }
        return finance;
    }

    public RegionalGraph regionalGraph() {
        return regionalGraph;
    }

    public RegionalGraph requireRegionalGraph() {
        if (regionalGraph == null) {
            throw new IllegalStateException("This SimulationContext was not given a RegionalGraph");
        }
        return regionalGraph;
    }

    public int worldSizeTiles() {
        return worldSizeTiles;
    }

    public long currentTick() {
        return currentTick;
    }

    public boolean inBounds(int tileX, int tileY) {
        return tileX >= 0 && tileY >= 0 && tileX < worldSizeTiles && tileY < worldSizeTiles;
    }
}
