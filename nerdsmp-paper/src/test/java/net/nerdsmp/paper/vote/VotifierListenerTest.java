package net.nerdsmp.paper.vote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Cipher;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Talks to a real listener the way vote sites do, in both Votifier versions. */
class VotifierListenerTest {
    private static final String TOKEN = "s3cret-token";

    @TempDir Path folder;
    private final LinkedBlockingQueue<VotifierProtocol.Vote> votes = new LinkedBlockingQueue<>();
    private KeyPair keys;
    private VotifierListener listener;

    @BeforeEach
    void open() throws Exception {
        keys = VotifierKeys.loadOrCreate(folder.resolve("votifier"));
        listener = new VotifierListener(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), Map.of("default", TOKEN), keys.getPrivate(),
            votes::add, Logger.getLogger("VotifierListenerTest"));
    }

    @AfterEach
    void close() { listener.close(); }

    @Test
    void takesASignedVersionTwoVoteAndSaysOk() throws Exception {
        JsonObject reply = sendV2("PlanetMinecraft.com", "Steve", TOKEN, null);
        assertEquals("ok", reply.get("status").getAsString());
        VotifierProtocol.Vote vote = votes.poll(2, TimeUnit.SECONDS);
        assertEquals(new VotifierProtocol.Vote("PlanetMinecraft.com", "Steve", true), vote);
    }

    @Test
    void refusesAVersionTwoVoteSignedWithTheWrongToken() throws Exception {
        JsonObject reply = sendV2("PlanetMinecraft.com", "Steve", "not-the-token", null);
        assertEquals("error", reply.get("status").getAsString());
        assertTrue(reply.get("error").getAsString().contains("token"));
        assertNull(votes.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void refusesAReplayedVoteWhoseChallengeIsStale() throws Exception {
        JsonObject reply = sendV2("PlanetMinecraft.com", "Steve", TOKEN, "an-old-challenge");
        assertEquals("error", reply.get("status").getAsString());
        assertNull(votes.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void refusesANameThatIsNotAMinecraftName() throws Exception {
        assertEquals("error", sendV2("Site", "<b>bad</b>", TOKEN, null).get("status").getAsString());
        assertNull(votes.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void takesAVersionOneVoteEncryptedToThePublicKeyOnFile() throws Exception {
        // Sites are given the key as the text of public.key, so encrypt with the key read back from that text.
        String published = Files.readString(folder.resolve("votifier/public.key"));
        PublicKey key = java.security.KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(published)));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] block = cipher.doFinal("VOTE\nMinecraftServers.org\nAlex\n203.0.113.9\n1700000000\n".getBytes(StandardCharsets.UTF_8));
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
            new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
            socket.getOutputStream().write(block);
            socket.getOutputStream().flush();
            assertEquals(new VotifierProtocol.Vote("MinecraftServers.org", "Alex", false), votes.poll(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void keepsTheSameKeysAcrossRestarts() throws Exception {
        KeyPair again = VotifierKeys.loadOrCreate(folder.resolve("votifier"));
        assertEquals(keys.getPublic(), again.getPublic());
        assertEquals(keys.getPrivate(), again.getPrivate());
    }

    /** Sends a v2 vote, echoing the server's challenge unless {@code challenge} overrides it, and returns the reply. */
    private JsonObject sendV2(String service, String username, String token, String challenge) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.port())) {
            socket.setSoTimeout(3000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String[] greeting = in.readLine().split(" ");
            assertEquals("VOTIFIER", greeting[0]);
            assertEquals("2", greeting[1]);
            JsonObject vote = new JsonObject();
            vote.addProperty("serviceName", service);
            vote.addProperty("username", username);
            vote.addProperty("address", "203.0.113.9");
            vote.addProperty("timestamp", 1700000000000L);
            vote.addProperty("challenge", challenge == null ? greeting[2] : challenge);
            String payload = vote.toString();
            JsonObject message = new JsonObject();
            message.addProperty("payload", payload);
            message.addProperty("signature", Base64.getEncoder().encodeToString(VotifierProtocol.sign(payload, token)));
            byte[] body = message.toString().getBytes(StandardCharsets.UTF_8);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeShort(VotifierProtocol.V2_MAGIC);
            out.writeShort(body.length);
            out.write(body);
            out.flush();
            return JsonParser.parseString(in.readLine()).getAsJsonObject();
        }
    }
}
