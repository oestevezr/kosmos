package com.kosmos.atlas.sim.persistence;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Writes a file via temp-file + fsync + atomic rename, so a crash or power loss mid-save can
 * never leave a half-written file where the previous good save used to be (spec §47: "Avoid
 * pausing the render thread for full-world saves" / recover safely from interrupted saves).
 */
public final class AtomicFileWriter {

    private AtomicFileWriter() {
    }

    public static void write(Path target, IOConsumer<OutputStream> writer) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.accept(os);
            os.flush();
        }
        // fsync the temp file's contents before the rename is visible.
        try (var fc = java.nio.channels.FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            fc.force(true);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
