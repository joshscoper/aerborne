package net.nerdsmp.paper.support;

import net.nerdsmp.api.model.AccountId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SupportStoreTest {
    @TempDir Path directory;
    final AccountId owner = new AccountId(UUID.randomUUID()), staff = new AccountId(UUID.randomUUID()), stranger = new AccountId(UUID.randomUUID());
    final Instant now = Instant.parse("2026-10-24T12:00:00Z");
    SupportStore store() { return new SupportStore(directory.resolve("support.yml").toFile()); }
    @Test void completeConversationSurvivesRestartWithUnreadRepliesAndAssignment() {
        var s = store(); var t = s.create(owner, "Player", "Please help", now);
        s.claim(t.id(), staff, "Helper"); s.reply(t.id(), staff, "Helper", true, "Hello <red>literal</red>", now.plusSeconds(1));
        var restored = store().get(t.id()).orElseThrow();
        assertEquals(staff, restored.assigned()); assertEquals(1, restored.unread()); assertEquals("Hello <red>literal</red>", restored.messages().getLast().text());
        s.read(t.id(), owner, 2); assertEquals(0, store().get(t.id()).orElseThrow().unread());
        s.close(t.id(), owner); assertTrue(store().get(t.id()).orElseThrow().closed()); assertTrue(store().queue().isEmpty());
    }
    @Test void assignmentAndOwnershipAreEnforcedForEveryMutation() {
        var s = store(); var t = s.create(owner, "P", "Need help", now);
        assertThrows(IllegalArgumentException.class, () -> s.claim(t.id(), owner, "P"));
        assertThrows(IllegalArgumentException.class, () -> s.reply(t.id(), stranger, "X", false, "secret", now));
        assertThrows(IllegalArgumentException.class, () -> s.reply(t.id(), staff, "S", true, "hello", now));
        s.claim(t.id(), staff, "S");
        assertThrows(IllegalArgumentException.class, () -> s.claim(t.id(), stranger, "X"));
        assertThrows(IllegalArgumentException.class, () -> s.reply(t.id(), stranger, "X", true, "secret", now));
        assertThrows(IllegalArgumentException.class, () -> s.close(t.id(), stranger));
        assertThrows(IllegalArgumentException.class, () -> s.read(t.id(), stranger, 1));
        assertThrows(IllegalArgumentException.class, () -> s.release(t.id(), stranger));
        assertEquals(1, s.get(t.id()).orElseThrow().messages().size());
    }
    @Test void releasedOrClosedThreadsCannotReceiveFormerStaffMessages() {
        var s = store(); var t = s.create(owner, "P", "Need help", now); s.claim(t.id(), staff, "S"); s.release(t.id(), staff);
        assertThrows(IllegalArgumentException.class, () -> s.reply(t.id(), staff, "S", true, "old response", now));
        s.claim(t.id(), stranger, "New"); assertEquals(stranger, store().get(t.id()).orElseThrow().assigned());
        s.close(t.id(), stranger);
        assertThrows(IllegalArgumentException.class, () -> s.reply(t.id(), owner, "P", false, "after close", now));
        assertThrows(IllegalArgumentException.class, () -> s.claim(t.id(), staff, "S"));
    }
    @Test void duplicateAndRapidReopenedRequestsArePreventedAcrossRestart() {
        var s = store(); var t = s.create(owner, "P", "Need help", now);
        assertThrows(IllegalArgumentException.class, () -> s.create(owner, "P", "Duplicate", now.plusSeconds(600)));
        s.close(t.id(), owner);
        assertThrows(IllegalArgumentException.class, () -> store().create(owner, "P", "Again", now.plusSeconds(59)));
        assertEquals(2, store().create(owner, "P", "Follow-up", now.plusSeconds(60)).id());
    }
    @Test void failedSaveNeverPublishesMessageClaimCloseOrReadMarker() throws Exception {
        var s = store(); var t = s.create(owner, "P", "Need help", now); s.claim(t.id(), staff, "S");
        Files.createDirectory(directory.resolve("support.yml.tmp"));
        assertThrows(IllegalStateException.class, () -> s.reply(t.id(), staff, "S", true, "not delivered", now));
        assertThrows(IllegalStateException.class, () -> s.release(t.id(), staff));
        assertThrows(IllegalStateException.class, () -> s.close(t.id(), owner));
        assertThrows(IllegalStateException.class, () -> s.read(t.id(), owner, 1));
        assertEquals(t.messages(), s.get(t.id()).orElseThrow().messages());
        assertEquals(staff, s.get(t.id()).orElseThrow().assigned()); assertFalse(s.get(t.id()).orElseThrow().closed()); assertEquals(0, s.get(t.id()).orElseThrow().read());
        assertEquals(s.get(t.id()), store().get(t.id()));
    }
    @Test void failedCreateDoesNotConsumeTicketNumberOrPublishQueue() throws Exception {
        var s = store(); Path blocker = Files.createDirectory(directory.resolve("support.yml.tmp"));
        assertThrows(IllegalStateException.class, () -> s.create(owner, "P", "Need help", now));
        assertTrue(s.queue().isEmpty()); Files.delete(blocker); assertEquals(1, s.create(owner, "P", "Need help", now).id());
    }
    @Test void malformedQueueFailsClosedAndIsNeverReplaced() throws Exception {
        String bad = "version: 1\nnext-id: 2\ntickets:\n  - {id: 1, owner: broken}\n";
        Files.writeString(directory.resolve("support.yml"), bad);
        assertThrows(IllegalStateException.class, this::store); assertEquals(bad, Files.readString(directory.resolve("support.yml")));
    }
    @Test void schemaAndYamlCorruptionFailClosed() throws Exception {
        for (String bad : new String[]{"version: 2\n", "version: [\n", "version: 1\nnext-id: 0\n", "version: 1\nnext-id: 2\ntickets: broken\n", "version: 1\nnext-id: 2\ntickets: [broken]\n"}) {
            Files.writeString(directory.resolve("support.yml"), bad); assertThrows(IllegalStateException.class, this::store);
        }
    }
    @Test void messagesHaveSafeBoundsAndNoControlCharacters() {
        for (String bad : new String[]{"", "a", "x".repeat(501), "help\nline", "help\u0000"}) assertThrows(IllegalArgumentException.class, () -> store().create(owner, "P", bad, now));
        assertEquals("help", SupportStore.text("  help  ", 3)); assertEquals("x".repeat(500), SupportStore.text("x".repeat(500), 1));
    }
    @Test void conversationLimitStillAllowsClosingAndFollowUp() {
        var s = store(); var t = s.create(owner, "P", "Need help", now);
        for (int i = 1; i < SupportStore.MAX_MESSAGES; i++) s.reply(t.id(), owner, "P", false, "message " + i, now.plusSeconds(i));
        assertThrows(IllegalArgumentException.class, () -> s.reply(t.id(), owner, "P", false, "overflow", now));
        s.close(t.id(), owner); assertEquals(2, s.create(owner, "P", "Follow up", now.plusSeconds(600)).id());
    }
}
