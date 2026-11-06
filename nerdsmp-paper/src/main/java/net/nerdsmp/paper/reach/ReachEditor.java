package net.nerdsmp.paper.reach;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Reach's editor mode, for staff fixing the map as they walk it. {@code /reach edit} turns it on: creative mode,
 * the protection bypass and a debug stick. While it is on, every block the editor places, breaks, fills, empties,
 * turns with the stick or letters is recorded in the edit overlay ({@link ReachEdits}), so the fix survives the
 * world being generated again. {@code /reach edit undo} takes back the last change, {@code /reach edit revert <r>}
 * puts everything within r blocks back the way the generator made it, and {@code /reach edit note <text>} leaves a
 * located note for a builder to fix in the generator itself. {@code /reach edit} again turns it off.
 */
public final class ReachEditor implements Listener {
    private final NerdSmpPlugin plugin;
    private final ReachService reach;
    private final ReachEdits edits;
    private final File notesFile;
    private final Map<UUID, Session> sessions = new HashMap<>();

    /** One editor's session: what to restore when it ends, and the changes they can still undo, newest last. */
    private static final class Session {
        GameMode previous;
        PermissionAttachment bypass;
        final Deque<List<ReachEdits.Edit>> undo = new ArrayDeque<>();
        int changes;
    }

