package net.nerdsmp.paper.npc.chat;

import net.nerdsmp.api.model.AccountId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Short-lived, player-bound response nonces. No world or reward decisions are made here. */
public final class ChatDialogueSessions {
    public static final int MAX_PLAYERS = 256;
    public static final Duration TTL = Duration.ofSeconds(120);

    private final Clock clock;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public ChatDialogueSessions() {
        this(Clock.systemUTC());
    }

    public ChatDialogueSessions(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized Session start(
        UUID player,
        AccountId account,
        UUID instance,
        String npcId,
        String nodeId,
        List<String> choiceIds,
        UUID token
    ) {
        Session created = new Session(player, account, instance, npcId, nodeId, choiceIds,
            token, clock.instant().plus(TTL), false);
        removeExpired();
        if (!sessions.containsKey(player) && sessions.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Too many active NPC conversations. Please try again shortly.");
        }
        Session previous = sessions.get(player);
        if (previous != null && previous.token().equals(token)) {
            throw new IllegalArgumentException("A new conversation page needs a fresh response token");
        }
        sessions.put(player, created);
        return created;
    }

    /** Includes a claimed response while its asynchronous action is still running. */
    public synchronized Optional<Session> current(UUID player) {
        Objects.requireNonNull(player, "player");
        Session session = sessions.get(player);
        if (session != null && !session.expiresAt().isAfter(clock.instant())) {
            sessions.remove(player);
            return Optional.empty();
        }
        return Optional.ofNullable(session);
    }

    public synchronized boolean matches(UUID player, AccountId account, UUID token) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(token, "token");
        return current(player).filter(session -> session.account().equals(account)
            && session.token().equals(token)).isPresent();
    }

    /** Claims exactly one numbered response for this rendering before any asynchronous work starts. */
    public synchronized Claim begin(UUID player, AccountId account, UUID token, int oneBasedChoice) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(token, "token");
        Session session = sessions.get(player);
        if (session == null) return new Claim(Status.MISSING, null, oneBasedChoice);
        if (!session.expiresAt().isAfter(clock.instant())) {
            sessions.remove(player);
            return new Claim(Status.EXPIRED, null, oneBasedChoice);
        }
        if (!session.account().equals(account) || !session.token().equals(token)) {
            return new Claim(Status.STALE, null, oneBasedChoice);
        }
        if (oneBasedChoice < 1 || oneBasedChoice > session.choiceIds().size()) {
            return new Claim(Status.INVALID_CHOICE, null, oneBasedChoice);
        }
        if (session.pending()) return new Claim(Status.BUSY, session, oneBasedChoice);
        Session claimed = new Session(session.player(), session.account(), session.instance(), session.npcId(),
            session.nodeId(), session.choiceIds(), session.token(), session.expiresAt(), true);
        sessions.put(player, claimed);
        return new Claim(Status.ACCEPTED, claimed, oneBasedChoice);
    }

    /** An old action may not close a newer conversation. */
    public synchronized boolean complete(UUID player, UUID token) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(token, "token");
        Session session = sessions.get(player);
        if (session == null || !session.token().equals(token)) return false;
        sessions.remove(player);
        return true;
    }

    public synchronized void invalidate(UUID player) {
        sessions.remove(Objects.requireNonNull(player, "player"));
    }

    public synchronized void clear() {
        sessions.clear();
    }

    private void removeExpired() {
        Instant now = clock.instant();
        sessions.values().removeIf(session -> !session.expiresAt().isAfter(now));
    }

    public enum Status { ACCEPTED, MISSING, EXPIRED, STALE, BUSY, INVALID_CHOICE }

    public record Claim(Status status, Session session, int oneBasedChoice) {
        public boolean accepted() {
            return status == Status.ACCEPTED;
        }

        public String choiceId() {
            if (!accepted()) throw new IllegalStateException("This response has not been claimed");
            return session.choiceIds().get(oneBasedChoice - 1);
        }
    }

    public record Session(
        UUID player,
        AccountId account,
        UUID instance,
        String npcId,
        String nodeId,
        List<String> choiceIds,
        UUID token,
        Instant expiresAt,
        boolean pending
    ) {
        public Session {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(instance, "instance");
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(expiresAt, "expiresAt");
            npcId = identifier(npcId);
            nodeId = identifier(nodeId);
            choiceIds = List.copyOf(choiceIds);
            if (choiceIds.size() > 5 || choiceIds.stream().distinct().count() != choiceIds.size()) {
                throw new IllegalArgumentException("Conversation responses need at most five unique IDs");
            }
            choiceIds.forEach(ChatDialogueSessions::identifier);
        }
    }

    private static String identifier(String value) {
        Objects.requireNonNull(value, "id");
        if (!value.matches("[a-z0-9_.:/-]{1,128}")) {
            throw new IllegalArgumentException("Invalid conversation content ID: " + value);
        }
        return value;
    }
}
