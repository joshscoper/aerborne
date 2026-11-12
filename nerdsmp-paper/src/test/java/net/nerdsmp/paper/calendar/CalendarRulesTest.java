package net.nerdsmp.paper.calendar;

import net.nerdsmp.paper.collection.CollectionRulesProbe;
import net.nerdsmp.paper.cosmetic.CompanionBond;
import net.nerdsmp.paper.release.WhatsNew;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class CalendarRulesTest {
    @Test void threeDifferentCandidatesStandEachMonthAndTheMostVotedWins() {
        Set<WardensCouncil.Candidate> everyone = new HashSet<>();
        for (int m = 1; m <= 24; m++) {
            var standing = WardensCouncil.standing(YearMonth.of(2026, 1).plusMonths(m));
            assertEquals(3, standing.stream().distinct().count());
            assertEquals(standing, WardensCouncil.standing(YearMonth.of(2026, 1).plusMonths(m)));
            everyone.addAll(standing);
        }
        assertEquals(WardensCouncil.CANDIDATES.size(), everyone.size(), "everyone gets a turn over two years");
        var standing = WardensCouncil.standing(YearMonth.of(2026, 12));
        assertEquals(standing.get(1), WardensCouncil.winner(standing, Map.of(standing.get(1).id(), 3, standing.get(2).id(), 2)));
        assertEquals(standing.getFirst(), WardensCouncil.winner(standing, Map.of(standing.get(0).id(), 2, standing.get(2).id(), 2)), "ties go to the first listed");
        assertTrue(WardensCouncil.voting(LocalDate.of(2026, 12, 5)));
        assertFalse(WardensCouncil.voting(LocalDate.of(2026, 12, 6)));
    }

    @Test void eachMonthHasItsFestivalForAFewDays() {
        assertEquals(12, Festivals.FESTIVALS.stream().map(Festivals.Festival::id).distinct().count());
        assertEquals("Harvest Moon", Festivals.of(Month.SEPTEMBER).name());
        assertTrue(Festivals.during(LocalDate.of(2026, 11, 13), 13, 4));
        assertTrue(Festivals.during(LocalDate.of(2026, 11, 16), 13, 4));
        assertFalse(Festivals.during(LocalDate.of(2026, 11, 17), 13, 4));
        assertFalse(Festivals.during(LocalDate.of(2026, 11, 12), 13, 4));
    }

    @Test void companionBondsClimbWithTimeTogether() {
        assertEquals(1, CompanionBond.level(0));
        assertEquals(2, CompanionBond.level(30));
        assertEquals(10, CompanionBond.level(100_000));
        assertFalse(CompanionBond.unlocked(29, CompanionBond.Unlock.NAME));
        assertTrue(CompanionBond.unlocked(2400, CompanionBond.Unlock.RADIANT));
    }

    @Test void collectionsCountTheRightThings() { CollectionRulesProbe.check(); }

    @Test void theNotesFileReads() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/content/whats-new.yml"), StandardCharsets.UTF_8)) {
            WhatsNew.Notes notes = WhatsNew.parse(YamlConfiguration.loadConfiguration(reader));
            assertFalse(notes.version().isBlank());
            assertTrue(notes.pages().size() >= 3);
            for (String page : notes.pages()) assertTrue(page.split("\n").length <= 14, "a book page holds about 14 lines");
        }
    }
}
