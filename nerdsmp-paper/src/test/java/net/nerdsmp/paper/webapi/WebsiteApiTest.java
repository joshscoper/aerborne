package net.nerdsmp.paper.webapi;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.leaderboard.LeaderboardService;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Leaderboards, public profiles and website link codes added for the Aerborne website. */
class WebsiteApiTest {
    private static final String TOKEN = "a".repeat(64);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private static ApiSettings settings() throws Exception {
        return new ApiSettings(true, ApiSettings.address("127.0.0.1"), 0, Set.of(), Set.of(), 5, 600, 120);
    }

    private HttpResponse<String> get(ApiHttpListener listener, String path, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + listener.port() + path))
            .timeout(Duration.ofSeconds(3)).GET();
        if (authorization != null) request.header("Authorization", authorization);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static ApiSnapshot snapshot() {
        var amy = AccountId.random();
        var zed = AccountId.random();
        var rankings = Map.of(
            LeaderboardService.Board.MARKS, List.of(
                new LeaderboardService.Entry(amy, "Amy", Map.of(LeaderboardService.Board.MARKS, 900L)),
                new LeaderboardService.Entry(zed, "Zed_2", Map.of(LeaderboardService.Board.MARKS, 10L))));
        var profiles = Map.of(
            amy, new LeaderboardService.Profile("Amy", 12, "Scout", Map.of(Skill.MINING, 7, Skill.FISHING, 3)),
            zed, new LeaderboardService.Profile("Zed_2", 1, "Wanderer", Map.of()));
        Instant updated = Instant.parse("2026-10-18T12:00:00Z");
        return new ApiSnapshot(System.currentTimeMillis(), "{\"ready\":true}", "{}", "{}",
            ApiSnapshots.leaderboardJson(rankings, updated), ApiSnapshots.profileJson(rankings, profiles, updated));
    }

    @Test void leaderboardsAndProfilesArePublicAndBounded() throws Exception {
        try (var listener = new ApiHttpListener(settings(), ApiToken.of(TOKEN), WebsiteApiTest::snapshot)) {
            var boards = get(listener, "/api/v1/leaderboards", null);
            assertEquals(200, boards.statusCode());
            assertTrue(boards.body().contains("\"marks\":{\"players\":2,\"top\":[{\"rank\":1,\"name\":\"Amy\",\"score\":900}"));
            assertTrue(boards.body().contains("\"cooking\":{\"players\":0,\"top\":[]}"));

            var amy = get(listener, "/api/v1/players/aMy", null);
            assertEquals(200, amy.statusCode());
            assertTrue(amy.body().contains("\"adventurer\":{\"level\":12,\"rank\":\"Scout\"}"));
            assertTrue(amy.body().contains("\"skills\":{\"mining\":7,\"fishing\":3}"));
            assertTrue(amy.body().contains("\"marks\":{\"rank\":1,\"score\":900}"));

            assertEquals(404, get(listener, "/api/v1/players/Nobody", null).statusCode());
            for (String bad : List.of("/api/v1/players/ab", "/api/v1/players/has-dash", "/api/v1/players/", "/api/v1/players/Amy/x")) {
                assertEquals(404, get(listener, bad, null).statusCode(), bad);
            }
        }
    }

    @Test void linkCodesNeedTheTokenAndExpire() throws Exception {
        var clock = new AtomicLong(1_000);
        var links = new WebLinkCodes(clock::get);
        UUID player = UUID.randomUUID();
        var issued = links.issue("acct-1", player, "Amy");
        assertTrue(WebLinkCodes.valid(issued.code()));
        try (var listener = new ApiHttpListener(settings(), ApiToken.of(TOKEN), WebsiteApiTest::snapshot, links)) {
            String path = "/api/v1/links/" + issued.code();
            assertEquals(401, get(listener, path, null).statusCode());
            var found = get(listener, path, "Bearer " + TOKEN);
            assertEquals(200, found.statusCode());
            assertTrue(found.body().contains("\"playerId\":\"" + player + "\""));
            assertTrue(found.body().contains("\"name\":\"Amy\""));
            assertEquals(404, get(listener, "/api/v1/links/ABCDEFGH", "Bearer " + TOKEN).statusCode());
            assertEquals(404, get(listener, "/api/v1/links/abcdefgh", "Bearer " + TOKEN).statusCode());
            clock.addAndGet(WebLinkCodes.TTL_MILLIS);
            assertEquals(404, get(listener, path, "Bearer " + TOKEN).statusCode());
        }
    }

    @Test void aNewCodeReplacesThePlayersOldOne() {
        var clock = new AtomicLong(0);
        var links = new WebLinkCodes(clock::get);
        UUID player = UUID.randomUUID();
        var first = links.issue("acct", player, "Amy");
        var second = links.issue("acct", player, "Amy");
        assertTrue(links.find(first.code()).isEmpty() || first.code().equals(second.code()));
        assertTrue(links.find(second.code()).isPresent());
        assertEquals(1, links.active());
        for (String bad : new String[]{null, "", "ABCDEFG", "ABCDEFGHI", "ABCDEFG0", "ABCDEFGI", "abcdefgh"}) {
            assertFalse(WebLinkCodes.valid(bad), String.valueOf(bad));
        }
    }
}
