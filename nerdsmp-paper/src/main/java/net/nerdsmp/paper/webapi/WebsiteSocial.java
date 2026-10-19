package net.nerdsmp.paper.webapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Keeps friends, blocks and messages in step with the Aerborne website for players who linked their account.
 *
 * <p>On a timer the server POSTs its own social changes to the website and receives the website's in return.
 * Both sides number their changes and acknowledge what they applied, and the server's pending changes are kept
 * on disk, so nothing is lost when either side restarts. Changes received from the website are applied without
 * being sent back. Players without a linked website account are never sent anywhere.
 */
public final class WebsiteSocial implements AutoCloseable {
    static final int MAX_BYTES = 4 * 1024 * 1024;
    static final int MAX_BATCH = 200;
    static final int MAX_PENDING = 2000;
    static final int MAX_MESSAGE = 512;
    private static final Set<String> TYPES = Set.of("friend", "unfriend", "block", "unblock", "message");

    public record Settings(URI url, int intervalSeconds) {
        public Settings {
            String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
            String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
            boolean local = host.equals("localhost") || host.equals("127.0.0.1");
            if (!(scheme.equals("https") || local && scheme.equals("http")) || url.getRawUserInfo() != null
                || url.getRawQuery() != null || url.getRawFragment() != null) {
                throw new IllegalArgumentException("website-social url must be an https:// address (http only for localhost)");
            }
            if (intervalSeconds < 5 || intervalSeconds > 3600) throw new IllegalArgumentException("website-social interval-seconds must be 5-3600");
        }
    }

    /** What the website knows about a linked player. */
    public record Linked(String member, String messages, int unreadMessages, int unreadNotifications) {
        public boolean accepts(boolean friends) { return messages.equals("everyone") || messages.equals("friends") && friends; }
    }

    /** A player's game friends and blocks, sent to the website when they link. */
    public record Snapshot(Set<AccountId> friends, Set<AccountId> ignored) {}

    /** The game side. Every call runs on the main thread, and none of them may queue changes back to the website. */
    public interface Port {
        void friend(AccountId first, AccountId second, boolean friends);
        void block(AccountId actor, AccountId target, boolean blocked);
        void message(AccountId target, String fromName, Optional<AccountId> from, String body);
        Snapshot snapshot(AccountId account);
    }

    record Change(long seq, String type, AccountId actor, AccountId target, String body, boolean quiet) {}

    record Incoming(long id, String type, Optional<AccountId> actor, AccountId target, String actorName, String body) {}

    private final Settings settings;
    private final String token;
    private final Port port;
    private final File stateFile;
    private final Executor mainThread;
    private final Logger logger;
    private final HttpClient client;
    private final ScheduledExecutorService timer;
    private final List<Change> pending = new ArrayList<>();
    private volatile Map<AccountId, Linked> linked = Map.of();
    private long acknowledged;
    private long nextSeq = 1;
    private boolean failing;

