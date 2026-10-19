package net.nerdsmp.paper.webapi;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Short-lived codes that let the website prove which Minecraft account a web account belongs to.
 * The player asks for a code in game and types it on the website; the website reads it back through
 * the private API. Codes never leave the server any other way, and each player has at most one.
 */
public final class WebLinkCodes {
    static final long TTL_MILLIS = 10 * 60 * 1000L;
    static final int MAX_ACTIVE = 10_000;
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    public record Link(String code, String accountId, UUID playerId, String name, long issuedAtMillis, long expiresAtMillis) {}

    private final Map<String, Link> byCode = new ConcurrentHashMap<>();
    private final Map<UUID, String> codeByPlayer = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final LongSupplier clock;

    public WebLinkCodes(LongSupplier clock) { this.clock = clock; }

    /** Issues a fresh code for this player, replacing any code they already had. */
    public synchronized Link issue(String accountId, UUID playerId, String name) {
        long now = clock.getAsLong();
        prune(now);
        String previous = codeByPlayer.remove(playerId);
        if (previous != null) byCode.remove(previous);
        if (byCode.size() >= MAX_ACTIVE) throw new IllegalStateException("Too many website link codes are active");
        String code;
        do { code = generate(); } while (byCode.containsKey(code));
        Link link = new Link(code, accountId, playerId, name, now, now + TTL_MILLIS);
        byCode.put(code, link);
        codeByPlayer.put(playerId, code);
        return link;
    }

    /** Safe to call from HTTP workers: only reads this class's own concurrent maps. */
    public Optional<Link> find(String code) {
        if (!valid(code)) return Optional.empty();
        Link link = byCode.get(code);
        if (link == null) return Optional.empty();
        if (clock.getAsLong() >= link.expiresAtMillis()) {
            byCode.remove(code, link);
            codeByPlayer.remove(link.playerId(), code);
            return Optional.empty();
        }
        return Optional.of(link);
    }

    public static boolean valid(String code) {
        if (code == null || code.length() != 8) return false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (!(c >= 'A' && c <= 'Z' || c >= '2' && c <= '9') || c == 'I' || c == 'O') return false;
        }
        return true;
    }

    int active() { return byCode.size(); }

    private void prune(long now) {
        byCode.values().removeIf(link -> {
            if (now < link.expiresAtMillis()) return false;
            codeByPlayer.remove(link.playerId(), link.code());
            return true;
        });
    }

    private String generate() {
        char[] out = new char[8];
        for (int i = 0; i < out.length; i++) out[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        return new String(out);
    }
}
