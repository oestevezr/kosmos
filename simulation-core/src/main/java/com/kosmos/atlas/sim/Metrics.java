package com.kosmos.atlas.sim;

import com.kosmos.atlas.sim.util.Histogram;

/**
 * Central, allocation-free instrumentation point (spec §48, §49). Every field here is either a
 * primitive counter or a preallocated {@link Histogram} — recording a sample must never itself
 * become a source of the garbage-collection pauses this whole architecture exists to avoid
 * (spec §42.4: "Garbage-collection pauses should be treated as frame-time bugs").
 */
public final class Metrics {

    public final Histogram tickDuration = new Histogram();
    public final Histogram chunkGenerationDuration = new Histogram();
    public final Histogram saveDuration = new Histogram();

    private long chunksGenerated;
    private long chunksLoaded;
    private long chunksEvicted;
    private long commandsAccepted;
    private long commandsRejected;

    public void onChunkGenerated(long durationNanos) {
        chunksGenerated++;
        chunkGenerationDuration.record(durationNanos);
    }

    public void onChunkLoaded() {
        chunksLoaded++;
    }

    public void onChunkEvicted() {
        chunksEvicted++;
    }

    public void onCommandAccepted() {
        commandsAccepted++;
    }

    public void onCommandRejected() {
        commandsRejected++;
    }

    public long chunksGenerated() {
        return chunksGenerated;
    }

    public long chunksLoaded() {
        return chunksLoaded;
    }

    public long chunksEvicted() {
        return chunksEvicted;
    }

    public long commandsAccepted() {
        return commandsAccepted;
    }

    public long commandsRejected() {
        return commandsRejected;
    }

    /** Current JVM heap usage in bytes, for headless benchmark reports (spec §48: "Peak heap"). */
    public static long usedHeapBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
