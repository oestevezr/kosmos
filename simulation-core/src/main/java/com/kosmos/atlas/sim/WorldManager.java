package com.kosmos.atlas.sim;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.Command;
import com.kosmos.atlas.sim.commands.CommandBus;
import com.kosmos.atlas.sim.commands.CommandJournal;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.economy.GovernmentFinanceSystem;
import com.kosmos.atlas.sim.economy.LoanRegistry;
import com.kosmos.atlas.sim.economy.LoanSystem;
import com.kosmos.atlas.sim.economy.MarketSystem;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.PopulationSystem;
import com.kosmos.atlas.sim.trade.PortRegistry;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.trade.ShipmentRegistry;
import com.kosmos.atlas.sim.trade.ShipmentSystem;
import com.kosmos.atlas.sim.transport.RoadNetwork;
import com.kosmos.atlas.sim.utility.UtilitySystem;
import com.kosmos.atlas.sim.world.ChunkManager;
import com.kosmos.atlas.sim.world.HardwareProfile;
import com.kosmos.atlas.sim.world.gen.ProceduralGenerator;
import com.kosmos.atlas.sim.world.gen.WorldGenSettings;

import java.io.IOException;

/**
 * The single entry point into the simulation core (spec §35, §50: "WorldManager" heads the list
 * of proposed core systems). Both {@code headless-runner} and {@code game-client} drive the
 * simulation exclusively through this class — neither ever reaches into {@code sim.world} or
 * {@code sim.commands} internals directly, which is what keeps the client/headless split honest.
 *
 * <p>Owns the command bus, chunk streaming, the tick scheduler, metrics, and the city-building
 * state: {@link BuildingRegistry} and {@link CityRegistry} — the latter owning every
 * player-founded city's own treasury and goods ledger (spec §9), which is what makes "whose money
 * is this" a well-defined question once more than one city exists. {@link #update(double)} is the
 * one method a host (render loop or headless loop) calls every frame; everything else is
 * one-time setup or event submission.
 */
public final class WorldManager implements AutoCloseable {

    private static final int MAX_CHUNK_INTEGRATIONS_PER_TICK = 8;
    private static final int MAX_TICKS_PER_FRAME = 20;
    private static final int COMMAND_BUS_CAPACITY = 1024;

    /** Cadences, in ticks, matching the tiers spec §41 lays out (traffic > buildings > economy). */
    private static final int ROAD_NETWORK_CADENCE_TICKS = 5;
    private static final int UTILITY_CADENCE_TICKS = 10;
    private static final int POPULATION_CADENCE_TICKS = 10;
    private static final int FINANCE_CADENCE_TICKS = 50;
    private static final int MARKET_CADENCE_TICKS = 50;
    private static final int SHIPMENT_CADENCE_TICKS = 5;
    private static final int LOAN_CADENCE_TICKS = 50;

    private final WorldGenSettings genSettings;
    private final HardwareProfile profile;
    private final ChunkManager chunkManager;
    private final CommandBus commandBus = new CommandBus(COMMAND_BUS_CAPACITY);
    private final SimulationScheduler scheduler = new SimulationScheduler();
    private final TimeManager timeManager = new TimeManager(MAX_TICKS_PER_FRAME);
    private final Metrics metrics = new Metrics();

    private final BuildingRegistry buildings = new BuildingRegistry();
    private final CityRegistry cities;
    private final RegionalGraph regionalGraph = new RegionalGraph();
    private final ShipmentRegistry shipments = new ShipmentRegistry();
    private final LoanRegistry loans = new LoanRegistry();
    private final PortRegistry ports = new PortRegistry();
    private final RoadNetwork roadNetwork = new RoadNetwork();
    private final UtilitySystem utilitySystem = new UtilitySystem();
    private final PopulationSystem populationSystem = new PopulationSystem();
    private final GovernmentFinanceSystem financeSystem = new GovernmentFinanceSystem();
    private final MarketSystem marketSystem = new MarketSystem();
    private final ShipmentSystem shipmentSystem = new ShipmentSystem();
    private final LoanSystem loanSystem = new LoanSystem();

    private CommandJournal journal; // optional — set via enableJournal()

    public WorldManager(WorldGenSettings genSettings, HardwareProfile profile) {
        this(genSettings, profile, Difficulty.MEDIUM);
    }

