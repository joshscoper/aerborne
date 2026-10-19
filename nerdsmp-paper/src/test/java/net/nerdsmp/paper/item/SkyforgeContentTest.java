package net.nerdsmp.paper.item;

import net.nerdsmp.paper.engine.ItemStats;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Static content checks: no server, item-meta factory, or live Bukkit registry is required. */
class SkyforgeContentTest {
    private static final Path CONTENT = Path.of("src/main/resources/content");
    private static final Set<String> SIGNATURES = Set.of("furnace_heart", "deep_resonance", "harvest_moon",
        "tideweaver", "second_dawn", "hunters_mercy", "astral_insight", "everlasting", "venom_heart", "stormstring");

    private YamlConfiguration catalog(String file) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(Files.readString(CONTENT.resolve(file)));
        return yaml;
    }

    private Map<String, Double> stats(ConfigurationSection entry) {
        Map<String, Double> values = new HashMap<>();
        assertNotNull(entry, "Missing stats");
        for (String key : entry.getKeys(false)) {
            assertInstanceOf(Number.class, entry.get(key), key);
            values.put(key, ((Number) entry.get(key)).doubleValue());
        }
        return values;
    }

    @Test void exactlyOneHundredAdditionalItemsWithPromisedRarityAndAcquisitionCounts() throws Exception {
        var yaml = catalog("skyforge-items.yml");
        assertEquals(1, yaml.getInt("schema-version"));
        var items = yaml.getConfigurationSection("items");
        assertNotNull(items);
        assertEquals(100, items.getKeys(false).size());
        Map<String, Long> counts = items.getKeys(false).stream().collect(Collectors.groupingBy(
            id -> items.getString(id + ".rarity"), Collectors.counting()));
        assertEquals(Map.of("uncommon", 20L, "rare", 25L, "epic", 25L, "legendary", 20L, "mythic", 10L), counts);
        assertEquals(30, items.getKeys(false).stream().filter(id -> "craft".equals(items.getString(id + ".acquisition"))).count());
        assertEquals(70, items.getKeys(false).stream().filter(id -> "drop".equals(items.getString(id + ".acquisition"))).count());
        var original = catalog("relics.yml").getConfigurationSection("relics");
        assertNotNull(original);
        for (String id : items.getKeys(false)) {
            assertTrue(id.matches("[a-z][a-z0-9_]*"), id);
            assertFalse(original.contains(id), "An additional item replaced an existing ID: " + id);
            assertTrue(Set.of("craft", "drop").contains(items.getString(id + ".acquisition")), id);
        }
    }

    @Test void itemDefinitionsUseOnlySupportedStatsAndVanillaEquipment() throws Exception {
        var items = catalog("skyforge-items.yml").getConfigurationSection("items");
        assertNotNull(items);
        for (String id : items.getKeys(false)) {
            var item = items.getConfigurationSection(id);
            assertNotNull(item);
            Material material = Material.valueOf(item.getString("material").toUpperCase(java.util.Locale.ROOT));
            assertTrue(material.name().matches("(?:IRON|DIAMOND|NETHERITE|LEATHER)_(?:HELMET|CHESTPLATE|LEGGINGS|BOOTS|PICKAXE|AXE|HOE|SHOVEL|SWORD|SPEAR)")
                || Set.of(Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.MACE, Material.SHIELD, Material.FISHING_ROD).contains(material), id);
            assertTrue(Set.of("main_hand", "off_hand", "head", "chest", "legs", "feet").contains(item.getString("slot")), id);
            String expectedSlot = switch (material.name()) {
                case "SHIELD" -> "off_hand";
                default -> material.name().endsWith("_HELMET") ? "head"
                    : material.name().endsWith("_CHESTPLATE") ? "chest"
                    : material.name().endsWith("_LEGGINGS") ? "legs"
                    : material.name().endsWith("_BOOTS") ? "feet" : "main_hand";
            };
            assertEquals(expectedSlot, item.getString("slot"), id);
            assertFalse(item.contains("model"), id + " requires a resource pack");
            assertFalse(item.getString("name", "").isBlank(), id);
            assertFalse(item.getStringList("lore").isEmpty(), id);
            assertTrue(item.getInt("sockets") >= 1 && item.getInt("sockets") <= 3, id);
            assertTrue(item.getInt("item-level") >= 5 && item.getInt("item-level") <= 50, id);
            assertFalse(ItemStats.validate(stats(item.getConfigurationSection("stats")), false).isEmpty(), id);
        }
    }

    @Test void eightSixPieceSetsFitAllEquipmentSlotsAndHaveTwoFourFivePieceBonuses() throws Exception {
        var yaml = catalog("skyforge-items.yml");
        var sets = yaml.getConfigurationSection("sets");
        var items = yaml.getConfigurationSection("items");
        assertNotNull(sets);
        assertNotNull(items);
        assertEquals(8, sets.getKeys(false).size());
        for (String id : sets.getKeys(false)) {
            assertEquals(6, sets.getInt(id + ".piece-count"), id);
            var pieces = items.getKeys(false).stream().filter(item -> id.equals(items.getString(item + ".set"))).toList();
            assertEquals(6, pieces.size(), id);
            assertEquals(Set.of("head", "chest", "legs", "feet", "main_hand", "off_hand"),
                pieces.stream().map(item -> items.getString(item + ".slot")).collect(Collectors.toSet()), id);
            var bonuses = sets.getConfigurationSection(id + ".bonuses");
            assertNotNull(bonuses);
            assertEquals(Set.of("2", "4", "5"), bonuses.getKeys(false), id);
            for (String threshold : bonuses.getKeys(false)) {
                assertFalse(bonuses.getString(threshold + ".description", "").isBlank(), id);
                assertFalse(ItemStats.validate(stats(bonuses.getConfigurationSection(threshold + ".stats")), true).isEmpty(), id);
            }
        }
        assertEquals(48, items.getKeys(false).stream().filter(item -> items.contains(item + ".set")).count());
        for (String id : items.getKeys(false))
            if (items.contains(id + ".set")) assertTrue(sets.contains(items.getString(id + ".set")), id);
    }

    @Test void tenDropOnlyMythicsEachHaveOneDistinctSignature() throws Exception {
        var items = catalog("skyforge-items.yml").getConfigurationSection("items");
        assertNotNull(items);
        Set<String> found = new HashSet<>();
        for (String id : items.getKeys(false)) {
            if ("mythic".equals(items.getString(id + ".rarity"))) {
                assertEquals("drop", items.getString(id + ".acquisition"), id);
                String signature = items.getString(id + ".unique-affix");
                assertTrue(SIGNATURES.contains(signature), id);
                assertTrue(found.add(signature), "Repeated signature: " + signature);
                assertFalse(items.contains(id + ".set"), "Mythics should support a five-piece set, not require another set");
            } else assertFalse(items.contains(id + ".unique-affix"), id);
        }
        assertEquals(SIGNATURES, found);
        assertEquals("netherite_pickaxe", items.getString("sunforge_pickaxe.material"));
        assertEquals("furnace_heart", items.getString("sunforge_pickaxe.unique-affix"));
        assertEquals(5, items.getInt("sunforge_pickaxe.stats.mining-power"));
    }

    @Test void thirtyCraftingRoutesMatchEveryCraftItemAndExcludeAllDropItems() throws Exception {
        var items = catalog("skyforge-items.yml").getConfigurationSection("items");
        var yaml = catalog("skyforge-recipes.yml");
        var recipes = yaml.getConfigurationSection("recipes");
        assertNotNull(items);
        assertNotNull(recipes);
        assertEquals(1, yaml.getInt("schema-version"));
        assertEquals(30, recipes.getKeys(false).size());
        Set<String> results = new HashSet<>();
        for (String id : recipes.getKeys(false)) {
            assertTrue(id.startsWith("skyforge_"), id);
            String result = recipes.getString(id + ".result");
            assertTrue(items.contains(result), id);
            assertEquals("craft", items.getString(result + ".acquisition"), id);
            assertTrue(results.add(result), "Duplicate route for " + result);
            assertEquals(1, recipes.getInt(id + ".introduced"), id);
            assertTrue(Set.of("mining", "forestry", "farming", "fishing", "excavation", "smithing")
                .contains(recipes.getString(id + ".skill")), id);
            assertTrue(recipes.getInt(id + ".level") >= 5 && recipes.getInt(id + ".level") <= 50, id);
        }
        assertEquals(items.getKeys(false).stream().filter(id -> "craft".equals(items.getString(id + ".acquisition")))
            .collect(Collectors.toSet()), results);
    }

    @Test void craftingSymbolsAndNormalOrMirroredMaterialPatternsDoNotCollide() throws Exception {
        var base = catalog("recipes.yml").getConfigurationSection("recipes");
        var recipes = catalog("skyforge-recipes.yml").getConfigurationSection("recipes");
        assertNotNull(base);
        assertNotNull(recipes);
        Set<String> occupied = new HashSet<>();
        for (String id : base.getKeys(false)) {
            var entry = base.getConfigurationSection(id);
            assertNotNull(entry);
            occupied.add(pattern(entry, false));
            occupied.add(pattern(entry, true));
        }
        for (String id : recipes.getKeys(false)) {
            var entry = recipes.getConfigurationSection(id);
            assertNotNull(entry);
            assertFalse(base.contains(id), "An expansion recipe replaced an existing ID: " + id);
            List<String> shape = entry.getStringList("shape");
            assertTrue(shape.size() >= 1 && shape.size() <= 3, id);
            assertTrue(shape.getFirst().length() >= 1 && shape.getFirst().length() <= 3, id);
            assertTrue(shape.stream().allMatch(row -> row.length() == shape.getFirst().length()), id);
            var ingredients = entry.getConfigurationSection("ingredients");
            assertNotNull(ingredients);
            Set<String> used = shape.stream().flatMapToInt(String::chars).filter(c -> c != ' ')
                .mapToObj(c -> Character.toString((char)c)).collect(Collectors.toSet());
            assertEquals(used, ingredients.getKeys(false), id + " has missing or unused symbols");
            for (String symbol : ingredients.getKeys(false)) {
                assertEquals(1, symbol.length(), id);
                Material ingredient = Material.valueOf(ingredients.getString(symbol).toUpperCase(java.util.Locale.ROOT));
                assertNotEquals(Material.AIR, ingredient, id);
            }
            String normal = pattern(entry, false), mirrored = pattern(entry, true);
            assertFalse(occupied.contains(normal) || occupied.contains(mirrored), "Conflicting recipe: " + id);
            occupied.add(normal);
            occupied.add(mirrored);
        }
    }

    /** Trim empty margins as Bukkit shaped recipes do, then compare actual materials, not symbol names. */
    private String pattern(ConfigurationSection recipe, boolean mirror) {
        List<String> rows = new ArrayList<>(recipe.getStringList("shape"));
        while (!rows.isEmpty() && rows.getFirst().isBlank()) rows.removeFirst();
        while (!rows.isEmpty() && rows.getLast().isBlank()) rows.removeLast();
        int left = rows.stream().mapToInt(row -> {
            int i = 0; while (i < row.length() && row.charAt(i) == ' ') i++; return i;
        }).min().orElse(0);
        int right = rows.stream().mapToInt(row -> {
            int i = row.length(); while (i > 0 && row.charAt(i - 1) == ' ') i--; return i;
        }).max().orElse(0);
        return rows.stream().map(row -> row.substring(left, right)).map(row -> mirror ? new StringBuilder(row).reverse().toString() : row)
            .map(row -> row.chars().mapToObj(c -> c == ' ' ? "air" : recipe.getString("ingredients." + (char)c))
                .collect(Collectors.joining(","))).collect(Collectors.joining(";"));
    }
}
