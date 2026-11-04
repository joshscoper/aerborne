package net.nerdsmp.paper.vote;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Voting on more sites in a day, and the server's daily goal. */
class VoteBonusTest {
    private static final VoteLedger.Rules RULES = new VoteLedger.Rules(100, 10, 7, 0, Duration.ofHours(6), 8, 25, 250);
    private static final Set<String> MENU = Set.of("planet", "list", "topg");

    private static long at(int day, int hour) { return LocalDateTime.of(2026, 11, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli(); }

    private static VoteLedger.Result vote(YamlConfiguration data, String site, long at) {
        return VoteLedger.record(data, site, at, ZoneOffset.UTC, RULES, MENU);
    }

    @Test
    void eachMoreSiteInADayPaysMore() {
        YamlConfiguration data = new YamlConfiguration();
        assertEquals(100, vote(data, "planet", at(3, 9)).marks());
        VoteLedger.Result second = vote(data, "list", at(3, 10));
        assertEquals(125, second.marks());
        assertEquals(2, second.site());
    }

    @Test
    void theLastSiteOfTheMenuAddsTheAllSitesBonusOnce() {
        YamlConfiguration data = new YamlConfiguration();
        vote(data, "planet", at(3, 9));
        vote(data, "list", at(3, 10));
        VoteLedger.Result third = vote(data, "topg", at(3, 11));
        assertTrue(third.allSites());
        assertEquals(100 + 50 + 250, third.marks());
        // Another vote on the first site later that day keeps its first place and pays no second bonus.
        VoteLedger.Result again = vote(data, "planet", at(3, 18));
        assertFalse(again.allSites());
        assertEquals(100, again.marks());
    }

    @Test
    void theClimbStartsAgainTheNextDay() {
        YamlConfiguration data = new YamlConfiguration();
        vote(data, "planet", at(3, 9));
        vote(data, "list", at(3, 10));
        VoteLedger.Result tomorrow = vote(data, "list", at(4, 10));
        assertEquals(1, tomorrow.site());
        assertEquals(110, tomorrow.marks()); // First site again, with a 2-day streak.
        assertEquals(java.util.List.of("list"), VoteLedger.sitesToday(data, at(4, 12), ZoneOffset.UTC));
    }

    @Test
    void sitesOutsideTheMenuClimbButNeverCompleteIt() {
        YamlConfiguration data = new YamlConfiguration();
        vote(data, "planet", at(3, 9));
        vote(data, "list", at(3, 9));
        VoteLedger.Result other = vote(data, "somewhere-else", at(3, 10));
        assertEquals(150, other.marks());
        assertFalse(other.allSites());
    }

    @Test
    void theGoalCallsOutQuartersThenPaysOnce() {
        VoteGoal goal = new VoteGoal();
        LocalDate day = LocalDate.of(2026, 11, 3);
        assertEquals(0, goal.add("a", day, 8).quarter());
        assertEquals(25, goal.add("b", day, 8).quarter());
        goal.add("a", day, 8);
        assertEquals(50, goal.add("c", day, 8).quarter());
        goal.add("d", day, 8);
        assertEquals(75, goal.add("e", day, 8).quarter());
        assertFalse(goal.add("f", day, 8).reached());
        VoteGoal.Step reached = goal.add("g", day, 8);
        assertTrue(reached.reachedNow());
        assertEquals(java.util.List.of("a", "b", "c", "d", "e", "f", "g"), goal.voters(day));
        VoteGoal.Step after = goal.add("h", day, 8);
        assertFalse(after.reachedNow());
        assertTrue(after.reached());
    }

    @Test
    void theGoalStartsAgainAtMidnightAndSurvivesARestart() {
        VoteGoal goal = new VoteGoal();
        LocalDate day = LocalDate.of(2026, 11, 3);
        goal.add("a", day, 2);
        goal.add("b", day, 2);
        YamlConfiguration saved = new YamlConfiguration();
        goal.save(saved.createSection("community"));
        VoteGoal loaded = new VoteGoal();
        loaded.load(saved.getConfigurationSection("community"));
        assertTrue(loaded.reached(day));
        assertEquals(2, loaded.votes(day));
        assertFalse(loaded.reached(day.plusDays(1)));
        assertEquals(0, loaded.votes(day.plusDays(1)));
    }

    @Test
    void aGoalOfZeroIsOff() {
        VoteGoal goal = new VoteGoal();
        VoteGoal.Step step = goal.add("a", LocalDate.of(2026, 11, 3), 0);
        assertFalse(step.reachedNow());
        assertEquals(0, step.quarter());
    }

    @Test
    void theShippedFileSetsTheGoal() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var in = VoteBonusTest.class.getResourceAsStream("/votes.yml")) {
            yaml.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        assertEquals(new VoteService.Goal(25, true, 150), VoteService.Settings.read(yaml).goal());
    }
}
