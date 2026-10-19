package net.nerdsmp.paper.webapi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Pays Marks earned in the website Arcade. The plugin pulls pending payouts from the website, credits each
 * one through the economy with an idempotency key, then acknowledges it on the next pull. A payout fetched
 * twice is only ever applied once. Per-payout and per-hour ceilings limit the damage if the website were
 * ever compromised.
 */
public final class WebsiteRewards implements AutoCloseable {
    static final int MAX_BYTES = 64 * 1024;
    static final int MAX_BATCH = 100;

    public record Settings(URI url, int intervalSeconds, long maxPerPayout, long maxPerHour) {
        public Settings {
            String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
            String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
            boolean local = host.equals("localhost") || host.equals("127.0.0.1");
            if (!(scheme.equals("https") || local && scheme.equals("http")) || url.getRawUserInfo() != null
                || url.getRawQuery() != null || url.getRawFragment() != null) {
                throw new IllegalArgumentException("website-rewards url must be an https:// address (http only for localhost)");
            }
            if (intervalSeconds < 15 || intervalSeconds > 3600) throw new IllegalArgumentException("website-rewards interval-seconds must be 15-3600");
            if (maxPerPayout < 1 || maxPerPayout > 10_000) throw new IllegalArgumentException("website-rewards max-marks-per-payout must be 1-10000");
            if (maxPerHour < maxPerPayout || maxPerHour > 1_000_000) throw new IllegalArgumentException("website-rewards max-marks-per-hour is out of range");
        }
    }

    record Payout(long id, AccountId account, long marks, String game) {}

    private final Settings settings;
    private final String token;
    private final EconomyService economy;
    private final Executor mainThread;
    private final Logger logger;
    private final LongSupplier clock;
    private final HttpClient client;
    private final ScheduledExecutorService timer;
    private final Deque<long[]> hour = new ArrayDeque<>(); // {millis, marks}
    private long acknowledged;
    private boolean failing;
    private boolean paused;

