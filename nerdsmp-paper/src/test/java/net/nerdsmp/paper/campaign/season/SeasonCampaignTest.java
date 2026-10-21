package net.nerdsmp.paper.campaign.season;

import net.nerdsmp.paper.campaign.season.SeasonDefinition.BoostKind;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Reward;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.SourceType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class SeasonCampaignTest {
    private static final Instant T0 = Instant.parse("2026-10-20T00:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-10-20");
    private static final SeasonValidator.Catalog CATALOG = new SeasonValidator.Catalog() {
        @Override public boolean material(String key) { return !key.contains("unknown"); }
        @Override public boolean mob(String id) { return id.contains(":") && !id.contains("unknown"); }
        @Override public boolean item(String id) { return !id.contains("unknown"); }
        @Override public boolean effect(String key) { return !key.contains("unknown"); }
        @Override public boolean expedition(String id) { return List.of("hollow_barrow", "cinder_keep").contains(id); }
    };

    private static YamlConfiguration shipped() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(SeasonCampaignTest.class.getResourceAsStream("/campaign/season.yml")), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static SeasonDefinition season() { return SeasonLoader.parse(shipped(), T0); }

    @Test void shippedSeasonLoadsCleanly() {
        SeasonDefinition season = season();
        assertEquals("ember-accord", season.id());
        assertEquals(T0, season.startsAt(), "No starts-at means the first load starts it");
        assertEquals(T0.plus(Duration.ofDays(42)), season.endsAt());
        assertEquals(List.of("forge-fires", "delvers", "wardens", "harvest"), List.copyOf(season.goals().keySet()));
        assertEquals(List.of(), SeasonValidator.validate(season, CATALOG).stream().filter(SeasonValidator.Issue::error).toList());
        var delve = season.goals().get("delvers").sources().get(0);
        assertEquals(SourceType.EXPEDITION, delve.type());
        assertEquals(4.0, delve.tierMultipliers().get("mythic"));
        assertEquals(40L, season.goals().get("delvers").dailyCap());
        var mine = season.goals().get("forge-fires").sources().get(0);
        assertTrue(mine.accepts("iron_ore") && !mine.accepts("stone"));
        assertTrue(season.goals().get("forge-fires").sources().get(1).accepts("anything"), "No filter accepts everything");
        assertEquals("ember-gate", season.unlocking("cinder_keep").orElseThrow().id());
        assertTrue(season.unlocking("hollow_barrow").isEmpty());
        var gate = season.milestones().get("ember-gate");
        assertEquals(Map.of("forge-fires", 20_000L, "delvers", 40L), gate.requires());
        assertTrue(gate.claimable());
        assertTrue(season.milestones().get("first-spark").rewards().contains(new Reward.Buff("haste", 0, Duration.ofHours(6))));
        assertTrue(season.milestones().get("hunters-moon").rewards().contains(new Reward.Boost(BoostKind.SKILL_XP, 25, Duration.ofHours(12))));
    }

    @Test void loaderAndValidatorReportMistakes() {
        YamlConfiguration yaml = shipped();
        yaml.set("goals.wardens.sources", List.of(Map.of("type", "dance")));
        var failure = assertThrows(IllegalArgumentException.class, () -> SeasonLoader.parse(yaml, T0));
        assertTrue(failure.getMessage().contains("goals.wardens.sources[1].type"), failure.getMessage());
        YamlConfiguration noEnd = shipped();
        noEnd.set("duration-days", null);
        assertThrows(IllegalArgumentException.class, () -> SeasonLoader.parse(noEnd, T0));
        YamlConfiguration rewards = shipped();
        rewards.set("milestones.first-spark.rewards", List.of(Map.of("type", "buff", "effect", "haste")));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> SeasonLoader.parse(rewards, T0)).getMessage().contains("minutes, hours or days"));

        YamlConfiguration names = shipped();
        names.set("milestones.ember-gate.requires.nonsense", 5);
        names.set("milestones.first-spark.rewards", List.of(Map.of("type", "buff", "effect", "unknown_glow", "hours", 1),
            Map.of("type", "unlock", "expedition", "moon_base"), Map.of("type", "unlock", "feature", "flying")));
        names.set("goals.wardens.sources", List.of(Map.of("type", "kill", "mobs", List.of("custom:unknown_beast"))));
        List<String> errors = SeasonValidator.validate(SeasonLoader.parse(names, T0), CATALOG).stream()
            .filter(SeasonValidator.Issue::error).map(SeasonValidator.Issue::message).toList();
        for (String expected : List.of("unknown goal nonsense", "unknown effect unknown_glow", "unknown expedition moon_base",
            "unknown feature flying", "unknown 'custom:unknown_beast'"))
            assertTrue(errors.stream().anyMatch(message -> message.contains(expected)), expected + " in " + errors);
    }

    @Test void dailyCapsClampContributionsAndResetEachDay() {
        SeasonLedger ledger = new SeasonLedger();
        assertEquals(60, ledger.add("a", "forge-fires", 60, DAY, 100));
        assertEquals(40, ledger.add("a", "forge-fires", 60, DAY, 100), "Clamped to what's left today");
        assertEquals(0, ledger.add("a", "forge-fires", 5, DAY, 100));
        assertEquals(30, ledger.add("b", "forge-fires", 30, DAY, 100), "Caps are per player");
        assertEquals(10, ledger.add("a", "forge-fires", 10, DAY.plusDays(1), 100), "A new day starts fresh");
        assertEquals(140, ledger.points("forge-fires"));
        assertEquals(110, ledger.contributed("a", "forge-fires"));
        assertEquals(List.of(Map.entry("a", 110L), Map.entry("b", 30L)), ledger.top("forge-fires", 5));
        ledger.adjust("forge-fires", -1_000);
        assertEquals(0, ledger.points("forge-fires"), "Staff adjustments never go negative");
    }

    @Test void milestonesFireOnceAndStartTheirBuffs() {
        SeasonDefinition season = season();
        SeasonLedger ledger = new SeasonLedger();
        ledger.add("a", "forge-fires", 7_999, DAY, Long.MAX_VALUE);
        assertTrue(ledger.evaluate(season, T0).isEmpty());
        ledger.add("a", "forge-fires", 1, DAY, Long.MAX_VALUE);
        assertEquals(List.of("first-spark"), ledger.evaluate(season, T0).stream().map(SeasonDefinition.Milestone::id).toList());
        assertTrue(ledger.evaluate(season, T0.plusSeconds(5)).isEmpty(), "Never twice");
        var haste = ledger.activeBuffs(T0).get(0);
        assertEquals("haste", haste.effect());
        assertEquals(T0.plus(Duration.ofHours(6)), haste.endsAt());
        ledger.add("a", "forge-fires", 20_000, DAY, Long.MAX_VALUE);
        assertTrue(ledger.evaluate(season, T0).stream().noneMatch(milestone -> milestone.id().equals("ember-gate")), "Ember Gate needs delvers too");
        ledger.add("a", "delvers", 40, DAY, Long.MAX_VALUE);
        assertEquals(List.of("ember-gate"), ledger.evaluate(season, T0).stream().map(SeasonDefinition.Milestone::id).toList());
        assertTrue(ledger.activeBuffs(T0.plus(Duration.ofHours(7))).isEmpty(), "Buffs expire");
    }

    @Test void repeatedBuffsExtendInsteadOfStacking() {
        SeasonLedger ledger = new SeasonLedger();
        ledger.startTimed(new Reward.Buff("haste", 0, Duration.ofHours(2)), "One", T0);
        ledger.startTimed(new Reward.Buff("haste", 1, Duration.ofHours(1)), "Two", T0.plus(Duration.ofHours(1)));
        var haste = ledger.activeBuffs(T0).get(0);
        assertEquals(T0.plus(Duration.ofHours(3)), haste.endsAt());
        assertEquals(1, haste.amplifier());
        ledger.startTimed(new Reward.Boost(BoostKind.JOB_MARKS, 20, Duration.ofHours(1)), "Feast", T0);
        assertEquals(20, ledger.boostPercent(BoostKind.JOB_MARKS, T0.plusSeconds(60)));
        assertEquals(0, ledger.boostPercent(BoostKind.SKILL_XP, T0));
        assertEquals(0, ledger.boostPercent(BoostKind.JOB_MARKS, T0.plus(Duration.ofHours(2))));
    }

    @Test void itemAndMarkRewardsAreClaimedOncePerAccount() {
        SeasonDefinition season = season();
        SeasonLedger ledger = new SeasonLedger();
        ledger.add("a", "forge-fires", 20_000, DAY, Long.MAX_VALUE);
        ledger.add("a", "delvers", 40, DAY, Long.MAX_VALUE);
        ledger.evaluate(season, T0);
        assertEquals(List.of("ember-gate"), ledger.claimable(season, "b").stream().map(SeasonDefinition.Milestone::id).toList(),
            "Everyone can claim, not only contributors; buff-only milestones aren't claimable");
        assertTrue(ledger.claim("b", "ember-gate"));
        assertFalse(ledger.claim("b", "ember-gate"));
        assertTrue(ledger.claimable(season, "b").isEmpty());
        assertEquals(1, ledger.claimable(season, "a").size());
    }

    @Test void ledgerSurvivesARestart() {
        SeasonDefinition season = season();
        SeasonLedger ledger = new SeasonLedger();
        ledger.firstLoaded(T0);
        ledger.add("acct", "forge-fires", 9_000, DAY, Long.MAX_VALUE);
        ledger.name("acct", "Ash");
        ledger.evaluate(season, T0);
        ledger.startTimed(new Reward.Boost(BoostKind.SKILL_XP, 25, Duration.ofHours(1)), "Moon", T0);
        ledger.claim("acct", "first-spark");
        YamlConfiguration saved = new YamlConfiguration();
        assertDoesNotThrow(() -> saved.loadFromString(ledger.save().saveToString()));
        SeasonLedger loaded = SeasonLedger.load(saved);
        assertEquals(9_000, loaded.points("forge-fires"));
        assertEquals(9_000, loaded.contributed("acct", "forge-fires"));
        assertEquals(9_000, loaded.today("acct", "forge-fires", DAY));
        assertTrue(loaded.reached("first-spark"));
        assertTrue(loaded.claimed("acct", "first-spark"));
        assertEquals(25, loaded.boostPercent(BoostKind.SKILL_XP, T0.plusSeconds(10)));
        assertEquals("haste", loaded.activeBuffs(T0).stream().filter(SeasonLedger.ActiveBuff::potion).findFirst().orElseThrow().effect());
        assertEquals(T0, loaded.firstLoaded(T0.plusSeconds(999)));
        assertEquals("Ash", loaded.name("acct").orElseThrow());
    }
}
