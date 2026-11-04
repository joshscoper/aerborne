package net.nerdsmp.paper.vote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * The Votifier wire format that server list sites speak, both versions NuVotifier accepts. On connect the
 * server greets the site with a random challenge. Version 2 answers with a framed JSON message that echoes
 * the challenge and is signed with a shared token (HMAC-SHA256); version 1 sends one 256-byte block
 * encrypted to the server's RSA public key.
 */
final class VotifierProtocol {
    static final int V2_MAGIC = 0x733A;
    static final int V1_BLOCK = 256;
    static final int MAX_MESSAGE = 8192;

    record Vote(String service, String username, boolean v2) {}

    /** A vote the server won't take. The message is safe to send back to the site. */
    static final class Refused extends IOException {
        private static final long serialVersionUID = 1L;
        final boolean v2;

        Refused(String message, boolean v2) {
            super(message);
            this.v2 = v2;
        }
    }

    private VotifierProtocol() {}

    static String challenge(SecureRandom random) { return new BigInteger(130, random).toString(32); }

    static byte[] greeting(String challenge) { return ("VOTIFIER 2 " + challenge + "\n").getBytes(StandardCharsets.UTF_8); }

    static byte[] ok() { return "{\"status\":\"ok\"}\r\n".getBytes(StandardCharsets.UTF_8); }

    static byte[] error(String message) {
        JsonObject reply = new JsonObject();
        reply.addProperty("status", "error");
        reply.addProperty("cause", "Refused");
        reply.addProperty("error", message);
        return (reply + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    /** Reads one vote of either version: a version 2 message starts with its magic number, anything else is a version 1 block. */
    static Vote read(DataInputStream in, String challenge, Map<String, String> tokens, PrivateKey key) throws IOException {
        int head = in.readUnsignedShort();
        if (head == V2_MAGIC) return readV2(in, challenge, tokens);
        byte[] block = new byte[V1_BLOCK];
        block[0] = (byte) (head >> 8);
        block[1] = (byte) head;
        in.readFully(block, 2, V1_BLOCK - 2);
        return readV1(block, key);
    }

    private static Vote readV2(DataInputStream in, String challenge, Map<String, String> tokens) throws IOException {
        int length = in.readUnsignedShort();
        if (length == 0 || length > MAX_MESSAGE) throw new Refused("Message is empty or too long", true);
        byte[] body = new byte[length];
        in.readFully(body);
        JsonObject message = json(new String(body, StandardCharsets.UTF_8));
        String payload = string(message, "payload");
        byte[] signature;
        try { signature = Base64.getDecoder().decode(string(message, "signature")); }
        catch (IllegalArgumentException invalid) { throw new Refused("Signature is not valid base64", true); }
        JsonObject vote = json(payload);
        String service = string(vote, "serviceName");
        String token = tokens.getOrDefault(service, tokens.get("default"));
        if (token == null) throw new Refused("Unknown service '" + service + "'", true);
        if (!MessageDigest.isEqual(sign(payload, token), signature)) throw new Refused("Signature is not valid (invalid token?)", true);
        if (!challenge.equals(string(vote, "challenge"))) throw new Refused("Challenge is not valid", true);
        return vote(service, string(vote, "username"), true);
    }

    private static Vote readV1(byte[] block, PrivateKey key) throws Refused {
        byte[] plain;
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            plain = cipher.doFinal(block);
        } catch (GeneralSecurityException invalid) {
            throw new Refused("Could not decrypt the vote (wrong public key?)", false);
        }
        String[] lines = new String(plain, StandardCharsets.UTF_8).split("\n");
        if (lines.length < 3 || !lines[0].equals("VOTE")) throw new Refused("Not a vote", false);
        return vote(lines[1], lines[2], false);
    }

    static byte[] sign(String payload, String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException missing) {
            throw new IllegalStateException("HmacSHA256 is unavailable", missing);
        }
    }

    private static Vote vote(String service, String username, boolean v2) throws Refused {
        String site = service.replaceAll("[^\\p{Print}]", "").strip();
        String name = username.strip();
        if (site.isEmpty() || site.length() > 64) throw new Refused("Service name is missing or too long", v2);
        // Java names, plus the . or * a Bedrock bridge puts in front of a Bedrock name.
        if (!name.matches("[.*]?[A-Za-z0-9_]{1,32}")) throw new Refused("Username is not a Minecraft name", v2);
        return new Vote(site, name, v2);
    }

    private static JsonObject json(String text) throws Refused {
        try { return JsonParser.parseString(text).getAsJsonObject(); }
        catch (RuntimeException invalid) { throw new Refused("Message is not JSON", true); }
    }

    private static String string(JsonObject object, String field) throws Refused {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()) throw new Refused("Message has no " + field, true);
        return value.getAsString();
    }
}
