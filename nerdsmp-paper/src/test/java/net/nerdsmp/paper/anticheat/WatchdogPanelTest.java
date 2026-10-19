package net.nerdsmp.paper.anticheat;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WatchdogPanelTest {
    @Test void freshInstallTemplateIsPackagedDisabledAndContainsNoUsableCredentials() throws Exception {
        try (var stream = WatchdogPanel.class.getResourceAsStream("/security/watchdog-panel.yml")) {
            assertNotNull(stream, "The uploaded jar must include its first-run configuration");
            var yaml = new org.bukkit.configuration.file.YamlConfiguration();
            yaml.load(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
            assertEquals(1, yaml.getInt("schema"));
            assertFalse(yaml.getBoolean("enabled", true));
            assertEquals("127.0.0.1", yaml.getString("bind"));
            assertEquals(9029, yaml.getInt("port"));
            assertEquals("", yaml.getString("salt"));
            assertEquals("", yaml.getString("hash"));
            assertEquals(600_000, yaml.getInt("iterations"));
            assertTrue(yaml.getStringList("allowed-hosts").isEmpty());
            assertThrows(IllegalArgumentException.class, () -> new PanelCredentials(yaml.getString("username"), yaml.getString("salt"), yaml.getString("hash"), yaml.getInt("iterations")));
        }
    }
    private PanelCredentials credentials() {
        byte[] salt = new byte[32]; new java.security.SecureRandom().nextBytes(salt);
        return new PanelCredentials("tester", Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(PanelCredentials.derive("test-only-password".toCharArray(), salt, 600_000)), 600_000);
    }
    private String auth(String password) { return "Basic " + Base64.getEncoder().encodeToString(("tester:" + password).getBytes(StandardCharsets.UTF_8)); }
    @Test void passwordHashesAreBoundedAndDoNotAcceptWrongCredentials() {
        var c = credentials(); assertTrue(c.verify("tester", "test-only-password".toCharArray()));
        assertFalse(c.verify("tester", "incorrect".toCharArray())); assertFalse(c.verify("different", "test-only-password".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> new PanelCredentials(c.username(), c.salt(), c.hash(), 1));
    }
    @Test void everyRouteRequiresAuthAndQueriesAreRefused() throws Exception {
        try (var panel = WatchdogPanel.testListener(credentials())) {
            var client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
            String root = "http://127.0.0.1:" + panel.port();
            for (String path : List.of("/", "/api/status", "/api/session", "/panel.js", "/panel.css", "/favicon.svg")) {
                var denied = client.send(HttpRequest.newBuilder(URI.create(root + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(401, denied.statusCode()); assertTrue(denied.headers().firstValue("WWW-Authenticate").isPresent());
                var valid = client.send(HttpRequest.newBuilder(URI.create(root + path)).header("Authorization", auth("test-only-password")).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, valid.statusCode(), path); assertEquals("no-store", valid.headers().firstValue("Cache-Control").orElseThrow());
                assertTrue(valid.headers().firstValue("Content-Security-Policy").orElseThrow().contains("frame-ancestors 'none'"));
                assertFalse(valid.body().contains("test-only-password"));
                if (path.equals("/")) assertTrue(valid.body().contains("<title>Aerborne | Staff Console</title>"));
                if (path.equals("/panel.css")) assertTrue(valid.body().contains("--blue: #77C8F5"));
                if (path.equals("/favicon.svg")) assertTrue(valid.body().contains("Aerborne staff console"));
            }
            assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(root)).header("Authorization", auth("wrong")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(root + "/api/action")).POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(405, client.send(HttpRequest.newBuilder(URI.create(root + "/api/status")).header("Authorization", auth("test-only-password")).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(405, client.send(HttpRequest.newBuilder(URI.create(root + "/api/action")).header("Authorization", auth("test-only-password")).DELETE().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(400, client.send(HttpRequest.newBuilder(URI.create(root + "/?command=op")).header("Authorization", auth("test-only-password")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(root + "/api/player/not-a-uuid")).header("Authorization", auth("test-only-password")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }
    @Test void actionsRequireSessionTokenSameOriginAndJsonAndAreAttributedToTheLogin() throws Exception {
        var calls = new java.util.concurrent.CopyOnWriteArrayList<String>();
        PanelBackend backend = new PanelBackend() {
            @Override public String status() { return "{\"status\":\"online\"}"; }
            @Override public Reply player(UUID id) { return Reply.error(404, "none"); }
            @Override public Reply act(String actor, com.google.gson.JsonObject request) {
                calls.add(actor + ":" + request.get("action").getAsString());
                var body = new com.google.gson.JsonObject(); body.addProperty("message", "<b>done</b>"); return Reply.ok(body);
            }
        };
        try (var panel = WatchdogPanel.testListener(credentials(), "127.0.0.1", List.of(), backend)) {
            var client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
            String root = "http://127.0.0.1:" + panel.port();
            var session = com.google.gson.JsonParser.parseString(client.send(HttpRequest.newBuilder(URI.create(root + "/api/session"))
                .header("Authorization", auth("test-only-password")).GET().build(), HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
            assertEquals("tester", session.get("user").getAsString());
            String token = session.get("csrf").getAsString();
            assertTrue(token.length() >= 40);
            String body = "{\"action\":\"kick\",\"target\":\"" + UUID.randomUUID() + "\"}";
            java.util.function.Function<HttpRequest.Builder, Integer> post = builder -> {
                try { return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()).statusCode(); }
                catch (Exception failure) { throw new RuntimeException(failure); }
            };
            HttpRequest.Builder base = HttpRequest.newBuilder(URI.create(root + "/api/action")).header("Authorization", auth("test-only-password"));
            assertEquals(403, post.apply(base.copy().header("Content-Type", "application/json")), "missing token");
            assertEquals(403, post.apply(base.copy().header("Content-Type", "application/json").header("X-Watchdog-Token", token + "x")), "wrong token");
            assertEquals(415, post.apply(base.copy().header("Content-Type", "text/plain").header("X-Watchdog-Token", token)), "form/text bodies");
            assertEquals(403, post.apply(base.copy().header("Content-Type", "application/json").header("X-Watchdog-Token", token).header("Origin", "http://evil.example")), "foreign origin");
            assertEquals(403, post.apply(base.copy().header("Content-Type", "application/json").header("X-Watchdog-Token", token).header("Sec-Fetch-Site", "cross-site")), "cross-site fetch");
            assertEquals(401, post.apply(HttpRequest.newBuilder(URI.create(root + "/api/action")).header("Authorization", auth("wrong")).header("Content-Type", "application/json").header("X-Watchdog-Token", token)), "bad login");
            assertTrue(calls.isEmpty(), "No refused request may reach the backend");
            var accepted = client.send(base.copy().header("Content-Type", "application/json").header("X-Watchdog-Token", token)
                .header("Origin", "http://127.0.0.1:" + panel.port()).header("Sec-Fetch-Site", "same-origin")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, accepted.statusCode());
            assertFalse(accepted.body().contains("<b>"), "JSON output must escape markup characters");
            assertEquals(List.of("tester:kick"), calls);
            var malformed = client.send(base.copy().header("Content-Type", "application/json").header("X-Watchdog-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString("[1,2")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, malformed.statusCode());
            var huge = client.send(base.copy().header("Content-Type", "application/json").header("X-Watchdog-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString("{\"a\":\"" + "x".repeat(9_000) + "\"}")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(413, huge.statusCode());
            assertEquals(1, calls.size());
        }
    }
    @Test void renderedJsonCannotInjectMarkupOrControlCharacters() {
        assertEquals("\"\\u003cscript\\u003e\\\"\\\\\\u000a\"", WatchdogPanel.quote("<script>\"\\\n"));
    }
    @Test void unexpectedHostNamesAreDeniedBeforeAnyCredentialsAreProcessed() throws Exception {
        try(var panel=WatchdogPanel.testListener(credentials()); var socket=new java.net.Socket("127.0.0.1",panel.port())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write("GET /api/status HTTP/1.1\r\nHost: malicious.example\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            var first=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII)).readLine();
            assertTrue(first.contains("403"));
        }
    }
    @Test void wildcardListenerAcceptsInterfaceAddressesButStillRequiresLogin() throws Exception {
        try (var panel = WatchdogPanel.testListener(credentials(), "0.0.0.0", List.of())) {
            assertTrue(panel.bindAddress().isAnyLocalAddress());
            var client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
            Set<String> addresses = new HashSet<>(Set.of("127.0.0.1"));
            for (var network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (network.isUp()) for (var address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isAnyLocalAddress() && !address.isMulticastAddress()) addresses.add(address.getHostAddress());
                }
            }
            for (String address : addresses) {
                var uri = URI.create("http://" + address + ":" + panel.port() + "/api/status");
                assertEquals(401, client.send(HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode(), address);
                assertEquals(200, client.send(HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(5)).header("Authorization", auth("test-only-password")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode(), address);
            }
            assertEquals(403, rawStatus(panel.port(), "malicious.example:" + panel.port(), null));
        }
    }
    @Test void explicitHostAliasesAreExactAndDoNotBypassAuthentication() throws Exception {
        try (var panel = WatchdogPanel.testListener(credentials(), "0.0.0.0", List.of("staff.example.com", "staff.example.com:9029"))) {
            assertEquals(401, rawStatus(panel.port(), "STAFF.EXAMPLE.COM", null));
            assertEquals(200, rawStatus(panel.port(), "staff.example.com:9029", auth("test-only-password")));
            assertEquals(401, rawStatus(panel.port(), "staff.example.com:9029", auth("wrong")));
            assertEquals(403, rawStatus(panel.port(), "staff.example.com.evil.test:9029", null));
            assertEquals(403, rawStatus(panel.port(), "staff.example.com:9030", null));
        }
    }
    @Test void bindAddressesAreLiteralIpv4WithoutDnsLookups() throws Exception {
        assertTrue(WatchdogPanel.parseBind("127.0.0.1").isLoopbackAddress());
        assertTrue(WatchdogPanel.parseBind("0.0.0.0").isAnyLocalAddress());
        assertEquals("192.168.1.10", WatchdogPanel.parseBind("192.168.1.10").getHostAddress());
        for (String invalid : List.of("localhost", "https://example.com", "0", "127.1", "127.00.0.1", "256.0.0.1", "224.0.0.1", "0.0.0.0:9029", " 0.0.0.0", "::")) {
            assertThrows(IllegalArgumentException.class, () -> WatchdogPanel.parseBind(invalid), invalid);
        }
    }
    @Test void allowedHostsCannotContainWildcardsUrlsCredentialsOrHeaderInjection() {
        assertEquals(Set.of("staff.example.com:9029"), WatchdogPanel.parseAllowedHosts(List.of("Staff.Example.com:9029")));
        for (String invalid : List.of("*", "*.example.com", "https://example.com", "user@example.com", "example.com/path", "example.com:0", "example.com:65536", "example.com\r\nHost: evil", "example.com ", "example.com,evil.test")) {
            assertThrows(IllegalArgumentException.class, () -> WatchdogPanel.parseAllowedHosts(List.of(invalid)), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> WatchdogPanel.parseAllowedHosts(Collections.nCopies(17, "example.com")));
    }
    private int rawStatus(int port, String host, String authorization) throws Exception {
        try (var socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            String request = "GET /api/status HTTP/1.1\r\nHost: " + host + "\r\n"
                + (authorization == null ? "" : "Authorization: " + authorization + "\r\n") + "Connection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            var line = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            return Integer.parseInt(line.split(" ")[1]);
        }
    }
}
