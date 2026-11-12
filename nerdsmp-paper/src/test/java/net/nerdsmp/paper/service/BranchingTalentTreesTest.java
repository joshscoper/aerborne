package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class BranchingTalentTreesTest {
    @TempDir Path directory;
    @Test void everyProfessionHasAnAcyclicConnectedGraphAndPreservesLegacyIds() {
        Set<String> unique=new HashSet<>();
        for(Skill skill:Skill.values()) {
            var tree=SkillTreeCatalog.tree(skill);
            assertEquals(4,tree.branches().size()); assertEquals(4,tree.junctions().size());
            assertEquals(16,SkillTreeCatalog.nodes(skill).size());
            Set<String> seen=new HashSet<>();
            for(var branch:tree.branches()) for(var node:branch.nodes()) {
                assertTrue(unique.add(node.id())); assertTrue(seen.containsAll(node.requirement().options())); seen.add(node.id());
                assertEquals(skill,node.skill());
            }
            for(var node:tree.junctions()) {
                assertTrue(unique.add(node.id())); assertTrue(seen.containsAll(node.requirement().options())); seen.add(node.id());
                assertEquals(skill,node.skill());
            }
        }
        assertTrue(SkillTreeCatalog.node("mining.ore_study.1").isPresent());
        assertEquals(11*16,unique.size());
    }
    @Test void eitherParentCanOpenEachSharedProfessionTalentAndTwoOpenTheCapstone() {
        for(Skill skill:Skill.values()) for(int junction=0;junction<3;junction++) for(int parent=junction;parent<=junction+1;parent++) {
            var service=new InMemoryProgressionService();var account=AccountId.random();var tree=SkillTreeCatalog.tree(skill);
            service.awardExperience(account,skill,250000,"train");
            var shared=tree.junctions().get(junction);
            assertEquals(ProgressionService.TalentResult.PREREQUISITE_MISSING,service.unlockTalent(account,shared.id()).toCompletableFuture().join());
            var path=tree.branches().get(parent).nodes();
            service.unlockTalent(account,path.get(0).id());service.unlockTalent(account,path.get(1).id());
            assertEquals(ProgressionService.TalentResult.UNLOCKED,service.unlockTalent(account,shared.id()).toCompletableFuture().join());
            assertEquals(ProgressionService.TalentResult.PREREQUISITE_MISSING,service.unlockTalent(account,tree.junctions().get(3).id()).toCompletableFuture().join());
        }
        var service=new InMemoryProgressionService();var account=AccountId.random();var tree=SkillTreeCatalog.tree(Skill.MINING);
        service.awardExperience(account,Skill.MINING,250000,"train");
        for(var n:tree.branches().get(1).nodes().subList(0,2)) service.unlockTalent(account,n.id());
        service.unlockTalent(account,tree.junctions().get(0).id());service.unlockTalent(account,tree.junctions().get(1).id());
        assertEquals(ProgressionService.TalentResult.UNLOCKED,service.unlockTalent(account,tree.junctions().get(3).id()).toCompletableFuture().join());
        assertEquals(5,service.snapshot(account).toCompletableFuture().join().talentPointsSpent(Skill.MINING));
    }
    @Test void adventurerAndProfessionPointsCannotSpendEachOthersBudget() {
        var service=new InMemoryProgressionService();var account=AccountId.random();
        service.awardExperience(account,Skill.MINING,3200,"first-adventure");
        var before=service.snapshot(account).toCompletableFuture().join();
        assertEquals(5,before.adventurer().level());assertEquals(1,before.adventurerPointsAvailable());assertEquals(1,before.talentPointsAvailable(Skill.MINING));
        assertEquals(ProgressionService.TalentResult.UNLOCKED,service.unlockTalent(account,"ADVENTURER.WAYFARER.1").toCompletableFuture().join());
        assertEquals(ProgressionService.TalentResult.NO_POINTS_AVAILABLE,service.unlockTalent(account,"adventurer.warden.1").toCompletableFuture().join());
        var after=service.snapshot(account).toCompletableFuture().join();
        assertEquals(1,after.talentPointsAvailable(Skill.MINING));assertEquals(1,after.adventurerStat("skill-xp-percent"));
        assertEquals(ProgressionService.TalentResult.UNLOCKED,service.unlockTalent(account,"mining.ore_study.1").toCompletableFuture().join());
        assertEquals(0,service.snapshot(account).toCompletableFuture().join().talentPointsAvailable(Skill.MINING));
    }
    @Test void adventurerChoicesSurviveRestartRemainSeasonalAndDeduplicate() {
        var data=new PlayerDataStore(directory.toFile(),Logger.getAnonymousLogger());var account=AccountId.random();
        var service=new InMemoryProgressionService(data,"test");
        for(Skill skill:Skill.values()) service.awardExperience(account,skill,250000,"train");
        for(var node:AdventurerTalents.branch(1).subList(0,2)) assertEquals(ProgressionService.TalentResult.UNLOCKED,service.unlockTalent(account,node.id()).toCompletableFuture().join());
        for(var node:AdventurerTalents.junctions().subList(0,2)) service.unlockTalent(account,node.id());
        assertEquals(ProgressionService.TalentResult.UNLOCKED,service.unlockTalent(account,"adventurer.crossroads.capstone").toCompletableFuture().join());
        var snapshot=service.snapshot(account).toCompletableFuture().join();assertEquals(11,snapshot.adventurerPointsEarned());
        var restarted=new InMemoryProgressionService(new PlayerDataStore(directory.toFile(),Logger.getAnonymousLogger()),"test");
        assertEquals(snapshot,restarted.snapshot(account).toCompletableFuture().join());
        assertEquals(ProgressionService.TalentResult.ALREADY_UNLOCKED,restarted.unlockTalent(account,"adventurer.crossroads.capstone").toCompletableFuture().join());
        assertTrue(new InMemoryProgressionService(data,"next").snapshot(account).toCompletableFuture().join().talents().isEmpty());
        assertEquals(13,snapshot.adventurerStat("skill-xp-percent")); // automatic ranks 10%, new capstone 3%
    }
}
