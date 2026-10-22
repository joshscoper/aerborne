package net.nerdsmp.paper.item;

import net.nerdsmp.api.service.ProgressionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLampsTest {
    @Test void bottlesGrantAShareOfTheNextLevelWithAFloor() {
        // Level 10 needs 100 * (11^2 - 10^2) = 2,100 XP for level 11.
        assertEquals(210, SkillLamps.experience(SkillLamps.Grade.PLAIN, 10));
        assertEquals(525, SkillLamps.experience(SkillLamps.Grade.GREATER, 10));
        assertEquals(1260, SkillLamps.experience(SkillLamps.Grade.GRAND, 10));
        assertEquals(40, SkillLamps.experience(SkillLamps.Grade.PLAIN, 0), "The floor keeps a fresh skill's bottle worthwhile");
        assertEquals(250, SkillLamps.experience(SkillLamps.Grade.GRAND, 0));
    }

    @Test void higherGradesAlwaysGiveMoreAndMasteredSkillsGetNothing() {
        for (int level = 0; level < ProgressionService.MAX_LEVEL; level++) {
            assertTrue(SkillLamps.experience(SkillLamps.Grade.GREATER, level) > SkillLamps.experience(SkillLamps.Grade.PLAIN, level));
            assertTrue(SkillLamps.experience(SkillLamps.Grade.GRAND, level) > SkillLamps.experience(SkillLamps.Grade.GREATER, level));
        }
        assertEquals(0, SkillLamps.experience(SkillLamps.Grade.GRAND, ProgressionService.MAX_LEVEL));
        assertEquals(SkillLamps.Grade.GRAND, SkillLamps.Grade.parse("GRAND").orElseThrow());
        assertTrue(SkillLamps.Grade.parse("lamp").isEmpty());
    }
}
