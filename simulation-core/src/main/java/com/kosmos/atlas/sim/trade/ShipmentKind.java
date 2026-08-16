package com.kosmos.atlas.sim.trade;

/** Direction of a {@link ShipmentRegistry} entry relative to the depot that created it. */
public final class ShipmentKind {

    public static final byte IMPORT = 0;
    public static final byte EXPORT = 1;

    private ShipmentKind() {
    }
}