    public WorldManager(WorldGenSettings genSettings, HardwareProfile profile, Difficulty difficulty) {
        this.genSettings = genSettings;
        this.profile = profile;
        this.cities = new CityRegistry(difficulty);
        ProceduralGenerator generator = new ProceduralGenerator(genSettings);
        this.chunkManager = new ChunkManager(generator, profile, genSettings.worldSizeTiles, metrics);

        scheduler.register("chunk-integration", 1, tick ->
            chunkManager.integrateReadyChunks(MAX_CHUNK_INTEGRATIONS_PER_TICK));
        scheduler.register("road-network", ROAD_NETWORK_CADENCE_TICKS, tick ->
            roadNetwork.update(chunkManager.store()));
        scheduler.register("utilities", UTILITY_CADENCE_TICKS, tick ->
            utilitySystem.update(chunkManager.store(), buildings, cities));
        scheduler.register("population", POPULATION_CADENCE_TICKS, tick ->
            populationSystem.tick(chunkManager.store(), buildings, cities, utilitySystem));
        scheduler.register("government-finance", FINANCE_CADENCE_TICKS, tick ->
            financeSystem.tick(buildings, cities));
        scheduler.register("market", MARKET_CADENCE_TICKS, tick ->
            marketSystem.tick(buildings, cities, regionalGraph, shipments, ports, tick));
        scheduler.register("shipments", SHIPMENT_CADENCE_TICKS, tick ->
            shipmentSystem.tick(tick, shipments, cities));
        scheduler.register("loans", LOAN_CADENCE_TICKS, tick ->
            loanSystem.tick(loans));
    }

    public WorldGenSettings genSettings() {
        return genSettings;
    }

    public HardwareProfile hardwareProfile() {
        return profile;
    }

    public ChunkManager chunkManager() {
        return chunkManager;
    }

    public SimulationScheduler scheduler() {
        return scheduler;
    }

    public TimeManager timeManager() {
        return timeManager;
    }

    public Metrics metrics() {
        return metrics;
    }

    public BuildingRegistry buildings() {
        return buildings;
    }

    public CityRegistry cities() {
        return cities;
    }

    public PopulationSystem populationSystem() {
        return populationSystem;
    }

    public RegionalGraph regionalGraph() {
        return regionalGraph;
    }

    public ShipmentRegistry shipments() {
        return shipments;
    }

    public LoanRegistry loans() {
        return loans;
    }

    public PortRegistry ports() {
        return ports;
    }

    public void enableJournal(CommandJournal journal) {
        this.journal = journal;
    }

    /** Called by the client thread: enqueues a command for the simulation thread to apply. Never blocks. */
    public boolean submitCommand(Command command) {
        return commandBus.offer(command);
    }

    /** Called by the render/camera owner to update chunk-streaming focus. Cheap; safe every frame. */
    public void updateCameraFocus(int worldTileX, int worldTileY) {
        int chunkX = Math.floorDiv(worldTileX, com.kosmos.atlas.sim.world.WorldConstants.CHUNK_SIZE);
        int chunkY = Math.floorDiv(worldTileY, com.kosmos.atlas.sim.world.WorldConstants.CHUNK_SIZE);
        chunkManager.updateFocus(chunkX, chunkY);
    }

    /**
     * Drains pending commands and advances the simulation by however many fixed ticks
     * {@code realDeltaSeconds} (scaled by the current time-compression speed) produces.
     *
     * @return the number of ticks actually advanced this call.
     */
    public int update(double realDeltaSeconds) {
        drainCommands();
        int ticks = timeManager.consumeFrame(realDeltaSeconds);
        if (ticks > 0) {
            long tickStartNanos = System.nanoTime();
            scheduler.advance(ticks);
            metrics.tickDuration.record((System.nanoTime() - tickStartNanos) / Math.max(1, ticks));
        }
        return ticks;
    }

    private void drainCommands() {
        Command command;
        while ((command = commandBus.poll()) != null) {
            SimulationContext ctx = new SimulationContext(
                chunkManager.store(), buildings, cities, regionalGraph, loans, ports, genSettings.worldSizeTiles, scheduler.currentTick());
            CommandResult result = command.apply(ctx);
            if (result == CommandResult.ACCEPTED) {
                metrics.onCommandAccepted();
                if (journal != null) {
                    try {
                        journal.append(scheduler.currentTick(), command);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to journal accepted command", e);
                    }
                }
            } else {
                metrics.onCommandRejected();
            }
        }
    }

    @Override
    public void close() {
        chunkManager.close();
        if (journal != null) {
            try {
                journal.close();
            } catch (IOException ignored) {
                // best-effort on shutdown
            }
        }
    }
}
