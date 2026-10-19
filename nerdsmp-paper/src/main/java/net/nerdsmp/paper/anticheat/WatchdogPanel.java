package net.nerdsmp.paper.anticheat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/**
 * Watchdog security console. HTTP threads read an immutable main-thread snapshot and never touch Bukkit entities;
 * actions are handed to the main thread by the backend. Every request needs an exact Host, HTTP Basic login and
 * stays inside the rate limit. Mutations additionally need same-origin JSON carrying the per-start CSRF token.
 */
public final class WatchdogPanel implements AutoCloseable {
    private static final int MAX_BODY = 8_192;
    private final HttpServer server;
    private final ThreadPoolExecutor executor;
    private final PanelAccounts accounts;
    private final PanelBackend backend;
    private PanelSync sync;
    private final boolean loopbackOnly;
    private final Set<String> allowedHosts;
    private final String csrf;
    private long windowStart = System.nanoTime();
    private int attempts;
    private long actionWindowStart = System.nanoTime();
    private int actions;

    private WatchdogPanel(PanelAccounts accounts, int port, InetAddress bind, Set<String> allowedHosts, PanelBackend backend) throws IOException {
        this.accounts = accounts;
        this.backend = backend;
        this.loopbackOnly = bind.isLoopbackAddress();
        this.allowedHosts = Set.copyOf(allowedHosts);
        byte[] token = new byte[32]; new SecureRandom().nextBytes(token);
        csrf = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        server = HttpServer.create(new InetSocketAddress(bind, port), 32);
        executor = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
            Thread thread = new Thread(runnable, "NerdSMP-Watchdog-HTTP"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor); server.createContext("/", this::request); server.start();
    }
    public static WatchdogPanel start(NerdSmpPlugin plugin) throws Exception {
        File file = new File(plugin.getDataFolder(), "security/watchdog-panel.yml");
        if (!file.exists()) {
            plugin.saveResource("security/watchdog-panel.yml", false);
            plugin.getLogger().info("Created security/watchdog-panel.yml. The panel is disabled until credentials are configured; copy your existing private settings file or use scripts/configure-watchdog-panel.ps1 -Rotate, then restart.");
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration(); yaml.load(file);
        var required = Set.of("schema", "enabled", "port", "username", "salt", "hash", "iterations");
        var supported = new HashSet<>(required); supported.addAll(Set.of("bind", "allowed-hosts", "website-sync"));
        if (!yaml.getKeys(false).containsAll(required) || !supported.containsAll(yaml.getKeys(false))
            || !yaml.isInt("schema") || yaml.getInt("schema") != 1 || !yaml.isBoolean("enabled")) throw new IllegalArgumentException("Invalid panel configuration");
        if (!yaml.getBoolean("enabled")) return null;
        if (!yaml.isInt("port") || yaml.getInt("port") < 1024 || yaml.getInt("port") > 65535 || !yaml.isInt("iterations")) throw new IllegalArgumentException("Invalid panel port or hash work factor");
        if (yaml.contains("bind") && !yaml.isString("bind")) throw new IllegalArgumentException("Panel bind must be an IPv4 address");
        if (yaml.contains("allowed-hosts") && (!yaml.isList("allowed-hosts")
            || yaml.getList("allowed-hosts").stream().anyMatch(value -> !(value instanceof String)))) throw new IllegalArgumentException("Panel allowed-hosts must be a list of HTTP authorities");
        InetAddress bind = parseBind(yaml.getString("bind", "127.0.0.1"));
        PanelSync.Settings syncSettings = parseSync(yaml.getConfigurationSection("website-sync"), yaml.contains("website-sync"));
        // With website sync on, the yml login becomes an optional break-glass account.
        boolean localBlank = yaml.getString("salt", "").isEmpty() && yaml.getString("hash", "").isEmpty();
        PanelCredentials local = syncSettings != null && localBlank ? null
            : new PanelCredentials(yaml.getString("username"), yaml.getString("salt"), yaml.getString("hash"), yaml.getInt("iterations"));
        PanelAccounts accounts = new PanelAccounts(local, syncSettings == null ? 0 : syncSettings.maxAgeHours() * 3_600_000L, System::currentTimeMillis);
        PanelData data = new PanelData(plugin);
        WatchdogPanel panel = new WatchdogPanel(accounts, yaml.getInt("port"), bind, parseAllowedHosts(yaml.getStringList("allowed-hosts")), data);
        try {
            if (syncSettings != null) {
                String token = PanelSync.loadOrCreateToken(file.toPath().resolveSibling("watchdog-website-token.txt"));
                panel.sync = new PanelSync(syncSettings, token, accounts, plugin.getLogger());
                panel.sync.start();
                plugin.getLogger().info("Watchdog website sync on: website admins can sign in to the panel. Copy security/watchdog-website-token.txt"
                    + " into the website's AERBORNE_PANEL_SYNC_TOKEN setting; the token is never printed.");
            }
            data.start();
            plugin.getLogger().info("Watchdog security console listening at http://" + bind.getHostAddress() + ":" + panel.port());
            if (!bind.isLoopbackAddress()) plugin.getLogger().warning("Watchdog HTTP is network-accessible and can moderate players. Use a trusted network, VPN or HTTPS reverse proxy; HTTP does not encrypt login credentials. No firewall rules were changed.");
            return panel;
        } catch (RuntimeException failure) { panel.close(); throw failure; }
    }
    /** Ephemeral loopback listener for authentication/HTTP regression tests, not connected to a game server. */
    public static WatchdogPanel testListener(PanelCredentials credentials) throws IOException { return testListener(credentials, "127.0.0.1", List.of()); }
    public static WatchdogPanel testListener(PanelCredentials credentials, String bind, List<String> allowedHosts) throws IOException {
        return testListener(credentials, bind, allowedHosts, PanelBackend.offline());
    }
    public static WatchdogPanel testListener(PanelCredentials credentials, String bind, List<String> allowedHosts, PanelBackend backend) throws IOException {
        return testListener(PanelAccounts.localOnly(credentials), bind, allowedHosts, backend);
    }
    public static WatchdogPanel testListener(PanelAccounts accounts, String bind, List<String> allowedHosts, PanelBackend backend) throws IOException {
        return new WatchdogPanel(accounts, 0, parseBind(bind), parseAllowedHosts(allowedHosts), backend);
    }
    /** Reads the optional website-sync section. Returns null when it's absent or switched off. */
    static PanelSync.Settings parseSync(org.bukkit.configuration.ConfigurationSection section, boolean present) {
        if (!present) return null;
        if (section == null) throw new IllegalArgumentException("Panel website-sync must be a section");
        var keys = section.getKeys(false);
        if (!Set.of("enabled", "url", "interval-seconds", "max-age-hours").containsAll(keys) || !section.isBoolean("enabled")) {
            throw new IllegalArgumentException("Invalid panel website-sync section");
        }
        if (!section.getBoolean("enabled")) return null;
        if (!section.isString("url")) throw new IllegalArgumentException("Panel website-sync needs a url");
        java.net.URI url;
        try { url = new java.net.URI(section.getString("url")); }
        catch (java.net.URISyntaxException invalid) { throw new IllegalArgumentException("Invalid panel website-sync url"); }
        return new PanelSync.Settings(url, section.getInt("interval-seconds", 60), section.getInt("max-age-hours", 24));
    }
    public int port() { return server.getAddress().getPort(); }
    public InetAddress bindAddress() { return server.getAddress().getAddress(); }
    static InetAddress parseBind(String value) throws IOException {
        if (value == null || !value.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) throw new IllegalArgumentException("Panel bind must be a literal IPv4 address");
        String[] parts = value.split("\\."); byte[] address = new byte[4];
        for (int i = 0; i < 4; i++) {
            int part = Integer.parseInt(parts[i]);
            if (part > 255) throw new IllegalArgumentException("Invalid panel IPv4 address");
            address[i] = (byte) part;
        }
        InetAddress bind = InetAddress.getByAddress(address);
        if (bind.isMulticastAddress()) throw new IllegalArgumentException("Panel cannot bind a multicast address");
        return bind;
    }
    static Set<String> parseAllowedHosts(List<String> values) {
        if (values.size() > 16) throw new IllegalArgumentException("At most 16 panel hosts may be configured");
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (value == null || value.length() > 259 || !value.matches("(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?::[0-9]{1,5})?")) throw new IllegalArgumentException("Invalid panel host; use a hostname or IPv4 address with optional port, not a URL");
            int colon = value.indexOf(':');
            if (colon >= 0 && (Integer.parseInt(value.substring(colon + 1)) < 1 || Integer.parseInt(value.substring(colon + 1)) > 65535)) throw new IllegalArgumentException("Invalid panel host port");
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(result);
    }
    private boolean acceptsHost(HttpExchange exchange) {
        var hosts = exchange.getRequestHeaders().get("Host");
        if (hosts == null || hosts.size() != 1) return false;
        String host = hosts.getFirst().toLowerCase(Locale.ROOT);
        // The accepted socket's local address is the actual destination interface, even on 0.0.0.0.
        String local = exchange.getLocalAddress().getAddress().getHostAddress() + ":" + port();
        return host.equals(local) || allowedHosts.contains(host)
            || (exchange.getRemoteAddress().getAddress().isLoopbackAddress() && host.equals("localhost:" + port()));
    }
    private synchronized boolean allowAttempt() {
        long now = System.nanoTime(); if (now - windowStart > TimeUnit.MINUTES.toNanos(1)) { windowStart = now; attempts = 0; }
        return ++attempts <= 240;
    }
    private synchronized boolean allowAction() {
        long now = System.nanoTime(); if (now - actionWindowStart > TimeUnit.MINUTES.toNanos(1)) { actionWindowStart = now; actions = 0; }
        return ++actions <= 40;
    }
    private void request(HttpExchange exchange) throws IOException {
        try (exchange) {
            var headers = exchange.getResponseHeaders(); headers.set("Cache-Control", "no-store"); headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY"); headers.set("Referrer-Policy", "no-referrer");
            headers.set("Cross-Origin-Opener-Policy", "same-origin"); headers.set("Cross-Origin-Resource-Policy", "same-origin");
            headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            headers.set("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
            // Host validation also prevents DNS rebinding; no CORS permissions are issued.
            if ((loopbackOnly && !exchange.getRemoteAddress().getAddress().isLoopbackAddress()) || !acceptsHost(exchange)) { send(exchange, 403, "text/plain", "Forbidden"); return; }
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("POST")) { headers.set("Allow", "GET, POST"); send(exchange, 405, "text/plain", "Method not allowed"); return; }
            if (!allowAttempt()) { headers.set("Retry-After", "60"); send(exchange, 429, "text/plain", "Too many requests"); return; }
            String user = authenticated(exchange.getRequestHeaders().getFirst("Authorization"));
            if (user == null) { headers.set("WWW-Authenticate", "Basic realm=\"Watchdog\", charset=\"UTF-8\""); send(exchange, 401, "text/plain", "Authentication required"); return; }
            if (exchange.getRequestURI().getRawQuery() != null) { send(exchange, 400, "text/plain", "Queries are not supported"); return; }
            String path = exchange.getRequestURI().getPath();
            if (method.equals("POST")) {
                if (!path.equals("/api/action")) { headers.set("Allow", "GET"); send(exchange, 405, "text/plain", "Method not allowed"); return; }
                action(exchange, user);
                return;
            }
            switch (path) {
                case "/" -> send(exchange, 200, "text/html", HTML);
                case "/panel.js" -> send(exchange, 200, "text/javascript", SCRIPT);
                case "/panel.css" -> send(exchange, 200, "text/css", CSS);
                case "/favicon.svg" -> send(exchange, 200, "image/svg+xml", ICON);
                case "/api/status" -> send(exchange, 200, "application/json", backend.status());
                case "/api/session" -> {
                    JsonObject session = new JsonObject(); session.addProperty("user", user); session.addProperty("csrf", csrf);
                    send(exchange, 200, "application/json", PanelBackend.json(session));
                }
                default -> {
                    if (path.startsWith("/api/player/")) {
                        UUID id;
                        try { id = UUID.fromString(path.substring("/api/player/".length())); }
                        catch (IllegalArgumentException invalid) { send(exchange, 404, "application/json", PanelBackend.Reply.error(404, "Unknown player.").json()); return; }
                        reply(exchange, backend.player(id));
                    } else send(exchange, 404, "text/plain", "Not found");
                }
            }
        } catch (RuntimeException error) { /* Closed/aborted requests never log credentials or headers. */ }
    }
    private void action(HttpExchange exchange, String user) throws IOException {
        var request = exchange.getRequestHeaders();
        // Browsers attach Basic credentials to cross-site requests, so mutations prove same-origin intent three ways.
        String site = request.getFirst("Sec-Fetch-Site");
        String origin = request.getFirst("Origin");
        String host = request.getFirst("Host");
        if ((site != null && !site.equals("same-origin"))
            || (origin != null && !origin.equalsIgnoreCase("http://" + host) && !origin.equalsIgnoreCase("https://" + host))) {
            reply(exchange, PanelBackend.Reply.error(403, "Cross-site requests are refused.")); return;
        }
        String type = request.getFirst("Content-Type");
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json")) { reply(exchange, PanelBackend.Reply.error(415, "Send JSON.")); return; }
        String token = request.getFirst("X-Watchdog-Token");
        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), csrf.getBytes(StandardCharsets.UTF_8))) {
            reply(exchange, PanelBackend.Reply.error(403, "Your session token expired. Reload the console.")); return;
        }
        if (!allowAction()) { exchange.getResponseHeaders().set("Retry-After", "60"); reply(exchange, PanelBackend.Reply.error(429, "Too many actions. Wait a minute.")); return; }
        byte[] body;
        try (InputStream input = exchange.getRequestBody()) { body = input.readNBytes(MAX_BODY + 1); }
        if (body.length > MAX_BODY) { reply(exchange, PanelBackend.Reply.error(413, "Request too large.")); return; }
        JsonObject json;
        try {
            var parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IllegalStateException();
            json = parsed.getAsJsonObject();
        } catch (RuntimeException invalid) { reply(exchange, PanelBackend.Reply.error(400, "Malformed JSON.")); return; }
        reply(exchange, backend.act(user, json));
    }
    /** Returns the verified username, or null. */
    private String authenticated(String header) {
        if (header == null || !header.startsWith("Basic ") || header.length() > 512) return null;
        byte[] decoded = null; char[] password = null;
        try {
            decoded = Base64.getDecoder().decode(header.substring(6)); String raw = new String(decoded, StandardCharsets.UTF_8);
            int split = raw.indexOf(':'); if (split < 1) return null;
            String user = raw.substring(0, split);
            password = raw.substring(split + 1).toCharArray(); return accounts.authenticate(user, password);
        } catch (IllegalArgumentException invalid) { return null; }
        finally { if (decoded != null) Arrays.fill(decoded, (byte)0); if (password != null) Arrays.fill(password, '\0'); }
    }
    private static void reply(HttpExchange exchange, PanelBackend.Reply reply) throws IOException {
        send(exchange, reply.status(), "application/json", reply.json());
    }
    private static void send(HttpExchange exchange, int status, String type, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
    }
    public static String quote(String value) {
        StringBuilder text = new StringBuilder("\""); for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') text.append('\\').append(c);
            else if (c < 32 || c == '<' || c == '>' || c == '&' || c == ' ' || c == ' ') text.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
            else text.append(c);
        } return text.append('"').toString();
    }
    @Override public void close() { if (sync != null) sync.close(); backend.close(); server.stop(0); executor.shutdownNow(); }
    private static String resource(String name) {
        try (InputStream stream = WatchdogPanel.class.getResourceAsStream("/panel/" + name)) {
            if (stream == null) throw new IllegalStateException("Missing panel resource " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new IllegalStateException("Unreadable panel resource " + name, failure); }
    }
    private static final String HTML = resource("index.html");
    private static final String SCRIPT = resource("panel.js");
    private static final String CSS = resource("panel.css");
    private static final String ICON = resource("favicon.svg");
}
