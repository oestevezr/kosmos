package com.kosmos.atlas.sim.trade;

/**
 * Regional-graph node categories (spec §13.2). MVP 0.3 only ever creates {@link #EXTERNAL_MARKET}
 * nodes (one per {@code TradeDepotCommand}); {@link #PORT}, {@link #AIRPORT} and {@link #STATION}
 * are declared now so MVP 0.5/0.6 add specialized gateway nodes to this same graph instead of
 * inventing a second one (spec roadmap adjustment: see {@code docs/roadmap.md}).
 */
public final class NodeType {

    public static final byte EXTERNAL_MARKET = 0;
    public static final byte PORT = 1;
    public static final byte AIRPORT = 2;
    public static final byte STATION = 3;

    private NodeType() {
    }
}
