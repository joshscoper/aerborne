package net.nerdsmp.paper.anticheat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * A website administrator allowed into the panel. The website stores Werkzeug-format PBKDF2 hashes
 * ({@code pbkdf2:sha256:<iterations>$<salt>$<hex>}); the same derivation is checked here, so no
 * plaintext password ever leaves the website and the panel never stores one.
 */
public record WebsiteAccount(String username, String salt, int iterations, byte[] hash) {
    static final int MIN_ITERATIONS = 600_000;
    static final int MAX_ITERATIONS = 2_000_000;

    public WebsiteAccount {
        if (username == null || !username.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("Invalid website username");
        if (salt == null || !salt.matches("[A-Za-z0-9]{8,64}")) throw new IllegalArgumentException("Invalid website password salt");
        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) throw new IllegalArgumentException("Website password hash is too weak");
        if (hash == null || hash.length != 32) throw new IllegalArgumentException("Invalid website password hash");
        hash = hash.clone();
    }

    /** Parses {@code pbkdf2:sha256:600000$salt$hex}. */
    public static WebsiteAccount parse(String username, String encoded) {
        if (encoded == null || encoded.length() > 160) throw new IllegalArgumentException("Invalid website password hash");
        String[] parts = encoded.split("\\$", -1);
        if (parts.length != 3 || !parts[0].matches("pbkdf2:sha256:[0-9]{6,7}") || !parts[2].matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Unsupported website password hash");
        }
        int iterations = Integer.parseInt(parts[0].substring("pbkdf2:sha256:".length()));
        return new WebsiteAccount(username, parts[1], iterations, HexFormat.of().parseHex(parts[2]));
    }

    public boolean verify(char[] password) {
        if (password == null || password.length > 128) return false;
        byte[] candidate = PanelCredentials.derive(password, salt.getBytes(StandardCharsets.UTF_8), iterations);
        try { return MessageDigest.isEqual(candidate, hash); }
        finally { Arrays.fill(candidate, (byte) 0); }
    }

    /** Same account and same password hash: lets a sync skip logging unchanged accounts. */
    boolean sameAs(WebsiteAccount other) {
        return other != null && username.equals(other.username) && salt.equals(other.salt)
            && iterations == other.iterations && MessageDigest.isEqual(hash, other.hash);
    }

    @Override public String toString() { return "WebsiteAccount[" + username + "]"; }
    @Override public boolean equals(Object other) { return other instanceof WebsiteAccount account && sameAs(account); }
    @Override public int hashCode() { return username.hashCode(); }
}
