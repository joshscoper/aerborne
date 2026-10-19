package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.paper.engine.ItemBlueprint;
import net.nerdsmp.paper.engine.ItemStats;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.function.Consumer;

/** Installs a separate expansion once. Existing operator files are validated, never rewritten or refilled. */
final class AdditiveItemCatalogFiles {
    private static final Set<String> ITEM_FIELDS = Set.of("material", "name", "set", "slot", "category", "rarity",
        "model", "sockets", "lore", "stats", "item-level", "unique-affix", "acquisition");
    private AdditiveItemCatalogFiles() { }

    record Result(YamlConfiguration merged, Map<ItemId, PaperItemService.ExpansionEntry> entries) {
        Result { entries = Map.copyOf(entries); }
    }

    static Result load(File file, String bundled, YamlConfiguration baseline,
                       Consumer<YamlConfiguration> validateMerged) throws Exception {
        if (Files.isSymbolicLink(file.toPath()) || (file.exists() && !file.isFile()))
            throw new IllegalStateException("Item expansion is not a regular file");
        boolean exists = file.exists();
        String raw = exists ? Files.readString(file.toPath()) : bundled;
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setCodePointLimit(2_000_000);
        Object parsed = new Yaml(new SafeConstructor(options)).load(raw);
        if (!(parsed instanceof Map<?, ?>)) throw new IllegalStateException("Item expansion must be a mapping");
        YamlConfiguration expansion = new YamlConfiguration();
        expansion.loadFromString(raw);
        if (!expansion.isInt("schema-version") || expansion.getInt("schema-version") != 1
            || !expansion.isConfigurationSection("sets") || !expansion.isConfigurationSection("items"))
            throw new IllegalStateException("Unsupported item expansion schema");
        if (!Set.of("schema-version", "sets", "items").containsAll(expansion.getKeys(false)))
            throw new IllegalStateException("Unknown item expansion section");

        YamlConfiguration merged = new YamlConfiguration();
        merged.loadFromString(baseline.saveToString());
        if (!merged.isConfigurationSection("sets") || !merged.isConfigurationSection("relics"))
            throw new IllegalStateException("Missing baseline item sections");
        Set<String> occupiedSets = new java.util.HashSet<>();
        merged.getConfigurationSection("sets").getKeys(false).forEach(id -> occupiedSets.add(id.toLowerCase(Locale.ROOT)));
        Set<String> occupiedItems = new java.util.HashSet<>();
        merged.getConfigurationSection("relics").getKeys(false).forEach(id -> occupiedItems.add(new ItemId(id).value()));
        var sets = expansion.getConfigurationSection("sets");
        for (String id : sets.getKeys(false)) {
            identifier(id);
            if (!occupiedSets.add(id)) throw new IllegalStateException("Duplicate item set ID: " + id);
            validateSet(sets.getConfigurationSection(id), id);
            merged.set("sets." + id, sets.get(id));
        }
        Map<ItemId, PaperItemService.ExpansionEntry> entries = new HashMap<>();
        var items = expansion.getConfigurationSection("items");
        if (items.getKeys(false).size() > 512) throw new IllegalStateException("Too many expansion items");
        for (String id : items.getKeys(false)) {
            identifier(id);
            if (id.startsWith("crafted_") || !occupiedItems.add(id))
                throw new IllegalStateException("Duplicate or reserved item ID: " + id);
            ConfigurationSection item = items.getConfigurationSection(id);
            if (item == null || !ITEM_FIELDS.containsAll(item.getKeys(false)))
                throw new IllegalStateException("Invalid expansion item: " + id);
            Material material = item.isString("material") ? Material.matchMaterial(item.getString("material")) : null;
            if (material == null || !EquipmentMaterials.isEquipment(material))
                throw new IllegalStateException("Invalid equipment material: " + id);
            var slot = EquipmentMaterials.slot(material);
            if (!item.isString("slot") || !slot.name().equals(item.getString("slot").toUpperCase(Locale.ROOT)))
                throw new IllegalStateException("Material and slot do not match: " + id);
            String rarity = item.isString("rarity") ? item.getString("rarity").toUpperCase(Locale.ROOT) : null;
            if (rarity == null || !Set.of("UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC").contains(rarity))
                throw new IllegalStateException("Invalid expansion rarity: " + id);
            String acquisition = item.getString("acquisition");
            if (acquisition == null || !Set.of("craft", "drop").contains(acquisition))
                throw new IllegalStateException("Invalid expansion acquisition: " + id);
            if (!item.isInt("sockets") || item.getInt("sockets") < 0 || item.getInt("sockets") > 4
                || !item.isInt("item-level") || item.getInt("item-level") < 1 || item.getInt("item-level") > 100)
                throw new IllegalStateException("Invalid item level or sockets: " + id);
            if (!item.isString("name")) throw new IllegalStateException("Missing item name: " + id);
            ItemBlueprint.text(item.getString("name"), 256, "Item name");
            if (item.contains("lore")) {
                if (!item.isList("lore") || item.getList("lore").size() > 8)
                    throw new IllegalStateException("Invalid item lore: " + id);
                for (Object line : item.getList("lore")) {
                    if (!(line instanceof String text)) throw new IllegalStateException("Invalid item lore: " + id);
                    ItemBlueprint.text(text, 256, "Item lore");
                }
            }
            validateStats(item, false);
            for (String field : Set.of("set", "category", "model")) {
                if (item.contains(field) && !item.isString(field))
                    throw new IllegalStateException("Invalid item " + field + ": " + id);
            }
            String setId = item.getString("set");
            if (setId != null) {
                identifier(setId);
                if (!occupiedSets.contains(setId)) throw new IllegalStateException("Unknown expansion set: " + id);
            }
            if (item.contains("unique-affix")) {
                SignatureAffix signature = item.isString("unique-affix")
                    ? SignatureAffix.fromId(item.getString("unique-affix")).orElse(null) : null;
                if (signature == null || !signature.supports(material, slot, rarity) || !acquisition.equals("drop"))
                    throw new IllegalStateException("Invalid item signature: " + id);
                Map<String, Double> withSignature = new HashMap<>(statValues(item));
                signature.stats().forEach((stat, value) -> withSignature.merge(stat, value, Double::sum));
                ItemStats.validate(withSignature, false);
            }
            merged.set("relics." + id, item);
            entries.put(new ItemId(id), new PaperItemService.ExpansionEntry(rarity, acquisition, setId));
        }
        validateMerged.accept(merged);
        if (!exists) AtomicYamlFiles.save(expansion, file);
        return new Result(merged, entries);
    }

