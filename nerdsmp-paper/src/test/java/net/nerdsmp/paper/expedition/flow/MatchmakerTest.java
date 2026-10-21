package net.nerdsmp.paper.expedition.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchmakerTest {
    private static final Instant T0 = Instant.parse("2026-10-20T12:00:00Z");
    private static final Map<String, Matchmaker.Rules> RULES = Map.of("barrow", new Matchmaker.Rules(1, 5), "keep", new Matchmaker.Rules(2, 5));

    private static Matchmaker.Ticket ticket(String expedition, int size, int secondsIn) {
        List<UUID> members = new ArrayList<>();
        for (int index = 0; index < size; index++) members.add(UUID.randomUUID());
        return new Matchmaker.Ticket(UUID.randomUUID(), expedition, "normal", members, T0.plusSeconds(secondsIn));
    }

    @Test void fullGroupsStartAtOnceAndPackOldestFirst() {
        Matchmaker finder = new Matchmaker(Duration.ofSeconds(90));
        var first = ticket("barrow", 3, 0);
        var tooBig = ticket("barrow", 3, 1);
        var pair = ticket("barrow", 2, 2);
        for (var ticket : List.of(first, tooBig, pair)) assertEquals(Matchmaker.QueueResult.QUEUED, finder.enqueue(ticket, RULES.get("barrow")));
        List<Matchmaker.Group> groups = finder.match(RULES, 4, T0.plusSeconds(5));
        assertEquals(1, groups.size());
        assertEquals(List.of(first, pair), groups.get(0).tickets(), "The pair fills the gap the trio left");
        assertEquals(5, groups.get(0).members().size());
        assertEquals(List.of(tooBig), finder.tickets());
    }

    @Test void partialGroupsWaitForTheFillTimeAndTheMinimum() {
        Matchmaker finder = new Matchmaker(Duration.ofSeconds(90));
        var solo = ticket("keep", 1, 0);
        finder.enqueue(solo, RULES.get("keep"));
        assertTrue(finder.match(RULES, 4, T0.plusSeconds(200)).isEmpty(), "Cinder Keep needs two");
        var second = ticket("keep", 1, 150);
        finder.enqueue(second, RULES.get("keep"));
        assertTrue(finder.match(RULES, 4, T0.plusSeconds(80)).isEmpty(), "Still inside the fill wait");
        var groups = finder.match(RULES, 4, T0.plusSeconds(160));
        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).members().size());
    }

    @Test void startsStopWhenInstancesAreFull() {
        Matchmaker finder = new Matchmaker(Duration.ZERO);
        for (int index = 0; index < 3; index++) finder.enqueue(ticket("barrow", 5, index), RULES.get("barrow"));
        assertEquals(2, finder.match(RULES, 2, T0.plusSeconds(10)).size());
        assertEquals(1, finder.tickets().size());
        assertEquals(0, finder.match(RULES, 0, T0.plusSeconds(10)).size());
    }

    @Test void queueRulesAndReturningToTheQueue() {
        Matchmaker finder = new Matchmaker(Duration.ZERO);
        var party = ticket("barrow", 2, 0);
        assertEquals(Matchmaker.QueueResult.TOO_LARGE, finder.enqueue(ticket("barrow", 6, 0), RULES.get("barrow")));
        assertEquals(Matchmaker.QueueResult.UNKNOWN, finder.enqueue(party, null));
        finder.enqueue(party, RULES.get("barrow"));
        var overlap = new Matchmaker.Ticket(UUID.randomUUID(), "keep", "normal", List.of(party.members().get(0)), T0);
        assertEquals(Matchmaker.QueueResult.ALREADY_QUEUED, finder.enqueue(overlap, RULES.get("keep")));
        var later = ticket("barrow", 1, 50);
        finder.enqueue(later, RULES.get("barrow"));
        assertEquals(party, finder.leave(party.members().get(1)).orElseThrow(), "One member leaving removes the whole ticket");
        finder.requeue(List.of(party));
        assertEquals(List.of(party, later), finder.tickets(), "A requeued ticket keeps its place");
        assertEquals(3, finder.waiting("barrow", "normal"));
        finder.match(Map.of(), 4, T0);
        assertTrue(finder.tickets().isEmpty(), "Tickets for expeditions that closed are dropped");
    }

    @Test void readyChecksResolveAndReturnTheInnocent() {
        var good = ticket("barrow", 2, 0);
        var bad = ticket("barrow", 1, 1);
        List<UUID> members = new ArrayList<>(good.members());
        members.addAll(bad.members());
        ReadyCheck check = new ReadyCheck("barrow", "normal", members, List.of(good, bad), T0, Duration.ofSeconds(30));
        check.accept(good.members().get(0));
        assertEquals(ReadyCheck.State.PENDING, check.state());
        check.decline(bad.members().get(0));
        assertEquals(ReadyCheck.State.CANCELLED, check.state());
        assertEquals(List.of(good), check.innocentTickets());

        ReadyCheck timeout = new ReadyCheck("barrow", "normal", members, List.of(good, bad), T0, Duration.ofSeconds(30));
        good.members().forEach(timeout::accept);
        assertEquals(ReadyCheck.State.CANCELLED, timeout.tick(T0.plusSeconds(30)));
        assertEquals(List.of(good), timeout.innocentTickets(), "Players who never answered lose their place");

        ReadyCheck ready = new ReadyCheck("barrow", "normal", members, List.of(), T0, Duration.ofSeconds(30));
        members.forEach(ready::accept);
        assertEquals(ReadyCheck.State.READY, ready.state());
        assertEquals(ReadyCheck.State.READY, ready.decline(members.get(0)), "A finished check can't be undone");
    }

    @Test void recordsTrackClearsTiersAndBestTimes(@TempDir Path folder) {
        ExpeditionRecords records = new ExpeditionRecords(folder.resolve("records.yml").toFile());
        assertFalse(records.cleared("a", "barrow", "normal"));
        assertTrue(records.record("barrow", "normal", 600, Map.of("a", "Ash", "b", "Bryn")));
        assertFalse(records.record("barrow", "normal", 700, Map.of("a", "Ash")));
        assertTrue(records.record("barrow", "normal", 500, Map.of("b", "Bryn")));
        assertTrue(records.cleared("a", "barrow", "normal"));
        assertFalse(records.cleared("a", "barrow", "heroic"));
        assertEquals(2, records.clears("a", "barrow"));
        ExpeditionRecords reloaded = new ExpeditionRecords(folder.resolve("records.yml").toFile());
        var totals = reloaded.totals("barrow").get("normal");
        assertEquals(3, totals.clears());
        assertEquals(500, totals.bestSeconds());
        assertEquals(List.of("Bryn"), totals.bestParty());
    }
}
