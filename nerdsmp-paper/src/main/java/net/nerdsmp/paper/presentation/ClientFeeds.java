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
 * <p>{@value #EFFECTS}, version 1: count (int), then per labelled creature that has effects its entity id (int) and
 * effect count (byte), and per effect its key (string, e.g. {@code minecraft:poison}), amplifier (byte) and remaining
 * ticks (int, -1 for no end). Sent when the effects change and every few seconds to resync the client's countdown.
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
    public static final String EFFECTS = "aerborne:effects";
    private static final int EFFECTS_PER_CREATURE = 8;
    private static final long EFFECTS_RESYNC_MILLIS = 3000;
    private static final Map<UUID, EffectsSent> lastEffects = new ConcurrentHashMap<>();

    private record EffectsSent(String signature, long at) { }
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
        owner.getServer().getMessenger().registerOutgoingPluginChannel(owner, EFFECTS);
        for (String channel : List.of(PARTY, TOAST, QUESTS, SKILLS, TRACKER, SOCIAL, FLIGHT)) owner.getServer().getMessenger().registerOutgoingPluginChannel(owner, channel);
    }

    static void stop() { plugin = null; lastPlates.clear(); lastParty.clear(); lastEffects.clear(); }

    static void forget(UUID player) { lastPlates.remove(player); lastParty.remove(player); lastTracker.remove(player); lastEffects.remove(player); }

    public static boolean listens(Player player, String channel) {
        return plugin != null && player != null && player.getListeningPluginChannels().contains(channel);
    }

    /** Sends the creatures this player's client should label, only when the list has changed. */
    public static void plates(Player viewer, List<LivingEntity> targets, java.util.function.Function<LivingEntity, Component> name,
                              java.util.function.Predicate<LivingEntity> boss, java.util.function.Predicate<LivingEntity> elite) {
        plates(viewer, targets, name, boss, elite, target -> false);
    }

    /**
     * Plates for these creatures. Flags: 1 boss, 2 elite, 4 a townsperson (NPC): the companion names it in the target
     * frame but draws no health plate over it, since NPCs already wear their own name.
     */
    public static void plates(Player viewer, List<LivingEntity> targets, java.util.function.Function<LivingEntity, Component> name,
                              java.util.function.Predicate<LivingEntity> boss, java.util.function.Predicate<LivingEntity> elite,
                              java.util.function.Predicate<LivingEntity> npc) {
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
                out.writeByte((boss.test(target) ? 1 : 0) | (elite.test(target) ? 2 : 0) | (npc.test(target) ? 4 : 0));
            }
            byte[] encoded = bytes.toByteArray();
            byte[] previous = lastPlates.get(viewer.getUniqueId());
            if (previous != null && Arrays.equals(previous, encoded)) return;
            lastPlates.put(viewer.getUniqueId(), encoded);
            viewer.sendPluginMessage(owner, PLATES, encoded);
        } catch (IOException | RuntimeException ignored) { }
    }

    /**
     * Sends the status effects on the creatures this player's client labels, so its nameplates and target frame can
     * show them (a client only sees other creatures' effect particles, not the effects). Sent when the set of effects
     * changes, and otherwise every few seconds so the client's countdown stays true.
     */
    public static void effects(Player viewer, List<LivingEntity> targets) {
        Plugin owner = plugin;
        if (owner == null || !listens(viewer, EFFECTS)) return;
        try {
            StringBuilder signature = new StringBuilder();
            List<LivingEntity> affected = targets.stream().filter(target -> !target.getActivePotionEffects().isEmpty()).toList();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(16 + affected.size() * 64);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(VERSION);
            out.writeInt(affected.size());
            for (LivingEntity target : affected) {
                List<org.bukkit.potion.PotionEffect> active = target.getActivePotionEffects().stream()
                    .limit(EFFECTS_PER_CREATURE).toList();
                out.writeInt(target.getEntityId());
                out.writeByte(active.size());
                signature.append(target.getEntityId()).append(':');
                for (org.bukkit.potion.PotionEffect effect : active) {
                    String key = effect.getType().getKey().toString();
                    writeString(out, key);
                    out.writeByte(Math.clamp(effect.getAmplifier(), 0, 127));
                    out.writeInt(effect.isInfinite() ? -1 : effect.getDuration());
                    signature.append(key).append('/').append(effect.getAmplifier()).append(',');
                }
                signature.append(';');
            }
            long now = System.currentTimeMillis();
            EffectsSent previous = lastEffects.get(viewer.getUniqueId());
            String sig = signature.toString();
            if (previous != null && previous.signature().equals(sig)
                && (sig.isEmpty() || now - previous.at() < EFFECTS_RESYNC_MILLIS)) return;
            lastEffects.put(viewer.getUniqueId(), new EffectsSent(sig, now));
            viewer.sendPluginMessage(owner, EFFECTS, bytes.toByteArray());
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

    // ------------------------------------------------------------------ party, toasts, quest book

    public static final String PARTY = "aerborne:party";
    public static final String TOAST = "aerborne:toast";
    public static final String QUESTS = "aerborne:quests";
    /** The Skills screen (MenuManager.sendSkills): see that method for the format. */
    public static final String SKILLS = "aerborne:skills";
    /** The tracked quest with its next target (QuestGuide.sendTracker). */
    public static final String TRACKER = "aerborne:tracker";
    /** The Social screen (SocialManager.sendSocial). */
    public static final String SOCIAL = "aerborne:social";
    /**
     * A flight between roosts (ReachFlights), version 1: active (boolean); when active the destination (string),
     * distance flown and total distance (floats, blocks) and seconds left (float). Sent as the flight starts, once a
     * second while it lasts, and with active false when it ends; the client eases the timer between updates.
     */
    public static final String FLIGHT = "aerborne:flight";

    public static void flight(Player player, String destination, double travelled, double total, double secondsLeft) {
        if (!listens(player, FLIGHT)) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(1);
            out.writeBoolean(destination != null);
            if (destination != null) {
                writeString(out, clean(destination));
                out.writeFloat((float) travelled); out.writeFloat((float) total); out.writeFloat((float) Math.max(0, secondsLeft));
            }
            send(player, FLIGHT, bytes.toByteArray());
        } catch (IOException ignored) { }
    }
    private static final Map<UUID, byte[]> lastTracker = new ConcurrentHashMap<>();
    private static final Map<UUID, byte[]> lastParty = new ConcurrentHashMap<>();

    /** One party member as the party frames show them. {@code distance} is -1 when offline or in another world. */
    public record Member(String name, boolean leader, boolean online, float health, float maxHealth, int distance) { }

    /**
     * {@value #PARTY}, version 1: member count (int), then per member their name (string), leader and online
     * (booleans), health and max health (floats) and distance in blocks (int, -1 when not in this world).
     * The player themself is left out; an empty list means no party. Sent only when it changes.
     */
    static void party(Player player, List<Member> members) {
        Plugin owner = plugin;
        if (owner == null || !listens(player, PARTY)) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 + members.size() * 32);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(VERSION);
            out.writeInt(Math.min(16, members.size()));
            for (Member member : members.subList(0, Math.min(16, members.size()))) {
                writeString(out, clean(member.name()));
                out.writeBoolean(member.leader());
                out.writeBoolean(member.online());
                out.writeFloat(member.health());
                out.writeFloat(member.maxHealth());
                out.writeInt(member.distance());
            }
            byte[] encoded = bytes.toByteArray();
            byte[] previous = lastParty.get(player.getUniqueId());
            if (previous != null && Arrays.equals(previous, encoded)) return;
            lastParty.put(player.getUniqueId(), encoded);
            player.sendPluginMessage(owner, PARTY, encoded);
        } catch (IOException | RuntimeException ignored) { }
    }

    /**
     * {@value #TOAST}, version 1: kind (string: level, skill, achievement, quest, drop), title and line (strings,
     * legacy colours). Returns false when this player's client shows no toasts, so the caller shows its title.
     */
    public static boolean toast(Player player, String kind, Component title, Component line) {
        Plugin owner = plugin;
        if (owner == null || player == null || !listens(player, TOAST)) return false;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(VERSION);
            writeString(out, kind);
            writeString(out, LEGACY.serialize(title));
            writeString(out, LEGACY.serialize(line));
            player.sendPluginMessage(owner, TOAST, bytes.toByteArray());
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    /** Sends the encoded tracker only when it has changed since the last one. */
    public static void tracker(Player player, byte[] encoded) {
        byte[] previous = lastTracker.get(player.getUniqueId());
        if (previous != null && Arrays.equals(previous, encoded)) return;
        if (send(player, TRACKER, encoded)) lastTracker.put(player.getUniqueId(), encoded);
    }

    /** Sends an already encoded feed on a channel; false when this player's client does not listen on it. */
    public static boolean send(Player player, String channel, byte[] encoded) {
        Plugin owner = plugin;
        if (owner == null || !listens(player, channel)) return false;
        player.sendPluginMessage(owner, channel, encoded);
        return true;
    }

    /** Sends an already encoded quest book (see QuestService.sendBook); false when the client has no quest book. */
    public static boolean questBook(Player player, byte[] encoded) {
        Plugin owner = plugin;
        if (owner == null || !listens(player, QUESTS)) return false;
        player.sendPluginMessage(owner, QUESTS, encoded);
        return true;
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

    public static String clean(String text) {
        String value = text == null ? "" : text.replaceAll("[\\p{Cntrl}\\u00a7]", " ").strip();
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    public static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] utf = value.getBytes(StandardCharsets.UTF_8);
        int length = utf.length;
        while ((length & ~0x7F) != 0) { out.writeByte((length & 0x7F) | 0x80); length >>>= 7; }
        out.writeByte(length);
        out.write(utf);
    }
}
