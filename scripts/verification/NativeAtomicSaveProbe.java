package net.nerdsmp.probe;

import com.sun.nio.file.ExtendedOpenOption;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Exercises actual Windows sharing violations in a caller-provided disposable fixture directory. */
public final class NativeAtomicSaveProbe {
    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name").startsWith("Windows")) throw new IllegalStateException("Windows fixture only");
        Path directory = Path.of(args[0]).toAbsolutePath().normalize();
        if (!directory.getFileName().toString().equals("native-save-probe")) throw new IllegalArgumentException("Dedicated fixture directory required");
        Files.createDirectory(directory);
        AtomicYamlFiles.write("warmup\n", directory.resolve("warmup.yml").toFile());
        int[] recoveries = {0};
        var logger = java.util.logging.Logger.getLogger(AtomicYamlFiles.class.getName());
        var handler = new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord record) { if (record.getMessage().contains("recovered after")) recoveries[0]++; }
            public void flush() { }
            public void close() { }
        };
        logger.addHandler(handler);
        Path target = directory.resolve("player.yml");
        Files.writeString(target,"value: old\n");
        FileChannel transientLock = FileChannel.open(target, Set.of(StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE));
        Thread release = Thread.ofPlatform().start(() -> {
            try { Thread.sleep(35); transientLock.close(); } catch (Exception failure) { throw new RuntimeException(failure); }
        });
        try { AtomicYamlFiles.write("value: recovered\n",target.toFile()); }
        finally { transientLock.close(); release.join(); logger.removeHandler(handler); }
        if (recoveries[0] != 1) throw new AssertionError("Fixture must observe an actual sharing-denial retry before recovery");
        if (!Files.readString(target).equals("value: recovered\n")) throw new AssertionError("Transient sharing denial did not recover");
        int checks = 1;
        try (FileChannel permanentLock = FileChannel.open(target,Set.of(StandardOpenOption.READ,ExtendedOpenOption.NOSHARE_DELETE))) {
            boolean denied = false;
            long start = System.nanoTime();
            try { AtomicYamlFiles.write("value: must-not-publish\n",target.toFile()); }
            catch (AccessDeniedException expected) { denied = true; }
            long elapsed = (System.nanoTime()-start)/1_000_000;
            if (!denied || elapsed>2000) throw new AssertionError("Permanent denial must fail with bounded retries: "+elapsed+"ms");
            checks++;
        }
        if (!Files.readString(target).equals("value: recovered\n")) throw new AssertionError("Failed replacement changed committed document");
        checks++;
        if (Files.exists(directory.resolve("player.yml.tmp"))) throw new AssertionError("Temporary file leaked");
        checks++;
        String result = "PASS "+checks+" native Windows save checks: transient recovery, bounded permanent denial, old document retained, temporary file cleaned\n";
        Files.writeString(directory.resolve("result.txt"), result);
        System.out.print(result);
    }
}
