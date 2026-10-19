package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/** Each authored revision owns a separate world and manifest; older worlds are never regenerated in place. */
public final class ExpeditionWorld {
    static final int CURRENT_REVISION = 2;
    private ExpeditionWorld() { }

    /** Restore only recorded worlds for saved warps/NPCs. Never create a new revision in manual mode. */
    public static java.util.List<World> openExisting(JavaPlugin plugin, String season) throws Exception {
        java.util.List<World> restored = new java.util.ArrayList<>();
        for (int revision : new int[]{1, CURRENT_REVISION}) {
            File manifest = manifestFile(plugin.getDataFolder(), season, revision);
            if (!manifest.exists()) continue;
            if (!manifest.isFile()) throw new IllegalStateException("Recorded expedition manifest is not a file: " + manifest);
            YamlConfiguration recorded = new YamlConfiguration(); recorded.load(manifest);
            if (!recorded.contains("uuid")) throw new IllegalStateException("Recorded expedition world has no UUID; restore or review its manifest: " + manifest);
            restored.add(openRevision(plugin, season, revision, true));
        }
        return java.util.List.copyOf(restored);
    }

    public static World open(JavaPlugin plugin, String season) throws Exception {
        // Load the former world before travel destinations and manual NPC bodies resolve their saved UUIDs.
        File legacyManifest = manifestFile(plugin.getDataFolder(), season, 1);
        String legacyName = worldName(season, 1);
        Path legacyDirectory = dimensionDirectory(Bukkit.getServer().getLevelDirectory(), legacyName);
        if (legacyManifest.exists()) openRevision(plugin, season, 1);
        else if (legacyDirectory.toFile().exists() || new File(Bukkit.getWorldContainer(), legacyName).exists()
            || Bukkit.getWorld(NamespacedKey.minecraft(legacyName)) != null) {
            throw new IllegalStateException("Legacy expedition world has no manifest; restore it before opening revision 2");
        }
        return openRevision(plugin, season, CURRENT_REVISION);
    }

    private static World openRevision(JavaPlugin plugin, String season, int revision) throws Exception {
        return openRevision(plugin, season, revision, false);
    }
    private static World openRevision(JavaPlugin plugin, String season, int revision, boolean preserve) throws Exception {
        String name = worldName(season, revision);
        NamespacedKey key = NamespacedKey.minecraft(name);
        File manifest = manifestFile(plugin.getDataFolder(), season, revision);
        Path directory = dimensionDirectory(Bukkit.getServer().getLevelDirectory(), name);
        YamlConfiguration yaml = prepareManifest(manifest, name, revision, directory,
            new File(Bukkit.getWorldContainer(), name).exists() || Bukkit.getWorld(key) != null);
        ChunkGenerator generator = revision == 1 ? new ExpeditionGenerator() : new ExpeditionGeneratorV2();
        World world = WorldCreator.ofKey(key).environment(World.Environment.NORMAL).generator(generator)
            .generateStructures(false).createWorld();
        if (world == null) throw new IllegalStateException("Expedition world did not load: " + name);
        verifyLoadedWorld(yaml, key.toString(), directory, world.getKey().toString(), world.getWorldPath(), world.getUID());
        if (preserve || revision == 1) return world; // Preserve recorded worlds, spawn and administrator-selected rules.
        if (!yaml.contains("uuid")) {
            yaml.set("uuid", world.getUID().toString());
            AtomicYamlFiles.save(yaml, manifest);
        }
        world.setSpawnLocation(0, 97, 0);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRules.PVP, false);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setStorm(false);
        world.setThundering(false);
        return world;
    }

    static String worldName(String season, int revision) {
        validateSeason(season);
        validateRevision(revision);
        return "nerdsmp_expeditions_" + season.toLowerCase(Locale.ROOT) + (revision == 1 ? "" : "_r2");
    }

    static File manifestFile(File dataFolder, String season, int revision) {
        validateSeason(season);
        validateRevision(revision);
        return new File(dataFolder, "expeditions/" + season + (revision == 1 ? "-world.yml" : "-world-r2.yml"));
    }

    /** The only manifest write here is creation of a new revision-2 manifest after ownership checks. */
    static YamlConfiguration prepareManifest(File manifest, String name, int revision, Path directory,
                                               boolean alternativeLocationOrLoadedWorldExists) throws Exception {
        validateRevision(revision);
        YamlConfiguration yaml = new YamlConfiguration();
        if (manifest.exists()) {
            yaml.load(manifest);
            if (!yaml.isInt("schema") || yaml.getInt("schema") != 1
                || !yaml.isInt("layout") || yaml.getInt("layout") != revision
                || !yaml.isInt("template") || yaml.getInt("template") != revision
                || !name.equals(yaml.getString("world"))) {
                throw new IllegalStateException("Expedition world manifest mismatch: " + manifest);
            }
            if (yaml.contains("uuid")) {
                UUID.fromString(yaml.getString("uuid"));
                if (!directory.toFile().isDirectory()) {
                    throw new IllegalStateException("Expedition world is missing; restore its backup: " + name);
                }
            } else if (revision == 1) {
                throw new IllegalStateException("Legacy expedition world UUID is missing; preserve it for review");
            }
        } else {
            if (revision == 1) throw new IllegalStateException("Legacy expedition world manifest is missing");
            if (directory.toFile().exists() || alternativeLocationOrLoadedWorldExists) {
                throw new IllegalStateException("Refusing to adopt existing world " + name);
            }
            yaml.set("schema", 1);
            yaml.set("layout", revision);
            yaml.set("template", revision);
            yaml.set("world", name);
            AtomicYamlFiles.save(yaml, manifest);
        }
        return yaml;
    }

    static void verifyLoadedWorld(YamlConfiguration manifest, String expectedKey, Path expectedDirectory,
                                   String actualKey, Path actualDirectory, UUID actualId) {
        if (!expectedKey.equals(actualKey)
            || !actualDirectory.toAbsolutePath().normalize().equals(expectedDirectory.toAbsolutePath().normalize())) {
            throw new IllegalStateException("Expedition world storage path changed; review the Paper storage layout before continuing");
        }
        if (manifest.contains("uuid") && !actualId.toString().equals(manifest.getString("uuid"))) {
            throw new IllegalStateException("Expedition world UUID changed; restore its backup");
        }
    }

    private static void validateSeason(String season) {
        if (season == null || !season.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid expedition season");
        }
    }

    private static void validateRevision(int revision) {
        if (revision != 1 && revision != CURRENT_REVISION) throw new IllegalArgumentException("Unsupported expedition revision");
    }

    /** Paper 26.1+ stores all dimensions inside the server's configured level directory. */
    static Path dimensionDirectory(Path levelDirectory, String name) {
        if (name == null || !name.matches("nerdsmp_expeditions_[a-z0-9_-]{1,64}(?:_r2)?")) {
            throw new IllegalArgumentException("Invalid expedition world name");
        }
        return levelDirectory.toAbsolutePath().normalize().resolve("dimensions").resolve("minecraft").resolve(name);
    }
}
