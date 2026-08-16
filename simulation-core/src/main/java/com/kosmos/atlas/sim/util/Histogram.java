package com.kosmos.atlas.sim.util;

/**
 * Fixed-bucket histogram over {@code long} values (nanoseconds, bytes, whatever), used to report
 * p50/p95/p99 without ever allocating per sample (spec §45, §49: frame-time and profiling gates
 * must be measured, not guessed). Buckets are exponential so both microsecond frame times and
 * multi-second worst cases fit in the same fixed-size array.
 */
public final class Histogram {

    private final long[] bucketUpperBoundNanos;
    private final long[] counts;
    private long total;
    private long sum;
    private long max;

    public Histogram() {
        // 48 exponential buckets from ~1us to ~1.4 days — generous enough for any frame/tick metric.
        int bucketCount = 48;
        bucketUpperBoundNanos = new long[bucketCount];
        long bound = 1_000; // 1 microsecond
        for (int i = 0; i < bucketCount; i++) {
            bucketUpperBoundNanos[i] = bound;
            bound *= 2;
        }
        counts = new long[bucketCount];
    }

    public void record(long valueNanos) {
        int idx = 0;
        while (idx < bucketUpperBoundNanos.length - 1 && valueNanos > bucketUpperBoundNanos[idx]) {
            idx++;
        }
        counts[idx]++;
        total++;
        sum += valueNanos;
        if (valueNanos > max) {
            max = valueNanos;
        }
    }

    public void reset() {
        java.util.Arrays.fill(counts, 0);
        total = 0;
        sum = 0;
        max = 0;
    }

    public long count() {
        return total;
    }

    public long maxNanos() {
        return max;
    }

    public double meanNanos() {
        return total == 0 ? 0.0 : (double) sum / total;
    }

    /** Approximate percentile (e.g. 0.95 for p95), accurate to the containing bucket's upper bound. */
    public long percentileNanos(double percentile) {
        if (total == 0) {
            return 0;
        }
        long target = (long) Math.ceil(total * percentile);
        long running = 0;
        for (int i = 0; i < counts.length; i++) {
            running += counts[i];
            if (running >= target) {
                return bucketUpperBoundNanos[i];
            }
        }
        return bucketUpperBoundNanos[bucketUpperBoundNanos.length - 1];
    }
}
