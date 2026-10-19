package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.paper.forge.ArcaneEnchantment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static net.nerdsmp.paper.forge.arcane.ArcaneAspect.AFFINITY;
import static net.nerdsmp.paper.forge.arcane.ArcaneAspect.CELERITY;
import static net.nerdsmp.paper.forge.arcane.ArcaneAspect.MIGHT;
import static net.nerdsmp.paper.forge.arcane.ArcaneAspect.PRECISION;
import static net.nerdsmp.paper.forge.arcane.ArcaneAspect.WARD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcaneResearchTest {
    private final ArcaneRegistry registry = ArcaneRegistry.defaults();
    private final ArcaneSettings.Research rules = ArcaneSettings.defaults().research();

    private static List<ArcaneResearch.Reagent> gems(int tier, ArcaneAspect... aspects) {
        return java.util.Arrays.stream(aspects).map(aspect -> new ArcaneResearch.Reagent(aspect, tier)).toList();
    }

    private ArcaneResearch.Result run(ResearchProfile profile, int level, ArcaneAspect... aspects) {
        var plan = ArcaneResearch.plan(registry, rules, profile, gems(1, aspects), false, level);
        assertEquals(ArcaneResearch.Status.READY, plan.status(), plan.toString());
        return ArcaneResearch.apply(registry, rules, profile, plan, java.util.UUID.randomUUID().toString(), "test");
    }

    @Test
    void aSingleAspectExperimentDiscoversItsFundamentalInOneStep() {
        var result = run(ResearchProfile.empty(), 5, MIGHT);
        assertTrue(result.discovered());
        assertTrue(result.profile().known("keen_edge"));
        assertTrue(result.profile().studied(MIGHT));
        assertEquals(rules.discoveryExperience().get(ArcaneTier.FUNDAMENTAL), result.experience());
        // Every specialized and signature recipe with Might made partial progress, and none is named or known.
        assertTrue(result.outcomes().stream().filter(outcome -> outcome.kind() == ArcaneResearch.OutcomeKind.PARTIAL)
            .allMatch(outcome -> !result.profile().known(outcome.definition().id())));
    }

    @Test
    void specializedRecipesTakeTwoExperimentsAndThenAreRefusedAsKnown() {
        var first = run(ResearchProfile.empty(), 10, MIGHT, PRECISION);
        assertEquals(50, first.profile().entry("executioner").progress());
        assertEquals(ResearchProfile.State.PARTIALLY_DISCOVERED, first.profile().state("executioner", rules.masteryBinds()));
        var second = run(first.profile(), 10, MIGHT, PRECISION);
        assertTrue(second.profile().known("executioner"));
        assertEquals(ArcaneResearch.Status.ALREADY_KNOWN,
            ArcaneResearch.plan(registry, rules, second.profile(), gems(1, MIGHT, PRECISION), false, 10).status());
    }

    @Test
    void higherTierGemsAndPrismaticCatalystsAdvanceResearchFaster() {
        var plan = ArcaneResearch.plan(registry, rules, ResearchProfile.empty(), gems(4, MIGHT, PRECISION), true, 10);
        var result = ArcaneResearch.apply(registry, rules, ResearchProfile.empty(), plan, "r", "test");
        assertTrue(result.profile().known("executioner"), "2.0 x 1.5 x 50 progress completes it at once");
    }

    @Test
    void repeatedFruitlessExperimentsAreRefusedBeforeAnythingIsSpent() {
        // Ward + Precision belongs to no definition and no larger recipe contains both.
        var first = run(ResearchProfile.empty(), 10, WARD, PRECISION);
        assertTrue(first.outcomes().stream().allMatch(outcome -> outcome.kind() == ArcaneResearch.OutcomeKind.ASPECT_STUDIED));
        assertEquals(0L, first.experience(), "studying Aspects grants no XP, so cheap combinations cannot be farmed");
        var again = ArcaneResearch.plan(registry, rules, first.profile(), gems(1, WARD, PRECISION), false, 10);
        assertEquals(ArcaneResearch.Status.FRUITLESS, again.status());
    }

    @Test
    void partialProgressDiminishesWithRepeatsAndCanNeverCompleteALargerRecipe() {
        ResearchProfile profile = ResearchProfile.empty();
        int previous = -1;
        int gained = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 40; attempt++) {
            var plan = ArcaneResearch.plan(registry, rules, profile, gems(4, MIGHT, CELERITY), true, 10);
            if (plan.status() != ArcaneResearch.Status.READY) break;
            profile = ArcaneResearch.apply(registry, rules, profile, plan, "r" + attempt, "test").profile();
            int now = profile.entry("stormwake").progress();
            if (previous >= 0) {
                assertTrue(now - previous <= gained, "gains never grow on repeats");
                gained = now - previous;
            }
            previous = now;
        }
        assertTrue(profile.entry("stormwake").progress() <= rules.partialCap());
        assertFalse(profile.known("stormwake"));
        assertTrue(profile.entry("stormwake").clue(), "enough partial work reveals a clue");
        assertEquals(ArcaneResearch.Status.FRUITLESS,
            ArcaneResearch.plan(registry, rules, profile, gems(4, MIGHT, CELERITY), true, 10).status());
    }

    @Test
    void signatureResearchNeedsEnchantingAndARelatedMastery() {
        ResearchProfile profile = ResearchProfile.empty();
        assertEquals(ArcaneResearch.Status.NEEDS_LEVEL,
            ArcaneResearch.plan(registry, rules, profile, gems(1, MIGHT, CELERITY, AFFINITY), false, 29).status());
        assertEquals(ArcaneResearch.Status.NEEDS_MASTERY,
            ArcaneResearch.plan(registry, rules, profile, gems(1, MIGHT, CELERITY, AFFINITY), false, 30).status());
        for (int bind = 0; bind < rules.masteryBinds(); bind++) profile = profile.recordBind("keen_edge", 5, 5);
        assertEquals(ResearchProfile.State.MASTERED, profile.state("keen_edge", rules.masteryBinds()));
        assertEquals(ArcaneResearch.Status.READY,
            ArcaneResearch.plan(registry, rules, profile, gems(1, MIGHT, CELERITY, AFFINITY), false, 30).status());
        ResearchProfile current = profile;
        for (int run = 0; run < 4; run++) current = run(current, 30, MIGHT, CELERITY, AFFINITY).profile();
        assertTrue(current.known("stormwake"));
    }

    @Test
    void lockedAndInvalidExperimentsCannotRun() {
        assertEquals(ArcaneResearch.Status.LOCKED,
            ArcaneResearch.plan(registry, rules, ResearchProfile.empty(), gems(1, MIGHT), false, 4).status());
        assertEquals(ArcaneResearch.Status.INVALID,
            ArcaneResearch.plan(registry, rules, ResearchProfile.empty(), List.of(), false, 50).status());
        assertEquals(ArcaneResearch.Status.INVALID, ArcaneResearch.plan(registry, rules, ResearchProfile.empty(),
            List.of(new ArcaneResearch.Reagent(MIGHT, 1), new ArcaneResearch.Reagent(MIGHT, 2)), false, 50).status());
        var invalid = ArcaneResearch.plan(registry, rules, ResearchProfile.empty(), List.of(), false, 50);
        assertThrows(IllegalArgumentException.class,
            () -> ArcaneResearch.apply(registry, rules, ResearchProfile.empty(), invalid, "r", "x"));
    }

    @Test
    void theSameReceiptNeverAwardsTwice() {
        var plan = ArcaneResearch.plan(registry, rules, ResearchProfile.empty(), gems(1, MIGHT), false, 5);
        var first = ArcaneResearch.apply(registry, rules, ResearchProfile.empty(), plan, "receipt-1", "x");
        var replay = ArcaneResearch.apply(registry, rules, first.profile(), plan, "receipt-1", "x");
        assertTrue(replay.duplicate());
        assertSame(first.profile(), replay.profile());
        assertEquals(0L, replay.experience());
    }

    @Test
    void seedingFromOwnedGearRunsOnceAndOnlyAddsKnowledge() {
        ResearchProfile seeded = ResearchProfile.empty().seed(Set.of("warding", "scholar"));
        assertTrue(seeded.seeded() && seeded.known("warding") && seeded.known("scholar"));
        assertSame(seeded, seeded.seed(Set.of("keen_edge")), "a second seed is ignored");
        ResearchProfile bound = seeded.recordBind("keen_edge", 2, 5);
        assertTrue(bound.known("keen_edge"), "a table or tome enchant teaches the enchantment");
        assertEquals(0, bound.entry("keen_edge").binds(), "only top-level binds count toward mastery");
    }

    @Test
    void profilesRoundTripThroughYamlAndKeepUnknownIds() throws Exception {
        ResearchProfile profile = run(ResearchProfile.empty().seed(Set.of("warding")), 10, MIGHT, PRECISION).profile()
            .recordBind(ArcaneEnchantment.WARDING.id(), 5, 5)
            .withEntry("from_a_newer_build", new ResearchProfile.Entry(40, false, 0, true));
        YamlConfiguration yaml = new YamlConfiguration();
        profile.write(yaml.createSection("arcane"));
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(yaml.saveToString());
        ResearchProfile read = ResearchProfile.read(reloaded.getConfigurationSection("arcane"));
        assertEquals(profile, read);
        assertEquals(40, read.entry("from_a_newer_build").progress());
        assertEquals(ResearchProfile.empty(), ResearchProfile.read(null));
    }

    @Test
    void historyAndReceiptsStayBounded() {
        ResearchProfile profile = ResearchProfile.empty();
        for (int index = 0; index < 500; index++) profile = profile.log("line " + index).receipt("r" + index);
        assertEquals(ResearchProfile.HISTORY_LIMIT, profile.history().size());
        assertEquals("line 499", profile.history().getLast());
        assertTrue(profile.hasReceipt("r499"));
        assertFalse(profile.hasReceipt("r0"));
    }
}
