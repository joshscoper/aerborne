package net.nerdsmp.paper.expedition.content;

import net.nerdsmp.paper.cosmetic.CosmeticCatalog;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rewards grow with the player and the difficulty, and the Barrow's scenes and drops are wired up. */
class PlaytestRewardsTest {
    private static ExpeditionDefinition barrow() throws Exception {
        try (var reader = new InputStreamReader(Objects.requireNonNull(PlaytestRewardsTest.class.getResourceAsStream("/expeditions/hollow_barrow.yml")), StandardCharsets.UTF_8)) {
            return ExpeditionLoader.parse("hollow_barrow", YamlConfiguration.loadConfiguration(reader));
        }
    }

    @Test void gearGainsItemLevelsFromDifficultyAndThePlayersLevel() {
        assertEquals(0, ExpeditionLoot.itemLevelBonus("normal", 0, 8, 8));
        assertEquals(0, ExpeditionLoot.itemLevelBonus("normal", 0, 8, 2), "A low-level player never loses levels");
        assertEquals(6, ExpeditionLoot.itemLevelBonus("heroic", 0, 8, 8));
        assertEquals(12, ExpeditionLoot.itemLevelBonus("mythic", 0, 8, 8));
        assertEquals(21, ExpeditionLoot.itemLevelBonus("heroic", 0, 8, 100), "The player's share stops at 15");
        assertEquals(10, ExpeditionLoot.itemLevelBonus("normal", 0, 8, 28));
        assertEquals(20, ExpeditionLoot.itemLevelBonus("keystone", 4, 8, 8));
        assertEquals(ExpeditionLoot.MAX_ITEM_LEVEL_BONUS, ExpeditionLoot.itemLevelBonus("keystone", 30, 8, 100));
    }

    @Test void stacksGrowButSinglesStaySingle() {
        assertEquals(4, ExpeditionLoot.scaled(4, 1, 8, 8));
        assertEquals(5, ExpeditionLoot.scaled(4, 1.35, 8, 8));
        assertEquals(6, ExpeditionLoot.scaled(4, 1, 8, 200), "Half again at most for level");
        assertEquals(1, ExpeditionLoot.scaled(1, 1.7, 8, 100), "A single item never doubles");
        assertEquals(64, ExpeditionLoot.scaled(60, 1.7, 8, 100));
    }

    @Test void skillExperienceGrowsWithTheEnemyAndTheDungeon() {
        assertEquals(18, ExpeditionLoot.skillExperience(ExpeditionLoot.Source.NORMAL, 8, 1));
        assertEquals(540, ExpeditionLoot.skillExperience(ExpeditionLoot.Source.BOSS, 8, 1));
        assertEquals(729, ExpeditionLoot.skillExperience(ExpeditionLoot.Source.BOSS, 8, 1.35));
        assertEquals(1080, ExpeditionLoot.finishExperience(8, 1));
    }

    @Test void rareDropsRiseWithDifficultyWithinReason() {
        assertEquals(.005, ExpeditionLoot.rareChance(.005, 1), 1e-9);
        assertEquals(.0085, ExpeditionLoot.rareChance(.005, 1.7), 1e-9);
        assertEquals(1, ExpeditionLoot.rareChance(.9, 2), 1e-9);
    }

    @Test void theBarrowsCompanionsAreRealAndRare() throws Exception {
        var drops = barrow().story().companionDrops();
        assertEquals(java.util.Set.of("wardens_hall", "widows_cell", "kings_rest"), drops.keySet());
        CosmeticCatalog catalog = CosmeticCatalog.defaults();
        for (var drop : drops.values()) {
            assertTrue(catalog.companion(drop.companion()).isPresent(), drop.companion());
            assertNotNull(catalog.companion(drop.companion()).orElseThrow().source(), drop.companion() + " says where it comes from");
            assertTrue(drop.chance() <= .01, drop.companion() + " stays rare");
            assertTrue(catalog.known(catalog.companion(drop.companion()).orElseThrow().product()));
        }
    }

    @Test void everyBossArrivesAndTheIntroCoversTheDungeon() throws Exception {
        var barrow = barrow();
        for (String room : java.util.List.of("wardens_hall", "widows_cell", "kings_rest"))
            assertNotNull(barrow.rooms().get(room).encounter().entrance(), room);
        var intro = barrow.story().cutscenes().get("prologue");
        assertTrue(intro.shots().stream().allMatch(shot -> shot.to() != null), "Every shot of the flyover moves");
        assertTrue(intro.shots().size() >= 5 && intro.seconds() <= 25, "A brief look at each part: " + intro.seconds() + "s");
    }

    @Test void pellsDailyHuntsTheBarrowsOwnDead() throws Exception {
        String quests;
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/content/world-quests.yml"))) {
            quests = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var daily = net.nerdsmp.paper.quest.QuestCatalog.parse(quests).quests().get("daily_barrow_sweep");
        assertTrue(daily.daily());
        assertEquals("sexton_pell", barrow().story().npcs().get("sexton").quests());
        assertEquals("sexton_pell", daily.giver());
        var mobs = YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/content/expedition-mobs.yml")), StandardCharsets.UTF_8));
        String text = mobs.saveToString();
        for (String target : daily.objectives().getFirst().targets()) assertTrue(text.contains(target.substring("custom:".length()) + ":"), target);
    }

    @Test void charactersCanBeMovedByScript() {
        var action = ScriptAction.parse("npc sexton 0 64 129 180");
        assertEquals(ScriptAction.Verb.NPC, action.verb());
        assertEquals(129, action.intArg(3, 0));
        assertEquals(180, action.intArg(4, 0));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ScriptAction.parse("npc sexton 0 64"));
    }
}
