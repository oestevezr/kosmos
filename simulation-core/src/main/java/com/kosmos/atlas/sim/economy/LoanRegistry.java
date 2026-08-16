package com.kosmos.atlas.sim.economy;

import java.util.Arrays;

/**
 * The authoritative directory of outstanding loans (the user's requested loan system: "un sistema
 * de préstamos, con el mercado externo simulado serían intereses altos y para ciudades/paises
 * fundadas dependería de la simulación"). Same growable-SoA-with-tombstone-free-list id shape as
 * {@code BuildingRegistry}/{@code ShipmentRegistry}/{@code CityRegistry} — a loan id is just an
 * index, stable across unrelated changes elsewhere.
 *
 * <p>Deliberately simple, matching {@link GovernmentFinance}'s "MVP economy should be
 * understandable rather than hyper-realistic" precedent: a loan's {@link #balance} accrues
 * interest every {@code LoanSystem.tick} but is never auto-debited — the borrower must actively
 * repay via {@code RepayLoanCommand}. There is no fixed term or amortization schedule; a loan
 * simply exists, growing, until it is repaid in full (which tombstones it).
 */
public final class LoanRegistry {

    private byte[] lenderType;
    private int[] borrowerCityId;
    /** 0 (no city) for {@link LoanLenderType#EXTERNAL_MARKET} loans. */
    private int[] lenderCityId;
    private double[] principal;
    private double[] balance;
    private double[] interestRatePerAccrual;
    private long[] originationTick;
    private boolean[] active;

    private int highWaterMark = 1; // id 0 reserved as "no loan"
    private int activeCount;
    private int[] freeIds;
    private int freeTop;

    public LoanRegistry() {
        this(16);
    }

    public LoanRegistry(int initialCapacity) {
        int capacity = Math.max(2, initialCapacity) + 1;
        lenderType = new byte[capacity];
        borrowerCityId = new int[capacity];
        lenderCityId = new int[capacity];
        principal = new double[capacity];
        balance = new double[capacity];
        interestRatePerAccrual = new double[capacity];
        originationTick = new long[capacity];
        active = new boolean[capacity];
        freeIds = new int[capacity];
    }

    public int highWaterMark() {
        return highWaterMark;
    }

    public int activeCount() {
        return activeCount;
    }

    public int create(byte lenderTypeValue, int borrowerCityIdValue, int lenderCityIdValue,
                       double principalValue, double interestRatePerAccrualValue, long tick) {
        int id = freeTop > 0 ? freeIds[--freeTop] : allocateFreshId();
        lenderType[id] = lenderTypeValue;
        borrowerCityId[id] = borrowerCityIdValue;
        lenderCityId[id] = lenderCityIdValue;
        principal[id] = principalValue;
        balance[id] = principalValue;
        interestRatePerAccrual[id] = interestRatePerAccrualValue;
        originationTick[id] = tick;
        active[id] = true;
        activeCount++;
        return id;
    }

    public boolean isActive(int id) {
        return id > 0 && id < highWaterMark && active[id];
    }

    public byte lenderType(int id) {
        return lenderType[id];
    }

    public int borrowerCityId(int id) {
        return borrowerCityId[id];
    }

    public int lenderCityId(int id) {
        return lenderCityId[id];
    }

    public double principal(int id) {
        return principal[id];
    }

    public double balance(int id) {
        return balance[id];
    }

    public double interestRatePerAccrual(int id) {
        return interestRatePerAccrual[id];
    }

    public long originationTick(int id) {
        return originationTick[id];
    }

    /** Grows {@link #balance} by this loan's rate — called once per {@code LoanSystem.tick}. */
    public void accrueInterest(int id) {
        balance[id] += balance[id] * interestRatePerAccrual[id];
    }

    /**
     * Applies a repayment of {@code amount} (already capped by the caller to at most
     * {@link #balance}) and tombstones the loan once its balance reaches zero.
     */
    public void applyRepayment(int id, double amount) {
        balance[id] = Math.max(0.0, balance[id] - amount);
        if (balance[id] < 0.01) {
            active[id] = false;
            activeCount--;
            freeIds[freeTop++] = id;
        }
    }

    /** Visits every currently-active loan without allocating an iterator. */
    public void forEachActive(LoanVisitor visitor) {
        for (int id = 1; id < highWaterMark; id++) {
            if (active[id]) {
                visitor.visit(id);
            }
        }
    }

    public static LoanRegistry createForRestore(int highWaterMarkValue) {
        LoanRegistry registry = new LoanRegistry(Math.max(2, highWaterMarkValue));
        registry.highWaterMark = Math.max(1, highWaterMarkValue);
        return registry;
    }

    public void restoreActive(int id, byte lenderTypeValue, int borrowerCityIdValue, int lenderCityIdValue,
                               double principalValue, double balanceValue, double interestRatePerAccrualValue, long tick) {
        lenderType[id] = lenderTypeValue;
        borrowerCityId[id] = borrowerCityIdValue;
        lenderCityId[id] = lenderCityIdValue;
        principal[id] = principalValue;
        balance[id] = balanceValue;
        interestRatePerAccrual[id] = interestRatePerAccrualValue;
        originationTick[id] = tick;
        active[id] = true;
        activeCount++;
    }

    public void restoreTombstone(int id) {
        active[id] = false;
        freeIds[freeTop++] = id;
    }

    private int allocateFreshId() {
        if (highWaterMark >= lenderType.length) {
            grow();
        }
        return highWaterMark++;
    }

    private void grow() {
        int newCapacity = lenderType.length * 2;
        lenderType = Arrays.copyOf(lenderType, newCapacity);
        borrowerCityId = Arrays.copyOf(borrowerCityId, newCapacity);
        lenderCityId = Arrays.copyOf(lenderCityId, newCapacity);
        principal = Arrays.copyOf(principal, newCapacity);
        balance = Arrays.copyOf(balance, newCapacity);
        interestRatePerAccrual = Arrays.copyOf(interestRatePerAccrual, newCapacity);
        originationTick = Arrays.copyOf(originationTick, newCapacity);
        active = Arrays.copyOf(active, newCapacity);
        freeIds = Arrays.copyOf(freeIds, newCapacity);
    }

    @FunctionalInterface
    public interface LoanVisitor {
        void visit(int loanId);
    }
}
