package net.nerdsmp.paper.anticheat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

/**
 * The companion mod's integrity probe. The mod answers a per-join challenge over a plain-looking channel
 * ({@value #CHANNEL}, presented to players as a latency diagnostic) and, in the same message, reports the facts a
 * server can trust only from inside the client: its loaded mod list, its brightness and field-of-view, its loader and
 * its own build. {@link WatchdogService} scores every finding alongside movement and combat evidence, so a forged
 * answer, a stripped probe, a flagged mod or an impossible setting stacks with the rest of a player's record.
 *
 * <p>The challenge carries a fresh nonce and salt; the reply must carry an HMAC over them keyed by a shared secret.
 * This defeats replay and off-the-shelf forgeries, and — combined with the server staying the sole authority on every
 * verdict — is the realistic bar for client telemetry: a determined attacker who decompiles the mod can still extract
 * the secret, which is why no judgement is ever taken on the client's word alone, only scored for staff and the model.
 */
public final class ClientProbe implements Listener, PluginMessageListener, AutoCloseable {
    /** Looks like a round-trip-time diagnostic; the mod registers it under the same cover. */
    static final String CHANNEL = "aerborne:rtt";
    /** The mod's HUD channel: a client that announces it has our mod but not the probe has been tampered with. */
    private static final String HUD_CHANNEL = "aerborne:hud";
    private static final byte VERSION = 1, TYPE_CHALLENGE = 0, TYPE_REPORT = 1;
    private static final int MAX_MODS = 400, MAX_ID = 80;

    /** Shared HMAC root, baked identically into the mod (there XOR-masked so it is not a visible string). */
    private static final byte[] SECRET = {
        (byte) 0x82, (byte) 0x31, (byte) 0xEB, (byte) 0x87, (byte) 0x02, (byte) 0xE9, (byte) 0xB4, (byte) 0x24,
        (byte) 0x69, (byte) 0x6B, (byte) 0x5A, (byte) 0x89, (byte) 0x02, (byte) 0x90, (byte) 0xBB, (byte) 0x56,
        (byte) 0xDA, (byte) 0x5D, (byte) 0xA8, (byte) 0x3D, (byte) 0xAD, (byte) 0x73, (byte) 0xCC, (byte) 0xE8};

    private final NerdSmpPlugin plugin;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    /** The last accepted report per player, for the Staff Console. */
    private final Map<UUID, Report> reports = new ConcurrentHashMap<>();

    private boolean enabled;
    private long timeoutMillis;
    private double forgedWeight, silentWeight, strippedWeight, fullbrightWeight;
    private List<String> flaggedMods = List.of();
    private double flaggedModWeight;
    private BukkitTask sweeper;

    private record Pending(byte[] nonce, byte[] salt, long deadline, boolean modPresent) { }

    /** A client's last self-report. Immutable snapshot for staff display. */
    public record Report(String loader, String loaderVersion, String build, float gamma, float fov,
                         List<String> mods, List<String> flagged, long at) { }

