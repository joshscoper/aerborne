package net.nerdsmp.paper.digsite;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.resource.ResourceMaterials;
import net.nerdsmp.paper.zone.ZoneCommand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/** Captures existing staff-built soil only; never generates a pit or changes its block palette. */
public final class DigSiteCommand {
    private static final long MAX_SCAN_VOLUME = 32_768;
    private final NerdSmpPlugin plugin;
    public DigSiteCommand(NerdSmpPlugin plugin) { this.plugin = plugin; }

    public boolean command(CommandSender sender, String[] args) {
        if (plugin.digSites() == null) { Chat.error(sender, "Dig sites are available in Skyblock mode."); return true; }
        try {
            if (args.length == 0) { list(sender, 1); if (staff(sender)) help(sender); return true; }
            String action = args[0].toLowerCase(Locale.ROOT);
            if (action.equals("list")) {
                if (args.length > 2) throw new IllegalArgumentException("Use /digsite list [page].");
                list(sender, args.length == 2 ? Integer.parseInt(args[1]) : 1); return true;
            }
            if (action.equals("info") && args.length == 2) { info(sender, args[1]); return true; }
            if (!staff(sender)) { Chat.error(sender, "Only staff can edit dig sites. Use " + Chat.command("digsite list") + " to find one."); return true; }
            switch (action) {
                case "wand" -> {
                    if(args.length!=1) throw new IllegalArgumentException("Use /digsite wand.");
                    plugin.zoneCommands().command(sender,new String[]{"wand"});
                }
                case "selection", "clear" -> plugin.zoneCommands().command(sender,args);
                case "create" -> create(sender, args);
                case "enable", "disable" -> {
                    if (args.length != 2) throw new IllegalArgumentException("Use /digsite " + action + " <id>.");
                    plugin.digSites().setEnabled(args[1], action.equals("enable"));
                    Chat.success(sender, "Dig site <white>" + Chat.escape(args[1]) + "</white> " + (action.equals("enable") ? "enabled." : "disabled. Its terrain was not changed."));
                }
                case "delete" -> {
                    if (args.length != 3 || !args[2].equalsIgnoreCase("confirm"))
                        throw new IllegalArgumentException("Use /digsite delete <id> confirm. This stops renewal; it does not restore or remove terrain.");
                    plugin.digSites().remove(args[1]); Chat.success(sender, "Removed dig site <white>" + Chat.escape(args[1]) + "</white>. Terrain was left untouched.");
                }
                default -> help(sender);
            }
        } catch (NumberFormatException invalid) { Chat.error(sender, "Use a whole number for the page."); }
        catch (IllegalArgumentException invalid) { Chat.error(sender, Chat.escape(invalid.getMessage() == null ? "That dig site edit was not valid." : invalid.getMessage())); }
        catch (IOException | IllegalStateException failed) {
            plugin.getLogger().log(Level.WARNING, "Dig site edit could not be completed", failed);
            Chat.error(sender, "The dig site edit could not be saved or validated. Check the server log before retrying.");
        }
        return true;
    }

    public void show(Player player) { if (plugin.digSites() != null) list(player, 1); }

