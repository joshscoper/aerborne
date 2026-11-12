package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** A separate, operator-editable catalog; existing files are validated without replacing edits. */
record MetallurgyCatalog(List<Smelting> smelting, List<CustomRecipeService.RecipeDefinition> crafting) {
    MetallurgyCatalog { smelting = List.copyOf(smelting); crafting = List.copyOf(crafting); }

    record Smelting(String id, ItemId input, ItemId result, int ticks, float experience, long smithingExperience) { }

    /** Bump when the bundled recipes change: an older file gets the new ids and keeps every recipe it has. */
    private static final int VERSION = 2;

    static MetallurgyCatalog load(JavaPlugin plugin, PaperItemService items) {
        File file = new File(plugin.getDataFolder(), "content/metallurgy.yml");
        try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource("content/metallurgy.yml")), StandardCharsets.UTF_8)) {
            if (java.nio.file.Files.isSymbolicLink(file.toPath()) || file.exists() && !file.isFile())
                throw new IllegalStateException("Metallurgy catalog is not a regular file");
            var bundled = new YamlConfiguration();
            bundled.load(reader);
            var yaml = bundled;
            if (file.exists()) {
                yaml = new YamlConfiguration();
                yaml.load(file);
            }
            boolean older = upgrade(yaml, bundled);
            MetallurgyCatalog catalog = parse(yaml, id -> items.itemMaterial(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown metallurgy item: " + id)));
            if (!file.exists() || older) AtomicYamlFiles.save(yaml, file);
            return catalog;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load metallurgy catalog; existing content was preserved", error);
        }
    }

    /** Fills recipes missing from an older file. Whether it did anything, so the caller can save. */
    static boolean upgrade(YamlConfiguration yaml, YamlConfiguration bundled) {
        if (!yaml.isInt("schema-version") || yaml.getInt("schema-version") >= VERSION) return false;
        for (String section : List.of("smelting", "crafting")) {
            var defaults = bundled.getConfigurationSection(section);
            for (String id : defaults.getKeys(false))
                if (!yaml.contains(section + "." + id)) yaml.set(section + "." + id, defaults.get(id));
        }
        yaml.set("schema-version", VERSION);
        return true;
    }

    static MetallurgyCatalog parse(YamlConfiguration yaml, Function<ItemId, Material> materialFor) {
        if (!yaml.isInt("schema-version") || yaml.getInt("schema-version") != VERSION
            || !yaml.isConfigurationSection("smelting") || !yaml.isConfigurationSection("crafting")
            || !java.util.Set.of("schema-version", "smelting", "crafting").containsAll(yaml.getKeys(false)))
            throw new IllegalArgumentException("Invalid metallurgy schema");
        var smelting = new ArrayList<Smelting>();
        var crafting = new ArrayList<CustomRecipeService.RecipeDefinition>();
        var inputs = new HashSet<ItemId>();
        var outputs = new HashSet<ItemId>();
        var smelts = yaml.getConfigurationSection("smelting");
        if (smelts.getKeys(false).size() > 100) throw new IllegalArgumentException("Too many smelting recipes");
        for (String id : smelts.getKeys(false)) {
            identifier(id);
            var entry = Objects.requireNonNull(smelts.getConfigurationSection(id));
            var input = new ItemId(entry.getString("input"));
            var result = new ItemId(entry.getString("result"));
            Objects.requireNonNull(materialFor.apply(input)); Objects.requireNonNull(materialFor.apply(result));
            int ticks = entry.getInt("ticks", 200);
            double xp = entry.getDouble("experience", .7);
            long smithingXp = entry.getLong("smithing-xp", 8);
            if (input.equals(result) || !inputs.add(input) || !outputs.add(result) || ticks < 20 || ticks > 2400
                || !Double.isFinite(xp) || xp < 0 || xp > 10 || smithingXp < 1 || smithingXp > 1000)
                throw new IllegalArgumentException("Invalid smelting recipe: " + id);
            smelting.add(new Smelting(id, input, result, ticks, (float) xp, smithingXp));
        }
        var crafts = yaml.getConfigurationSection("crafting");
        if (crafts.getKeys(false).size() > 256) throw new IllegalArgumentException("Too many metallurgy recipes");
        for (String id : crafts.getKeys(false)) {
            identifier(id);
            var entry = Objects.requireNonNull(crafts.getConfigurationSection(id));
            var result = new ItemId(entry.getString("result"));
            Objects.requireNonNull(materialFor.apply(result));
            Map<Character, Material> materials = new LinkedHashMap<>();
            Map<Character, ItemId> custom = new LinkedHashMap<>();
            ConfigurationSection ingredients = Objects.requireNonNull(entry.getConfigurationSection("ingredients"));
            for (String symbol : ingredients.getKeys(false)) {
                if (symbol.length() != 1 || symbol.equals(" ")) throw new IllegalArgumentException("Invalid recipe symbol: " + id);
                String value = Objects.requireNonNull(ingredients.getString(symbol));
                Material material;
                if (value.startsWith("item:")) {
                    ItemId item = new ItemId(value.substring(5));
                    material = Objects.requireNonNull(materialFor.apply(item));
                    custom.put(symbol.charAt(0), item);
                } else {
                    material = Material.matchMaterial(value);
                    if (material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR)
                        throw new IllegalArgumentException("Invalid vanilla ingredient: " + id);
                }
                materials.put(symbol.charAt(0), material);
            }
            if (custom.isEmpty()) throw new IllegalArgumentException("Metallurgy recipes must use a custom material: " + id);
            crafting.add(new CustomRecipeService.RecipeDefinition("metallurgy_" + id, result,
                Skill.valueOf(entry.getString("skill", "smithing").toUpperCase(Locale.ROOT)), entry.getInt("level", 1),
                entry.getStringList("shape"), materials, custom));
        }
        return new MetallurgyCatalog(smelting, crafting);
    }

    private static void identifier(String id) {
        if (!id.matches("[a-z0-9_]{1,36}")) throw new IllegalArgumentException("Invalid metallurgy recipe ID: " + id);
    }
}
