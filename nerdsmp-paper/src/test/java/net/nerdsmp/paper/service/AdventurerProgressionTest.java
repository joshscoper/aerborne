package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AdventurerProgression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AdventurerProgressionTest {
    @Test
    void combinedSkillExperienceDrivesOneToOneHundredProgression() {
        assertEquals(1, AdventurerProgression.levelForExperience(0L));
        assertEquals(1, AdventurerProgression.levelForExperience(199L));
        assertEquals(2, AdventurerProgression.levelForExperience(200L));
        assertEquals(100, AdventurerProgression.levelForExperience(2_000_000L));
    }

    @Test
    void ranksUnlockAtPublishedLevelThresholds() {
        assertEquals(8, AdventurerProgression.Rank.values().length);
        assertEquals(AdventurerProgression.Rank.WANDERER, AdventurerProgression.rankForLevel(9));
        assertEquals(AdventurerProgression.Rank.SCOUT, AdventurerProgression.rankForLevel(10));
        assertEquals(AdventurerProgression.Rank.VANGUARD, AdventurerProgression.rankForLevel(50));
        assertEquals(AdventurerProgression.Rank.LEGEND, AdventurerProgression.rankForLevel(99));
        assertEquals(AdventurerProgression.Rank.MYTHIC, AdventurerProgression.rankForLevel(100));
        assertEquals(5.0D, AdventurerProgression.rankStat(70, "skill-xp-percent"));
    }
}
