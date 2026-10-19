package net.nerdsmp.paper.item;

import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

/** Add newly introduced recipe IDs without replacing operator-edited recipes or resurrecting removed old IDs. */
final class RecipeCatalogFiles {
    private RecipeCatalogFiles() { }

    static YamlConfiguration load(JavaPlugin plugin, String resource, String section, int version) {
        try (var input = new InputStreamReader(Objects.requireNonNull(plugin.getResource(resource)), StandardCharsets.UTF_8)) {
            YamlConfiguration bundled = new YamlConfiguration();
            bundled.load(input);
            return load(new File(plugin.getDataFolder(), resource), bundled, section, version,
                candidate -> validateRuntimeMaterials(candidate, section));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load recipe catalog " + resource + "; existing content was preserved", error);
        }
    }

    static YamlConfiguration load(File file, YamlConfiguration bundled, String section, int version) throws Exception {
        return load(file, bundled, section, version, ignored -> { });
    }

    private static YamlConfiguration load(File file, YamlConfiguration bundled, String section, int version,
                                         java.util.function.Consumer<YamlConfiguration> validateRuntime) throws Exception {
        if (file.exists() && !file.isFile()) throw new IllegalStateException("Recipe catalog is not a regular file");
        YamlConfiguration current = new YamlConfiguration();
        if (!file.exists()) {
            current.loadFromString(bundled.saveToString());
            validate(current, section, version);
            validateEntries(current, section);
            validateRuntime.accept(current);
            AtomicYamlFiles.save(current, file);
            return current;
        }
        current.load(file);
        int previous = validate(current, section, version);
        if (previous == version) { validateEntries(current, section); validateRuntime.accept(current); return current; }
        ConfigurationSection defaults = Objects.requireNonNull(bundled.getConfigurationSection(section));
        for (String id : defaults.getKeys(false)) {
            String path = section + "." + id;
            int introduced = bundled.getInt(path + ".introduced", 1);
            if (introduced > previous && !current.contains(path)) current.set(path, defaults.get(id));
        }
        current.set("schema-version", version);
        validateEntries(current, section);
        validateRuntime.accept(current);
        File backup = new File(file.getParentFile(), file.getName() + ".before-v" + version + ".bak");
        if (!backup.exists()) Files.copy(file.toPath(), backup.toPath());
        AtomicYamlFiles.save(current, file);
        return current;
    }

    // Paper 26.2 material traits read live registries. Keep them at this server-only boundary,
    // while schema/pattern validation and migration are independently testable without a server.
    private static void validateRuntimeMaterials(YamlConfiguration yaml, String section) {
        for (String id : yaml.getConfigurationSection(section).getKeys(false)) {
            var entry = yaml.getConfigurationSection(section + "." + id);
            if (section.equals("meals") && !org.bukkit.Material.matchMaterial(entry.getString("material")).isEdible())
                throw new IllegalArgumentException("Meal base must be edible: " + id);
            if (HarvestDish.ingredients(entry.getConfigurationSection("ingredients")).values().stream().anyMatch(m -> !m.isItem()))
                throw new IllegalArgumentException("Recipe ingredient must be an inventory item: " + id);
        }
    }

    private static void validateEntries(YamlConfiguration yaml, String section) {
        if (section.equals("meals")) { HarvestDish.load(yaml); return; }
        for (String id : yaml.getConfigurationSection(section).getKeys(false)) {
            if (!id.matches("[a-z0-9_]{1,48}")) throw new IllegalArgumentException("Invalid recipe ID: " + id);
            ConfigurationSection entry = Objects.requireNonNull(yaml.getConfigurationSection(section + "." + id), "Invalid recipe entry " + id);
            new net.nerdsmp.api.model.ItemId(entry.getString("result", id));
            net.nerdsmp.api.model.Skill.valueOf(entry.getString("skill", "smithing").toUpperCase(java.util.Locale.ROOT));
            int level = entry.getInt("level", 1);
            if (level < 1 || level > 50) throw new IllegalArgumentException("Invalid recipe level: " + id);
            HarvestDish.validateShape(entry.getStringList("shape"), HarvestDish.ingredients(entry.getConfigurationSection("ingredients")));
        }
    }

    private static int validate(YamlConfiguration yaml, String section, int version) {
        if (!yaml.isInt("schema-version") || yaml.getInt("schema-version") < 1
            || yaml.getInt("schema-version") > version || !yaml.isConfigurationSection(section)) {
            throw new IllegalStateException("Invalid or unsupported recipe catalog schema");
        }
        return yaml.getInt("schema-version");
    }
}
