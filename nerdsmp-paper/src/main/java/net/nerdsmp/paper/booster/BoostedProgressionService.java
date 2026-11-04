package net.nerdsmp.paper.booster;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;

import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.function.DoubleSupplier;

/** Every XP award, from every source, goes through here and is raised while a booster runs. Staff grants aren't. */
public final class BoostedProgressionService implements ProgressionService {
    private final ProgressionService delegate;
    private final DoubleSupplier multiplier;

    public BoostedProgressionService(ProgressionService delegate, DoubleSupplier multiplier) {
        this.delegate = delegate;
        this.multiplier = multiplier;
    }

    /** What an award is worth: staff grants (fingerprint {@code admin:}) stay as asked. */
    static long boosted(long experience, String fingerprint, double multiplier) {
        if (experience <= 0 || multiplier <= 1D || fingerprint != null && fingerprint.startsWith("admin:")) return experience;
        return Math.round(experience * multiplier);
    }

    @Override public CompletionStage<ProgressionSnapshot> snapshot(AccountId account) { return delegate.snapshot(account); }

    @Override public CompletionStage<AwardResult> awardExperience(AccountId account, Skill skill, long experience, String fingerprint) {
        return delegate.awardExperience(account, skill, boosted(experience, fingerprint, multiplier.getAsDouble()), fingerprint);
    }

    @Override public CompletionStage<SkillProgress> setExperience(AccountId account, Skill skill, long experience) { return delegate.setExperience(account, skill, experience); }
    @Override public CompletionStage<MasteryResult> selectMastery(AccountId account, Skill skill) { return delegate.selectMastery(account, skill); }
    @Override public CompletionStage<MasteryResult> removeMastery(AccountId account, Skill skill, Instant now) { return delegate.removeMastery(account, skill, now); }
    @Override public CompletionStage<TalentResult> unlockTalent(AccountId account, String talent) { return delegate.unlockTalent(account, talent); }
}
