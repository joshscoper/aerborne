package net.nerdsmp.paper.listener;

import net.nerdsmp.api.model.Skill;

import java.util.Map;

/** Equipment power grants five percent skill XP per point for its matching activity. */
final class SkillPower {
    private SkillPower() {
    }

    static double experienceBonusPercent(Skill skill, Map<String, Double> stats) {
        double power = switch (skill) {
            case MINING -> stats.getOrDefault("mining-power", 0.0D);
            case EXCAVATION -> stats.getOrDefault("excavation-power", 0.0D);
            case FISHING -> stats.getOrDefault("fishing-power", 0.0D);
            case FORESTRY, FARMING -> stats.getOrDefault("gathering-power", 0.0D);
            default -> 0.0D;
        };
        return power * 5.0D;
    }
}
