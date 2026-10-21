package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Where each player stood before entering a run. Kept on disk so a crash or restart mid-run still
 * sends players home when they next join, even though the run world itself is gone.
 */
final class ReturnStore {
    private final File file;
    private final Logger logger;
    private final YamlConfiguration yaml;

    ReturnStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.yaml = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    synchronized void remember(UUID player, Location location) {
        if (location == null || location.getWorld() == null || InstanceWorlds.isRunWorld(location.getWorld())) return;
        String path = "returns." + player;
        yaml.set(path + ".world", location.getWorld().getKey().toString());
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", (double) location.getYaw());
        yaml.set(path + ".pitch", (double) location.getPitch());
        save();
    }

    synchronized Optional<Location> location(UUID player) {
        String path = "returns." + player;
        if (!yaml.contains(path)) return Optional.empty();
        var key = org.bukkit.NamespacedKey.fromString(yaml.getString(path + ".world", ""));
        World world = key == null ? null : Bukkit.getWorld(key);
        if (world == null) return Optional.empty();
        return Optional.of(new Location(world, yaml.getDouble(path + ".x"), yaml.getDouble(path + ".y"), yaml.getDouble(path + ".z"),
            (float) yaml.getDouble(path + ".yaw"), (float) yaml.getDouble(path + ".pitch")));
    }

    synchronized boolean has(UUID player) { return yaml.contains("returns." + player); }

    synchronized void forget(UUID player) {
        if (!yaml.contains("returns." + player)) return;
        yaml.set("returns." + player, null);
        save();
    }

    private void save() {
        try {
            AtomicYamlFiles.save(yaml, file);
        } catch (IOException failure) {
            logger.warning("Could not save expedition return points: " + failure.getMessage());
        }
    }
}
