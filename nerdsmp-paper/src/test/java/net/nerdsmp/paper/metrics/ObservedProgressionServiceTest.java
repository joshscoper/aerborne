package net.nerdsmp.paper.metrics;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import static org.junit.jupiter.api.Assertions.*;

final class ObservedProgressionServiceTest {
    static final class Fake implements ProgressionService {
        CompletableFuture<AwardResult> next=new CompletableFuture<>();
        public CompletionStage<ProgressionSnapshot> snapshot(AccountId a){return CompletableFuture.completedFuture(ProgressionSnapshot.empty());}
        public CompletionStage<AwardResult> awardExperience(AccountId a,Skill s,long xp,String key){return next;}
        public CompletionStage<MasteryResult> selectMastery(AccountId a,Skill s){return CompletableFuture.completedFuture(MasteryResult.SELECTED);}
        public CompletionStage<MasteryResult> removeMastery(AccountId a,Skill s,Instant n){return CompletableFuture.completedFuture(MasteryResult.REMOVED);}
        public CompletionStage<TalentResult> unlockTalent(AccountId a,String s){return CompletableFuture.completedFuture(TalentResult.UNLOCKED);}
    }
    @Test void onlyCommittedAwardsAreObservedAndFingerprintsAreNotRetained() {
        var fake=new Fake();var events=new ArrayList<ObservedProgressionService.Award>();var service=new ObservedProgressionService(fake,events::add);
        var id=AccountId.random();var pending=service.awardExperience(id,Skill.SMITHING,32,"craft:private-operation-id");
        assertTrue(events.isEmpty());fake.next.complete(new ProgressionService.AwardResult(true,new ProgressionService.SkillProgress(32,0),false));
        assertTrue(pending.toCompletableFuture().join().awarded());assertEquals(1,events.size());assertEquals("craft",events.getFirst().source());
        fake.next=CompletableFuture.completedFuture(new ProgressionService.AwardResult(false,new ProgressionService.SkillProgress(32,0),false));
        service.awardExperience(id,Skill.SMITHING,32,"craft:retry").toCompletableFuture().join();assertEquals(1,events.size());
    }
    @Test void metricsCannotFailAwardsAndOtherProgressionCallsStillDelegate() {
        var fake=new Fake();fake.next=CompletableFuture.completedFuture(new ProgressionService.AwardResult(true,new ProgressionService.SkillProgress(10,0),false));
        var service=new ObservedProgressionService(fake,event->{throw new IllegalStateException("metrics unavailable");});var id=AccountId.random();
        assertTrue(service.awardExperience(id,Skill.MINING,10,"test").toCompletableFuture().join().awarded());
        assertEquals(ProgressionService.MasteryResult.SELECTED,service.selectMastery(id,Skill.MINING).toCompletableFuture().join());
        assertEquals(ProgressionService.TalentResult.UNLOCKED,service.unlockTalent(id,"test").toCompletableFuture().join());
    }
    @Test void unstructuredOperationIdsAreNeverRetainedAndFailedAwardsAreNotCounted() {
        var fake=new Fake();var events=new ArrayList<ObservedProgressionService.Award>();var service=new ObservedProgressionService(fake,events::add);var id=AccountId.random();
        fake.next=CompletableFuture.completedFuture(new ProgressionService.AwardResult(true,new ProgressionService.SkillProgress(10,0),false));
        service.awardExperience(id,Skill.MINING,10,java.util.UUID.randomUUID().toString()).toCompletableFuture().join();
        service.awardExperience(id,Skill.MINING,10,java.util.UUID.randomUUID()+":reward").toCompletableFuture().join();
        assertTrue(events.stream().allMatch(event->event.source().equals("unknown")));
        fake.next=CompletableFuture.failedFuture(new IllegalStateException("rejected"));
        assertThrows(java.util.concurrent.CompletionException.class,()->service.awardExperience(id,Skill.MINING,10,"mining:failed").toCompletableFuture().join());
        assertEquals(2,events.size());
    }
}
