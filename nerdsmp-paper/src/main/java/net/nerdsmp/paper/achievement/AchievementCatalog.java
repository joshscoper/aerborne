package net.nerdsmp.paper.achievement;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class AchievementCatalog {
    static final int SCHEMA_VERSION = 3;

    private AchievementCatalog() {
    }

    static Map<String, AchievementDefinition> load(JavaPlugin plugin) {
        YamlConfiguration yaml = content(plugin);
        ConfigurationSection root = yaml.getConfigurationSection("achievements");
        if (root == null) {
            throw new IllegalStateException("content/achievements.yml does not contain an achievements section");
        }
        Map<String, AchievementDefinition> definitions = new LinkedHashMap<>();
        for (String rawId : root.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            ConfigurationSection section = Objects.requireNonNull(root.getConfigurationSection(rawId));
            AchievementMetric metric = AchievementMetric.fromId(section.getString("metric", ""));
            Material icon = Material.matchMaterial(section.getString("icon", ""));
            AchievementDefinition definition = new AchievementDefinition(
                id,
                section.getString("title", title(id)),
                section.getString("description", ""),
                section.getString("category", "Journey"),
                metric,
                section.getLong("target"),
                section.getInt("points"),
                section.getLong("marks"),
                icon
            );
            if (definitions.putIfAbsent(id, definition) != null) {
                throw new IllegalStateException("Duplicate achievement id: " + id);
            }
        }
        if (definitions.isEmpty() || definitions.size() > 250) {
            throw new IllegalStateException("Achievement catalog must contain 1-250 entries");
        }
        return Map.copyOf(definitions);
    }

    private static YamlConfiguration content(JavaPlugin plugin) {
        File target = new File(plugin.getDataFolder(), "content/achievements.yml");
        if (!target.exists()) {
            plugin.saveResource("content/achievements.yml", false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(target); }
        catch (Exception error) { throw new IllegalStateException("Cannot read achievements; existing content was preserved", error); }
        int previous = yaml.getInt("schema-version", 0);
        if (!yaml.isInt("schema-version") || previous < 1 || previous > SCHEMA_VERSION || !yaml.isConfigurationSection("achievements")) throw new IllegalStateException("Unsupported achievement catalog");
        if (previous == SCHEMA_VERSION) return yaml;
        try (var reader = new InputStreamReader(
            Objects.requireNonNull(plugin.getResource("content/achievements.yml")), StandardCharsets.UTF_8
        )) {
            var bundled = new YamlConfiguration(); bundled.load(reader);
            for (String id : bundled.getConfigurationSection("achievements").getKeys(false)) {
                String path = "achievements." + id;
                if (bundled.getInt(path + ".introduced", 1) > previous && !yaml.contains(path)) yaml.set(path, bundled.get(path));
            }
            // Old IDs, edits and deliberately removed entries stay untouched. Validate all
            // definitions before persisting the additive catalog migration.
            for (String id : yaml.getConfigurationSection("achievements").getKeys(false)) {
                var entry = Objects.requireNonNull(yaml.getConfigurationSection("achievements." + id));
                new AchievementDefinition(id, entry.getString("title", title(id)), entry.getString("description", ""), entry.getString("category", "Journey"),
                    AchievementMetric.fromId(entry.getString("metric", "")), entry.getLong("target"), entry.getInt("points"), entry.getLong("marks"), Material.matchMaterial(entry.getString("icon", "")));
            }
            yaml.set("schema-version", SCHEMA_VERSION);
            java.nio.file.Path backup = target.toPath().resolveSibling(target.getName() + ".before-v3.bak");
            if (!java.nio.file.Files.exists(backup)) java.nio.file.Files.copy(target.toPath(), backup);
            net.nerdsmp.paper.storage.AtomicYamlFiles.save(yaml, target);
            return yaml;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load bundled achievement content", exception);
        }
    }

    private static String title(String id) {
        return Arrays.stream(id.split("_"))
            .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
            .collect(Collectors.joining(" "));
    }
}
