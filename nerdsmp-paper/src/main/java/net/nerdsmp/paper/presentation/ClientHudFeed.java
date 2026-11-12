package net.nerdsmp.paper.presentation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Feeds players running the Aerborne client their real stats, so the client draws its own HUD instead of the
 * plugin's action bar and sidebar. A player counts as modded once their client listens on {@link #CHANNEL};
 * for them the action bar vitals and the sidebar are skipped, and passing notices travel in the feed instead.
 *
 * <p>Wire format (big-endian, strings as a VarInt byte length then UTF-8, as Minecraft writes them), version 1:
 * health, max health, absorption, defense (floats); recovery held back (boolean); food status (string);
 * Adventurer level (int), XP, XP needed (longs), rank (string); Marks (long); progress title, progress line
 * (strings); overlay title (string, empty for none) and its lines (int count, strings); appendix lines
 * (int count, strings); notice (string, empty for none). Version 2 adds the skill toast: its line (string, empty for
 * none) and how far through the level the skill is (float). Text carries legacy section-sign colours.
 */
public final class ClientHudFeed implements Listener, AutoCloseable {
    public static final String CHANNEL = "aerborne:hud";
    private static final int VERSION = 2;
    private static final long SKILL_NANOS = 3_500_000_000L;
    private static final long NOTICE_NANOS = 3_000_000_000L;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static volatile ClientHudFeed active;

    private final NerdSmpPlugin plugin;
    private final Map<UUID, byte[]> lastSent = new ConcurrentHashMap<>();
    private final Map<UUID, Notice> notices = new ConcurrentHashMap<>();
    private final Map<UUID, Skill> skills = new ConcurrentHashMap<>();
    private final Set<UUID> known = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    private record Notice(String text, long until) { }
    private record Skill(String text, float progress, long until) { }

    public ClientHudFeed(NerdSmpPlugin plugin) { this.plugin = plugin; }

