package net.nerdsmp.paper.anticheat;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Website administrators synced into the Watchdog panel. */
class PanelWebsiteSyncTest {
    // Made by the website's Werkzeug: generate_password_hash("website-pässword-1", "pbkdf2:sha256:600000", 16).
    private static final String WERKZEUG = "pbkdf2:sha256:600000$9CWP73zRo6VPeRuP$2d2aaae376cfcda2ad2a077970d2eb5c7dfbfbb55edbf4e16aab4a7833052548";
    private static final String PASSWORD = "website-pässword-1";
    private static final String TOKEN = "c".repeat(64);
    @TempDir Path directory;

    private static PanelCredentials local() {
        byte[] salt = new byte[32];
        new java.security.SecureRandom().nextBytes(salt);
        return new PanelCredentials("breakglass", Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(PanelCredentials.derive("local-only-password".toCharArray(), salt, 600_000)), 600_000);
    }

    @Test void werkzeugHashesVerifyExactlyAsTheWebsiteMadeThem() {
        var account = WebsiteAccount.parse("j0sh", WERKZEUG);
        assertTrue(account.verify(PASSWORD.toCharArray()));
        assertFalse(account.verify("website-password-1".toCharArray()));
        assertFalse(account.toString().contains("9CWP73"), "Hashes never appear in logs");
        for (String weak : List.of("pbkdf2:sha256:260000$9CWP73zRo6VPeRuP$" + "a".repeat(64), "scrypt:32768:8:1$abc$def",
            "pbkdf2:sha1:600000$9CWP73zRo6VPeRuP$" + "a".repeat(64), "pbkdf2:sha256:600000$bad salt$" + "a".repeat(64), "")) {
            assertThrows(IllegalArgumentException.class, () -> WebsiteAccount.parse("j0sh", weak), weak);
        }
        assertThrows(IllegalArgumentException.class, () -> WebsiteAccount.parse("bad name", WERKZEUG));
    }

    @Test void websiteAdminsExpireWhenTheWebsiteStopsConfirmingThem() {
        var clock = new AtomicLong(1_000);
        var accounts = new PanelAccounts(local(), 60_000, clock::get);
        assertNull(accounts.authenticate("j0sh", PASSWORD.toCharArray()), "Nobody from the website before the first sync");
        assertEquals("added j0sh", accounts.replace(List.of(WebsiteAccount.parse("j0sh", WERKZEUG))));
        assertEquals("j0sh", accounts.authenticate("j0sh", PASSWORD.toCharArray()));
        assertNull(accounts.authenticate("j0sh", "wrong".toCharArray()));
        assertEquals("breakglass", accounts.authenticate("breakglass", "local-only-password".toCharArray()));
        clock.addAndGet(61_000);
        assertNull(accounts.authenticate("j0sh", PASSWORD.toCharArray()), "Cached logins expire with the sync too");
        assertEquals("breakglass", accounts.authenticate("breakglass", "local-only-password".toCharArray()));
        accounts.replace(List.of(WebsiteAccount.parse("j0sh", WERKZEUG)));
        assertEquals("j0sh", accounts.authenticate("j0sh", PASSWORD.toCharArray()));
        assertEquals("removed j0sh", accounts.replace(List.of()));
        assertNull(accounts.authenticate("j0sh", PASSWORD.toCharArray()), "A removed admin is refused at once, even if cached");
    }

    @Test void theBreakGlassLoginCannotBeShadowedByAWebsiteAccount() {
        var accounts = new PanelAccounts(local(), 60_000, System::currentTimeMillis);
        accounts.replace(List.of(WebsiteAccount.parse("breakglass", WERKZEUG)));
        assertNull(accounts.authenticate("breakglass", PASSWORD.toCharArray()));
        assertTrue(accounts.websiteUsers().isEmpty());
    }

