package net.nerdsmp.paper.trade;

import net.nerdsmp.api.model.AccountId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TradeJournalTest {
    @TempDir Path directory;
    private TradeJournal journal() { return new TradeJournal(directory.resolve("trades").toFile()); }
    private TradeJournal.Side side() { return new TradeJournal.Side(UUID.randomUUID(), AccountId.random(), Collections.nCopies(36, ""), Collections.nCopies(36, "AQID"), false); }
    @Test void durableDecisionAndEachReceiptSurviveRestart() {
        var journal = journal(); var left = side(); var right = side(); var entry = journal.begin(left, right);
        var restarted = journal(); assertEquals(entry, restarted.forPlayer(left.player()).orElseThrow());
        restarted.acknowledge(entry.id(), left.player());
        restarted = journal(); var pending = restarted.pending().getFirst();
        assertTrue(pending.left().saved()); assertFalse(pending.right().saved()); assertTrue(restarted.blocked(left.account()));
        restarted.acknowledge(entry.id(), right.player());
        assertTrue(journal().pending().isEmpty());
        assertTrue(Files.exists(directory.resolve("trades/completed/" + entry.id() + ".yml")));
    }
    @Test void duplicateAcknowledgmentsDoNotReapplyOrReopenAnExchange() {
        var journal = journal(); var a = side(); var b = side(); var entry = journal.begin(a, b);
        journal.acknowledge(entry.id(), a.player()); journal.acknowledge(entry.id(), a.player());
        assertEquals(1, journal.pending().size());
        journal.acknowledge(entry.id(), b.player()); journal.acknowledge(entry.id(), b.player());
        assertTrue(journal.pending().isEmpty());
    }
    @Test void failedInitialWriteCannotPublishATrade() throws Exception {
        var journal = journal(); Files.createFile(directory.resolve("trades"));
        var a = side(); assertThrows(IllegalStateException.class, () -> journal.begin(a, side()));
        assertTrue(journal.pending().isEmpty()); assertFalse(journal.blocked(a.account()));
    }
    @Test void failedReceiptLeavesPendingSideUnacknowledgedInMemoryAndOnDisk() throws Exception {
        var journal = journal(); var a = side(); var entry = journal.begin(a, side());
        Files.createDirectory(directory.resolve("trades/" + entry.id() + ".yml.tmp"));
        assertThrows(IllegalStateException.class, () -> journal.acknowledge(entry.id(), a.player()));
        assertFalse(journal.pending().getFirst().left().saved()); assertFalse(journal().pending().getFirst().left().saved());
    }
    @Test void pendingAccountsAndPhysicalPlayersCannotJoinASecondTrade() {
        var journal = journal(); var a = side(); journal.begin(a, side());
        assertThrows(IllegalStateException.class, () -> journal.begin(a, side()));
        var linked = new TradeJournal.Side(UUID.randomUUID(), a.account(), a.before(), a.after(), false);
        assertThrows(IllegalStateException.class, () -> journal.begin(linked, side()));
        var relinked = new TradeJournal.Side(a.player(), AccountId.random(), a.before(), a.after(), false);
        assertThrows(IllegalStateException.class, () -> journal.begin(relinked, side()));
    }
    @Test void sameCanonicalAccountAndUnknownReceiptAreRejected() {
        var journal = journal(); var a = side(); var linked = new TradeJournal.Side(UUID.randomUUID(), a.account(), a.before(), a.after(), false);
        assertThrows(IllegalArgumentException.class, () -> journal.begin(a, linked));
        var entry = journal.begin(a, side());
        assertThrows(IllegalArgumentException.class, () -> journal.acknowledge(entry.id(), UUID.randomUUID()));
    }
    @Test void corruptionStopsLoadingRatherThanDiscardingItems() throws Exception {
        var entry = journal().begin(side(), side()); var path = directory.resolve("trades/" + entry.id() + ".yml");
        Files.writeString(path, "schema: 77\n");
        assertThrows(IllegalStateException.class, this::journal); assertTrue(Files.exists(path));
    }
    @Test void invalidInventoryShapeEncodingAndBudgetAreRejected() {
        var a = side();
        assertThrows(IllegalArgumentException.class, () -> new TradeJournal.Side(a.player(), a.account(), List.of(""), a.after(), false));
        assertThrows(IllegalArgumentException.class, () -> new TradeJournal.Side(a.player(), a.account(), Collections.nCopies(36, "not base64!"), a.after(), false));
        assertThrows(IllegalArgumentException.class, () -> new TradeJournal.Side(a.player(), a.account(), Collections.nCopies(36, "AAAA".repeat(4096)), a.after(), false));
    }
}
