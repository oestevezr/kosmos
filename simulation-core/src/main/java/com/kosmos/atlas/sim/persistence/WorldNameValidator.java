package com.kosmos.atlas.sim.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Guards the save/world-name -> filesystem-path boundary against path traversal (CWE-22).
 *
 * <p>A world name ultimately becomes a directory name under the saves root. Without validation, a
 * name like {@code "../../etc"} or an absolute path could escape the saves directory entirely.
 * Two independent layers are enforced: (1) a strict whitelist on the raw name, and (2) after
 * resolving the final path, a check that it is still contained within the saves root — belt and
 * suspenders against any normalization edge case.
 */
public final class WorldNameValidator {

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9 _-]{1,64}");

    private WorldNameValidator() {
    }

    public static void validateName(String worldName) throws IOException {
        if (worldName == null || !ALLOWED.matcher(worldName).matches()) {
            throw new SaveCorruptedException("Invalid world name: " + worldName);
        }
    }

    /** Resolves {@code worldName} under {@code savesRoot}, guaranteeing the result stays inside it. */
    public static Path resolveWorldDir(Path savesRoot, String worldName) throws IOException {
        validateName(worldName);
        Path root = savesRoot.toAbsolutePath().normalize();
        Path resolved = root.resolve(worldName).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new SaveCorruptedException("World path escapes saves root: " + worldName);
        }
        return resolved;
    }
}
