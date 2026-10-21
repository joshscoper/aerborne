package net.nerdsmp.paper.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Bounded, restart-local chat context and public-channel policy. Never captures private player channels. */
public final class ChatConsole {
    public static final int CAPACITY = 300;
    private static final Set<String> PUBLIC = Set.of("global", "local", "trade");
    private static final Set<String> MONITORED = Set.of("global", "local", "trade", "staff", "broadcast", "direct");
    private final LongSupplier clock;
    private final String started;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Map<UUID, Long> lastMessages = new HashMap<>();
    private Settings settings = new Settings(false, 0);

    public ChatConsole() { this(System::currentTimeMillis); }
    ChatConsole(LongSupplier clock) { this.clock = clock; started = Instant.ofEpochMilli(clock.getAsLong()).toString(); }

    public record Settings(boolean paused, int slowSeconds) {
        public Settings {
            if (slowSeconds < 0 || slowSeconds > 120) throw new IllegalArgumentException("Slow mode must be between 0 and 120 seconds.");
        }
    }

    public static Settings parseSettings(JsonObject request) {
        try {
            var paused = request.get("paused");
            var seconds = request.get("slowSeconds");
            if (paused == null || !paused.isJsonPrimitive() || !paused.getAsJsonPrimitive().isBoolean()
                || seconds == null || !seconds.isJsonPrimitive() || !seconds.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Choose whether to pause chat and a whole number of seconds from 0 to 120.");
            return new Settings(paused.getAsBoolean(), seconds.getAsBigDecimal().intValueExact());
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new IllegalArgumentException("Slow mode must be a whole number of seconds from 0 to 120.");
        }
    }

    public synchronized void configure(Settings next) { settings = next; lastMessages.clear(); }

    /** Call only after normal channel, message and mute validation, immediately before delivering. */
    public synchronized String refuse(UUID player, String channel, boolean staff) {
        if (staff || !PUBLIC.contains(channel)) return null;
        if (settings.paused()) return "Public chat is paused by staff. Please wait before sending another message.";
        long now = clock.getAsLong();
        Long previous = lastMessages.get(player);
        long interval = settings.slowSeconds() * 1000L;
        if (previous != null && interval > 0 && now - previous < interval) {
            long wait = Math.max(1, (interval - Math.max(0, now - previous) + 999) / 1000);
            return "Slow mode is enabled. Wait " + wait + " seconds before sending another public message.";
        }
        if (interval > 0) lastMessages.put(player, now);
        return null;
    }

    public synchronized void forget(UUID player) { lastMessages.remove(player); }

    /** Delivered messages only. Direct entries are outbound panel replies, never intercepted player DMs. */
    public synchronized void record(String channel, UUID player, String sender, String message,
                                    String world, String target, int recipients, boolean panel) {
        if (!MONITORED.contains(channel)) return;
        if (channel.equals("direct") && (!panel || player != null)) return;
        if (message == null || message.isBlank() || message.length() > 512) return;
        entries.addLast(new Entry(UUID.randomUUID().toString(), Instant.ofEpochMilli(clock.getAsLong()).toString(),
            channel, player, sender, message, world, target, Math.max(0, recipients), panel));
        while (entries.size() > CAPACITY) entries.removeFirst();
    }

    public synchronized JsonObject snapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("started", started);
        root.addProperty("capacity", CAPACITY);
        root.addProperty("paused", settings.paused());
        root.addProperty("slowSeconds", settings.slowSeconds());
        JsonArray messages = new JsonArray();
        for (Entry entry : entries) {
            JsonObject row = new JsonObject();
            row.addProperty("id", entry.id()); row.addProperty("time", entry.time());
            row.addProperty("channel", entry.channel()); row.addProperty("uuid", entry.player() == null ? null : entry.player().toString());
            row.addProperty("sender", entry.sender()); row.addProperty("message", entry.message());
            row.addProperty("world", entry.world()); row.addProperty("target", entry.target());
            row.addProperty("recipients", entry.recipients()); row.addProperty("panel", entry.panel());
            messages.add(row);
        }
        root.add("messages", messages);
        return root;
    }

    private record Entry(String id, String time, String channel, UUID player, String sender, String message,
                         String world, String target, int recipients, boolean panel) {}
}
