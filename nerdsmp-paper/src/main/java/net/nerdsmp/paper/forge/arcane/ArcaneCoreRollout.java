package net.nerdsmp.paper.forge.arcane;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Explicit, recoverable operator rollout. Never runs during startup and never enables runtime powers. */
public final class ArcaneCoreRollout {
    public static final List<String> SWITCHES = List.of("research", "imbuement", "attunement", "corruption", "overcharge");

    private ArcaneCoreRollout() { }

    public record Plan(byte[] original, String updated, ArcaneSettings settings, List<String> changes) {
        public Plan {
            original = original.clone();
            changes = List.copyOf(changes);
        }
        @Override public byte[] original() { return original.clone(); }
    }

    public static Plan preview(Path config) throws IOException, InvalidConfigurationException {
        byte[] original = Files.readAllBytes(config);
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.options().parseComments(true);
        candidate.loadFromString(new String(original, StandardCharsets.UTF_8));
        ArcaneSettings existing = ArcaneSettings.from(candidate);
        if (!existing.errors().isEmpty()) throw new IllegalArgumentException("Fix the Arcane config before rollout: " + String.join("; ", existing.errors()));
        List<String> changes = SWITCHES.stream()
            .map(feature -> "arcane-forge." + feature + ".enabled")
            .filter(path -> !Boolean.TRUE.equals(candidate.get(path))).toList();
        changes.forEach(path -> candidate.set(path, true));
        ArcaneSettings settings = ArcaneSettings.from(candidate);
        if (!settings.errors().isEmpty()) throw new IllegalArgumentException("Fix the Arcane config before rollout: " + String.join("; ", settings.errors()));
        return new Plan(original, candidate.saveToString(), settings, changes);
    }

    /** Backup and atomic replacement precede live publication. No non-atomic fallback is permitted. */
    public static Path commit(Path config, Plan plan) throws IOException {
        if (!Arrays.equals(Files.readAllBytes(config), plan.original())) throw new IOException("config.yml changed; review the rollout again");
        if (plan.changes().isEmpty()) return null;
        Path backup = config.resolveSibling(config.getFileName() + ".arcane-core-"
            + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8) + ".bak");
        writeNew(backup, plan.original());
        Path temporary = Files.createTempFile(config.toAbsolutePath().getParent(), ".arcane-core-", ".tmp");
        try {
            writeExisting(temporary, plan.updated().getBytes(StandardCharsets.UTF_8));
            if (!Arrays.equals(Files.readAllBytes(config), plan.original())) throw new IOException("config.yml changed during rollout; backup retained");
            Files.move(temporary, config, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return backup;
    }

    private static void writeNew(Path file, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            write(channel, bytes);
        }
    }

    private static void writeExisting(Path file, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            write(channel, bytes);
        }
    }

    private static void write(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
        channel.force(true);
    }
}
