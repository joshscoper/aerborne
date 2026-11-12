package net.nerdsmp.paper.death;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

/** Inventory stays in Minecraft. Spirit state only controls where and how a player returns to life. */
public final class SpiritService implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final SpiritRepository repository;
    private final SanctuaryStore sanctuaries;
    private final Map<UUID,Active> active=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,BossBar> bars=new HashMap<>();
    private final Map<UUID,List<Entity>> markers=new HashMap<>();
    private final Map<UUID,UUID> markerOwners=new HashMap<>();
    private final Map<UUID,Long> nextFailureNotice=new HashMap<>();
    private final Set<UUID> transfers=new HashSet<>(), applying=new HashSet<>(), offered=new HashSet<>();
    private final Set<UUID> loading=new HashSet<>();
    private final NamespacedKey stride;
    private final long delayMillis, sicknessMillis;
    private final double radius;
    private BukkitTask task;
    private static final String MARKER="nerdsmp.spirit_marker";

    public SpiritService(NerdSmpPlugin plugin) {
        this.plugin=plugin;repository=new SpiritRepository(plugin.playerData());
        sanctuaries=new SanctuaryStore(new File(plugin.getDataFolder(),"spirit-sanctuaries.yml"));stride=new NamespacedKey(plugin,"spirit_stride");
        delayMillis=Math.clamp(plugin.getConfig().getLong("death.resurrection-delay-seconds",10),0,60)*1000;
        sicknessMillis=Math.clamp(plugin.getConfig().getLong("death.healer-sickness-seconds",60),0,300)*1000;
        radius=Math.clamp(plugin.getConfig().getDouble("death.corpse-radius",16),4,32);
    }
    public SpiritRepository repository(){return repository;}
    public SanctuaryStore sanctuaries(){return sanctuaries;}
    public boolean ghost(UUID player){var a=active.get(player);return a!=null&&a.state().ghost();}
    private boolean restricted(UUID player){return loading.contains(player)||ghost(player);}
    @EventHandler(priority=EventPriority.LOWEST) public void joining(PlayerJoinEvent event){loading.add(event.getPlayer().getUniqueId());}
    public SpiritState state(UUID player){var a=active.get(player);return a==null?SpiritState.alive():a.state();}
    public void start(){
        for(World world:Bukkit.getWorlds())for(Entity entity:world.getEntities())if(entity.getScoreboardTags().contains(MARKER))entity.remove();
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,10,20);
    }
    public void ready(Player player){
        AccountId actor=plugin.sessions().account(player.getUniqueId()).orElse(null);if(actor==null)return;
        try{
            var state=repository.load(actor);active.put(player.getUniqueId(),new Active(actor,state));loading.remove(player.getUniqueId());
            for(Player other:Bukkit.getOnlinePlayers())if(ghost(other.getUniqueId())&&!other.equals(player))player.hideEntity(plugin,other);
            if(state.ghost()&&!player.isDead()){
                if(state.restoration()!=null)complete(player);
                else{applyGhost(player);if(!player.getWorld().getUID().equals(state.soul().corpse().world())||player.getY()<player.getWorld().getMinHeight())returnToSanctuary(player);announce(player);}
            }
        }catch(RuntimeException failure){fail(player,failure);player.kick(Component.text("Your resurrection data could not be loaded. Please contact staff."));}
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void death(PlayerDeathEvent event){
        retain(event);Player player=event.getPlayer();
        if(ghost(player.getUniqueId())||!spiritWorld(player.getWorld())||!Set.of(GameMode.SURVIVAL,GameMode.ADVENTURE).contains(player.getGameMode()))return;
        AccountId actor=plugin.sessions().account(player.getUniqueId()).orElse(null);if(actor==null)return;
        try{
            Location corpse=safe(player.getLocation(),4,8);var shrine=nearest(player.getLocation());long now=System.currentTimeMillis();
            var abilities=new SpiritState.Abilities(player.getGameMode().name(),player.isInvulnerable(),player.getAllowFlight(),player.isFlying(),point(player.getCompassTarget()));
            var soul=new SpiritState.Soul(UUID.randomUUID(),point(corpse==null?bounded(player.getLocation()):corpse),shrine.location(),shrine.name(),corpse!=null&&corpse.getWorld().getUID().equals(shrine.location().world()),now,now+delayMillis,abilities);
            save(player,new SpiritState(soul,null,0,0));
            Chat.notice(player,"Your inventory and XP are safe. Respawn to release your spirit at "+Chat.escape(shrine.name())+".");
        }catch(RuntimeException failure){fail(player,failure);Chat.notice(player,"Your inventory is safe. You will use a normal safe respawn this time.");}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void retainFinal(PlayerDeathEvent event){retain(event);}
    private static void retain(PlayerDeathEvent event){event.setKeepInventory(true);event.setKeepLevel(true);event.getDrops().clear();event.setDroppedExp(0);}

    @EventHandler(priority=EventPriority.HIGHEST)
    public void respawn(PlayerRespawnEvent event){
        Player player=event.getPlayer();var soul=state(player.getUniqueId()).soul();if(soul==null)return;
        Location destination=resolve(soul.sanctuary());if(destination==null)destination=fallback(player.getWorld());
        event.setRespawnLocation(destination);
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(!current(player)||!ghost(player.getUniqueId())||player.isDead())return;
            try{if(state(player.getUniqueId()).restoration()!=null)complete(player);else{applyGhost(player);announce(player);open(player);}}catch(RuntimeException failure){fail(player,failure);}
        },2);
    }
    private void applyGhost(Player player){
        if(!ghost(player.getUniqueId())||player.isDead())return;
        applying.add(player.getUniqueId());
        try{
            player.closeInventory();player.leaveVehicle();player.setGameMode(GameMode.ADVENTURE);player.setFlying(false);player.setAllowFlight(false);player.setInvulnerable(true);player.setGliding(false);player.setFireTicks(0);player.setFallDistance(0);
            var movement=player.getAttribute(Attribute.MOVEMENT_SPEED);if(movement!=null&&movement.getModifier(stride)==null)movement.addTransientModifier(new AttributeModifier(stride,.15,AttributeModifier.Operation.ADD_SCALAR));
            var soul=state(player.getUniqueId()).soul();Location corpse=resolve(soul.corpse());if(corpse!=null)player.setCompassTarget(corpse);
            for(Player viewer:Bukkit.getOnlinePlayers())if(!viewer.equals(player))viewer.hideEntity(plugin,player);
            for(Entity entity:player.getNearbyEntities(48,24,48))if(entity instanceof Mob mob&&player.equals(mob.getTarget()))mob.setTarget(null);
            if(plugin.watchdog()!=null)plugin.watchdog().grantGrace(player,5000);
        }finally{applying.remove(player.getUniqueId());}
    }
    private void announce(Player player){
        Chat.info(player,"<aqua>You are a spirit.</aqua> Return to your body and choose "+Chat.command("spirit resurrect")+", or use the Spirit Healer at your sanctuary.");
        Chat.info(player,"You cannot fight, gather, trade, or travel while a spirit. "+Chat.command("spirit")+" opens your choices; "+Chat.command("spirit return")+" returns to the healer.");
    }
    private void save(Player player,SpiritState state){
        AccountId actor=plugin.sessions().account(player.getUniqueId()).orElseThrow(()->new IllegalStateException("Account is not ready"));
        repository.save(actor,state);active.put(player.getUniqueId(),new Active(actor,state));
    }
    private boolean current(Player player){var a=active.get(player.getUniqueId());return player.isOnline()&&Bukkit.getPlayer(player.getUniqueId())==player&&a!=null&&plugin.sessions().account(player.getUniqueId()).filter(a.account()::equals).isPresent();}

    private boolean spiritWorld(World world){
        if(net.nerdsmp.paper.expedition.InstanceWorlds.isRunWorld(world)||plugin.skyblock()!=null&&plugin.skyblock().isIslandWorld(world))return false;
        List<String> configured=plugin.getConfig().getStringList("death.spirit-worlds");if(configured.isEmpty())configured=List.of("MMORPG");
        return configured.stream().anyMatch(name->world.getName().equalsIgnoreCase(name))||plugin.worldPacks().adventureWorld(world);
    }
    private SanctuaryStore.Sanctuary nearest(Location origin){
        List<SanctuaryStore.Sanctuary> choices=new ArrayList<>(sanctuaries.all().stream().filter(s->s.location().world().equals(origin.getWorld().getUID())).toList());
        List<String> warps=plugin.getConfig().getStringList("death.sanctuary-warps");if(warps.isEmpty())warps=List.of("highcrown","druids_ward","sandmere");
        for(String warp:warps)plugin.travel().warpLocation(warp).filter(l->l.getWorld().equals(origin.getWorld())).ifPresent(l->choices.add(new SanctuaryStore.Sanctuary("warp_"+warp,words(warp),point(l))));
        var ordered=choices.stream().sorted(Comparator.comparingDouble(s->resolve(s.location()).distanceSquared(origin))).toList();
        for(var choice:ordered){Location safe=safe(resolve(choice.location()),3,6);if(safe!=null)return new SanctuaryStore.Sanctuary(choice.id(),choice.name(),point(safe));}
        Location safe=fallback(origin.getWorld());return new SanctuaryStore.Sanctuary("world_spawn","Sanctuary",point(safe));
    }
    private Location fallback(World world){
        Location destination=safe(world.getSpawnLocation(),6,12);if(destination!=null)return destination;
        Location hub=plugin.travel().publicSpawn();if(hub!=null){destination=safe(hub,6,12);if(destination!=null)return destination;}
        throw new IllegalStateException("No safe resurrection sanctuary is available");
    }
    private static String words(String value){return Arrays.stream(value.split("[_-]")).map(s->s.isEmpty()?s:Character.toUpperCase(s.charAt(0))+s.substring(1)).collect(java.util.stream.Collectors.joining(" "));}
    public static SpiritState.Point point(Location location){return new SpiritState.Point(location.getWorld().getUID(),location.getX(),location.getY(),location.getZ(),location.getYaw(),location.getPitch());}
    public static Location resolve(SpiritState.Point point){World world=Bukkit.getWorld(point.world());return world==null?null:new Location(world,point.x(),point.y(),point.z(),point.yaw(),point.pitch());}
    private static Location bounded(Location location){Location result=location.clone();result.setY(Math.clamp(result.getY(),location.getWorld().getMinHeight(),location.getWorld().getMaxHeight()-2));return result;}
    public static Location safe(Location at,int horizontal,int vertical){
        if(at==null)return null;World world=at.getWorld();
        int y=Math.clamp(at.getBlockY(),world.getMinHeight()+1,world.getMaxHeight()-2);
        for(int r=0;r<=horizontal;r++)for(int dy=0;dy<=vertical;dy++)for(int sign:dy==0?new int[]{1}:new int[]{1,-1})for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
            if(Math.max(Math.abs(x),Math.abs(z))!=r)continue;
            int testY=y+dy*sign;if(testY<=world.getMinHeight()||testY>=world.getMaxHeight()-1)continue;
            Location candidate=new Location(world,at.getBlockX()+x+.5,testY,at.getBlockZ()+z+.5,at.getYaw(),at.getPitch());
            if(standing(candidate)&&world.getWorldBorder().isInside(candidate))return candidate;
        }
        return null;
    }
    private static boolean standing(Location at){
        var feet=at.getBlock();var head=feet.getRelative(0,1,0);var floor=feet.getRelative(0,-1,0);
        return feet.isPassable()&&head.isPassable()&&!feet.isLiquid()&&!head.isLiquid()&&floor.getType().isSolid()&&!hazard(feet.getType())&&!hazard(head.getType())&&!hazard(floor.getType());
    }
    private static boolean hazard(Material material){return Set.of(Material.LAVA,Material.FIRE,Material.SOUL_FIRE,Material.MAGMA_BLOCK,Material.CACTUS,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW,Material.SWEET_BERRY_BUSH,Material.WITHER_ROSE,Material.NETHER_PORTAL,Material.END_PORTAL).contains(material);}

    private void returnToSanctuary(Player player){
        var soul=state(player.getUniqueId()).soul();if(soul==null)return;
        Location target=resolve(soul.sanctuary());if(target==null||safe(target,3,6)==null){target=fallback(player.getWorld());var next=new SpiritState.Soul(soul.id(),soul.corpse(),point(target),"Sanctuary",false,soul.diedAt(),soul.readyAt(),soul.abilities());save(player,new SpiritState(next,null,0,0));}
        else target=safe(target,3,6);
        if(!teleport(player,target))throw new IllegalStateException("The sanctuary teleport was refused");player.setFallDistance(0);player.setFireTicks(0);
    }
    private boolean teleport(Player player,Location destination){transfers.add(player.getUniqueId());try{return player.teleport(destination);}finally{transfers.remove(player.getUniqueId());}}

    private void resurrect(Player player,boolean healer){
        if(!current(player)||player.isDead())return;var state=state(player.getUniqueId());var soul=state.soul();
        if(soul==null){Chat.notice(player,"You are already alive.");return;}
        if(state.restoration()!=null){complete(player);return;}
        long now=System.currentTimeMillis();if(!soul.ready(now)){Chat.notice(player,"Your spirit is gathering strength. Resurrection is ready in "+((soul.readyAt()-now+999)/1000)+" seconds.");return;}
        Location target;
        if(healer){
            if(!SpiritState.within(point(player.getLocation()),soul.sanctuary(),8)){Chat.notice(player,"Return to the Spirit Healer first: "+Chat.command("spirit return")+".");return;}
            target=safe(resolve(soul.sanctuary()),3,6);if(target==null)throw new IllegalArgumentException("This sanctuary is obstructed. Use /spirit return for a safe alternative.");
        }else{
            if(!soul.recoverable()||!SpiritState.within(point(player.getLocation()),soul.corpse(),radius)){Chat.notice(player,"Move within "+(int)radius+" blocks of your body, or return to the Spirit Healer.");return;}
            target=standing(player.getLocation())?player.getLocation().clone():safe(player.getLocation(),2,3);
            if(target==null||!SpiritState.within(point(target),soul.corpse(),radius)){Chat.notice(player,"Stand on safe ground near your body before resurrecting.");return;}
        }
        save(player,state.resurrect(point(target),healer,now,sicknessMillis,5000));complete(player);
    }
    private void complete(Player player){
        if(!current(player)||player.isDead())return;SpiritState state=state(player.getUniqueId());var restore=state.restoration();if(restore==null)return;
        if(state.protectedUntil()<System.currentTimeMillis()){save(player,new SpiritState(state.soul(),restore,state.sicknessUntil(),System.currentTimeMillis()+5000));state=state(player.getUniqueId());}
        Location target=resolve(restore.destination());if(target==null||!standing(target)){target=fallback(player.getWorld());save(player,new SpiritState(state.soul(),new SpiritState.Restoration(point(target),restore.healthFraction()),state.sicknessUntil(),state.protectedUntil()));state=state(player.getUniqueId());}
        if(!teleport(player,target))throw new IllegalStateException("Resurrection teleport was refused; your spirit remains safe");
        restoreAbilities(player,state.soul().abilities());plugin.playerStats().refresh(player);
        double maximum=player.getAttribute(Attribute.MAX_HEALTH).getValue();player.setHealth(Math.clamp(maximum*restore.healthFraction(),1,maximum));
        player.setFoodLevel(20);player.setSaturation(5);player.setFireTicks(0);player.setFallDistance(0);
        player.saveData(); // A crash before acknowledgement replays restoration while interaction remains blocked.
        save(player,state.restored());clearPresentation(player);player.closeInventory();
        if(plugin.watchdog()!=null)plugin.watchdog().grantGrace(player,5000);
        Chat.success(player,"You are alive. Your inventory and XP are intact."+(state.sick(System.currentTimeMillis())?" Resurrection sickness reduces damage dealt by 25% for "+Math.max(1,(state.sicknessUntil()-System.currentTimeMillis()+999)/1000)+" seconds.":""));
    }
    private void restoreAbilities(Player player,SpiritState.Abilities abilities){
        applying.add(player.getUniqueId());try{
            var movement=player.getAttribute(Attribute.MOVEMENT_SPEED);if(movement!=null)movement.removeModifier(stride);
            player.setGameMode(GameMode.valueOf(abilities.mode()));player.setInvulnerable(abilities.invulnerable());player.setAllowFlight(abilities.allowFlight());player.setFlying(abilities.flying());
            Location compass=resolve(abilities.compass());if(compass!=null)player.setCompassTarget(compass);
        }finally{applying.remove(player.getUniqueId());}
    }

    public boolean handle(org.bukkit.command.CommandSender sender,String[] args){
        try{
            if(args.length>0&&args[0].equalsIgnoreCase("sanctuary")){manageSanctuary(sender,args);return true;}
            if(args.length>0&&args[0].equalsIgnoreCase("restore")){
                if(!sender.hasPermission("nerdsmp.death.admin"))throw new IllegalArgumentException("Only death-system administrators can restore another player.");
                Player target=args.length==2?Bukkit.getPlayerExact(args[1]):null;if(target==null||!ghost(target.getUniqueId()))throw new IllegalArgumentException("Use /spirit restore <online spirit player>.");
                var old=state(target.getUniqueId());Location destination=resolve(old.soul().sanctuary());if(destination==null||!standing(destination))destination=fallback(target.getWorld());
                save(target,new SpiritState(old.soul(),new SpiritState.Restoration(point(destination),1),0,System.currentTimeMillis()+5000));complete(target);
                plugin.getLogger().info(sender.getName()+" restored "+target.getName()+" through the spirit recovery command.");Chat.success(sender,"Restored "+Chat.escape(target.getName())+".");return true;
            }
            if(!(sender instanceof Player player)){Chat.playersOnly(sender);return true;}
            if(!current(player)){Chat.accountLoading(player);return true;}
            if(!ghost(player.getUniqueId())){Chat.info(player,"Death keeps your inventory and XP. In adventure worlds, return to your body as a spirit or choose the sanctuary's Spirit Healer.");return true;}
            String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
            switch(action){
                case "menu"->open(player);
                case "resurrect"->resurrect(player,false);
                case "healer"->open(player,true);
                case "return","unstuck"->{returnToSanctuary(player);open(player);}
                case "locate"->{var s=state(player.getUniqueId()).soul();Chat.info(player,"Your body: <white>"+Math.round(s.corpse().x())+", "+Math.round(s.corpse().y())+", "+Math.round(s.corpse().z())+"</white>. "+Chat.command("spirit")+" shows your choices.");}
                default->Chat.usage(player,"/spirit [resurrect|healer|return|locate]");
            }
        }catch(IllegalArgumentException failure){Chat.error(sender,Chat.escape(failure.getMessage()));}
        catch(RuntimeException failure){if(sender instanceof Player p)fail(p,failure);else{plugin.getLogger().log(java.util.logging.Level.SEVERE,"Spirit command failed",failure);Chat.error(sender,"The resurrection change could not be saved.");}}
        return true;
    }
    private void manageSanctuary(org.bukkit.command.CommandSender sender,String[] args){
        if(!sender.hasPermission("nerdsmp.death.admin"))throw new IllegalArgumentException("Only death-system administrators can manage sanctuaries.");
        if(args.length==1||args.length==2&&args[1].equalsIgnoreCase("list")){
            Chat.info(sender,"Configured spirit sanctuaries (shipped safe-town warps are also used):");for(var s:sanctuaries.all())Chat.info(sender,Chat.escape(s.id()+" · "+s.name()+" · "+s.location().world()+" · "+Math.round(s.location().x())+", "+Math.round(s.location().y())+", "+Math.round(s.location().z())));return;
        }
        if(args.length==3&&args[1].equalsIgnoreCase("remove")){if(!sanctuaries.remove(args[2]))throw new IllegalArgumentException("No sanctuary with that id.");Chat.success(sender,"Sanctuary removed. Existing spirits keep their saved sanctuary.");return;}
        if(args.length>=3&&args[1].equalsIgnoreCase("set")&&sender instanceof Player p){
            if(ghost(p.getUniqueId()))throw new IllegalArgumentException("Resurrect before placing a sanctuary.");
            if(net.nerdsmp.paper.expedition.InstanceWorlds.isRunWorld(p.getWorld())||plugin.skyblock()!=null&&plugin.skyblock().isIslandWorld(p.getWorld()))throw new IllegalArgumentException("Place sanctuaries in a public adventure world.");
            if(!standing(p.getLocation()))throw new IllegalArgumentException("Stand on a safe floor before placing a sanctuary.");
            String name=args.length>3?String.join(" ",Arrays.copyOfRange(args,3,args.length)):words(args[2]);sanctuaries.put(new SanctuaryStore.Sanctuary(args[2],name,point(p.getLocation())));Chat.success(sender,"Sanctuary saved at your position.");return;
        }
        throw new IllegalArgumentException("Use /spirit sanctuary set <id> [name], remove <id>, or list.");
    }
    public List<String> complete(org.bukkit.command.CommandSender sender,String[] args){
        if(args.length==1)return sender.hasPermission("nerdsmp.death.admin")?List.of("resurrect","healer","return","locate","sanctuary","restore"):List.of("resurrect","healer","return","locate");
        if(sender.hasPermission("nerdsmp.death.admin")&&args.length==2&&args[0].equals("sanctuary"))return List.of("set","remove","list");return List.of();
    }
    private void open(Player player){open(player,false);}
    private void open(Player player,boolean confirmation){
        var soul=state(player.getUniqueId()).soul();if(soul==null||player.isDead())return;
        SpiritPage page=new SpiritPage(player.getUniqueId(),soul.id(),confirmation);page.inventory=Bukkit.createInventory(page,27,Chat.render(confirmation?"<aqua>Spirit Healer · Confirm</aqua>":"<aqua>Between Life and Death</aqua>"));MenuKit.frame(page.inventory);
        if(confirmation){
            page.inventory.setItem(13,MenuKit.item(Material.TOTEM_OF_UNDYING,"<aqua>Resurrect at "+Chat.escape(soul.sanctuaryName())+"</aqua>",List.of("<gray>Return with full health. Keep all gear and XP.</gray>",soul.recoverable()?"<yellow>Damage dealt reduced by 25% for "+sicknessMillis/1000+" seconds.</yellow>":"<green>Your body is unreachable: no sickness penalty.</green>","<gray>No money charge or durability penalty.</gray>")));
            page.inventory.setItem(20,MenuKit.cancel());page.inventory.setItem(24,MenuKit.confirm(List.of("<gray>Requires the Spirit Healer within eight blocks.</gray>")));
        }else{
            page.inventory.setItem(4,summary(soul));
            page.inventory.setItem(11,MenuKit.item(Material.SKELETON_SKULL,"<green>Return to your body</green>",List.of("<gray>Body: "+Math.round(soul.corpse().x())+", "+Math.round(soul.corpse().y())+", "+Math.round(soul.corpse().z())+"</gray>","<gray>Stand within "+(int)radius+" blocks on safe ground.</gray>","<gray>Return with 50% health and no sickness.</gray>",soul.recoverable()?"<yellow>Click to resurrect when near your body.</yellow>":"<gray>Body unreachable. Use the healer without sickness.</gray>")));
            page.inventory.setItem(13,MenuKit.item(Material.TOTEM_OF_UNDYING,"<aqua>Spirit Healer</aqua>",List.of("<gray>Return at your sanctuary with full health.</gray>",soul.recoverable()?"<yellow>25% less damage dealt for "+sicknessMillis/1000+" seconds.</yellow>":"<green>Unreachable body: no sickness.</green>","<yellow>Click to review and confirm.</yellow>")));
            page.inventory.setItem(15,MenuKit.item(Material.RECOVERY_COMPASS,"<yellow>Return to the healer</yellow>",List.of("<gray>Move your spirit back to its sanctuary.</gray>","<gray>Also works if you become stuck.</gray>")));
            page.inventory.setItem(22,MenuKit.close());
        }
        player.openInventory(page.inventory);
    }
    private ItemStack summary(SpiritState.Soul soul){long seconds=Math.max(0,(soul.readyAt()-System.currentTimeMillis()+999)/1000);return MenuKit.item(Material.SOUL_LANTERN,"<aqua>You are a spirit</aqua>",List.of("<gray>Your inventory and XP are safe.</gray>","<gray>Sanctuary: "+Chat.escape(soul.sanctuaryName())+"</gray>",seconds>0?"<yellow>Resurrection ready in "+seconds+" seconds.</yellow>":"<green>Your spirit is ready to return.</green>"));}
    @EventHandler(priority=EventPriority.LOWEST)
    public void click(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player)||!restricted(player.getUniqueId()))return;event.setCancelled(true);
        if(!(event.getView().getTopInventory().getHolder() instanceof SpiritPage page)||!page.viewer.equals(player.getUniqueId())||!page.soul.equals(state(player.getUniqueId()).soul().id())||!event.isLeftClick())return;
        int slot=event.getRawSlot();Bukkit.getScheduler().runTask(plugin,()->{
            if(!current(player)||!restricted(player.getUniqueId())||player.getOpenInventory().getTopInventory()!=page.inventory||!page.soul.equals(state(player.getUniqueId()).soul().id()))return;
            safely(player,()->{
                if(page.confirmation){if(slot==20)open(player);else if(slot==24)resurrect(player,true);}
                else if(slot==11)resurrect(player,false);else if(slot==13)open(player,true);else if(slot==15){returnToSanctuary(player);open(player);}else if(slot==22)player.closeInventory();
            });
        });
    }
    @EventHandler(priority=EventPriority.LOWEST) public void drag(InventoryDragEvent event){if(restricted(event.getWhoClicked().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void inventory(InventoryOpenEvent event){if(restricted(event.getPlayer().getUniqueId())&&!(event.getInventory().getHolder() instanceof SpiritPage))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void blockBreak(BlockBreakEvent event){if(restricted(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void blockPlace(BlockPlaceEvent event){if(restricted(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void interact(PlayerInteractEvent event){if(restricted(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void interactEntity(PlayerInteractEntityEvent event){
        UUID owner=markerOwners.get(event.getRightClicked().getUniqueId());
        if(owner!=null){event.setCancelled(true);if(owner.equals(event.getPlayer().getUniqueId())&&restricted(owner))open(event.getPlayer());return;}
        if(restricted(event.getPlayer().getUniqueId()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void armorStand(PlayerArmorStandManipulateEvent event){if(restricted(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void drop(PlayerDropItemEvent event){if(restricted(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void pickup(EntityPickupItemEvent event){if(restricted(event.getEntity().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void swap(PlayerSwapHandItemsEvent event){if(restricted(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void consume(PlayerItemConsumeEvent event){if(restricted(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void bucketEmpty(PlayerBucketEmptyEvent event){if(restricted(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void bucketFill(PlayerBucketFillEvent event){if(restricted(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void experience(PlayerExpChangeEvent event){if(restricted(event.getPlayer().getUniqueId()))event.setAmount(0);}
    @EventHandler(priority=EventPriority.LOWEST) public void experiencePickup(com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent event){if(restricted(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void combust(EntityCombustEvent event){if(restricted(event.getEntity().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void food(FoodLevelChangeEvent event){if(restricted(event.getEntity().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void exhaustion(EntityExhaustionEvent event){if(restricted(event.getEntity().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void air(EntityAirChangeEvent event){if(restricted(event.getEntity().getUniqueId())&&event.getEntity() instanceof LivingEntity living)event.setAmount(living.getMaximumAir());}
    @EventHandler(priority=EventPriority.LOWEST) public void frostWalker(EntityBlockFormEvent event){if(restricted(event.getEntity().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void fishing(PlayerFishEvent event){if(restricted(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void shoot(EntityShootBowEvent event){if(restricted(event.getEntity().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void launch(ProjectileLaunchEvent event){if(event.getEntity().getShooter() instanceof Player p&&restricted(p.getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void target(EntityTargetLivingEntityEvent event){if(event.getTarget()!=null&&restricted(event.getTarget().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void mount(EntityMountEvent event){if(restricted(event.getEntity().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void mode(PlayerGameModeChangeEvent event){if(ghost(event.getPlayer().getUniqueId())&&!applying.contains(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void flight(PlayerToggleFlightEvent event){if(ghost(event.getPlayer().getUniqueId())&&!applying.contains(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void travel(PlayerTeleportEvent event){if(ghost(event.getPlayer().getUniqueId())&&!transfers.contains(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void portal(PlayerPortalEvent event){if(ghost(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void damage(EntityDamageEvent event){if(markerOwners.containsKey(event.getEntity().getUniqueId())||restricted(event.getEntity().getUniqueId())||event.getEntity() instanceof Player p&&state(p.getUniqueId()).protectedNow(System.currentTimeMillis()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void attack(EntityDamageByEntityEvent event){
        if(!(event.getDamageSource().getCausingEntity() instanceof Player player))return;
        if(restricted(player.getUniqueId())){event.setCancelled(true);return;}
        var state=state(player.getUniqueId());if(state.protectedNow(System.currentTimeMillis())){try{save(player,state.endProtection());}catch(RuntimeException failure){event.setCancelled(true);fail(player,failure);}}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void sickness(EntityDamageByEntityEvent event){if(event.getDamageSource().getCausingEntity() instanceof Player p&&state(p.getUniqueId()).sick(System.currentTimeMillis()))event.setDamage(event.getDamage()*.75);}
    @EventHandler(priority=EventPriority.LOWEST) public void command(PlayerCommandPreprocessEvent event){
        if(!restricted(event.getPlayer().getUniqueId()))return;
        String command=event.getMessage().substring(1).split("\\s+",2)[0].toLowerCase(Locale.ROOT);if(command.contains(":"))command=command.substring(command.lastIndexOf(':')+1);
        if(!Set.of("spirit","resurrect","soul","support","helpme","helpop","report","msg","tell","w","reply","r","channel","globalchat","localchat","tradechat","staffchat","partychat","tips","website","web").contains(command)){event.setCancelled(true);Chat.notice(event.getPlayer(),"Resurrect before using gameplay commands. "+Chat.command("spirit")+" opens your choices; staff help remains available.");}
    }
    @EventHandler public void quit(PlayerQuitEvent event){Player p=event.getPlayer();clearPresentation(p);active.remove(p.getUniqueId());offered.remove(p.getUniqueId());nextFailureNotice.remove(p.getUniqueId());loading.remove(p.getUniqueId());}

    private void tick(){
        for(Player player:Bukkit.getOnlinePlayers()){
            if(!current(player)||player.isDead())continue;
            safely(player,()->{
                var state=state(player.getUniqueId());long now=System.currentTimeMillis();
                if(state.ghost()){
                    if(state.restoration()!=null){complete(player);return;}
                    if(player.getGameMode()!=GameMode.ADVENTURE||!player.isInvulnerable())applyGhost(player);
                    if(player.getY()<player.getWorld().getMinHeight()+1){returnToSanctuary(player);return;}
                    var soul=state.soul();boolean near=soul.recoverable()&&SpiritState.within(point(player.getLocation()),soul.corpse(),radius);
                    if(player.getOpenInventory().getTopInventory().getHolder() instanceof SpiritPage page&&!page.confirmation)page.inventory.setItem(4,summary(soul));
                    String distance=soul.recoverable()&&player.getWorld().getUID().equals(soul.corpse().world())?SpiritState.direction(point(player.getLocation()),soul.corpse())+" · "+Math.round(player.getLocation().distance(resolve(soul.corpse())))+" blocks to your body":"Body unreachable · healer has no penalty";
                    showBar(player,Component.text("Spirit · "+distance+" · /spirit"),BossBar.Color.BLUE,1);
                    if(near&&soul.ready(now)&&offered.add(player.getUniqueId()))Chat.info(player,"<green>Your body is nearby.</green> Stand somewhere safe and choose "+Chat.command("spirit resurrect")+" to return to life.");
                    if(!near)offered.remove(player.getUniqueId());ensureMarkers(player,soul);
                }else if(state.sick(now)){showBar(player,Component.text("Resurrection sickness · −25% damage dealt · "+((state.sicknessUntil()-now+999)/1000)+"s"),BossBar.Color.PURPLE,(float)Math.clamp((state.sicknessUntil()-now)/(double)Math.max(1,sicknessMillis),0,1));}
                else hideBar(player);
            });
        }
    }
    private void showBar(Player player,Component text,BossBar.Color color,float progress){var bar=bars.computeIfAbsent(player.getUniqueId(),id->{var created=BossBar.bossBar(text,progress,color,BossBar.Overlay.PROGRESS);player.showBossBar(created);return created;});bar.name(text).color(color).progress(progress);}
    private void hideBar(Player player){BossBar bar=bars.remove(player.getUniqueId());if(bar!=null)player.hideBossBar(bar);}
    private void ensureMarkers(Player player,SpiritState.Soul soul){
        List<Entity> owned=markers.computeIfAbsent(player.getUniqueId(),id->new ArrayList<>());owned.removeIf(entity->{if(entity.isValid())return false;markerOwners.remove(entity.getUniqueId());return true;});
        Location shrine=resolve(soul.sanctuary()),corpse=resolve(soul.corpse());
        if(shrine!=null&&shrine.getWorld().equals(player.getWorld())&&shrine.distanceSquared(player.getLocation())<64*64&&owned.stream().noneMatch(e->e instanceof Allay)){
            Allay healer=shrine.getWorld().spawn(shrine.clone().add(1.5,1,0),Allay.class,e->{e.setAI(false);e.setSilent(true);e.setGravity(false);e.setInvulnerable(true);e.setCollidable(false);e.customName(Component.text("Spirit Healer"));e.setCustomNameVisible(true);e.setGlowing(true);privateEntity(e);});attach(player,owned,healer);
        }
        if(soul.recoverable()&&corpse!=null&&corpse.getWorld().equals(player.getWorld())&&corpse.distanceSquared(player.getLocation())<64*64&&owned.stream().noneMatch(e->e instanceof TextDisplay)){
            TextDisplay body=corpse.getWorld().spawn(corpse.clone().add(0,.5,0),TextDisplay.class,e->{e.text(Component.text("Your fallen body\nInventory and XP retained\n/spirit resurrect"));e.setBillboard(Display.Billboard.CENTER);e.setSeeThrough(true);e.setBrightness(new Display.Brightness(15,15));privateEntity(e);});attach(player,owned,body);
            ItemDisplay icon=corpse.getWorld().spawn(corpse.clone().add(0,.15,0),ItemDisplay.class,e->{e.setItemStack(new ItemStack(Material.SKELETON_SKULL));privateEntity(e);});attach(player,owned,icon);
            Interaction hitbox=corpse.getWorld().spawn(corpse,Interaction.class,e->{e.setInteractionWidth(1.5f);e.setInteractionHeight(2);e.setResponsive(true);privateEntity(e);});attach(player,owned,hitbox);
        }
        if(corpse!=null&&corpse.getWorld().equals(player.getWorld())&&corpse.distanceSquared(player.getLocation())<48*48)player.spawnParticle(Particle.SOUL_FIRE_FLAME,corpse.clone().add(0,.4,0),5,.25,.3,.25,0);
    }
    private static void privateEntity(Entity entity){entity.setVisibleByDefault(false);entity.setPersistent(false);entity.addScoreboardTag(MARKER);}
    private void attach(Player player,List<Entity> owned,Entity entity){owned.add(entity);markerOwners.put(entity.getUniqueId(),player.getUniqueId());player.showEntity(plugin,entity);}
    private void clearPresentation(Player player){hideBar(player);offered.remove(player.getUniqueId());var entities=markers.remove(player.getUniqueId());if(entities!=null)for(Entity entity:entities){markerOwners.remove(entity.getUniqueId());entity.remove();}for(Player viewer:Bukkit.getOnlinePlayers())if(!viewer.equals(player))viewer.showEntity(plugin,player);}
    private void safely(Player player,Runnable action){try{action.run();}catch(IllegalArgumentException failure){Chat.error(player,Chat.escape(failure.getMessage()));}catch(RuntimeException failure){fail(player,failure);}}
    private void fail(Player player,RuntimeException failure){long now=System.currentTimeMillis();if(nextFailureNotice.getOrDefault(player.getUniqueId(),0L)>now)return;nextFailureNotice.put(player.getUniqueId(),now+10000);plugin.getLogger().log(java.util.logging.Level.SEVERE,"Spirit recovery failed for "+player.getUniqueId(),failure);Chat.error(player,"Your spirit change could not be saved. Your inventory is safe; try again or use /helpme.");}
    @Override public void close(){if(task!=null)task.cancel();for(Player player:Bukkit.getOnlinePlayers())clearPresentation(player);active.clear();markers.clear();markerOwners.clear();}
    private static final class SpiritPage implements InventoryHolder{
        final UUID viewer,soul;final boolean confirmation;Inventory inventory;
        SpiritPage(UUID viewer,UUID soul,boolean confirmation){this.viewer=viewer;this.soul=soul;this.confirmation=confirmation;}
        @Override public Inventory getInventory(){return inventory;}
    }
    private record Active(AccountId account,SpiritState state){}
}
