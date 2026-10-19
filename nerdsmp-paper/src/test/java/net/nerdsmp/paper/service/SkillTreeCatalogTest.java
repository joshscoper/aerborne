package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.model.SkillTreeCatalog;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SkillTreeCatalogTest {
    @Test
    void everySkillHasFourBranchesAndTwelveUniqueNodes() {
        Set<String> ids = new HashSet<>();
        for (Skill skill : Skill.values()) {
            var tree = SkillTreeCatalog.tree(skill);
            assertEquals(4, tree.branches().size());
            assertEquals(12, tree.branches().stream().mapToInt(branch -> branch.nodes().size()).sum());
            tree.branches().forEach(branch -> branch.nodes().forEach(node -> ids.add(node.id())));
        }
        assertEquals(Skill.values().length * 12, ids.size());
    }

    @Test
    void aMaxedSkillEarnsEightOfTwelvePossiblePoints() {
        assertEquals(0, SkillTreeCatalog.earnedPoints(4));
        assertEquals(1, SkillTreeCatalog.earnedPoints(5));
        assertEquals(8, SkillTreeCatalog.earnedPoints(50));
    }

    @Test
    void milestoneQueriesPreserveTheOriginalPointBudgetAndExposeNoMutableArrays() {
        assertEquals(java.util.List.of(5, 10, 18, 27, 37, 40, 47, 50), SkillTreeCatalog.pointLevels());
        assertEquals(8, SkillTreeCatalog.maximumPoints());
        assertEquals(5, SkillTreeCatalog.nextPointLevel(0).orElseThrow());
        assertEquals(18, SkillTreeCatalog.nextPointLevel(10).orElseThrow());
        assertEquals(50, SkillTreeCatalog.nextPointLevel(47).orElseThrow());
        assertTrue(SkillTreeCatalog.nextPointLevel(50).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> SkillTreeCatalog.pointLevels().clear());
        for (Skill skill : Skill.values()) for (var branch : SkillTreeCatalog.tree(skill).branches()) {
            assertEquals(branch.nodes().stream().mapToDouble(SkillTreeCatalog.TalentNode::value).sum(), branch.maximumValue());
            assertEquals(branch.maximumValue(), branch.valueThrough(3));
            assertEquals(0.0, branch.valueThrough(0));
        }
    }

    @Test
    void buildingSureFootingPathProvidesFiftyPercentProtection() {
        Set<String> path = Set.of(
            "building.sure_footing.1",
            "building.sure_footing.2",
            "building.sure_footing.3"
        );

        assertEquals(50.0D,
            SkillTreeCatalog.stat(path, Skill.BUILDING, "building-fall-reduction-percent"));
    }
}
