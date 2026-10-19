package net.nerdsmp.paper.skyblock;

import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.nio.file.Path;

/** Dedicated world manifest prevents a mistyped name from taking over an existing world. */
public final class IslandWorld {
    private IslandWorld() { }
    public static World open(JavaPlugin plugin, String season) throws Exception {
        if (!season.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid island season");
        String name = "nerdsmp_skyblock_" + season.toLowerCase(java.util.Locale.ROOT);
        NamespacedKey key = NamespacedKey.minecraft(name);
        File manifest = new File(plugin.getDataFolder(), "skyblock/" + season + "-world.yml");
        Path directory = dimensionDirectory(Bukkit.getServer().getLevelDirectory(), name);
        YamlConfiguration yaml = new YamlConfiguration();
        if (manifest.exists()) {
            yaml.load(manifest);
            if (yaml.getInt("schema") != 1 || yaml.getInt("layout") != 1 || !name.equals(yaml.getString("world")))
                throw new IllegalStateException("Island world manifest mismatch");
            if (yaml.contains("uuid") && !directory.toFile().isDirectory()) throw new IllegalStateException("Island world is missing; restore its backup");
        } else {
            if (directory.toFile().exists() || new File(Bukkit.getWorldContainer(), name).exists() || Bukkit.getWorld(key) != null)
                throw new IllegalStateException("Refusing to adopt existing world " + name);
            yaml.set("schema", 1); yaml.set("layout", 1); yaml.set("world", name);
            yaml.set("template", IslandGenerator.CURRENT_TEMPLATE);
            AtomicYamlFiles.save(yaml, manifest);
        }
        int template = yaml.getInt("template", 1); // Missing field belongs to the original worlds.
        World world = WorldCreator.ofKey(key).environment(World.Environment.NORMAL).generator(new IslandGenerator(template))
            .generateStructures(false).createWorld();
        if (world == null) throw new IllegalStateException("Island world did not load");
        if (!world.getKey().equals(key) || !world.getWorldPath().toAbsolutePath().normalize().equals(directory))
            throw new IllegalStateException("Island world storage path changed; review the Paper storage layout before continuing");
        if (yaml.contains("uuid") && !world.getUID().toString().equals(yaml.getString("uuid")))
            throw new IllegalStateException("Island world UUID changed; restore its backup");
        if (!yaml.contains("uuid")) { yaml.set("uuid", world.getUID().toString()); AtomicYamlFiles.save(yaml, manifest); }
        world.setSpawnLocation(8, IslandGrid.SURFACE_Y + 1, 8);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRules.PVP, false);
        return world;
    }

    /** Paper 26.1+ stores all dimensions inside the server's configured level directory. */
    static Path dimensionDirectory(Path levelDirectory, String name) {
        if (!name.matches("nerdsmp_skyblock_[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid island world name");
        return levelDirectory.toAbsolutePath().normalize().resolve("dimensions").resolve("minecraft").resolve(name);
    }
}
