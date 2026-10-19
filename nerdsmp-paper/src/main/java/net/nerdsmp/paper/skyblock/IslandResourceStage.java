package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.Skill;

import java.util.Map;
import java.util.Optional;

/** Resource unlocks are separate from island size, so existing builds never move or shrink. */
public enum IslandResourceStage {
    SETTLER("Settler", 1, Map.of()),
    HOMESTEAD("Homestead", 1, IslandChapter.HOMESTEAD.requirements()),
    WORKSHOP("Workshop", 1, IslandChapter.WORKSHOP.requirements()),
    SANCTUARY("Sanctuary", 1, IslandChapter.SANCTUARY.requirements()),
    FOUNDRY("Foundry", 15, Map.of(Skill.MINING, 25, Skill.SMITHING, 20, Skill.EXCAVATION, 20)),
    AETHERWORKS("Aetherworks", 25, Map.of(Skill.MINING, 35, Skill.ALCHEMY, 30, Skill.FARMING, 30));

    private final String title;
    private final int islandLevel;
    private final Map<Skill, Integer> requirements;

    IslandResourceStage(String title, int islandLevel, Map<Skill, Integer> requirements) {
        this.title = title;
        this.islandLevel = islandLevel;
        this.requirements = Map.copyOf(requirements);
    }

    public int index() { return ordinal(); }
    public String title() { return title; }
    public int islandLevel() { return islandLevel; }
    public Map<Skill, Integer> requirements() { return requirements; }
    public Optional<IslandResourceStage> next() {
        return ordinal() + 1 < values().length ? Optional.of(values()[ordinal() + 1]) : Optional.empty();
    }

    public static IslandResourceStage at(int stage) {
        if (stage < 0 || stage >= values().length) throw new IllegalArgumentException("Invalid island resource stage");
        return values()[stage];
    }
}
