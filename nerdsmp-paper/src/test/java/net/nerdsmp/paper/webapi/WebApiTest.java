package net.nerdsmp.paper.webapi;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class WebApiTest {
    private static final String TOKEN = "a".repeat(64);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    @TempDir Path directory;

    private ApiSettings settings(Set<String> origins) throws Exception {
        return new ApiSettings(true, ApiSettings.address("127.0.0.1"), 0, Set.of(), origins, 5, 600, 120);
    }

    private ApiSnapshot snapshot() {
        return new ApiSnapshot(System.currentTimeMillis(),
            "{\"name\":\"Aerborne\",\"onlinePlayers\":3,\"ready\":true}",
            "{\"averageTickMs\":4.5,\"loadedEntities\":25}",
            "{\"items\":200,\"readOnly\":true}");
    }

    private HttpResponse<String> get(ApiHttpListener listener, String path, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + listener.port() + path))
            .timeout(Duration.ofSeconds(3)).GET();
        if (authorization != null) request.header("Authorization", authorization);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String raw(ApiHttpListener listener, String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", listener.port())) {
            socket.setSoTimeout(3_000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void newInstallIsExplicitlyNetworkBoundAndHasNoEmbeddedCredential() throws Exception {
        try (var resource = WebApi.class.getResourceAsStream("/security/web-api.yml")) {
            assertNotNull(resource);
            var yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(resource, StandardCharsets.UTF_8));
            var config = ApiSettings.read(yaml);
            assertTrue(config.enabled());
            assertEquals("0.0.0.0", config.bind().getHostAddress());
            assertEquals(9028, config.port());
            assertTrue(config.allowedHosts().isEmpty());
            assertTrue(config.corsOrigins().isEmpty());
            assertFalse(yaml.getKeys(true).stream().anyMatch(key -> key.contains("token") || key.contains("password")));
        }
    }

    @Test void configurationFailsClosedForTyposFutureSchemaAndInvalidLimits() throws Exception {
        var yaml = new YamlConfiguration();
        try (var resource = WebApi.class.getResourceAsStream("/security/web-api.yml")) {
            yaml.load(new InputStreamReader(resource, StandardCharsets.UTF_8));
        }
        yaml.set("enabled", false);
        assertFalse(ApiSettings.read(yaml).enabled(), "An operator's disabled setting is retained");
        yaml.set("schema", 2);
        assertThrows(IllegalArgumentException.class, () -> ApiSettings.read(yaml));
        yaml.set("schema", 1);
        yaml.set("allow-hosts", List.of("*"));
        assertThrows(IllegalArgumentException.class, () -> ApiSettings.read(yaml));
        yaml.set("allow-hosts", null);
        yaml.set("port", 0);
        assertThrows(IllegalArgumentException.class, () -> ApiSettings.read(yaml), "External ephemeral binds are not accepted");
        yaml.set("bind", "127.0.0.1");
        assertEquals(0, ApiSettings.read(yaml).port(), "Disposable loopback checks use conflict-free ephemeral ports");
        yaml.set("port", 9028);
        yaml.set("per-address-per-minute", 20_000);
        assertThrows(IllegalArgumentException.class, () -> ApiSettings.read(yaml));
    }

    @Test void addressesAndOriginsAreExactAndNeverResolveDns() throws Exception {
        assertEquals("0.0.0.0", ApiSettings.address("0.0.0.0").getHostAddress());
        for (String bind : List.of("localhost", "256.0.0.1", "127.00.0.1", "224.0.0.1", "255.255.255.255", "::1")) {
            assertThrows(IllegalArgumentException.class, () -> ApiSettings.address(bind), bind);
        }
        assertEquals(Set.of("api.example.com:443"), ApiSettings.hosts(List.of("API.example.com:443")));
        for (String host : List.of("*", "https://api.example.com", "a.example:0", "a.example:99999", "a.example/path")) {
            assertThrows(IllegalArgumentException.class, () -> ApiSettings.hosts(List.of(host)), host);
        }
        assertEquals(Set.of("https://example.com"), ApiSettings.origins(List.of("https://example.com")));
        assertEquals(Set.of("http://localhost:3000"), ApiSettings.origins(List.of("http://localhost:3000")));
        for (String origin : List.of("null", "*", "http://example.com", "https://example.com/", "https://a.example/path",
            "https://user:pass@example.com", "https://example.com?x=1", "https://example.com#x")) {
            assertThrows(IllegalArgumentException.class, () -> ApiSettings.origins(List.of(origin)), origin);
        }
    }

    @Test void tokenIsGeneratedOncePrivatelyAndExistingOrMalformedFilesAreNotOverwritten() throws Exception {
        Path file = directory.resolve("web-api-token.txt");
        ApiToken first = ApiToken.loadOrCreate(file);
        String content = Files.readString(file).strip();
        assertTrue(content.matches("[a-f0-9]{64}"));
        assertTrue(first.verify("Bearer " + content));
        assertTrue(ApiToken.loadOrCreate(file).verify("Bearer " + content));
        assertEquals(content, Files.readString(file).strip());
        assertFalse(first.verify("Bearer " + "b".repeat(64)));
        assertFalse(first.verify("Basic " + content));
        assertFalse(first.verify("Bearer " + content + " "));
        assertFalse(first.verify(null));
        assertFalse(first.toString().contains(content));
        var posix = Files.getFileAttributeView(file, java.nio.file.attribute.PosixFileAttributeView.class);
        if (posix != null) assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), posix.readAttributes().permissions());
        Files.writeString(file, "do-not-replace");
        assertThrows(IllegalArgumentException.class, () -> ApiToken.loadOrCreate(file));
        assertEquals("do-not-replace", Files.readString(file));
    }

    @Test void tokenRejectsDirectoriesAndOversizedFiles() throws Exception {
        Path folder = Files.createDirectory(directory.resolve("folder"));
        assertThrows(IOException.class, () -> ApiToken.loadOrCreate(folder));
        Path large = directory.resolve("large");
        Files.writeString(large, "a".repeat(100));
        assertThrows(IOException.class, () -> ApiToken.loadOrCreate(large));
        assertEquals(100, Files.size(large));
    }

    @Test void boundedRateTableExpiresAndBothGlobalAndAddressLimitsApply() {
        AtomicLong clock = new AtomicLong(1);
        var limits = new ApiRateLimit(5, 2, clock::get);
        assertTrue(limits.acquire("one"));
        assertTrue(limits.acquire("one"));
        assertFalse(limits.acquire("one"));
        assertTrue(limits.acquire("two"));
        assertTrue(limits.acquire("three"));
        assertFalse(limits.acquire("four"));
        clock.addAndGet(TimeUnit.MINUTES.toNanos(1));
        assertTrue(limits.acquire("one"));
        assertEquals(1, limits.trackedAddresses());
        var bounded = new ApiRateLimit(10_000, 10_000, clock::get);
        for (int index = 0; index < 1_024; index++) assertTrue(bounded.acquire("address-" + index));
        assertFalse(bounded.acquire("overflow"));
        assertEquals(1_024, bounded.trackedAddresses());
    }

    @Test void publicStatusAndHealthAreSeparateFromBearerProtectedTelemetryAndCatalog() throws Exception {
        try (var listener = new ApiHttpListener(settings(Set.of()), ApiToken.of(TOKEN), this::snapshot)) {
            for (String path : List.of("/api/v1/status", "/api/v1/health")) {
                var response = get(listener, path, null);
                assertEquals(200, response.statusCode());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
                assertEquals("close", response.headers().firstValue("Connection").orElseThrow());
                assertFalse(response.body().contains("averageTickMs"));
                assertFalse(response.body().contains(TOKEN));
                assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            }
            for (String path : List.of("/api/v1/telemetry", "/api/v1/catalog")) {
                assertEquals(401, get(listener, path, null).statusCode());
                assertEquals(401, get(listener, path, "Bearer " + "b".repeat(64)).statusCode());
                var response = get(listener, path, "Bearer " + TOKEN);
                assertEquals(200, response.statusCode());
                assertFalse(response.body().contains(TOKEN));
            }
            assertTrue(get(listener, "/api/v1/telemetry", "Bearer " + TOKEN).body().contains("averageTickMs"));
        }
    }

    @Test void noMutationQueryEncodedPathOrCredentialQueryRouteExists() throws Exception {
        try (var listener = new ApiHttpListener(settings(Set.of()), ApiToken.of(TOKEN), this::snapshot)) {
            for (String path : List.of("/api/v1/status?token=" + TOKEN, "/api/v1/%73tatus")) {
                assertEquals(400, get(listener, path, "Bearer " + TOKEN).statusCode());
            }
            for (String path : List.of("/", "/api/v1/player/anyone", "/api/v1/command", "/api/v1/status/", "/api/v1/../status")) {
                assertEquals(404, get(listener, path, "Bearer " + TOKEN).statusCode());
            }
            for (String method : List.of("POST", "PUT", "DELETE", "HEAD", "TRACE")) {
                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + listener.port() + "/api/v1/status"))
                    .timeout(Duration.ofSeconds(3)).method(method, HttpRequest.BodyPublishers.noBody()).build();
                assertEquals(405, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode(), method);
            }
        }
    }

    @Test void hostsAndDuplicateCredentialsAreRejectedAndRequestBodiesAreNeverRead() throws Exception {
        try (var listener = new ApiHttpListener(settings(Set.of()), ApiToken.of(TOKEN), this::snapshot)) {
            String host = "127.0.0.1:" + listener.port();
            assertTrue(raw(listener, "GET /api/v1/status HTTP/1.1\r\nHost: evil.example\r\n\r\n").startsWith("HTTP/1.1 403"));
            assertTrue(raw(listener, "GET /api/v1/status HTTP/1.1\r\nHost: " + host + "\r\nHost: " + host + "\r\n\r\n")
                .startsWith("HTTP/1.1 403"));
            assertTrue(raw(listener, "GET /api/v1/telemetry HTTP/1.1\r\nHost: " + host + "\r\nAuthorization: Bearer "
                + TOKEN + "\r\nAuthorization: Bearer " + TOKEN + "\r\n\r\n").startsWith("HTTP/1.1 401"));
            assertTrue(raw(listener, "GET /api/v1/status HTTP/1.1\r\nHost: " + host
                + "\r\nContent-Length: 900000000\r\n\r\n").startsWith("HTTP/1.1 413"), "Do not wait for even one body byte");
            String huge = raw(listener, "GET /api/v1/status HTTP/1.1\r\nHost: " + host + "\r\nX-Large: " + "x".repeat(9_000) + "\r\n\r\n");
            assertTrue(huge.startsWith("HTTP/1.1 400"), huge);
        }
    }

    @Test void onlyExplicitCorsOriginsCanReadOrPreflightAndCredentialsAreNeverAllowed() throws Exception {
        try (var listener = new ApiHttpListener(settings(Set.of("https://site.example")), ApiToken.of(TOKEN), this::snapshot)) {
            URI uri = URI.create("http://127.0.0.1:" + listener.port() + "/api/v1/telemetry");
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3));
            var denied = client.send(request.copy().header("Origin", "https://evil.example")
                .header("Authorization", "Bearer " + TOKEN).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, denied.statusCode());
            assertTrue(denied.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            var preflight = client.send(request.copy().header("Origin", "https://site.example")
                .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "Authorization")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(204, preflight.statusCode());
            assertEquals("https://site.example", preflight.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
            assertTrue(preflight.headers().firstValue("Access-Control-Allow-Credentials").isEmpty());
            var allowed = client.send(request.copy().header("Origin", "https://site.example")
                .header("Authorization", "Bearer " + TOKEN).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, allowed.statusCode());
            var wrongMethod = client.send(request.copy().header("Origin", "https://site.example")
                .header("Access-Control-Request-Method", "POST").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(403, wrongMethod.statusCode());
        }
    }

    @Test void httpWorkersOnlyReadImmutableBoundedSnapshotsAndStaleDataReturnsUnavailable() throws Exception {
        var threads = new CopyOnWriteArrayList<String>();
        try (var listener = new ApiHttpListener(settings(Set.of()), ApiToken.of(TOKEN), () -> {
            threads.add(Thread.currentThread().getName());
            return snapshot();
        })) {
            assertEquals(200, get(listener, "/api/v1/status", null).statusCode());
            assertEquals(1, threads.size());
            assertTrue(threads.getFirst().contains("Aerborne-API-HTTP"));
        }
        var stale = new ApiSnapshot(0, "{}", "{}", "{}");
        try (var listener = new ApiHttpListener(settings(Set.of()), ApiToken.of(TOKEN), () -> stale)) {
            assertEquals(503, get(listener, "/api/v1/health", null).statusCode());
            assertEquals(401, get(listener, "/api/v1/telemetry", null).statusCode());
            assertEquals(503, get(listener, "/api/v1/telemetry", "Bearer " + TOKEN).statusCode());
        }
        assertThrows(IllegalArgumentException.class, () -> new ApiSnapshot(1, "x".repeat(32_769), "{}", "{}"));
    }

    @Test void requestRateLimitsIncludeFailedAuthentication() throws Exception {
        var limited = new ApiSettings(true, ApiSettings.address("127.0.0.1"), 0, Set.of(), Set.of(), 5, 10, 5);
        try (var listener = new ApiHttpListener(limited, ApiToken.of(TOKEN), this::snapshot)) {
            for (int attempt = 0; attempt < 5; attempt++) assertEquals(401, get(listener, "/api/v1/telemetry", null).statusCode());
            var rejected = get(listener, "/api/v1/status", null);
            assertEquals(429, rejected.statusCode());
            assertEquals("60", rejected.headers().firstValue("Retry-After").orElseThrow());
        }
    }

    @Test void slowPartialHeadersExpireEvenWithoutSendingTheRemainingBytes() throws Exception {
        try (var listener = new ApiHttpListener(settings(Set.of()), ApiToken.of(TOKEN), this::snapshot);
             var socket = new Socket("127.0.0.1", listener.port())) {
            socket.setSoTimeout(7_000);
            long start = System.nanoTime();
            socket.getOutputStream().write("GET /api/v1/status HTTP/1.1\r\nHo".getBytes(StandardCharsets.US_ASCII));
            assertEquals(-1, socket.getInputStream().read());
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue(elapsed >= 3_000 && elapsed < 7_000, "Absolute socket lifetime: " + elapsed);
        }
    }

    @Test void connectionAdmissionIsBoundedAndCloseReleasesTheListeningPort() throws Exception {
        var listener = new ApiHttpListener(settings(Set.of()), ApiToken.of(TOKEN), this::snapshot);
        List<Socket> sockets = new ArrayList<>();
        int port = listener.port();
        try {
            for (int count = 0; count < 80; count++) {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("127.0.0.1", port), 1_000);
                sockets.add(socket);
            }
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (listener.connections() < ApiHttpListener.MAX_CONNECTIONS && System.nanoTime() < until) Thread.sleep(5);
            assertEquals(ApiHttpListener.MAX_CONNECTIONS, listener.connections());
            sockets.getLast().setSoTimeout(1_000);
            assertEquals(-1, sockets.getLast().getInputStream().read(), "Excess sockets are closed, not queued indefinitely");
        } finally {
            sockets.forEach(socket -> { try { socket.close(); } catch (IOException ignored) { } });
            listener.close();
            listener.close();
        }
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        ApiHttpListener rebound = null;
        while (rebound == null && System.nanoTime() < until) {
            try { rebound = new ApiHttpListener(new ApiSettings(true, ApiSettings.address("127.0.0.1"), port,
                Set.of(), Set.of(), 5, 600, 120), ApiToken.of(TOKEN), this::snapshot); }
            catch (IOException releasedSoon) { Thread.sleep(20); }
        }
        assertNotNull(rebound);
        rebound.close();
    }
}
