package com.kosmos.atlas.sim.trade;

import com.kosmos.atlas.sim.economy.GoodsLedger;
import com.kosmos.atlas.sim.economy.GovernmentFinance;

/**
 * Advances every in-flight {@link ShipmentRegistry} entry and settles the ones that have arrived
 * (spec §14, §15). {@code MarketSystem} decides *that* a depot needs to trade and creates the
 * shipment; this system only owns the passage of time between departure and ETA — the same split
 * of responsibility as {@code PopulationSystem} deciding growth vs. {@code RoadNetwork}/
 * {@code UtilitySystem} only maintaining derived reachability.
 *
 * <p>Settlement is split so the two sides of a trade happen at the economically sensible moment:
 * an <b>import</b> is paid for at departure (see {@code MarketSystem}) but the goods only land in
 * inventory when this system completes it at its ETA; an <b>export</b> leaves inventory
 * immediately at departure but the treasury is only paid once this system completes it — money on
 * delivery, not on promise.
 */
public final class ShipmentSystem {

    private static final double EXPORT_SALE_DISCOUNT = 0.9;

    public void tick(long currentTick, ShipmentRegistry shipments, GoodsLedger ledger, GovernmentFinance finance) {
        int highWaterMark = shipments.highWaterMark();
        for (int id = 1; id < highWaterMark; id++) {
            if (!shipments.isActive(id) || shipments.etaTick(id) > currentTick) {
                continue;
            }
            byte commodity = shipments.commodity(id);
            int quantity = shipments.quantity(id);
            if (shipments.kind(id) == ShipmentKind.IMPORT) {
                ledger.importGood(commodity, quantity);
            } else {
                finance.adjustTreasury(quantity * ledger.price(commodity) * EXPORT_SALE_DISCOUNT);
            }
            shipments.complete(id);
        }
    }
}
