package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PassiveTalentStatsTest {
    @Test void durabilityPassivesFromDifferentSkillsStackOnceIncludingTempering() {
        var stats=PassiveTalentStats.resolve(Set.of("smithing.tempering.1","forestry.careful_tools.1","smithing.lasting_work.1","building.workshop_care.1"));
        assertEquals(12,stats.get("durability-save-percent"));
        assertEquals(1,stats.size());
    }
    @Test void everyGlobalPassiveIsResolvedIndependentlyOfBranchPosition() {
        Set<String> talents=new HashSet<>();
        for(Skill skill:Skill.values()) for(var branch:SkillTreeCatalog.tree(skill).branches()) for(var node:branch.nodes()) talents.add(node.id());
        var stats=PassiveTalentStats.resolve(talents);
        assertEquals(51,stats.get("durability-save-percent"));assertEquals(65,stats.get("geode-find-percent"));
        assertEquals(20,stats.get("fishing-luck-percent"));assertEquals(2.25,stats.get("life-steal-percent"));
        assertEquals(20,stats.get("excavation-find-percent"));assertEquals(20,stats.get("poison-potency-percent"));
        assertEquals(6,stats.size());
    }
    @Test void scopedBonusesCannotLeakIntoOtherSkillsOrBeCountedTwice() {
        var stats=PassiveTalentStats.resolve(Set.of("forestry.woodcraft.1","forestry.timber_trade.1","forestry.clean_cuts.1", "cooking.nourishment.1","cooking.seasoning.1","farming.stockbreeder.1","removed.legacy.1"));
        assertTrue(stats.isEmpty());
    }
}
