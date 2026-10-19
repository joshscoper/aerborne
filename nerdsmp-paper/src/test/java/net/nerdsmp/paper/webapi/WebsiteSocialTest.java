package net.nerdsmp.paper.webapi;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.nerdsmp.api.model.AccountId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Friends, blocks and messages exchanged with the website for linked players. */
class WebsiteSocialTest {
    private static final String TOKEN = "f".repeat(64);
    private static final AccountId AVA = AccountId.parse("aaaaaaaa-0000-4000-8000-000000000001");
    private static final AccountId BO = AccountId.parse("bbbbbbbb-0000-4000-8000-000000000002");
    private static final AccountId CY = AccountId.parse("cccccccc-0000-4000-8000-000000000003");

    /** Records what the website asked the game to do. */
    static final class FakePort implements WebsiteSocial.Port {
        final List<String> calls = new ArrayList<>();
        @Override public void friend(AccountId a, AccountId b, boolean friends) { calls.add((friends ? "friend " : "unfriend ") + a + " " + b); }
        @Override public void block(AccountId a, AccountId b, boolean blocked) { calls.add((blocked ? "block " : "unblock ") + a + " " + b); }
        @Override public void message(AccountId target, String name, Optional<AccountId> from, String body) { calls.add("message " + target + " " + name + " " + body); }
        @Override public WebsiteSocial.Snapshot snapshot(AccountId account) { return new WebsiteSocial.Snapshot(Set.of(BO), Set.of(CY)); }
    }

    private static String reply(long accepted, String events) {
        return "{\"apiVersion\":1,\"accepted\":" + accepted + ",\"events\":[" + events + "],\"linked\":["
            + "{\"account\":\"" + AVA.value() + "\",\"member\":\"Ava\",\"messages\":\"everyone\",\"unreadMessages\":2,\"unreadNotifications\":1},"
            + "{\"account\":\"" + BO.value() + "\",\"member\":\"Bo\",\"messages\":\"friends\",\"unreadMessages\":0,\"unreadNotifications\":0}]}";
    }

    private static String event(long id, String type, AccountId actor, AccountId target, String body) {
        return "{\"id\":" + id + ",\"type\":\"" + type + "\",\"actor\":" + (actor == null ? "null" : "\"" + actor.value() + "\"")
            + ",\"target\":\"" + target.value() + "\",\"actorName\":\"Ava\",\"body\":\"" + body + "\"}";
    }

    @Test void changesFlowBothWaysAndSurviveRestarts(@TempDir File folder) throws Exception {
        var answer = new AtomicReference<>(reply(0, event(1, "friend", AVA, BO, "") + "," + event(2, "message", AVA, BO, "hello from the web")));
        var requests = new ArrayList<String>();
        var auth = new AtomicReference<String>();
        HttpServer website = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        website.createContext("/admin/api/social-sync", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = answer.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        website.start();
        var settings = new WebsiteSocial.Settings(URI.create("http://127.0.0.1:" + website.getAddress().getPort() + "/admin/api/social-sync"), 15);
        File state = new File(folder, "website-social-state.yml");
        try {
            var port = new FakePort();
            try (var sync = new WebsiteSocial(settings, TOKEN, port, state, Runnable::run, Logger.getAnonymousLogger())) {
                sync.queue("block", AVA, CY, "", false);
                assertEquals(2, sync.pollOnce());
                assertEquals("Bearer " + TOKEN, auth.get());
                var sent = JsonParser.parseString(requests.getFirst()).getAsJsonObject();
                assertEquals("block", sent.getAsJsonArray("events").get(0).getAsJsonObject().get("type").getAsString());
                assertEquals(List.of("friend " + AVA + " " + BO, "message " + BO + " Ava hello from the web"), port.calls);
                assertEquals(2, sync.acknowledged());
                assertEquals(1, sync.pending().size(), "Not accepted by the website yet, so kept");
                assertEquals(2, sync.linked(AVA).orElseThrow().unreadMessages());
                assertTrue(sync.linked(CY).isEmpty());
                // Now the website knows who's linked, changes involving unlinked players aren't queued.
                sync.queue("friend", AVA, CY, "", false);
                assertEquals(1, sync.pending().size());
                sync.queue("message", BO, AVA, "back at you", false);
            }
            // After a restart the unsent changes and the acknowledgement are still there.
            answer.set(reply(2, event(1, "friend", AVA, BO, "") + "," + event(3, "link", AVA, AVA, "")));
            try (var sync = new WebsiteSocial(settings, TOKEN, port, state, Runnable::run, Logger.getAnonymousLogger())) {
                assertEquals(2, sync.pending().size());
                assertEquals(1, sync.pollOnce(), "Only the change after the acknowledged one is applied");
                var sent = JsonParser.parseString(requests.getLast()).getAsJsonObject();
                assertEquals(2, sent.get("ack").getAsLong());
                assertEquals("back at you", sent.getAsJsonArray("events").get(1).getAsJsonObject().get("body").getAsString());
                assertEquals(List.of("friend " + BO, "block " + CY), sync.pending().stream()
                    .map(change -> change.type() + " " + change.target()).toList(), "Linking sends the game friends and blocks");
                assertTrue(sync.pending().stream().allMatch(WebsiteSocial.Change::quiet));
            }
        } finally {
            website.stop(0);
        }
    }

    @Test void malformedOrUnsafeResponsesAreRejected() {
        assertThrows(java.io.IOException.class, () -> WebsiteSocial.parse("not json"));
        assertThrows(java.io.IOException.class, () -> WebsiteSocial.parse("{\"apiVersion\":2,\"accepted\":0,\"events\":[],\"linked\":[]}"));
        assertThrows(java.io.IOException.class, () -> WebsiteSocial.parse(reply(0, event(2, "friend", AVA, BO, "") + "," + event(1, "friend", AVA, BO, ""))),
            "ids must increase");
        assertThrows(java.io.IOException.class, () -> WebsiteSocial.parse(reply(0, event(1, "op", AVA, BO, ""))), "unknown changes are refused");
        assertThrows(java.io.IOException.class, () -> WebsiteSocial.parse(reply(0, event(1, "message", AVA, BO, "x".repeat(600)))));
        assertThrows(IllegalArgumentException.class, () -> new WebsiteSocial.Settings(URI.create("http://aerborne.net/x"), 15));
        assertTrue(new WebsiteSocial.Linked("Bo", "friends", 0, 0).accepts(true));
        assertFalse(new WebsiteSocial.Linked("Bo", "friends", 0, 0).accepts(false));
        assertFalse(new WebsiteSocial.Linked("Bo", "nobody", 0, 0).accepts(true));
    }
}