    WebsiteSocial(Settings settings, String token, Port port, File stateFile, Executor mainThread, Logger logger) throws IOException {
        if (token == null || !token.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid website social token");
        this.settings = settings;
        this.token = token;
        this.port = port;
        this.stateFile = stateFile;
        this.mainThread = mainThread;
        this.logger = logger;
        load();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "NerdSMP-Website-Social");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Reads security/website-social.yml. Returns null when the file is new or sync is switched off. */
    public static WebsiteSocial start(NerdSmpPlugin plugin, Port port) throws Exception {
        Path directory = plugin.getDataFolder().toPath().resolve("security");
        Files.createDirectories(directory);
        Path file = directory.resolve("website-social.yml");
        if (Files.isSymbolicLink(file)) throw new IOException("website-social.yml must not be a symbolic link");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            plugin.saveResource("security/website-social.yml", false);
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        if (!Set.of("schema", "enabled", "url", "interval-seconds").containsAll(yaml.getKeys(false))
            || yaml.getInt("schema") != 1 || !yaml.isBoolean("enabled")) {
            throw new IllegalArgumentException("Invalid security/website-social.yml");
        }
        if (!yaml.getBoolean("enabled")) return null;
        Settings settings = new Settings(URI.create(yaml.getString("url", "")), yaml.getInt("interval-seconds", 15));
        String token = net.nerdsmp.paper.anticheat.PanelSync.loadOrCreateToken(directory.resolve("website-social-token.txt"));
        WebsiteSocial social = new WebsiteSocial(settings, token, port, new File(plugin.getDataFolder(), "website-social-state.yml"),
            task -> Bukkit.getScheduler().runTask(plugin, task), plugin.getLogger());
        social.timer.scheduleWithFixedDelay(social::pollQuietly, 5, settings.intervalSeconds(), TimeUnit.SECONDS);
        plugin.getLogger().info("Website friends, blocks and messages sync on. Copy security/website-social-token.txt into the"
            + " website's AERBORNE_SOCIAL_TOKEN setting; the token is never printed.");
        return social;
    }

    /** The website account linked to this game account, if any. Empty until the first successful sync. */
    public Optional<Linked> linked(AccountId account) { return Optional.ofNullable(linked.get(account)); }

    /**
     * Queues a change made in game. Only changes between linked players are sent; before the first sync the
     * server doesn't know who is linked yet, so it queues them all and the website skips the rest.
     */
    public synchronized void queue(String type, AccountId actor, AccountId target, String body, boolean quiet) {
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Unknown social change " + type);
        Map<AccountId, Linked> known = linked;
        if (!known.isEmpty() && (!known.containsKey(actor) || !known.containsKey(target))) return;
        String text = body == null ? "" : body.length() > MAX_MESSAGE ? body.substring(0, MAX_MESSAGE) : body;
        pending.add(new Change(nextSeq++, type, actor, target, text, quiet));
        if (pending.size() > MAX_PENDING) {
            pending.removeFirst();
            logger.warning("Website social sync has been offline for a while; dropped its oldest unsent change.");
        }
        saveQuietly();
    }

    private void pollQuietly() {
        try {
            pollOnce();
            if (failing) logger.info("Website social sync recovered.");
            failing = false;
        } catch (Exception failure) {
            if (!failing) logger.warning("Website social sync paused (" + failure.getMessage() + "); retrying every "
                + settings.intervalSeconds() + "s. Friends, blocks and /msg keep working in game.");
            failing = true;
        }
    }

    /** One exchange with the website. Returns how many website changes were applied in game. */
    int pollOnce() throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("apiVersion", 1);
        JsonArray events = new JsonArray();
        synchronized (this) {
            request.addProperty("ack", acknowledged);
            for (Change change : pending.subList(0, Math.min(MAX_BATCH, pending.size()))) {
                JsonObject event = new JsonObject();
                event.addProperty("seq", change.seq());
                event.addProperty("type", change.type());
                event.addProperty("actor", change.actor().value().toString());
                event.addProperty("target", change.target().value().toString());
                if (!change.body().isEmpty()) event.addProperty("body", change.body());
                if (change.quiet()) event.addProperty("quiet", true);
                events.add(event);
            }
        }
        request.add("events", events);
        HttpRequest http = HttpRequest.newBuilder(settings.url()).timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + token).header("Accept", "application/json")
            .header("Content-Type", "application/json").header("User-Agent", "AerborneSocial/1")
            .POST(HttpRequest.BodyPublishers.ofString(request.toString(), StandardCharsets.UTF_8)).build();
        HttpResponse<InputStream> response = client.send(http, HttpResponse.BodyHandlers.ofInputStream());
        byte[] body;
        try (InputStream input = response.body()) { body = input.readNBytes(MAX_BYTES + 1); }
        if (response.statusCode() != 200) throw new IOException("the website answered HTTP " + response.statusCode());
        if (body.length > MAX_BYTES) throw new IOException("the response was too large");
        Reply reply = parse(new String(body, StandardCharsets.UTF_8));
        linked = reply.linked();
        synchronized (this) {
            pending.removeIf(change -> change.seq() <= reply.accepted());
            saveQuietly();
        }
        List<Incoming> fresh = new ArrayList<>();
        for (Incoming incoming : reply.events()) if (incoming.id() > acknowledged) fresh.add(incoming);
        if (fresh.isEmpty()) return 0;
        CompletableFuture<Void> applied = new CompletableFuture<>();
        mainThread.execute(() -> {
            try { fresh.forEach(this::apply); applied.complete(null); }
            catch (RuntimeException failure) { applied.completeExceptionally(failure); }
        });
        applied.get(20, TimeUnit.SECONDS);
        synchronized (this) {
            acknowledged = fresh.getLast().id();
            saveQuietly();
        }
        return fresh.size();
    }

    private void apply(Incoming change) {
        try {
            switch (change.type()) {
                case "friend", "unfriend" -> change.actor().ifPresent(actor -> port.friend(actor, change.target(), change.type().equals("friend")));
                case "block", "unblock" -> change.actor().ifPresent(actor -> port.block(actor, change.target(), change.type().equals("block")));
                case "message" -> port.message(change.target(), change.actorName(), change.actor(), change.body());
                case "link" -> {
                    // A player just linked: send their game friends and blocks so the website matches.
                    Snapshot snapshot = port.snapshot(change.target());
                    synchronized (this) {
                        for (AccountId friend : snapshot.friends()) queueUnchecked("friend", change.target(), friend);
                        for (AccountId other : snapshot.ignored()) queueUnchecked("block", change.target(), other);
                    }
                }
                default -> { }
            }
        } catch (RuntimeException failure) {
            logger.warning("Skipped a website social change (" + change.type() + "): " + failure.getMessage());
        }
    }

    private void queueUnchecked(String type, AccountId actor, AccountId target) {
        pending.add(new Change(nextSeq++, type, actor, target, "", true));
        saveQuietly();
    }

    record Reply(long accepted, List<Incoming> events, Map<AccountId, Linked> linked) {}

    static Reply parse(String json) throws IOException {
        JsonObject root;
        try { root = JsonParser.parseString(json).getAsJsonObject(); }
        catch (RuntimeException invalid) { throw new IOException("the response was not JSON"); }
        try {
            if (root.get("apiVersion").getAsInt() != 1) throw new IllegalArgumentException();
            long accepted = root.get("accepted").getAsLong();
            JsonArray list = root.getAsJsonArray("events");
            if (list.size() > MAX_BATCH) throw new IOException("the website sent too many changes");
            List<Incoming> events = new ArrayList<>();
            long previous = 0;
            for (var element : list) {
                JsonObject e = element.getAsJsonObject();
                long id = e.get("id").getAsLong();
                String type = e.get("type").getAsString();
                if (id <= previous || !(TYPES.contains(type) || type.equals("link"))) throw new IllegalArgumentException();
                Optional<AccountId> actor = e.has("actor") && !e.get("actor").isJsonNull()
                    ? Optional.of(AccountId.parse(e.get("actor").getAsString())) : Optional.empty();
                String name = e.has("actorName") && !e.get("actorName").isJsonNull() ? e.get("actorName").getAsString() : "";
                String body = e.has("body") && !e.get("body").isJsonNull() ? e.get("body").getAsString() : "";
                if (name.length() > 32 || body.length() > MAX_MESSAGE) throw new IllegalArgumentException();
                events.add(new Incoming(id, type, actor, AccountId.parse(e.get("target").getAsString()), name, body));
                previous = id;
            }
            Map<AccountId, Linked> linked = new HashMap<>();
            for (var element : root.getAsJsonArray("linked")) {
                JsonObject l = element.getAsJsonObject();
                String messages = l.get("messages").getAsString();
                if (!Set.of("everyone", "friends", "nobody").contains(messages)) throw new IllegalArgumentException();
                linked.put(AccountId.parse(l.get("account").getAsString()), new Linked(l.get("member").getAsString(), messages,
                    Math.max(0, l.get("unreadMessages").getAsInt()), Math.max(0, l.get("unreadNotifications").getAsInt())));
            }
            return new Reply(accepted, events, Map.copyOf(linked));
        } catch (IOException tooMany) {
            throw tooMany;
        } catch (RuntimeException invalid) {
            throw new IOException("the website sent a change the server can't use");
        }
    }

    private void load() throws IOException {
        if (!stateFile.exists()) return;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(stateFile);
            if (yaml.getInt("schema") != 1) throw new IllegalArgumentException("unknown schema");
            acknowledged = yaml.getLong("acknowledged");
            nextSeq = Math.max(1, yaml.getLong("next-seq", 1));
            for (Map<?, ?> raw : yaml.getMapList("pending")) {
                long seq = ((Number) raw.get("seq")).longValue();
                String type = (String) raw.get("type");
                if (!TYPES.contains(type) || seq >= nextSeq) throw new IllegalArgumentException("bad pending change");
                Object body = raw.get("body");
                pending.add(new Change(seq, type, AccountId.parse((String) raw.get("actor")), AccountId.parse((String) raw.get("target")),
                    body == null ? "" : body.toString(), Boolean.TRUE.equals(raw.get("quiet"))));
            }
        } catch (Exception broken) {
            throw new IOException("website-social-state.yml can't be read; fix or remove it", broken);
        }
    }

    private void saveQuietly() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        yaml.set("acknowledged", acknowledged);
        yaml.set("next-seq", nextSeq);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Change change : pending) {
            Map<String, Object> raw = new HashMap<>();
            raw.put("seq", change.seq());
            raw.put("type", change.type());
            raw.put("actor", change.actor().value().toString());
            raw.put("target", change.target().value().toString());
            if (!change.body().isEmpty()) raw.put("body", change.body());
            if (change.quiet()) raw.put("quiet", true);
            list.add(raw);
        }
        yaml.set("pending", list);
        try { AtomicYamlFiles.save(yaml, stateFile); }
        catch (IOException failure) { logger.warning("Could not save website-social-state.yml: " + failure.getMessage()); }
    }

    long acknowledged() { return acknowledged; }

    synchronized List<Change> pending() { return List.copyOf(pending); }

    @Override public void close() { timer.shutdownNow(); }
}
