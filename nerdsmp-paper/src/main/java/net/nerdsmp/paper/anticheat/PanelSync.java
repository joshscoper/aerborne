package net.nerdsmp.paper.anticheat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Pulls the website's administrator list on a timer and hands it to {@link PanelAccounts}.
 * The website answers only with a shared bearer token; the panel never accepts pushed changes.
 */
public final class PanelSync implements AutoCloseable {
    static final int MAX_BYTES = 64 * 1024;
    static final int MAX_ACCOUNTS = 50;

    public record Settings(URI url, int intervalSeconds, int maxAgeHours) {
        public Settings {
            String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
            String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
            boolean local = host.equals("localhost") || host.equals("127.0.0.1");
            if (!(scheme.equals("https") || local && scheme.equals("http")) || url.getRawUserInfo() != null
                || url.getRawQuery() != null || url.getRawFragment() != null) {
                throw new IllegalArgumentException("Panel website-sync url must be an https:// address (http only for localhost)");
            }
            if (intervalSeconds < 30 || intervalSeconds > 3600) throw new IllegalArgumentException("Panel website-sync interval-seconds must be 30-3600");
            if (maxAgeHours < 1 || maxAgeHours > 168) throw new IllegalArgumentException("Panel website-sync max-age-hours must be 1-168");
        }
    }

    private final Settings settings;
    private final String token;
    private final PanelAccounts accounts;
    private final Logger logger;
    private final HttpClient client;
    private final ScheduledExecutorService timer;
    private volatile boolean failing;

    PanelSync(Settings settings, String token, PanelAccounts accounts, Logger logger) {
        if (token == null || !token.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid website sync token");
        this.settings = settings;
        this.token = token;
        this.accounts = accounts;
        this.logger = logger;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "NerdSMP-Watchdog-Sync");
            thread.setDaemon(true);
            return thread;
        });
    }

    void start() {
        timer.scheduleWithFixedDelay(this::syncQuietly, 0, settings.intervalSeconds(), TimeUnit.SECONDS);
    }

    private void syncQuietly() {
        try {
            String changes = syncOnce();
            if (failing) logger.info("Watchdog website sync recovered.");
            failing = false;
            if (!changes.isEmpty()) logger.info("Watchdog website admins updated: " + changes + ".");
        } catch (Exception failure) {
            if (!failing) {
                logger.warning("Watchdog website sync failed (" + failure.getMessage() + "). Website admins keep panel access for up to "
                    + settings.maxAgeHours() + "h after the last successful sync; the panel's own login still works.");
            }
            failing = true;
        }
    }

    /** One fetch. Throws on any problem so the previous list stays in place. */
    String syncOnce() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(settings.url()).timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + token).header("Accept", "application/json")
            .header("User-Agent", "AerborneWatchdog/1").GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        byte[] body;
        try (InputStream input = response.body()) { body = input.readNBytes(MAX_BYTES + 1); }
        if (response.statusCode() != 200) throw new IOException("the website answered HTTP " + response.statusCode());
        if (body.length > MAX_BYTES) throw new IOException("the response was too large");
        return accounts.replace(parse(new String(body, StandardCharsets.UTF_8)));
    }

    static List<WebsiteAccount> parse(String json) throws IOException {
        JsonObject root;
        try { root = JsonParser.parseString(json).getAsJsonObject(); }
        catch (RuntimeException invalid) { throw new IOException("the response was not JSON"); }
        if (!root.has("apiVersion") || root.get("apiVersion").getAsInt() != 1 || !root.has("accounts") || !root.get("accounts").isJsonArray()) {
            throw new IOException("the response had an unexpected shape");
        }
        var list = root.getAsJsonArray("accounts");
        if (list.size() > MAX_ACCOUNTS) throw new IOException("the website sent too many accounts");
        List<WebsiteAccount> out = new ArrayList<>();
        for (var element : list) {
            try {
                JsonObject account = element.getAsJsonObject();
                out.add(WebsiteAccount.parse(account.get("username").getAsString(), account.get("hash").getAsString()));
            } catch (RuntimeException invalid) {
                throw new IOException("the website sent an account the panel can't use");
            }
        }
        return out;
    }

    /** Reads the shared token, creating a private one on first use. Never logged. */
    public static String loadOrCreateToken(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || Files.isSymbolicLink(path.getParent())) throw new IOException("Panel sync token paths must not be symbolic links");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            String token = HexFormat.of().formatHex(random);
            if (Files.getFileAttributeView(path.getParent(), PosixFileAttributeView.class) != null) {
                Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } else {
                Files.createFile(path);
                AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                if (acl == null) { Files.delete(path); throw new IOException("File system cannot protect the panel sync token"); }
                acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
            }
            Files.writeString(path, token + "\n", StandardCharsets.US_ASCII, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 66) throw new IOException("Invalid panel sync token file");
        String raw = Files.readString(path, StandardCharsets.US_ASCII).strip();
        if (!raw.matches("[a-f0-9]{64}")) throw new IOException("Invalid panel sync token file");
        return raw;
    }

    @Override public void close() { timer.shutdownNow(); }
}
