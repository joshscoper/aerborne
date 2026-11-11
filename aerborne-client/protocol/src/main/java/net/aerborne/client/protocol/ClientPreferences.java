package net.aerborne.client.protocol;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local UI preferences and personal waypoints. No gameplay state or credentials. */
public final class ClientPreferences {
    public enum Theme {
        SKY("Sky", 0xFF77C8F5), RELIC("Relic", 0xFFF4C76B), CELESTIAL("Celestial", 0xFFC59AFA);
        public final String label;
        public final int accent;
        Theme(String label, int accent) { this.label = label; this.accent = accent; }
    }
    public record Waypoint(String id, String scope, String name, int x, int y, int z) {
        public Waypoint {
            if (id == null || id.isBlank() || scope == null || scope.isBlank() || scope.length() > 512
                    || name == null || name.isBlank() || name.length() > 32
                    || name.chars().anyMatch(Character::isISOControl)
                    || Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000
                    || y < -2048 || y > 2048) throw new IllegalArgumentException("Invalid waypoint");
        }
        public double distance(double px, double py, double pz) {
            return Math.sqrt(Math.pow(x - px, 2) + Math.pow(y - py, 2) + Math.pow(z - pz, 2));
        }
    }
    public static final String SERVER = "play.aerborne.net";
    public Theme theme = Theme.SKY;
    public boolean hud = true, coordinates = true, durability = true, topRight = false, serverSeeded;
    /** The compass strip, the minimap and the frame for the creature under the crosshair. */
    public boolean compass = true, minimap = true, target = true;
    /** The companion features, each of which can be switched off (see FeaturesScreen). */
    public boolean effects = true, vignette = true, damageNumbers = true, session = true, waypointMarker = true,
            rarity = true, deathWaypoint = true, chatTimestamps = true, chatMentions = true, chatStack = true,
            chatCoordinates = true, partyFrames = true;
    /** Player inventory slots (0-35, 40 off hand) the player has locked against dropping and moving. */
    public final Set<Integer> lockedSlots = new TreeSet<>();
    public int scale = 100;
    public String tracked = "";
    public final List<Waypoint> waypoints = new ArrayList<>();

    public static boolean isDefaultServer(String address) {
        if (address == null) return false;
        String normalized = address.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(":25565")) normalized = normalized.substring(0, normalized.length() - 6);
        if (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        return SERVER.equals(normalized);
    }

    public Optional<Waypoint> trackedFor(String scope) {
        return waypoints.stream().filter(w -> w.id().equals(tracked) && w.scope().equals(scope)).findFirst();
    }

    public static ClientPreferences load(Path path) throws IOException {
        ClientPreferences result = new ClientPreferences();
        if (!Files.exists(path)) return result;
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { data.load(reader); }
        try { result.theme = Theme.valueOf(data.getProperty("theme", "SKY")); } catch (IllegalArgumentException ignored) { }
        result.hud = bool(data, "hud", true);
        result.coordinates = bool(data, "coordinates", true);
        result.durability = bool(data, "durability", true);
        result.topRight = bool(data, "topRight", false);
        result.compass = bool(data, "compass", true);
        result.minimap = bool(data, "minimap", true);
        result.target = bool(data, "target", true);
        result.serverSeeded = bool(data, "serverSeeded", false);
        result.effects = bool(data, "effects", true);
        result.vignette = bool(data, "vignette", true);
        result.damageNumbers = bool(data, "damageNumbers", true);
        result.session = bool(data, "session", true);
        result.waypointMarker = bool(data, "waypointMarker", true);
        result.rarity = bool(data, "rarity", true);
        result.deathWaypoint = bool(data, "deathWaypoint", true);
        result.chatTimestamps = bool(data, "chatTimestamps", true);
        result.chatMentions = bool(data, "chatMentions", true);
        result.chatStack = bool(data, "chatStack", true);
        result.chatCoordinates = bool(data, "chatCoordinates", true);
        result.partyFrames = bool(data, "partyFrames", true);
        for (String slot : data.getProperty("lockedSlots", "").split(",")) {
            try { int value = Integer.parseInt(slot.trim()); if (value >= 0 && value <= 40) result.lockedSlots.add(value); }
            catch (NumberFormatException ignored) { }
        }
        result.scale = Math.max(75, Math.min(125, number(data, "scale", 100)));
        result.tracked = data.getProperty("tracked", "");
        int count = Math.max(0, Math.min(32, number(data, "waypoints", 0)));
        for (int i = 0; i < count; i++) {
            String p = "waypoint." + i + ".";
            try {
                result.waypoints.add(new Waypoint(data.getProperty(p + "id"), data.getProperty(p + "scope"),
                        data.getProperty(p + "name"), Integer.parseInt(data.getProperty(p + "x")),
                        Integer.parseInt(data.getProperty(p + "y")), Integer.parseInt(data.getProperty(p + "z"))));
            } catch (RuntimeException ignored) { /* A damaged row must not discard the remaining saved waypoints. */ }
        }
        return result;
    }

    public void save(Path path) throws IOException {
        Properties data = new Properties();
        data.setProperty("theme", theme.name());
        data.setProperty("hud", Boolean.toString(hud));
        data.setProperty("coordinates", Boolean.toString(coordinates));
        data.setProperty("durability", Boolean.toString(durability));
        data.setProperty("topRight", Boolean.toString(topRight));
        data.setProperty("compass", Boolean.toString(compass));
        data.setProperty("minimap", Boolean.toString(minimap));
        data.setProperty("target", Boolean.toString(target));
        data.setProperty("serverSeeded", Boolean.toString(serverSeeded));
        data.setProperty("effects", Boolean.toString(effects));
        data.setProperty("vignette", Boolean.toString(vignette));
        data.setProperty("damageNumbers", Boolean.toString(damageNumbers));
        data.setProperty("session", Boolean.toString(session));
        data.setProperty("waypointMarker", Boolean.toString(waypointMarker));
        data.setProperty("rarity", Boolean.toString(rarity));
        data.setProperty("deathWaypoint", Boolean.toString(deathWaypoint));
        data.setProperty("chatTimestamps", Boolean.toString(chatTimestamps));
        data.setProperty("chatMentions", Boolean.toString(chatMentions));
        data.setProperty("chatStack", Boolean.toString(chatStack));
        data.setProperty("chatCoordinates", Boolean.toString(chatCoordinates));
        data.setProperty("partyFrames", Boolean.toString(partyFrames));
        data.setProperty("lockedSlots", String.join(",", lockedSlots.stream().map(String::valueOf).toList()));
        data.setProperty("scale", Integer.toString(scale));
        data.setProperty("tracked", tracked);
        data.setProperty("waypoints", Integer.toString(waypoints.size()));
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint w = waypoints.get(i);
            String p = "waypoint." + i + ".";
            data.setProperty(p + "id", w.id()); data.setProperty(p + "scope", w.scope());
            data.setProperty(p + "name", w.name()); data.setProperty(p + "x", Integer.toString(w.x()));
            data.setProperty(p + "y", Integer.toString(w.y())); data.setProperty(p + "z", Integer.toString(w.z()));
        }
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "aerborne-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                data.store(writer, "Aerborne client preferences");
            }
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        return Boolean.parseBoolean(p.getProperty(key, Boolean.toString(fallback)));
    }
    private static int number(Properties p, String key, int fallback) {
        try { return Integer.parseInt(p.getProperty(key)); } catch (RuntimeException ignored) { return fallback; }
    }
}