    public ClientProbe(NerdSmpPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("anti-cheat.client-probe");
        enabled = config == null || config.getBoolean("enabled", true);
        if (!enabled) return;
        timeoutMillis = Math.max(2_000L, (config == null ? 10L : config.getLong("response-timeout-seconds", 10L)) * 1_000L);
        forgedWeight = config == null ? 30.0D : config.getDouble("weights.forged", 30.0D);
        silentWeight = config == null ? 15.0D : config.getDouble("weights.no-response", 15.0D);
        strippedWeight = config == null ? 20.0D : config.getDouble("weights.stripped", 20.0D);
        fullbrightWeight = config == null ? 8.0D : config.getDouble("weights.fullbright", 8.0D);
        flaggedModWeight = config == null ? 20.0D : config.getDouble("weights.flagged-mod", 20.0D);
        flaggedMods = (config == null ? List.<String>of() : config.getStringList("flagged-mods"))
            .stream().map(id -> id.toLowerCase(Locale.ROOT).strip()).filter(id -> !id.isEmpty()).toList();
        if (flaggedMods.isEmpty()) flaggedMods = DEFAULT_FLAGGED;

        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) schedule(player);
        sweeper = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 20L, 20L);
    }

    @Override public void close() {
        if (sweeper != null) sweeper.cancel();
        if (!enabled) return;
        try { Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this); } catch (RuntimeException ignored) { }
        try { Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL); } catch (RuntimeException ignored) { }
        pending.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { schedule(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { pending.remove(event.getPlayer().getUniqueId()); reports.remove(event.getPlayer().getUniqueId()); }

    /** The last accepted report for a player, or null. */
    public Report report(UUID playerId) { return reports.get(playerId); }

    // ---- Challenge ----

    private void schedule(Player player) {
        // The client announces its channels in the moments after joining; give it room before the challenge.
        long delay = 70L + random.nextInt(40);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) challenge(player); }, delay);
    }

    private void challenge(Player player) {
        var channels = player.getListeningPluginChannels();
        boolean probe = channels.contains(CHANNEL);
        boolean hud = channels.contains(HUD_CHANNEL);
        if (!probe) {
            // The mod is present but the probe channel is gone: it was stripped out. Vanilla players have neither.
            if (hud) plugin.watchdog().clientSignal(player, strippedWeight, "companion client with its integrity probe removed");
            return;
        }
        byte[] nonce = new byte[16], salt = new byte[8];
        random.nextBytes(nonce); random.nextBytes(salt);
        pending.put(player.getUniqueId(), new Pending(nonce, salt, System.currentTimeMillis() + timeoutMillis, hud));
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(40); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeByte(TYPE_CHALLENGE);
            out.write(nonce);
            out.write(salt);
            out.writeLong(System.currentTimeMillis());
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException ignored) { }
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        for (var entry : List.copyOf(pending.entrySet())) {
            Pending p = entry.getValue();
            if (now < p.deadline()) continue;
            pending.remove(entry.getKey());
            Player player = Bukkit.getPlayer(entry.getKey());
            // Challenged but never answered: a client that advertises the probe channel yet cannot satisfy it.
            if (player != null && player.isOnline())
                plugin.watchdog().clientSignal(player, silentWeight, "companion client did not answer its integrity challenge");
        }
    }

    // ---- Report ----

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || !enabled) return;
        Pending p = pending.remove(player.getUniqueId());
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            if (in.readByte() != VERSION || in.readByte() != TYPE_REPORT) return;
            byte[] nonce = in.readNBytes(16), mac = in.readNBytes(32);
            byte[] body = in.readAllBytes();
            if (nonce.length != 16 || mac.length != 32) return;
            if (p == null || !MessageDigest.isEqual(nonce, p.nonce())) {
                // A reply with no matching outstanding challenge (replayed or unsolicited).
                plugin.watchdog().clientSignal(player, forgedWeight, "companion client sent an unsolicited integrity reply");
                return;
            }
            byte[] expected = mac(p.salt(), concat(nonce, p.salt(), body));
            if (!MessageDigest.isEqual(expected, mac)) {
                plugin.watchdog().clientSignal(player, forgedWeight, "companion client failed its integrity signature (forged or modified build)");
                return;
            }
            evaluate(player, body);
        } catch (IOException | RuntimeException ignored) { }
    }

    private void evaluate(Player player, byte[] body) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
        in.readLong(); // client clock, for the round-trip cover story
        float gamma = in.readFloat();
        float fov = in.readFloat();
        String loader = readCapped(in), loaderVersion = readCapped(in), build = readCapped(in);
        in.readLong(); // self-hash of the mod's own files; authenticity is already proven by the signature
        int count = Math.min(MAX_MODS, Math.max(0, in.readInt()));
        List<String> mods = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = readCapped(in).toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) mods.add(id);
        }
        List<String> flagged = new ArrayList<>();
        for (String id : mods) if (isFlagged(id)) flagged.add(id);
        reports.put(player.getUniqueId(), new Report(loader, loaderVersion, build, gamma, fov, mods, flagged, System.currentTimeMillis()));

        for (String id : flagged) plugin.watchdog().clientSignal(player, flaggedModWeight, "loaded a flagged mod: " + id);
        // Vanilla's brightness slider tops out at gamma 1.0; higher is a full-bright mod forcing it past the limit.
        if (gamma > 1.05F) plugin.watchdog().clientSignal(player, fullbrightWeight,
            String.format(Locale.ROOT, "brightness forced to %.1f (full-bright)", gamma));
    }

    private boolean isFlagged(String id) {
        for (String needle : flaggedMods) if (id.equals(needle) || id.contains(needle)) return true;
        return false;
    }

    // ---- Crypto ----

    private static byte[] mac(byte[] salt, byte[] data) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(SECRET);
            byte[] key = sha.digest(salt);
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key, "HmacSHA256"));
            return hmac.doFinal(data);
        } catch (Exception failure) {
            throw new IllegalStateException("HMAC unavailable", failure);
        }
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) length += part.length;
        byte[] out = new byte[length];
        int offset = 0;
        for (byte[] part : parts) { System.arraycopy(part, 0, out, offset, part.length); offset += part.length; }
        return out;
    }

    private static String readCapped(DataInputStream in) throws IOException {
        String value = in.readUTF();
        return value.length() > MAX_ID ? value.substring(0, MAX_ID) : value;
    }

    /** Fabric ids of common cheat clients, pathing/automation tools and x-ray; overridable in config. */
    private static final List<String> DEFAULT_FLAGGED = List.of(
        "wurst", "meteor-client", "meteorclient", "baritone", "xray", "xraymod", "fullbright", "autoclicker",
        "aristois", "impact", "liquidbounce", "inertia", "rusherhack", "future", "clickcrystals", "killaura",
        "reach", "autototem", "freecam", "nofall", "trajectories", "elytrabot", "autofish", "packetfly");
}