    private static void identifier(String id) {
        if (!ItemBlueprint.identifier(id).equals(id)) throw new IllegalStateException("Invalid expansion ID");
    }

    private static void validateSet(ConfigurationSection section, String id) {
        if (section == null || !Set.of("name", "piece-count", "bonuses").containsAll(section.getKeys(false))
            || !section.isString("name") || !section.isInt("piece-count") || section.getInt("piece-count") < 1
            || section.getInt("piece-count") > 8 || !section.isConfigurationSection("bonuses"))
            throw new IllegalStateException("Invalid expansion set: " + id);
        ItemBlueprint.text(section.getString("name"), 96, "Set name");
        for (String key : section.getConfigurationSection("bonuses").getKeys(false)) {
            int pieces;
            try { pieces = Integer.parseInt(key); } catch (NumberFormatException error) {
                throw new IllegalStateException("Invalid set threshold: " + id, error);
            }
            var bonus = section.getConfigurationSection("bonuses." + key);
            if (!Integer.toString(pieces).equals(key) || pieces < 1 || pieces > section.getInt("piece-count")
                || bonus == null || !Set.of("description", "stats").containsAll(bonus.getKeys(false))
                || !bonus.isString("description")) throw new IllegalStateException("Invalid set bonus: " + id);
            ItemBlueprint.text(bonus.getString("description"), 256, "Set bonus");
            validateStats(bonus, true);
        }
    }

    private static void validateStats(ConfigurationSection parent, boolean setBonus) {
        ItemStats.validate(statValues(parent), setBonus);
    }

    private static Map<String, Double> statValues(ConfigurationSection parent) {
        if (!parent.contains("stats")) return Map.of();
        ConfigurationSection section = parent.getConfigurationSection("stats");
        if (section == null) throw new IllegalStateException("Invalid item stats");
        Map<String, Double> result = new HashMap<>();
        for (String id : section.getKeys(false)) {
            if (!(section.get(id) instanceof Number number)) throw new IllegalStateException("Non-numeric item stat: " + id);
            result.put(id, number.doubleValue());
        }
        return result;
    }
}
