package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import static net.nerdsmp.paper.expedition.ExpeditionLayout.*;

/** Public gathering is confined to catalog nodes; ordinary claim protections still guard everything else. */
public final class ExpeditionController implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final World world;
    private final World legacyWorld;
    private final boolean generated;
    private final Set<UUID> retainedWorlds;
    private final List<Node> nodes = List.copyOf(ExpeditionPlanV2.NODES.values());
    private final Map<Cell, NamespacedKey> keys = new HashMap<>();
    private final Set<Cell> invalidMarkers = new HashSet<>();
    private final BukkitTask renewal;
    private int cursor;

    public ExpeditionController(NerdSmpPlugin plugin) throws Exception {
        this.plugin=plugin;
        generated=plugin.getConfig().getBoolean("expeditions.generated-worlds",false);
        String season=plugin.getConfig().getString("season.id","season-1");
        retainedWorlds=new HashSet<>();
        if(!generated) ExpeditionWorld.openExisting(plugin,season).forEach(existing->retainedWorlds.add(existing.getUID()));
        world=generated ? ExpeditionWorld.open(plugin,season) : null;
        legacyWorld=Bukkit.getWorld(NamespacedKey.minecraft(ExpeditionWorld.worldName(
            plugin.getConfig().getString("season.id","season-1"),1)));
        for (Node node:nodes) keys.put(node.cell(),new NamespacedKey(plugin,"expedition_"+node.cell().x()+"_"+node.cell().y()+"_"+node.cell().z()));
        // A bounded scan of loaded chunks only; no chunk tickets, synchronous loads or per-block tasks.
        renewal=generated ? Bukkit.getScheduler().runTaskTimer(plugin,this::renew,20,20) : null;
    }
    public World world() { return world; }
    public boolean generated() { return generated; }
    public Location hub() { return generated ? destination(Zone.CITY) : plugin.travel().publicSpawn(); }
    public boolean manages(World candidate) { return world!=null && candidate!=null && world.getUID().equals(candidate.getUID()); }
    public Location destination(Zone zone) {
        if(!generated) return zone==Zone.CITY ? plugin.travel().publicSpawn() : plugin.travel().warpLocation(zone.id).orElse(null);
        Cell arrival=arrival(zone);
        return new Location(world,arrival.x()+.5,arrival.y(),arrival.z()+.5,180,0);
    }
    static Cell arrival(Zone zone) {
        return new Cell(ExpeditionPlanV2.centerX(zone),97,
            ExpeditionPlanV2.centerZ(zone)+(zone==Zone.CITY ? 0 : zone==Zone.HARBOR ? 23 : 19));
    }
    /** Revision-aware region lookup keeps legacy warps gated without adopting old terrain into the new catalog. */
    public Optional<Zone> zoneAt(Location location) {
        if(!generated) return Optional.empty();
        if(location==null || location.getWorld()==null) return Optional.empty();
        if(manages(location.getWorld())) return ExpeditionPlanV2.zoneAt(location.getX(),location.getZ());
        return legacyWorld!=null && legacyWorld.getUID().equals(location.getWorld().getUID())
            ? ExpeditionLayout.zoneAt(location.getX(),location.getZ()) : Optional.empty();
    }
    private boolean unlocked(Zone zone) { return zone.unlock==null || plugin.serverProgression().unlocked(zone.unlock); }
    static boolean routeAccessible(Player player,boolean campaignUnlocked) {
        return campaignUnlocked || player.hasPermission("nerdsmp.progression.bypass");
    }
    private boolean accessible(Player player,Zone zone) { return routeAccessible(player,unlocked(zone)); }
    public boolean mayHarvest(BlockBreakEvent event) {
        Block block=event.getBlock();
        if(!manages(block.getWorld()) || event.getPlayer().getGameMode()!=GameMode.SURVIVAL
            || plugin.sessions().account(event.getPlayer().getUniqueId()).isEmpty()) return false;
        Node node=ExpeditionPlanV2.NODES.get(new Cell(block.getX(),block.getY(),block.getZ()));
        return node!=null && unlocked(node.zone()) && block.getType()==node.material()
            && !block.getChunk().getPersistentDataContainer().has(keys.get(node.cell()));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onHarvest(BlockBreakEvent event) {
        if(!mayHarvest(event)) return;
        Block block=event.getBlock();
        Node node=ExpeditionPlanV2.NODES.get(new Cell(block.getX(),block.getY(),block.getZ()));
        var key=keys.get(node.cell());
        block.getChunk().getPersistentDataContainer().set(key,PersistentDataType.LONG,System.currentTimeMillis()+node.cooldownSeconds()*1000L);
        Bukkit.getScheduler().runTask(plugin,()-> {
            if (!world.isChunkLoaded(block.getX()>>4,block.getZ()>>4)) return;
            if(event.isCancelled()) block.getChunk().getPersistentDataContainer().remove(key);
            else if(block.getType().isAir()) block.setType(depleted(node),false);
        });
    }
    private void renew() {
        long now=System.currentTimeMillis();
        for(int count=0;count<Math.min(32,nodes.size());count++) {
            Node node=nodes.get(cursor++%nodes.size()); if(cursor==nodes.size()) cursor=0;
            Cell cell=node.cell();
            if(!world.isChunkLoaded(cell.x()>>4,cell.z()>>4)) continue;
            Block block=world.getBlockAt(cell.x(),cell.y(),cell.z());
            var data=block.getChunk().getPersistentDataContainer(); var key=keys.get(cell);
            if(!data.has(key)) continue;
            if(!data.has(key,PersistentDataType.LONG)) {
                if(invalidMarkers.add(cell)) plugin.getLogger().severe("Invalid expedition cooldown at "+cell+"; preserve chunk data for review");
                continue;
            }
            Long due=data.get(key,PersistentDataType.LONG);
            if(due==null || due<0 || due>now) continue; // Unknown/corrupt markers never grant resources.
            if(block.getType()!=depleted(node) && !block.getType().isAir() && block.getType()!=node.material()) continue;
            block.setType(node.material(),false);
            plugin.resources().renewManaged(block,node.material());
            data.remove(key);
        }
    }
    public boolean command(org.bukkit.command.CommandSender sender,String[] args) {
        if(!(sender instanceof Player player)) { Chat.playersOnly(sender); Chat.info(sender,"Destinations: <white>"+Chat.escape(String.join(", ",names()))+"</white>"); return true; }
        if(!generated) {
            if(args.length==0) openManual(player,0);
            else if(args.length==1) manualTravel(player,args[0].toLowerCase(Locale.ROOT));
            else Chat.usage(player,"/expedition [warp]");
            return true;
        }
        if(args.length==0) { open(player); return true; }
        if(args.length!=1) { Chat.usage(player,"/expedition [destination]"); return true; }
        named(args[0]).ifPresentOrElse(zone->travel(player,zone),()->Chat.error(player,"Unknown destination. Choose one with "+Chat.command("expedition")+"."));
        return true;
    }
    public List<String> names() {
        if(generated) return Arrays.stream(Zone.values()).map(zone->zone.id).toList();
        var names=new TreeSet<>(plugin.travel().warpNames()); names.remove("city");
        if(plugin.travel().publicSpawn()!=null) names.add("city");
        return List.copyOf(names);
    }
    private void travel(Player player,Zone zone) {
        if(!accessible(player,zone)) { Chat.error(player,"That route is sealed. Advance the campaign to open it."); return; }
        plugin.travel().visit(player,destination(zone),zone.title,()->accessible(player,zone));
    }
    public void open(Player player) {
        if(!generated) { openManual(player,0); return; }
        Board board=new Board(player.getUniqueId());
        Inventory menu=Bukkit.createInventory(board,27,Chat.render("<dark_aqua><bold>Expeditions</bold></dark_aqua>")); board.inventory=menu;
        MenuKit.frame(menu);
        for(int i=0;i<Zone.values().length;i++) {
            Zone zone=Zone.values()[i]; boolean campaignOpen=unlocked(zone), access=routeAccessible(player,campaignOpen);
            List<String> lore=new ArrayList<>();
            for(String line:wrap(zone.description,42)) lore.add("<gray>"+Chat.escape(line)+"</gray>");
            if(access && !campaignOpen) lore.add("<gold>Staff preview; campaign remains sealed.</gold>");
            if(access) menu.setItem(10+i,MenuKit.item(zone.icon,"<aqua>"+Chat.escape(zone.title)+"</aqua>",MenuKit.withHint(lore,"travel")));
            else {
                lore.add(""); lore.add("<gold>Requires campaign: "+Chat.escape(zone.unlock.displayName())+"</gold>");
                menu.setItem(10+i,MenuKit.locked(Material.GRAY_DYE,zone.title,lore));
            }
        }
        menu.setItem(MenuKit.backSlot(27),MenuKit.back("World & Trade"));
        menu.setItem(MenuKit.closeSlot(27),MenuKit.close());
        player.openInventory(menu);
    }
    private static List<String> wrap(String text,int width) {
        List<String> lines=new ArrayList<>(); String line="";
        for(String word:text.split(" ")) { if(line.length()+word.length()+1>width) { lines.add(line); line=""; } line+=(line.isEmpty()?"":" ")+word; }
        if(!line.isEmpty()) lines.add(line); return lines;
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof TravelBoard board) {
            event.setCancelled(true);
            if(!(event.getWhoClicked() instanceof Player player) || !board.owner.equals(player.getUniqueId()) || !event.isLeftClick()) return;
            int slot=event.getRawSlot();
            Bukkit.getScheduler().runTask(plugin,()-> {
                if(!player.isOnline() || player.getOpenInventory().getTopInventory()!=board.inventory) return;
                if(slot==MenuKit.previousSlot(54) && board.page>0) { MenuKit.clickSound(player); openManual(player,board.page-1); }
                else if(slot==MenuKit.nextSlot(54) && (board.page+1)*45<board.names.size()) { MenuKit.clickSound(player); openManual(player,board.page+1); }
                else if(slot==MenuKit.backSlot(54)) { MenuKit.clickSound(player); plugin.menus().open(player,MenuManager.View.WORLD); }
                else if(slot==MenuKit.closeSlot(54)) player.closeInventory();
                else if(slot>=0 && slot<45 && board.page*45+slot<board.names.size()) {
                    MenuKit.clickSound(player); player.closeInventory(); manualTravel(player,board.names.get(board.page*45+slot));
                }
            });
            return;
        }
        if(!(event.getView().getTopInventory().getHolder() instanceof Board board)) return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player) || !board.owner.equals(player.getUniqueId()) || !event.isLeftClick()) return;
        int slot=event.getRawSlot(), index=slot-10;
        boolean zone=index>=0 && index<Zone.values().length;
        if(!zone && slot!=MenuKit.backSlot(27) && slot!=MenuKit.closeSlot(27)) return;
        Bukkit.getScheduler().runTask(plugin,()-> {
            if(!player.isOnline() || player.getOpenInventory().getTopInventory()!=board.inventory) return;
            if(slot==MenuKit.closeSlot(27)) { player.closeInventory(); return; }
            MenuKit.clickSound(player);
            if(slot==MenuKit.backSlot(27)) plugin.menus().open(player,MenuManager.View.WORLD);
            else { player.closeInventory(); travel(player,Zone.values()[index]); }
        });
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Board || event.getView().getTopInventory().getHolder() instanceof TravelBoard) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onBoard(PlayerInteractEvent event) {
        Block block=event.getClickedBlock();
        if(block==null || !manages(block.getWorld()) || block.getX()!=0 || block.getY()!=SURFACE+1 || block.getZ()!=8
            || event.getHand()!=EquipmentSlot.HAND || event.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true); open(event.getPlayer());
    }
    private boolean locked(Player player,Location location) {
        return zoneAt(location).filter(zone->!accessible(player,zone)).isPresent();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onTeleport(PlayerTeleportEvent event) {
        if(locked(event.getPlayer(),event.getTo())) { event.setCancelled(true); Chat.error(event.getPlayer(),"That route is still sealed by the campaign."); }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onMove(PlayerMoveEvent event) {
        if(event instanceof PlayerTeleportEvent || !event.hasChangedBlock() || !locked(event.getPlayer(),event.getTo())) return;
        event.setTo(locked(event.getPlayer(),event.getFrom()) ? hub() : event.getFrom());
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player=event.getPlayer();
        Bukkit.getScheduler().runTask(plugin,()-> { if(player.isOnline() && locked(player,player.getLocation())) player.teleport(hub()); });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onVoid(EntityDamageEvent event) {
        if(!generated) return; // Manual hubs use normal world/island death and rescue policy.
        if(event.getCause()==EntityDamageEvent.DamageCause.VOID && event.getEntity() instanceof Player player
            && (manages(player.getWorld()) || retainedWorlds.contains(player.getWorld().getUID()) || legacyWorld!=null && legacyWorld.getUID().equals(player.getWorld().getUID()))) {
            Location target=hub();
            if(target!=null) { event.setCancelled(true); player.setFallDistance(0); player.teleport(target); }
        }
    }
    private void manualTravel(Player player,String id) {
        // Reuse warp generation/access checks, including invalidation if staff move a warp mid-warmup.
        if(id.equals("city")) {
            if(plugin.travel().publicSpawn()==null) Chat.error(player,"The hub is not available yet. Ask staff to set it.");
            else plugin.travel().handle("spawn",player,new String[0]);
        } else plugin.travel().handle("warp",player,new String[]{id});
    }
    private void openManual(Player player,int requestedPage) {
        List<String> routes=names(); int page=Math.clamp(requestedPage,0,Math.max(0,(routes.size()-1)/45));
        TravelBoard board=new TravelBoard(player.getUniqueId(),routes,page);
        board.inventory=Bukkit.createInventory(board,54,Chat.render("<dark_aqua><bold>Warps</bold></dark_aqua>"));
        int pages=Math.max(1,(routes.size()+44)/45);
        for(int slot=0;slot<45 && page*45+slot<routes.size();slot++) {
            String route=routes.get(page*45+slot);
            board.inventory.setItem(slot,MenuKit.item(route.equals("city")?Material.NETHER_STAR:Material.ENDER_PEARL,
                "<aqua>"+Chat.escape(route.equals("city")?"Hub":title(route))+"</aqua>",List.of(MenuKit.hint("travel"))));
        }
        if(routes.isEmpty()) board.inventory.setItem(22,MenuKit.item(Material.COMPASS,"<aqua>No routes yet</aqua>",
            List.of("<gray>Staff: use /setspawn and /setwarp \\<name></gray>")));
        if(page>0) board.inventory.setItem(MenuKit.previousSlot(54),MenuKit.previous(page,pages));
        if((page+1)*45<routes.size()) board.inventory.setItem(MenuKit.nextSlot(54),MenuKit.next(page,pages));
        board.inventory.setItem(MenuKit.backSlot(54),MenuKit.back("World & Trade"));
        board.inventory.setItem(MenuKit.closeSlot(54),MenuKit.close());
        player.openInventory(board.inventory);
    }
    private static String title(String id) { return Arrays.stream(id.replace('-','_').split("_")).filter(word->!word.isEmpty()).map(word->Character.toUpperCase(word.charAt(0))+word.substring(1)).collect(java.util.stream.Collectors.joining(" ")); }
    @Override public void close() { if(renewal!=null) renewal.cancel(); }
    private static final class TravelBoard implements InventoryHolder {
        private final UUID owner; private final List<String> names; private final int page; private Inventory inventory;
        private TravelBoard(UUID owner,List<String> names,int page) { this.owner=owner;this.names=names;this.page=page; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class Board implements InventoryHolder {
        private final UUID owner; private Inventory inventory;
        private Board(UUID owner) { this.owner=owner; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
