package net.nerdsmp.paper.expedition.content;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpeditionLootTest {
    private static final ExpeditionLoot.Table BARROW = new ExpeditionLoot.Table(List.of("grave_dust", "barrow_wax", "huntsmans_antler"),
        List.of("barrowguard_helm", "sextons_edge"), List.of("huntsmans_longbow"));

    private static YamlConfiguration resource(String path) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(ExpeditionLootTest.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test void scoresGradeSpeedSurvivalAndExploration() {
        ExpeditionLoot.Score perfect = ExpeditionLoot.Score.of(600, 1800, 0, 4, 3, 3);
        assertEquals(300, perfect.total());
        assertEquals(ExpeditionLoot.Grade.S_PLUS, perfect.grade());
        assertEquals("S+", perfect.grade().title());
        ExpeditionLoot.Score late = ExpeditionLoot.Score.of(1800, 1800, 8, 4, 0, 3);
        assertEquals(new ExpeditionLoot.Score(40, 50, 40), late);
        assertEquals(ExpeditionLoot.Grade.D, late.grade());
        assertEquals(100, ExpeditionLoot.Score.of(100, 1800, 0, 1, 0, 0).exploration(), "Nothing to find is full marks");
        assertEquals(ExpeditionLoot.Grade.A, ExpeditionLoot.Grade.of(230));
    }

    @Test void enemiesDropByRoleAndBossesAlwaysPayMaterials() {
        Random random = new Random(7);
        int normal = 0, elite = 0;
        for (int i = 0; i < 2000; i++) {
            normal += ExpeditionLoot.roll(BARROW, ExpeditionLoot.Source.NORMAL, 1, random).size();
            elite += ExpeditionLoot.roll(BARROW, ExpeditionLoot.Source.ELITE, 1, random).size();
        }
        assertTrue(elite > normal * 2, "Elites drop far more than regular enemies: " + elite + " vs " + normal);
        for (int i = 0; i < 50; i++) {
            var boss = ExpeditionLoot.roll(BARROW, ExpeditionLoot.Source.BOSS, 1, random);
            assertTrue(boss.stream().anyMatch(drop -> drop.item().equals("barrow_wax")));
        }
    }

    @Test void betterGradesAndBonusChestsHoldMore() {
        Random random = new Random(11);
        int worst = 0, best = 0, bonusGear = 0;
        for (int i = 0; i < 300; i++) {
            worst += ExpeditionLoot.chest(BARROW, false, ExpeditionLoot.Grade.D, 1, random).stream().mapToInt(ExpeditionLoot.Drop::amount).sum();
            best += ExpeditionLoot.chest(BARROW, false, ExpeditionLoot.Grade.S_PLUS, 1, random).stream().mapToInt(ExpeditionLoot.Drop::amount).sum();
            if (ExpeditionLoot.chest(BARROW, true, ExpeditionLoot.Grade.D, 1, random).stream().anyMatch(drop -> drop.kind() == ExpeditionLoot.Kind.GEAR)) bonusGear++;
        }
        assertTrue(best > worst * 2, best + " vs " + worst);
        assertEquals(300, bonusGear, "A bonus chest always holds a piece of set gear");
        assertTrue(ExpeditionLoot.bonusPrice(26, 2.5) > ExpeditionLoot.bonusPrice(8, 1));
    }

    @Test void expeditionsWithoutGearOrSignaturesFallBackToMaterials() {
        ExpeditionLoot.Table tutorial = new ExpeditionLoot.Table(List.of("skyward_feather"), List.of(), List.of());
        Random random = new Random(3);
        for (int i = 0; i < 200; i++)
            for (var drop : ExpeditionLoot.chest(tutorial, true, ExpeditionLoot.Grade.S_PLUS, 2, random))
                assertTrue(drop.kind() == ExpeditionLoot.Kind.LAMP || drop.item().equals("skyward_feather"), drop.toString());
        assertTrue(ExpeditionLoot.roll(ExpeditionLoot.Table.EMPTY, ExpeditionLoot.Source.CHEST, 1, random).stream()
            .allMatch(drop -> drop.kind() == ExpeditionLoot.Kind.LAMP));
    }

    @Test void forgingTheChestOrWeaponCostsMoreAndARareMaterial() {
        var helm = ExpeditionLoot.forgeCost(BARROW, "barrowguard_helm", 8);
        assertEquals(java.util.Map.of("grave_dust", 16, "barrow_wax", 4), helm.items());
        assertEquals(88, helm.marks());
        var blade = ExpeditionLoot.forgeCost(BARROW, "sextons_edge", 8);
        assertEquals(java.util.Map.of("grave_dust", 24, "barrow_wax", 6, "huntsmans_antler", 1), blade.items());
        assertEquals(132, blade.marks());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ExpeditionLoot.forgeCost(BARROW, "huntsmans_longbow", 8));
    }

    @Test void harderTiersAreLuckier() {
        assertEquals(1.0, ExpeditionLoot.luck("normal", 0));
        assertTrue(ExpeditionLoot.luck("mythic", 0) > ExpeditionLoot.luck("heroic", 0));
        assertEquals(1.7 + .05 * 10, ExpeditionLoot.luck("keystone", 10), 1e-9);
    }

    @Test void everyShippedLootItemIsInTheCatalogAndEveryCampaignExpeditionHasATable() {
        YamlConfiguration loot = resource("/content/expedition-loot.yml");
        Set<String> catalog = resource("/content/relics.yml").getConfigurationSection("relics").getKeys(false);
        var expeditions = loot.getConfigurationSection("expeditions");
        for (String expedition : List.of("first_flight", "hollow_barrow", "thornwood_canopy", "sunken_aqueduct", "gloomforge_mines",
            "hall_of_wardens", "cinder_keep")) {
            assertTrue(expeditions.contains(expedition), expedition);
            assertFalse(expeditions.getStringList(expedition + ".materials").isEmpty(), expedition);
            for (String key : List.of("materials", "gear", "signatures"))
                for (String item : expeditions.getStringList(expedition + "." + key)) assertTrue(catalog.contains(item), expedition + " " + item);
        }
    }

    @Test void everyBossItemIsInTheCatalogAndItsRoomExists() {
        YamlConfiguration loot = resource("/content/expedition-loot.yml"), abilities = resource("/content/item-abilities.yml");
        Set<String> relics = resource("/content/relics.yml").getConfigurationSection("relics").getKeys(false);
        Set<String> added = abilities.getConfigurationSection("items").getKeys(false);
        var bosses = loot.getConfigurationSection("expeditions.hollow_barrow.bosses");
        assertEquals(Set.of("wardens_hall", "widows_cell", "kings_rest"), bosses.getKeys(false));
        for (String room : bosses.getKeys(false)) for (String item : bosses.getStringList(room))
            assertTrue(relics.contains(item) || added.contains(item), room + " " + item);
        var canopy = loot.getConfigurationSection("expeditions.thornwood_canopy.bosses");
        assertEquals(Set.of("matrons_nest", "verrins_perch", "heart_grove"), canopy.getKeys(false));
        for (String room : canopy.getKeys(false)) for (String item : canopy.getStringList(room))
            assertTrue(relics.contains(item) || added.contains(item), room + " " + item);
        var mines = loot.getConfigurationSection("expeditions.gloomforge_mines.bosses");
        assertEquals(Set.of("grells_shaft", "ore_line", "slag_pits", "the_gloomforge", "the_molten_deep"), mines.getKeys(false));
        for (String room : mines.getKeys(false)) for (String item : mines.getStringList(room))
            assertTrue(relics.contains(item) || added.contains(item), room + " " + item);
        var hall = loot.getConfigurationSection("expeditions.hall_of_wardens.bosses");
        assertEquals(Set.of("high_gate", "ember_court", "ember_throne", "frost_throne", "stone_throne", "the_unwritten"), hall.getKeys(false));
        for (String room : hall.getKeys(false)) for (String item : hall.getStringList(room))
            assertTrue(relics.contains(item) || added.contains(item), room + " " + item);
        for (String item : added) assertFalse(relics.contains(item), item + " would lose to the catalog's own item");
    }

    @Test void aBossChestAlwaysPaysAndItsRarestItemIsRare() {
        var table = new ExpeditionLoot.Table(BARROW.materials(), BARROW.gear(), BARROW.signatures(), java.util.Map.of("wardens_hall", List.of("common", "middling", "rarest")));
        Random random = new Random(5);
        int common = 0, rarest = 0, gear = 0, runs = 20_000;
        for (int i = 0; i < runs; i++) {
            var chest = ExpeditionLoot.bossChest(table, "wardens_hall", 1, random);
            assertTrue(chest.stream().anyMatch(drop -> drop.item().equals("barrow_wax")));
            common += (int) chest.stream().filter(drop -> drop.item().equals("common")).count();
            rarest += (int) chest.stream().filter(drop -> drop.item().equals("rarest")).count();
            gear += (int) chest.stream().filter(drop -> BARROW.gear().contains(drop.item())).count();
        }
        assertEquals(ExpeditionLoot.BOSS_ITEM_CHANCE / 2, common / (double) runs, .02);
        assertEquals(ExpeditionLoot.BOSS_ITEM_CHANCE / 6, rarest / (double) runs, .01);
        assertEquals(.5, gear / (double) runs, .02);
        assertTrue(ExpeditionLoot.bossChest(table, "no_such_room", 1, random).stream().noneMatch(drop -> drop.item().equals("common")));
    }

    @Test void findsTurnUpWithSetPiecesButTheBonusChestStaysSetGearAndTheForgeIgnoresThem() {
        var table = new ExpeditionLoot.Table(BARROW.materials(), BARROW.gear(), BARROW.signatures(), java.util.Map.of(), List.of("found_ring"));
        Random random = new Random(13);
        int finds = 0, sets = 0;
        for (int i = 0; i < 4000; i++) for (var drop : ExpeditionLoot.roll(table, ExpeditionLoot.Source.SECRET, 2, random)) {
            if (drop.item().equals("found_ring")) finds++;
            if (BARROW.gear().contains(drop.item())) sets++;
        }
        assertTrue(finds > 0 && sets > finds, sets + " vs " + finds);
        assertEquals(sets / 2.0, finds, sets * .2, "One find among two set pieces is a third of the gear");
        for (int i = 0; i < 300; i++)
            assertTrue(BARROW.gear().contains(ExpeditionLoot.chest(table, true, ExpeditionLoot.Grade.D, 1, random).getFirst().item()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ExpeditionLoot.forgeCost(table, "found_ring", 8));
    }

    @Test void theBarrowHasGearForEverySlot() { hasGearForEverySlot("hollow_barrow"); }

    @Test void theCanopyHasGearForEverySlot() { hasGearForEverySlot("thornwood_canopy"); }

    private static void hasGearForEverySlot(String expedition) {
        YamlConfiguration loot = resource("/content/expedition-loot.yml"), abilities = resource("/content/item-abilities.yml");
        var barrow = loot.getConfigurationSection("expeditions." + expedition);
        var relics = resource("/content/relics.yml").getConfigurationSection("relics");
        var added = abilities.getConfigurationSection("items");
        java.util.Set<String> slots = new java.util.TreeSet<>(), items = new java.util.LinkedHashSet<>(barrow.getStringList("gear"));
        items.addAll(barrow.getStringList("finds"));
        for (String room : barrow.getConfigurationSection("bosses").getKeys(false)) items.addAll(barrow.getStringList("bosses." + room));
        for (String item : items) {
            var section = added.contains(item) ? added : relics;
            assertTrue(section.contains(item), item);
            slots.add(section.getString(item + ".slot").toLowerCase(java.util.Locale.ROOT));
        }
        assertEquals(new java.util.TreeSet<>(Set.of("main_hand", "off_hand", "head", "chest", "legs", "feet", "neck", "shoulders", "back", "wrists", "hands", "waist", "ring", "trinket")), slots);
    }

    @Test void harderTiersGetTheirBossItemsMoreOften() {
        var table = new ExpeditionLoot.Table(List.of("a"), List.of(), List.of(), java.util.Map.of("room", List.of("only")));
        Random random = new Random(9);
        int normal = 0, mythic = 0;
        for (int i = 0; i < 5000; i++) {
            if (ExpeditionLoot.bossChest(table, "room", 1, random).stream().anyMatch(drop -> drop.item().equals("only"))) normal++;
            if (ExpeditionLoot.bossChest(table, "room", 1.7, random).stream().anyMatch(drop -> drop.item().equals("only"))) mythic++;
        }
        assertTrue(mythic > normal * 1.4, mythic + " vs " + normal);
    }
}
