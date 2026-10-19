package net.nerdsmp.paper.gui;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.model.SkillTreeCatalog;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.service.InMemoryProgressionService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import static net.nerdsmp.paper.gui.SkillTreePresentation.State.*;
import static org.junit.jupiter.api.Assertions.*;

class SkillTreePresentationTest {
    private ProgressionService.ProgressionSnapshot snapshot(Skill skill, int level, Set<String> talents) {
        EnumMap<Skill, ProgressionService.SkillProgress> skills = new EnumMap<>(Skill.class);
        for (Skill value : Skill.values()) skills.put(value, new ProgressionService.SkillProgress(0, 0));
        skills.put(skill, new ProgressionService.SkillProgress(ProgressionService.experienceForLevel(level), level));
        return new ProgressionService.ProgressionSnapshot(skills, Set.of(), talents, Instant.EPOCH);
    }
    @Test void threeEvenlySpacedPathsNeverOverwriteEachOtherOrNavigation() {
        Set<Integer> occupied = new HashSet<>(Set.of(0, 8, 45, 53));
        for (int branch = 0; branch < 4; branch++) {
            assertTrue(occupied.add(SkillTreePresentation.branchSlot(branch)));
            for (int tier = 1; tier <= 3; tier++) {
                int slot = SkillTreePresentation.nodeSlot(branch, tier);
                assertTrue(occupied.add(slot)); assertTrue(slot >= 0 && slot < 54);
                assertEquals(SkillTreePresentation.branchSlot(branch), slot % 9);
                if (tier < 3) assertTrue(occupied.add(SkillTreePresentation.linkSlot(branch, tier)));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> SkillTreePresentation.nodeSlot(4, 1));
        assertThrows(IllegalArgumentException.class, () -> SkillTreePresentation.nodeSlot(0, 4));
        assertThrows(IllegalArgumentException.class, () -> SkillTreePresentation.linkSlot(1, 3));
    }
    @Test void everySkillIncludingBuildingUsesTheSameActualPurchaseRules() {
        for (Skill skill : Skill.values()) {
            var service = new InMemoryProgressionService();
            AccountId id = AccountId.random();
            var tree = SkillTreeCatalog.tree(skill);
            assertEquals(LEVEL_LOCKED, SkillTreePresentation.node(service.snapshot(id).toCompletableFuture().join(), tree.branches().getFirst().nodes().getFirst()).state());
            service.awardExperience(id, skill, ProgressionService.experienceForLevel(50), "training").toCompletableFuture().join();
            assertEquals(PATH_LOCKED, SkillTreePresentation.node(service.snapshot(id).toCompletableFuture().join(), tree.branches().getFirst().nodes().get(1)).state());
            for (int branch = 0; branch < 2; branch++) for (var node : tree.branches().get(branch).nodes()) {
                assertEquals(AVAILABLE, SkillTreePresentation.node(service.snapshot(id).toCompletableFuture().join(), node).state());
                assertEquals(ProgressionService.TalentResult.UNLOCKED, service.unlockTalent(id, node.id()).toCompletableFuture().join());
                assertEquals(UNLOCKED, SkillTreePresentation.node(service.snapshot(id).toCompletableFuture().join(), node).state());
            }
            var exhausted = service.snapshot(id).toCompletableFuture().join();
            assertEquals(6, exhausted.talentPointsSpent(skill));
            assertEquals(AVAILABLE, SkillTreePresentation.node(exhausted, tree.branches().get(2).nodes().getFirst()).state());
            assertEquals(ProgressionService.TalentResult.UNLOCKED, service.unlockTalent(id, tree.branches().get(2).nodes().getFirst().id()).toCompletableFuture().join());
        }
    }
    @Test void previewNamesTheExactPrerequisiteCostAndCumulativeBenefit() {
        var node = SkillTreeCatalog.node("building.sure_footing.3").orElseThrow();
        var view = SkillTreePresentation.node(snapshot(Skill.BUILDING, 35, Set.of()), node);
        String lore = String.join(" ", view.lore());
        assertEquals(PATH_LOCKED, view.state());
        assertTrue(lore.contains("Sure Footing II required"));
        assertTrue(lore.contains("1 Building point"));
        assertTrue(lore.contains("+50%"));
        assertThrows(UnsupportedOperationException.class, () -> view.lore().clear());
    }
    @Test void nextPointShowsItsRealLevelAndExperienceGap() {
        var lore = SkillTreePresentation.overview(snapshot(Skill.MINING, 10, Set.of()), Skill.MINING);
        assertTrue(String.join(" ", lore).contains("Mining 18"));
        assertTrue(String.join(" ", lore).contains("22,400 skill XP"));
        assertTrue(String.join(" ", SkillTreePresentation.overview(snapshot(Skill.MINING, 50, Set.of()), Skill.MINING)).contains("All eight"));
    }
    @Test void cappedExperienceAndDailyJobLimitsAreNotHiddenFromPlayers() {
        var maxed = snapshot(Skill.MINING, 50, Set.of("mining.ore_study.1"));
        var tree = SkillTreeCatalog.tree(Skill.MINING);
        assertTrue(String.join(" ", SkillTreePresentation.branch(maxed, Skill.MINING, tree.branches().getFirst())).contains("more XP has no effect"));
        var xp = SkillTreePresentation.node(maxed, tree.branches().getFirst().nodes().getFirst());
        assertTrue(String.join(" ", xp.lore()).contains("reached its cap"));
        assertFalse(String.join(" ", xp.lore()).contains("This bonus is active"));
        assertTrue(String.join(" ", SkillTreePresentation.branch(maxed, Skill.MINING, tree.branches().get(1))).contains("Daily work limits"));
    }
    @Test void farmingAndForestryExplainTheirRealNewControls() {
        String farming = String.join(" ", SkillTreePresentation.branch(snapshot(Skill.FARMING, 5, Set.of()), Skill.FARMING, SkillTreeCatalog.tree(Skill.FARMING).branches().get(2)));
        assertTrue(farming.contains("Right-click ripe crops with any hoe"));
        assertTrue(farming.contains("without crouching")); assertTrue(farming.contains("available without talents"));
        assertTrue(farming.contains("One seed is kept from the harvest"));
        String forestry = String.join(" ", SkillTreePresentation.branch(snapshot(Skill.FORESTRY, 5, Set.of()), Skill.FORESTRY, SkillTreeCatalog.tree(Skill.FORESTRY).branches().get(2)));
        assertTrue(forestry.contains("Timber Rhythm"));
        var hunting = SkillTreeCatalog.node("hunting.slayer.1").orElseThrow();
        assertTrue(hunting.effect().contains("PvE weapon damage")); assertFalse(hunting.effect().contains("custom"));
    }
}
