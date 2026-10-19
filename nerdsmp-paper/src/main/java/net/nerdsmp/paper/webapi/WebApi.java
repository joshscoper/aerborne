package net.nerdsmp.paper.webapi;

import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Read-only integration service; no relationship to Watchdog credentials or moderation actions. */
public final class WebApi implements AutoCloseable {
    private final ApiSettings settings;
    private final AtomicReference<ApiSnapshot> published = new AtomicReference<>();
    private final ApiSnapshots snapshots;
    private final ApiHttpListener listener;
    private final WebLinkCodes links = new WebLinkCodes(System::currentTimeMillis);

    private WebApi(NerdSmpPlugin plugin, ApiSettings settings, ApiToken token) throws Exception {
        this.settings = settings;
        snapshots = new ApiSnapshots(plugin, settings.snapshotSeconds(), published::set);
        snapshots.refresh();
        listener = new ApiHttpListener(settings, token, published::get, links);
        try { snapshots.start(); }
        catch (RuntimeException failure) { listener.close(); throw failure; }
    }

    /** Call on the main thread after gameplay services initialize. Returns null only when explicitly disabled. */
    public static WebApi start(NerdSmpPlugin plugin) throws Exception {
        Path data = plugin.getDataFolder().toPath();
        ApiToken.rejectLink(data);
        Path directory = data.resolve("security");
        ApiToken.rejectLink(directory);
        Files.createDirectories(directory);
        Path config = directory.resolve("web-api.yml");
        ApiToken.rejectLink(config);
        if (!Files.exists(config, LinkOption.NOFOLLOW_LINKS)) plugin.saveResource("security/web-api.yml", false);
        if (!Files.isRegularFile(config, LinkOption.NOFOLLOW_LINKS) || Files.size(config) > 16_384) {
            throw new IllegalArgumentException("Invalid security/web-api.yml; existing file was preserved");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(config.toFile());
        ApiSettings settings = ApiSettings.read(yaml);
        if (!settings.enabled()) return null;
        ApiToken token = ApiToken.loadOrCreate(directory.resolve("web-api-token.txt"));
        WebApi api = new WebApi(plugin, settings, token);
        plugin.getLogger().info("Read-only web API listening on " + settings.bind().getHostAddress() + ":" + api.port()
            + ". Private endpoints use security/web-api-token.txt; the token is never printed.");
        if (!settings.bind().isLoopbackAddress()) {
            plugin.getLogger().warning("The web API is network-accessible. Public status is anonymous; private data needs a bearer token."
                + " Use HTTPS through a reverse proxy or a trusted VPN before sending that token. No firewall rules were changed.");
        }
        return api;
    }

    public int port() { return listener.port(); }
    public WebLinkCodes links() { return links; }
    public InetAddress bindAddress() { return listener.bindAddress(); }
    public String status() { return "read-only " + bindAddress().getHostAddress() + ":" + port(); }

    /** Main-thread diagnostics/smoke hook. HTTP requests never invoke this method. */
    public void refreshNow() { snapshots.refresh(); }

    @Override public void close() { snapshots.close(); listener.close(); }
}
