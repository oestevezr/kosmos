package com.kosmos.atlas.sim.commands;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.economy.LoanRegistry;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.world.ChunkStore;

/**
 * The narrow surface a {@link Command} is allowed to touch when applying itself. Commands never
 * receive the whole {@code WorldManager} — only what they need — so it stays obvious, module by
 * module, exactly what authoritative state a command type can mutate (spec §38: terrain
 * validity, ownership/policy rules are evaluated here, not in the client).
 *
 * <p>{@link #buildings()}, {@link #cities()} and {@link #regionalGraph()} are {@code null} for
 * contexts that don't carry that state (e.g. terrain-only commands in a headless benchmark) —
 * commands that need them must call {@code requireX()} rather than assume presence.
 *
 * <p>There is deliberately no standalone {@code GovernmentFinance}/{@code GoodsLedger} field here
 * (unlike Fase 2/3) — since a world can have multiple player-founded cities (spec §9), "the"
 * treasury and "the" goods ledger stopped being well-defined singletons. A command that needs one
 * goes through {@link #requireCities()}{@code .finance(cityId)}/{@code .ledger(cityId)} for a
 * specific city instead.
 */
public final class SimulationContext {

    private final ChunkStore chunkStore;
    private final BuildingRegistry buildings;
    private final CityRegistry cities;
    private final RegionalGraph regionalGraph;
    private final LoanRegistry loans;
    private final int worldSizeTiles;
    private final long currentTick;

    public SimulationContext(ChunkStore chunkStore, int worldSizeTiles, long currentTick) {
        this(chunkStore, null, null, null, worldSizeTiles, currentTick);
    }

    public SimulationContext(ChunkStore chunkStore, BuildingRegistry buildings, int worldSizeTiles, long currentTick) {
        this(chunkStore, buildings, null, null, worldSizeTiles, currentTick);
    }

    public SimulationContext(ChunkStore chunkStore, BuildingRegistry buildings, CityRegistry cities,
                              int worldSizeTiles, long currentTick) {
        this(chunkStore, buildings, cities, null, worldSizeTiles, currentTick);
    }

    public SimulationContext(ChunkStore chunkStore, BuildingRegistry buildings, CityRegistry cities,
                              RegionalGraph regionalGraph, int worldSizeTiles, long currentTick) {
        this(chunkStore, buildings, cities, regionalGraph, null, worldSizeTiles, currentTick);
    }

    public SimulationContext(ChunkStore chunkStore, BuildingRegistry buildings, CityRegistry cities,
                              RegionalGraph regionalGraph, LoanRegistry loans, int worldSizeTiles, long currentTick) {
        this.chunkStore = chunkStore;
        this.buildings = buildings;
        this.cities = cities;
        this.regionalGraph = regionalGraph;
        this.loans = loans;
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

    public CityRegistry cities() {
        return cities;
    }

    public CityRegistry requireCities() {
        if (cities == null) {
            throw new IllegalStateException("This SimulationContext was not given a CityRegistry");
        }
        return cities;
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

    public LoanRegistry loans() {
        return loans;
    }

    public LoanRegistry requireLoans() {
        if (loans == null) {
            throw new IllegalStateException("This SimulationContext was not given a LoanRegistry");
        }
        return loans;
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
