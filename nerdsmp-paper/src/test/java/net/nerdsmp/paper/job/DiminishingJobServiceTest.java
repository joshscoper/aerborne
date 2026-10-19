package net.nerdsmp.paper.job;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.service.InMemoryEconomyService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiminishingJobServiceTest {
    @Test
    void rewardsDiminishAcrossDailyBandsAndActivitiesAreIdempotent() {
        InMemoryEconomyService economy = new InMemoryEconomyService();
        DiminishingJobService jobs = new DiminishingJobService(economy, 2L, 2L, 4L);
        AccountId account = AccountId.random();

        assertEquals(2L, jobs.recordWork(account, Skill.MINING, 2L, "ore-1")
            .toCompletableFuture().join().marks());
        assertEquals(1L, jobs.recordWork(account, Skill.MINING, 2L, "ore-2")
            .toCompletableFuture().join().marks());
        assertEquals(1L, jobs.recordWork(account, Skill.MINING, 4L, "ore-3")
            .toCompletableFuture().join().marks());

        var duplicate = jobs.recordWork(account, Skill.MINING, 2L, "ore-3")
            .toCompletableFuture().join();
        assertTrue(duplicate.duplicate());
        assertEquals(0L, duplicate.marks());

        var snapshot = jobs.snapshot(account).toCompletableFuture().join();
        assertEquals(8L, snapshot.workUnits().get(Skill.MINING));
        assertEquals(4L, snapshot.earnedMarks().get(Skill.MINING));
        assertEquals(4L, economy.balance(account).toCompletableFuture().join());
    }

    @Test
    void talentBonusRaisesActualAndReportedMarks() {
        InMemoryEconomyService economy = new InMemoryEconomyService();
        DiminishingJobService jobs = new DiminishingJobService(economy, 100L, 0L, 0L);
        AccountId account = AccountId.random();

        assertEquals(12L, jobs.recordWork(account, Skill.FARMING, 10L, "crop-1", 20.0D)
            .toCompletableFuture().join().marks());
        assertEquals(12L, jobs.snapshot(account).toCompletableFuture().join().earnedMarks().get(Skill.FARMING));

        DiminishingJobService smallJobs = new DiminishingJobService(economy, 100L, 0L, 0L);
        AccountId miner = AccountId.random();
        long paid = 0L;
        for (int index = 0; index < 5; index++) {
            paid += smallJobs.recordWork(miner, Skill.MINING, 1L, "ore-" + index, 20.0D)
                .toCompletableFuture().join().marks();
        }
        assertEquals(6L, paid);
    }
}
