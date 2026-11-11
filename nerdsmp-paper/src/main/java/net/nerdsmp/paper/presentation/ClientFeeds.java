package net.nerdsmp.paper.presentation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.nerdsmp.paper.npc.chat.ChatDialogueRenderer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The Aerborne client's other feeds, beside {@link ClientHudFeed}: names for the creatures it draws nameplates over,
 * and NPC conversations it shows in its own dialogue panel. A client gets a feed only if it listens on the channel.
 *
 * <p>{@value #PLATES}, version 1: count (int), then per creature its entity id (int), name (string, legacy colours)
 * and flags (byte: 1 boss, 2 named/elite creature). Health comes from the entity itself on the client.
 *
 * <p>{@value #DIALOGUE}, version 1: reply token (string; a page with an empty name closes the conversation with that
 * token), NPC name, title (strings), body paragraphs (int count, strings), replies (int count; per reply its text,
 * available and highlighted booleans, and detail lines as an int count and strings). Replies go back as
 * {@code /npc reply <token> <n>}, leaving as {@code /npc bye <token>}.
 *
 * <p>{@value #MENU}, version 1: sent as one of the plugin's chest menus opens; the menu's kind (its holder class)
 * and plain title (strings). The client draws the chest screen with that title in the Aerborne frame.
 */
public final class ClientFeeds {
    public static final String PLATES = "aerborne:plates";
    public static final String DIALOGUE = "aerborne:dialogue";
    public static final String MENU = "aerborne:menu";
    private static final int VERSION = 1;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Map<UUID, byte[]> lastPlates = new ConcurrentHashMap<>();
    private static volatile Plugin plugin;

    private ClientFeeds() { }

    static void start(Plugin owner) {
        plugin = owner;
        owner.getServer().getMessenger().registerOutgoingPluginChannel(owner, PLATES);
        owner.getServer().getMessenger().registerOutgoingPluginChannel(owner, DIALOGUE);
        owner.getServer().getMessenger().registerOutgoingPluginChannel(owner, MENU);
    }

    static void stop() { plugin = null; lastPlates.clear(); }

    static void forget(UUID player) { lastPlates.remove(player); }

    public static boolean listens(Player player, String channel) {
        return plugin != null && player != null && player.getListeningPluginChannels().contains(channel);
    }

    /** Sends the creatures this player's client should label, only when the list has changed. */
    public static void plates(Player viewer, List<LivingEntity> targets, java.util.function.Function<LivingEntity, Component> name,
                              java.util.function.Predicate<LivingEntity> boss, java.util.function.Predicate<LivingEntity> elite) {
        Plugin owner = plugin;
        if (owner == null) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 + targets.size() * 24);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(VERSION);
            out.writeInt(targets.size());
            for (LivingEntity target : targets) {
                out.writeInt(target.getEntityId());
                String label = LEGACY.serialize(name.apply(target));
                // Gradient names carry a colour code per letter; the client accepts up to 2048 characters.
                writeString(out, label.length() > 2000 ? label.substring(0, 2000) : label);
                out.writeByte((boss.test(target) ? 1 : 0) | (elite.test(target) ? 2 : 0));
            }
            byte[] encoded = bytes.toByteArray();
            byte[] previous = lastPlates.get(viewer.getUniqueId());
            if (previous != null && Arrays.equals(previous, encoded)) return;
            lastPlates.put(viewer.getUniqueId(), encoded);
            viewer.sendPluginMessage(owner, PLATES, encoded);
        } catch (IOException | RuntimeException ignored) { }
    }

    /** Shows a conversation page in the client's panel; false when this player's client has none (use chat). */
    public static boolean dialogue(Player player, UUID token, String name, String title, List<String> body,
                                   List<ChatDialogueRenderer.Choice> choices) {
        Plugin owner = plugin;
        if (owner == null || !listens(player, DIALOGUE)) return false;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(VERSION);
            writeString(out, token.toString());
            writeString(out, clean(name));
            writeString(out, clean(title));
            List<String> paragraphs = body.stream().map(ClientFeeds::clean).limit(24).toList();
            out.writeInt(paragraphs.size());
            for (String paragraph : paragraphs) writeString(out, paragraph);
            out.writeInt(Math.min(5, choices.size()));
            for (ChatDialogueRenderer.Choice choice : choices.subList(0, Math.min(5, choices.size()))) {
                writeString(out, clean(choice.text()));
                out.writeBoolean(choice.available());
                out.writeBoolean(choice.highlighted());
                List<String> details = (choice.available() ? choice.hints() : choice.lockedReasons()).stream()
                    .map(ClientFeeds::clean).distinct().limit(6).toList();
                out.writeInt(details.size());
                for (String detail : details) writeString(out, detail);
            }
            player.sendPluginMessage(owner, DIALOGUE, bytes.toByteArray());
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    /** Tells the client the chest screen about to open is one of the plugin's menus. */
    static void menu(Player player, String kind, String title) {
        Plugin owner = plugin;
        if (owner == null || !listens(player, MENU)) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(VERSION);
            writeString(out, clean(kind));
            writeString(out, title.length() > 200 ? title.substring(0, 200) : title);
            player.sendPluginMessage(owner, MENU, bytes.toByteArray());
        } catch (IOException | RuntimeException ignored) { }
    }

    /** Closes the client's panel if it still shows the conversation with this token. */
    public static void endDialogue(Player player, UUID token) {
        Plugin owner = plugin;
        if (owner == null || token == null || !listens(player, DIALOGUE)) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(VERSION);
            writeString(out, token.toString());
            writeString(out, "");
            writeString(out, "");
            out.writeInt(0);
            out.writeInt(0);
            player.sendPluginMessage(owner, DIALOGUE, bytes.toByteArray());
        } catch (IOException | RuntimeException ignored) { }
    }

    private static String clean(String text) {
        String value = text == null ? "" : text.replaceAll("[\\p{Cntrl}\\u00a7]", " ").strip();
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] utf = value.getBytes(StandardCharsets.UTF_8);
        int length = utf.length;
        while ((length & ~0x7F) != 0) { out.writeByte((length & 0x7F) | 0x80); length >>>= 7; }
        out.writeByte(length);
        out.write(utf);
    }
}
