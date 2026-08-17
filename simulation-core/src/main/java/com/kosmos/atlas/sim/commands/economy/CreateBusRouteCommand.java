package com.kosmos.atlas.sim.commands.economy;

import com.kosmos.atlas.sim.commands.Command;
import com.kosmos.atlas.sim.commands.CommandDecoder;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.economy.BuildingEconomics;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.trade.BusRouteRegistry;
import com.kosmos.atlas.sim.trade.EdgeType;
import com.kosmos.atlas.sim.trade.NodeType;
import com.kosmos.atlas.sim.trade.RegionalGraph;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Draws a bus route: an ordered sequence of {@code BuildingType.BUS_STOP} buildings dispatched
 * from one {@code BuildingType.BUS_DEPOT} ({@code docs/roadmap.md}'s bus-route mechanic, MVP 0.6).
 * The first real gameplay consumer of {@link RegionalGraph#addEdge} — one {@link EdgeType#ROAD}
 * edge per consecutive pair of stops (buses run on streets, not rails). The edges have no flow/
 * congestion consumer yet (that's the next "real passengers" pass); this command's payoff is
 * purely enabling {@code UtilitySystem}'s transit-coverage flood-fill for the stops it connects.
 */
public final class CreateBusRouteCommand extends Command {

    /** A route's own capacity/cost — a coarse stand-in until real passenger flow exists (spec §20's
     *  "understandable rather than hyper-realistic"), same idiom as {@code MarketSystem}'s flat gateway constants. */
    private static final int ROUTE_EDGE_CAPACITY_PER_TICK = 40;
    private static final float ROUTE_EDGE_COST_PER_TILE = 0.02f;

    private final int depotBuildingId;
    private final int[] stopBuildingIds;

    public CreateBusRouteCommand(int depotBuildingId, int[] stopBuildingIds) {
        this.depotBuildingId = depotBuildingId;
        this.stopBuildingIds = stopBuildingIds;
    }

    @Override
    public int typeId() {
        return EconomyCommandTypeIds.CREATE_BUS_ROUTE;
    }

    @Override
    public CommandResult apply(SimulationContext ctx) {
        if (stopBuildingIds.length < BusRouteRegistry.MIN_STOPS_PER_ROUTE
            || stopBuildingIds.length > BusRouteRegistry.MAX_STOPS_PER_ROUTE) {
            return CommandResult.REJECTED_INVALID_ROUTE;
        }

        BuildingRegistry buildings = ctx.requireBuildings();
        if (!buildings.isActive(depotBuildingId) || buildings.type(depotBuildingId) != BuildingType.BUS_DEPOT) {
            return CommandResult.REJECTED_INVALID_ROUTE;
        }
        int cityId = buildings.cityId(depotBuildingId);
        for (int stopId : stopBuildingIds) {
            if (!buildings.isActive(stopId) || buildings.type(stopId) != BuildingType.BUS_STOP
                || buildings.cityId(stopId) != cityId) {
                return CommandResult.REJECTED_INVALID_ROUTE;
            }
        }

        BusRouteRegistry busRoutes = ctx.requireBusRoutes();
        int maxRoutes = BuildingEconomics.capacity(BuildingType.BUS_DEPOT);
        if (busRoutes.countRoutesForDepot(depotBuildingId) >= maxRoutes) {
            return CommandResult.REJECTED_DEPOT_AT_CAPACITY;
        }

        RegionalGraph graph = ctx.requireRegionalGraph();
        int[] stopNodeIds = new int[stopBuildingIds.length];
        for (int i = 0; i < stopBuildingIds.length; i++) {
            int nodeId = graph.nearestNodeOfType(buildings.tileX(stopBuildingIds[i]), buildings.tileY(stopBuildingIds[i]), NodeType.BUS_STOP);
            if (nodeId < 0 || graph.nodeTileX(nodeId) != buildings.tileX(stopBuildingIds[i])
                || graph.nodeTileY(nodeId) != buildings.tileY(stopBuildingIds[i])) {
                return CommandResult.REJECTED_INVALID_ROUTE; // stop building exists but its graph node doesn't (shouldn't happen in practice)
            }
            stopNodeIds[i] = nodeId;
        }

        for (int i = 0; i + 1 < stopNodeIds.length; i++) {
            int fromNode = stopNodeIds[i];
            int toNode = stopNodeIds[i + 1];
            double distance = graph.distanceTiles(fromNode, graph.nodeTileX(toNode), graph.nodeTileY(toNode));
            graph.addEdge(EdgeType.ROAD, fromNode, toNode, (float) distance, ROUTE_EDGE_CAPACITY_PER_TICK, ROUTE_EDGE_COST_PER_TILE);
        }

        busRoutes.create(depotBuildingId, cityId, stopBuildingIds);
        return CommandResult.ACCEPTED;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(depotBuildingId);
        out.writeInt(stopBuildingIds.length);
        for (int stopId : stopBuildingIds) {
            out.writeInt(stopId);
        }
    }

    public static final CommandDecoder DECODER = new CommandDecoder() {
        @Override
        public CreateBusRouteCommand decode(DataInput in) throws IOException {
            int depotId = in.readInt();
            int count = in.readInt();
            int[] stops = new int[count];
            for (int i = 0; i < count; i++) {
                stops[i] = in.readInt();
            }
            return new CreateBusRouteCommand(depotId, stops);
        }
    };
}