    public void start() {
        active = this;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        ClientFeeds.start(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::send), 20L, 10L);
    }

    /** True when this player's client draws the Aerborne HUD itself. */
    public static boolean modded(Player player) {
        return active != null && player.getListeningPluginChannels().contains(CHANNEL);
    }

    /** Hands a passing notice (XP gained, a catch, a warning) to the client in place of the action bar. */
    public static void notice(Player player, Component message) {
        ClientHudFeed feed = active;
        if (feed == null) return;
        feed.notices.put(player.getUniqueId(), new Notice(LEGACY.serialize(message), System.nanoTime() + NOTICE_NANOS));
        feed.send(player);
    }

    /** Shows skill experience in the client's HUD in place of the plugin's boss bar; false when the client has no HUD. */
    public static boolean skill(Player player, Component text, double progress) {
        ClientHudFeed feed = active;
        if (feed == null || !modded(player)) return false;
        feed.skills.put(player.getUniqueId(), new Skill(LEGACY.serialize(text), (float) Math.clamp(progress, 0D, 1D), System.nanoTime() + SKILL_NANOS));
        feed.send(player);
        return true;
    }

    private void send(Player player) {
        if (!player.isOnline() || !modded(player)) {
            known.remove(player.getUniqueId());
            return;
        }
        // The first time a client is seen, redraw its sidebar so the plugin's one disappears straight away.
        if (known.add(player.getUniqueId()) && plugin.presentation() != null) plugin.presentation().refresh(player);
        try { sendParty(player); } catch (RuntimeException ignored) { }
        byte[] bytes;
        try { bytes = encode(player); } catch (IOException | RuntimeException failure) { return; }
        byte[] previous = lastSent.get(player.getUniqueId());
        if (previous != null && Arrays.equals(previous, bytes)) return;
        lastSent.put(player.getUniqueId(), bytes);
        player.sendPluginMessage(plugin, CHANNEL, bytes);
    }

    /** The player's party as the client's party frames show it; ClientFeeds sends it only when it changes. */
    private void sendParty(Player player) {
        var parties = plugin.partyManager();
        var account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        List<ClientFeeds.Member> members = new java.util.ArrayList<>();
        if (parties != null && account != null) {
            var details = parties.roster().details(account).orElse(null);
            if (details != null) for (var member : details.members()) {
                if (member.equals(account)) continue;
                Player online = plugin.sessions().player(member).map(Bukkit::getPlayer).filter(Player::isOnline).orElse(null);
                String name = online != null ? online.getName() : plugin.social().accountName(member);
                if (online == null) { members.add(new ClientFeeds.Member(name, member.equals(details.leader()), false, 0, 0, -1)); continue; }
                var maximum = online.getAttribute(Attribute.MAX_HEALTH);
                int distance = online.getWorld().equals(player.getWorld()) ? (int) Math.round(online.getLocation().distance(player.getLocation())) : -1;
                members.add(new ClientFeeds.Member(name, member.equals(details.leader()), true, (float) online.getHealth(),
                    (float) (maximum == null ? 20 : maximum.getValue()), distance));
            }
        }
        ClientFeeds.party(player, members);
    }

    private byte[] encode(Player player) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        DataOutputStream out = new DataOutputStream(bytes);
        var presentation = plugin.presentation();
        var maximum = player.getAttribute(Attribute.MAX_HEALTH);
        out.writeInt(VERSION);
        out.writeFloat((float) player.getHealth());
        out.writeFloat((float) (maximum == null ? 20 : maximum.getValue()));
        out.writeFloat((float) player.getAbsorptionAmount());
        double defense = plugin.playerStats() != null && presentation.progressionReady(player) ? plugin.playerStats().snapshot(player).defense() : 0;
        out.writeFloat((float) defense);
        var recovery = plugin.recovery();
        out.writeBoolean(recovery != null && recovery.delayed(player));
        writeString(out, recovery == null ? "" : legacy(recovery.status(player).replace("<dark_gray>│</dark_gray>", "").trim()));
        var view = presentation.view(player);
        out.writeInt(view.adventurerLevel());
        out.writeLong(view.adventurerExperience());
        out.writeLong(view.adventurerExperienceNeeded());
        writeString(out, view.adventurerRank());
        out.writeLong(view.marks());
        var progress = presentation.progressFor(player, view);
        writeString(out, legacy(progress.get(0)));
        writeString(out, legacy(progress.get(1)));
        var overlay = presentation.overlayFor(player);
        writeString(out, overlay == null ? "" : legacy(overlay.getKey()));
        writeLines(out, overlay == null ? List.of() : overlay.getValue());
        writeLines(out, presentation.appendixFor(player));
        Notice notice = notices.get(player.getUniqueId());
        if (notice != null && notice.until() < System.nanoTime()) { notices.remove(player.getUniqueId()); notice = null; }
        writeString(out, notice == null ? "" : notice.text());
        Skill skill = skills.get(player.getUniqueId());
        if (skill != null && skill.until() < System.nanoTime()) { skills.remove(player.getUniqueId()); skill = null; }
        writeString(out, skill == null ? "" : skill.text());
        out.writeFloat(skill == null ? 0F : skill.progress());
        out.flush();
        return bytes.toByteArray();
    }

    private static String legacy(String miniMessage) { return miniMessage.isEmpty() ? "" : LEGACY.serialize(Chat.render(miniMessage)); }

    private static void writeLines(DataOutputStream out, List<String> lines) throws IOException {
        List<String> shown = lines.stream().filter(line -> !line.isBlank()).limit(16).toList();
        out.writeInt(shown.size());
        for (String line : shown) writeString(out, legacy(line));
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] utf = value.getBytes(StandardCharsets.UTF_8);
        int length = utf.length;
        while ((length & ~0x7F) != 0) { out.writeByte((length & 0x7F) | 0x80); length >>>= 7; }
        out.writeByte(length);
        out.write(utf);
    }

    /** Tags the plugin's own menus as they open, so the Aerborne client draws them in its own frame. */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onMenuOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        var inventory = event.getInventory();
        var holder = inventory.getHolder(false);
        // Plugin menus are chests held by a NerdSMP class, or by nothing at all; real containers have a block holder.
        boolean ours = holder == null ? inventory.getType() == org.bukkit.event.inventory.InventoryType.CHEST
            : holder.getClass().getName().startsWith("net.nerdsmp.");
        if (!ours) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        // The full class name: holders are often generic inner classes (Page, Board), so the outer class says whose menu it is.
        ClientFeeds.menu(player, holder == null ? "menu" : holder.getClass().getName(), title);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastSent.remove(id); notices.remove(id); known.remove(id); skills.remove(id);
        ClientFeeds.forget(id);
    }

    @Override public void close() {
        if (task != null) task.cancel();
        if (active == this) active = null;
        ClientFeeds.stop();
        lastSent.clear(); notices.clear(); known.clear(); skills.clear();
    }
}
