package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Template and run worlds. A run copies only the template's block, entity and POI region files into a
 * fresh world, so the copy never inherits the template's identity or level data, then loads it with
 * the template's generator. Run worlds never autosave and are deleted when the run ends.
 */
public final class InstanceWorlds {
    public static final String RUN_PREFIX = "nerdsmp_exp_run_";
    private static final List<String> COPIED = List.of("region", "entities", "poi");
    private final JavaPlugin plugin;
    private final AtomicLong counter = new AtomicLong(System.currentTimeMillis() % 100_000);

    public InstanceWorlds(JavaPlugin plugin) { this.plugin = plugin; }

    public static World.Environment environment(ExpeditionDefinition.Dimension dimension) {
        return switch (dimension) {
            case NETHER -> World.Environment.NETHER;
            case END -> World.Environment.THE_END;
            case OVERWORLD -> World.Environment.NORMAL;
        };
    }

    public static boolean isRunWorld(World world) { return world != null && world.getName().startsWith(RUN_PREFIX); }

    public static Path dimensionDirectory(String name) {
        if (!name.matches("[a-z0-9_.-]{1,96}")) throw new IllegalArgumentException("Invalid world name " + name);
        return Bukkit.getServer().getLevelDirectory().toAbsolutePath().normalize()
            .resolve("dimensions").resolve("minecraft").resolve(name);
    }

    public boolean templateExists(ExpeditionDefinition expedition) {
        World loaded = Bukkit.getWorld(NamespacedKey.minecraft(expedition.templateWorld()));
        return loaded != null || Files.isDirectory(dimensionDirectory(expedition.templateWorld()));
    }

    /** Loads (or creates) the template so staff can build in it. */
    public World openTemplate(ExpeditionDefinition expedition, BuildCanvas.Plan plan) {
        World loaded = Bukkit.getWorld(NamespacedKey.minecraft(expedition.templateWorld()));
        if (loaded != null) return loaded;
        World world = WorldCreator.ofKey(NamespacedKey.minecraft(expedition.templateWorld()))
            .environment(environment(expedition.dimension()))
            .generator(new TemplateGenerator(plan, expedition.biome(), expedition.dimension()))
            .generateStructures(false).createWorld();
        if (world == null) throw new IllegalStateException("Template world did not load: " + expedition.templateWorld());
        rules(world, false);
        return world;
    }

    /** Saves and unloads a template that nobody is standing in. */
    public void closeTemplate(ExpeditionDefinition expedition) {
        World loaded = Bukkit.getWorld(NamespacedKey.minecraft(expedition.templateWorld()));
        if (loaded != null && loaded.getPlayers().isEmpty()) Bukkit.unloadWorld(loaded, true);
    }

    /**
     * Copies the template off the main thread and then loads the run world on it. Must be called on the
     * main thread; the returned future completes on the main thread.
     */
    public CompletableFuture<World> createRun(ExpeditionDefinition expedition, BuildCanvas.Plan plan) {
        String name = RUN_PREFIX + expedition.id() + "_" + counter.incrementAndGet();
        World template = Bukkit.getWorld(NamespacedKey.minecraft(expedition.templateWorld()));
        if (template != null) template.save();
        Path source = dimensionDirectory(expedition.templateWorld());
        Path target = dimensionDirectory(name);
        if (template == null && !Files.isDirectory(source) && plan == null)
            return CompletableFuture.failedFuture(new IllegalStateException("The template world " + expedition.templateWorld() + " is missing."));
        CompletableFuture<World> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (Files.exists(target)) delete(target);
                Files.createDirectories(target);
                for (String folder : COPIED) if (Files.isDirectory(source.resolve(folder))) copy(source.resolve(folder), target.resolve(folder));
            } catch (IOException | UncheckedIOException failure) {
                result.completeExceptionally(failure);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    World world = WorldCreator.ofKey(NamespacedKey.minecraft(name))
                        .environment(environment(expedition.dimension()))
                        .generator(new TemplateGenerator(plan, expedition.biome(), expedition.dimension()))
                        .generateStructures(false).createWorld();
                    if (world == null) throw new IllegalStateException("Run world did not load: " + name);
                    rules(world, true);
                    result.complete(world);
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                    deleteLater(target);
                }
            });
        });
        return result;
    }

    /** Unloads a run world (players must already be gone) and deletes its folder. */
    public void destroy(World world) {
        if (!isRunWorld(world)) throw new IllegalArgumentException("Refusing to delete non-run world " + world.getName());
        Path directory = dimensionDirectory(world.getName());
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().warning("Could not unload expedition run world " + world.getName() + "; it will be removed on the next start.");
            return;
        }
        deleteLater(directory);
    }

    /** Removes run worlds left behind by a crash. Only unloaded folders with the run prefix are touched. */
    public void cleanupLeftovers() {
        Path root = Bukkit.getServer().getLevelDirectory().toAbsolutePath().normalize().resolve("dimensions").resolve("minecraft");
        if (!Files.isDirectory(root)) return;
        try (var folders = Files.list(root)) {
            for (Path folder : folders.toList()) {
                String name = folder.getFileName().toString();
                if (name.startsWith(RUN_PREFIX) && Bukkit.getWorld(NamespacedKey.minecraft(name)) == null) delete(folder);
            }
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not clean up old expedition run worlds", failure);
        }
    }

    private void deleteLater(Path directory) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                delete(directory);
            } catch (IOException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not delete expedition run world " + directory, failure);
            }
        });
    }

    private static void rules(World world, boolean run) {
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        world.setGameRule(GameRules.SPAWN_PATROLS, false);
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        world.setGameRule(GameRules.SPAWN_WARDENS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.PVP, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS, false);
        world.setStorm(false);
        world.setThundering(false);
        if (world.getEnvironment() == World.Environment.NORMAL) world.setTime(18_000);
        // Normal, not Hard: Hard multiplies mob melee and ability damage by 1.5, and tiers own difficulty here.
        world.setDifficulty(Difficulty.NORMAL);
        if (run) world.setAutoSave(false);
    }

    private static void copy(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void delete(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        if (!directory.getFileName().toString().startsWith(RUN_PREFIX)) throw new IOException("Refusing to delete " + directory);
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