    private void create(CommandSender sender, String[] args) throws IOException {
        if (args.length < 2 || args.length > 4) throw new IllegalArgumentException("Use /digsite create <id> [cooldown] with your wand selection, or /digsite create <id> <zone> [cooldown]. Cooldowns look like 90s or 10m.");
        if (!(sender instanceof Player player)) throw new IllegalArgumentException("Stand in your build's world to capture a dig site.");
        if (plugin.digSites().store().find(args[1]).isPresent()) throw new IllegalArgumentException("That dig site already exists. Disable/delete it before capturing a replacement.");
        boolean fromSelection=args.length==2 || args.length==3 && args[2].toLowerCase(Locale.ROOT).matches("[+-]?[0-9]+[smhdw]?");
        var selected=fromSelection ? plugin.zoneCommands().selections().selection(player.getUniqueId()).orElseThrow(
            () -> new IllegalArgumentException("Get /digsite wand, then left-click pos1 and right-click pos2 first.")) : null;
        if(fromSelection && (!selected.complete() || !selected.worldId().equals(player.getWorld().getUID())))
            throw new IllegalArgumentException("Select both corners in your current shared world before saving.");
        var plan=fromSelection ? DigSiteSelectionPlan.plan(plugin.zones().all(),args[1],selected.worldId(),selected.bounds()) : null;
        var zone=fromSelection ? plan.zone() : plugin.zones().find(args[2]).orElseThrow(() -> new IllegalArgumentException("Create or select an existing zone first."));
        if (!zone.enabled()) throw new IllegalArgumentException("Enable the zone first. For a peaceful pit use /zone safe " + zone.id() + " on, then /zone enable " + zone.id() + ".");
        World world = Bukkit.getWorld(zone.worldId());
        if (world == null || world != player.getWorld()) throw new IllegalArgumentException("Stand in the zone's loaded world before capturing its soil.");
        if (plugin.skyblock() != null && plugin.skyblock().isIslandWorld(world)) throw new IllegalArgumentException("Use a shared world, not a private island world.");
        var bounds = fromSelection ? selected.bounds() : zone.bounds();
        long volume = ((long)bounds.maxX()-bounds.minX()+1) * ((long)bounds.maxY()-bounds.minY()+1) * ((long)bounds.maxZ()-bounds.minZ()+1);
        if (volume > MAX_SCAN_VOLUME) throw new IllegalArgumentException("Select a thin digging area of at most 32,768 blocks. Large encounter zones are not dig-site selections.");
        if (bounds.minY() < world.getMinHeight() || bounds.maxY() >= world.getMaxHeight()) throw new IllegalArgumentException("The selected height is outside this world's limits.");
        for (int x=bounds.minX()>>4; x<=bounds.maxX()>>4; x++) for (int z=bounds.minZ()>>4; z<=bounds.maxZ()>>4; z++)
            if (!world.isChunkLoaded(x,z)) throw new IllegalArgumentException("Load every selected chunk before capturing it. Dig sites never force-load chunks.");
        List<DigSiteDefinition.Node> nodes = new ArrayList<>();
        for (int x=bounds.minX(); x<=bounds.maxX(); x++) for (int y=bounds.minY(); y<=bounds.maxY(); y++) for (int z=bounds.minZ(); z<=bounds.maxZ(); z++) {
            var block=world.getBlockAt(x,y,z); String material=block.getType().name();
            if (!ResourceMaterials.excavation(material) || material.startsWith("SUSPICIOUS_")) continue;
            if (!world.getWorldBorder().isInside(new Location(world,x+.5,y+.5,z+.5))) throw new IllegalArgumentException("A digging node is outside the world border.");
            nodes.add(new DigSiteDefinition.Node(x,y,z,material,block.getBlockData().getAsString()));
            if (nodes.size() > DigSiteDefinition.MAX_NODES) throw new IllegalArgumentException("A site supports at most 2,048 digging blocks. Capture a smaller area.");
        }
        if (nodes.isEmpty()) throw new IllegalArgumentException("There is no eligible soil in that zone. Build it with dirt, sand, gravel, clay, mud or soul sand first.");
        int cooldown=fromSelection && args.length==3 ? cooldown(args[2])
            : args.length==4 ? cooldown(args[3]) : DigSiteDefinition.DEFAULT_COOLDOWN_SECONDS;
        var definition=new DigSiteDefinition(args[1],zone.id(),world.getUID(),UUID.randomUUID(),true,cooldown,nodes);
        boolean createdZone=fromSelection && plan.created();
        if(createdZone) plugin.zones().put(zone);
        try { plugin.digSites().register(definition); }
        catch(IOException | RuntimeException failure) {
            // A failed site save must not leave a surprise region. Never remove a saved site's parent.
            if(createdZone && plugin.digSites().store().find(definition.id()).isEmpty()
                && plugin.zones().find(zone.id()).filter(current -> current==zone).isPresent()) {
                try { plugin.zones().remove(zone.id()); }
                catch(IOException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
        Chat.success(sender, "Saved <white>" + Chat.escape(definition.id()) + "</white>: " + nodes.size() + " digging blocks, " + seconds(cooldown) + " cooldown. Terrain was not replaced.");
        if(createdZone) Chat.info(sender, "Created the selected safe region <white>"+Chat.escape(zone.id())+"</white>. No buildings, NPCs or warps were generated.");
        else Chat.info(sender, "Linked to existing zone <white>"+Chat.escape(zone.id())+"</white>. Its settings were not changed.");
        Chat.info(sender, "Set a travel point with " + Chat.command("setwarp " + zone.id()) + " at a safe entrance.");
    }

    private void list(CommandSender sender, int requested) {
        var all=plugin.digSites().store().all().stream().filter(site -> staff(sender) || publiclyOpen(site)).toList();
        int pages=Math.max(1,(all.size()+7)/8);
        if (requested<1 || requested>pages) throw new IllegalArgumentException("Page must be 1–" + pages + ".");
        Chat.send(sender, "<white>Dig sites</white> <gray>— " + all.size() + " available, page " + requested + "/" + pages + "</gray>");
        if (all.isEmpty()) Chat.notice(sender, "No dig sites are open yet. Staff must mark an existing digging area.");
        all.stream().skip((long)(requested-1)*8).limit(8).forEach(site -> {
            var status=plugin.digSites().status(site.id());
            Chat.info(sender, "<white>"+Chat.escape(site.id())+"</white> — "+site.nodes().size()+" blocks · "+seconds(site.cooldownSeconds())+" regrowth · "+status.ready()+" ready");
            if (sender instanceof Player player && plugin.travel().warpNames().contains(site.zoneId()))
                Chat.info(player, "  Travel there: " + Chat.command("warp " + site.zoneId()));
        });
        if (!all.isEmpty()) Chat.info(sender, "Use <white>/digsite info &lt;id&gt;</white> for its location. Only marked blocks regrow.");
    }

    private void info(CommandSender sender, String id) {
        var site=plugin.digSites().store().find(id).orElseThrow(() -> new IllegalArgumentException("No dig site named " + id + "."));
        if (!staff(sender) && !publiclyOpen(site)) throw new IllegalArgumentException("That dig site is not open.");
        var status=plugin.digSites().status(site.id());
        Chat.send(sender, "<white>"+Chat.escape(site.id())+"</white> <gray>· zone "+Chat.escape(site.zoneId())+" · "+seconds(site.cooldownSeconds())+" cooldown</gray>");
        Chat.info(sender, "Nodes: "+status.nodes()+" · ready "+status.ready()+" · cooling "+status.cooling()+" · blocked "+status.blocked()+" · inactive "+status.inactive());
        World world=Bukkit.getWorld(site.worldId());
        if (!site.nodes().isEmpty()) {
            var at=site.nodes().getFirst(); Chat.info(sender, "Location: "+Chat.escape(world==null?site.worldId()+" (unloaded)":world.getName())+" · "+at.x()+", "+at.y()+", "+at.z());
        }
        Chat.info(sender, "Use an excavation tool. Regrowth waits until the block is empty and clear of players and mobs.");
    }

    private boolean publiclyOpen(DigSiteDefinition site) {
        return site.enabled() && plugin.zones().find(site.zoneId()).filter(zone -> zone.enabled() && zone.worldId().equals(site.worldId())).isPresent();
    }
    /** Reads {@code 90s}, {@code 10m} or {@code 1h}; a bare number is seconds, as before. */
    static int cooldown(String raw) {
        return Chat.duration(raw, ChronoUnit.SECONDS).filter(duration -> duration.toSeconds() <= Integer.MAX_VALUE)
            .map(duration -> (int) duration.toSeconds())
            .orElseThrow(() -> new IllegalArgumentException("Use a cooldown such as 90s, 10m or 1h (5 seconds to 1 hour)."));
    }
    private static String seconds(int seconds) { return Chat.duration(Duration.ofSeconds(seconds)); }
    private boolean staff(CommandSender sender) { return sender.hasPermission(ZoneCommand.PERMISSION); }
    private void help(CommandSender sender) {
        Chat.info(sender, Chat.command("digsite wand") + " — left-click pos1, right-click pos2");
        Chat.info(sender, "<white>" + Chat.escape("/digsite create <id> [cooldown]") + "</white> — save your selected cuboid; cooldowns look like 90s or 10m");
        Chat.info(sender, "<white>" + Chat.escape("/digsite create <id> <zone> [cooldown]") + "</white> — capture an existing zone instead");
        Chat.info(sender, "<white>" + Chat.escape("/digsite list [page] | info <id> | enable <id> | disable <id> | delete <id> confirm") + "</white>");
    }
    public List<String> complete(CommandSender sender, String[] args) {
        if (plugin.digSites()==null || args.length==0) return List.of();
        if (args.length==1) return staff(sender) ? List.of("wand","selection","clear","list","info","create","enable","disable","delete") : List.of("list","info");
        if (args.length==2 && !List.of("wand","selection","clear","create","list").contains(args[0].toLowerCase(Locale.ROOT)))
            return plugin.digSites().store().all().stream().filter(site -> staff(sender)||publiclyOpen(site)).map(DigSiteDefinition::id).toList();
        if (staff(sender) && args.length==3 && args[0].equalsIgnoreCase("create")) {
            var choices=new ArrayList<>(plugin.zones().all().stream().map(zone -> zone.id()).toList());
            choices.addAll(List.of("20","30","60")); return choices;
        }
        if (staff(sender) && args.length==3 && args[0].equalsIgnoreCase("delete")) return List.of("confirm");
        if (staff(sender) && args.length==4 && args[0].equalsIgnoreCase("create")) return List.of("20","30","60");
        return List.of();
    }
}