    ReachEditor(NerdSmpPlugin plugin, ReachService reach, ReachEdits edits) {
        this.plugin = plugin;
        this.reach = reach;
        this.edits = edits;
        this.notesFile = new File(plugin.getDataFolder(), "reach-notes.yml");
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::banner, 20L, 30L);
        Bukkit.getScheduler().runTaskTimer(plugin, edits::save, 100L, 100L);
    }

    boolean editing(Player player) { return sessions.containsKey(player.getUniqueId()); }

    // ------------------------------------------------------------------ commands

    boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Chat.error(sender, "The editor is for players in the Reach."); return true; }
        if (!player.hasPermission("nerdsmp.reach.admin")) { Chat.error(sender, "The Reach editor is for staff."); return true; }
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        switch (sub) {
            case "", "toggle" -> { if (editing(player)) leave(player); else enter(player); }
            case "on" -> enter(player);
            case "off" -> leave(player);
            case "undo" -> undo(player);
            case "revert" -> revert(player, args.length > 2 ? parse(args[2], 4) : 4);
            case "note" -> note(player, args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "");
            case "notes" -> notes(player);
            case "count", "status" -> Chat.info(player, "<gold>Reach edits</gold> <gray>" + edits.size() + " blocks kept over the generated world"
                + (editing(player) ? " · editor on" : "") + "</gray>");
            case "save" -> { edits.save(); Chat.success(player, "Saved " + edits.size() + " edits to reach-edits.yml."); }
            default -> Chat.info(player, "<gray>/reach edit · /reach edit undo · /reach edit revert <radius> · /reach edit note <text> · /reach edit notes · /reach edit count</gray>");
        }
        return true;
    }

    private static int parse(String s, int fallback) {
        try { return Math.max(0, Math.min(32, Integer.parseInt(s))); } catch (NumberFormatException e) { return fallback; }
    }

    private void enter(Player player) {
        if (!reach.isReach(player.getWorld())) { Chat.error(player, "Go to the Reach first (/reach go)."); return; }
        if (editing(player)) { Chat.notice(player, "The editor is already on."); return; }
        Session session = new Session();
        session.previous = player.getGameMode();
        session.bypass = player.addAttachment(plugin, "nerdsmp.claim.bypass", true);
        sessions.put(player.getUniqueId(), session);
        player.setGameMode(GameMode.CREATIVE);
        if (!player.getInventory().contains(Material.DEBUG_STICK)) player.getInventory().addItem(new ItemStack(Material.DEBUG_STICK));
        Chat.success(player, "Editor on. Everything you place, break or turn here is kept over the generated world.");
        player.sendRichMessage("<gray>/reach edit undo · /reach edit revert <radius> · /reach edit note <text> · /reach edit to stop</gray>");
    }

    private void leave(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) { Chat.notice(player, "The editor is off."); return; }
        if (session.bypass != null) player.removeAttachment(session.bypass);
        player.setGameMode(session.previous == null ? GameMode.SURVIVAL : session.previous);
        edits.save();
        Chat.success(player, "Editor off. " + session.changes + " change" + (session.changes == 1 ? "" : "s") + " this session; "
            + edits.size() + " edits kept in all.");
    }

    private void banner() {
        for (Map.Entry<UUID, Session> e : sessions.entrySet()) {
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null) continue;
            player.sendActionBar(Component.text("EDITOR · " + e.getValue().changes + " changes · /reach edit to stop", NamedTextColor.LIGHT_PURPLE));
        }
    }

    // ------------------------------------------------------------------ recording

    private boolean recording(Player player, Block block) { return editing(player) && reach.isReach(block.getWorld()); }

    /**
     * Records the blocks at these positions as they stand a tick from now, after the change has happened, as one
     * undoable change. {@code before} holds what each was before this change, where the event told us.
     */
    private void capture(Player player, Map<Block, String> before) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || before.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<ReachEdits.Edit> undo = new ArrayList<>();
            for (Map.Entry<Block, String> e : before.entrySet()) {
                Block b = e.getKey();
                String after = b.getBlockData().getAsString();
                ReachEdits.Edit old = edits.get(b.getX(), b.getY(), b.getZ());
                String was = e.getValue() != null ? e.getValue() : old != null ? old.after() : after;
                String original = old != null ? old.before() : was;
                List<String> lines = b.getState() instanceof org.bukkit.block.Sign sign ? lines(sign) : null;
                if (lines != null && lines.stream().allMatch(String::isEmpty)) lines = null;
                undo.add(new ReachEdits.Edit(b.getX(), b.getY(), b.getZ(), was, original, old == null ? null : old.lines()));
                edits.put(new ReachEdits.Edit(b.getX(), b.getY(), b.getZ(), after, original, lines));
            }
            session.undo.addLast(undo);
            while (session.undo.size() > 200) session.undo.removeFirst();
            session.changes++;
        });
    }

    private static List<String> lines(org.bukkit.block.Sign sign) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) out.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(sign.getSide(org.bukkit.block.sign.Side.FRONT).line(i)));
        return out;
    }

    private static Map<Block, String> one(Block block, String before) {
        Map<Block, String> m = new LinkedHashMap<>();
        m.put(block, before);
        return m;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!recording(event.getPlayer(), event.getBlock())) return;
        Map<Block, String> before = new LinkedHashMap<>();
        if (event instanceof BlockMultiPlaceEvent multi) for (BlockState s : multi.getReplacedBlockStates()) before.put(s.getBlock(), s.getBlockData().getAsString());
        else before.put(event.getBlock(), event.getBlockReplacedState().getBlockData().getAsString());
        // A placed block can reshape its neighbours (fences joining, stairs turning): keep those as they settle too.
        for (Block n : new Block[] {event.getBlock().getRelative(1, 0, 0), event.getBlock().getRelative(-1, 0, 0), event.getBlock().getRelative(0, 0, 1),
            event.getBlock().getRelative(0, 0, -1), event.getBlock().getRelative(0, 1, 0), event.getBlock().getRelative(0, -1, 0)})
            before.putIfAbsent(n, n.getBlockData().getAsString());
        capture(event.getPlayer(), before);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!recording(event.getPlayer(), event.getBlock())) return;
        Map<Block, String> before = new LinkedHashMap<>();
        Block b = event.getBlock();
        before.put(b, b.getBlockData().getAsString());
        // Doors, beds and tall plants go in pairs, and things standing on the block fall with it.
        for (Block n : new Block[] {b.getRelative(0, 1, 0), b.getRelative(0, -1, 0), b.getRelative(1, 0, 0), b.getRelative(-1, 0, 0),
            b.getRelative(0, 0, 1), b.getRelative(0, 0, -1)}) before.put(n, n.getBlockData().getAsString());
        capture(event.getPlayer(), before);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (recording(event.getPlayer(), event.getBlock())) capture(event.getPlayer(), one(event.getBlock(), event.getBlock().getBlockData().getAsString()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent event) {
        if (recording(event.getPlayer(), event.getBlock())) capture(event.getPlayer(), one(event.getBlock(), event.getBlock().getBlockData().getAsString()));
    }

    /** The debug stick turns a block's state: keep the block as it ends up. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getItem() == null || event.getItem().getType() != Material.DEBUG_STICK) return;
        if (recording(event.getPlayer(), block)) capture(event.getPlayer(), one(block, block.getBlockData().getAsString()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        if (recording(event.getPlayer(), event.getBlock())) capture(event.getPlayer(), one(event.getBlock(), null));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { if (editing(event.getPlayer())) leave(event.getPlayer()); }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) { if (editing(event.getPlayer())) leave(event.getPlayer()); }

    // ------------------------------------------------------------------ undo, revert, notes

    private void undo(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.undo.isEmpty()) { Chat.notice(player, "Nothing to undo."); return; }
        List<ReachEdits.Edit> last = session.undo.removeLast();
        org.bukkit.World world = player.getWorld();
        for (ReachEdits.Edit e : last) {
            Block b = world.getBlockAt(e.x(), e.y(), e.z());
            try { b.setBlockData(Bukkit.createBlockData(e.after()), false); } catch (IllegalArgumentException ignored) { continue; }
            edits.put(new ReachEdits.Edit(e.x(), e.y(), e.z(), e.after(), e.before(), e.lines()));
        }
        session.changes = Math.max(0, session.changes - 1);
        Chat.success(player, "Undid " + last.size() + " block" + (last.size() == 1 ? "" : "s") + ".");
    }

    /** Puts every edited block within {@code radius} back the way the generator made it, and forgets the edits. */
    private void revert(Player player, int radius) {
        Location at = player.getLocation();
        int n = 0;
        for (ReachEdits.Edit e : new ArrayList<>(edits.all())) {
            if (Math.abs(e.x() - at.getBlockX()) > radius || Math.abs(e.y() - at.getBlockY()) > radius || Math.abs(e.z() - at.getBlockZ()) > radius) continue;
            try { player.getWorld().getBlockAt(e.x(), e.y(), e.z()).setBlockData(Bukkit.createBlockData(e.before()), false); }
            catch (IllegalArgumentException ignored) { }
            edits.remove(e.x(), e.y(), e.z());
            n++;
        }
        Chat.success(player, "Put " + n + " block" + (n == 1 ? "" : "s") + " within " + radius + " back as generated.");
    }

    /** A located note for the builder: where, in which build and zone, by whom, and what's wrong. */
    private void note(Player player, String text) {
        if (text.isBlank()) { Chat.error(player, "Say what needs fixing: /reach edit note <text>."); return; }
        Location at = player.getLocation();
        YamlConfiguration notes = YamlConfiguration.loadConfiguration(notesFile);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<?, ?> m : notes.getMapList("notes")) {
            Map<String, Object> copy = new LinkedHashMap<>();
            m.forEach((k, v) -> copy.put(String.valueOf(k), v));
            list.add(copy);
        }
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("at", at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ());
        note.put("facing", at.getYaw());
        note.put("zone", reach.terrain().zoneAt(at.getX(), at.getZ()).title());
        List<String> builds = new ArrayList<>();
        for (ReachFeatures.Feature f : reach.features().features()) if (f.footprint().contains(at.getBlockX(), at.getBlockZ())) builds.add(f.id());
        note.put("builds", builds);
        note.put("by", player.getName());
        note.put("when", Instant.now().toString());
        note.put("text", text);
        list.add(note);
        notes.set("notes", list);
        try {
            notes.save(notesFile);
            Chat.success(player, "Noted at " + note.get("at") + (builds.isEmpty() ? "" : " (" + String.join(", ", builds) + ")") + ".");
        } catch (IOException failure) {
            Chat.error(player, "Could not save the note.");
        }
    }

    private void notes(Player player) {
        YamlConfiguration notes = YamlConfiguration.loadConfiguration(notesFile);
        List<Map<?, ?>> list = notes.getMapList("notes");
        if (list.isEmpty()) { Chat.notice(player, "No notes yet."); return; }
        Chat.info(player, "<gold>Reach notes</gold> <gray>" + list.size() + "</gray>");
        for (int i = Math.max(0, list.size() - 8); i < list.size(); i++) {
            Map<?, ?> n = list.get(i);
            player.sendRichMessage("<gray>" + Chat.escape(String.valueOf(n.get("at"))) + "</gray> <white>" + Chat.escape(String.valueOf(n.get("text"))) + "</white>");
        }
    }
}
