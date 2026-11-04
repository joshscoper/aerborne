package net.nerdsmp.paper.vote;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteLedgerTest {
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final VoteLedger.Rules RULES = new VoteLedger.Rules(100, 10, 7, 20, Duration.ofHours(6), 3, 0, 0);

    private static long at(int day, int hour) { return LocalDateTime.of(2026, 11, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli(); }

    @Test
    void aFirstVotePaysTheBaseReward() {
        VoteLedger.Result result = VoteLedger.record(new YamlConfiguration(), "PlanetMinecraft.com", at(3, 12), UTC, RULES, java.util.Set.of());
        assertEquals(VoteLedger.Status.PAID, result.status());
        assertEquals(100, result.marks());
        assertEquals(1, result.streak());
        assertEquals(1, result.total());
    }

    @Test
    void theSameSiteAgainWithinTheRepeatWindowPaysNothing() {
        YamlConfiguration data = new YamlConfiguration();
        VoteLedger.record(data, "PlanetMinecraft.com", at(3, 12), UTC, RULES, java.util.Set.of());
        assertEquals(VoteLedger.Status.REPEAT, VoteLedger.record(data, "PlanetMinecraft.com", at(3, 17), UTC, RULES, java.util.Set.of()).status());
        assertEquals(VoteLedger.Status.PAID, VoteLedger.record(data, "Another Site", at(3, 17), UTC, RULES, java.util.Set.of()).status());
        assertEquals(VoteLedger.Status.PAID, VoteLedger.record(data, "PlanetMinecraft.com", at(3, 19), UTC, RULES, java.util.Set.of()).status());
    }

    @Test
    void daysInARowAddTheStreakBonusUpToTheCap() {
        YamlConfiguration data = new YamlConfiguration();
        long marks = 0;
        for (int day = 1; day <= 9; day++) marks = VoteLedger.record(data, "Site", at(day, 12), UTC, RULES, java.util.Set.of()).marks();
        assertEquals(9, data.getInt("votes.streak"));
        assertEquals(160, marks); // 100 + 10 for each of 6 days after the first, capped at 7.
    }

    @Test
    void missingADayStartsTheStreakAgain() {
        YamlConfiguration data = new YamlConfiguration();
        VoteLedger.record(data, "Site", at(1, 12), UTC, RULES, java.util.Set.of());
        VoteLedger.record(data, "Site", at(2, 12), UTC, RULES, java.util.Set.of());
        assertEquals(2, VoteLedger.streak(data, at(3, 9), UTC));
        assertEquals(0, VoteLedger.streak(data, at(4, 9), UTC));
        assertEquals(1, VoteLedger.record(data, "Site", at(4, 12), UTC, RULES, java.util.Set.of()).streak());
    }

    @Test
    void nothingPaysPastTheDailyLimit() {
        YamlConfiguration data = new YamlConfiguration();
        for (String site : new String[]{"A", "B", "C"}) assertEquals(VoteLedger.Status.PAID, VoteLedger.record(data, site, at(3, 12), UTC, RULES, java.util.Set.of()).status());
        assertEquals(VoteLedger.Status.DAILY_LIMIT, VoteLedger.record(data, "D", at(3, 13), UTC, RULES, java.util.Set.of()).status());
        assertEquals(3, data.getInt("votes.total"));
        assertEquals(VoteLedger.Status.PAID, VoteLedger.record(data, "D", at(4, 1), UTC, RULES, java.util.Set.of()).status());
    }

    @Test
    void everyTwentiethVoteEarnsABooster() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("votes.total", 18);
        assertFalse(VoteLedger.record(data, "Site", at(3, 12), UTC, RULES, java.util.Set.of()).booster());
        assertTrue(VoteLedger.record(data, "Site", at(4, 12), UTC, RULES, java.util.Set.of()).booster());
    }

    @Test
    void aSiteCanBeVotedOnAgainADayLater() {
        YamlConfiguration data = new YamlConfiguration();
        VoteLedger.record(data, "Planet Minecraft", at(3, 12), UTC, RULES, java.util.Set.of());
        assertEquals(at(4, 12), VoteLedger.nextVote(data, "planet-minecraft", at(3, 20)));
        assertEquals(0, VoteLedger.nextVote(data, "Planet Minecraft", at(4, 12)));
    }

    @Test
    void siteNamesBecomeSafeDataKeys() {
        assertEquals("planetminecraft-com", VoteLedger.key("PlanetMinecraft.com"));
        assertEquals("site", VoteLedger.key("..."));
    }

    @Test
    void theShippedVotesFileReads() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var in = VoteLedgerTest.class.getResourceAsStream("/votes.yml")) {
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        VoteService.Settings settings = VoteService.Settings.read(yaml);
        assertTrue(settings.enabled());
        assertEquals(9027, settings.port());
        assertEquals(new VoteLedger.Rules(100, 10, 7, 20, Duration.ofHours(6), 8, 25, 250), settings.rules());
        assertTrue(settings.sites().isEmpty());
    }
}
