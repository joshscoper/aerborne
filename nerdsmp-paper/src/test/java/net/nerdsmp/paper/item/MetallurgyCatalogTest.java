package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.paper.engine.ItemStats;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MetallurgyCatalogTest {
    private YamlConfiguration yaml(String name) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(Files.readString(Path.of("src/main/resources/content", name)));
        return yaml;
    }
    private Map<ItemId, Material> materials() throws Exception {
        Map<ItemId, Material> materials = new HashMap<>();
        var relics = yaml("relics.yml").getConfigurationSection("relics");
        for (String id : relics.getKeys(false)) materials.put(new ItemId(id), Material.matchMaterial(relics.getString(id + ".material")));
        return materials;
    }
    @Test void everyRefinedMaterialAndAlloyHasAReachableEquipmentUse() throws Exception {
        var materials = materials();
        var catalog = MetallurgyCatalog.parse(yaml("metallurgy.yml"), materials::get);
        assertEquals(6, catalog.smelting().size());
        assertEquals(13, catalog.crafting().size());
        Set<ItemId> produced = new HashSet<>(), consumed = new HashSet<>();
        var relics = yaml("relics.yml").getConfigurationSection("relics");
        for (var smelt : catalog.smelting()) {
            assertTrue(produced.add(smelt.result()));
            consumed.add(smelt.input());
            assertEquals("none", relics.getString(smelt.result().value() + ".slot"));
        }
        Set<ItemId> available = new HashSet<>(materials.keySet());
        catalog.crafting().forEach(recipe -> available.remove(recipe.result()));
        for (var recipe : catalog.crafting()) {
            assertTrue(produced.add(recipe.result()));
            consumed.addAll(recipe.customIngredients().values());
            assertTrue(available.containsAll(recipe.customIngredients().values()), "Recipe chain has a forward reference/cycle: " + recipe.id());
            available.add(recipe.result());
            var stats = relics.getConfigurationSection(recipe.result().value() + ".stats");
            if (stats != null) {
                Map<String, Double> values = new HashMap<>(); stats.getKeys(false).forEach(key -> values.put(key, stats.getDouble(key)));
                assertFalse(ItemStats.validate(values, false).isEmpty());
                assertTrue(EquipmentMaterials.isEquipment(materials.get(recipe.result())));
            } else {
                assertEquals("none", relics.getString(recipe.result().value() + ".slot"));
                assertEquals(0, relics.getInt(recipe.result().value() + ".sockets"));
            }
        }
        produced.removeAll(consumed);
        assertEquals(Set.of("gloomsteel_mattock", "gloomsteel_hauberk", "tideforged_rod", "warded_buckler", "cindersteel_cleaver", "briarbound_hoe", "the_binding", "crown_of_the_accord", "sovereignbane_plate"),
            produced.stream().map(ItemId::value).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void ordinaryMetalAndWrongCustomItemsCannotSubstituteForRefinedMaterials() {
        var id = new ItemId("gloomsteel_ingot");
        var custom = new RecipeIngredient(Material.IRON_INGOT, id);
        assertTrue(custom.matches(Material.IRON_INGOT, id, false));
        assertFalse(custom.matches(Material.IRON_INGOT, null, true));
        assertFalse(custom.matches(Material.IRON_INGOT, new ItemId("tideforged_alloy"), false));
        assertFalse(custom.matches(Material.COPPER_INGOT, id, false));
        var ordinary = new RecipeIngredient(Material.IRON_INGOT, null);
        assertTrue(ordinary.matches(Material.IRON_INGOT, null, true));
        assertFalse(ordinary.matches(Material.IRON_INGOT, id, false));
        assertFalse(ordinary.matches(Material.IRON_INGOT, null, false));
    }
    @Test void malformedAndUnknownRecipesFailValidation() throws Exception {
        var materials = materials();
        var original = yaml("metallurgy.yml");
        for (var bad : Map.of("schema-version", 99, "smelting.gloomsteel.ticks", 0,
            "smelting.gloomsteel.input", "missing_ore", "crafting.gloomsteel_alloy.level", 51,
            "crafting.gloomsteel_alloy.ingredients.G", "item:missing_ingot").entrySet()) {
            var candidate = new YamlConfiguration(); candidate.loadFromString(original.saveToString());
            candidate.set(bad.getKey(), bad.getValue());
            assertThrows(RuntimeException.class, () -> MetallurgyCatalog.parse(candidate, materials::get), bad.getKey());
        }
    }
    @Test void anOlderFileGetsTheNewRecipesAndKeepsItsOwn() throws Exception {
        var bundled = yaml("metallurgy.yml");
        var installed = yaml("metallurgy.yml");
        installed.set("schema-version", 1);
        installed.set("smelting.bindingsteel", null);
        installed.set("smelting.gloomsteel.ticks", 77);
        assertTrue(MetallurgyCatalog.upgrade(installed, bundled));
        assertTrue(installed.contains("smelting.bindingsteel"));
        assertEquals(77, installed.getInt("smelting.gloomsteel.ticks"));
        assertFalse(MetallurgyCatalog.upgrade(installed, bundled));
    }
    @Test void upgradeAddsNewMaterialsWithoutOverwritingTheServersExistingOre() throws Exception {
        var current = yaml("relics.yml");
        current.set("schema-version", 13);
        current.set("relics.gloomsteel_ore.name", "Operator Gloomsteel");
        for (String id : new HashSet<>(current.getConfigurationSection("relics").getKeys(false))) {
            if (Set.of("refined material", "alloy").contains(current.getString("relics." + id + ".category", ""))) current.set("relics." + id, null);
        }
        Path directory = Files.createTempDirectory("metallurgy-migration");
        try {
            Path file = directory.resolve("relics.yml"); Files.writeString(file, current.saveToString());
            var result = RelicCatalogFiles.load(file.toFile(), yaml("relics.yml"), 14, ignored -> { });
            assertEquals("Operator Gloomsteel", result.getString("relics.gloomsteel_ore.name"));
            assertTrue(result.contains("relics.gloomsteel_ingot"));
            assertTrue(Files.exists(directory.resolve("relics.yml.before-v14.bak")));
        } finally {
            try (var paths = Files.list(directory)) { for (Path path : paths.toList()) Files.delete(path); }
            Files.delete(directory);
        }
    }
}
