package net.nerdsmp.paper.anticheat;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Who may sign in to the panel: the optional break-glass login from watchdog-panel.yml, plus website
 * administrators synced by {@link PanelSync}. Website accounts stop working if the website hasn't
 * confirmed them within the configured age, so a removed admin can't linger when sync breaks.
 */
public final class PanelAccounts {
    private static final long CACHE_MILLIS = 5 * 60 * 1000L;
    private static final int CACHE_LIMIT = 64;
    private static final byte[] DUMMY_SALT = new byte[32];

    private final PanelCredentials local;
    private final long maxAgeMillis;
    private final LongSupplier clock;
    private final byte[] pepper = new byte[32];
    private final Map<String, Long> verified = new ConcurrentHashMap<>();
    private volatile Map<String, WebsiteAccount> website = Map.of();
    private volatile long confirmedAt = Long.MIN_VALUE;

    public PanelAccounts(PanelCredentials local, long maxAgeMillis, LongSupplier clock) {
        this.local = local;
        this.maxAgeMillis = maxAgeMillis;
        this.clock = clock;
        new SecureRandom().nextBytes(pepper);
    }

    /** Only the yml login, as before website sync existed. */
    public static PanelAccounts localOnly(PanelCredentials credentials) {
        return new PanelAccounts(credentials, 0, System::currentTimeMillis);
    }

    /** Replaces the synced list. Returns a short description of what changed, for the server log. */
    public synchronized String replace(List<WebsiteAccount> accounts) {
        Map<String, WebsiteAccount> next = new ConcurrentHashMap<>();
        for (WebsiteAccount account : accounts) {
            if (local != null && local.username().equals(account.username())) continue; // break-glass login wins
            next.putIfAbsent(account.username(), account);
        }
        Map<String, WebsiteAccount> previous = website;
        Set<String> added = new TreeSet<>();
        Set<String> removed = new TreeSet<>(previous.keySet());
        Set<String> changed = new TreeSet<>();
        for (var entry : next.entrySet()) {
            WebsiteAccount before = previous.get(entry.getKey());
            removed.remove(entry.getKey());
            if (before == null) added.add(entry.getKey());
            else if (!before.sameAs(entry.getValue())) changed.add(entry.getKey());
        }
        website = Map.copyOf(next);
        confirmedAt = clock.getAsLong();
        if (!removed.isEmpty() || !changed.isEmpty()) verified.clear();
        if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        if (!added.isEmpty()) text.append("added ").append(String.join(", ", added));
        if (!changed.isEmpty()) text.append(text.length() > 0 ? "; " : "").append("new password for ").append(String.join(", ", changed));
        if (!removed.isEmpty()) text.append(text.length() > 0 ? "; " : "").append("removed ").append(String.join(", ", removed));
        return text.toString();
    }

    public boolean hasLocal() { return local != null; }
    public Set<String> websiteUsers() { return websiteFresh() ? website.keySet() : Set.of(); }

    boolean websiteFresh() {
        long confirmed = confirmedAt;
        return confirmed != Long.MIN_VALUE && clock.getAsLong() - confirmed <= maxAgeMillis;
    }

    /** Returns the signed-in username, or null. */
    public String authenticate(String user, char[] password) {
        if (user == null || password == null || password.length > 128) return null;
        String key = cacheKey(user, password);
        Long until = verified.get(key);
        long now = clock.getAsLong();
        if (until != null && until > now && stillAllowed(user)) return user;
        boolean ok;
        if (local != null && local.username().equals(user)) {
            ok = local.verify(user, password);
        } else {
            WebsiteAccount account = websiteFresh() ? website.get(user) : null;
            if (account != null) ok = account.verify(password);
            else { PanelCredentials.derive(password, DUMMY_SALT, 600_000); ok = false; } // same cost as a real check
        }
        if (!ok) return null;
        if (verified.size() >= CACHE_LIMIT) verified.clear();
        verified.put(key, now + CACHE_MILLIS);
        return user;
    }

    private boolean stillAllowed(String user) {
        return local != null && local.username().equals(user) || websiteFresh() && website.containsKey(user);
    }

    private String cacheKey(String user, char[] password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper);
            digest.update(user.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
            digest.update(bytes.duplicate());
            if (bytes.hasArray()) Arrays.fill(bytes.array(), (byte) 0); // no plaintext copy left behind
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
