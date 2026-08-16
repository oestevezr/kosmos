package com.kosmos.atlas.sim.commands.economy;

import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.economy.GoodType;
import com.kosmos.atlas.sim.economy.GovernmentFinance;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildProductionBuildingCommandTest {

    private ChunkStore store;
    private BuildingRegistry buildings;
    private RegionalGraph graph;

    @BeforeEach
    void setUp() {
        store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        chunk.resourceFlags[Chunk.tileIndex(5, 5)] = WorldConstants.RESOURCE_FERTILE_BONUS;
        chunk.resourceFlags[Chunk.tileIndex(6, 5)] = WorldConstants.RESOURCE_TIMBER;
        chunk.resourceFlags[Chunk.tileIndex(7, 5)] = WorldConstants.RESOURCE_IRON;
        chunk.resourceFlags[Chunk.tileIndex(8, 5)] = WorldConstants.RESOURCE_COAL;
        chunk.resourceFlags[Chunk.tileIndex(9, 5)] = WorldConstants.RESOURCE_STONE;
        // tile (10,5) deliberately left with no resource flags
        store.put(chunk);
        buildings = new BuildingRegistry();
        graph = new RegionalGraph();
    }

    private SimulationContext ctx() {
        return new SimulationContext(store, buildings, new GovernmentFinance(), graph, 4096, 0);
    }

    @Test
    void farmRequiresFertileTile() {
        assertEquals(CommandResult.ACCEPTED,
            new BuildProductionBuildingCommand(5, 5, BuildingType.FARM).apply(ctx()));
        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN,
            new BuildProductionBuildingCommand(10, 5, BuildingType.FARM).apply(ctx()));
    }

    @Test
    void farmProducesFood() {
        new BuildProductionBuildingCommand(5, 5, BuildingType.FARM).apply(ctx());
        int id = store.get(0, 0).buildingId[Chunk.tileIndex(5, 5)];
        assertEquals(GoodType.FOOD, buildings.outputGood(id));
        assertEquals(GoodType.NONE, buildings.inputGood(id));
    }

    @Test
    void mineProducesOreOnIronTileAndFuelOnCoalTile() {
        new BuildProductionBuildingCommand(7, 5, BuildingType.MINE).apply(ctx());
        int ironMine = store.get(0, 0).buildingId[Chunk.tileIndex(7, 5)];
        assertEquals(GoodType.ORE, buildings.outputGood(ironMine));

        new BuildProductionBuildingCommand(8, 5, BuildingType.MINE).apply(ctx());
        int coalMine = store.get(0, 0).buildingId[Chunk.tileIndex(8, 5)];
        assertEquals(GoodType.FUEL, buildings.outputGood(coalMine));
    }

    @Test
    void mineOnTileWithoutMineralsIsRejected() {
        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN,
            new BuildProductionBuildingCommand(10, 5, BuildingType.MINE).apply(ctx()));
    }

    @Test
    void steelMillNeedsNoResourceFlagButConsumesOre() {
        assertEquals(CommandResult.ACCEPTED,
            new BuildProductionBuildingCommand(10, 5, BuildingType.STEEL_MILL).apply(ctx()));
        int id = store.get(0, 0).buildingId[Chunk.tileIndex(10, 5)];
        assertEquals(GoodType.STEEL, buildings.outputGood(id));
        assertEquals(GoodType.ORE, buildings.inputGood(id));
        assertTrue(buildings.inputRatePerTick(id) > 0);
    }

    @Test
    void tradeDepotRegistersExternalMarketNode() {
        assertEquals(CommandResult.ACCEPTED,
            new BuildProductionBuildingCommand(10, 5, BuildingType.TRADE_DEPOT).apply(ctx()));
        int nearest = graph.nearestNodeOfType(10, 5, NodeType.EXTERNAL_MARKET);
        assertTrue(nearest >= 0);
        assertEquals(10, graph.nodeTileX(nearest));
        assertEquals(5, graph.nodeTileY(nearest));
    }

    @Test
    void unknownBuildingTypeIsRejected() {
        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN,
            new BuildProductionBuildingCommand(10, 5, BuildingType.RESIDENTIAL).apply(ctx()));
    }
}
