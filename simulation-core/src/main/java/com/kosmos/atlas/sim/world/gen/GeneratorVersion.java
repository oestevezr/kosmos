package com.kosmos.atlas.sim.world.gen;

/**
 * The world generation algorithm version compiled into this build.
 *
 * <p>Every save stores the generator version it was created with (spec §32). If terrain
 * algorithms ever change, this constant must be bumped rather than modified in place —
 * old saves stay pinned to the version they were generated with unless explicitly migrated.
 */
public final class GeneratorVersion {

    public static final int CURRENT = 1;

    private GeneratorVersion() {
    }
}
