package net.nerdsmp.paper.zone;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import static net.nerdsmp.paper.zone.ZoneDefinition.*;

/** Staff-only manual region editor. It never changes terrain, moves players or spawns mobs. */
public final class ZoneCommand {
    public static final String PERMISSION = "nerdsmp.zone.admin";
    private static final List<String> ACTIONS = List.of("wand", "selection", "clear", "pos1", "pos2", "create", "list", "info", "catalog", "spawn", "title", "point", "mob", "cap", "respawn", "radius", "safe", "enable", "disable", "delete");
    interface Context {
        World world(UUID id);
        boolean islandWorld(World world);
        void validateMob(String id);
        List<String> customMobIds();
        void saveFailed(IOException error);
    }
    private final Context context;
    private final ZoneStore store;
    private final AreaSelection selections = new AreaSelection();
    private AreaWand wand;
    public AreaSelection selections() { return selections; }
    public void wand(AreaWand wand) { this.wand=wand; }

    public ZoneCommand(NerdSmpPlugin plugin, ZoneStore store) {
        this(new Context() {
            public World world(UUID id) { return Bukkit.getWorld(id); }
            public boolean islandWorld(World world) { return plugin.skyblock() != null && plugin.skyblock().isIslandWorld(world); }
            public void validateMob(String id) { plugin.mobs().zoneType(id); }
            public List<String> customMobIds() { return plugin.mobs().ids(); }
            public void saveFailed(IOException error) { plugin.getLogger().log(Level.SEVERE, "Could not save staff zone edit; previous snapshot remains active", error); }
        }, store);
    }
    ZoneCommand(Context context, ZoneStore store) { this.context = context; this.store = store; }

