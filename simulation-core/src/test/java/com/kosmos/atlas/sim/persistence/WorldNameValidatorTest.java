package com.kosmos.atlas.sim.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards against CWE-22 path traversal through a player-supplied world name (spec §8). */
class WorldNameValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "../../etc/passwd",
        "../secret",
        "..",
        "/etc/passwd",
        "a/../../b",
        "name/with/slash",
        "name\\with\\backslash",
        "",
        "this-name-is-way-too-long-to-be-a-reasonable-world-name-and-should-be-rejected-outright-ok",
        "emoji😀name",
    })
    void maliciousOrInvalidNamesAreRejected(String name) {
        assertThrows(SaveCorruptedException.class, () -> WorldNameValidator.validateName(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Riverlands", "My World 1", "iron-valley", "world_42"})
    void reasonableNamesAreAccepted(String name) {
        assertDoesNotThrow(() -> WorldNameValidator.validateName(name));
    }

    @Test
    void resolvedPathAlwaysStaysInsideSavesRoot(@TempDir Path savesRoot) throws IOException {
        Path resolved = WorldNameValidator.resolveWorldDir(savesRoot, "Riverlands");
        assertTrue(resolved.normalize().startsWith(savesRoot.toAbsolutePath().normalize()));
    }

    @Test
    void traversalAttemptViaResolveWorldDirIsRejected(@TempDir Path savesRoot) {
        assertThrows(SaveCorruptedException.class,
            () -> WorldNameValidator.resolveWorldDir(savesRoot, "../outside"));
    }
}
