package com.kosmos.atlas.sim.population;

import com.kosmos.atlas.sim.economy.GoodType;
import com.kosmos.atlas.sim.world.WorldConstants;

import java.util.Arrays;

/**
 * The authoritative "one record per building" store, holding population/jobs/income aggregates
 * at building granularity rather than one object per resident (spec §22, §42.3):
 * <pre>
 * BUILDING population aggregates -> DISTRICT -> CITY -> REGION
 * </pre>
 * Fase 2 implements only the BUILDING tier. Backed by growable parallel primitive arrays (Structure
 * of Arrays, spec §42.2) instead of a {@code Building} object per building — a building id is just
 * an index into these arrays, stored directly in {@code Chunk.buildingId[tile]}.
 *
 * <p>Ids are stable for the lifetime of a building and start at 1 ({@link WorldConstants#NO_BUILDING}
 * = 0 means "no building"). {@link #demolish(int)} does not compact the arrays or renumber
 * anything — it tombstones the slot and pushes it onto a free list for reuse, so a
 * {@code Chunk.buildingId} reference is never silently invalidated by an unrelated demolition
 * elsewhere in the city.
 */
public final class BuildingRegistry {

    private byte[] type;
    private int[] tileX;
    private int[] tileY;
    private int[] population;
    private int[] jobs;
    private byte[] incomeLevel;
    private byte[] employmentRatePercent;
    private byte[] satisfactionPercent;
    private boolean[] active;

    // --- MVP 0.3 production chain (spec §7, §21) ---
    private byte[] outputGood;
    private int[] outputRatePerTick;
    private byte[] inputGood;
    private int[] inputRatePerTick;

    private int highWaterMark; // one past the highest id ever allocated
    private int activeCount;
    private int[] freeIds;
    private int freeTop;

    public BuildingRegistry() {
        this(64);
    }

    public BuildingRegistry(int initialCapacity) {
        int capacity = Math.max(2, initialCapacity) + 1; // slot 0 reserved for NO_BUILDING
        type = new byte[capacity];
        tileX = new int[capacity];
        tileY = new int[capacity];
        population = new int[capacity];
        jobs = new int[capacity];
        incomeLevel = new byte[capacity];
        employmentRatePercent = new byte[capacity];
        satisfactionPercent = new byte[capacity];
        active = new boolean[capacity];
        freeIds = new int[capacity];
        outputGood = new byte[capacity];
        outputRatePerTick = new int[capacity];
        inputGood = new byte[capacity];
        inputRatePerTick = new int[capacity];
        Arrays.fill(outputGood, GoodType.NONE);
        Arrays.fill(inputGood, GoodType.NONE);
        highWaterMark = 1; // id 0 is the NO_BUILDING sentinel and is never allocated
    }

    public int capacity() {
        return type.length;
    }

    public int highWaterMark() {
        return highWaterMark;
    }

    public int activeCount() {
        return activeCount;
    }

    /** Allocates a new non-production building record and returns its id (always >= 1). */
    public int create(byte buildingType, int worldTileX, int worldTileY) {
        return create(buildingType, worldTileX, worldTileY, GoodType.NONE, 0, GoodType.NONE, 0);
    }

    /** Allocates a new production building (spec §7, §21): produces {@code outputGood} from {@code inputGood} (or from nothing, for extraction). */
    public int create(byte buildingType, int worldTileX, int worldTileY,
                       byte outputGoodType, int outputRate, byte inputGoodType, int inputRate) {
        int id = freeTop > 0 ? freeIds[--freeTop] : allocateFreshId();
        type[id] = buildingType;
        tileX[id] = worldTileX;
        tileY[id] = worldTileY;
        population[id] = 0;
        jobs[id] = 0;
        incomeLevel[id] = 0;
        employmentRatePercent[id] = 0;
        satisfactionPercent[id] = 50;
        outputGood[id] = outputGoodType;
        outputRatePerTick[id] = outputRate;
        inputGood[id] = inputGoodType;
        inputRatePerTick[id] = inputRate;
        active[id] = true;
        activeCount++;
        return id;
    }

    /**
     * Creates an empty registry sized for a load-from-save restore, without going through
     * {@link #create}/{@link #demolish} (whose free-list semantics assume live gameplay churn,
     * not a batch replay of a persisted id sequence). Use with {@link #restoreActive} and
     * {@link #restoreTombstone} in strictly increasing id order starting at 1, matching how
     * {@code BuildingRegistryIO} wrote them.
     */
    public static BuildingRegistry createForRestore(int highWaterMark) {
        BuildingRegistry registry = new BuildingRegistry(Math.max(2, highWaterMark));
        registry.highWaterMark = Math.max(1, highWaterMark);
        return registry;
    }

    /** Directly installs an active building at {@code id} during restore. See {@link #createForRestore}. */
    public void restoreActive(int id, byte buildingType, int worldTileX, int worldTileY,
                               int populationValue, int jobsValue, byte incomeLevelValue,
                               int employmentRatePercentValue, int satisfactionPercentValue,
                               byte outputGoodType, int outputRate, byte inputGoodType, int inputRate) {
        type[id] = buildingType;
        tileX[id] = worldTileX;
        tileY[id] = worldTileY;
        population[id] = populationValue;
        jobs[id] = jobsValue;
        incomeLevel[id] = incomeLevelValue;
        employmentRatePercent[id] = (byte) clampPercent(employmentRatePercentValue);
        satisfactionPercent[id] = (byte) clampPercent(satisfactionPercentValue);
        outputGood[id] = outputGoodType;
        outputRatePerTick[id] = outputRate;
        inputGood[id] = inputGoodType;
        inputRatePerTick[id] = inputRate;
        active[id] = true;
        activeCount++;
    }

