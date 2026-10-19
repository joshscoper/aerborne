package net.nerdsmp.paper.forge.arcane;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Arcane Research rules. An experiment combines one to three Aspects (one socket gem each) and
 * resolves deterministically against the registry. Nothing here is random.
 *
 * <ul>
 *   <li>Exactly matching an undiscovered definition advances it by its tier's progress, scaled by
 *       gem tier. Fundamentals are discovered in one experiment.</li>
 *   <li>A subset of a larger undiscovered definition gives smaller "partial" progress, capped below
 *       discovery, that shrinks with each repeat, and reveals a clue once it passes a threshold.</li>
 *   <li>Every Aspect used for the first time is studied for the Aspect encyclopedia.</li>
 *   <li>The preview never reveals what an untried combination does. A combination the player has
 *       already tried and that can teach nothing more is refused before anything is spent.</li>
 * </ul>
 */
public final class ArcaneResearch {
    private ArcaneResearch() {
    }

    public record Reagent(ArcaneAspect aspect, int tier) {
        public Reagent {
            tier = Math.max(1, Math.min(4, tier));
        }
    }

    public enum Status {
        READY,
        /** No reagents, too many, or the same Aspect twice. */
        INVALID,
        /** Research is not unlocked at this Enchanting level. */
        LOCKED,
        /** This many Aspects needs a higher Enchanting level. */
        NEEDS_LEVEL,
        /** Three-Aspect research needs a mastered enchantment sharing one of the Aspects. */
        NEEDS_MASTERY,
        /** Its exact definition is dormant on this server. Nothing may be spent. */
        FEATURE_DISABLED,
        /** The exact recipe is already known. */
        ALREADY_KNOWN,
        /** Already tried, and it can teach nothing more. */
        FRUITLESS
    }

    public record Plan(Status status, Set<ArcaneAspect> aspects, String combination, int dustCost,
                       double multiplier, int requiredLevel, boolean prismatic) {
        public Plan {
            aspects = aspects.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(aspects));
        }

