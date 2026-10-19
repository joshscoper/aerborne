package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import java.util.Map;

/** Unlocks are earned through RPG skills, never block-value scores or paid perks. */
public enum IslandChapter {
    HOMESTEAD("A Place to Grow", Map.of(Skill.MINING, 5, Skill.FARMING, 5, Skill.BUILDING, 5)),
    WORKSHOP("Tools of the Trade", Map.of(Skill.SMITHING, 10, Skill.FORESTRY, 10, Skill.HUNTING, 10)),
    SANCTUARY("Beyond the Horizon", Map.of(Skill.FISHING, 15, Skill.EXCAVATION, 15, Skill.ALCHEMY, 15));

    private final String title;
    private final Map<Skill, Integer> requirements;
    IslandChapter(String title, Map<Skill, Integer> requirements) {
        this.title = title; this.requirements = Map.copyOf(requirements);
    }
    public String title() { return title; }
    public Map<Skill, Integer> requirements() { return requirements; }
    public boolean qualifies(ProgressionService.ProgressionSnapshot snapshot) {
        return requirements.entrySet().stream().allMatch(e -> snapshot.skills()
            .getOrDefault(e.getKey(), new ProgressionService.SkillProgress(0, 0)).level() >= e.getValue());
    }
}