    /** Marks {@code id} as a demolished slot during restore, making it reusable by future {@link #create}. See {@link #createForRestore}. */
    public void restoreTombstone(int id) {
        active[id] = false;
        freeIds[freeTop++] = id;
    }

    private int allocateFreshId() {
        if (highWaterMark >= type.length) {
            grow();
        }
        return highWaterMark++;
    }

    private void grow() {
        int oldCapacity = type.length;
        int newCapacity = oldCapacity * 2;
        type = Arrays.copyOf(type, newCapacity);
        tileX = Arrays.copyOf(tileX, newCapacity);
        tileY = Arrays.copyOf(tileY, newCapacity);
        population = Arrays.copyOf(population, newCapacity);
        jobs = Arrays.copyOf(jobs, newCapacity);
        incomeLevel = Arrays.copyOf(incomeLevel, newCapacity);
        employmentRatePercent = Arrays.copyOf(employmentRatePercent, newCapacity);
        satisfactionPercent = Arrays.copyOf(satisfactionPercent, newCapacity);
        active = Arrays.copyOf(active, newCapacity);
        freeIds = Arrays.copyOf(freeIds, newCapacity);
        outputGood = Arrays.copyOf(outputGood, newCapacity);
        outputRatePerTick = Arrays.copyOf(outputRatePerTick, newCapacity);
        inputGood = Arrays.copyOf(inputGood, newCapacity);
        inputRatePerTick = Arrays.copyOf(inputRatePerTick, newCapacity);
        // Arrays.copyOf zero-pads new slots, but 0 is a valid GoodType (FOOD) — the new tail must
        // be explicitly reset to the NONE sentinel, not left looking like "produces Food".
        Arrays.fill(outputGood, oldCapacity, newCapacity, GoodType.NONE);
        Arrays.fill(inputGood, oldCapacity, newCapacity, GoodType.NONE);
    }

    public void demolish(int id) {
        requireActive(id);
        active[id] = false;
        population[id] = 0;
        jobs[id] = 0;
        outputGood[id] = GoodType.NONE;
        outputRatePerTick[id] = 0;
        inputGood[id] = GoodType.NONE;
        inputRatePerTick[id] = 0;
        activeCount--;
        freeIds[freeTop++] = id;
    }

    public boolean isActive(int id) {
        return id > 0 && id < highWaterMark && active[id];
    }

    public byte type(int id) {
        return type[id];
    }

    public int tileX(int id) {
        return tileX[id];
    }

    public int tileY(int id) {
        return tileY[id];
    }

    public int population(int id) {
        return population[id];
    }

    public void setPopulation(int id, int value) {
        population[id] = value;
    }

    public int jobs(int id) {
        return jobs[id];
    }

    public void setJobs(int id, int value) {
        jobs[id] = value;
    }

    public byte incomeLevel(int id) {
        return incomeLevel[id];
    }

    public void setIncomeLevel(int id, byte value) {
        incomeLevel[id] = value;
    }

    public int employmentRatePercent(int id) {
        return employmentRatePercent[id] & 0xFF;
    }

    public void setEmploymentRatePercent(int id, int percent0to100) {
        employmentRatePercent[id] = (byte) clampPercent(percent0to100);
    }

    public int satisfactionPercent(int id) {
        return satisfactionPercent[id] & 0xFF;
    }

    public void setSatisfactionPercent(int id, int percent0to100) {
        satisfactionPercent[id] = (byte) clampPercent(percent0to100);
    }

    /** The good this building produces, or {@link GoodType#NONE} if it doesn't produce anything. */
    public byte outputGood(int id) {
        return outputGood[id];
    }

    /** Units of {@link #outputGood(int)} produced per tick this building is running at full capacity. */
    public int outputRatePerTick(int id) {
        return outputRatePerTick[id];
    }

    /** The good this building consumes as an input, or {@link GoodType#NONE} for pure extraction/no input. */
    public byte inputGood(int id) {
        return inputGood[id];
    }

    /** Units of {@link #inputGood(int)} consumed per tick this building is running at full capacity. */
    public int inputRatePerTick(int id) {
        return inputRatePerTick[id];
    }

    /** Visits every currently-active building without allocating an iterator. */
    public void forEachActive(BuildingVisitor visitor) {
        for (int id = 1; id < highWaterMark; id++) {
            if (active[id]) {
                visitor.visit(id);
            }
        }
    }

    private void requireActive(int id) {
        if (!isActive(id)) {
            throw new IllegalArgumentException("Building id " + id + " is not active");
        }
    }

    private static int clampPercent(int v) {
        return Math.max(0, Math.min(100, v));
    }

    @FunctionalInterface
    public interface BuildingVisitor {
        void visit(int buildingId);
    }
}
