package com.kosmos.atlas.sim.trade;

import java.util.Arrays;

/**
 * Regional transport graph — nodes (city/port/station/airport/external market) and edges with
 * distance/capacity/cost/travel_time (spec §13.2). Moved here from MVP 0.4 (roadmap adjustment,
 * see {@code docs/roadmap.md}): MVP 0.3 needs a distance-to-external-market figure to make
 * transport cost affect price (spec §20), and building the graph's shape now means MVP 0.4 only
 * has to add real flow (shipments, congestion) on top of it instead of replacing a placeholder.
 *
 * <p>MVP 0.3 never calls {@link #addEdge} — no truck/rail/sea routes exist yet, only
 * {@link #nearestNodeOfType} using straight-line tile distance as an honest stand-in for "no
 * infrastructure has been built to this gateway yet". Once MVP 0.4 places real edges, a path-cost
 * lookup can switch from straight-line to graph-shortest-path without changing callers — that
 * substitution point is the whole reason this class exists already.
 *
 * <p>Same growable-SoA-with-tombstone-free-list shape as {@link com.kosmos.atlas.sim.population.BuildingRegistry},
 * for the same reason: node/edge ids are stored elsewhere (a building's "which market does this
 * connect to" reference) and must stay stable across unrelated removals.
 */
public final class RegionalGraph {

    // --- Nodes ---
    private byte[] nodeType;
    private int[] nodeTileX;
    private int[] nodeTileY;
    private boolean[] nodeActive;
    private int nodeHighWaterMark = 1; // id 0 reserved as "no node"
    private int[] freeNodeIds;
    private int freeNodeTop;

    // --- Edges (unused by MVP 0.3 gameplay; structure only — see class javadoc) ---
    private byte[] edgeType;
    private int[] edgeFrom;
    private int[] edgeTo;
    private float[] edgeDistanceTiles;
    private int[] edgeCapacityPerTick;
    private float[] edgeCostPerTile;
    private int edgeCount;

    public RegionalGraph() {
        this(16, 16);
    }

    public RegionalGraph(int nodeCapacity, int edgeCapacity) {
        int nc = Math.max(2, nodeCapacity) + 1;
        nodeType = new byte[nc];
        nodeTileX = new int[nc];
        nodeTileY = new int[nc];
        nodeActive = new boolean[nc];
        freeNodeIds = new int[nc];

        int ec = Math.max(1, edgeCapacity);
        edgeType = new byte[ec];
        edgeFrom = new int[ec];
        edgeTo = new int[ec];
        edgeDistanceTiles = new float[ec];
        edgeCapacityPerTick = new int[ec];
        edgeCostPerTile = new float[ec];
    }

    public int addNode(byte type, int worldTileX, int worldTileY) {
        int id = freeNodeTop > 0 ? freeNodeIds[--freeNodeTop] : allocateFreshNodeId();
        nodeType[id] = type;
        nodeTileX[id] = worldTileX;
        nodeTileY[id] = worldTileY;
        nodeActive[id] = true;
        return id;
    }

    public void removeNode(int id) {
        nodeActive[id] = false;
        freeNodeIds[freeNodeTop++] = id;
    }

    public boolean isNodeActive(int id) {
        return id > 0 && id < nodeHighWaterMark && nodeActive[id];
    }

    public byte nodeType(int id) {
        return nodeType[id];
    }

    public int nodeTileX(int id) {
        return nodeTileX[id];
    }

    public int nodeTileY(int id) {
        return nodeTileY[id];
    }

    public int nodeHighWaterMark() {
        return nodeHighWaterMark;
    }

    /**
     * Nearest active node of {@code type} to {@code (tileX, tileY)}, by straight-line tile
     * distance, or {@code -1} if none exists. Linear scan — the number of gateway nodes in a
     * single city is small (spec's virgin-world principle means these are all player-placed), so
     * this is not a candidate for the graph-shortest-path machinery edges exist for.
     */
    public int nearestNodeOfType(int tileX, int tileY, byte type) {
        int best = -1;
        long bestDistSq = Long.MAX_VALUE;
        for (int id = 1; id < nodeHighWaterMark; id++) {
            if (!nodeActive[id] || nodeType[id] != type) {
                continue;
            }
            long dx = nodeTileX[id] - tileX;
            long dy = nodeTileY[id] - tileY;
            long distSq = dx * dx + dy * dy;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = id;
            }
        }
        return best;
    }

    public double distanceTiles(int nodeId, int tileX, int tileY) {
        double dx = nodeTileX[nodeId] - tileX;
        double dy = nodeTileY[nodeId] - tileY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Declared for MVP 0.5+ — real routable edges between distinct destination types (Port,
     *  Airport) aren't buildable yet in MVP 0.4, which only models the off-map trade connection
     *  at each gateway node directly (see {@code ShipmentSystem}). Not called by any system yet. */
    public int addEdge(byte type, int fromNodeId, int toNodeId, float distanceTiles, int capacityPerTick, float costPerTile) {
        if (edgeCount == edgeFrom.length) {
            growEdges();
        }
        int id = edgeCount++;
        edgeType[id] = type;
        edgeFrom[id] = fromNodeId;
        edgeTo[id] = toNodeId;
        edgeDistanceTiles[id] = distanceTiles;
        edgeCapacityPerTick[id] = capacityPerTick;
        edgeCostPerTile[id] = costPerTile;
        return id;
    }

    public byte edgeType(int id) {
        return edgeType[id];
    }

    public int edgeCount() {
        return edgeCount;
    }

    private int allocateFreshNodeId() {
        if (nodeHighWaterMark >= nodeType.length) {
            growNodes();
        }
        return nodeHighWaterMark++;
    }

    private void growNodes() {
        int newCapacity = nodeType.length * 2;
        nodeType = Arrays.copyOf(nodeType, newCapacity);
        nodeTileX = Arrays.copyOf(nodeTileX, newCapacity);
        nodeTileY = Arrays.copyOf(nodeTileY, newCapacity);
        nodeActive = Arrays.copyOf(nodeActive, newCapacity);
        freeNodeIds = Arrays.copyOf(freeNodeIds, newCapacity);
    }

    private void growEdges() {
        int newCapacity = edgeFrom.length * 2;
        edgeType = Arrays.copyOf(edgeType, newCapacity);
        edgeFrom = Arrays.copyOf(edgeFrom, newCapacity);
        edgeTo = Arrays.copyOf(edgeTo, newCapacity);
        edgeDistanceTiles = Arrays.copyOf(edgeDistanceTiles, newCapacity);
        edgeCapacityPerTick = Arrays.copyOf(edgeCapacityPerTick, newCapacity);
        edgeCostPerTile = Arrays.copyOf(edgeCostPerTile, newCapacity);
    }
}
