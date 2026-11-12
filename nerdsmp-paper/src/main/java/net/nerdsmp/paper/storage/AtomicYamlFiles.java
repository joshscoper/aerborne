package net.nerdsmp.paper.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicYamlFiles {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(AtomicYamlFiles.class.getName());
    private static final long[] RETRY_MILLIS = {10, 20, 40, 80};

    @FunctionalInterface interface Replacement { void move(Path source, Path target) throws IOException; }
    @FunctionalInterface interface Pause { void sleep(long millis) throws InterruptedException; }
    private AtomicYamlFiles() {
    }

    public static void save(YamlConfiguration configuration, File target) throws IOException {
        write(configuration.saveToString(), target);
    }

    /**
     * Writes already-serialized YAML. Lets callers serialize on the main thread (item stacks and
     * other live objects) and do only the disk work elsewhere.
     */
    public static void write(String content, File target) throws IOException {
        write(content, target, AtomicYamlFiles::replace, Thread::sleep);
    }

    static void write(String content, File target, Replacement replacement, Pause pause) throws IOException {
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        Path temporary = new File(parent, target.getName() + ".tmp").toPath();
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            for (int retry = 0; ; retry++) {
                try {
                    replacement.move(temporary, target.toPath());
                    if (retry > 0) LOGGER.warning("Atomic YAML replacement recovered after " + retry + " retries: " + target);
                    return;
                } catch (AccessDeniedException denied) {
                    if (retry == RETRY_MILLIS.length) {
                        LOGGER.warning("Atomic YAML replacement denied after " + (retry + 1) + " attempts: " + target);
                        throw denied;
                    }
                    try { pause.sleep(RETRY_MILLIS[retry]); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while replacing " + target, interrupted);
                    }
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
