package net.nerdsmp.paper.metrics;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Observe accepted awards from every producer, including quests, without changing service results. */
public final class ObservedProgressionService implements ProgressionService {
    public record Award(AccountId account,Skill skill,long experience,String source) { }
    private final ProgressionService delegate;
    private final Consumer<Award> listener;
    public ObservedProgressionService(ProgressionService delegate,Consumer<Award> listener) { this.delegate=delegate;this.listener=listener; }
    @Override public CompletionStage<ProgressionSnapshot> snapshot(AccountId id) { return delegate.snapshot(id); }
    @Override public CompletionStage<AwardResult> awardExperience(AccountId id,Skill skill,long experience,String fingerprint) {
        return delegate.awardExperience(id,skill,experience,fingerprint).whenComplete((result,error)->{
            if(error==null&&result!=null&&result.awarded())try { listener.accept(new Award(id,skill,experience,category(fingerprint))); }catch(RuntimeException ignored) { }
        });
    }
    private static String category(String fingerprint) {
        if(fingerprint==null||!fingerprint.contains(":"))return "unknown";
        String prefix=fingerprint.substring(0,fingerprint.indexOf(':'));
        return prefix.matches("[a-z][a-z_.-]{0,31}")?prefix:"unknown";
    }
    @Override public CompletionStage<SkillProgress> setExperience(AccountId id,Skill skill,long experience) { return delegate.setExperience(id,skill,experience); }
    @Override public CompletionStage<MasteryResult> selectMastery(AccountId id,Skill skill) { return delegate.selectMastery(id,skill); }
    @Override public CompletionStage<MasteryResult> removeMastery(AccountId id,Skill skill,Instant now) { return delegate.removeMastery(id,skill,now); }
    @Override public CompletionStage<TalentResult> unlockTalent(AccountId id,String talent) { return delegate.unlockTalent(id,talent); }
}
