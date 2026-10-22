package net.nerdsmp.paper.expedition.content;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExpeditionGuideContentTest {
    @Test void samplesShipRatingsGuidesAndTamedBosses() {
        ExpeditionDefinition barrow = ExpeditionContentTest.sample("hollow_barrow");
        assertEquals(2, Difficulty.rating(barrow));
        assertFalse(barrow.guide().isEmpty());
        assertTrue(barrow.guide().overview().startsWith("A crypt crawl"));
        assertEquals(4, barrow.guide().tips().size());
        assertEquals(barrow.rooms().keySet(), barrow.guide().rooms().keySet(), "Every room has a tip");
        assertTrue(barrow.guide().boss().contains("slam"));
        var huntsman = barrow.rooms().get("kings_rest").encounter();
        assertEquals(0.6, huntsman.bossDamage());
        assertEquals(0.85, huntsman.bossHealth());

        ExpeditionDefinition keep = ExpeditionContentTest.sample("cinder_keep");
        assertEquals(5, Difficulty.rating(keep));
        assertEquals(1.0, keep.rooms().get("molten_heart").encounter().bossHealth(), "Unset multipliers stay as authored");
        assertEquals(keep.rooms().keySet(), keep.guide().rooms().keySet());
    }

    @Test void ratingsAreEstimatedWhenUnsetAndRenderAsStars() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("level", 42);
        ExpeditionDefinition unrated = ExpeditionLoader.parse("deep_vault", yaml);
        assertNull(unrated.difficulty());
        assertEquals(4, Difficulty.rating(unrated));
        assertEquals(List.of(1, 1, 2, 2, 3, 3, 4, 4, 5), List.of(1, 10, 11, 20, 21, 35, 36, 55, 56).stream().map(Difficulty::estimate).toList());
        assertEquals("★★☆☆☆", Difficulty.stars(2));
        assertEquals("★★★★★", Difficulty.stars(9));
        assertEquals("Moderate", Difficulty.label(2));
        assertEquals("Brutal", Difficulty.label(5));
    }

    @Test void validatorChecksRatingsBossMultipliersAndGuideRooms() {
        YamlConfiguration yaml = ExpeditionContentTest.resource("/expeditions/hollow_barrow.yml");
        yaml.set("difficulty", 7);
        yaml.set("rooms.kings_rest.encounter.boss-damage", 0.01);
        yaml.set("guide.rooms.nowhere", "A tip for a room that isn't there.");
        var issues = ExpeditionValidator.validate(ExpeditionLoader.parse("hollow_barrow", yaml), Tier.DEFAULTS, ExpeditionContentTest.CATALOG);
        assertTrue(issues.stream().anyMatch(issue -> issue.error() && issue.message().contains("Difficulty must be 1-5")), issues.toString());
        assertTrue(issues.stream().anyMatch(issue -> issue.error() && issue.message().contains("boss-damage")), issues.toString());
        assertTrue(issues.stream().anyMatch(issue -> !issue.error() && issue.message().contains("unknown room 'nowhere'")), issues.toString());

        YamlConfiguration bare = ExpeditionContentTest.resource("/expeditions/hollow_barrow.yml");
        bare.set("guide", null);
        bare.set("difficulty", null);
        var advice = ExpeditionValidator.validate(ExpeditionLoader.parse("hollow_barrow", bare), Tier.DEFAULTS, ExpeditionContentTest.CATALOG);
        assertTrue(advice.stream().anyMatch(issue -> !issue.error() && issue.message().contains("No player guide")));
        assertTrue(advice.stream().anyMatch(issue -> !issue.error() && issue.message().contains("No difficulty rating")));
        assertTrue(ExpeditionValidator.publishable(advice), "A missing guide or rating is advice, not an error");
    }

    @Test void badBossMultipliersAreStructuralErrors() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("rooms.hall.encounter.boss", "custom:king");
        yaml.set("rooms.hall.encounter.boss-damage", "lots");
        var failure = assertThrows(IllegalArgumentException.class, () -> ExpeditionLoader.parse("broken", yaml));
        assertTrue(failure.getMessage().contains("boss-damage"), failure.getMessage());
    }
}
