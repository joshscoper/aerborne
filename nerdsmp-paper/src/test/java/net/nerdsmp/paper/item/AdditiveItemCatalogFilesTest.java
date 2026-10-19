package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.ItemId;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AdditiveItemCatalogFilesTest {
    @TempDir Path directory;

    private static final String BUNDLE = """
        schema-version: 1
        sets: {}
        items:
          furnace_test:
            material: DIAMOND_PICKAXE
            name: '<light_purple>Furnace Test</light_purple>'
            slot: MAIN_HAND
            rarity: MYTHIC
            sockets: 2
            item-level: 80
            stats: {damage: 7, mining-power: 5}
            unique-affix: furnace_heart
            acquisition: drop
        """;

    private YamlConfiguration baseline() throws Exception {
        var result = new YamlConfiguration();
        result.loadFromString("schema-version: 12\nsets: {}\nrelics:\n  old: {material: IRON_SWORD, name: 'Operator blade'}\nnotes: keep\n");
        return result;
    }

    @Test void newSeparateFileInstallsOnlyAfterValidationAndLeavesBaselineUntouched() throws Exception {
        Path target = directory.resolve("skyforge-items.yml");
        YamlConfiguration old = baseline();
        String before = old.saveToString();
        var result = AdditiveItemCatalogFiles.load(target.toFile(), BUNDLE, old,
            merged -> assertTrue(merged.isConfigurationSection("relics.furnace_test")));
        assertTrue(Files.isRegularFile(target));
        assertEquals(before, old.saveToString());
        assertEquals("Operator blade", result.merged().getString("relics.old.name"));
        assertEquals("keep", result.merged().getString("notes"));
        var entry = result.entries().get(new ItemId("furnace_test"));
        assertEquals("MYTHIC", entry.rarity());
        assertEquals("drop", entry.acquisition());
        assertNull(entry.setId());
        assertThrows(UnsupportedOperationException.class, () -> result.entries().clear());
    }

    @Test void operatorEditsAndIntentionalDeletionsAreNeverReplacedOrRefilled() throws Exception {
        Path target = directory.resolve("skyforge-items.yml");
        String edited = "# Keep this comment\n" + BUNDLE.replace("mining-power: 5", "mining-power: 9");
        Files.writeString(target, edited);
        var result = AdditiveItemCatalogFiles.load(target.toFile(), BUNDLE, baseline(), ignored -> { });
        assertEquals(9, result.merged().getInt("relics.furnace_test.stats.mining-power"));
        assertEquals(edited, Files.readString(target));
        String deleted = "schema-version: 1\nsets: {}\nitems: {}\n";
        Files.writeString(target, deleted);
        result = AdditiveItemCatalogFiles.load(target.toFile(), BUNDLE, baseline(), ignored -> { });
        assertTrue(result.entries().isEmpty());
        assertFalse(result.merged().contains("relics.furnace_test"));
        assertEquals(deleted, Files.readString(target));
    }

    @Test void collisionsAndReservedCraftedIdsNeverCreateOrRewriteExpansion() throws Exception {
        Path target = directory.resolve("skyforge-items.yml");
        for (String bad : new String[]{BUNDLE.replace("furnace_test:", "old:"),
            BUNDLE.replace("furnace_test:", "crafted_diamond_pickaxe:"),
            BUNDLE.replace("sets: {}", "sets:\n  existing: {name: Test, piece-count: 1, bonuses: {}}")}) {
            var old = baseline();
            old.set("sets.existing.name", "Original");
            assertThrows(Exception.class, () -> AdditiveItemCatalogFiles.load(target.toFile(), bad, old, ignored -> { }));
            assertFalse(Files.exists(target));
        }
    }

    @Test void duplicateYamlKeysMalformedFilesFutureSchemasAndBadTypesFailWithoutWrites() throws Exception {
        Path target = directory.resolve("skyforge-items.yml");
        for (String bad : new String[]{BUNDLE.replace("sets: {}", "sets: {}\nsets: {}"),
            BUNDLE.replace("schema-version: 1", "schema-version: 2"),
            BUNDLE.replace("mining-power: 5", "mining-power: nope"),
            BUNDLE.replace("mining-power: 5", "mining-power: .nan"),
            BUNDLE.replace("mining-power: 5", "made-up-power: 5"),
            BUNDLE.replace("item-level: 80", "item-level: '80'"),
            BUNDLE.replace("sockets: 2", "sockets: 5"),
            "items: [unterminated"}) {
            Files.writeString(target, bad);
            assertThrows(Exception.class, () -> AdditiveItemCatalogFiles.load(target.toFile(), BUNDLE, baseline(), ignored -> { }));
            assertEquals(bad, Files.readString(target));
        }
    }

    @Test void signaturesRejectWrongRarityWrongSlotWrongMaterialUnknownIdAndCraftAcquisition() throws Exception {
        Path target = directory.resolve("skyforge-items.yml");
        for (String bad : new String[]{BUNDLE.replace("rarity: MYTHIC", "rarity: EPIC"),
            BUNDLE.replace("slot: MAIN_HAND", "slot: HEAD"),
            BUNDLE.replace("material: DIAMOND_PICKAXE", "material: DIAMOND_SHOVEL"),
            BUNDLE.replace("furnace_heart", "made_up_signature"),
            BUNDLE.replace("acquisition: drop", "acquisition: craft"),
            BUNDLE.replace("acquisition: drop", "acquisition: anywhere")}) {
            assertThrows(Exception.class, () -> AdditiveItemCatalogFiles.load(target.toFile(), bad, baseline(), ignored -> { }));
            assertFalse(Files.exists(target));
        }
    }

    @Test void passiveSignatureAndBaseTotalsMustFitExistingStatLimits() throws Exception {
        Path target = directory.resolve("skyforge-items.yml");
        String bad = BUNDLE.replace("DIAMOND_PICKAXE", "NETHERITE_CHESTPLATE").replace("slot: MAIN_HAND", "slot: CHEST")
            .replace("stats: {damage: 7, mining-power: 5}", "stats: {defense: 999}").replace("furnace_heart", "second_dawn");
        assertThrows(Exception.class, () -> AdditiveItemCatalogFiles.load(target.toFile(), bad, baseline(), ignored -> { }));
        assertFalse(Files.exists(target));
    }

    @Test void fullParserFailureDoesNotPublishNewExpansion() throws Exception {
        Path target = directory.resolve("skyforge-items.yml");
        assertThrows(IllegalStateException.class, () -> AdditiveItemCatalogFiles.load(target.toFile(), BUNDLE, baseline(),
            ignored -> { throw new IllegalStateException("Full parser rejected"); }));
        assertFalse(Files.exists(target));
    }

    @Test void bundledHundredItemCatalogUsesTheSameStrictOperatorValidation() throws Exception {
        Path content = Path.of("src/main/resources/content");
        var original = new YamlConfiguration();
        original.loadFromString(Files.readString(content.resolve("relics.yml")));
        String before = original.saveToString();
        var result = AdditiveItemCatalogFiles.load(directory.resolve("skyforge-items.yml").toFile(),
            Files.readString(content.resolve("skyforge-items.yml")), original, ignored -> { });
        assertEquals(100, result.entries().size());
        assertEquals(70, result.entries().values().stream().filter(entry -> entry.acquisition().equals("drop")).count());
        assertEquals(10, result.entries().values().stream().filter(entry -> entry.rarity().equals("MYTHIC")).count());
        assertEquals(original.getConfigurationSection("relics").getKeys(false).size() + 100,
            result.merged().getConfigurationSection("relics").getKeys(false).size());
        assertEquals(before, original.saveToString());
    }

    @Test void collisionChecksUseCanonicalIdsEvenWhenLegacyOperatorKeysUsedCapitals() throws Exception {
        Path target = directory.resolve("skyforge-items.yml");
        var old = baseline();
        old.set("relics.Furnace_Test.material", "DIAMOND_PICKAXE");
        assertThrows(IllegalStateException.class,
            () -> AdditiveItemCatalogFiles.load(target.toFile(), BUNDLE, old, ignored -> { }));
        assertFalse(Files.exists(target));
        assertEquals("DIAMOND_PICKAXE", old.getString("relics.Furnace_Test.material"));
    }
}
