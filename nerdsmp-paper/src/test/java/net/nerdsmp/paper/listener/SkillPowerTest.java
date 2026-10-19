package net.nerdsmp.paper.listener;

import net.nerdsmp.api.model.Skill;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SkillPowerTest {
    @Test
    void excavationUsesItsSpadePowerRatherThanMiningPower() {
        assertEquals(15.0D, SkillPower.experienceBonusPercent(Skill.EXCAVATION,
            Map.of("excavation-power", 3.0D, "mining-power", 8.0D)));
    }

    @Test
    void miningPowerAloneDoesNotBoostExcavation() {
        assertEquals(0.0D, SkillPower.experienceBonusPercent(Skill.EXCAVATION,
            Map.of("mining-power", 8.0D)));
    }

    @Test
    void miningKeepsItsOwnPowerBonusAndIgnoresSpadePower() {
        assertEquals(15.0D, SkillPower.experienceBonusPercent(Skill.MINING,
            Map.of("mining-power", 3.0D, "excavation-power", 8.0D)));
    }

    @Test
    void fishingKeepsItsOwnPowerBonus() {
        assertEquals(10.0D, SkillPower.experienceBonusPercent(Skill.FISHING,
            Map.of("fishing-power", 2.0D, "gathering-power", 8.0D)));
    }

    @Test
    void farmingAndForestryKeepFractionalGatheringPower() {
        Map<String, Double> stats = Map.of("gathering-power", 2.5D,
            "mining-power", 8.0D, "excavation-power", 8.0D);
        assertEquals(12.5D, SkillPower.experienceBonusPercent(Skill.FARMING, stats));
        assertEquals(12.5D, SkillPower.experienceBonusPercent(Skill.FORESTRY, stats));
    }

    @Test
    void unrelatedSkillsNeverInheritGatheringToolBonuses() {
        Map<String, Double> stats = Map.of("mining-power", 8.0D, "excavation-power", 8.0D,
            "fishing-power", 8.0D, "gathering-power", 8.0D);
        for (Skill skill : new Skill[]{Skill.HUNTING, Skill.SMITHING, Skill.ALCHEMY, Skill.BUILDING}) {
            assertEquals(0.0D, SkillPower.experienceBonusPercent(skill, stats), skill.name());
        }
    }

    @Test
    void equipmentWithoutPowerNeverChangesSkillExperience() {
        for (Skill skill : Skill.values()) {
            assertEquals(0.0D, SkillPower.experienceBonusPercent(skill, Map.of()), skill.name());
        }
    }
}