        public boolean ready() {
            return status == Status.READY;
        }
    }

    public enum OutcomeKind { DISCOVERED, PROGRESS, PARTIAL, CLUE, ASPECT_STUDIED, NO_RESONANCE }

    /** {@code definition} is null for ASPECT_STUDIED and NO_RESONANCE. PARTIAL never names its definition to the player. */
    public record Outcome(OutcomeKind kind, ArcaneDefinition definition, ArcaneAspect aspect, int from, int to) {
    }

    public record Result(ResearchProfile profile, List<Outcome> outcomes, long experience, boolean duplicate) {
        public Result {
            outcomes = List.copyOf(outcomes);
        }

        public boolean discovered() {
            return outcomes.stream().anyMatch(outcome -> outcome.kind() == OutcomeKind.DISCOVERED);
        }
    }

    public static Plan plan(ArcaneRegistry registry, ArcaneSettings.Research rules, ResearchProfile profile,
                            List<Reagent> reagents, boolean prismatic, int arcane) {
        return plan(registry, rules, profile, reagents, prismatic, arcane, 0.0D);
    }

    /** {@code bonusPercent} is the Keen Insight talent: it scales progress, never what is revealed. */
    public static Plan plan(ArcaneRegistry registry, ArcaneSettings.Research rules, ResearchProfile profile,
                            List<Reagent> reagents, boolean prismatic, int arcane, double bonusPercent) {
        Set<ArcaneAspect> aspects = reagents.isEmpty() ? EnumSet.noneOf(ArcaneAspect.class)
            : EnumSet.copyOf(reagents.stream().map(Reagent::aspect).toList());
        String combination = ArcaneAspect.key(aspects);
        int dust = rules.dustPerReagent() * reagents.size();
        double multiplier = reagents.stream().mapToDouble(reagent -> rules.gemMultiplier(reagent.tier())).average().orElse(1.0D)
            * (prismatic ? rules.prismaticMultiplier() : 1.0D) * (1.0D + Math.max(0.0D, bonusPercent) / 100.0D);
        if (reagents.isEmpty() || reagents.size() > 3 || aspects.size() != reagents.size()) {
            return new Plan(Status.INVALID, aspects, combination, dust, multiplier, rules.unlockLevel(), prismatic);
        }
        if (arcane < rules.unlockLevel()) {
            return new Plan(Status.LOCKED, aspects, combination, dust, multiplier, rules.unlockLevel(), prismatic);
        }
        ArcaneTier tier = ArcaneTier.forAspectCount(aspects.size());
        int required = rules.requiredLevel(tier);
        if (arcane < required) {
            return new Plan(Status.NEEDS_LEVEL, aspects, combination, dust, multiplier, required, prismatic);
        }
        if (tier == ArcaneTier.SIGNATURE && !masteredSharing(registry, rules, profile, aspects)) {
            return new Plan(Status.NEEDS_MASTERY, aspects, combination, dust, multiplier, required, prismatic);
        }
        Optional<ArcaneDefinition> exact = registry.exact(aspects);
        if (exact.isPresent() && profile.known(exact.get().id())) {
            return new Plan(Status.ALREADY_KNOWN, aspects, combination, dust, multiplier, required, prismatic);
        }
        Plan ready = new Plan(Status.READY, aspects, combination, dust, multiplier, required, prismatic);
        if (profile.attempts(combination) > 0 && teaches(registry, rules, profile, ready).isEmpty()) {
            return new Plan(Status.FRUITLESS, aspects, combination, dust, multiplier, required, prismatic);
        }
        return ready;
    }

    /**
     * Applies a ready plan. The receipt makes this idempotent: applying the same receipt twice
     * returns the profile unchanged and marks the result as a duplicate.
     */
    public static Result apply(ArcaneRegistry registry, ArcaneSettings.Research rules, ResearchProfile profile,
                               Plan plan, String receipt, String historyLine) {
        if (!plan.ready()) throw new IllegalArgumentException("Only a ready plan can be applied: " + plan.status());
        if (profile.hasReceipt(receipt)) return new Result(profile, List.of(), 0L, true);
        List<Outcome> outcomes = teaches(registry, rules, profile, plan);
        ResearchProfile next = profile;
        long experience = 0L;
        for (Outcome outcome : outcomes) {
            switch (outcome.kind()) {
                case DISCOVERED -> {
                    next = next.discover(outcome.definition().id());
                    experience += rules.discoveryExperience().getOrDefault(outcome.definition().tier(), 0L);
                }
                case PROGRESS, PARTIAL -> {
                    ResearchProfile.Entry entry = next.entry(outcome.definition().id());
                    next = next.withEntry(outcome.definition().id(),
                        new ResearchProfile.Entry(outcome.to(), false, entry.binds(), entry.clue()));
                }
                case CLUE -> {
                    ResearchProfile.Entry entry = next.entry(outcome.definition().id());
                    next = next.withEntry(outcome.definition().id(),
                        new ResearchProfile.Entry(Math.max(entry.progress(), outcome.to()), false, entry.binds(), true));
                    experience += rules.clueExperience();
                }
                case ASPECT_STUDIED -> next = next.study(List.of(outcome.aspect()));
                case NO_RESONANCE -> { }
            }
        }
        if (outcomes.isEmpty()) outcomes = List.of(new Outcome(OutcomeKind.NO_RESONANCE, null, null, 0, 0));
        next = next.attempt(plan.combination()).receipt(receipt);
        if (historyLine != null && !historyLine.isBlank()) next = next.log(historyLine);
        return new Result(next, outcomes, experience, false);
    }

    /** What this plan would teach right now. Empty means it would teach nothing. */
    static List<Outcome> teaches(ArcaneRegistry registry, ArcaneSettings.Research rules, ResearchProfile profile, Plan plan) {
        List<Outcome> outcomes = new ArrayList<>();
        Optional<ArcaneDefinition> exact = registry.exact(plan.aspects());
        if (exact.isPresent() && !profile.known(exact.get().id())) {
            ArcaneDefinition definition = exact.get();
            int from = profile.entry(definition.id()).progress();
            int gain = Math.max(1, (int) Math.round(rules.progress().get(definition.tier()) * plan.multiplier()));
            int to = Math.min(100, from + gain);
            outcomes.add(new Outcome(to >= 100 ? OutcomeKind.DISCOVERED : OutcomeKind.PROGRESS, definition, null, from, to));
        }
        int repeats = profile.attempts(plan.combination());
        for (ArcaneDefinition definition : registry.supersets(plan.aspects())) {
            ResearchProfile.Entry entry = profile.entry(definition.id());
            if (entry.discovered()) continue;
            int gain = (int) Math.floor(rules.partialGain() * plan.multiplier() / (1 + repeats));
            int to = Math.min(rules.partialCap(), entry.progress() + gain);
            if (to > entry.progress()) {
                boolean clue = !entry.clue() && to >= rules.clueThreshold();
                outcomes.add(new Outcome(clue ? OutcomeKind.CLUE : OutcomeKind.PARTIAL, definition, null, entry.progress(), to));
            }
        }
        for (ArcaneAspect aspect : plan.aspects()) {
            if (!profile.studied(aspect)) outcomes.add(new Outcome(OutcomeKind.ASPECT_STUDIED, null, aspect, 0, 0));
        }
        return outcomes;
    }

    private static boolean masteredSharing(ArcaneRegistry registry, ArcaneSettings.Research rules,
                                           ResearchProfile profile, Set<ArcaneAspect> aspects) {
        return registry.all().stream()
            .filter(definition -> !definition.isSignature())
            .filter(definition -> definition.aspects().stream().anyMatch(aspects::contains))
            .anyMatch(definition -> profile.state(definition.id(), rules.masteryBinds()) == ResearchProfile.State.MASTERED);
    }

    /** The Aspects of a partly discovered definition the player has been told about. */
    public static Set<ArcaneAspect> revealedAspects(ArcaneDefinition definition, ResearchProfile profile) {
        ResearchProfile.Entry entry = profile.entry(definition.id());
        if (entry.discovered()) return definition.aspects();
        Set<ArcaneAspect> revealed = EnumSet.noneOf(ArcaneAspect.class);
        if (entry.progress() <= 0 && !entry.clue()) return revealed;
        // Studied Aspects that belong to it are shown; a clue reveals all of its Aspects.
        for (ArcaneAspect aspect : definition.aspects()) if (entry.clue() || profile.studied(aspect)) revealed.add(aspect);
        return revealed;
    }
}