    @Test void syncFetchesWithTheTokenAndKeepsTheOldListOnFailure() throws Exception {
        var answer = new AtomicReference<String>("{\"apiVersion\":1,\"accounts\":[{\"username\":\"j0sh\",\"hash\":\"" + WERKZEUG + "\"}]}");
        var status = new AtomicReference<>(200);
        var seenAuth = new AtomicReference<String>();
        HttpServer website = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        website.createContext("/admin/api/panel-accounts", exchange -> {
            seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = answer.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        website.start();
        try {
            var accounts = new PanelAccounts(null, 60_000, System::currentTimeMillis);
            var settings = new PanelSync.Settings(URI.create("http://127.0.0.1:" + website.getAddress().getPort() + "/admin/api/panel-accounts"), 60, 24);
            try (var sync = new PanelSync(settings, TOKEN, accounts, Logger.getAnonymousLogger())) {
                assertEquals("added j0sh", sync.syncOnce());
                assertEquals("Bearer " + TOKEN, seenAuth.get());
                status.set(500);
                assertThrows(java.io.IOException.class, sync::syncOnce);
                status.set(200);
                answer.set("{\"apiVersion\":1,\"accounts\":[{\"username\":\"j0sh\",\"hash\":\"pbkdf2:sha256:1000$abcdefgh$" + "a".repeat(64) + "\"}]}");
                assertThrows(java.io.IOException.class, sync::syncOnce, "A weak hash rejects the whole update");
                assertEquals(java.util.Set.of("j0sh"), accounts.websiteUsers(), "Failures keep the last good list");
            }
        } finally {
            website.stop(0);
        }
    }

    @Test void syncUrlsMustBeHttpsExceptLocalhost() {
        assertThrows(IllegalArgumentException.class, () -> new PanelSync.Settings(URI.create("http://aerborne.net/admin/api/panel-accounts"), 60, 24));
        assertThrows(IllegalArgumentException.class, () -> new PanelSync.Settings(URI.create("https://user:pw@aerborne.net/x"), 60, 24));
        assertThrows(IllegalArgumentException.class, () -> new PanelSync.Settings(URI.create("https://aerborne.net/x"), 5, 24));
        assertDoesNotThrow(() -> new PanelSync.Settings(URI.create("https://aerborne.net/admin/api/panel-accounts"), 60, 24));
    }

    @Test void tokenFileIsCreatedOnceAndReused() throws Exception {
        Path file = directory.resolve("watchdog-website-token.txt");
        String first = PanelSync.loadOrCreateToken(file);
        assertTrue(first.matches("[a-f0-9]{64}"));
        assertEquals(first, PanelSync.loadOrCreateToken(file));
        Files.writeString(file, "not-a-token");
        assertThrows(java.io.IOException.class, () -> PanelSync.loadOrCreateToken(file));
    }

    @Test void websiteAdminsSignInToThePanelOverHttp() throws Exception {
        var accounts = new PanelAccounts(null, 60_000, System::currentTimeMillis);
        accounts.replace(List.of(WebsiteAccount.parse("j0sh", WERKZEUG)));
        try (var panel = WatchdogPanel.testListener(accounts, "127.0.0.1", List.of(), PanelBackend.offline())) {
            var client = HttpClient.newHttpClient();
            var good = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panel.port() + "/api/session"))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(("j0sh:" + PASSWORD).getBytes(StandardCharsets.UTF_8)))
                .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, good.statusCode());
            assertTrue(good.body().contains("\"user\":\"j0sh\""));
            var bad = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panel.port() + "/api/session"))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString("j0sh:nope".getBytes(StandardCharsets.UTF_8)))
                .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, bad.statusCode());
        }
    }

    @Test void syncSectionIsStrictAndOptional() {
        assertNull(WatchdogPanel.parseSync(null, false));
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("website-sync.enabled", false);
        assertNull(WatchdogPanel.parseSync(yaml.getConfigurationSection("website-sync"), true));
        yaml.set("website-sync.enabled", true);
        yaml.set("website-sync.url", "https://aerborne.net/admin/api/panel-accounts");
        assertNotNull(WatchdogPanel.parseSync(yaml.getConfigurationSection("website-sync"), true));
        yaml.set("website-sync.token", "inline secrets are not supported");
        assertThrows(IllegalArgumentException.class, () -> WatchdogPanel.parseSync(yaml.getConfigurationSection("website-sync"), true));
    }
}
