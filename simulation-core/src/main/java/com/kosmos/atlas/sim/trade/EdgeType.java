package com.kosmos.atlas.sim.trade;

/** Regional-graph edge categories (spec §13.2: "highway, road, railway, sea route, air route"). */
public final class EdgeType {

    public static final byte ROAD = 0;
    public static final byte RAILWAY = 1;
    public static final byte SEA_ROUTE = 2;
    public static final byte AIR_ROUTE = 3;

    private EdgeType() {
    }
}