    public boolean command(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) { fail(sender, "You do not have permission to manage zones."); return true; }
        try {
            if (args.length == 0) { help(sender); return true; }
            String action = args[0].toLowerCase(Locale.ROOT);
            if (action.equals("wand")) {
                requireLength(args,1,"/zone wand");
                if (wand==null) throw new IllegalArgumentException("The area wand is unavailable.");
                wand.give(player(sender)); return true;
            }
            if (action.equals("selection") || action.equals("clear")) {
                requireLength(args,1,"/zone "+action); Player actor=player(sender);
                if(action.equals("clear")) { selections.clear(actor.getUniqueId()); ok(sender,"Area selection cleared. No saved areas were changed."); }
                else { showSelection(actor); if(wand!=null) wand.preview(actor); }
                return true;
            }
            if (action.equals("pos1") || action.equals("pos2")) {
                if (args.length != 1 && !(args.length == 2 && args[1].equalsIgnoreCase("target")))
                    throw usage("/zone " + action + " [target]");
                select(sender, action.equals("pos1"), args.length == 2); return true;
            }
            if (action.equals("list")) { list(sender, args); return true; }
            if (action.equals("catalog")) { catalog(sender, args); return true; }
            if (action.equals("create")) { requireLength(args, 2, "/zone create <id>"); create(sender, args[1]); return true; }
            if (!ACTIONS.contains(action) || args.length < 2) { help(sender); return true; }
            ZoneDefinition zone = store.find(args[1]).orElseThrow(() -> new IllegalArgumentException("No zone named " + args[1] + "."));
            ZoneDefinition next;
            switch (action) {
                case "info" -> { requireLength(args, 2, "/zone info <id>"); info(sender, zone); return true; }
                case "title" -> {
                    if (args.length < 3) throw usage("/zone title <id> <title>");
                    next = change(zone, String.join(" ", Arrays.copyOfRange(args, 2, args.length)), null, null, null, null, null, null, null);
                }
                case "point" -> { point(sender, zone, args); return true; }
                case "mob" -> { mob(sender, zone, args); return true; }
                case "spawn" -> { spawn(sender, zone, args); return true; }
                case "cap" -> { requireLength(args, 3, "/zone cap <id> <1–64>"); next = change(zone, null, null, null, number(args[2]), null, null, null, null); }
                case "respawn" -> { requireLength(args, 3, "/zone respawn <id> <5–3600 seconds>"); next = change(zone, null, null, null, null, number(args[2]), null, null, null); }
                case "radius" -> { requireLength(args, 3, "/zone radius <id> <8–128 blocks>"); next = change(zone, null, null, null, null, null, number(args[2]), null, null); }
                case "safe" -> {
                    requireLength(args, 3, "/zone safe <id> <on|off>");
                    if (!args[2].equalsIgnoreCase("on") && !args[2].equalsIgnoreCase("off")) throw usage("/zone safe <id> <on|off>");
                    next = change(zone, null, null, args[2].equalsIgnoreCase("on"), null, null, null, null, null);
                }
                case "enable", "disable" -> {
                    requireLength(args, 2, "/zone " + action + " <id>");
                    if (action.equals("enable")) {
                        World world = context.world(zone.worldId());
                        if (world == null) throw new IllegalArgumentException("Load this zone's world before enabling it.");
                        publicWorld(world);
                        if (!zone.safe()) for (MobEntry entry : zone.mobs()) context.validateMob(entry.mobId());
                    }
                    next = change(zone, null, action.equals("enable"), null, null, null, null, null, null);
                }
                case "delete" -> {
                    if (args.length != 3 || !args[2].equalsIgnoreCase("confirm")) throw usage("/zone delete " + zone.id() + " confirm");
                    store.remove(zone.id());
                    ok(sender, "Removed zone " + zone.id() + ". Terrain, builds and NPCs were not changed.");
                    return true;
                }
                default -> { help(sender); return true; }
            }
            store.put(next);
            ok(sender, "Saved " + next.title() + " [" + next.id() + "] — " + status(next) + ".");
        } catch (IllegalArgumentException | IllegalStateException e) {
            fail(sender, e.getMessage());
        } catch (IOException e) {
            context.saveFailed(e);
            fail(sender, "The zone could not be saved. Your previous settings are still active; check the server log.");
        }
        return true;
    }

    private void select(CommandSender sender, boolean first, boolean target) {
        Player player = player(sender);
        publicWorld(player.getWorld());
        org.bukkit.block.Block lookedAt = target ? player.getTargetBlockExact(12) : null;
        if (target && lookedAt == null) throw new IllegalArgumentException("Look at a block within 12 blocks to select that corner.");
        Location location = target ? lookedAt.getLocation() : player.getLocation();
        selectCorner(player,first,location);
    }
    public void selectCorner(Player player,boolean first,Location location) {
        if(!player.hasPermission(PERMISSION)) throw new IllegalArgumentException("You do not have permission to manage areas.");
        if(location.getWorld()!=player.getWorld()) throw new IllegalArgumentException("Both corners must be in your current world.");
        publicWorld(player.getWorld());
        var selected=selections.select(player.getUniqueId(),player.getWorld().getUID(),location.getBlockX(),location.getBlockY(),location.getBlockZ(),first);
        ok(player,"Pos"+(first?1:2)+": "+location.getBlockX()+", "+location.getBlockY()+", "+location.getBlockZ()+" in "+player.getWorld().getName()+".");
        if(selected.complete()) detail(player,"Cuboid: "+selected.volume()+" blocks. Save with /digsite create <id> [seconds] or /zone create <id>.");
    }
    private void showSelection(Player player) {
        var selected=selections.selection(player.getUniqueId()).orElseThrow(() -> new IllegalArgumentException("Select two corners with the area wand first."));
        if(!selected.complete()) { note(player,"Select the other corner to finish your cuboid."); return; }
        var b=selected.bounds();
        detail(player,"Selected world: "+selected.worldId()+" · "+selected.volume()+" blocks");
        detail(player,"Cuboid: "+b.minX()+", "+b.minY()+", "+b.minZ()+" to "+b.maxX()+", "+b.maxY()+", "+b.maxZ());
    }
    private void create(CommandSender sender, String id) throws IOException {
        Player player = player(sender);
        publicWorld(player.getWorld());
        if (store.find(id).isPresent()) throw new IllegalArgumentException("A zone with that ID already exists.");
        AreaSelection.Selection selection = selections.selection(player.getUniqueId()).orElse(null);
        if (selection == null || selection.first() == null || selection.second() == null) throw new IllegalArgumentException("Select two opposite corners with /zone pos1 and /zone pos2 first.");
        if (!selection.worldId().equals(player.getWorld().getUID())) throw new IllegalArgumentException("Return to the selected world or select two new corners.");
        Bounds bounds = selection.bounds();
        ZoneDefinition zone = new ZoneDefinition(id, id.replace('_', ' ').replace('-', ' '), selection.worldId(), bounds, false, false, 8, 20, 48, List.of(), List.of());
        store.put(zone);
        ok(sender, "Created " + id + " (disabled). Add spawn points and mobs, then /zone enable " + id + ". Use /zone safe " + id + " on for a peaceful hub.");
    }
    private void point(CommandSender sender, ZoneDefinition zone, String[] args) throws IOException {
        List<SpawnPoint> points = new ArrayList<>(zone.points());
        if ((args.length == 3 || args.length == 4) && args[2].equalsIgnoreCase("list")) {
            pointList(sender, zone, args.length == 4 ? number(args[3]) : 1); return;
        } else if (args.length == 3 && args[2].equalsIgnoreCase("add")) {
            Player player = player(sender);
            publicWorld(player.getWorld());
            if (!zone.worldId().equals(player.getWorld().getUID())) throw new IllegalArgumentException("Stand in the zone's world to add a spawn point.");
            Location location = player.getLocation();
            points.add(new SpawnPoint(location.getX(), location.getY(), location.getZ(), location.getYaw()));
            store.put(change(zone, null, null, null, null, null, null, points, null));
            ok(sender, "Saved spawn point " + points.size() + " in " + zone.id() + ". Mobs only spawn with a clear, supported landing.");
        } else if (args.length == 4 && args[2].equalsIgnoreCase("remove")) {
            int index = number(args[3]);
            if (index < 1 || index > points.size()) throw new IllegalArgumentException("Point index must be 1–" + points.size() + "; see /zone info " + zone.id() + ".");
            points.remove(index - 1);
            store.put(change(zone, null, null, null, null, null, null, points, null));
            ok(sender, "Removed spawn point " + index + " from " + zone.id() + ".");
        } else throw usage("/zone point <id> add | list [page] | remove <index>");
    }
    private void mob(CommandSender sender, ZoneDefinition zone, String[] args) throws IOException {
        List<MobEntry> mobs = new ArrayList<>(zone.mobs());
        if (args.length == 3 && args[2].equalsIgnoreCase("list")) {
            mobList(sender, zone); return;
        } else if (args.length == 6 && args[2].equalsIgnoreCase("add")) {
            MobEntry entry = new MobEntry(args[3], number(args[4]), number(args[5]));
            context.validateMob(entry.mobId());
            mobs.removeIf(old -> old.mobId().equals(entry.mobId()));
            mobs.add(entry);
            store.put(change(zone, null, null, null, null, null, null, null, mobs));
            ok(sender, "Saved " + entry.mobId() + " in " + zone.id() + ": level " + entry.level() + ", weight " + entry.weight() + ".");
        } else if (args.length == 4 && args[2].equalsIgnoreCase("remove")) {
            if (!mobs.removeIf(entry -> entry.mobId().equals(args[3]))) throw new IllegalArgumentException("That mob is not in this zone's roster.");
            store.put(change(zone, null, null, null, null, null, null, null, mobs));
            ok(sender, "Removed " + args[3] + " from " + zone.id() + ".");
        } else throw usage("/zone mob <id> add <mobId> <weight> <level> | list | remove <mobId>");
    }
    private void spawn(CommandSender sender, ZoneDefinition zone, String[] args) throws IOException {
        if (args.length < 3 || args.length > 5) throw usage("/zone spawn <zone> <mobId> [level] [weight]");
        Player player = player(sender); publicWorld(player.getWorld());
        if (!zone.worldId().equals(player.getWorld().getUID())) throw new IllegalArgumentException("Stand in the zone's world to place a spawn point.");
        context.validateMob(args[2]);
        MobEntry old = zone.mobs().stream().filter(entry -> entry.mobId().equals(args[2])).findFirst().orElse(null);
        MobEntry entry = new MobEntry(args[2], args.length == 5 ? number(args[4]) : old == null ? 1 : old.weight(),
            args.length >= 4 ? number(args[3]) : old == null ? 10 : old.level());
        if (old != null && !old.equals(entry)) throw new IllegalArgumentException("That mob already has a level and weight here. Change its roster with /zone mob " + zone.id() + " add " + entry.mobId() + " " + entry.weight() + " " + entry.level() + ". No spawn point was added.");
        Location location = player.getLocation();
        SpawnPoint point = new SpawnPoint(location.getX(), location.getY(), location.getZ(), location.getYaw());
        List<SpawnPoint> points = new ArrayList<>(zone.points());
        boolean added = points.stream().noneMatch(p -> Double.compare(p.x(), point.x()) == 0 && Double.compare(p.y(), point.y()) == 0 && Double.compare(p.z(), point.z()) == 0);
        if (added) points.add(point);
        List<MobEntry> mobs = new ArrayList<>(zone.mobs());
        if (old == null) mobs.add(entry);
        store.put(change(zone, null, null, null, null, null, null, points, mobs));
        ok(sender, (added ? "Placed spawn point " + points.size() : "Reused the existing spawn point") + " in " + zone.id() + "; " + entry.mobId() + " is level " + entry.level() + ".");
        detail(sender, "Every point draws from this zone's weighted roster. Use separate zones for fixed encounters. " + (zone.enabled() ? "" : "Enable when ready: /zone enable " + zone.id()));
    }
    private void pointList(CommandSender sender, ZoneDefinition zone, int page) {
        int pages = Math.max(1, (zone.points().size() + 11) / 12);
        if (page < 1 || page > pages) throw new IllegalArgumentException("Page must be 1–" + pages + ".");
        heading(sender, zone.id() + " — " + zone.points().size() + " spawn points, page " + page + "/" + pages);
        for (int index = (page - 1) * 12; index < Math.min(zone.points().size(), page * 12); index++) {
            SpawnPoint point = zone.points().get(index);
            detail(sender, "  " + (index + 1) + ": " + String.format(Locale.ROOT, "%.1f, %.1f, %.1f · yaw %.0f", point.x(), point.y(), point.z(), point.yaw()));
        }
        if (zone.points().isEmpty()) detail(sender, "Stand on a supported landing, then /zone point " + zone.id() + " add.");
    }
    private void mobList(CommandSender sender, ZoneDefinition zone) {
        heading(sender, zone.id() + " — " + zone.mobs().size() + " roster entries (shared by all spawn points)");
        long total = zone.mobs().stream().mapToLong(MobEntry::weight).sum();
        zone.mobs().forEach(m -> detail(sender, "  " + m.mobId() + " · level " + m.level() + " · weight " + m.weight()
            + " (" + String.format(Locale.ROOT, "%.1f", 100.0 * m.weight() / total) + "% of rolls)"));
        if (zone.mobs().isEmpty()) detail(sender, "Browse /zone catalog, then /zone mob " + zone.id() + " add <mobId> <weight> <level>.");
    }
    private List<String> catalogIds() {
        ArrayList<String> ids = new ArrayList<>(ZoneMobTypes.vanillaIds());
        context.customMobIds().forEach(id -> ids.add("custom:" + id));
        return ids.stream().distinct().filter(id -> { try { context.validateMob(id); return true; } catch (IllegalArgumentException ignored) { return false; } }).sorted().toList();
    }
    private void catalog(CommandSender sender, String[] args) {
        if (args.length > 3) throw usage("/zone catalog [all|custom|vanilla] [page]");
        String kind = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";
        if (!List.of("all", "custom", "vanilla").contains(kind)) throw usage("/zone catalog [all|custom|vanilla] [page]");
        List<String> ids = catalogIds().stream().filter(id -> kind.equals("all") || id.startsWith(kind + ":")).toList();
        int pages = Math.max(1, (ids.size() + 11) / 12), page = args.length == 3 ? number(args[2]) : 1;
        if (page < 1 || page > pages) throw new IllegalArgumentException("Page must be 1–" + pages + ".");
        heading(sender, "Zone mob catalog — " + kind + ", page " + page + "/" + pages);
        ids.stream().skip((long) (page - 1) * 12).limit(12).forEach(id -> detail(sender, "  " + id));
        if (ids.isEmpty()) note(sender, "No supported mobs in this category.");
        detail(sender, "Place at your feet: /zone spawn <zone> <mobId> [level] [weight].");
    }
    private void list(CommandSender sender, String[] args) {
        if (args.length > 2) throw usage("/zone list [page]");
        List<ZoneDefinition> zones = store.all();
        int pages = Math.max(1, (zones.size() + 11) / 12), page = args.length == 2 ? number(args[1]) : 1;
        if (page < 1 || page > pages) throw new IllegalArgumentException("Page must be 1–" + pages + ".");
        heading(sender, "Zones — " + zones.size() + " total, page " + page + "/" + pages);
        zones.stream().skip((long) (page - 1) * 12).limit(12).forEach(z -> detail(sender, z.id() + " · " + z.title() + " · " + status(z)));
        if (zones.isEmpty()) detail(sender, "Select two corners, then /zone create <id>.");
    }
    private void info(CommandSender sender, ZoneDefinition zone) {
        Bounds b = zone.bounds();
        World world = context.world(zone.worldId());
        heading(sender, zone.title() + " [" + zone.id() + "] — " + status(zone));
        detail(sender, "World: " + (world == null ? zone.worldId() + " (unloaded)" : world.getName()));
        detail(sender, "Bounds: " + b.minX() + ", " + b.minY() + ", " + b.minZ() + " to " + b.maxX() + ", " + b.maxY() + ", " + b.maxZ());
        detail(sender, "Cap " + zone.cap() + " · respawn " + zone.respawnSeconds() + "s · activation " + zone.activationRadius() + " blocks");
        detail(sender, "Points: " + zone.points().size() + " — /zone point " + zone.id() + " list");
        mobList(sender, zone);
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION) || args.length == 0) return List.of();
        List<String> candidates = List.of();
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) candidates = ACTIONS;
        else if (args.length == 2 && (action.equals("pos1") || action.equals("pos2"))) candidates = List.of("target");
        else if (args.length == 2 && action.equals("catalog")) candidates = List.of("all", "custom", "vanilla");
        else if (args.length == 2 && !List.of("wand", "selection", "clear", "pos1", "pos2", "create", "list").contains(action)) candidates = store.all().stream().map(ZoneDefinition::id).toList();
        else if (args.length == 3) candidates = switch (action) {
            case "point", "mob" -> List.of("add", "list", "remove");
            case "spawn" -> catalogIds();
            case "catalog" -> List.of("1", "2", "3", "4", "5", "6");
            case "safe" -> List.of("on", "off");
            case "delete" -> List.of("confirm");
            case "cap" -> List.of("4", "8", "16", "32");
            case "respawn" -> List.of("10", "20", "30", "60");
            case "radius" -> List.of("24", "48", "64", "96");
            default -> List.of();
        };
        else if (args.length == 4 && action.equals("mob")) {
            if (args[2].equalsIgnoreCase("remove")) candidates = store.find(args[1]).map(z -> z.mobs().stream().map(MobEntry::mobId).toList()).orElse(List.of());
            else if (args[2].equalsIgnoreCase("add")) {
                candidates = catalogIds();
            }
        } else if (args.length == 4 && action.equals("point") && args[2].equalsIgnoreCase("remove"))
            candidates = store.find(args[1]).map(z -> java.util.stream.IntStream.rangeClosed(1, z.points().size()).mapToObj(Integer::toString).toList()).orElse(List.of());
        else if (args.length == 5 && action.equals("mob") && args[2].equalsIgnoreCase("add")) candidates = List.of("1", "5", "10");
        else if (args.length == 6 && action.equals("mob") && args[2].equalsIgnoreCase("add")) candidates = List.of("1", "5", "10", "25", "50", "100");
        else if (args.length == 4 && action.equals("spawn")) candidates = List.of("1", "5", "10", "25", "50", "100");
        else if (args.length == 5 && action.equals("spawn")) candidates = List.of("1", "5", "10");
        else if (args.length == 4 && action.equals("point") && args[2].equalsIgnoreCase("list")) candidates = store.find(args[1]).map(z -> java.util.stream.IntStream.rangeClosed(1, Math.max(1, (z.points().size() + 11) / 12)).mapToObj(Integer::toString).toList()).orElse(List.of());
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream().filter(c -> c.startsWith(prefix)).toList();
    }
    private void publicWorld(World world) {
        if (context.islandWorld(world)) throw new IllegalArgumentException("Zones belong in shared worlds, not player islands.");
    }
    private static ZoneDefinition change(ZoneDefinition z, String title, Boolean enabled, Boolean safe, Integer cap, Integer respawn, Integer radius, List<SpawnPoint> points, List<MobEntry> mobs) {
        return new ZoneDefinition(z.id(), title == null ? z.title() : title, z.worldId(), z.bounds(), enabled == null ? z.enabled() : enabled,
            safe == null ? z.safe() : safe, cap == null ? z.cap() : cap, respawn == null ? z.respawnSeconds() : respawn,
            radius == null ? z.activationRadius() : radius, points == null ? z.points() : points, mobs == null ? z.mobs() : mobs);
    }
    private static Player player(CommandSender sender) {
        if (!(sender instanceof Player player)) throw new IllegalArgumentException("Stand in the world to use this command."); return player;
    }
    private static int number(String input) {
        try { return Integer.parseInt(input); } catch (NumberFormatException e) { throw new IllegalArgumentException("Enter a whole number within the supported range."); }
    }
    private static void requireLength(String[] args, int length, String usage) { if (args.length != length) throw usage(usage); }
    private static IllegalArgumentException usage(String usage) { return new IllegalArgumentException("Use " + usage); }
    private static String status(ZoneDefinition z) { return !z.enabled() ? "disabled" : z.safe() ? "safe area" : "mob zone"; }
    private static void ok(CommandSender sender, String message) { Chat.success(sender, Chat.escape(message)); }
    private static void fail(CommandSender sender, String message) { Chat.error(sender, Chat.escape(message == null ? "That zone change was not valid." : message)); }
    private static void note(CommandSender sender, String message) { Chat.notice(sender, Chat.escape(message)); }
    private static void detail(CommandSender sender, String message) { Chat.info(sender, Chat.escape(message)); }
    private static void heading(CommandSender sender, String message) { Chat.send(sender, "<white>" + Chat.escape(message) + "</white>"); }
    private static void help(CommandSender sender) {
        heading(sender, "Zones — staff setup");
        detail(sender, "/zone pos1 [target] · /zone pos2 [target] · /zone create <id>");
        detail(sender, "/zone wand · /zone selection · /zone clear — left-click pos1, right-click pos2");
        detail(sender, "/zone catalog [all|custom|vanilla] [page] · /zone spawn <zone> <mobId> [level] [weight]");
        detail(sender, "/zone point <id> add | list [page] | remove <index>");
        detail(sender, "/zone mob <id> add <vanilla:type|custom:id> <weight> <level> | list | remove <mobId>");
        detail(sender, "/zone title <id> <title> · cap <id> <1–64> · respawn <id> <seconds> · radius <id> <blocks>");
        detail(sender, "/zone safe <id> <on|off> · enable <id> · disable <id>");
        detail(sender, "/zone list [page] · /zone info <id> · /zone delete <id> confirm");
        detail(sender, "Zones stay disabled until enabled. No terrain or NPCs are created or moved.");
        detail(sender, "Spawn points share the zone's weighted roster. Separate zones keep encounters distinct.");
    }
}
