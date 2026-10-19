package net.nerdsmp.paper.travel;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/** Raw destinations remain durable even when their worlds are not loaded. Save before publishing any mutation. */
final class TravelStore {
    @FunctionalInterface interface Writer { void save(YamlConfiguration yaml, File file) throws IOException; }
    record Destination(String world, double x, double y, double z, float yaw, float pitch) {
        Destination {
            if (world == null || world.isBlank() || world.length() > 256 || world.codePoints().anyMatch(Character::isISOControl)
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new IllegalArgumentException("Invalid travel destination");
            }
        }
        static Destination from(Location location) {
            return new Destination(Objects.requireNonNull(location.getWorld(), "world").getUID().toString(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        }
        Optional<Location> resolve(Function<String, World> resolver) {
            World loaded = resolver.apply(world);
            return loaded == null ? Optional.empty() : Optional.of(new Location(loaded, x, y, z, yaw, pitch));
        }
    }
    private record Snapshot(Destination spawn, Map<String, Destination> warps, Map<String, String> originalNames) {
        private Snapshot { warps = Map.copyOf(warps); originalNames = Map.copyOf(originalNames); }
    }
    private final File file;
    private final Writer writer;
    private YamlConfiguration document;
    private Snapshot snapshot;

    TravelStore(File file) { this(file, TravelStore::atomicSave); }
    TravelStore(File file, Writer writer) {
        this.file = Objects.requireNonNull(file);
        this.writer = Objects.requireNonNull(writer);
        document = new YamlConfiguration();
        try {
            if (file.exists()) {
                if (!file.isFile()) throw new IOException("Travel data is not a regular file");
                document.load(file);
            }
            snapshot = parse(document);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load travel data; refusing to overwrite " + file, error);
        }
    }
    synchronized Optional<Destination> spawn() { return Optional.ofNullable(snapshot.spawn); }
    synchronized Optional<Destination> warp(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(snapshot.warps.get(name.toLowerCase(Locale.ROOT)));
    }
    synchronized List<String> names() { return snapshot.warps.keySet().stream().sorted().toList(); }
    synchronized void setSpawn(Destination destination) {
        Objects.requireNonNull(destination);
        commit(yaml -> write(yaml, "spawn", destination));
    }
    synchronized void setWarp(String name, Destination destination) {
        String normalized = normalizeName(name);
        Objects.requireNonNull(destination);
        String original = snapshot.originalNames.getOrDefault(normalized, normalized);
        commit(yaml -> write(yaml, "warps." + original, destination));
    }
    synchronized boolean deleteWarp(String name) {
        String normalized = normalizeName(name);
        String original = snapshot.originalNames.get(normalized);
        if (original == null) return false;
        commit(yaml -> yaml.set("warps." + original, null));
        return true;
    }

    private void commit(Consumer<YamlConfiguration> change) {
        try {
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.loadFromString(document.saveToString());
            candidate.set("schema", 1);
            change.accept(candidate);
            Snapshot next = parse(candidate);
            writer.save(candidate, file);
            document = candidate;
            snapshot = next;
        } catch (Exception error) {
            throw new IllegalStateException("Could not save travel data; previous destinations remain unchanged", error);
        }
    }

    private static Snapshot parse(YamlConfiguration yaml) {
        if (yaml.contains("schema") && (!yaml.isInt("schema") || yaml.getInt("schema") != 1)) {
            throw new IllegalArgumentException("Unsupported travel schema");
        }
        Destination spawn = read(yaml, "spawn");
        Map<String, Destination> warps = new LinkedHashMap<>();
        Map<String, String> originals = new LinkedHashMap<>();
        if (yaml.contains("warps") && !yaml.isConfigurationSection("warps")) {
            throw new IllegalArgumentException("Warps must be a mapping");
        }
        ConfigurationSection section = yaml.getConfigurationSection("warps");
        if (section != null) for (String original : section.getKeys(false)) {
            String normalized = normalizeName(original);
            Destination destination = Objects.requireNonNull(read(yaml, "warps." + original));
            if (warps.putIfAbsent(normalized, destination) != null) throw new IllegalArgumentException("Duplicate warp name: " + original);
            originals.put(normalized, original);
        }
        return new Snapshot(spawn, warps, originals);
    }

    private static Destination read(YamlConfiguration yaml, String path) {
        if (!yaml.contains(path)) return null;
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null || !(section.get("world") instanceof String world)) {
            throw new IllegalArgumentException("Invalid travel record: " + path);
        }
        return new Destination(world, number(section, "x", false), number(section, "y", false),
            number(section, "z", false), (float) number(section, "yaw", true), (float) number(section, "pitch", true));
    }
    private static double number(ConfigurationSection section, String key, boolean optional) {
        if (optional && !section.contains(key)) return 0;
        if (!(section.get(key) instanceof Number number)) throw new IllegalArgumentException("Invalid travel coordinate: " + key);
        return number.doubleValue();
    }
    private static void write(YamlConfiguration yaml, String path, Destination destination) {
        // Preserve unrelated metadata even within a destination record.
        yaml.set(path + ".world", destination.world);
        yaml.set(path + ".x", destination.x); yaml.set(path + ".y", destination.y); yaml.set(path + ".z", destination.z);
        yaml.set(path + ".yaw", destination.yaw); yaml.set(path + ".pitch", destination.pitch);
    }
    private static String normalizeName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,24}")) throw new IllegalArgumentException("Invalid warp name");
        return name.toLowerCase(Locale.ROOT);
    }
    private static void atomicSave(YamlConfiguration yaml, File target) throws IOException {
        Path destination = target.toPath().toAbsolutePath();
        Path parent = destination.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "travel-", ".tmp");
        try {
            byte[] bytes = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
