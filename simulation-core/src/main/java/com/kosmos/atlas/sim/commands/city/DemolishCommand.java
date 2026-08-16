package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.commands.Command;
import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.RegionalGraph;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * The universal "bulldoze" tool (spec §38 lists {@code DemolishCommand} explicitly). Removes
 * whatever combination of building, road and zoning occupies a tile — a building's departure
 * frees its {@code BuildingRegistry} id for reuse, exactly like a player-triggered move-out.
 */
public final class DemolishCommand extends Command {

    private final int tileX;
    private final int tileY;

    public DemolishCommand(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public int typeId() {
        return CityCommandTypeIds.DEMOLISH;
    }

    @Override
    public CommandResult apply(SimulationContext ctx) {
        if (!ctx.inBounds(tileX, tileY)) {
            return CommandResult.REJECTED_OUT_OF_BOUNDS;
        }
        int chunkX = Math.floorDiv(tileX, WorldConstants.CHUNK_SIZE);
        int chunkY = Math.floorDiv(tileY, WorldConstants.CHUNK_SIZE);
        Chunk chunk = ctx.chunkStore().get(chunkX, chunkY);
        if (chunk == null) {
            return CommandResult.REJECTED_UNKNOWN_CHUNK;
        }
        int idx = Chunk.tileIndex(Math.floorMod(tileX, WorldConstants.CHUNK_SIZE), Math.floorMod(tileY, WorldConstants.CHUNK_SIZE));

        boolean hadRoad = chunk.roadType[idx] != WorldConstants.ROAD_NONE;
        boolean hadZone = chunk.zoneType[idx] != WorldConstants.ZONE_NONE;
        int existingBuilding = chunk.buildingId[idx];
        if (!hadRoad && !hadZone && existingBuilding == WorldConstants.NO_BUILDING) {
            return CommandResult.REJECTED_NOTHING_TO_DEMOLISH;
        }

        if (existingBuilding != WorldConstants.NO_BUILDING) {
            BuildingRegistry buildings = ctx.requireBuildings();
            if (buildings.isActive(existingBuilding)) {
                if (buildings.type(existingBuilding) == BuildingType.TRADE_DEPOT) {
                    removeMatchingGraphNode(ctx);
                }
                buildings.demolish(existingBuilding);
            }
            chunk.buildingId[idx] = WorldConstants.NO_BUILDING;
        }
        chunk.roadType[idx] = WorldConstants.ROAD_NONE;
        chunk.zoneType[idx] = WorldConstants.ZONE_NONE;
        chunk.markDirty();
        return CommandResult.ACCEPTED;
    }

    /**
     * A demolished {@code TradeDepot} must also stop being an external-market gateway in the
     * {@link RegionalGraph} — the graph has no back-reference from building id to node id, so this
     * looks the node up by the exact tile the depot stood on (positions always match: the node was
     * created at this same tile by {@code BuildProductionBuildingCommand}).
     */
    private void removeMatchingGraphNode(SimulationContext ctx) {
        RegionalGraph graph = ctx.regionalGraph();
        if (graph == null) {
            return;
        }
        int nodeId = graph.nearestNodeOfType(tileX, tileY, NodeType.EXTERNAL_MARKET);
        if (nodeId >= 0 && graph.nodeTileX(nodeId) == tileX && graph.nodeTileY(nodeId) == tileY) {
            graph.removeNode(nodeId);
        }
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(tileX);
        out.writeInt(tileY);
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public DemolishCommand decode(DataInput in) throws IOException {
            return new DemolishCommand(in.readInt(), in.readInt());
        }
    };
}
