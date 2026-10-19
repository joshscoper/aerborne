package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MobOverhaulContractTest {
    @Test
    void scalingAndBossMechanicsAreExplicitlyConfigured() throws IOException {
        String config = Files.readString(Path.of("src", "main", "resources", "config.yml"));

        assertTrue(config.contains("blocks-per-level: 128"));
        assertTrue(config.contains("elite-chance-per-level: 0.0005"));
        assertTrue(config.contains("maximum-combatants: 8"));
        assertTrue(config.contains("elite-health-per-extra-player: 0.30"));
        assertTrue(config.contains("boss-reward-per-extra-player: 0.35"));
        assertTrue(config.contains("boss-mechanics:"));
        assertTrue(config.contains("phase-timeout-seconds: 90"));
    }

    @Test
    void pinnedDropReviewContainsEveryTopLevelPaperTable() throws IOException {
        String review = Files.readString(Path.of("..", "docs", "VANILLA_MOB_DROPS_26.2.md"));
        long rows = review.lines().filter(line -> line.matches("\\| [0-9]+ \\|.*")).count();

        assertEquals(93L, rows);
        assertTrue(review.contains("| Camel Husk | `camel_husk` |"));
        assertTrue(review.contains("| Nautilus | `nautilus` |"));
        assertTrue(review.contains("| Parched | `parched` |"));
        assertTrue(review.contains("| Sulfur Cube | `sulfur_cube` |"));
        assertTrue(review.contains("| Zombie Nautilus | `zombie_nautilus` |"));
        assertTrue(review.contains("| Ender Dragon | `ender_dragon` |"));
        assertTrue(review.contains("| Elder Guardian | `elder_guardian` |"));
        assertTrue(review.contains("| Warden | `warden` |"));
        assertTrue(review.contains("| Wither | `wither` |"));
        assertTrue(review.contains("sealed gear + coin purse"));
        assertTrue(review.contains("Tune vanilla + add bonus"));
    }

    @Test
    void mobLabelsAndPersonalHealthBarsRequireVisibility() throws IOException {
        String mobs = Files.readString(Path.of("src", "main", "java", "net", "nerdsmp", "paper",
            "mob", "CustomMobManager.java"));
        String mechanics = Files.readString(Path.of("src", "main", "java", "net", "nerdsmp", "paper",
            "mob", "VanillaBossMechanicsService.java"));
        String healthBars = Files.readString(Path.of("src", "main", "java", "net", "nerdsmp", "paper",
            "presentation", "MobHealthBarService.java"));

        assertTrue(mobs.contains("entity.setCustomNameVisible(false)"));
        assertTrue(mobs.contains("entity.customName(null)"));
        assertTrue(mobs.contains("mob_display_name"));
        assertTrue(mechanics.contains("objective.setCustomNameVisible(false)"));
        assertTrue(healthBars.contains("viewer.hasLineOfSight(target)"));
        assertTrue(healthBars.contains("display.setShadowed(false)"));
        assertTrue(healthBars.contains("return mobs.displayName(target)"));
    }
}
