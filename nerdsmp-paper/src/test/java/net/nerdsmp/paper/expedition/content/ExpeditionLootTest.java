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
}