    WebsiteRewards(Settings settings, String token, EconomyService economy, Executor mainThread, Logger logger, LongSupplier clock) {
        if (token == null || !token.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid website rewards token");
        this.settings = settings;
        this.token = token;
        this.economy = economy;
        this.mainThread = mainThread;
        this.logger = logger;
        this.clock = clock;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "NerdSMP-Website-Rewards");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Reads security/website-rewards.yml. Returns null when the file is new or rewards are switched off. */
    public static WebsiteRewards start(NerdSmpPlugin plugin) throws Exception {
        Path directory = plugin.getDataFolder().toPath().resolve("security");
        Files.createDirectories(directory);
        Path file = directory.resolve("website-rewards.yml");
        if (Files.isSymbolicLink(file)) throw new IOException("website-rewards.yml must not be a symbolic link");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            plugin.saveResource("security/website-rewards.yml", false);
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        if (!Set.of("schema", "enabled", "url", "interval-seconds", "max-marks-per-payout", "max-marks-per-hour").containsAll(yaml.getKeys(false))
            || yaml.getInt("schema") != 1 || !yaml.isBoolean("enabled")) {
            throw new IllegalArgumentException("Invalid security/website-rewards.yml");
        }
        if (!yaml.getBoolean("enabled")) return null;
        Settings settings = new Settings(URI.create(yaml.getString("url", "")), yaml.getInt("interval-seconds", 60),
            yaml.getLong("max-marks-per-payout", 50), yaml.getLong("max-marks-per-hour", 5000));
        String token = net.nerdsmp.paper.anticheat.PanelSync.loadOrCreateToken(directory.resolve("website-rewards-token.txt"));
        WebsiteRewards rewards = new WebsiteRewards(settings, token, plugin.economy(),
            task -> Bukkit.getScheduler().runTask(plugin, task), plugin.getLogger(), System::currentTimeMillis);
        rewards.timer.scheduleWithFixedDelay(rewards::pollQuietly, 5, settings.intervalSeconds(), TimeUnit.SECONDS);
        plugin.getLogger().info("Website Arcade rewards on. Copy security/website-rewards-token.txt into the website's"
            + " AERBORNE_REWARDS_TOKEN setting; the token is never printed.");
        return rewards;
    }

    private void pollQuietly() {
        try {
            int paid = pollOnce();
            if (failing) logger.info("Website Arcade rewards recovered.");
            failing = false;
            if (paid > 0) logger.info("Paid " + paid + " website Arcade reward" + (paid == 1 ? "" : "s") + ".");
        } catch (Exception failure) {
            if (!failing) logger.warning("Website Arcade rewards paused (" + failure.getMessage() + "); retrying every "
                + settings.intervalSeconds() + "s.");
            failing = true;
        }
    }

    /** One pull. Returns how many payouts were newly credited. */
    int pollOnce() throws Exception {
        if (paused) return 0;
        URI url = URI.create(settings.url() + "?ack=" + acknowledged);
        HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + token).header("Accept", "application/json")
            .header("User-Agent", "AerborneRewards/1").GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        byte[] body;
        try (InputStream input = response.body()) { body = input.readNBytes(MAX_BYTES + 1); }
        if (response.statusCode() != 200) throw new IOException("the website answered HTTP " + response.statusCode());
        if (body.length > MAX_BYTES) throw new IOException("the response was too large");
        int paid = 0;
        for (Payout payout : parse(new String(body, StandardCharsets.UTF_8))) {
            if (payout.id() <= acknowledged) continue;
            if (payout.marks() > settings.maxPerPayout()) {
                logger.warning("Skipped website Arcade payout #" + payout.id() + ": " + payout.marks()
                    + " Marks is over max-marks-per-payout.");
                acknowledged = payout.id();
                continue;
            }
            if (!withinHourlyLimit(payout.marks())) {
                paused = true;
                logger.severe("Website Arcade rewards stopped: more than " + settings.maxPerHour()
                    + " Marks requested within an hour. Check the website, then restart the server to resume.");
                break;
            }
            var reason = new EconomyService.TransactionReason("website.arcade", "Website Arcade: " + payout.game());
            CompletableFuture<EconomyService.TransactionResult> result = new CompletableFuture<>();
            mainThread.execute(() -> economy.credit(payout.account(), payout.marks(), reason, "website-arcade:" + payout.id())
                .whenComplete((value, error) -> { if (error != null) result.completeExceptionally(error); else result.complete(value); }));
            var status = result.get(20, TimeUnit.SECONDS).status();
            switch (status) {
                case APPLIED -> { paid++; hour.addLast(new long[]{clock.getAsLong(), payout.marks()}); }
                case DUPLICATE -> { }
                case INVALID_AMOUNT, INSUFFICIENT_FUNDS ->
                    logger.warning("Website Arcade payout #" + payout.id() + " was refused by the economy (" + status + ").");
            }
            acknowledged = payout.id();
        }
        return paid;
    }

    private boolean withinHourlyLimit(long marks) {
        long now = clock.getAsLong();
        while (!hour.isEmpty() && now - hour.peekFirst()[0] > 3_600_000L) hour.removeFirst();
        long total = 0;
        for (long[] entry : hour) total += entry[1];
        return total + marks <= settings.maxPerHour();
    }

    long acknowledged() { return acknowledged; }

    static List<Payout> parse(String json) throws IOException {
        JsonObject root;
        try { root = JsonParser.parseString(json).getAsJsonObject(); }
        catch (RuntimeException invalid) { throw new IOException("the response was not JSON"); }
        if (!root.has("apiVersion") || root.get("apiVersion").getAsInt() != 1 || !root.has("payouts") || !root.get("payouts").isJsonArray()) {
            throw new IOException("the response had an unexpected shape");
        }
        var list = root.getAsJsonArray("payouts");
        if (list.size() > MAX_BATCH) throw new IOException("the website sent too many payouts");
        List<Payout> out = new ArrayList<>();
        long previous = 0;
        for (var element : list) {
            try {
                JsonObject p = element.getAsJsonObject();
                long id = p.get("id").getAsLong();
                long marks = p.get("marks").getAsLong();
                String game = p.get("game").getAsString();
                if (id <= previous || marks < 1 || !game.matches("[a-z0-9-]{1,32}")) throw new IllegalArgumentException();
                out.add(new Payout(id, AccountId.parse(p.get("accountId").getAsString()), marks, game));
                previous = id;
            } catch (RuntimeException invalid) {
                throw new IOException("the website sent a payout the server can't use");
            }
        }
        return out;
    }

    @Override public void close() { timer.shutdownNow(); }
}
