package net.nerdsmp.paper.anticheat.geo;

import com.google.gson.JsonObject;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Offline IP-to-location lookups against a local .mmdb file (DB-IP Lite City by default). Lookups run on one
 * background thread and report back on the main thread. The only network traffic is the database itself, fetched
 * at most once a month; addresses are never sent anywhere.
 */
public final class GeoIpService implements AutoCloseable {
    public static final String DEFAULT_URL = "https://download.db-ip.com/free/dbip-city-lite-{month}.mmdb.gz";
    private static final Pattern MONTH = Pattern.compile("(\\d{4}-\\d{2})");
    private static final String DOWNLOAD_PREFIX = "dbip-city-lite-";
    private static final long MAX_DOWNLOAD_BYTES = 1L << 30;
    private static final long CHECK_TICKS = 20L * 60 * 60 * 24; // daily; a new edition appears once a month

    private final NerdSmpPlugin plugin;
    private final Path folder;
    private final boolean autoDownload;
    private final String urlTemplate;
    private final ExecutorService lookups = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("nerdsmp-geoip").daemon().factory());
    private final List<Runnable> readyListeners = new ArrayList<>();
    private volatile MmdbReader reader;
    private volatile Path current;
    private volatile String state = "missing";
    private volatile String lastError;
    private volatile long lastDownload;
    private BukkitTask checker;

    public GeoIpService(NerdSmpPlugin plugin, String section) {
        this.plugin = plugin;
        folder = new File(plugin.getDataFolder(), "geoip").toPath();
        autoDownload = plugin.getConfig().getBoolean(section + ".auto-download", true);
        urlTemplate = plugin.getConfig().getString(section + ".download-url", DEFAULT_URL);
    }

    /** Main thread. Loads the local file in the background, then keeps it current if auto-download is on. */
    public void start() {
        checker = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refresh, 0L, CHECK_TICKS);
    }

    /** Main thread. Runs on the main thread each time a database becomes available, for backfilling. */
    public void onReady(Runnable listener) {
        readyListeners.add(listener);
    }

    public boolean ready() {
        return reader != null;
    }

    /**
     * Resolves an address on the lookup thread and hands the result to {@code onMain} on the main thread. Nothing is
     * reported when the address is unparsable or no database is loaded yet; local addresses resolve without one.
     */
    public void lookup(String address, Consumer<GeoLocation> onMain) {
        lookupAll(List.of(address), (ignored, location) -> onMain.accept(location));
    }

    /** Batch form of {@link #lookup}, used to backfill stored history in one background pass. */
    public void lookupAll(List<String> addresses, BiConsumer<String, GeoLocation> onMain) {
        if (addresses.isEmpty()) return;
        try {
            lookups.execute(() -> {
                List<String> found = new ArrayList<>();
                List<GeoLocation> locations = new ArrayList<>();
                for (String address : addresses) {
                    GeoLocation location = resolve(address);
                    if (location == null) continue;
                    found.add(address);
                    locations.add(location);
                }
                if (found.isEmpty() || !plugin.isEnabled()) return;
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> { for (int i = 0; i < found.size(); i++) onMain.accept(found.get(i), locations.get(i)); });
                } catch (RuntimeException disabled) {
                    // The plugin stopped while this lookup ran; the result is simply dropped.
                }
            });
        } catch (RejectedExecutionException closed) {
            // Shutting down.
        }
    }

    /** Lookup thread only. */
    private GeoLocation resolve(String text) {
        InetAddress address = parse(text);
        if (address == null) return null;
        if (GeoLocation.isLocal(address)) return GeoLocation.LOCAL;
        MmdbReader database = reader;
        if (database == null) return null;
        try {
            return GeoLocation.fromRecord(database.lookup(address));
        } catch (IOException | RuntimeException failure) {
            lastError = "Lookup failed: " + failure.getMessage();
            return null;
        }
    }

    /** Parses a literal address only; never does a DNS lookup. */
    static InetAddress parse(String text) {
        if (text == null || text.isBlank() || text.length() > 64) return null;
        String literal = text.strip();
        if (literal.startsWith("[") && literal.endsWith("]")) literal = literal.substring(1, literal.length() - 1);
        int zone = literal.indexOf('%');
        if (zone >= 0) literal = literal.substring(0, zone);
        try {
            if (literal.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                byte[] raw = new byte[4];
                String[] parts = literal.split("\\.");
                for (int i = 0; i < 4; i++) {
                    int part = Integer.parseInt(parts[i]);
                    if (part > 255) return null;
                    raw[i] = (byte) part;
                }
                return InetAddress.getByAddress(raw);
            }
            // A colon makes the JDK parse it as an IPv6 literal and refuse it if malformed, never a name lookup.
            if (literal.indexOf(':') >= 0 && literal.matches("[0-9A-Fa-f:.]+")) return InetAddress.getByName(literal);
        } catch (UnknownHostException invalid) {
            return null;
        }
        return null;
    }

    public JsonObject status() {
        JsonObject root = new JsonObject();
        root.addProperty("state", state);
        root.addProperty("autoDownload", autoDownload);
        MmdbReader database = reader;
        Path file = current;
        if (file != null) root.addProperty("file", file.getFileName().toString());
        if (database != null) {
            root.addProperty("database", database.databaseType());
            root.addProperty("built", database.buildEpoch() * 1000L);
            root.addProperty("attribution", attribution(database.databaseType()));
            root.addProperty("attributionUrl", attributionUrl(database.databaseType()));
        }
        if (lastDownload > 0) root.addProperty("lastDownload", lastDownload);
        if (lastError != null) root.addProperty("error", lastError);
        return root;
    }

    @Override
    public void close() {
        if (checker != null) checker.cancel();
        lookups.shutdownNow();
    }

    // ---- Database file ----

    /** Async thread: load the newest local file, then download a newer edition when one is due. */
    private synchronized void refresh() {
        try {
            if (reader == null) loadNewest();
            if (autoDownload) downloadIfDue();
            if (reader == null) state = autoDownload ? "error" : "missing";
        } catch (RuntimeException failure) {
            lastError = failure.getMessage();
            plugin.getLogger().warning("GeoIP refresh failed: " + failure.getMessage());
        }
    }

    private void loadNewest() {
        for (Path candidate : localFiles()) {
            try {
                use(candidate, MmdbReader.open(candidate));
                return;
            } catch (IOException unreadable) {
                lastError = candidate.getFileName() + " could not be read: " + unreadable.getMessage();
                plugin.getLogger().warning("GeoIP: " + lastError);
            }
        }
    }

    private List<Path> localFiles() {
        if (!Files.isDirectory(folder)) return List.of();
        try (var files = Files.list(folder)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".mmdb") && Files.isRegularFile(path))
                .sorted(Comparator.comparing(GeoIpService::edition).reversed()).toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    private static YearMonth edition(Path file) {
        YearMonth named = monthOf(file.getFileName().toString());
        if (named != null) return named;
        try {
            return YearMonth.from(Files.getLastModifiedTime(file).toInstant().atZone(ZoneOffset.UTC));
        } catch (IOException unreadable) {
            return YearMonth.of(1970, 1);
        }
    }

    private void downloadIfDue() {
        YearMonth have = current == null ? null : edition(current);
        for (YearMonth month : downloadCandidates(YearMonth.now(ZoneOffset.UTC), have)) {
            String url = url(urlTemplate, month);
            state = reader == null ? "downloading" : state;
            try {
                Path file = download(url, folder.resolve(DOWNLOAD_PREFIX + month + ".mmdb"));
                if (file == null) continue; // not published yet; try last month's edition
                use(file, MmdbReader.open(file));
                lastDownload = System.currentTimeMillis();
                lastError = null;
                plugin.getLogger().info("GeoIP: downloaded " + file.getFileName() + ".");
                return;
            } catch (IOException | InterruptedException failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                lastError = "Download failed: " + failure.getMessage();
                plugin.getLogger().warning("GeoIP: " + lastError);
                return;
            }
        }
        if (reader == null) lastError = "No database edition is published at " + URI.create(url(urlTemplate, YearMonth.now(ZoneOffset.UTC))).getHost() + " yet.";
    }

    /** Downloads to a temporary file and only moves it into place once it opens as a valid database. */
    private Path download(String url, Path target) throws IOException, InterruptedException {
        Files.createDirectories(folder);
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build()) {
            return download(client, url, target);
        }
    }

    private Path download(HttpClient client, String url, Path target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10))
            .header("User-Agent", "NerdSMP/" + plugin.getPluginMeta().getVersion()).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() == 404) return null;
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " from " + URI.create(url).getHost());
            Path temporary = Files.createTempFile(folder, "download-", ".part");
            try {
                try (InputStream raw = url.endsWith(".gz") ? new GZIPInputStream(body, 65_536) : body;
                     OutputStream out = Files.newOutputStream(temporary)) {
                    byte[] chunk = new byte[65_536];
                    long total = 0;
                    for (int read; (read = raw.read(chunk)) >= 0; ) {
                        total += read;
                        if (total > MAX_DOWNLOAD_BYTES) throw new IOException("The database is larger than 1 GB.");
                        out.write(chunk, 0, read);
                    }
                }
                MmdbReader.open(temporary); // throws if the file is not a usable database
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return target;
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private void use(Path file, MmdbReader database) {
        reader = database;
        current = file;
        state = "ready";
        if (!plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> readyListeners.forEach(Runnable::run));
        } catch (RuntimeException disabled) {
            // Shutting down.
        }
        for (Path old : localFiles()) {
            if (!old.equals(file) && old.getFileName().toString().startsWith(DOWNLOAD_PREFIX)) deleteQuietly(old);
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException stillMapped) {
            // Windows keeps a memory-mapped file locked until it is garbage-collected; the next start removes it.
        }
    }

    // ---- Pure helpers (unit-tested) ----

    static YearMonth monthOf(String fileName) {
        Matcher matcher = MONTH.matcher(fileName);
        YearMonth found = null;
        while (matcher.find()) {
            try { found = YearMonth.parse(matcher.group(1)); } catch (DateTimeParseException ignored) { /* not a date */ }
        }
        return found;
    }

    /** Editions worth fetching, newest first: this month's (if published yet) and last month's, never older than {@code have}. */
    static List<YearMonth> downloadCandidates(YearMonth now, YearMonth have) {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month : List.of(now, now.minusMonths(1))) {
            if (have == null || month.isAfter(have)) months.add(month);
        }
        return months;
    }

    static String url(String template, YearMonth month) {
        return template.replace("{month}", month.toString());
    }

    static String attribution(String databaseType) {
        return databaseType != null && databaseType.toLowerCase(java.util.Locale.ROOT).contains("geolite")
            ? "This product includes GeoLite2 data created by MaxMind" : "IP Geolocation by DB-IP";
    }

    static String attributionUrl(String databaseType) {
        return databaseType != null && databaseType.toLowerCase(java.util.Locale.ROOT).contains("geolite")
            ? "https://www.maxmind.com" : "https://db-ip.com";
    }
}
