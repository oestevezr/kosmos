package com.kosmos.atlas.sim.world;

import com.kosmos.atlas.sim.Metrics;
import com.kosmos.atlas.sim.world.gen.ProceduralGenerator;

import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Streams chunks in and out around a moving focus point (the camera), instead of ever holding
 * the full world resident (spec §27 "Camera / World Streaming", §43).
 *
 * <p>Threading (spec §40): generation happens on a small pool of daemon "world worker" threads;
 * results flow back through a lock-free MPSC queue and are only integrated into the
 * {@link ChunkStore} from {@link #integrateReadyChunks(int)}, which the owner must call once per
 * simulation tick. The render/sim thread never blocks waiting for a worker.
 */
public final class ChunkManager implements AutoCloseable {

    private final ChunkStore store;
    private final ChunkPool pool;
    private final ProceduralGenerator generator;
    private final HardwareProfile profile;
    private final int worldChunksX;
    private final int worldChunksY;

    private final Thread[] workers;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final PriorityQueue<PendingRequest> pendingQueue = new PriorityQueue<>();
    private final Object queueLock = new Object();
    private final java.util.Set<Long> requested = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Chunk> readyQueue = new ConcurrentLinkedQueue<>();

    private final AtomicInteger generatedCount = new AtomicInteger();
    private final Metrics metrics;
    private int focusChunkX;
    private int focusChunkY;
    private boolean focusInitialized;

    // Reused scratch buffers for updateFocus()'s eviction pass — sized once to the store's fixed
    // capacity so a hot path that runs every render frame never allocates (spec §42.4). See the
    // early-return above: this buffer is only ever touched when the focus chunk actually changed.
    private final int[] evictScratchX;
    private final int[] evictScratchY;

    public ChunkManager(ProceduralGenerator generator, HardwareProfile profile, int worldSizeTiles) {
        this(generator, profile, worldSizeTiles, null);
    }

    public ChunkManager(ProceduralGenerator generator, HardwareProfile profile, int worldSizeTiles, Metrics metrics) {
        this.generator = generator;
        this.profile = profile;
        this.metrics = metrics;
        this.worldChunksX = Math.max(1, worldSizeTiles / WorldConstants.CHUNK_SIZE);
        this.worldChunksY = this.worldChunksX;
        int capacity = profile.maxLoadedChunks() + 8;
        this.store = new ChunkStore(capacity);
        this.pool = new ChunkPool(capacity);
        this.evictScratchX = new int[capacity];
        this.evictScratchY = new int[capacity];

        int workerCount = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() - 1));
        this.workers = new Thread[workerCount];
        for (int i = 0; i < workerCount; i++) {
            Thread t = new Thread(this::workerLoop, "atlas-world-worker-" + i);
            t.setDaemon(true);
            workers[i] = t;
            t.start();
        }
    }

    public ChunkStore store() {
        return store;
    }

    public int loadedChunkCount() {
        return store.loadedCount();
    }

    public long generatedTotal() {
        return generatedCount.get();
    }

    /**
     * Updates the streaming focus (typically the camera's chunk coordinate), enqueuing newly
     * in-range chunks for generation and evicting chunks that fell outside the unload radius.
     * Safe and effectively free to call every frame: a host like {@code AtlasGame} calls this
     * unconditionally from its render loop, so this method — not just its caller — is
     * responsible for recognizing "the camera hasn't crossed into a new chunk" and doing zero
     * work (no scan, no allocation) in that overwhelmingly common case.
     */
    public void updateFocus(int chunkX, int chunkY) {
        if (focusInitialized && chunkX == focusChunkX && chunkY == focusChunkY) {
            return;
        }
        this.focusChunkX = chunkX;
        this.focusChunkY = chunkY;
        this.focusInitialized = true;

        int preload = profile.preloadRadius;
        for (int dy = -preload; dy <= preload; dy++) {
            for (int dx = -preload; dx <= preload; dx++) {
                int cx = chunkX + dx;
                int cy = chunkY + dy;
                if (!inWorldBounds(cx, cy)) {
                    continue;
                }
                if (store.contains(cx, cy)) {
                    continue;
                }
                long key = ChunkKey.pack(cx, cy);
                if (requested.add(key)) {
                    int priority = dx * dx + dy * dy;
                    synchronized (queueLock) {
                        pendingQueue.add(new PendingRequest(cx, cy, priority));
                        queueLock.notifyAll();
                    }
                }
            }
        }

        int unload = profile.unloadRadius;
        // Collect out-of-range chunks first; ChunkStore.forEach must not mutate the store mid-iteration.
        // Uses the preallocated evictScratch* buffers (sized to store.capacity() in the
        // constructor) instead of an ArrayList<long[]> so this scan never allocates.
        int evictCount = evictScan(chunkX, chunkY, unload);
        for (int i = 0; i < evictCount; i++) {
            Chunk evicted = store.remove(evictScratchX[i], evictScratchY[i]);
            if (evicted != null) {
                pool.release(evicted);
            }
        }
    }

    private int evictScan(int chunkX, int chunkY, int unload) {
        // forEach's ChunkVisitor lambda is a single reused instance field, not a fresh capturing
        // lambda per call — see EvictionVisitor below.
        evictionVisitor.chunkX = chunkX;
        evictionVisitor.chunkY = chunkY;
        evictionVisitor.unloadRadiusSquared = unload * unload;
        evictionVisitor.count = 0;
        store.forEach(evictionVisitor);
        return evictionVisitor.count;
    }

    private final EvictionVisitor evictionVisitor = new EvictionVisitor();

    /** Reused visitor instance for {@link #evictScan}, avoiding a fresh capturing lambda every call. */
    private final class EvictionVisitor implements ChunkStore.ChunkVisitor {
        int chunkX;
        int chunkY;
        int unloadRadiusSquared;
        int count;

        @Override
        public void visit(Chunk chunk) {
            int ddx = chunk.chunkX() - chunkX;
            int ddy = chunk.chunkY() - chunkY;
            if (ddx * ddx + ddy * ddy > unloadRadiusSquared) {
                evictScratchX[count] = chunk.chunkX();
                evictScratchY[count] = chunk.chunkY();
                count++;
            }
        }
    }

    /**
     * Drains up to {@code maxToIntegrate} freshly-generated chunks from the worker result queue
     * into the store. Bounding the per-tick amount avoids a burst of newly-visible chunks
     * causing a frame-time spike (spec §45: "no long GC pauses... safety margin").
     */
    public int integrateReadyChunks(int maxToIntegrate) {
        int integrated = 0;
        Chunk chunk;
        while (integrated < maxToIntegrate && (chunk = readyQueue.poll()) != null) {
            requested.remove(ChunkKey.pack(chunk.chunkX(), chunk.chunkY()));
            if (store.hasFreeSlot() && !store.contains(chunk.chunkX(), chunk.chunkY())) {
                store.put(chunk);
            } else {
                pool.release(chunk);
            }
            integrated++;
        }
        return integrated;
    }

    private boolean inWorldBounds(int cx, int cy) {
        return cx >= 0 && cy >= 0 && cx < worldChunksX && cy < worldChunksY;
    }

    private void workerLoop() {
        while (running.get()) {
            PendingRequest request;
            synchronized (queueLock) {
                while (pendingQueue.isEmpty() && running.get()) {
                    try {
                        queueLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!running.get()) {
                    return;
                }
                request = pendingQueue.poll();
            }
            if (request == null) {
                continue;
            }
            Chunk chunk = pool.acquire(request.chunkX, request.chunkY);
            long startNanos = System.nanoTime();
            generator.generate(chunk);
            long durationNanos = System.nanoTime() - startNanos;
            generatedCount.incrementAndGet();
            if (metrics != null) {
                metrics.onChunkGenerated(durationNanos);
            }
            readyQueue.add(chunk);
        }
    }

    @Override
    public void close() {
        running.set(false);
        synchronized (queueLock) {
            queueLock.notifyAll();
        }
        for (Thread t : workers) {
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class PendingRequest implements Comparable<PendingRequest> {
        final int chunkX;
        final int chunkY;
        final int priority;

        PendingRequest(int chunkX, int chunkY, int priority) {
            this.chunkX = chunkX;
            this.chunkY = chunkY;
            this.priority = priority;
        }

        @Override
        public int compareTo(PendingRequest o) {
            return Integer.compare(priority, o.priority);
        }
    }
}
