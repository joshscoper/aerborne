package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InMemoryProgressionServiceTest {
    @Test
    void duplicateActivityCannotAwardExperienceTwice() {
        InMemoryProgressionService service = new InMemoryProgressionService();
        AccountId account = AccountId.random();

        assertTrue(service.awardExperience(account, Skill.MINING, 100L, "ore:1")
            .toCompletableFuture().join().awarded());
        assertFalse(service.awardExperience(account, Skill.MINING, 100L, "ore:1")
            .toCompletableFuture().join().awarded());
        assertEquals(1, service.snapshot(account).toCompletableFuture().join()
            .skills().get(Skill.MINING).level());
    }

    @Test
    void exactlyTwoEligibleMasteriesCanBeSelected() {
        InMemoryProgressionService service = new InMemoryProgressionService();
        AccountId account = AccountId.random();
        long masteryExperience = InMemoryProgressionService.experienceForLevel(ProgressionService.MASTERY_LEVEL);

        service.awardExperience(account, Skill.MINING, masteryExperience, "mining-mastery").toCompletableFuture().join();
        service.awardExperience(account, Skill.FORESTRY, masteryExperience, "forestry-mastery").toCompletableFuture().join();
        service.awardExperience(account, Skill.FARMING, masteryExperience, "farming-mastery").toCompletableFuture().join();

        assertEquals(ProgressionService.MasteryResult.SELECTED,
            service.selectMastery(account, Skill.MINING).toCompletableFuture().join());
        assertEquals(ProgressionService.MasteryResult.SELECTED,
            service.selectMastery(account, Skill.FORESTRY).toCompletableFuture().join());
        assertEquals(ProgressionService.MasteryResult.LIMIT_REACHED,
            service.selectMastery(account, Skill.FARMING).toCompletableFuture().join());
    }

    @Test
    void talentsRequireLevelsPointsAndThePreviousNode() {
        InMemoryProgressionService service = new InMemoryProgressionService();
        AccountId account = AccountId.random();

        assertEquals(ProgressionService.TalentResult.LEVEL_TOO_LOW,
            service.unlockTalent(account, "mining.ore_study.1").toCompletableFuture().join());
        service.awardExperience(account, Skill.MINING, ProgressionService.experienceForLevel(5), "level-five")
            .toCompletableFuture().join();
        assertEquals(ProgressionService.TalentResult.UNLOCKED,
            service.unlockTalent(account, "mining.ore_study.1").toCompletableFuture().join());
        assertEquals(ProgressionService.TalentResult.NO_POINTS_AVAILABLE,
            service.unlockTalent(account, "mining.contract_miner.1").toCompletableFuture().join());
        service.awardExperience(account, Skill.MINING, ProgressionService.experienceForLevel(20), "level-twenty")
            .toCompletableFuture().join();
        assertEquals(ProgressionService.TalentResult.PREREQUISITE_MISSING,
            service.unlockTalent(account, "mining.contract_miner.2").toCompletableFuture().join());
    }
}
