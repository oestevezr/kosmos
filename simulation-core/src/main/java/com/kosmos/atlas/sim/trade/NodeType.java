package com.kosmos.atlas.sim.trade;

/**
 * Regional-graph node categories (spec §13.2). {@link #EXTERNAL_MARKET}/{@link #PORT}/
 * {@link #AIRPORT}/{@link #STATION} are all cargo-gateway nodes (MVP 0.3/0.5/0.6's
 * {@code MarketSystem.runGateways} pattern) — {@link #STATION} is specifically the Rail Terminal's
 * node type, not a generic "station" (bus stops get their own {@link #BUS_STOP} to avoid that
 * ambiguity). {@link #BUS_STOP} is the first node type whose edges (added by
 * {@code CreateBusRouteCommand}) actually matter — see {@code docs/roadmap.md}'s bus-route
 * mechanic.
 */
public final class NodeType {

    public static final byte EXTERNAL_MARKET = 0;
    public static final byte PORT = 1;
    public static final byte AIRPORT = 2;
    public static final byte STATION = 3;
    public static final byte BUS_STOP = 4;

    private NodeType() {
    }
}
