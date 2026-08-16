package com.kosmos.atlas.sim.world;

/**
 * Streaming radii and target frame rate per hardware tier (spec §27, §28, §46).
 *
 * <p>Profiles change representation fidelity and how much of the world is kept resident, never
 * simulation rules — economic/terrain outcomes must stay identical across profiles (spec §28,
 * §46: "Hardware settings should primarily change visual fidelity... not economic rules").
 */
public enum HardwareProfile {

    LOW(2, 3, 5, 30),
    MEDIUM(3, 4, 6, 60),
    HIGH(4, 6, 9, 60);

    /** Radius, in chunks, considered "high detail" (spec §27: "High-detail: 3x3 chunks"). */
    public final int activeRadius;
    /** Radius, in chunks, kept generated/resident but not necessarily fully detailed. */
    public final int preloadRadius;
    /** Radius beyond which loaded chunks are evicted back to the pool. */
    public final int unloadRadius;
    public final int targetFps;

    HardwareProfile(int activeRadius, int preloadRadius, int unloadRadius, int targetFps) {
        this.activeRadius = activeRadius;
        this.preloadRadius = preloadRadius;
        this.unloadRadius = unloadRadius;
        this.targetFps = targetFps;
    }

    /** Upper bound on concurrently loaded chunks, used to size {@link ChunkPool}/{@link ChunkStore}. */
    public int maxLoadedChunks() {
        int span = unloadRadius * 2 + 1;
        return span * span;
    }
}
