package com.kosmos.atlas.sim.persistence;

import java.io.IOException;

/**
 * Thrown whenever a save file fails a structural check (bad magic, bad version, CRC mismatch, an
 * implausible declared length). A save file is untrusted input the moment it could have been
 * hand-edited, corrupted by a crash, or come from another machine — every load path must fail
 * loudly and cleanly rather than proceed with partially-read state.
 */
public final class SaveCorruptedException extends IOException {
    private static final long serialVersionUID = 1L;

    public SaveCorruptedException(String message) {
        super(message);
    }
}
