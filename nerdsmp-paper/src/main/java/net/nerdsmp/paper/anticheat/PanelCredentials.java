package net.nerdsmp.paper.anticheat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/** No plaintext password is persisted. PBKDF2 work factors are validated before use. */
public record PanelCredentials(String username, String salt, String hash, int iterations) {
    public PanelCredentials {
        if (username == null || !username.matches("[A-Za-z0-9_-]{1,32}") || iterations < 600_000 || iterations > 2_000_000
            || Base64.getDecoder().decode(salt).length != 32 || Base64.getDecoder().decode(hash).length != 32)
            throw new IllegalArgumentException("Invalid panel password hash configuration");
    }
    public boolean verify(String user, char[] password) {
        if (user == null || password == null || password.length > 128) return false;
        byte[] candidate = derive(password, Base64.getDecoder().decode(salt), iterations);
        try { return MessageDigest.isEqual(candidate, Base64.getDecoder().decode(hash)) & username.equals(user); }
        finally { Arrays.fill(candidate, (byte) 0); }
    }
    public static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (java.security.GeneralSecurityException error) { throw new IllegalStateException("Password hashing unavailable", error); }
        finally { spec.clearPassword(); }
    }
}
