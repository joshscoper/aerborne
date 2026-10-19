package net.nerdsmp.paper.anticheat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.anticheat.geo.GeoIpService;
import net.nerdsmp.paper.anticheat.geo.GeoLocation;
import net.nerdsmp.paper.anticheat.geo.GeoSpread;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Staff-only connection history: addresses, sessions and client details. Main thread only. Addresses are personal
 * data, so each account keeps at most {@value #MAX_ADDRESSES} and recording can be switched off in config. Each address
 * also carries its coarse location (country, region, city) from an offline GeoIP database, resolved off the main thread.
 */
public final class ConnectionLog implements Listener, AutoCloseable {
    private static final int MAX_ADDRESSES = 10;
    private static final int MAX_CHANNELS = 64;
    private final NerdSmpPlugin plugin;
    private final File file;
    private final boolean recordAddresses;
    private final boolean locateAddresses;
    private final GeoIpService geo;
    private final Map<UUID, Profile> profiles = new HashMap<>();
    private final Map<UUID, Long> sessionStarts = new HashMap<>();
    private boolean dirty;
    private BukkitTask autosave;

    public ConnectionLog(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "security/connections.yml");
        recordAddresses = plugin.getConfig().getBoolean("anti-cheat.record-ip-addresses", true);
        // Locations hang off addresses, so switching addresses off switches locations off too.
        locateAddresses = recordAddresses && plugin.getConfig().getBoolean("anti-cheat.geolocation.enabled", true);
        geo = locateAddresses ? new GeoIpService(plugin, "anti-cheat.geolocation") : null;
        load();
    }

    public void start() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) { // reload safety: treat current players as just joined
            sessionStarts.put(player.getUniqueId(), now);
            capture(player);
        }
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 6_000L, 6_000L);
        if (geo != null) {
            geo.onReady(this::backfillLocations);
            geo.start();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        sessionStarts.put(player.getUniqueId(), now);
        Profile profile = profile(player);
        profile.sessions++;
        profile.lastJoin = now;
        if (recordAddresses) {
            String address = address(player.getAddress());
            if (address != null) {
                AddressSeen seen = profile.addresses.computeIfAbsent(address, ignored -> new AddressSeen(now));
                seen.lastSeen = now;
                seen.joins++;
                trimAddresses(profile);
                locate(player.getUniqueId(), address);
            }
            InetSocketAddress host = player.getVirtualHost();
            if (host != null) profile.host = host.getHostString() + ":" + host.getPort();
        }
        capture(player);
        // The client sends its brand and mod channels just after joining.
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) capture(player); }, 60L);
        dirty = true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannel(PlayerRegisterChannelEvent event) {
        Profile profile = profile(event.getPlayer());
        if (profile.channels.size() < MAX_CHANNELS && profile.channels.add(event.getChannel())) dirty = true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Long started = sessionStarts.remove(player.getUniqueId());
        Profile profile = profile(player);
        long now = System.currentTimeMillis();
        profile.lastQuit = now;
        if (started != null) {
            long length = Math.max(0L, now - started);
            profile.trackedMillis += length;
            profile.longestMillis = Math.max(profile.longestMillis, length);
            profile.completedSessions++;
        }
        capture(player);
        save();
    }

    @Override
    public void close() {
        if (autosave != null) autosave.cancel();
        if (geo != null) geo.close();
        long now = System.currentTimeMillis();
        sessionStarts.forEach((id, started) -> {
            Profile profile = profiles.get(id);
            if (profile == null) return;
            profile.trackedMillis += Math.max(0L, now - started);
            profile.longestMillis = Math.max(profile.longestMillis, now - started);
            profile.completedSessions++;
            profile.lastQuit = now;
        });
        sessionStarts.clear();
        save();
    }

    /** Connection details for one account, including other accounts seen on the same addresses. */
    public JsonObject panelPlayer(UUID playerId) {
        JsonObject root = new JsonObject();
        root.addProperty("recordsAddresses", recordAddresses);
        Profile profile = profiles.get(playerId);
        if (profile == null) return root;
        Player online = Bukkit.getPlayer(playerId);
        Long started = sessionStarts.get(playerId);
        long current = started == null ? 0L : System.currentTimeMillis() - started;
        root.addProperty("sessions", profile.sessions);
        root.addProperty("trackedMillis", profile.trackedMillis + current);
        root.addProperty("averageMillis", (profile.trackedMillis + current) / Math.max(1, profile.completedSessions + (started == null ? 0 : 1)));
        root.addProperty("longestMillis", Math.max(profile.longestMillis, current));
        root.addProperty("trackedSince", profile.trackedSince);
        root.addProperty("lastJoin", profile.lastJoin);
        root.addProperty("lastQuit", profile.lastQuit);
        root.addProperty("brand", profile.brand);
        root.addProperty("protocol", profile.protocol);
        root.addProperty("serverProtocol", Bukkit.getUnsafe().getProtocolVersion());
        root.addProperty("locale", profile.locale);
        root.addProperty("viewDistance", profile.viewDistance);
        root.addProperty("host", profile.host);
        JsonArray channels = new JsonArray();
        profile.channels.forEach(channels::add);
        root.add("channels", channels);
        JsonArray mods = new JsonArray();
        modHints(profile).forEach(mods::add);
        root.add("mods", mods);
        if (online != null && recordAddresses) root.addProperty("currentAddress", address(online.getAddress()));
        JsonArray addresses = new JsonArray();
        profile.addresses.entrySet().stream().sorted(Map.Entry.<String, AddressSeen>comparingByValue(Comparator.comparingLong(seen -> -seen.lastSeen)))
            .forEach(entry -> {
                JsonObject row = new JsonObject();
                row.addProperty("address", entry.getKey());
                row.addProperty("firstSeen", entry.getValue().firstSeen);
                row.addProperty("lastSeen", entry.getValue().lastSeen);
                row.addProperty("joins", entry.getValue().joins);
                if (entry.getValue().location != null) row.add("location", locationJson(entry.getValue().location));
                JsonArray shared = new JsonArray();
                profiles.forEach((otherId, other) -> {
                    if (otherId.equals(playerId) || !other.addresses.containsKey(entry.getKey())) return;
                    JsonObject alt = new JsonObject();
                    alt.addProperty("uuid", otherId.toString());
                    alt.addProperty("name", other.name);
                    alt.addProperty("lastSeen", other.addresses.get(entry.getKey()).lastSeen);
                    shared.add(alt);
                });
                row.add("sharedWith", shared);
                addresses.add(row);
            });
        root.add("addresses", addresses);
        if (!addresses.isEmpty()) root.addProperty("lastAddress", addresses.get(0).getAsJsonObject().get("address").getAsString());
        root.addProperty("locatesAddresses", locateAddresses);
        GeoLocation location = lastLocation(profile);
        if (location != null) root.add("location", locationJson(location));
        return root;
    }

    /** Players by country for the console, each account counted once at its last-known location. */
    public JsonObject panelGeography() {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", locateAddresses);
        root.addProperty("recordsAddresses", recordAddresses);
        if (geo == null) return root;
        root.add("database", geo.status());
        List<GeoSpread.Player> players = new ArrayList<>(profiles.size());
        profiles.forEach((id, profile) -> players.add(new GeoSpread.Player(lastLocation(profile), sessionStarts.containsKey(id))));
        GeoSpread.Summary summary = GeoSpread.of(players);
        root.addProperty("located", summary.located());
        root.addProperty("unlocated", summary.unlocated());
        root.addProperty("onlineLocated", summary.onlineLocated());
        JsonArray countries = new JsonArray();
        for (GeoSpread.Country country : summary.countries()) {
            JsonObject row = new JsonObject();
            row.addProperty("code", country.code());
            row.addProperty("name", country.name());
            row.addProperty("players", country.players());
            row.addProperty("online", country.online());
            row.add("regions", places(country.regions()));
            row.add("cities", places(country.cities()));
            countries.add(row);
        }
        root.add("countries", countries);
        return root;
    }

    /** Count of other accounts that share any recorded address, for list views. */
    public int sharedAccounts(UUID playerId) {
        Profile profile = profiles.get(playerId);
        if (profile == null || profile.addresses.isEmpty()) return 0;
        int count = 0;
        for (var entry : profiles.entrySet()) {
            if (entry.getKey().equals(playerId)) continue;
            for (String address : entry.getValue().addresses.keySet()) {
                if (profile.addresses.containsKey(address)) { count++; break; }
            }
        }
        return count;
    }

    public String brand(UUID playerId) {
        Profile profile = profiles.get(playerId);
        return profile == null ? null : profile.brand;
    }

    // ---- Internals ----

    /** Background lookup; the result lands on the main thread and is kept only if the address is still on record. */
    private void locate(UUID playerId, String address) {
        if (geo == null) return;
        geo.lookup(address, location -> store(playerId, address, location));
    }

    private void store(UUID playerId, String address, GeoLocation location) {
        Profile profile = profiles.get(playerId);
        AddressSeen seen = profile == null ? null : profile.addresses.get(address);
        if (seen == null || location.equals(seen.location)) return;
        seen.location = location;
        dirty = true;
    }

    /** When a database loads (fresh install, or a monthly update), resolve addresses that have no location yet. */
    private void backfillLocations() {
        if (geo == null || !geo.ready()) return;
        Map<String, List<UUID>> owners = new HashMap<>();
        profiles.forEach((id, profile) -> profile.addresses.forEach((address, seen) -> {
            if (seen.location == null) owners.computeIfAbsent(address, ignored -> new ArrayList<>()).add(id);
        }));
        geo.lookupAll(new ArrayList<>(owners.keySet()), (address, location) -> {
            for (UUID id : owners.getOrDefault(address, List.of())) store(id, address, location);
        });
    }

    /** The location of the most recently used address that has one. */
    static GeoLocation lastLocation(Profile profile) {
        return profile.addresses.values().stream().filter(seen -> seen.location != null)
            .max(Comparator.comparingLong(seen -> seen.lastSeen)).map(seen -> seen.location).orElse(null);
    }

    private static JsonObject locationJson(GeoLocation location) {
        JsonObject row = new JsonObject();
        row.addProperty("countryCode", location.countryCode());
        row.addProperty("country", location.country());
        row.addProperty("region", location.region());
        row.addProperty("city", location.city());
        row.addProperty("label", location.label());
        return row;
    }

    private static JsonArray places(List<GeoSpread.Place> places) {
        JsonArray array = new JsonArray();
        for (GeoSpread.Place place : places) {
            JsonObject row = new JsonObject();
            row.addProperty("name", place.name());
            row.addProperty("players", place.players());
            array.add(row);
        }
        return array;
    }

    private void capture(Player player) {
        Profile profile = profile(player);
        String brand = player.getClientBrandName();
        if (brand != null) profile.brand = brand.length() > 64 ? brand.substring(0, 64) : brand;
        profile.protocol = player.getProtocolVersion();
        profile.locale = player.locale() == null ? null : player.locale().toLanguageTag();
        profile.viewDistance = player.getClientViewDistance();
        for (String channel : player.getListeningPluginChannels()) {
            if (profile.channels.size() >= MAX_CHANNELS) break;
            profile.channels.add(channel);
        }
        dirty = true;
    }

    private Profile profile(Player player) {
        Profile profile = profiles.computeIfAbsent(player.getUniqueId(), ignored -> new Profile(System.currentTimeMillis()));
        profile.name = player.getName();
        return profile;
    }

    private static String address(InetSocketAddress socket) {
        return socket == null || socket.getAddress() == null ? null : socket.getAddress().getHostAddress();
    }

    private static void trimAddresses(Profile profile) {
        while (profile.addresses.size() > MAX_ADDRESSES) {
            String oldest = profile.addresses.entrySet().stream()
                .min(Map.Entry.comparingByValue(Comparator.comparingLong(seen -> seen.lastSeen))).orElseThrow().getKey();
            profile.addresses.remove(oldest);
        }
    }

    /**
     * Readable names for well-known mod channel namespaces. Clients announce channels voluntarily, so this is a
     * hint, not proof: a modified client can hide or fake any of it.
     */
    static List<String> modHints(Profile profile) {
        Set<String> found = new TreeSet<>();
        String brand = profile.brand == null ? "" : profile.brand.toLowerCase(Locale.ROOT);
        for (String channel : profile.channels) {
            String namespace = channel.contains(":") ? channel.substring(0, channel.indexOf(':')) : channel;
            String label = KNOWN_MODS.get(namespace.toLowerCase(Locale.ROOT));
            if (label != null) found.add(label);
            else if (!IGNORED_NAMESPACES.contains(namespace.toLowerCase(Locale.ROOT))) found.add(namespace);
        }
        if (brand.contains("lunar")) found.add("Lunar Client");
        if (brand.contains("feather")) found.add("Feather Client");
        if (brand.contains("badlion")) found.add("Badlion Client");
        if (brand.contains("labymod")) found.add("LabyMod");
        return new ArrayList<>(found);
    }

    private static final Set<String> IGNORED_NAMESPACES = Set.of("minecraft", "bungeecord", "velocity", "paper", "c", "common");
    private static final Map<String, String> KNOWN_MODS = Map.ofEntries(
        Map.entry("fabric", "Fabric API"), Map.entry("fabric-screen-handler-api-v1", "Fabric API"),
        Map.entry("fabric-networking-api-v1", "Fabric API"), Map.entry("forge", "Forge"), Map.entry("fml", "Forge"),
        Map.entry("neoforge", "NeoForge"), Map.entry("xaerominimap", "Xaero's Minimap"),
        Map.entry("xaeroworldmap", "Xaero's World Map"), Map.entry("journeymap", "JourneyMap"),
        Map.entry("voicechat", "Simple Voice Chat"), Map.entry("plasmo", "Plasmo Voice"),
        Map.entry("worldedit", "WorldEdit CUI"), Map.entry("wecui", "WorldEdit CUI"), Map.entry("axiom", "Axiom"),
        Map.entry("litematica", "Litematica"), Map.entry("servux", "Servux / MiniHUD"), Map.entry("minihud", "MiniHUD"),
        Map.entry("tweakeroo", "Tweakeroo"), Map.entry("appleskin", "AppleSkin"), Map.entry("jei", "Just Enough Items"),
        Map.entry("rei", "Roughly Enough Items"), Map.entry("emi", "EMI"), Map.entry("jade", "Jade"),
        Map.entry("wthit", "WTHIT"), Map.entry("replaymod", "ReplayMod"), Map.entry("iris", "Iris Shaders"),
        Map.entry("sodium", "Sodium"), Map.entry("lunarclient", "Lunar Client"), Map.entry("lunar", "Lunar Client"),
        Map.entry("labymod", "LabyMod"), Map.entry("labymod3", "LabyMod"), Map.entry("badlion", "Badlion Client"),
        Map.entry("feather", "Feather Client"), Map.entry("schematica", "Schematica"), Map.entry("worlddownloader", "World Downloader"),
        Map.entry("wdl", "World Downloader"), Map.entry("meteor-client", "Meteor Client"), Map.entry("wurst", "Wurst"),
        Map.entry("baritone", "Baritone"), Map.entry("freecam", "Freecam"), Map.entry("flashback", "Flashback"),
        Map.entry("distanthorizons", "Distant Horizons"), Map.entry("voxelmap", "VoxelMap"), Map.entry("bobby", "Bobby"));

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) return;
        for (String raw : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(raw);
            UUID id;
            try { id = UUID.fromString(raw); } catch (IllegalArgumentException invalid) { continue; }
            if (section == null) continue;
            Profile profile = new Profile(section.getLong("tracked-since", System.currentTimeMillis()));
            profile.name = section.getString("name");
            profile.sessions = section.getInt("sessions");
            profile.completedSessions = section.getInt("completed-sessions");
            profile.trackedMillis = section.getLong("tracked-millis");
            profile.longestMillis = section.getLong("longest-millis");
            profile.lastJoin = section.getLong("last-join");
            profile.lastQuit = section.getLong("last-quit");
            profile.brand = section.getString("brand");
            profile.protocol = section.getInt("protocol");
            profile.locale = section.getString("locale");
            profile.viewDistance = section.getInt("view-distance");
            profile.host = section.getString("host");
            profile.channels.addAll(section.getStringList("channels").stream().limit(MAX_CHANNELS).toList());
            ConfigurationSection addresses = section.getConfigurationSection("addresses");
            if (addresses != null && recordAddresses) {
                for (String key : addresses.getKeys(false)) {
                    ConfigurationSection seen = addresses.getConfigurationSection(key);
                    if (seen == null) continue;
                    AddressSeen value = new AddressSeen(seen.getLong("first-seen"));
                    value.lastSeen = seen.getLong("last-seen");
                    value.joins = seen.getInt("joins");
                    ConfigurationSection place = seen.getConfigurationSection("location");
                    if (locateAddresses && place != null) { // dropped on the next save when the feature is off
                        GeoLocation location = new GeoLocation(place.getString("country-code"), place.getString("country"),
                            place.getString("region"), place.getString("city"));
                        if (location.known()) value.location = location;
                    }
                    profile.addresses.put(seen.getString("address", key), value);
                }
            }
            profiles.put(id, profile);
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Staff-only connection history for the Watchdog console. Contains IP addresses: keep private."));
        profiles.forEach((id, profile) -> {
            String path = "players." + id;
            yaml.set(path + ".name", profile.name);
            yaml.set(path + ".tracked-since", profile.trackedSince);
            yaml.set(path + ".sessions", profile.sessions);
            yaml.set(path + ".completed-sessions", profile.completedSessions);
            yaml.set(path + ".tracked-millis", profile.trackedMillis);
            yaml.set(path + ".longest-millis", profile.longestMillis);
            yaml.set(path + ".last-join", profile.lastJoin);
            yaml.set(path + ".last-quit", profile.lastQuit);
            yaml.set(path + ".brand", profile.brand);
            yaml.set(path + ".protocol", profile.protocol);
            yaml.set(path + ".locale", profile.locale);
            yaml.set(path + ".view-distance", profile.viewDistance);
            yaml.set(path + ".host", profile.host);
            yaml.set(path + ".channels", new ArrayList<>(profile.channels));
            int index = 0;
            for (var entry : profile.addresses.entrySet()) {
                // Keys are indexes because IPv6 addresses contain characters YAML paths cannot hold.
                String seen = path + ".addresses." + index++;
                yaml.set(seen + ".address", entry.getKey());
                yaml.set(seen + ".first-seen", entry.getValue().firstSeen);
                yaml.set(seen + ".last-seen", entry.getValue().lastSeen);
                yaml.set(seen + ".joins", entry.getValue().joins);
                GeoLocation location = entry.getValue().location;
                if (location != null) {
                    yaml.set(seen + ".location.country-code", location.countryCode());
                    yaml.set(seen + ".location.country", location.country());
                    yaml.set(seen + ".location.region", location.region());
                    yaml.set(seen + ".location.city", location.city());
                }
            }
        });
        try {
            AtomicYamlFiles.save(yaml, file);
            dirty = false;
        } catch (IOException failure) {
            plugin.getLogger().warning("Could not save connection history: " + failure.getMessage());
        }
    }

    static final class Profile {
        private final long trackedSince;
        private String name;
        private int sessions;
        private int completedSessions;
        private long trackedMillis;
        private long longestMillis;
        private long lastJoin;
        private long lastQuit;
        private String brand;
        private int protocol;
        private String locale;
        private int viewDistance;
        private String host;
        private final Set<String> channels = new TreeSet<>();
        private final Map<String, AddressSeen> addresses = new TreeMap<>();

        Profile(long trackedSince) {
            this.trackedSince = trackedSince;
        }

        /** Test hook: an account whose addresses were last seen at the given times, with optional locations. */
        static Profile withAddresses(Map<String, Long> lastSeen, Map<String, GeoLocation> locations) {
            Profile profile = new Profile(0L);
            lastSeen.forEach((address, time) -> {
                AddressSeen seen = new AddressSeen(time);
                seen.location = locations.get(address);
                profile.addresses.put(address, seen);
            });
            return profile;
        }

        /** Test hook for mod-hint classification. */
        static Profile of(String brand, List<String> channels) {
            Profile profile = new Profile(0L);
            profile.brand = brand;
            profile.channels.addAll(channels);
            return profile;
        }
    }

    private static final class AddressSeen {
        private final long firstSeen;
        private long lastSeen;
        private int joins;
        private GeoLocation location;

        private AddressSeen(long firstSeen) {
            this.firstSeen = firstSeen;
            this.lastSeen = firstSeen;
        }
    }
}
