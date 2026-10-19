package net.nerdsmp.paper.npc.chat;

import net.nerdsmp.api.model.AccountId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatDialogueSessionsTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID INSTANCE = UUID.randomUUID();
    private static final AccountId ACCOUNT = AccountId.random();

    @Test void oneResponseIsClaimedBeforeWorkAndReplayCannotChooseAnother() {
        ChatDialogueSessions sessions = new ChatDialogueSessions();
        UUID token = UUID.randomUUID();
        ChatDialogueSessions.Session rendered = start(sessions, PLAYER, token);
        assertFalse(rendered.pending());
        ChatDialogueSessions.Claim claimed = sessions.begin(PLAYER, ACCOUNT, token, 2);
        assertTrue(claimed.accepted());
        assertEquals("trade", claimed.choiceId());
        assertTrue(claimed.session().pending());
        assertTrue(sessions.current(PLAYER).orElseThrow().pending());
        assertTrue(sessions.matches(PLAYER, ACCOUNT, token));
        assertEquals(ChatDialogueSessions.Status.BUSY, sessions.begin(PLAYER, ACCOUNT, token, 1).status());
        assertTrue(sessions.complete(PLAYER, token));
        assertEquals(ChatDialogueSessions.Status.MISSING, sessions.begin(PLAYER, ACCOUNT, token, 2).status());
    }

    @Test void wrongPlayerAccountAndTokenNeverClaimTheOwnerResponse() {
        ChatDialogueSessions sessions = new ChatDialogueSessions();
        UUID token = UUID.randomUUID();
        start(sessions, PLAYER, token);
        assertEquals(ChatDialogueSessions.Status.MISSING,
            sessions.begin(UUID.randomUUID(), ACCOUNT, token, 1).status());
        assertEquals(ChatDialogueSessions.Status.STALE,
            sessions.begin(PLAYER, AccountId.random(), token, 1).status());
        assertEquals(ChatDialogueSessions.Status.STALE,
            sessions.begin(PLAYER, ACCOUNT, UUID.randomUUID(), 1).status());
        assertFalse(sessions.matches(PLAYER, AccountId.random(), token));
        assertFalse(sessions.matches(PLAYER, ACCOUNT, UUID.randomUUID()));
        assertFalse(sessions.current(PLAYER).orElseThrow().pending());
        assertTrue(sessions.begin(PLAYER, ACCOUNT, token, 1).accepted());
    }

    @Test void invalidNumberDoesNotConsumeTheConversation() {
        ChatDialogueSessions sessions = new ChatDialogueSessions();
        UUID token = UUID.randomUUID();
        start(sessions, PLAYER, token);
        for (int number : new int[]{Integer.MIN_VALUE, -1, 0, 3, Integer.MAX_VALUE}) {
            ChatDialogueSessions.Claim claim = sessions.begin(PLAYER, ACCOUNT, token, number);
            assertEquals(ChatDialogueSessions.Status.INVALID_CHOICE, claim.status());
            assertThrows(IllegalStateException.class, claim::choiceId);
        }
        assertTrue(sessions.begin(PLAYER, ACCOUNT, token, 1).accepted());
    }

    @Test void expiredAtExactlyTwoMinutesDoesNotAcceptAResponse() {
        MutableClock clock = new MutableClock();
        ChatDialogueSessions sessions = new ChatDialogueSessions(clock);
        UUID token = UUID.randomUUID();
        ChatDialogueSessions.Session initial = start(sessions, PLAYER, token);
        assertEquals(clock.instant().plusSeconds(120), initial.expiresAt());
        clock.advance(119);
        assertTrue(sessions.matches(PLAYER, ACCOUNT, token));
        clock.advance(1);
        assertEquals(ChatDialogueSessions.Status.EXPIRED, sessions.begin(PLAYER, ACCOUNT, token, 1).status());
        assertTrue(sessions.current(PLAYER).isEmpty());
    }

    @Test void currentAlsoPrunesExpiredPendingActions() {
        MutableClock clock = new MutableClock();
        ChatDialogueSessions sessions = new ChatDialogueSessions(clock);
        UUID token = UUID.randomUUID();
        start(sessions, PLAYER, token);
        assertTrue(sessions.begin(PLAYER, ACCOUNT, token, 1).accepted());
        clock.advance(120);
        assertTrue(sessions.current(PLAYER).isEmpty());
        assertFalse(sessions.complete(PLAYER, token));
    }

    @Test void aNewPageRejectsOldClicksAndCannotBeClosedByAnOldCompletion() {
        ChatDialogueSessions sessions = new ChatDialogueSessions();
        UUID oldToken = UUID.randomUUID();
        start(sessions, PLAYER, oldToken);
        assertTrue(sessions.begin(PLAYER, ACCOUNT, oldToken, 1).accepted());
        UUID newToken = UUID.randomUUID();
        ChatDialogueSessions.Session replacement = sessions.start(PLAYER, ACCOUNT, INSTANCE,
            "merchant", "greeting.next", List.of("question"), newToken);
        assertFalse(sessions.complete(PLAYER, oldToken));
        assertEquals(ChatDialogueSessions.Status.STALE, sessions.begin(PLAYER, ACCOUNT, oldToken, 1).status());
        assertEquals(replacement, sessions.current(PLAYER).orElseThrow());
        assertTrue(sessions.begin(PLAYER, ACCOUNT, newToken, 1).accepted());
    }

    @Test void restartingWithTheSameNonceFailsWithoutReplacingExistingState() {
        ChatDialogueSessions sessions = new ChatDialogueSessions();
        UUID token = UUID.randomUUID();
        ChatDialogueSessions.Session original = start(sessions, PLAYER, token);
        assertThrows(IllegalArgumentException.class, () -> start(sessions, PLAYER, token));
        assertEquals(original, sessions.current(PLAYER).orElseThrow());
    }

    @Test void allPlayersAreIsolatedAndCapacityIsBoundedWithoutEvictingLiveSessions() {
        ChatDialogueSessions sessions = new ChatDialogueSessions();
        List<UUID> players = new ArrayList<>();
        for (int index = 0; index < ChatDialogueSessions.MAX_PLAYERS; index++) {
            UUID player = new UUID(5, index);
            players.add(player);
            start(sessions, player, UUID.randomUUID());
        }
        assertThrows(IllegalStateException.class, () -> start(sessions, PLAYER, UUID.randomUUID()));
        assertTrue(sessions.current(players.getFirst()).isPresent());
        assertDoesNotThrow(() -> start(sessions, players.getFirst(), UUID.randomUUID()));
        sessions.invalidate(players.getLast());
        assertDoesNotThrow(() -> start(sessions, PLAYER, UUID.randomUUID()));
        sessions.clear();
        assertTrue(sessions.current(PLAYER).isEmpty());
        assertTrue(sessions.current(players.getFirst()).isEmpty());
    }

    @Test void aNewConversationPrunesExpiredEntriesBeforeCheckingCapacity() {
        MutableClock clock = new MutableClock();
        ChatDialogueSessions sessions = new ChatDialogueSessions(clock);
        for (int index = 0; index < ChatDialogueSessions.MAX_PLAYERS; index++) {
            start(sessions, new UUID(7, index), UUID.randomUUID());
        }
        clock.advance(120);
        assertDoesNotThrow(() -> start(sessions, PLAYER, UUID.randomUUID()));
        assertTrue(sessions.current(new UUID(7, 0)).isEmpty());
    }

    @Test void authoredIdentifiersAndChoicesAreImmutableAndValidated() {
        ChatDialogueSessions sessions = new ChatDialogueSessions();
        List<String> choices = new ArrayList<>(List.of("ask", "trade"));
        ChatDialogueSessions.Session snapshot = sessions.start(PLAYER, ACCOUNT, INSTANCE, "merchant", "greeting",
            choices, UUID.randomUUID());
        choices.clear();
        assertEquals(List.of("ask", "trade"), snapshot.choiceIds());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.choiceIds().clear());
        assertThrows(IllegalArgumentException.class, () -> sessions.start(PLAYER, ACCOUNT, INSTANCE, "bad id", "greeting",
            List.of("ask"), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> sessions.start(PLAYER, ACCOUNT, INSTANCE, "merchant", "greeting",
            List.of("ask", "ask"), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> sessions.start(PLAYER, ACCOUNT, INSTANCE, "merchant", "greeting",
            List.of("a", "b", "c", "d", "e", "f"), UUID.randomUUID()));
        assertEquals(snapshot, sessions.current(PLAYER).orElseThrow());
    }

    @Test void terminalDialogueWithNoResponsesStillHasAClosableSession() {
        ChatDialogueSessions sessions = new ChatDialogueSessions();
        UUID token = UUID.randomUUID();
        sessions.start(PLAYER, ACCOUNT, INSTANCE, "merchant", "farewell", List.of(), token);
        assertEquals(ChatDialogueSessions.Status.INVALID_CHOICE, sessions.begin(PLAYER, ACCOUNT, token, 1).status());
        assertTrue(sessions.complete(PLAYER, token));
    }

    private static ChatDialogueSessions.Session start(ChatDialogueSessions sessions, UUID player, UUID token) {
        return sessions.start(player, ACCOUNT, INSTANCE, "merchant", "greeting", List.of("ask", "trade"), token);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-12T00:00:00Z");

        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
