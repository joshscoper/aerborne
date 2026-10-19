package net.nerdsmp.smoke;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.zone.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Disposable loopback-only integration fixture. Never include this in a player-server release. */
public final class ZoneSmokePlugin extends JavaPlugin implements Listener {
    private boolean cancelNext;
    private org.bukkit.block.Block cancelledTreeBlock;
    private boolean cancelNpcBody, cancelNpcPlate;
    private UUID watchedStack;
    private int watchedStackDeaths;
    private UUID farmingPlayer;
    private boolean cancelFarmingItems, cancelFarmingPlant, replaceFarmingTool;
    private final List<net.nerdsmp.paper.gathering.HoeHarvestBreakEvent> farmingBreaks=new ArrayList<>();
    private org.bukkit.block.Block oreFurnace;
    private org.bukkit.event.inventory.FurnaceSmeltEvent lastOreSmelt;
    private int oreNativeSmelts, oreHopperMoves, oreCanceledSmelts;
    private boolean cancelOreSmelts;
    private NerdSmpPlugin core;
    @Override public void onEnable() {
        if(!Boolean.getBoolean("nerdsmp.zoneSmoke") || !Bukkit.getIp().equals("127.0.0.1"))
            throw new IllegalStateException("Run only in the disposable loopback smoke server");
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskLater(this,this::run,40);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void cancel(CreatureSpawnEvent e) {
        if(cancelNext && e.getEntity().getPersistentDataContainer().has(new NamespacedKey("nerdsmp","zone_spawn"))) {
            cancelNext=false; e.setCancelled(true);
        }
        if(cancelNpcBody && e.getEntity().getPersistentDataContainer().has(new NamespacedKey("nerdsmp","npc_instance"))) {
            cancelNpcBody=false; e.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void cancelPlate(org.bukkit.event.entity.EntitySpawnEvent e) {
        if(cancelNpcPlate && e.getEntity() instanceof TextDisplay) { cancelNpcPlate=false; e.setCancelled(true); }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void watchStack(org.bukkit.event.entity.EntityDeathEvent event) {
        if(event.getEntity().getUniqueId().equals(watchedStack)) watchedStackDeaths++;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void farmingItems(org.bukkit.event.player.PlayerHarvestBlockEvent event) {
        if(!event.getPlayer().getUniqueId().equals(farmingPlayer)
            || !(event instanceof net.nerdsmp.paper.gathering.HoeHarvestService.ItemsEvent)) return;
        if(cancelFarmingItems) { cancelFarmingItems=false;event.setCancelled(true); }
        if(replaceFarmingTool) { replaceFarmingTool=false;event.getPlayer().getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD)); }
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void farmingPlant(org.bukkit.event.block.BlockPlaceEvent event) {
        if(cancelFarmingPlant && event.getPlayer().getUniqueId().equals(farmingPlayer)) {
            cancelFarmingPlant=false;event.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void farmingBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if(event.getPlayer().getUniqueId().equals(farmingPlayer)
            && event instanceof net.nerdsmp.paper.gathering.HoeHarvestBreakEvent hoe) farmingBreaks.add(hoe);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void watchOreSmelt(org.bukkit.event.inventory.FurnaceSmeltEvent event) {
        if(oreFurnace==null || !event.getBlock().equals(oreFurnace)) return;
        lastOreSmelt=event; oreNativeSmelts++;
        getLogger().info("ORE_SMELT_EVENT source="+event.getSource().getType()+" amount="+event.getSource().getAmount()
            +" result="+event.getResult().getType()+" amount="+event.getResult().getAmount());
        // Deliberately later than the production MONITOR registration: verification must
        // observe final cancellation, not just the state at its own handler entry.
        if(cancelOreSmelts) { event.setCancelled(true); oreCanceledSmelts++; }
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void watchOreMove(org.bukkit.event.inventory.InventoryMoveItemEvent event) {
        if(oreFurnace!=null && event.getSource() instanceof org.bukkit.inventory.FurnaceInventory inventory
            && inventory.getHolder(false) instanceof org.bukkit.block.Furnace furnace
            && furnace.getBlock().equals(oreFurnace)) oreHopperMoves++;
    }
    private void run() {
        try {
            core=(NerdSmpPlugin)Bukkit.getPluginManager().getPlugin("NerdSMP");
            check(core!=null && core.isEnabled(),"plugin initialized");
            checkPublicBranding();
            WebApiSmoke.run(core, this::check);
            checkSocketItems();
            checkShopStock();
            checkRemoteShopRoutingAndRepairMetadata();
            check(!core.expeditions().generated(),"manual mode is default");
            check(Bukkit.getWorlds().stream().noneMatch(w->w.getKey().getKey().startsWith("nerdsmp_expeditions_")),"no generated expedition worlds created");
            World world=Bukkit.getWorlds().getFirst();
            for(int x=0;x<=15;x++) for(int z=0;z<=15;z++) for(int y=120;y<=127;y++) world.getBlockAt(x,y,z).setType(y==120?Material.STONE:Material.AIR,false);
            checkContentEngine(world);
            checkDeveloperSuite(world);
            checkNpcChatDialogue();
            checkEarnedGear();
            ItemExpansionSmoke.run(core, this::check);
            ArcaneForgeSmoke.run(core, world, this::check);
            checkBulkIdentification();
            checkBonusPipeline(world);
            checkProgressionExpansion(world);
            checkStaffPanel();
            checkAuctionAndPlayerStores(world);
            checkHoeHarvest(world);
            checkAreaWand(world);
            checkNpcPlacement(world);
            var zone=new ZoneDefinition("smoke","Smoke fixture",world.getUID(),new ZoneDefinition.Bounds(0,120,0,15,130,15),true,false,3,5,32,
                List.of(new ZoneDefinition.SpawnPoint(4.5,121,4.5,0),new ZoneDefinition.SpawnPoint(8.5,121,8.5,0)),
                List.of(new ZoneDefinition.MobEntry("vanilla:zombie",1,30)));
            core.zones().put(zone);
            check(new ZoneStore(core.getDataFolder().toPath().resolve("zones.yml").toFile()).find("smoke").orElseThrow().equals(zone),"zone file roundtrip");
            Method spawn=ZoneSpawner.class.getDeclaredMethod("spawn",ZoneDefinition.class,int.class,World.class,long.class); spawn.setAccessible(true);
            Method tick=ZoneSpawner.class.getDeclaredMethod("tick"); tick.setAccessible(true);
            tick.invoke(core.zoneSpawner());
            check(core.zoneSpawner().alive()==0,"empty combat area does not activate");
            long now=System.nanoTime()/1_000_000L;
            spawn.invoke(core.zoneSpawner(),zone,0,world,now);
            check(core.zoneSpawner().alive("smoke")==1,"safe real mob spawn counts once");
            LivingEntity zombie=world.getLivingEntities().stream().filter(e->core.zoneSpawner().zoneId(e).filter("smoke"::equals).isPresent()).findFirst().orElseThrow();
            check(core.mobs().level(zombie)==30,"authored level survives CreatureSpawnEvent");
            check(!zombie.isPersistent() && !zombie.getCanPickupItems(),"ephemeral mob cannot retain or steal items");
            check(zombie.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()>20,"authored level scales health");
            spawn.invoke(core.zoneSpawner(),zone,0,world,now);
            check(core.zoneSpawner().alive()==1,"occupied point cannot duplicate population");
            cancelNext=true; spawn.invoke(core.zoneSpawner(),zone,1,world,now);
            check(!cancelNext && core.zoneSpawner().alive()==1,"cancelled spawn consumes no capacity");
            LivingEntity outside=world.spawn(new Location(world,20.5,121,20.5),Cow.class);
            check(!core.zoneSpawner().canFight(zombie,outside),"zone mob cannot attack outside its area"); outside.remove();
            var safe=new ZoneDefinition(zone.id(),zone.title(),zone.worldId(),zone.bounds(),true,true,zone.cap(),zone.respawnSeconds(),zone.activationRadius(),zone.points(),zone.mobs());
            core.zones().put(safe); tick.invoke(core.zoneSpawner());
            check(core.zoneSpawner().alive()==0 && !zombie.isValid(),"safe-area edit removes managed population");
            core.zones().put(zone); tick.invoke(core.zoneSpawner());
            world.getBlockAt(4,122,4).setType(Material.STONE,false);
            spawn.invoke(core.zoneSpawner(),zone,0,world,now);
            check(core.zoneSpawner().alive()==0,"blocked headroom rejects spawn");
            world.getBlockAt(4,122,4).setType(Material.AIR,false);
            var custom=new ZoneDefinition(zone.id(),zone.title(),zone.worldId(),zone.bounds(),true,false,zone.cap(),zone.respawnSeconds(),zone.activationRadius(),zone.points(),
                List.of(new ZoneDefinition.MobEntry("custom:veil_stalker",1,22)));
            core.zones().put(custom); tick.invoke(core.zoneSpawner());
            spawn.invoke(core.zoneSpawner(),custom,0,world,now+10_000);
            LivingEntity elite=world.getLivingEntities().stream().filter(e->core.zoneSpawner().zoneId(e).isPresent()).findFirst().orElseThrow();
            check(core.zoneSpawner().alive()==1 && core.mobs().level(elite)==22 && core.mobs().isCustomMob(elite),"named RPG mob uses authored level and existing loot identity");
            checkAuthoredMobLoot(elite);
            core.zoneSpawner().close();
            check(!elite.isValid() && core.zoneSpawner().alive()==0,"orderly shutdown removes managed entities");
            core.zones().remove("smoke");
            AutoSmeltSmoke.run(core,this,world,()-> {
                try { startForestryCheck(world,()->startDigSiteCheck(world)); }
                catch(Throwable failure) {
                    getLogger().log(java.util.logging.Level.SEVERE,"ZONE_SMOKE_FAILED",failure);
                    try { Files.writeString(Path.of("zone-smoke-result.txt"),"FAIL: "+failure+"\n"); } catch(Exception ignored) { }
                    Bukkit.shutdown();
                }
            });
        } catch(Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE,"ZONE_SMOKE_FAILED",failure);
            try { Files.writeString(Path.of("zone-smoke-result.txt"),"FAIL: "+failure+"\n"); } catch(Exception ignored) { }
            Bukkit.shutdown();
        }
    }
    @SuppressWarnings("unchecked")
    private void checkPublicBranding() throws Exception {
        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        check(Arrays.stream(org.bukkit.event.server.ServerListPingEvent.getHandlerList().getRegisteredListeners())
            .anyMatch(listener->listener.getPlugin()==core&&listener.getListener() instanceof net.nerdsmp.paper.presentation.ServerBrandingListener),
            "public server-list branding listener is registered with Paper");
        var listener=new net.nerdsmp.paper.presentation.ServerBrandingListener(core);
        Object enabled=core.getConfig().get("branding.server-list-enabled"),name=core.getConfig().get("skyblock.display-name");
        UUID id=UUID.randomUUID();int[] updates={0};net.kyori.adventure.text.Component[] frame=new net.kyori.adventure.text.Component[3];
        Player viewer=(Player)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Player.class},
            (proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> id;case "getName" -> "ReleaseTester";
                case "playerListName" -> {frame[0]=(net.kyori.adventure.text.Component)args[0];updates[0]++;yield null;}
                case "sendPlayerListHeaderAndFooter" -> {frame[1]=(net.kyori.adventure.text.Component)args[0];frame[2]=(net.kyori.adventure.text.Component)args[1];updates[0]++;yield null;}
                default -> throw new UnsupportedOperationException("Branding fixture: "+method.getName());
            });
        var framesField=core.presentation().getClass().getDeclaredField("tabFrames");framesField.setAccessible(true);
        try {
            core.getConfig().set("branding.server-list-enabled",true);
            var ping=new org.bukkit.event.server.ServerListPingEvent(java.net.InetAddress.getLoopbackAddress(),net.kyori.adventure.text.Component.text("Before"),3,30);
            listener.onPing(ping);
            check(plain.serialize(ping.motd()).equals("AERBORNE » A Skyblock RPG Adventure\nBuild your island. Explore the skies. Discover magic.")
                &&ping.getNumPlayers()==3&&ping.getMaxPlayers()==30,
                "Paper server-list description renders the public two-line MOTD without spoofing player counts");
            core.getConfig().set("branding.server-list-enabled",false);ping.motd(net.kyori.adventure.text.Component.text("Proxy supplied"));listener.onPing(ping);
            check(plain.serialize(ping.motd()).equals("Proxy supplied"),"owners can opt out of the plugin MOTD without it overwriting a proxy's display");
            core.getConfig().set("skyblock.display-name","HearthSky");
            check(core.displayName().equals("Aerborne")&&plain.serialize(core.branding().welcome()).equals("Welcome to Aerborne!"),
                "an existing stock public name adapts in memory without a plugin identity or data-folder migration");
            check(core.getCommand("menu").equals(Bukkit.getPluginCommand("aerborne"))
                &&core.getCommand("menu").equals(Bukkit.getPluginCommand("hearthsky"))
                &&core.getCommand("menu").equals(Bukkit.getPluginCommand("nexus"))
                &&core.getCommand("nerdadmin").equals(Bukkit.getPluginCommand("aerborneadmin")),
                "new branded and compatibility command aliases resolve to the same secured handlers");
            var frameInventory=Bukkit.createInventory(null,45);net.nerdsmp.paper.gui.MenuKit.frame(frameInventory);
            check(frameInventory.getItem(1).getType()==Material.LIGHT_BLUE_STAINED_GLASS_PANE
                &&frameInventory.getItem(0).getType()==Material.BLACK_STAINED_GLASS_PANE
                &&frameInventory.getItem(10).getType()==Material.GRAY_STAINED_GLASS_PANE,
                "the shared sky-blue frame changes presentation but retains its existing slot geometry");
            core.getConfig().set("skyblock.display-name","<red>Literal Name</red>");core.getConfig().set("branding.server-list-enabled",true);listener.onPing(ping);
            check(plain.serialize(ping.motd()).contains("<red>Literal Name</red>"),"existing public-name configuration stays literal in the real Paper ping component");
            core.getConfig().set("skyblock.display-name",name);
            var view=new net.nerdsmp.paper.presentation.PlayerPresentationService.PlayerView(5000,25,200,300,"Vanguard","Undecided","Campaign",0,"None",
                net.nerdsmp.paper.presentation.PlayerPresentationService.Role.ADVENTURER,net.nerdsmp.api.model.PlatformIdentity.Platform.JAVA);
            var apply=core.presentation().getClass().getDeclaredMethod("applyTab",Player.class,view.getClass());apply.setAccessible(true);apply.invoke(core.presentation(),viewer,view);
            check(plain.serialize(frame[1]).equals("Aerborne\nSmall beginnings. Endless skies.")
                &&plain.serialize(frame[2]).contains("RPG Skyblock")&&!plain.serialize(frame[2]).contains("/menu")
                &&plain.serialize(frame[0]).contains("Vanguard")&&plain.serialize(frame[0]).contains("25"),
                "actual tab rendering uses public branding while preserving Adventurer rank and level");
            int before=updates[0];apply.invoke(core.presentation(),viewer,view);
            check(updates[0]==before,"unchanged branded tab frames are not resent on refresh");
            check(plain.serialize(core.branding().welcome()).equals("Welcome to Aerborne!"),"private welcome text uses the same public name");
            check(!core.campaigns().activeCampaign().toCompletableFuture().join().orElseThrow().milestones().isEmpty(),
                "compact candidate configuration loads real campaign milestone keys rather than an empty virtual default section");
            getLogger().info("ZONE_SMOKE_PUBLIC_BRANDING_COMPLETE PASS");
        } finally {
            core.getConfig().set("branding.server-list-enabled",enabled);core.getConfig().set("skyblock.display-name",name);
            ((Map<UUID,?>)framesField.get(core.presentation())).remove(id);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void cancelTreeBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if(event.getBlock().equals(cancelledTreeBlock)) event.setCancelled(true);
    }
    @SuppressWarnings("unchecked")
    private void checkBonusPipeline(World world) throws Exception {
        var fixture=new HoeFixture(world);var account=net.nerdsmp.api.model.AccountId.random();core.sessions().join(fixture.id,account);
        var snapshotsField=core.presentation().getClass().getDeclaredField("progressionSnapshots");snapshotsField.setAccessible(true);
        var snapshots=(Map<UUID,net.nerdsmp.api.service.ProgressionService.ProgressionSnapshot>)snapshotsField.get(core.presentation());
        var catalogField=core.items().getClass().getDeclaredField("catalog");catalogField.setAccessible(true);Object originalCatalog=catalogField.get(core.items());
        Cow target=world.spawn(new Location(world,12.5,121,12.5),Cow.class);
        try {
            ItemStack axe=core.items().createLoot(new net.nerdsmp.api.model.ItemId("wildwood_axe"),0,false,33449);
            var meta=axe.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(core,"loot_affixes"),PersistentDataType.STRING,"bountiful:3.0|scholarly:4.0|keen:2.0");axe.setItemMeta(meta);core.items().refresh(axe);
            check(core.items().improveQuality(axe)==net.nerdsmp.paper.item.PaperItemService.QualityUpgradeResult.APPLIED,"bonus fixture improves actual Wildwood workmanship");
            for(var gem:List.of(net.nerdsmp.paper.forge.GemType.JADE,net.nerdsmp.paper.forge.GemType.EMERALD))
                check(core.items().socket(axe,gem,2)==net.nerdsmp.paper.item.PaperItemService.UpgradeResult.APPLIED,"bonus fixture sockets "+gem);
            for(var enchantment:List.of(net.nerdsmp.paper.forge.ArcaneEnchantment.SCHOLAR,net.nerdsmp.paper.forge.ArcaneEnchantment.PRESERVATION,net.nerdsmp.paper.forge.ArcaneEnchantment.REAPING,net.nerdsmp.paper.forge.ArcaneEnchantment.EXECUTIONER))
                check(core.items().enchant(axe,enchantment,2)==net.nerdsmp.paper.item.PaperItemService.UpgradeResult.APPLIED,"bonus fixture enchants "+enchantment);
            fixture.storage[0]=axe;
            for(int index=0;index<4;index++) fixture.armor[index]=core.items().create(new net.nerdsmp.api.model.ItemId("stormbound_"+List.of("helm","cuirass","greaves","boots").get(index)));
            var base=core.gearSets().profile(fixture.player);
            double quality=core.items().quality(axe).multiplier();
            check(Math.abs(core.items().gear(axe).orElseThrow().stat("gathering-power")-(2*quality+3+net.nerdsmp.paper.forge.GemType.JADE.stats(2).get("gathering-power")))<1E-9,
                "base item quality, Bountiful affix and Jade gem contribute exactly once to Gathering Power");
            check(Math.abs(core.items().gear(axe).orElseThrow().stat("skill-xp-percent")-(4+12+net.nerdsmp.paper.forge.GemType.EMERALD.stats(2).get("skill-xp-percent")))<1E-9,
                "Scholarly affix, Scholar enchantment and Emerald gem combine exactly once");
            check(base.setPieces().get("stormbound")==4&&base.activeBonuses().size()==2,"four matching worn pieces activate both set thresholds alongside another held tool");
            var skills=new EnumMap<net.nerdsmp.api.model.Skill,net.nerdsmp.api.service.ProgressionService.SkillProgress>(net.nerdsmp.api.model.Skill.class);
            for(var skill:net.nerdsmp.api.model.Skill.values()) skills.put(skill,new net.nerdsmp.api.service.ProgressionService.SkillProgress(90_000,30));
            Set<String> talents=Set.of("forestry.woodcraft.1","forestry.timber_trade.1","forestry.clean_cuts.1","forestry.careful_tools.1",
                "smithing.tempering.1","smithing.lasting_work.1","mining.deep_reserves.1","alchemy.concentrates.1","fishing.tidal_instinct.1","excavation.relic_sense.1","hunting.second_wind.1","hunting.slayer.1");
            var training=new net.nerdsmp.api.service.ProgressionService.ProgressionSnapshot(skills,Set.of(),talents,Instant.EPOCH);snapshots.put(fixture.id,training);
            var trained=core.gearSets().profile(fixture.player);
            check(Math.abs(trained.stat("durability-save-percent")-base.stat("durability-save-percent")-9)<1E-9,
                "Tempering, Careful Tools and Lasting Work each add one preservation passive, not zero or twice");
            check(trained.stat("geode-find-percent")==base.stat("geode-find-percent")+15
                &&trained.stat("fishing-luck-percent")==base.stat("fishing-luck-percent")+4
                &&trained.stat("poison-potency-percent")==base.stat("poison-potency-percent")+4
                &&trained.stat("excavation-find-percent")==base.stat("excavation-find-percent")+4,
                "all four global gathering and poison passives reach actual equipped profiles");
            var bonuses=net.nerdsmp.paper.listener.ActivityBonuses.resolve(net.nerdsmp.api.model.Skill.FORESTRY,trained.stats(),training,0,0);
            check(Math.abs(bonuses.experiencePercent()-(trained.stat("skill-xp-percent")+trained.stat("gathering-power")*5+5+training.adventurerStat("skill-xp-percent")))<1E-9,
                "activity XP includes equipment, affixes, gems, set stats, power, matching skill talent and Adventurer bonus once");
            check(Math.abs(core.skills().forestryContext(fixture.player).experienceBonus()-bonuses.experiencePercent())<1E-9,
                "batched tree work captures the same complete XP composition as ordinary activity");
            core.skills().awardActivity(fixture.player,net.nerdsmp.api.model.Skill.FORESTRY,100,"bonus-smoke:"+UUID.randomUUID());
            long awarded=core.progression().snapshot(account).toCompletableFuture().join().skills().get(net.nerdsmp.api.model.Skill.FORESTRY).experience();
            check(awarded==(long)Math.floor(100*(1+bonuses.experiencePercent()/100)+1E-9),"actual activity award persists the full combined XP multiplier in the progression service");
            var itemGear=core.items().gear(axe).orElseThrow();target.setHealth(target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()*0.2);
            var damage=new org.bukkit.event.entity.EntityDamageByEntityEvent(fixture.player,target,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),1);
            core.gearSets().onDamage(damage);
            double expected=(itemGear.stat("damage")+trained.stat("damage-flat"))*(1+(trained.stat("damage-percent")+training.adventurerStat("damage-percent"))/100)
                *(1+training.talentStat(net.nerdsmp.api.model.Skill.HUNTING,"hunting-damage-percent")/100)*(1+trained.stat("execute-damage-percent")/100);
            check(Math.abs(damage.getDamage()-expected)<1E-8,"real Paper damage event includes item quality, flat affix, set bonus, Hunting and Executioner multipliers (expected "+expected+", actual "+damage.getDamage()+")");

            core.items().installEngineCatalog(List.of(new net.nerdsmp.paper.engine.ItemBlueprint("bonus_audit_helm","Audit Helm",Material.IRON_HELMET,"RARE",0,1,null,List.of(),Map.of("execute-damage-percent",25D))),List.of());
            fixture.armor[0]=core.items().create(new net.nerdsmp.api.model.ItemId("bonus_audit_helm"));fixture.storage[0]=new ItemStack(Material.DIAMOND_SWORD);
            var vanillaProfile=core.gearSets().profile(fixture.player);
            var vanillaHit=new org.bukkit.event.entity.EntityDamageByEntityEvent(fixture.player,target,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),10);
            core.gearSets().onDamage(vanillaHit);
            double vanillaExpected=(10+vanillaProfile.stat("damage-flat"))*(1+(vanillaProfile.stat("damage-percent")+training.adventurerStat("damage-percent"))/100)
                *(1+training.talentStat(net.nerdsmp.api.model.Skill.HUNTING,"hunting-damage-percent")/100)*1.25;
            check(Math.abs(vanillaHit.getDamage()-vanillaExpected)<1E-8,"ordinary weapon fallback also consumes equipped Executioner and multiplicative Hunting bonuses");
            getLogger().info("ZONE_SMOKE_BONUS_PIPELINE_COMPLETE PASS");
        } finally {target.remove();catalogField.set(core.items(),originalCatalog);snapshots.remove(fixture.id);core.sessions().quit(fixture.id);}
    }

    private void startForestryCheck(World world,DigStep complete) throws Exception {
        HoeFixture fixture=new HoeFixture(world);fixture.storage[0]=new ItemStack(Material.IRON_AXE);fixture.storage[2]=new ItemStack(Material.OAK_SAPLING,10);
        var account=net.nerdsmp.api.model.AccountId.random();core.sessions().join(fixture.id,account);fixture.at=new Location(world,5.5,121,13);
        List<org.bukkit.block.BlockState> saved=new ArrayList<>();
        for(int x=4;x<=8;x++) for(int z=9;z<=13;z++) for(int y=120;y<=130;y++) saved.add(world.getBlockAt(x,y,z).getState());
        var constructor=net.nerdsmp.paper.gathering.ForestryService.class.getDeclaredConstructor(NerdSmpPlugin.class,java.util.function.Predicate.class,java.util.function.BiFunction.class);constructor.setAccessible(true);
        var feller=(net.nerdsmp.paper.gathering.ForestryService)constructor.newInstance(core,(java.util.function.Predicate<Player>)player->player==fixture.player,
            (java.util.function.BiFunction<Player,org.bukkit.block.Block,Boolean>)(player,block)->{
                var event=new org.bukkit.event.block.BlockBreakEvent(block,player);Bukkit.getPluginManager().callEvent(event);
                if(event.isCancelled()||!event.isDropItems()) return false;block.setType(Material.AIR,false);fixture.wear++;return true;
            });
        Method pulse=feller.getClass().getDeclaredMethod("pulse");pulse.setAccessible(true);
        org.bukkit.block.Block root=world.getBlockAt(5,121,10);
        plantForestryFixture(root,6,1,Material.OAK_LOG,Material.OAK_LEAVES);
        startForestryRoot(feller,fixture,root);
        laterDig(()->{
            pulse.invoke(feller);
            check(root.getType()==Material.OAK_SAPLING&&fixture.storage[2].getAmount()==9,"controller fells the proven tree and replants one plain inventory sapling");
            check(fixture.wear==6&&core.progression().snapshot(account).toCompletableFuture().join().skills().get(net.nerdsmp.api.model.Skill.FORESTRY).experience()==108,
                "six committed controlled breaks receive exactly 108 base Forestry XP and one wear call per accepted log");
            check(core.economy().balance(account).toCompletableFuture().join()==6,"batched tree jobs preserve all six log work units rather than paying once or duplicating them");
            plantForestryFixture(root,6,1,Material.OAK_LOG,Material.OAK_LEAVES);cancelledTreeBlock=root;
            startForestryRoot(feller,fixture,root);
            laterDig(()->{
                pulse.invoke(feller);
                check(root.getType()==Material.OAK_LOG&&core.resources().naturalLog(root)&&fixture.storage[2].getAmount()==9
                    &&core.progression().snapshot(account).toCompletableFuture().join().skills().get(net.nerdsmp.api.model.Skill.FORESTRY).experience()==108,
                    "late-canceled root never consumes seeds or rewards and restores its original natural eligibility");
                cancelledTreeBlock=root.getRelative(org.bukkit.block.BlockFace.UP,2);startForestryRoot(feller,fixture,root);
                laterDig(()->{
                    pulse.invoke(feller);
                    check(root.getType().isAir()&&cancelledTreeBlock.getType()==Material.OAK_LOG&&core.resources().naturalLog(cancelledTreeBlock)
                        &&fixture.storage[2].getAmount()==9,"protection cancellation stops the tree at its blocked log without planting over remaining timber");
                    check(core.progression().snapshot(account).toCompletableFuture().join().skills().get(net.nerdsmp.api.model.Skill.FORESTRY).experience()==144,
                        "partial tree awards only the two committed logs");
                    cancelledTreeBlock=null;plantForestryFixture(root,6,1,Material.OAK_LOG,Material.OAK_LEAVES);
                    startForestryRoot(feller,fixture,root);fixture.storage[0]=new ItemStack(Material.IRON_PICKAXE);
                    laterDig(()->{
                        pulse.invoke(feller);
                        check(root.getRelative(org.bukkit.block.BlockFace.UP).getType()==Material.OAK_LOG
                            &&core.progression().snapshot(account).toCompletableFuture().join().skills().get(net.nerdsmp.api.model.Skill.FORESTRY).experience()==162,
                            "switching tools cancels remaining automatic work and retains only the earned root reward");
                        fixture.storage[0]=new ItemStack(Material.IRON_AXE);fixture.storage[2]=new ItemStack(Material.SPRUCE_SAPLING,8);
                        plantForestryFixture(root,4,2,Material.SPRUCE_LOG,Material.SPRUCE_LEAVES);startForestryRoot(feller,fixture,root);
                        laterDig(()->{
                            pulse.invoke(feller);
                            check(root.getType().isAir()&&fixture.storage[2].getAmount()==8,"per-pulse work budget defers a large tree and spends no saplings before completion");
                            laterDig(()->{
                                pulse.invoke(feller);
                                check(fixture.storage[2].getAmount()==4&&root.getType()==Material.SPRUCE_SAPLING
                                    &&root.getRelative(org.bukkit.block.BlockFace.EAST).getType()==Material.SPRUCE_SAPLING
                                    &&root.getRelative(org.bukkit.block.BlockFace.SOUTH).getType()==Material.SPRUCE_SAPLING
                                    &&root.getRelative(org.bukkit.block.BlockFace.SOUTH_EAST).getType()==Material.SPRUCE_SAPLING,
                                    "completed two-by-two tree consumes exactly four saplings and replants its four original roots");
                                check(core.progression().snapshot(account).toCompletableFuture().join().skills().get(net.nerdsmp.api.model.Skill.FORESTRY).experience()>=450,
                                    "large-tree progress includes all sixteen committed logs and the optional species rhythm bonus");
                                feller.close();core.sessions().quit(fixture.id);cancelledTreeBlock=null;saved.forEach(state->state.update(true,false));
                                getLogger().info("ZONE_SMOKE_FORESTRY_COMPLETE PASS");complete.run();
                            });
                        });
                    });
                });
            });
        });
    }
    private void plantForestryFixture(org.bukkit.block.Block root,int height,int width,Material log,Material leaves) {
        for(int x=-1;x<=3;x++) for(int z=-1;z<=3;z++) for(int y=0;y<=9;y++) root.getRelative(x,y,z).setType(Material.AIR,false);
        for(int x=0;x<width;x++) for(int z=0;z<width;z++) {
            root.getRelative(x,-1,z).setType(Material.DIRT,false);
            for(int y=0;y<height;y++) {var block=root.getRelative(x,y,z);block.setType(log,false);core.resources().renewManaged(block,log);}
            root.getRelative(x,height,z).setType(leaves,false);
        }
    }
    private void startForestryRoot(net.nerdsmp.paper.gathering.ForestryService feller,HoeFixture fixture,org.bukkit.block.Block root) {
        var event=new org.bukkit.event.block.BlockBreakEvent(root,fixture.player);feller.onRoot(event);Bukkit.getPluginManager().callEvent(event);
        if(!event.isCancelled()&&event.isDropItems()) {root.setType(Material.AIR,false);fixture.wear++;}
    }
    private void checkStaffPanel() throws Exception {
        check(core.staffPermissions()!=null && core.getCommand("permissions")!=null,"internal staff groups and command are registered");
        var panelFile=new java.io.File(core.getDataFolder(),"security/watchdog-panel.yml");
        check(panelFile.isFile(),"fresh jar upload creates security/watchdog-panel.yml automatically");
        var initialPanel=new org.bukkit.configuration.file.YamlConfiguration();initialPanel.load(panelFile);
        check(!initialPanel.getBoolean("enabled",true)&&initialPanel.getString("hash").isEmpty()&&initialPanel.getString("salt").isEmpty(),"generated panel settings are disabled without a shared default password");
        byte[] initialBytes=Files.readAllBytes(panelFile.toPath());
        check(net.nerdsmp.paper.anticheat.WatchdogPanel.start(core)==null&&Arrays.equals(initialBytes,Files.readAllBytes(panelFile.toPath())),"disabled settings do not open a listener or get overwritten");
        var unchanged=core.staffPermissions().registry().snapshot();
        var registry=new net.nerdsmp.paper.permission.PermissionRegistry(new java.io.File(getDataFolder(),"staff-permissions-fixture.yml"));
        UUID viewer=UUID.randomUUID(); registry.membership("fixture",viewer,"moderator",true);
        Set<String> known=new HashSet<>(); Map<String,Map<String,Boolean>> children=new HashMap<>();
        Bukkit.getPluginManager().getPermissions().forEach(permission->{known.add(permission.getName());children.put(permission.getName(),Map.copyOf(permission.getChildren()));});
        org.bukkit.permissions.ServerOperator ordinary=new org.bukkit.permissions.ServerOperator(){public boolean isOp(){return false;}public void setOp(boolean ignored){throw new AssertionError("Permission engine must never grant operator");}};
        var permissible=new org.bukkit.permissions.PermissibleBase(ordinary);var attachment=permissible.addAttachment(core);
        try {
            var rules=net.nerdsmp.paper.permission.PermissionRegistry.resolve(registry.snapshot(),viewer,known,children);
            rules.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).forEach(rule->attachment.setPermission(rule.getKey(),rule.getValue()));
            check(permissible.hasPermission("nerdsmp.staff")&&permissible.hasPermission("nerdsmp.staff.moderate")&&!permissible.hasPermission("nerdsmp.permissions.manage"),"real Bukkit attachments give moderator tools without permission ownership");
            registry.permission("fixture",null,viewer,"nerdsmp.staff.moderate",false);
            attachment.setPermission("nerdsmp.staff.moderate",false);
            check(!permissible.hasPermission("nerdsmp.staff.moderate"),"explicit Bukkit deny removes moderation capability without op changes");
        } finally {permissible.removeAttachment(attachment);permissible.clearPermissions();}
        byte[] salt=new byte[32];new java.security.SecureRandom().nextBytes(salt);
        String password="fixture-only-panel";
        var yaml=new org.bukkit.configuration.file.YamlConfiguration();yaml.set("schema",1);yaml.set("enabled",true);yaml.set("port",9029);yaml.set("username","fixture");
        yaml.set("salt",Base64.getEncoder().encodeToString(salt));yaml.set("hash",Base64.getEncoder().encodeToString(net.nerdsmp.paper.anticheat.PanelCredentials.derive(password.toCharArray(),salt,600000)));yaml.set("iterations",600000);
        net.nerdsmp.paper.storage.AtomicYamlFiles.save(yaml,new java.io.File(core.getDataFolder(),"security/watchdog-panel.yml"));
        try(var panel=net.nerdsmp.paper.anticheat.WatchdogPanel.start(core)) {
            check(panel!=null&&panel.port()==9029&&panel.bindAddress().isLoopbackAddress(),"legacy panel configuration still opens loopback port 9029");
            var client=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
            var uri=java.net.URI.create("http://127.0.0.1:9029/api/status");
            check(client.send(java.net.http.HttpRequest.newBuilder(uri).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()==401,"live Watchdog data is not available without authentication");
            var response=client.send(java.net.http.HttpRequest.newBuilder(uri).header("Authorization","Basic "+Base64.getEncoder().encodeToString(("fixture:"+password).getBytes(java.nio.charset.StandardCharsets.UTF_8))).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            check(response.statusCode()==200&&response.body().contains("\"watchdog\"")&&response.body().contains("\"staff\"")&&!response.body().contains(password),"authenticated panel returns captured game-thread evidence and staff without credentials");
        }
        yaml.set("bind","0.0.0.0");
        net.nerdsmp.paper.storage.AtomicYamlFiles.save(yaml,new java.io.File(core.getDataFolder(),"security/watchdog-panel.yml"));
        byte[] configuredBytes=Files.readAllBytes(panelFile.toPath());
        try(var panel=net.nerdsmp.paper.anticheat.WatchdogPanel.start(core)) {
            check(panel!=null&&panel.port()==9029&&panel.bindAddress().isAnyLocalAddress(),"production configuration can bind all IPv4 interfaces on port 9029");
            var client=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
            var uri=java.net.URI.create("http://127.0.0.1:9029/api/status");
            check(client.send(java.net.http.HttpRequest.newBuilder(uri).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()==401,"wildcard listener still requires authentication");
            var response=client.send(java.net.http.HttpRequest.newBuilder(uri).header("Authorization","Basic "+Base64.getEncoder().encodeToString(("fixture:"+password).getBytes(java.nio.charset.StandardCharsets.UTF_8))).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            check(response.statusCode()==200&&response.body().contains("\"watchdog\""),"authenticated wildcard listener returns live evidence");
            check(Arrays.equals(configuredBytes,Files.readAllBytes(panelFile.toPath())),"configured panel credentials and network settings are never overwritten on startup");
        }
        check(core.staffPermissions().registry().snapshot().equals(unchanged),"permission fixture never assigns real accounts or changes live staff records");
    }
    private void checkEarnedGear() {
        String[] families = {"wayfarer", "deepcurrent", "stormbound", "dawnguard", "astral_covenant"};
        String[] rarities = {"UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
        for (int tier = 0; tier < families.length; tier++) for (String piece : new String[]{"helm", "cuirass", "greaves", "boots", "blade", "rod"}) {
            var id = new net.nerdsmp.api.model.ItemId(families[tier] + "_" + piece);
            ItemStack item = core.items().create(id);
            check(core.items().isAuthentic(item) && core.items().gear(item).orElseThrow().rarity().equals(rarities[tier]), "earned gear has authentic rarity " + id);
            check(core.items().isQualityUpgradable(item), "earned collection equipment accepts workmanship " + id);
            check(core.items().improveQuality(item) == net.nerdsmp.paper.item.PaperItemService.QualityUpgradeResult.APPLIED,
                "workmanship improves collection equipment " + id);
            check(core.items().quality(item) == net.nerdsmp.paper.item.GearQuality.TEMPERED
                && core.items().isAuthentic(net.nerdsmp.paper.market.MarketItems.decode(net.nerdsmp.paper.market.MarketItems.encode(item))),
                "upgraded earned gear survives real Paper serialization " + id);
        }
    }
    @SuppressWarnings("unchecked")
    private void checkBulkIdentification() throws Exception {
        var service=core.identification();
        Method quote=service.getClass().getDeclaredMethod("quote",String.class,ItemStack[].class,int.class);quote.setAccessible(true);
        ItemStack blade=core.items().createLoot(Material.DIAMOND_SWORD,1,true,7341);
        ItemStack mythic=core.items().createLoot(new net.nerdsmp.api.model.ItemId("astral_covenant_blade"),3,true,7342);
        check(core.items().sealedGear(blade).orElseThrow().affixes()==1
            &&core.items().sealedGear(mythic).orElseThrow().rarity().equals("MYTHIC"),"sealed processing validates crafted and mythic gear without identifying it");
        for(String family:List.of("wayfarer","deepcurrent","stormbound","dawnguard","astral_covenant")) {
            var sealed=core.items().createLoot(new net.nerdsmp.api.model.ItemId(family+"_helm"),3,true,family.hashCode());
            check(core.items().sealedGear(sealed).isPresent()&&core.items().gear(sealed).isEmpty(),"every earned rarity can be processed while sealed stats remain inactive: "+family);
        }
        ItemStack invalid=blade.clone();invalid.setAmount(2);
        check(core.items().sealedGear(invalid).isEmpty(),"stacked unique equipment is never eligible for bulk conversion");
        invalid=blade.clone();var meta=invalid.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(core,"loot_affixes"),PersistentDataType.STRING,"sharp:NaN");invalid.setItemMeta(meta);
        check(core.items().sealedGear(invalid).isEmpty(),"malformed affixes do not turn into scrap rewards");
        invalid=blade.clone();meta=invalid.getItemMeta();meta.getPersistentDataContainer().remove(new NamespacedKey(core,"loot_instance"));invalid.setItemMeta(meta);
        check(core.items().sealedGear(invalid).isEmpty(),"missing unique loot identity is not silently accepted");
        var ordinary=new ItemStack(Material.DIAMOND_SWORD);meta=ordinary.getItemMeta();meta.itemName(net.kyori.adventure.text.Component.text("Unidentified Mythic Sword"));ordinary.setItemMeta(meta);
        check(core.items().sealedGear(ordinary).isEmpty(),"renamed vanilla equipment cannot imitate a sealed item");

        ItemStack[] storage=new ItemStack[36];storage[0]=blade;storage[9]=mythic;
        storage[5]=core.items().createCrafted(Material.IRON_AXE);storage[8]=new ItemStack(Material.DIRT,37);
        storage[12]=core.forge().createGem(net.nerdsmp.paper.forge.GemType.RUBY,2);storage[18]=core.enchanting().createArcaneDust(60);
        String bladeBefore=net.nerdsmp.paper.market.MarketItems.encode(blade),mythicBefore=net.nerdsmp.paper.market.MarketItems.encode(mythic);
        Object all=quote.invoke(service,"all",storage,0);
        long basePrice=core.getConfig().getLong("identification.base-cost-marks",75);
        long affixPrice=core.getConfig().getLong("identification.per-affix-cost-marks",25);
        check((int)lootBatchPart(all,"count")==2&&(long)lootBatchPart(all,"marks")==2*basePrice+4*affixPrice,"bulk identification quotes exactly two eligible items at configured per-item prices");
        List<ItemStack> identified=(List<ItemStack>)lootBatchPart(all,"after");
        check(core.items().isAuthentic(identified.get(0))&&!core.items().isUnidentified(identified.get(0))
            &&core.items().lootInstance(identified.get(0)).equals(core.items().lootInstance(blade))
            &&core.items().affixes(identified.get(0)).equals(core.items().affixes(blade))
            &&core.items().affixes(identified.get(9)).equals(core.items().affixes(mythic)),"identification reveals copies without rerolling identities or affixes");
        check(bladeBefore.equals(net.nerdsmp.paper.market.MarketItems.encode(blade))&&mythicBefore.equals(net.nerdsmp.paper.market.MarketItems.encode(mythic)),
            "opening identification previews never mutates or consumes source gear");
        Object held=quote.invoke(service,"held",storage,0);
        check((int)lootBatchPart(held,"count")==1&&(long)lootBatchPart(held,"marks")==basePrice+affixPrice,"held identification remains a one-item operation at the configured price");

        Object scrap=quote.invoke(service,"scrap",storage,0);List<ItemStack> scrapped=(List<ItemStack>)lootBatchPart(scrap,"after");
        check((int)lootBatchPart(scrap,"count")==2&&(int)lootBatchPart(scrap,"dust")==26&&(long)lootBatchPart(scrap,"marks")==0,
            "scrapping awards rarity plus affix dust and spends no Marks");
        int totalDust=scrapped.stream().filter(core.enchanting()::isArcaneDust).mapToInt(ItemStack::getAmount).sum();
        check(totalDust==86&&scrapped.get(18).getAmount()==64&&scrapped.stream().filter(core.items()::isUnidentified).findAny().isEmpty(),
            "dust is merged into existing stacks and freed slots without overflow or leftover sealed inputs");
        check(storage[5].equals(scrapped.get(5))&&storage[8].equals(scrapped.get(8))&&storage[12].equals(scrapped.get(12))
            &&bladeBefore.equals(net.nerdsmp.paper.market.MarketItems.encode(blade)),"scrap planning leaves identified gear, ordinary materials, gems and all live inputs untouched");

        Inventory inventory=Bukkit.createInventory(null,36);inventory.setContents(storage);
        Object plan=lootBatchPart(scrap,"plan");Method apply=plan.getClass().getDeclaredMethod("apply",java.util.function.Supplier.class,java.util.function.Consumer.class);apply.setAccessible(true);
        var source=(java.util.function.Supplier<List<ItemStack>>)()->Arrays.asList(inventory.getContents());
        var replace=(java.util.function.Consumer<List<ItemStack>>)next->inventory.setContents(next.toArray(ItemStack[]::new));
        check(apply.invoke(plan,source,replace).toString().equals("APPLIED")
            &&Arrays.stream(inventory.getContents()).filter(core.enchanting()::isArcaneDust).mapToInt(ItemStack::getAmount).sum()==86,
            "real Paper inventory receives removal and exact Arcane Dust reward in one replacement");
        check(apply.invoke(plan,source,replace).toString().equals("ALREADY_ATTEMPTED"),"completed scrap plan cannot be replayed");
        Object stale=quote.invoke(service,"all",storage,0);plan=lootBatchPart(stale,"plan");
        inventory.setContents(storage);inventory.setItem(8,new ItemStack(Material.DIRT,36));
        check(apply.invoke(plan,source,replace).toString().equals("INPUT_CHANGED")&&core.items().isUnidentified(inventory.getItem(0)),
            "any post-quote inventory change refuses the whole batch before item mutation");
        ItemStack[] duplicates=new ItemStack[36];duplicates[0]=blade;duplicates[9]=blade.clone();
        boolean refused=false;try { quote.invoke(service,"scrap",duplicates,0); } catch(java.lang.reflect.InvocationTargetException failure) {refused=failure.getCause() instanceof IllegalStateException;}
        check(refused,"duplicate loot-instance identities cannot both be converted to dust");
        Object empty=quote.invoke(service,"all",new ItemStack[36],0);
        check((int)lootBatchPart(empty,"count")==0&&lootBatchPart(empty,"plan")==null,"empty bulk batch cannot become a paid operation");
        Method insert=service.getClass().getDeclaredMethod("insertDust",List.class,int.class);insert.setAccessible(true);
        List<ItemStack> full=new ArrayList<>();for(int i=0;i<36;i++) full.add(new ItemStack(Material.STONE,64));
        full.set(0,null);
        check(!(boolean)insert.invoke(service,full,65),"reward-space preflight detects a configured yield too large for the freed slots");
        var command=core.getCommand("identify");
        check(command.getExecutor()!=null&&command.getTabCompleter()!=null&&command.getUsage().contains("held|all|scrap")
            &&"nerdsmp.station.remote".equals(command.getPermission()),"bulk command shortcuts keep existing remote-station permissions and expose accurate help");
        getLogger().info("ZONE_SMOKE_BULK_IDENTIFICATION_COMPLETE PASS");
    }
    private static Object lootBatchPart(Object quote,String name) throws Exception {
        Method accessor=quote.getClass().getDeclaredMethod(name);accessor.setAccessible(true);return accessor.invoke(quote);
    }
    private void checkProgressionExpansion(World world) throws Exception {
        for(String family:List.of("wayfarer","deepcurrent","stormbound","dawnguard","astral_covenant")) {
            var equipped=new EnumMap<org.bukkit.inventory.EquipmentSlot,ItemStack>(org.bukkit.inventory.EquipmentSlot.class);
            var slots=List.of(org.bukkit.inventory.EquipmentSlot.HEAD,org.bukkit.inventory.EquipmentSlot.CHEST,org.bukkit.inventory.EquipmentSlot.LEGS,org.bukkit.inventory.EquipmentSlot.FEET);
            var pieces=List.of("helm","cuirass","greaves","boots");
            for(int i=0;i<4;i++) equipped.put(slots.get(i),core.items().create(new net.nerdsmp.api.model.ItemId(family+"_"+pieces.get(i))));
            var armor=core.gearSets().profile(equipped);
            check(armor.setPieces().get(family)==4&&armor.activeBonuses().size()==2,"four worn pieces activate both armor thresholds: "+family);
            equipped.put(org.bukkit.inventory.EquipmentSlot.HAND,core.items().create(new net.nerdsmp.api.model.ItemId(family+"_blade")));
            var armed=core.gearSets().profile(equipped);
            check(armed.setPieces().get(family)==5&&armed.activeBonuses().size()==3&&armed.stat("skill-xp-percent")>0,"held set weapon activates the final XP bonus: "+family);
            equipped.put(org.bukkit.inventory.EquipmentSlot.OFF_HAND,equipped.remove(org.bukkit.inventory.EquipmentSlot.HAND));
            check(core.gearSets().profile(equipped).setPieces().get(family)==4,"main-hand gear cannot supply a bonus from the offhand: "+family);
        }
        var helm=core.items().create(new net.nerdsmp.api.model.ItemId("wayfarer_helm"));
        check(core.gearSets().profile(Map.of(org.bukkit.inventory.EquipmentSlot.HEAD,helm,org.bukkit.inventory.EquipmentSlot.HAND,helm.clone())).setPieces().get("wayfarer")==1,
            "duplicate pieces in the wrong slot never count twice");
        var gem=core.forge().createGem(net.nerdsmp.paper.forge.GemType.RUBY,1);
        check(net.nerdsmp.paper.item.CraftingGuard.protectedIngredient(gem)&&net.nerdsmp.paper.item.CraftingGuard.protectedIngredient(helm),"gems and equipment are protected crafting ingredients");
        check(!net.nerdsmp.paper.item.CraftingGuard.protectedIngredient(new ItemStack(Material.DIAMOND)),"ordinary vanilla crafting materials remain usable");
        var fixture=new HoeFixture(world);var account=net.nerdsmp.api.model.AccountId.random();core.sessions().join(fixture.id,account);
        var snapshotsField=core.presentation().getClass().getDeclaredField("progressionSnapshots");snapshotsField.setAccessible(true);
        Map<UUID,net.nerdsmp.api.service.ProgressionService.ProgressionSnapshot> snapshots=(Map<UUID,net.nerdsmp.api.service.ProgressionService.ProgressionSnapshot>)snapshotsField.get(core.presentation());
        try {
            check(core.recipes().mealCount()==38,"runtime registers thirty-eight Cooking meals");
            var kitchenField=core.recipes().getClass().getDeclaredField("kitchen");kitchenField.setAccessible(true);Object kitchen=kitchenField.get(core.recipes());
            var dishesField=kitchen.getClass().getDeclaredField("dishes");dishesField.setAccessible(true);Map<?,?> dishes=(Map<?,?>)dishesField.get(kitchen);
            Object dish=dishes.values().iterator().next();Method create=kitchen.getClass().getDeclaredMethod("create",dish.getClass(),Player.class);create.setAccessible(true);
            snapshots.put(fixture.id,core.progression().snapshot(account).toCompletableFuture().join());
            ItemStack novice=(ItemStack)create.invoke(kitchen,dish,fixture.player);
            core.progression().awardExperience(account,net.nerdsmp.api.model.Skill.COOKING,250_000,"cooking-quality-fixture").toCompletableFuture().join();
            snapshots.put(fixture.id,core.progression().snapshot(account).toCompletableFuture().join());
            ItemStack expert=(ItemStack)create.invoke(kitchen,dish,fixture.player);NamespacedKey seconds=new NamespacedKey(core,"meal_seconds");
            check(expert.getItemMeta().getPersistentDataContainer().get(seconds,PersistentDataType.INTEGER)>novice.getItemMeta().getPersistentDataContainer().get(seconds,PersistentDataType.INTEGER),"Cooking level improves the actual tradeable meal duration");
            ItemStack restored=net.nerdsmp.paper.market.MarketItems.decode(net.nerdsmp.paper.market.MarketItems.encode(expert));
            check(restored.getItemMeta().getPersistentDataContainer().get(seconds,PersistentDataType.INTEGER).equals(expert.getItemMeta().getPersistentDataContainer().get(seconds,PersistentDataType.INTEGER)),"food quality survives market serialization");
            check(net.nerdsmp.paper.item.CraftingGuard.protectedIngredient(expert),"prepared meals cannot impersonate raw ingredients");
            fixture.storage[0]=new ItemStack(Material.DIAMOND_SWORD);
            for(int i=0;i<4;i++) fixture.armor[i]=core.items().create(new net.nerdsmp.api.model.ItemId("stormbound_"+List.of("helm","cuirass","greaves","boots").get(i)));
            var target=world.spawn(new Location(world,12.5,121,12.5),Cow.class);
            try {
                var hit=new org.bukkit.event.entity.EntityDamageByEntityEvent(fixture.player,target,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),10);
                core.gearSets().onDamage(hit);
                check(Math.abs(hit.getDamage()-11)<.0001,"Stormbound's damage bonus actually changes an ordinary weapon's Paper damage event");
            } finally { target.remove();Arrays.fill(fixture.armor,null);fixture.storage[0]=null; }
            ItemStack[] matrix={new ItemStack(Material.WHEAT,8),null,null,null,null,null,null,null,null};ItemStack[] output={new ItemStack(Material.BREAD)};
            var recipe=new org.bukkit.inventory.ShapedRecipe(NamespacedKey.minecraft("expansion_fixture_bread"),output[0]);recipe.shape("W");recipe.setIngredient('W',Material.WHEAT);
            var inventory=(org.bukkit.inventory.CraftingInventory)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{org.bukkit.inventory.CraftingInventory.class},(proxy,method,args)->switch(method.getName()) {
                case "getMatrix" -> matrix.clone();case "getResult" -> output[0];case "setResult" -> {output[0]=(ItemStack)args[0];yield null;}case "getRecipe" -> recipe;
                case "getHolder" -> null;case "getSize" -> 10;case "getType" -> org.bukkit.event.inventory.InventoryType.WORKBENCH;
                default -> throw new UnsupportedOperationException("Craft matrix fixture: "+method.getName());
            });
            var receipt=new net.nerdsmp.paper.item.CraftingReceipt(inventory,fixture.player,output[0],true);
            check(receipt.crafts(inventory,fixture.player)==0,"a result preview grants no crafting work");
            matrix[0].setAmount(3);fixture.storage[1]=new ItemStack(Material.BREAD,5);
            check(receipt.crafts(inventory,fixture.player)==5,"shift-craft rewards reflect both consumed ingredients and received outputs");
            var view=editorView(fixture.player,inventory);matrix[0]=gem;
            var prepare=new org.bukkit.event.inventory.PrepareItemCraftEvent(inventory,view,false);new net.nerdsmp.paper.item.CraftingGuard().prepare(prepare);
            check(output[0]==null,"protected ingredients remove the crafting result, not just its tooltip");
            var craft=new org.bukkit.event.inventory.CraftItemEvent(recipe,view,org.bukkit.event.inventory.InventoryType.SlotType.RESULT,0,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
            new net.nerdsmp.paper.item.CraftingGuard().craft(craft);check(craft.isCancelled(),"protected ingredients are refused again at craft commit");
            check(core.getCommand("pay")!=null&&core.getCommand("leaderboard")!=null&&core.getCommand("cooking")!=null&&core.payments()!=null&&core.leaderboards()!=null,"player payments, leaderboards and Cooking are wired to live plugin services");
        } finally { snapshots.remove(fixture.id);core.sessions().quit(fixture.id); }
    }
    private void checkAuctionAndPlayerStores(World world) throws Exception {
        check(core.auctions()!=null && core.auctionController()!=null && core.marketController()!=null,
            "auction house and player-store services are wired into the enabled Paper plugin");
        var ah=core.getCommand("ah");var storeCommand=core.getCommand("store");
        check(ah!=null && ah.getExecutor()!=null && ah.getTabCompleter()!=null
            && storeCommand!=null && storeCommand.getExecutor()!=null && storeCommand.getTabCompleter()!=null,
            "auction-house and player-store commands have live executors and completion");
        check(ah.getAliases().contains("auctionhouse"),"auction house alias resolves to the registered command");
        var coreMarketBefore=core.market().allEntries();var coreAuctionsBefore=core.auctions().all();
        Path fixture=getDataFolder().toPath().resolve("market-auction-integration");Files.createDirectories(fixture);
        var clock=new AuctionFixtureClock();
        var wallet=new net.nerdsmp.paper.service.InMemoryEconomyService();
        var seller=net.nerdsmp.api.model.AccountId.random();
        var first=net.nerdsmp.api.model.AccountId.random();var second=net.nerdsmp.api.model.AccountId.random();
        for(var account:List.of(seller,first,second)) smokeAwait(wallet.credit(account,10_000,
            new net.nerdsmp.api.service.EconomyService.TransactionReason("smoke.fixture","Isolated disposable market wallet"),
            "smoke-fund:"+account));
        var marketFile=fixture.resolve("market.yml").toFile();var auctionFile=fixture.resolve("auctions.yml").toFile();
        var market=new net.nerdsmp.paper.market.DurableMarketService(new net.nerdsmp.paper.market.MarketStore(marketFile),wallet,clock);
        var auctions=new net.nerdsmp.paper.auction.DurableAuctionService(new net.nerdsmp.paper.auction.AuctionStore(auctionFile),market,wallet,clock,5);
        var stall=smokeAwait(market.registerStall(seller,new net.nerdsmp.api.service.MarketService.StallLocation(world.getUID(),4,121,4)));

        ItemStack gear=core.items().createCrafted(Material.DIAMOND_SWORD);
        check(core.items().improveQuality(gear)==net.nerdsmp.paper.item.PaperItemService.QualityUpgradeResult.APPLIED
            && core.items().socket(gear,net.nerdsmp.paper.forge.GemType.RUBY,2)==net.nerdsmp.paper.item.PaperItemService.UpgradeResult.APPLIED
            && core.items().enchant(gear,net.nerdsmp.paper.forge.ArcaneEnchantment.KEEN_EDGE,3)==net.nerdsmp.paper.item.PaperItemService.UpgradeResult.APPLIED
            && core.items().coat(gear,net.nerdsmp.paper.alchemy.PoisonType.NIGHTSHADE.id(),2,9),
            "auction fixture uses authentic improved, socketed, enchanted and coated Paper equipment");
        var gearMeta=gear.getItemMeta();gearMeta.displayName(net.kyori.adventure.text.Component.text("Stormwake"));
        ((org.bukkit.inventory.meta.Damageable)gearMeta).setDamage(117);
        gearMeta.getPersistentDataContainer().set(new NamespacedKey(this,"market_provenance"),PersistentDataType.BYTE_ARRAY,new byte[]{2,4,6,8});
        gear.setItemMeta(gearMeta);ItemStack original=gear.clone();
        String encoded=net.nerdsmp.paper.market.MarketItems.encode(gear);
        check(net.nerdsmp.paper.market.MarketItems.decode(encoded).equals(original),
            "Paper market serialization roundtrips the exact name, damage and equipment metadata");
        UUID id=smokeAwait(market.prepareAuctionListing(seller,stall.id(),encoded,100,10)).entry().orElseThrow().listing().id();
        check(smokeAwait(auctions.prepare(id,seller,100,250L,clock.instant().plusSeconds(3600))).outcome()
            ==net.nerdsmp.paper.auction.DurableAuctionService.Outcome.PREPARED,
            "auction plan is durably saved while its item deposit is still pending");
        check(smokeAwait(auctions.activate(id,seller)).outcome()==net.nerdsmp.paper.auction.DurableAuctionService.Outcome.PENDING
            && smokeAwait(wallet.balance(seller))==10_000,
            "auction cannot activate or charge its fee before deposit confirmation");
        smokeAwait(market.confirmDeposit(id,seller));smokeAwait(auctions.activate(id,seller));
        check(market.isAuction(id) && market.entry(id).orElseThrow().listing().status()==net.nerdsmp.api.service.MarketService.Status.AUCTION
            && smokeAwait(wallet.balance(seller))==9_990 && smokeAwait(market.activeListings()).isEmpty(),
            "confirmed auction charges one fee and never appears as fixed-price store stock");
        check(smokeAwait(market.reserve(id,first,clock.instant().plusSeconds(30))).outcome()==net.nerdsmp.api.service.MarketService.Outcome.UNAVAILABLE
            && smokeAwait(market.checkout(id,first,5)).outcome()==net.nerdsmp.api.service.MarketService.Outcome.UNAVAILABLE
            && smokeAwait(market.cancel(id,seller)).outcome()==net.nerdsmp.api.service.MarketService.Outcome.UNAVAILABLE,
            "legacy market reserve, buy and cancellation cannot bypass an active auction");
        long firstRevision=auctions.auction(id).orElseThrow().revision();
        check(smokeAwait(auctions.bid(id,first,100,firstRevision)).outcome()==net.nerdsmp.paper.auction.DurableAuctionService.Outcome.BID_ACCEPTED
            && smokeAwait(wallet.balance(first))==9_900,"first bid is funded before becoming the visible high bid");
        check(smokeAwait(auctions.bid(id,second,150,auctions.auction(id).orElseThrow().revision())).outcome()
            ==net.nerdsmp.paper.auction.DurableAuctionService.Outcome.BID_ACCEPTED
            && smokeAwait(wallet.balance(first))==10_000 && smokeAwait(wallet.balance(second))==9_850,
            "outbid player receives the full prior escrow while the new bidder funds their bid");
        check(smokeAwait(auctions.bid(id,second,175,auctions.auction(id).orElseThrow().revision())).outcome()
            ==net.nerdsmp.paper.auction.DurableAuctionService.Outcome.BID_ACCEPTED && smokeAwait(wallet.balance(second))==9_825,
            "raising your own bid debits only its increase and does not refund the winning escrow");
        check(smokeAwait(auctions.bid(id,first,200,firstRevision)).outcome()==net.nerdsmp.paper.auction.DurableAuctionService.Outcome.STALE
            && smokeAwait(wallet.balance(first))==10_000
            && smokeAwait(auctions.cancel(id,seller,auctions.auction(id).orElseThrow().revision())).outcome()
                ==net.nerdsmp.paper.auction.DurableAuctionService.Outcome.UNAVAILABLE,
            "stale offers do not spend Marks and sellers cannot cancel funded auctions");
        check(smokeAwait(auctions.buyNow(id,first,auctions.auction(id).orElseThrow().revision())).outcome()
            ==net.nerdsmp.paper.auction.DurableAuctionService.Outcome.COMPLETE
            && smokeAwait(wallet.balance(first))==9_750 && smokeAwait(wallet.balance(second))==10_000
            && smokeAwait(wallet.balance(seller))==10_228,
            "Buy Now refunds the outbid player, debits once and pays the seller after tax");
        var ready=market.entry(id).orElseThrow();
        check(ready.listing().status()==net.nerdsmp.api.service.MarketService.Status.READY && first.equals(ready.recipient())
            && net.nerdsmp.paper.market.MarketItems.decode(ready.listing().item()).equals(original),
            "auction winner receives an exact durable inbox snapshot with all gear metadata intact");
        check(smokeAwait(market.beginDelivery(id,second)).outcome()==net.nerdsmp.api.service.MarketService.Outcome.DENIED,
            "a different player cannot start the winner's delivery");
        var handoff=smokeAwait(market.beginDelivery(id,first));
        check(handoff.outcome()==net.nerdsmp.api.service.MarketService.Outcome.DELIVERING
            && handoff.entry().orElseThrow().deliveryId()!=null,"auction delivery starts with an account-bound durable hand-off token");
        // There is no real player here. Deliberately interrupt instead of claiming that a saved inventory received the item.
        market=new net.nerdsmp.paper.market.DurableMarketService(new net.nerdsmp.paper.market.MarketStore(marketFile),wallet,clock);
        smokeAwait(market.recover());
        auctions=new net.nerdsmp.paper.auction.DurableAuctionService(new net.nerdsmp.paper.auction.AuctionStore(auctionFile),market,wallet,clock,5);
        smokeAwait(auctions.recover());
        check(market.entry(id).orElseThrow().listing().status()==net.nerdsmp.api.service.MarketService.Status.REVIEW
            && market.entry(id).orElseThrow().reviewKind()==net.nerdsmp.api.service.MarketService.ReviewKind.DELIVERY
            && smokeAwait(market.beginDelivery(id,first)).outcome()!=net.nerdsmp.api.service.MarketService.Outcome.DELIVERING
            && auctions.auction(id).orElseThrow().status()==net.nerdsmp.paper.auction.Auction.Status.COMPLETE
            && smokeAwait(wallet.balance(seller))==10_228 && smokeAwait(wallet.balance(first))==9_750,
            "interrupted auction delivery is quarantined after restart without duplicate payment or an extra item hand-off");

        ItemStack bow=core.items().createCrafted(Material.BOW);String bowSnapshot=net.nerdsmp.paper.market.MarketItems.encode(bow);
        UUID fixed=smokeAwait(market.prepareListing(seller,stall.id(),bowSnapshot,90,0)).entry().orElseThrow().listing().id();
        smokeAwait(market.confirmDeposit(fixed,seller));
        check(!market.isAuction(fixed) && smokeAwait(market.activeListings()).size()==1
            && smokeAwait(market.activeListings()).getFirst().id().equals(fixed),
            "fixed-price player store stock remains separate from auction and review entries");
        check(smokeAwait(market.checkout(fixed,second,5)).outcome()==net.nerdsmp.api.service.MarketService.Outcome.READY
            && smokeAwait(wallet.balance(second))==9_910 && smokeAwait(wallet.balance(seller))==10_314
            && net.nerdsmp.paper.market.MarketItems.decode(market.entry(fixed).orElseThrow().listing().item()).equals(bow),
            "player-store purchase uses the same tax and metadata-preserving durable inbox workflow");

        UUID timed=smokeAuction(market,auctions,seller,stall.id(),encoded,50,null,clock);
        UUID unbid=smokeAuction(market,auctions,seller,stall.id(),encoded,60,null,clock);
        smokeAwait(auctions.bid(timed,first,50,auctions.auction(timed).orElseThrow().revision()));
        clock.advanceSeconds(3601);smokeAwait(auctions.tick());
        check(auctions.auction(timed).orElseThrow().status()==net.nerdsmp.paper.auction.Auction.Status.COMPLETE
            && market.entry(timed).orElseThrow().listing().status()==net.nerdsmp.api.service.MarketService.Status.READY
            && first.equals(market.entry(timed).orElseThrow().recipient()) && smokeAwait(wallet.balance(first))==9_700
            && smokeAwait(wallet.balance(seller))==10_362,
            "timed auction expiry settles its already-funded high bid without charging the winner twice");
        check(auctions.auction(unbid).orElseThrow().status()==net.nerdsmp.paper.auction.Auction.Status.CANCELLED
            && seller.equals(market.entry(unbid).orElseThrow().recipient())
            && net.nerdsmp.paper.market.MarketItems.decode(market.entry(unbid).orElseThrow().listing().item()).equals(original),
            "expired auction without bids returns the exact original item to the seller inbox");
        var reopened=new net.nerdsmp.paper.market.DurableMarketService(new net.nerdsmp.paper.market.MarketStore(marketFile),wallet,clock);
        var reopenedAuctions=new net.nerdsmp.paper.auction.DurableAuctionService(new net.nerdsmp.paper.auction.AuctionStore(auctionFile),reopened,wallet,clock,5);
        smokeAwait(reopened.recover());smokeAwait(reopenedAuctions.recover());
        check(reopened.allEntries().equals(market.allEntries()) && reopenedAuctions.all().equals(auctions.all())
            && smokeAwait(wallet.balance(seller))==10_362 && smokeAwait(wallet.balance(first))==9_700 && smokeAwait(wallet.balance(second))==9_910,
            "auction and fixed-store journals reopen without lost listings, metadata or extra economic operations");

        var playerData=new net.nerdsmp.paper.storage.PlayerDataStore(fixture.resolve("profiles").toFile(),getLogger());
        playerData.bind(seller.value(),seller,new net.nerdsmp.api.model.PlatformIdentity(
            net.nerdsmp.api.model.PlatformIdentity.Platform.JAVA,seller.toString()),"StoreSmoke");
        playerData.update(seller,yaml->yaml.set("smoke.island-progress",147));
        var profiles=new net.nerdsmp.paper.market.PlayerStoreProfiles(playerData,"smoke-season");
        var before=profiles.profile(seller);var named=new net.nerdsmp.paper.market.PlayerStoreProfiles.Profile("Stormwake Supplies","Tools and provisions for your next trip.");
        profiles.update(seller,before,named);
        var reopenedData=new net.nerdsmp.paper.storage.PlayerDataStore(fixture.resolve("profiles").toFile(),getLogger());
        check(new net.nerdsmp.paper.market.PlayerStoreProfiles(reopenedData,"smoke-season").profile(seller).equals(named)
            && reopenedData.read(seller,yaml->yaml.getInt("smoke.island-progress"))==147,
            "player-store branding persists additively without changing unrelated island progress");
        boolean staleRejected=false;try { profiles.update(seller,before,new net.nerdsmp.paper.market.PlayerStoreProfiles.Profile("Old Name","Old details")); }
        catch(IllegalArgumentException expected) { staleRejected=true; }
        check(staleRejected && profiles.profile(seller).equals(named),"stale store-profile edits cannot overwrite current branding");
        check(core.market().allEntries().equals(coreMarketBefore) && core.auctions().all().equals(coreAuctionsBefore),
            "isolated auction and store checks never mutate the plugin's main seasonal market journals");
    }
    private UUID smokeAuction(net.nerdsmp.paper.market.DurableMarketService market,
                              net.nerdsmp.paper.auction.DurableAuctionService auctions,
                              net.nerdsmp.api.model.AccountId seller,UUID stall,String item,long price,Long buyNow,
                              AuctionFixtureClock clock) {
        UUID id=smokeAwait(market.prepareAuctionListing(seller,stall,item,price,0)).entry().orElseThrow().listing().id();
        check(smokeAwait(auctions.prepare(id,seller,price,buyNow,clock.instant().plusSeconds(3600))).outcome()
            ==net.nerdsmp.paper.auction.DurableAuctionService.Outcome.PREPARED,"timed auction fixture prepares valid terms before deposit");
        smokeAwait(market.confirmDeposit(id,seller));
        check(smokeAwait(auctions.activate(id,seller)).outcome()==net.nerdsmp.paper.auction.DurableAuctionService.Outcome.ACTIVE,
            "timed auction fixture activates only confirmed item escrow");
        return id;
    }
    private static <T> T smokeAwait(java.util.concurrent.CompletionStage<T> stage) {
        var future=stage.toCompletableFuture();
        if(!future.isDone()) throw new IllegalStateException("Isolated fixture operation unexpectedly became asynchronous; never block Paper's main thread");
        return future.join();
    }
    private static final class AuctionFixtureClock extends java.time.Clock {
        private java.time.Instant now=java.time.Instant.parse("2026-10-12T12:00:00Z");
        void advanceSeconds(long seconds) { now=now.plusSeconds(seconds); }
        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public java.time.Instant instant() { return now; }
    }
    private void checkHoeHarvest(World world) throws Exception {
        var skillField=core.getClass().getDeclaredField("skillListener");skillField.setAccessible(true);
        var constructor=net.nerdsmp.paper.gathering.HoeHarvestService.class.getDeclaredConstructor(JavaPlugin.class,
            net.nerdsmp.paper.session.SessionRegistry.class,net.nerdsmp.paper.listener.SkillListener.class,
            java.util.function.Predicate.class,java.util.function.BiConsumer.class);
        constructor.setAccessible(true);
        HoeFixture fixture=new HoeFixture(world);
        var service=(net.nerdsmp.paper.gathering.HoeHarvestService)constructor.newInstance(core,core.sessions(),skillField.get(core),
            (java.util.function.Predicate<Player>)player->player==fixture.player,
            (java.util.function.BiConsumer<Player,ItemStack>)(player,item)->fixture.wear++);
        farmingPlayer=fixture.id;farmingBreaks.clear();core.sessions().join(fixture.id,net.nerdsmp.api.model.AccountId.random());
        Set<UUID> existingItems=itemIds(world);
        try {
            org.bukkit.block.Block crop=world.getBlockAt(3,121,4);
            Material[] fields={Material.WHEAT,Material.CARROTS,Material.POTATOES,Material.BEETROOTS,Material.NETHER_WART};
            for(Material material:fields) {
                fixture.ordinaryHoe();fixture.at=crop.getLocation().add(.5,0,1);
                world.getBlockAt(3,120,4).setType(material==Material.NETHER_WART?Material.SOUL_SAND:Material.FARMLAND,false);
                crop.setType(material,false);var age=(org.bukkit.block.data.Ageable)crop.getBlockData();age.setAge(age.getMaximumAge());crop.setBlockData(age,false);
                var spec=net.nerdsmp.paper.gathering.HoeHarvestRules.recipe(material);fixture.storage[1]=new ItemStack(spec.seed(),2);
                int worn=fixture.wear;int claims=farmingBreaks.size();
                var interaction=fixture.click(crop,org.bukkit.inventory.EquipmentSlot.HAND);service.onInteract(interaction);
                check(crop.getType()==material && ((org.bukkit.block.data.Ageable)crop.getBlockData()).getAge()==0
                    && interaction.useInteractedBlock()==Event.Result.DENY && fixture.wear==worn+1
                    && farmingBreaks.size()==claims+1 && farmingBreaks.getLast().committed(),
                    "real "+material+" harvest commits once, resets growth and suppresses vanilla interaction");
                check(world.getEntitiesByClass(Item.class).stream().anyMatch(item->!existingItems.contains(item.getUniqueId())),
                    "real "+material+" harvest creates native item drops");
                int repeated=farmingBreaks.size();service.onInteract(fixture.click(crop,org.bukkit.inventory.EquipmentSlot.HAND));
                check(farmingBreaks.size()==repeated && fixture.wear==worn+1,"unripe "+material+" cannot be harvested twice");
                removeFixtureItems(world,existingItems);
            }
            fixture.ordinaryHoe();world.getBlockAt(3,120,4).setType(Material.FARMLAND,false);
            crop.setType(Material.WHEAT,false);var wheat=(org.bukkit.block.data.Ageable)crop.getBlockData();wheat.setAge(wheat.getMaximumAge());crop.setBlockData(wheat,false);
            String matureWheat=crop.getBlockData().getAsString();int initialClaims=farmingBreaks.size(),initialWear=fixture.wear;
            service.onInteract(fixture.click(crop,org.bukkit.inventory.EquipmentSlot.OFF_HAND));
            check(crop.getBlockData().getAsString().equals(matureWheat) && farmingBreaks.size()==initialClaims && fixture.wear==initialWear,
                "offhand hoe clicks never harvest or spend durability");
            cancelFarmingItems=true;service.onInteract(fixture.click(crop,org.bukkit.inventory.EquipmentSlot.HAND));
            check(!cancelFarmingItems && crop.getBlockData().getAsString().equals(matureWheat)
                && farmingBreaks.size()==initialClaims && itemIds(world).equals(existingItems) && fixture.wear==initialWear,
                "a canceled native harvest emits no loot, crop change, skill claim or tool wear");
            fixture.storage[1]=new ItemStack(Material.WHEAT_SEEDS,2);cancelFarmingPlant=true;
            service.onInteract(fixture.click(crop,org.bukkit.inventory.EquipmentSlot.HAND));
            check(!cancelFarmingPlant && crop.getBlockData().getAsString().equals(matureWheat)
                && farmingBreaks.size()==initialClaims+1 && !farmingBreaks.getLast().committed()
                && fixture.storage[1].getAmount()==2 && itemIds(world).equals(existingItems) && fixture.wear==initialWear,
                "a canceled replant restores the mature crop and preserves seeds, drops and durability");
            replaceFarmingTool=true;service.onInteract(fixture.click(crop,org.bukkit.inventory.EquipmentSlot.HAND));
            check(!replaceFarmingTool && crop.getBlockData().getAsString().equals(matureWheat)
                && !farmingBreaks.getLast().committed() && itemIds(world).equals(existingItems) && fixture.wear==initialWear,
                "tool replacement during native harvest aborts before committing resources");

            fixture.ordinaryHoe();crop.setType(Material.COCOA,false);
            var cocoa=(org.bukkit.block.data.type.Cocoa)crop.getBlockData();cocoa.setFacing(org.bukkit.block.BlockFace.EAST);cocoa.setAge(cocoa.getMaximumAge());crop.setBlockData(cocoa,false);
            world.getBlockAt(4,121,4).setType(Material.JUNGLE_LOG,false);fixture.storage[1]=null;
            service.onInteract(fixture.click(crop,org.bukkit.inventory.EquipmentSlot.HAND));
            check(((org.bukkit.block.data.type.Cocoa)crop.getBlockData()).getAge()==0
                && ((org.bukkit.block.data.type.Cocoa)crop.getBlockData()).getFacing()==org.bukkit.block.BlockFace.EAST
                && farmingBreaks.getLast().committed(),"cocoa keeps its jungle-log mounting while reserving one harvested bean");
            removeFixtureItems(world,existingItems);world.getBlockAt(4,121,4).setType(Material.AIR,false);

            world.getBlockAt(3,120,4).setType(Material.DIRT,false);crop.setType(Material.SWEET_BERRY_BUSH,false);
            var bush=(org.bukkit.block.data.Ageable)crop.getBlockData();bush.setAge(bush.getMaximumAge());crop.setBlockData(bush,false);
            service.onInteract(fixture.click(crop,org.bukkit.inventory.EquipmentSlot.HAND));
            check(((org.bukkit.block.data.Ageable)crop.getBlockData()).getAge()==1 && fixture.storage[1]==null
                && farmingBreaks.getLast().committed(),"berry bushes stay planted at their native regrowth age without a seed cost");
            removeFixtureItems(world,existingItems);
            world.getBlockAt(3,122,4).setType(Material.STONE,false);crop.setType(Material.CAVE_VINES,false);
            var vine=(org.bukkit.block.data.type.CaveVinesPlant)crop.getBlockData();vine.setBerries(true);crop.setBlockData(vine,false);
            service.onInteract(fixture.click(crop,org.bukkit.inventory.EquipmentSlot.HAND));
            check(!((org.bukkit.block.data.type.CaveVinesPlant)crop.getBlockData()).hasBerries() && farmingBreaks.getLast().committed(),
                "glow-berry harvesting removes only fruit and retains the planted vine");
            removeFixtureItems(world,existingItems);world.getBlockAt(3,122,4).setType(Material.AIR,false);

            world.getBlockAt(3,120,4).setType(Material.FARMLAND,false);crop.setType(Material.TORCHFLOWER,false);
            ItemStack specialSeed=new ItemStack(Material.TORCHFLOWER_SEEDS);var seedMeta=specialSeed.getItemMeta();
            seedMeta.displayName(net.kyori.adventure.text.Component.text("Do not consume"));specialSeed.setItemMeta(seedMeta);fixture.storage[1]=specialSeed;
            int beforeFlower=farmingBreaks.size();service.onInteract(fixture.click(crop,org.bukkit.inventory.EquipmentSlot.HAND));
            check(crop.getType()==Material.TORCHFLOWER && fixture.storage[1].equals(specialSeed)
                && farmingBreaks.size()==beforeFlower && itemIds(world).equals(existingItems),"finished torchflowers never consume named planting seeds");
            fixture.storage[1]=new ItemStack(Material.TORCHFLOWER_SEEDS);service.onInteract(fixture.click(crop,org.bukkit.inventory.EquipmentSlot.HAND));
            check(crop.getType()==Material.TORCHFLOWER_CROP && ((org.bukkit.block.data.Ageable)crop.getBlockData()).getAge()==0
                && fixture.storage[1]==null && farmingBreaks.getLast().committed(),"a plain storage seed replants the finished torchflower transactionally");
            removeFixtureItems(world,existingItems);

            crop.setType(Material.PITCHER_CROP,false);var lower=(org.bukkit.block.data.type.PitcherCrop)crop.getBlockData();
            lower.setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);lower.setAge(lower.getMaximumAge());crop.setBlockData(lower,false);
            var top=crop.getRelative(org.bukkit.block.BlockFace.UP);top.setType(Material.PITCHER_CROP,false);
            var upper=(org.bukkit.block.data.type.PitcherCrop)top.getBlockData();upper.setHalf(org.bukkit.block.data.Bisected.Half.TOP);upper.setAge(upper.getMaximumAge());top.setBlockData(upper,false);
            fixture.storage[1]=new ItemStack(Material.PITCHER_POD);service.onInteract(fixture.click(top,org.bukkit.inventory.EquipmentSlot.HAND));
            check(((org.bukkit.block.data.type.PitcherCrop)crop.getBlockData()).getAge()==0 && top.getType().isAir()
                && fixture.storage[1]==null && farmingBreaks.getLast().committed(),"upper-half pitcher clicks replant one base and remove exactly one mature top");
            check(net.nerdsmp.paper.gathering.FarmingExperience.baseXp(core,Material.WHEAT)>5,
                "Paper runtime uses the increased farming XP catalog");
        } finally {
            core.sessions().quit(fixture.id);farmingPlayer=null;cancelFarmingItems=false;cancelFarmingPlant=false;replaceFarmingTool=false;
            removeFixtureItems(world,existingItems);
            for(int x=3;x<=4;x++) for(int y=121;y<=122;y++) world.getBlockAt(x,y,4).setType(Material.AIR,false);
            world.getBlockAt(3,120,4).setType(Material.STONE,false);
        }
    }
    private static Set<UUID> itemIds(World world) {
        return world.getEntitiesByClass(Item.class).stream().map(Entity::getUniqueId).collect(java.util.stream.Collectors.toSet());
    }
    private static void removeFixtureItems(World world,Set<UUID> existing) {
        world.getEntitiesByClass(Item.class).stream().filter(item->!existing.contains(item.getUniqueId())).forEach(Entity::remove);
    }
    private static final class HoeFixture {
        final UUID id=UUID.randomUUID();final ItemStack[] storage=new ItemStack[36];final ItemStack[] armor=new ItemStack[4];final Player player;
        final org.bukkit.inventory.PlayerInventory inventory;Location at;int wear;
        HoeFixture(World world) {
            at=new Location(world,3.5,121,5);
            inventory=(org.bukkit.inventory.PlayerInventory)java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.inventory.PlayerInventory.class.getClassLoader(),new Class<?>[]{org.bukkit.inventory.PlayerInventory.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "getItemInMainHand" -> storage[0];
                    case "setItemInMainHand" -> { storage[0]=(ItemStack)args[0];yield null; }
                    case "getItemInOffHand" -> null;
                    case "getHelmet" -> armor[0];case "getChestplate" -> armor[1];case "getLeggings" -> armor[2];case "getBoots" -> armor[3];
                    case "getStorageContents","getContents" -> storage.clone();
                    case "setStorageContents" -> {System.arraycopy((ItemStack[])args[0],0,storage,0,36);yield null;}
                    case "getArmorContents" -> armor.clone();
                    case "getItem" -> args[0] instanceof Integer slot?storage[slot]:null;
                    case "setItem" -> { storage[(Integer)args[0]]=(ItemStack)args[1];yield null; }
                    case "getHeldItemSlot" -> 0;
                    case "getSize" -> 36;
                    case "getMaxStackSize" -> 64;
                    case "toString" -> "HoeFixtureInventory";
                    default -> throw new UnsupportedOperationException("Hoe inventory: "+method.getName());
                });
            player=(Player)java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> "HoeFixture";
                    case "getWorld" -> world;
                    case "getLocation" -> at.clone();
                    case "getEyeLocation" -> at.clone().add(0,1.6,0);
                    case "getInventory" -> inventory;
                    case "getItemOnCursor" -> new ItemStack(Material.AIR);
                    case "getAttackCooldown" -> 1.0f;
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "isOnline","hasPermission","isOp" -> true;
                    case "isDead","isSneaking" -> false;
                    case "sendMessage","sendRichMessage","sendActionBar","playSound","spawnParticle" -> null;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy==args[0];
                    case "toString" -> "HoeFixturePlayer";
                    default -> throw new UnsupportedOperationException("Hoe fixture: "+method.getName());
                });
            ordinaryHoe();
        }
        void ordinaryHoe() { storage[0]=new ItemStack(Material.IRON_HOE); }
        org.bukkit.event.player.PlayerInteractEvent click(org.bukkit.block.Block crop,org.bukkit.inventory.EquipmentSlot hand) {
            return new org.bukkit.event.player.PlayerInteractEvent(player,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                storage[0],crop,org.bukkit.block.BlockFace.UP,hand);
        }
    }
    private void checkAreaWand(World world) throws Exception {
        var field=core.getClass().getDeclaredField("areaWand"); field.setAccessible(true);
        AreaWand wand=(AreaWand)field.get(core);
        check(wand!=null && Arrays.stream(org.bukkit.event.player.PlayerInteractEvent.getHandlerList().getRegisteredListeners())
            .anyMatch(listener->listener.getListener()==wand && listener.getPlugin()==core),"staff area wand listener is registered");
        ItemStack item=wand.create();
        check(item.getType()==Material.STICK && item.getAmount()==1 && item.getMaxStackSize()==1 && wand.isWand(item),
            "real Paper staff wand has a versioned identity and one-item stack limit");
        ItemStack named=new ItemStack(Material.STICK); var namedMeta=named.getItemMeta();
        namedMeta.itemName(net.kyori.adventure.text.Component.text("Area Wand")); named.setItemMeta(namedMeta);
        check(!wand.isWand(null) && !wand.isWand(new ItemStack(Material.STICK)) && !wand.isWand(named),
            "plain or renamed sticks cannot impersonate the staff wand");
        NamespacedKey marker=new NamespacedKey(core,"area_wand");
        ItemStack wrongType=item.clone(); var typedMeta=wrongType.getItemMeta();
        typedMeta.getPersistentDataContainer().set(marker,PersistentDataType.STRING,"1"); wrongType.setItemMeta(typedMeta);
        ItemStack wrongVersion=item.clone(); var versionMeta=wrongVersion.getItemMeta();
        versionMeta.getPersistentDataContainer().set(marker,PersistentDataType.INTEGER,2); wrongVersion.setItemMeta(versionMeta);
        ItemStack stacked=item.clone(); stacked.setAmount(2);
        check(!wand.isWand(wrongType) && !wand.isWand(wrongVersion) && !wand.isWand(stacked),
            "malformed, unknown-version, or stacked area wands fail closed");

        UUID id=UUID.randomUUID(); boolean[] permitted={true};
        Player staff=areaPlayer(id,world,permitted);
        org.bukkit.block.Block first=world.getBlockAt(1,121,1),second=world.getBlockAt(10,124,8);
        var firstBefore=first.getBlockData(); var secondBefore=second.getBlockData();
        org.bukkit.block.Block inside=world.getBlockAt(3,121,3),outside=world.getBlockAt(12,121,12);
        var insideBefore=inside.getBlockData(); var outsideBefore=outside.getBlockData();
        Path npcFile=core.getDataFolder().toPath().resolve("npc-instances.yml");
        byte[] savedNpcs=Files.exists(npcFile)?Files.readAllBytes(npcFile):null;
        List<ZoneDefinition> zones=List.copyOf(core.zones().all());
        try {
            first.setType(Material.ANVIL,false); second.setType(Material.STONE,false);
            String anvil=first.getBlockData().getAsString(),stone=second.getBlockData().getAsString();
            var left=areaClick(staff,item,first,org.bukkit.event.block.Action.LEFT_CLICK_BLOCK,org.bukkit.inventory.EquipmentSlot.HAND);
            wand.select(left);
            check(left.isCancelled() && left.useInteractedBlock()==Event.Result.DENY && left.useItemInHand()==Event.Result.DENY
                && core.zoneCommands().selections().selection(id).orElseThrow().first().equals(new AreaSelection.Point(1,121,1)),
                "left wand click captures pos1 and cancels both block and item interaction");
            var right=areaClick(staff,item,second,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,org.bukkit.inventory.EquipmentSlot.HAND);
            wand.select(right);
            var selected=core.zoneCommands().selections().selection(id).orElseThrow();
            check(right.isCancelled() && selected.worldId().equals(world.getUID()) && selected.complete()
                && selected.bounds().equals(new ZoneDefinition.Bounds(1,121,1,10,124,8)) && selected.volume()==320,
                "right wand click completes the shared inclusive pos1/pos2 cuboid");
            var offhand=areaClick(staff,item,first,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,org.bukkit.inventory.EquipmentSlot.OFF_HAND);
            wand.select(offhand);
            check(!offhand.isCancelled() && selected.equals(core.zoneCommands().selections().selection(id).orElseThrow()),
                "offhand interaction cannot overwrite the main-hand selection");
            var cancelled=areaClick(staff,item,first,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,org.bukkit.inventory.EquipmentSlot.HAND);
            cancelled.setCancelled(true); wand.select(cancelled);
            check(selected.equals(core.zoneCommands().selections().selection(id).orElseThrow()),
                "previously cancelled wand clicks cannot overwrite the selection");
            permitted[0]=false;
            var denied=areaClick(staff,item,first,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,org.bukkit.inventory.EquipmentSlot.HAND);
            wand.select(denied);
            check(denied.isCancelled() && selected.equals(core.zoneCommands().selections().selection(id).orElseThrow()),
                "permission revocation blocks selection and still prevents workstation interaction");
            permitted[0]=true;
            var plain=areaClick(staff,named,first,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,org.bukkit.inventory.EquipmentSlot.HAND);
            wand.select(plain);
            check(!plain.isCancelled() && selected.equals(core.zoneCommands().selections().selection(id).orElseThrow()),
                "ordinary named sticks retain ordinary block interactions");
            check(anvil.equals(first.getBlockData().getAsString()) && stone.equals(second.getBlockData().getAsString())
                && zones.equals(core.zones().all()) && Arrays.equals(savedNpcs,Files.exists(npcFile)?Files.readAllBytes(npcFile):null),
                "wand selection cannot activate the forge, rebuild terrain, save a zone, or edit NPC anchors");
            check(zones.isEmpty() && core.digSites().store().all().isEmpty(),"wand capture starts with empty disposable area catalogs");
            inside.setType(Material.DIRT,false); outside.setType(Material.CLAY,false);
            String dirt=inside.getBlockData().getAsString(),clay=outside.getBlockData().getAsString();
            core.digSiteCommands().command(staff,new String[]{"create","wand_capture","20"});
            var captured=core.digSites().store().find("wand_capture").orElseThrow();
            var newRegion=core.zones().find("wand_capture").orElseThrow();
            check(captured.enabled() && captured.cooldownSeconds()==20 && captured.worldId().equals(world.getUID())
                && captured.zoneId().equals(newRegion.id()) && captured.nodes().size()==1
                && captured.nodes().getFirst().x()==inside.getX() && captured.nodes().getFirst().y()==inside.getY()
                && captured.nodes().getFirst().z()==inside.getZ() && captured.nodes().getFirst().blockData().equals(dirt)
                && newRegion.enabled() && newRegion.safe() && newRegion.bounds().equals(selected.bounds()),
                "wand cuboid saves one selected soil node and an exact enabled safe region through the real command");
            check(new net.nerdsmp.paper.digsite.DigSiteStore(core.getDataFolder().toPath().resolve("dig-sites.yml").toFile())
                    .find(captured.id()).orElseThrow().equals(captured)
                && new ZoneStore(core.getDataFolder().toPath().resolve("zones.yml").toFile())
                    .find(newRegion.id()).orElseThrow().equals(newRegion),
                "wand-created digging catalog and safe region both roundtrip from disk");
            check(dirt.equals(inside.getBlockData().getAsString()) && clay.equals(outside.getBlockData().getAsString())
                && anvil.equals(first.getBlockData().getAsString()) && stone.equals(second.getBlockData().getAsString()),
                "explicit dig-site capture records only eligible soil without replacing terrain or workstations");
            core.digSites().remove(captured.id()); core.zones().remove(newRegion.id());

            var parent=new ZoneDefinition("wand_parent","Wand encounter fixture",world.getUID(),
                new ZoneDefinition.Bounds(0,120,0,15,127,15),true,false,7,35,24,
                List.of(new ZoneDefinition.SpawnPoint(4.5,122,4.5,90)),
                List.of(new ZoneDefinition.MobEntry("vanilla:zombie",3,18)));
            core.zones().put(parent);
            core.digSiteCommands().command(staff,new String[]{"create","wand_subset","20"});
            var subset=core.digSites().store().find("wand_subset").orElseThrow();
            check(core.zones().all().size()==1 && core.zones().find(parent.id()).orElseThrow()==parent
                && subset.zoneId().equals(parent.id()) && subset.nodes().size()==1
                && subset.nodes().getFirst().x()==inside.getX() && subset.nodes().getFirst().y()==inside.getY()
                && subset.nodes().getFirst().z()==inside.getZ() && subset.nodes().getFirst().blockData().equals(dirt)
                && !core.zones().find(subset.id()).isPresent(),
                "selected dig-site subset retains its exact existing encounter parent and excludes unselected clay");
            check(dirt.equals(inside.getBlockData().getAsString()) && clay.equals(outside.getBlockData().getAsString())
                && Arrays.equals(savedNpcs,Files.exists(npcFile)?Files.readAllBytes(npcFile):null),
                "subset capture leaves inside and outside terrain and all NPC anchors unchanged");
            core.digSites().remove(subset.id()); core.zones().remove(parent.id());
            wand.quit(new org.bukkit.event.player.PlayerQuitEvent(staff,net.kyori.adventure.text.Component.empty()));
            check(core.zoneCommands().selections().selection(id).isEmpty(),"player quit discards only the temporary staff selection");
        } finally {
            core.zoneCommands().selections().clear(id);
            for(String siteId:List.of("wand_capture","wand_subset"))
                if(core.digSites().store().find(siteId).isPresent()) core.digSites().remove(siteId);
            for(String zoneId:List.of("wand_capture","wand_parent"))
                if(core.zones().find(zoneId).isPresent()) core.zones().remove(zoneId);
            inside.setBlockData(insideBefore,false); outside.setBlockData(outsideBefore,false);
            first.setBlockData(firstBefore,false); second.setBlockData(secondBefore,false);
        }
    }

    private void checkAuthoredMobLoot(LivingEntity mob) throws Exception {
        var store=core.rewardDrops().profiles();
        var console=Bukkit.getConsoleSender();
        check(core.getCommand("mobloot")!=null && core.getCommand("mobloot").getExecutor()!=null,
            "staff loot command is registered with the router");
        for(String command:List.of("mobloot create smoke_loot",
                "mobloot add smoke_loot bones material bone 100 2 2",
                "mobloot add smoke_loot gear item ironroot_shovel 100 2 2 true 2",
                "mobloot add smoke_loot purse marks marks 100 7 7",
                "mobloot mode smoke_loot replace confirm",
                "mobloot assign zone smoke smoke_loot confirm"))
            Bukkit.dispatchCommand(console,command);
        check(store.find("smoke_loot").orElseThrow().entries().size()==3
            && store.zoneAssignments().get("smoke").equals("smoke_loot"),
            "real routed staff commands commit and assign an authored replacement table");
        var beforeInvalid=store.find("smoke_loot").orElseThrow();
        boolean invalidMaterial=false;
        try {
            store.put(new net.nerdsmp.paper.loot.profile.LootProfileDefinition("smoke_loot",beforeInvalid.mode(),List.of(
                new net.nerdsmp.paper.loot.profile.LootProfileDefinition.Entry("water",
                    net.nerdsmp.paper.loot.profile.LootProfileDefinition.Kind.MATERIAL,"WATER",1,1,1,false,0))));
        } catch(IllegalArgumentException expected) { invalidMaterial=true; }
        check(invalidMaterial && store.find("smoke_loot").orElseThrow().equals(beforeInvalid),
            "live Paper registry rejects non-item materials without publishing a partial table");
        var fallback=new net.nerdsmp.paper.loot.profile.LootProfileDefinition("smoke_fallback",
            net.nerdsmp.paper.loot.profile.LootProfileDefinition.Mode.REPLACE,List.of(
                new net.nerdsmp.paper.loot.profile.LootProfileDefinition.Entry("gold",
                    net.nerdsmp.paper.loot.profile.LootProfileDefinition.Kind.MATERIAL,"GOLD_NUGGET",1,3,3,false,0)));
        store.put(fallback);
        store.assignMob("custom:veil_stalker",fallback.id());
        check(core.rewardDrops().profile(mob).orElseThrow().id().equals("smoke_loot")
            && core.rewardDrops().replacesDefaultLoot(mob),"zone loot takes priority over mob loot without a double roll");
        var restarted=new net.nerdsmp.paper.loot.profile.LootProfileStore(
            core.getDataFolder().toPath().resolve("mob-loot.yml").toFile(),store.knownItems(),store.gearItems());
        check(restarted.resolve("smoke","veil_stalker").orElseThrow().equals(store.find("smoke_loot").orElseThrow()),
            "authored table and assignments survive a fresh store load");
        Player killer=areaPlayer(UUID.randomUUID(),mob.getWorld(),new boolean[]{false});
        LivingEntity wrapped=withKiller(mob,killer);
        var death=lootDeath(wrapped);
        core.mobs().onDeath(death); core.rewardDrops().onDeath(death);
        check(death.getDrops().size()==4 && death.getDrops().stream().noneMatch(item->item.getType()==Material.EMERALD
            || item.getType()==Material.GOLD_NUGGET)
            && death.getDrops().stream().filter(item->item.getType()==Material.BONE).mapToInt(ItemStack::getAmount).sum()==2,
            "replacement awards exactly its entries and removes normal/relic/default bonus drops");
        var gear=death.getDrops().stream().filter(core.items()::isUnidentified).toList();
        check(gear.size()==2 && gear.stream().allMatch(item->item.getAmount()==1 && core.items().isAuthentic(item)
            && core.items().affixes(item).size()==2)
            && !core.items().lootInstance(gear.get(0)).orElseThrow().equals(core.items().lootInstance(gear.get(1)).orElseThrow()),
            "each authored gear reward has valid affixes, sealed presentation and a separate loot identity");
        var purse=death.getDrops().stream().filter(item->item.getItemMeta().getPersistentDataContainer()
            .has(new NamespacedKey(core,"coin_purse_id"),PersistentDataType.STRING)).findFirst().orElseThrow();
        check(purse.getAmount()==1 && core.items().isAuthentic(purse)
            && purse.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(core,"coin_purse_value"),PersistentDataType.LONG)==7L,
            "authored Marks use the existing authentic single-use coin purse");
        Item dropped=mob.getWorld().dropItem(mob.getLocation(),purse);
        var pickup=new org.bukkit.event.inventory.InventoryPickupItemEvent(Bukkit.createInventory(null,org.bukkit.event.inventory.InventoryType.HOPPER),dropped);
        core.rewardDrops().onCoinPurseHopperPickup(pickup); dropped.remove();
        check(pickup.isCancelled(),"authored coin purses remain protected from hopper pickup");
        var environmental=lootDeath(mob); core.rewardDrops().onDeath(environmental);
        check(environmental.getDrops().isEmpty() && environmental.getDroppedExp()==5,
            "replacement suppresses environmental drops without awarding player-only loot or consuming XP");
        var canceled=lootDeath(wrapped); canceled.setCancelled(true);
        for(var listener:org.bukkit.event.entity.EntityDeathEvent.getHandlerList().getRegisteredListeners())
            if(listener.getListener()==core.rewardDrops() || listener.getListener()==core.mobs()) listener.callEvent(canceled);
        check(canceled.getDrops().size()==1 && canceled.getDrops().getFirst().getType()==Material.EMERALD,
            "canceled deaths do not award authored or mob-manager loot");
        store.unassignZone("smoke");
        var mobOnly=lootDeath(wrapped); core.rewardDrops().onDeath(mobOnly);
        check(mobOnly.getDrops().size()==1 && mobOnly.getDrops().getFirst().getType()==Material.GOLD_NUGGET
            && mobOnly.getDrops().getFirst().getAmount()==3,"mob loot applies when no zone override exists");
        store.assignMob("vanilla:zombie",fallback.id());
        LivingEntity vanilla=mob.getWorld().spawn(new Location(mob.getWorld(),12.5,121,12.5),Zombie.class,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM,false,entity->entity.setAdult());
        var vanillaDeath=lootDeath(withKiller(vanilla,killer)); core.rewardDrops().onDeath(vanillaDeath);
        check(vanillaDeath.getDrops().size()==1 && vanillaDeath.getDrops().getFirst().getType()==Material.GOLD_NUGGET,
            "vanilla mob assignments are editable without changing global content tables");
        vanilla.remove(); store.unassignMob("vanilla:zombie"); store.unassignMob("veil_stalker");
        store.remove("smoke_loot"); store.remove("smoke_fallback");
        check(core.rewardDrops().profile(mob).isEmpty() && !core.rewardDrops().replacesDefaultLoot(mob),
            "unassigned encounters immediately return to the existing reward pipeline");
    }

    private void checkContentEngine(World world) throws Exception {
        var items=core.itemEngine(); var mobs=core.mobEngine();
        check(items!=null && mobs!=null && core.contentEditor()!=null
            && core.getCommand("itemengine").getExecutor()!=null && core.getCommand("mobengine").getExecutor()!=null,
            "item and encounter engines and editor commands are wired into Paper");
        for(String template:items.templates()) {
            var preview=items.preview(items.draft("engine_preview",template));
            check(!core.items().isAuthentic(preview) && core.items().describe(preview).isEmpty()
                && preview.getItemMeta().getItemModel()==null,"inert vanilla-texture item template: "+template);
        }
        var set=items.draftSet("smoke_engine_ward"); items.publishSet(set,null);
        var blade=new net.nerdsmp.paper.engine.ItemBlueprint("smoke_engine_blade","Gale Blade",Material.IRON_SWORD,
            "RARE",2,18,null,List.of("Forged for the frontier."),Map.of("damage",8.5,"skill-xp-percent",6D));
        var preview=items.preview(blade);
        String tooltip=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(net.kyori.adventure.text.Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(),preview.getItemMeta().lore()));
        check(tooltip.contains("18") && tooltip.contains("RARE") && tooltip.contains("Damage"),
            "authored item preview renders rarity, level and functional stats");
        items.publish(blade,null);
        var id=new net.nerdsmp.api.model.ItemId(blade.id());
        var weapon=core.items().create(id);
        check(core.items().isAuthentic(weapon) && core.items().gear(weapon).orElseThrow().stat("damage")==8.5
            && core.items().socketCapacity(weapon)==2 && core.rewardDrops().profiles().knownItems().contains(id)
            && core.rewardDrops().profiles().gearItems().contains(id),"published gear enters combat and loot catalogs immediately");
        check(core.items().socket(weapon,net.nerdsmp.paper.forge.GemType.ONYX,1)==net.nerdsmp.paper.item.PaperItemService.UpgradeResult.APPLIED
            && core.items().enchant(weapon,net.nerdsmp.paper.forge.ArcaneEnchantment.KEEN_EDGE,2)==net.nerdsmp.paper.item.PaperItemService.UpgradeResult.APPLIED,
            "authored weapons use the existing socket and enchantment systems");
        var meta=weapon.getItemMeta(); ((org.bukkit.inventory.meta.Damageable)meta).setDamage(11); weapon.setItemMeta(meta);
        var revised=new net.nerdsmp.paper.engine.ItemBlueprint(blade.id(),"Tempered Gale Blade",blade.material(),"EPIC",2,20,
            null,blade.lore(),Map.of("damage",9.5,"skill-xp-percent",6D));
        items.publish(revised,blade); core.items().refresh(weapon);
        check(core.items().isAuthentic(weapon) && core.items().sockets(weapon).size()==1
            && core.items().enchantmentLevel(weapon,net.nerdsmp.paper.forge.ArcaneEnchantment.KEEN_EDGE)==2
            && ((org.bukkit.inventory.meta.Damageable)weapon.getItemMeta()).getDamage()==11
            && core.items().hasValidSockets(weapon),"item revisions preserve damage, sockets, enchantments and stable identity");
        boolean stale=false; try { items.publish(blade,blade); } catch(IllegalStateException expected) { stale=true; }
        check(stale && items.find(blade.id()).orElseThrow().equals(revised),"stale item editor cannot overwrite another published revision");
        boolean coreLocked=false; try { items.publish(items.draft("ironroot_shovel","tool"),null); }
            catch(IllegalArgumentException expected) { coreLocked=true; }
        check(coreLocked,"built-in equipment remains protected from content-engine overwrites");
        var helmet=new net.nerdsmp.paper.engine.ItemBlueprint("smoke_engine_helm","Gale Ward Helm",Material.IRON_HELMET,
            "UNCOMMON",1,10,set.id(),List.of(),Map.of("defense",2D));
        var chest=new net.nerdsmp.paper.engine.ItemBlueprint("smoke_engine_chest","Gale Ward Vest",Material.IRON_CHESTPLATE,
            "UNCOMMON",1,10,set.id(),List.of(),Map.of("defense",6D));
        items.publish(helmet,null); items.publish(chest,null);
        Method gearProfile=core.gearSets().getClass().getDeclaredMethod("profile",Map.class); gearProfile.setAccessible(true);
        var profile=(net.nerdsmp.paper.item.GearSetService.GearProfile)gearProfile.invoke(core.gearSets(),Map.of(
            org.bukkit.inventory.EquipmentSlot.HEAD,core.items().create(new net.nerdsmp.api.model.ItemId(helmet.id())),
            org.bukkit.inventory.EquipmentSlot.CHEST,core.items().create(new net.nerdsmp.api.model.ItemId(chest.id()))));
        check(profile.stat("defense")==10 && profile.activeBonuses().size()==1,
            "authored equipment sets activate real stat bonuses through the existing gear engine");
        var itemReload=new net.nerdsmp.paper.engine.ItemBlueprintStore(core.getDataFolder().toPath().resolve("content/engine-items.yml").toFile(),
            core.items().coreItemIds(),core.items().coreSetIds());
        check(itemReload.find(blade.id()).orElseThrow().equals(revised) && itemReload.findSet(set.id()).orElseThrow().equals(set),
            "published items and set bonuses survive a fresh file load");
        var loot=new net.nerdsmp.paper.loot.profile.LootProfileDefinition("smoke_engine_loot",
            net.nerdsmp.paper.loot.profile.LootProfileDefinition.Mode.REPLACE,List.of(
                new net.nerdsmp.paper.loot.profile.LootProfileDefinition.Entry("blade",
                    net.nerdsmp.paper.loot.profile.LootProfileDefinition.Kind.ITEM,blade.id(),1,1,1,true,2)));
        core.rewardDrops().profiles().put(loot);
        var guardian=mobs.draft("smoke_engine_guard","guardian").withName("Gale Sentinel").withLevel(14)
            .withHealth(64).withExperienceReward(12).withLootProfile(loot.id());
        mobs.publish(guardian,mobs.revision());
        LivingEntity old=core.mobs().spawn(guardian.id(),new Location(world,10.5,121,10.5));
        double oldMaximum=old.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue();
        check(core.mobs().zoneType("custom:"+guardian.id())==EntityType.HUSK && core.mobs().level(old)==14
            && core.mobs().isElite(old) && core.rewardDrops().profile(old).orElseThrow().id().equals(loot.id()),
            "authored guardian uses its level, rank, zone-compatible identity and selected loot profile");
        var revisedMob=guardian.withName("Revised Sentinel").withHealth(80).withAttackDamage(7).withExperienceReward(20);
        mobs.publish(revisedMob,mobs.revision());
        String oldLabel=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(core.mobs().displayName(old));
        check(oldMaximum==old.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue()
            && oldLabel.contains("Gale Sentinel") && !oldLabel.contains("Revised")
            && core.mobs().authoredLootProfile(old).orElseThrow().equals(loot.id()),
            "publishing a balance revision does not alter existing encounter snapshots");
        var fresh=core.mobs().spawn(guardian.id(),new Location(world,12.5,121,12.5));
        check(fresh.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue()>oldMaximum,
            "newly spawned encounters receive the published balance revision");
        Player killer=areaPlayer(UUID.randomUUID(),world,new boolean[]{false});
        var death=lootDeath(withKiller(old,killer)); core.mobs().onDeath(death); core.rewardDrops().onDeath(death);
        check(death.getDroppedExp()==Math.round(12*core.mobs().rewardMultiplier(old)) && death.getDrops().size()==1
            && core.items().isUnidentified(death.getDrops().getFirst())
            && core.items().describe(death.getDrops().getFirst()).orElseThrow().itemId().equals(id),
            "authored mob death uses spawn-time XP and its selected unidentified item table exactly once");
        var environmental=lootDeath(old); core.mobs().onDeath(environmental); core.rewardDrops().onDeath(environmental);
        check(environmental.getDroppedExp()==0 && environmental.getDrops().isEmpty(),
            "authored XP and replacement gear require a player-credited kill");
        boolean referenced=false; try { core.rewardDrops().profiles().remove(loot.id()); }
            catch(IllegalArgumentException expected) { referenced=true; }
        check(referenced,"loot editing cannot delete a profile referenced by an authored encounter");
        old.remove(); fresh.remove();
        var ranger=mobs.draft("smoke_engine_ranger","ranger").withAttackDamage(7).withName("Gale Ranger");
        mobs.publish(ranger,mobs.revision());
        var archer=core.mobs().spawn(ranger.id(),new Location(world,10.5,121,10.5));
        var target=world.spawn(new Location(world,12.5,121,12.5),Cow.class);
        var arrow=world.spawnArrow(archer.getEyeLocation(),new org.bukkit.util.Vector(0,0,1),.1F,0);
        arrow.setShooter(archer);
        var hit=new org.bukkit.event.entity.EntityDamageByEntityEvent(arrow,target,
            org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ARROW).withCausingEntity(archer).withDirectEntity(arrow).build(),1D);
        core.mobs().onCombatDamage(hit);
        check(Math.abs(hit.getDamage()-7*archer.getPersistentDataContainer().get(new NamespacedKey(core,"mob_damage_multiplier"),PersistentDataType.DOUBLE))<.00001
            && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(core.mobs().displayName(archer)).contains("Gale Ranger")
            && !archer.isCustomNameVisible(),"authored ranger arrows use configured power and normal ranks retain personal labels");
        arrow.remove(); target.remove(); archer.remove();
        mobs.reload(); check(mobs.find(guardian.id()).orElseThrow().equals(revisedMob),"authored mob definitions reload without a plugin restart");
        checkEditorRendering(world,revised,revisedMob);
    }
    private void checkEditorRendering(World world,net.nerdsmp.paper.engine.ItemBlueprint item,
            net.nerdsmp.paper.engine.mob.MobBlueprint mob) throws Exception {
        UUID actorId=UUID.randomUUID(); var account=new net.nerdsmp.api.model.AccountId(UUID.randomUUID());
        var view=new java.util.concurrent.atomic.AtomicReference<org.bukkit.inventory.InventoryView>();
        Player actor=(Player)java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},
            (proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> actorId;
                case "getName" -> "StudioFixture";
                case "hasPermission","isOp","isOnline" -> true;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world,2.5,121,2.5);
                case "getOpenInventory" -> view.get();
                case "openInventory" -> { view.set(editorView((Player)proxy,(org.bukkit.inventory.Inventory)args[0])); yield view.get(); }
                case "sendMessage","closeInventory" -> null;
                case "hashCode" -> actorId.hashCode();
                case "equals" -> proxy==args[0];
                case "toString" -> "StudioFixturePlayer";
                default -> throw new UnsupportedOperationException("Editor fixture must not execute gameplay: "+method.getName());
            });
        core.sessions().join(actorId,account);
        Class<?> draftClass=Class.forName("net.nerdsmp.paper.engine.ContentEditor$Draft");
        var constructor=draftClass.getDeclaredConstructor(boolean.class,net.nerdsmp.api.model.AccountId.class); constructor.setAccessible(true);
        Object itemDraft=constructor.newInstance(false,account);
        var itemField=draftClass.getDeclaredField("item"); itemField.setAccessible(true); itemField.set(itemDraft,item);
        Method showItem=core.contentEditor().getClass().getDeclaredMethod("showItem",Player.class,draftClass); showItem.setAccessible(true);
        showItem.invoke(core.contentEditor(),actor,itemDraft);
        var top=view.get().getTopInventory();
        check(top.getSize()==54 && top.getItem(4)!=null && top.getItem(24)!=null
            && !core.items().isAuthentic(top.getItem(4)) && core.items().describe(top.getItem(4)).isEmpty(),
            "real Paper item editor renders an inert preview and separate publishing control in a 54-slot layout");
        for(var click:List.of(org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.ClickType.SHIFT_LEFT,
                org.bukkit.event.inventory.ClickType.NUMBER_KEY,org.bukkit.event.inventory.ClickType.DOUBLE_CLICK,
                org.bukkit.event.inventory.ClickType.MIDDLE,org.bukkit.event.inventory.ClickType.DROP)) {
            var event=new org.bukkit.event.inventory.InventoryClickEvent(view.get(),org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                4,click,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL,0);
            core.contentEditor().onClick(event); check(event.isCancelled(),"editor blocks inventory extraction: "+click);
        }
        var drag=new org.bukkit.event.inventory.InventoryDragEvent(view.get(),new ItemStack(Material.STONE),new ItemStack(Material.STONE),false,
            Map.of(10,new ItemStack(Material.STONE)));
        core.contentEditor().onDrag(drag); check(drag.isCancelled(),"editor blocks dragging items into its controls");
        Object mobDraft=constructor.newInstance(true,account);
        var mobField=draftClass.getDeclaredField("encounter"); mobField.setAccessible(true); mobField.set(mobDraft,mob);
        Method showMob=core.contentEditor().getClass().getDeclaredMethod("showMob",Player.class,draftClass); showMob.setAccessible(true);
        int before=world.getLivingEntities().size(); showMob.invoke(core.contentEditor(),actor,mobDraft);
        top=view.get().getTopInventory();
        check(top.getSize()==54 && top.getItem(19)!=null && top.getItem(24)!=null && top.getItem(33)!=null
            && world.getLivingEntities().size()==before,"real Paper encounter editor renders ability, loot and publish controls without spawning a preview mob");
        core.contentEditor().onQuit(new org.bukkit.event.player.PlayerQuitEvent(actor,net.kyori.adventure.text.Component.empty()));
        core.sessions().quit(actorId);
    }

    private void checkDeveloperSuite(World world) throws Exception {
        for(String name:List.of("studio","lootstudio","worldstudio","npcstudio","recipestudio"))
            check(core.getCommand(name)!=null && core.getCommand(name).getExecutor()!=null,"developer tool is routed: "+name);
        var report=core.qaStudio().validate();
        check(!report.releaseApproved() && report.counts().getOrDefault(net.nerdsmp.paper.studio.qa.ContentRef.Type.ITEM,0)>0
            && report.counts().getOrDefault(net.nerdsmp.paper.studio.qa.ContentRef.Type.QUEST,0)==core.engagement().definitions().size(),
            "quality desk scans real item and quest catalogs without claiming release approval");
        var profile=core.rewardDrops().profiles().find("smoke_engine_loot").orElseThrow();
        int living=world.getLivingEntities().size(); var known=Set.copyOf(core.playerData().knownAccounts());
        var simulation=net.nerdsmp.paper.studio.qa.LootSimulation.simulate(profile,42);
        check(simulation.samples()==1000 && simulation.entries().getFirst().quantity()==1000
            && living==world.getLivingEntities().size() && known.equals(Set.copyOf(core.playerData().knownAccounts())),
            "loot QA simulates exact guaranteed rewards without creating entities or player records");
        var board=core.qaStudio().workboard(); UUID author=UUID.randomUUID();
        var task=board.create("studio_fixture","Check the starter generator",net.nerdsmp.paper.studio.qa.WorkItem.Kind.BUG,author);
        var ref=new net.nerdsmp.paper.studio.qa.ContentRef(net.nerdsmp.paper.studio.qa.ContentRef.Type.ITEM,"smoke_engine_blade");
        task=board.reference(task.id(),task.revision(),ref); task=board.note(task.id(),task.revision(),author,"Retest generator flow after content changes.");
        task=board.assign(task.id(),task.revision(),author);
        task=board.transition(task.id(),task.revision(),net.nerdsmp.paper.studio.qa.WorkItem.State.IN_PROGRESS);
        var reloaded=new net.nerdsmp.paper.studio.qa.WorkboardStore(core.getDataFolder().toPath().resolve("studio/workboard.yml").toFile());
        check(reloaded.find(task.id()).orElseThrow().equals(task),"team bug, owner, note and content reference persist across a fresh store load");
        boolean stale=false; try { board.note(task.id(),1,author,"Stale edit"); } catch(IllegalStateException expected) { stale=true; }
        check(stale && board.find(task.id()).orElseThrow().equals(task),"stale team workboard writes cannot overwrite newer notes");
        task=board.transition(task.id(),task.revision(),net.nerdsmp.paper.studio.qa.WorkItem.State.DONE);
        board.archive(task.id(),task.revision());
        check(board.find(task.id()).isEmpty() && Files.isDirectory(core.getDataFolder().toPath().resolve("studio/workboard-archive")),
            "completed work archives a durable snapshot and frees active workboard capacity");
        boolean reserved=false; try { board.create(task.id(),"Reuse old ID",task.kind(),author); }
            catch(IllegalArgumentException expected) { reserved=true; }
        check(reserved,"archived work-item IDs remain reserved for traceability");
        Path boardFile=core.getDataFolder().toPath().resolve("studio/workboard.yml"); byte[] healthyBoard=Files.readAllBytes(boardFile);
        try {
            Files.writeString(boardFile,"invalid: true\n");
            var degraded=new net.nerdsmp.paper.studio.qa.QAStudio(core,core.studioUi());
            check(!degraded.workboardAvailable() && !degraded.validate().releaseApproved() && core.isEnabled(),
                "corrupt optional workboard data leaves gameplay and content validation available");
        } finally { Files.write(boardFile,healthyBoard); }
        var npc=core.npcEngine().draft("studio_fixture_guide");
        var root=new net.nerdsmp.paper.studio.npc.NpcBlueprint.Node("root",List.of("<red>This stays literal.</red>"),List.of(
            new net.nerdsmp.paper.studio.npc.NpcBlueprint.Choice("services","Show me your services","services",
                net.nerdsmp.paper.studio.npc.NpcBlueprint.Service.NONE,5,10)));
        var services=new net.nerdsmp.paper.studio.npc.NpcBlueprint.Node("services",List.of("I can look after your supplies."),List.of(
            new net.nerdsmp.paper.studio.npc.NpcBlueprint.Choice("bank","Open my bank","",
                net.nerdsmp.paper.studio.npc.NpcBlueprint.Service.BANK,1,-1000)));
        npc=new net.nerdsmp.paper.studio.npc.NpcBlueprint(npc.id(),"Tessa Gale","Quartermaster",npc.type(),npc.faction(),"root","",8,Map.of("root",root,"services",services));
        var anchorsField=core.npcs().getClass().getDeclaredField("instances"); anchorsField.setAccessible(true);
        int anchors=((Map<?,?>)anchorsField.get(core.npcs())).size();
        core.npcEngine().publish(npc,null);
        String rootId=net.nerdsmp.paper.studio.npc.NpcBlueprint.dialogueId(npc.id(),npc.root());
        var content=core.npcs().content(); var actual=content.dialogues().get(rootId);
        check(content.npcs().containsKey(npc.id()) && actual.choices().getFirst().requirements().minimumAdventurerLevel()==5
            && actual.choices().getFirst().requirements().minimumReputation()==10
            && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                net.kyori.adventure.text.Component.text(actual.text().getFirst())).equals("<red>This stays literal.</red>"),
            "authored NPC dialogue publishes real branches, gates and raw literal speech");
        var terminal=content.dialogues().get(net.nerdsmp.paper.studio.npc.NpcBlueprint.dialogueId(npc.id(),"services"));
        check(terminal.choices().getFirst().effects().size()==1
            && terminal.choices().getFirst().effects().getFirst().type()==net.nerdsmp.paper.npc.NpcContent.EffectType.OPEN_BANK
            && anchors==((Map<?,?>)anchorsField.get(core.npcs())).size() && living==world.getLivingEntities().size(),
            "NPC publishing links bank service but creates no placement anchors or bodies");
        var parsed=new net.nerdsmp.paper.npc.NpcContentLoader(core,core.items()).load();
        check(parsed.npcs().get(npc.id()).equals(content.npcs().get(npc.id())) && parsed.dialogues().containsKey(rootId),
            "authored NPCs and branching dialogue survive a full catalog reload");
        var revised=new net.nerdsmp.paper.studio.npc.NpcBlueprint(npc.id(),"Tessa Brightwind",npc.title(),npc.type(),npc.faction(),npc.root(),npc.shop(),npc.lookRadius(),npc.nodes());
        core.npcEngine().publish(revised,npc);
        boolean oldNpc=false; try { core.npcEngine().publish(npc,npc); } catch(IllegalStateException expected) { oldNpc=true; }
        check(oldNpc && core.npcEngine().find(npc.id()).orElseThrow().equals(revised),"stale NPC graph drafts cannot replace a published revision");
        checkDeveloperRecipes(world);
        checkStudioReadOnlyViews(world);
    }
    private void checkNpcChatDialogue() throws Exception {
        var manager=core.npcs();
        var field=manager.getClass().getDeclaredField("chatSessions"); field.setAccessible(true);
        var sessions=(net.nerdsmp.paper.npc.chat.ChatDialogueSessions)field.get(manager);
        check(sessions!=null && core.getCommand("npc")!=null && core.getCommand("npc").getExecutor()!=null,
            "NPC runtime owns the short-lived chat registry and its registered command route");
        var player=UUID.randomUUID(); var account=new net.nerdsmp.api.model.AccountId(UUID.randomUUID());
        var instance=UUID.randomUUID(); var token=UUID.randomUUID();
        var choices=new ArrayList<>(List.of("locked","available","leave"));
        sessions.start(player,account,instance,"chat_fixture","root",choices,token);
        choices.clear();
        check(sessions.current(player).orElseThrow().choiceIds().equals(List.of("locked","available","leave")),
            "runtime chat registry freezes original response numbering, including locked responses");
        var stranger=new net.nerdsmp.api.model.AccountId(UUID.randomUUID());
        check(sessions.begin(player,stranger,token,2).status()==net.nerdsmp.paper.npc.chat.ChatDialogueSessions.Status.STALE
            && sessions.begin(UUID.randomUUID(),account,token,2).status()==net.nerdsmp.paper.npc.chat.ChatDialogueSessions.Status.MISSING
            && sessions.begin(player,account,UUID.randomUUID(),2).status()==net.nerdsmp.paper.npc.chat.ChatDialogueSessions.Status.STALE,
            "runtime response tokens are bound to the exact player, account and rendered page");
        check(sessions.begin(player,account,token,0).status()==net.nerdsmp.paper.npc.chat.ChatDialogueSessions.Status.INVALID_CHOICE
            && sessions.begin(player,account,token,4).status()==net.nerdsmp.paper.npc.chat.ChatDialogueSessions.Status.INVALID_CHOICE,
            "invalid numbered replies cannot consume a valid conversation");
        var claim=sessions.begin(player,account,token,2);
        check(claim.accepted() && claim.choiceId().equals("available")
            && sessions.begin(player,account,token,2).status()==net.nerdsmp.paper.npc.chat.ChatDialogueSessions.Status.BUSY,
            "one runtime reply claims exactly the frozen second response and blocks pending replay");
        var replacement=UUID.randomUUID();
        sessions.start(player,account,instance,"chat_fixture","next",List.of("back"),replacement);
        check(!sessions.complete(player,token) && sessions.matches(player,account,replacement),
            "an older asynchronous response cannot close a newer conversation page");
        check(sessions.complete(player,replacement)
            && sessions.begin(player,account,replacement,1).status()==net.nerdsmp.paper.npc.chat.ChatDialogueSessions.Status.MISSING,
            "completed chat response tokens cannot be replayed");

        var now=new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.parse("2026-10-12T12:00:00Z"));
        var clock=new java.time.Clock() {
            @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public java.time.Instant instant() { return now.get(); }
        };
        var expiring=new net.nerdsmp.paper.npc.chat.ChatDialogueSessions(clock);
        token=UUID.randomUUID(); expiring.start(player,account,instance,"chat_fixture","root",List.of("reply"),token);
        now.set(now.get().plusSeconds(119));
        check(expiring.matches(player,account,token),"chat response remains valid before the two-minute expiry");
        now.set(now.get().plusSeconds(1));
        check(expiring.begin(player,account,token,1).status()==net.nerdsmp.paper.npc.chat.ChatDialogueSessions.Status.EXPIRED
            && expiring.current(player).isEmpty(),"chat response expires exactly at two minutes without a scheduler delay");

        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        token=UUID.randomUUID();
        var rendered=net.nerdsmp.paper.npc.chat.ChatDialogueRenderer.render(token,"<red>Tessa</red>","Quartermaster",
            List.of("Use <click:run_command:'/op'>this</click>."),List.of(
                new net.nerdsmp.paper.npc.chat.ChatDialogueRenderer.Choice("Not yet",false,List.of(),List.of("Requires Adventurer Level 5")),
                new net.nerdsmp.paper.npc.chat.ChatDialogueRenderer.Choice("Show my supplies",true,List.of("Opens the bank"),List.of())));
        var text=rendered.stream().map(plain::serialize).toList();
        check(text.stream().anyMatch(line->line.contains("<red>Tessa</red>"))
            && text.stream().anyMatch(line->line.contains("<click:run_command:'/op'>this</click>")),
            "Paper Adventure chat output retains authored markup as literal speech rather than executable actions");
        var locked=rendered.stream().filter(line->plain.serialize(line).startsWith("  [1] ")).findFirst().orElseThrow();
        var available=rendered.stream().filter(line->plain.serialize(line).startsWith("  [2] ")).findFirst().orElseThrow();
        var leave=rendered.getLast();
        check(locked.clickEvent()==null && locked.hoverEvent()!=null
            && locked.hoverEvent().value() instanceof net.kyori.adventure.text.Component reason
            && plain.serialize(reason).contains("Requires Adventurer Level 5"),
            "unavailable replies render their actual gate reason without a clickable command");
        check(available.clickEvent()!=null && available.clickEvent().action()==net.kyori.adventure.text.event.ClickEvent.Action.RUN_COMMAND
            && ((net.kyori.adventure.text.event.ClickEvent.Payload.Text)available.clickEvent().payload()).value().equals("/npc reply "+token+" 2")
            && leave.clickEvent()!=null && ((net.kyori.adventure.text.event.ClickEvent.Payload.Text)leave.clickEvent().payload()).value().equals("/npc bye "+token),
            "Paper chat links carry the exact render token and the unfiltered response index");
        var clicks=new ArrayList<String>(); for(var line:rendered) chatClicks(line,clicks);
        check(clicks.equals(List.of("/npc reply "+token+" 2","/npc bye "+token)),
            "only available replies and goodbye produce executable chat actions");

        check(manager.handleNpcCommand(Bukkit.getConsoleSender(),new String[]{"reply",token.toString(),"2"})
            && manager.handleNpcCommand(Bukkit.getConsoleSender(),new String[]{"bye",token.toString()})
            && core.getCommand("npc").execute(Bukkit.getConsoleSender(),"npc",new String[]{"reply",token.toString(),"2"})
            && core.getCommand("npc").execute(Bukkit.getConsoleSender(),"npc",new String[]{"bye",token.toString()}),
            "registered NPC reply and goodbye handlers safely reject non-player senders");
        var tokensField=manager.getClass().getDeclaredField("conversationTokens"); tokensField.setAccessible(true);
        @SuppressWarnings("unchecked") var tokens=(Map<UUID,UUID>)tokensField.get(manager);
        var contextsField=manager.getClass().getDeclaredField("chatConversations"); contextsField.setAccessible(true);
        @SuppressWarnings("unchecked") var contexts=(Map<UUID,Object>)contextsField.get(manager);
        var world=Bukkit.getWorlds().getFirst(); var actor=areaPlayer(player,world,new boolean[]{false});
        var source=manager.content(); var npc=source.npcs().values().iterator().next(); var node=source.dialogues().get(npc.rootDialogue());
        var anchor=new net.nerdsmp.paper.npc.NpcManager.NpcInstance(instance,npc.id(),world.getUID(),2.5,121,2.5,0,0);
        var conversationClass=Class.forName("net.nerdsmp.paper.npc.NpcManager$ChatConversation");
        var constructor=conversationClass.getDeclaredConstructor(Player.class,net.nerdsmp.api.model.AccountId.class,
            net.nerdsmp.paper.npc.NpcManager.NpcInstance.class,net.nerdsmp.paper.npc.NpcContent.class,
            net.nerdsmp.paper.npc.NpcContent.NpcDefinition.class,net.nerdsmp.paper.npc.NpcContent.DialogueNode.class,
            UUID.class,java.util.concurrent.atomic.AtomicBoolean.class);
        constructor.setAccessible(true);
        var conversation=constructor.newInstance(actor,account,anchor,source,npc,node,token,new java.util.concurrent.atomic.AtomicBoolean(true));
        sessions.start(player,account,instance,"chat_fixture","root",List.of("reply"),token); tokens.put(player,token);
        contexts.put(player,conversation);
        manager.replaceAuthoredContent(manager.content(),Set.of());
        check(sessions.current(player).isEmpty() && !tokens.containsKey(player) && !contexts.containsKey(player),
            "publishing an NPC catalog invalidates outstanding chat contexts, response pages and shared UI tokens");
        var retiredToken=token;
        token=UUID.randomUUID();
        sessions.start(player,account,instance,"chat_fixture","root",List.of("reply"),token); tokens.put(player,token);
        contexts.put(player,constructor.newInstance(actor,account,anchor,manager.content(),npc,node,token,new java.util.concurrent.atomic.AtomicBoolean(true)));
        check(core.getCommand("npc").execute(actor,"npc",new String[]{"bye",retiredToken.toString()})
            && core.getCommand("npc").execute(actor,"npc",new String[]{"reply",retiredToken.toString(),"1"})
            && sessions.matches(player,account,token) && contexts.containsKey(player) && token.equals(tokens.get(player)),
            "old player chat links cannot dismiss or act on a newer rendered conversation");
        check(core.getCommand("npc").execute(actor,"npc",new String[]{"bye",token.toString()})
            && sessions.current(player).isEmpty() && !contexts.containsKey(player) && !tokens.containsKey(player),
            "a non-admin player can end their own conversation through the public registered command");
        token=UUID.randomUUID();
        sessions.start(player,account,instance,"chat_fixture","root",List.of("reply"),token); tokens.put(player,token);
        contexts.put(player,constructor.newInstance(actor,account,anchor,manager.content(),npc,node,token,new java.util.concurrent.atomic.AtomicBoolean(true)));
        check(core.getCommand("npc").execute(Bukkit.getConsoleSender(),"npc",new String[]{"reload"})
            && sessions.current(player).isEmpty() && !tokens.containsKey(player) && !contexts.containsKey(player),
            "the registered NPC reload command invalidates old chat contexts before refreshing catalog bodies");
    }
    private static void chatClicks(net.kyori.adventure.text.Component component,List<String> result) {
        if(component.clickEvent()!=null && component.clickEvent().payload() instanceof net.kyori.adventure.text.event.ClickEvent.Payload.Text payload)
            result.add(payload.value());
        component.children().forEach(child->chatClicks(child,result));
    }
    private void checkDeveloperRecipes(World world) throws Exception {
        var engine=core.recipeEngine();
        var grid=List.of(Material.AMETHYST_SHARD,Material.COPPER_INGOT,Material.STONE,Material.BONE,Material.AMETHYST_SHARD,
            Material.BONE,Material.COPPER_INGOT,Material.STONE,Material.AMETHYST_SHARD);
        var draft=new net.nerdsmp.paper.studio.RecipeBlueprint("studio_blade_pattern","smoke_engine_blade",1,
            net.nerdsmp.api.model.Skill.SMITHING,15,false,grid);
        check(!core.items().isAuthentic(engine.preview(draft)) && engine.preview(draft).getItemMeta().getPersistentDataContainer().getKeys().isEmpty(),
            "recipe ghost previews carry no authentic item metadata");
        engine.publish(draft,null); var key=new NamespacedKey(core,"engine_recipe_"+draft.id());
        check(Bukkit.getRecipe(key)==null,"publishing a disabled recipe does not enable crafting");
        var active=new net.nerdsmp.paper.studio.RecipeBlueprint(draft.id(),draft.output(),draft.amount(),draft.skill(),draft.level(),true,grid);
        engine.publish(active,draft);
        var nativeRecipe=(org.bukkit.inventory.ShapedRecipe)Bukkit.getRecipe(key);
        check(nativeRecipe!=null && core.items().isAuthentic(nativeRecipe.getResult())
            && core.recipes().recipes().stream().anyMatch(recipe->recipe.id().equals(draft.id()) && recipe.requiredLevel()==15),
            "enabled authored recipes register authentic output and integrate with the skill-gated recipe catalog");
        UUID crafterId=UUID.randomUUID(); var crafterAccount=new net.nerdsmp.api.model.AccountId(UUID.randomUUID());
        GameMode[] mode={GameMode.SURVIVAL};
        Player crafter=(Player)java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},
            (proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> crafterId;
                case "getGameMode" -> mode[0];
                case "getWorld" -> world;
                case "hashCode" -> crafterId.hashCode();
                case "equals" -> proxy==args[0];
                default -> throw new UnsupportedOperationException("Craft fixture must not execute gameplay: "+method.getName());
            });
        core.sessions().join(crafterId,crafterAccount);
        var recipeDefinition=core.recipes().recipes().stream().filter(recipe->recipe.id().equals(active.id())).findFirst().orElseThrow();
        Method unlocked=core.recipes().getClass().getDeclaredMethod("unlocked",Player.class,net.nerdsmp.paper.item.CustomRecipeService.RecipeDefinition.class);unlocked.setAccessible(true);
        check(!(boolean)unlocked.invoke(core.recipes(),crafter,recipeDefinition),"authored recipes reject insufficient skill progression");
        var award=core.progression().awardExperience(crafterAccount,net.nerdsmp.api.model.Skill.SMITHING,
            net.nerdsmp.paper.service.InMemoryProgressionService.experienceForLevel(15),"studio-skill-fixture").toCompletableFuture().join();
        var snapshotsField=core.presentation().getClass().getDeclaredField("progressionSnapshots");snapshotsField.setAccessible(true);
        Map<java.util.UUID,net.nerdsmp.api.service.ProgressionService.ProgressionSnapshot> snapshots=
            (Map<java.util.UUID,net.nerdsmp.api.service.ProgressionService.ProgressionSnapshot>)snapshotsField.get(core.presentation());
        snapshots.put(crafterId,core.progression().snapshot(crafterAccount).toCompletableFuture().join());
        check(award.awarded() && (boolean)unlocked.invoke(core.recipes(),crafter,recipeDefinition),"authored recipes unlock against the actual earned skill snapshot");
        mode[0]=GameMode.CREATIVE;
        check(!(boolean)unlocked.invoke(core.recipes(),crafter,recipeDefinition),"creative mode cannot bypass the player-crafting policy");
        snapshots.remove(crafterId);core.sessions().quit(crafterId);
        boolean stale=false; try { engine.publish(draft,draft); } catch(IllegalStateException expected) { stale=true; }
        check(stale && Bukkit.getRecipe(key)!=null,"stale recipe publication preserves the registered recipe");
        boolean reserved=false;
        try { engine.draft(core.recipes().coreRecipeIds().iterator().next(),draft.output()); }
        catch(IllegalArgumentException expected) { reserved=true; }
        check(reserved,"authored recipes cannot reuse built-in content reference IDs");
        var block=world.getBlockAt(15,121,15); var before=block.getBlockData(); block.setType(Material.CRAFTER,false);
        var automated=new org.bukkit.event.block.CrafterCraftEvent(block,nativeRecipe,nativeRecipe.getResult());
        core.recipes().onAutomatedCraft(automated); block.setBlockData(before,false);
        check(automated.isCancelled(),"vanilla automatic crafters cannot bypass authored skill requirements");
        Method ordinary=core.recipes().getClass().getDeclaredMethod("ordinaryIngredients",ItemStack[].class);ordinary.setAccessible(true);
        check((boolean)ordinary.invoke(null,(Object)new ItemStack[]{new ItemStack(Material.AMETHYST_SHARD)})
            && !(boolean)ordinary.invoke(null,(Object)new ItemStack[]{core.items().create(new net.nerdsmp.api.model.ItemId(draft.output()))}),
            "recipe ingredients distinguish plain supplies from affixed or authored gear");
        var storeField=engine.getClass().getDeclaredField("store"); storeField.setAccessible(true); Object original=storeField.get(engine);
        Class<?> writer=Class.forName("net.nerdsmp.paper.studio.RecipeBlueprintStore$Writer");
        Object failing=java.lang.reflect.Proxy.newProxyInstance(writer.getClassLoader(),new Class<?>[]{writer},(proxy,method,args)->{throw new java.io.IOException("Fixture disk failure");});
        var constructor=net.nerdsmp.paper.studio.RecipeBlueprintStore.class.getDeclaredConstructor(java.io.File.class,java.util.function.Consumer.class,writer);constructor.setAccessible(true);
        Object store=constructor.newInstance(core.getDataFolder().toPath().resolve("content/engine-recipes.yml").toFile(),(java.util.function.Consumer<net.nerdsmp.paper.studio.RecipeBlueprint>)recipe->{},failing);
        storeField.set(engine,store); boolean failed=false;
        try { engine.publish(new net.nerdsmp.paper.studio.RecipeBlueprint(active.id(),active.output(),1,active.skill(),20,true,grid),active); }
        catch(java.io.IOException expected) { failed=true; }
        finally { storeField.set(engine,original); }
        check(failed && engine.find(active.id()).orElseThrow().equals(active)
            && core.recipes().recipes().stream().filter(recipe->recipe.id().equals(active.id())).findFirst().orElseThrow().requiredLevel()==15
            && Bukkit.getRecipe(key)!=null,"failed recipe saves restore the live registry and previous skill gate");
        engine.publish(draft,active); check(Bukkit.getRecipe(key)==null,"disabling an authored recipe unregisters only its own recipe key");
        engine.remove(draft.id(),draft); check(engine.find(draft.id()).isEmpty(),"removing a recipe does not remove its authored item definition");
    }
    private void checkStudioReadOnlyViews(World world) throws Exception {
        UUID id=UUID.randomUUID(); var account=new net.nerdsmp.api.model.AccountId(UUID.randomUUID());
        Player actor=areaPlayer(id,world,new boolean[]{true}); core.sessions().join(id,account);
        Class<?> pageClass=Class.forName("net.nerdsmp.paper.studio.StudioUi$Page");
        var constructor=pageClass.getDeclaredConstructor(Player.class,String.class,String.class,List.class,net.nerdsmp.api.model.AccountId.class);constructor.setAccessible(true);
        Object page=constructor.newInstance(actor,"nerdsmp.studio.use","Fixture",List.of(),account);
        var top=Bukkit.createInventory((org.bukkit.inventory.InventoryHolder)page,54);
        var inventoryField=pageClass.getDeclaredField("inventory");inventoryField.setAccessible(true);inventoryField.set(page,top);
        var view=editorView(actor,top);
        for(var click:List.of(org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.ClickType.NUMBER_KEY,
                org.bukkit.event.inventory.ClickType.MIDDLE,org.bukkit.event.inventory.ClickType.DOUBLE_CLICK,org.bukkit.event.inventory.ClickType.SHIFT_LEFT)) {
            var event=new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,10,click,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL,0);
            core.studioUi().click(event);check(event.isCancelled(),"shared studio views prevent preview extraction: "+click);
        }
        var drag=new org.bukkit.event.inventory.InventoryDragEvent(view,new ItemStack(Material.STONE),new ItemStack(Material.STONE),false,Map.of(10,new ItemStack(Material.STONE)));
        core.studioUi().drag(drag);check(drag.isCancelled(),"shared studio controls cannot accept dragged inventory items");
        core.studioUi().quit(new org.bukkit.event.player.PlayerQuitEvent(actor,net.kyori.adventure.text.Component.empty()));core.sessions().quit(id);
    }
    private static org.bukkit.inventory.InventoryView editorView(Player actor,org.bukkit.inventory.Inventory top) {
        var bottom=Bukkit.createInventory(null,36);
        return (org.bukkit.inventory.InventoryView)java.lang.reflect.Proxy.newProxyInstance(org.bukkit.inventory.InventoryView.class.getClassLoader(),
            new Class<?>[]{org.bukkit.inventory.InventoryView.class},(proxy,method,args)->switch(method.getName()) {
                case "getTopInventory" -> top;
                case "getBottomInventory" -> bottom;
                case "getPlayer" -> actor;
                case "getType" -> org.bukkit.event.inventory.InventoryType.CHEST;
                case "getInventory" -> (int)args[0]<0 ? null : (int)args[0]<54 ? top : bottom;
                case "convertSlot" -> (int)args[0]<54 ? args[0] : (int)args[0]-54;
                case "getSlotType" -> org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER;
                case "getCursor" -> new ItemStack(Material.AIR);
                case "countSlots" -> 90;
                case "getTitle","getOriginalTitle" -> "Studio fixture";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy==args[0];
                default -> throw new UnsupportedOperationException("View fixture must not execute gameplay: "+method.getName());
            });
    }
    private static LivingEntity withKiller(LivingEntity actual,Player killer) {
        return (LivingEntity)java.lang.reflect.Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),new Class<?>[]{LivingEntity.class},
            (proxy,method,args)->{
                if(method.getName().equals("getKiller")) return killer;
                try { return method.invoke(actual,args); }
                catch(java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
            });
    }
    private static org.bukkit.event.entity.EntityDeathEvent lootDeath(LivingEntity entity) {
        return new org.bukkit.event.entity.EntityDeathEvent(entity,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
            new ArrayList<>(List.of(new ItemStack(Material.EMERALD))),5);
    }
    private static Player areaPlayer(UUID id,World world,boolean[] permitted) {
        return (Player)java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},
            (proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> id;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world,2.5,121,2.5);
                case "hasPermission","isOp" -> permitted[0];
                case "isOnline" -> false;
                case "sendMessage","sendRichMessage","playSound","spawnParticle" -> null;
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy==args[0];
                case "toString" -> "StaffAreaFixturePlayer";
                default -> throw new UnsupportedOperationException("Staff fixture cannot execute gameplay: "+method.getName());
            });
    }
    private static org.bukkit.event.player.PlayerInteractEvent areaClick(Player player,ItemStack item,org.bukkit.block.Block block,
            org.bukkit.event.block.Action action,org.bukkit.inventory.EquipmentSlot hand) {
        return new org.bukkit.event.player.PlayerInteractEvent(player,action,item,block,org.bukkit.block.BlockFace.UP,hand);
    }
    private void checkNpcPlacement(World world) throws Exception {
        var manager=core.npcs();
        Method create=manager.getClass().getDeclaredMethod("createInstance",String.class,Location.class); create.setAccessible(true);
        var field=manager.getClass().getDeclaredField("instances"); field.setAccessible(true);
        Map<?,?> anchors=(Map<?,?>)field.get(manager);
        int original=anchors.size();
        Location point=new Location(world,7.5,121,7.5,90,0);
        var instance=(net.nerdsmp.paper.npc.NpcManager.NpcInstance)create.invoke(manager,"mara_venn",point);
        check(instance!=null && anchors.size()==original+1,"manual NPC placement saves and publishes one anchor");
        Path file=core.getDataFolder().toPath().resolve("npc-instances.yml");
        byte[] saved=Files.readAllBytes(file);
        check(new String(saved,java.nio.charset.StandardCharsets.UTF_8).contains(instance.id().toString()),"manual NPC anchor is durable");
        cancelNpcBody=true;
        check(create.invoke(manager,"mara_venn",point.clone().add(3,0,0))==null && !cancelNpcBody
            && anchors.size()==original+1 && Arrays.equals(saved,Files.readAllBytes(file)),"cancelled NPC body leaves no saved anchor");
        cancelNpcPlate=true;
        check(create.invoke(manager,"mara_venn",point.clone().add(3,0,0))==null && !cancelNpcPlate
            && anchors.size()==original+1 && Arrays.equals(saved,Files.readAllBytes(file)),"cancelled nameplate rolls back NPC body and anchor");
        world.getBlockAt(10,122,7).setType(Material.STONE,false);
        check(create.invoke(manager,"mara_venn",point.clone().add(3,0,0))==null
            && anchors.size()==original+1 && Arrays.equals(saved,Files.readAllBytes(file)),"blocked NPC footprint is rejected without saving");
        world.getBlockAt(10,122,7).setType(Material.AIR,false);
        check(world.getLivingEntities().stream().filter(e->e.getPersistentDataContainer().has(new NamespacedKey(core,"npc_instance"))).count()==1,"failed placements leave no stray NPC bodies");
        manager.handleNpcCommand(Bukkit.getConsoleSender(),new String[]{"remove",instance.id().toString()});
        check(anchors.size()==original,"fixture NPC removed through staff management command");
    }
    private void checkSocketItems() throws Exception {
        var items=core.items();
        var ruby=new net.nerdsmp.paper.item.PaperItemService.SocketedGem(net.nerdsmp.paper.forge.GemType.RUBY,2);
        var onyx=new net.nerdsmp.paper.item.PaperItemService.SocketedGem(net.nerdsmp.paper.forge.GemType.ONYX,3);
        ItemStack gear=items.itemIds().stream().map(items::create).filter(item->items.socketCapacity(item)>=2).findFirst().orElseThrow();
        check(items.socket(gear,ruby.type(),ruby.tier())==net.nerdsmp.paper.item.PaperItemService.UpgradeResult.APPLIED,"first socket accepts tier II gem");
        check(items.socket(gear,onyx.type(),onyx.tier())==net.nerdsmp.paper.item.PaperItemService.UpgradeResult.APPLIED,"second socket accepts tier III gem");
        check(items.sockets(gear).equals(List.of(ruby,onyx)),"legacy socket payload retains gem order and tiers");
        ItemStack before=gear.clone();
        check(items.unsocket(gear,0,onyx).isEmpty() && before.equals(gear),"stale selected gem cannot mutate equipment");
        check(core.forge().unsocketDustCost(gear,0)==12 && core.forge().unsocketDustCost(gear,1)==16,"recovery charges selected gem tier");
        check(items.unsocket(gear,0,ruby).orElseThrow().equals(ruby) && items.sockets(gear).equals(List.of(onyx)),"indexed recovery preserves the other gem");
        ItemStack stacked=gear.clone(); stacked.setAmount(2);
        ItemStack stackedBefore=stacked.clone();
        check(items.unsocket(stacked,0,onyx).isEmpty() && stackedBefore.equals(stacked),"stacked gear cannot duplicate recovered gems");
        ItemStack malformed=gear.clone();
        var meta=malformed.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(core,"sockets"),PersistentDataType.STRING,"onyx:99");
        malformed.setItemMeta(meta);
        ItemStack malformedBefore=malformed.clone();
        check(!items.hasValidSockets(malformed) && items.unsocket(malformed,0,onyx).isEmpty()
            && items.socket(malformed,ruby.type(),ruby.tier())==net.nerdsmp.paper.item.PaperItemService.UpgradeResult.INVALID_SOCKET_DATA
            && malformedBefore.equals(malformed),"malformed payload is rejected without rewriting saved data");
        ItemStack gem=core.forge().createGem(ruby.type(),ruby.tier());
        ItemStack badGem=gem.clone(); var badGemMeta=badGem.getItemMeta();
        badGemMeta.getPersistentDataContainer().set(new NamespacedKey(core,"forge_kind"),PersistentDataType.INTEGER,1);
        badGem.setItemMeta(badGemMeta);
        check(core.forge().gem(badGem).isEmpty(),"wrong-type forge identity is rejected without a menu exception");
        ItemStack badIdentity=gear.clone(); var badIdentityMeta=badIdentity.getItemMeta();
        badIdentityMeta.getPersistentDataContainer().set(new NamespacedKey(core,"item_schema"),PersistentDataType.STRING,"5");
        badIdentity.setItemMeta(badIdentityMeta);
        check(!items.isAuthentic(badIdentity),"wrong-type equipment identity is rejected without a menu exception");
        Method insert=net.nerdsmp.paper.forge.ArcaneForgeService.class.getDeclaredMethod("insertRecoveredGem",ItemStack[].class,ItemStack.class,int.class);
        insert.setAccessible(true);
        ItemStack[] full=new ItemStack[36]; Arrays.setAll(full,index->new ItemStack(Material.STONE,64));
        ItemStack[] fullBefore=Arrays.stream(full).map(ItemStack::clone).toArray(ItemStack[]::new);
        check(!(boolean)insert.invoke(null,full,gem,64) && Arrays.equals(fullBefore,full),"full storage rejects recovery without changing stacks");
        ItemStack partial=gem.clone(); partial.setAmount(63); full[3]=partial;
        check((boolean)insert.invoke(null,full,gem,64) && full[3].getAmount()==64,"recovered gem fills a matching partial stack");
        full[7]=null;
        check((boolean)insert.invoke(null,full,gem,64) && full[7].equals(gem),"recovered gem uses an empty storage slot");
    }
    private void checkShopStock() throws Exception {
        var content=core.npcs().content();
        var command=core.getCommand("shops");
        check(command!=null && command.getExecutor()!=null && command.getTabCompleter()!=null
            && command.getAliases().containsAll(List.of("shop","vendors")),"shop directory command, aliases, and completion are registered");
        var yaml=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
            core.getDataFolder().toPath().resolve("content/npcs.yml").toFile());
        check(yaml.getInt("schema-version")==10,"shop content uses schema 10");
        check(content.shops().size()==10 && content.npcs().size()==31,"ten shop inventories and 31 manual NPC definitions load");
        ClassLoader loader=core.getClass().getClassLoader();
        Class<?> factory=Class.forName("net.nerdsmp.paper.npc.ShopItems",true,loader);
        Method create=factory.getDeclaredMethod("create",net.nerdsmp.paper.item.PaperItemService.class,
            net.nerdsmp.paper.npc.NpcContent.ShopProduct.class,int.class);
        create.setAccessible(true);
        int checked=0,crafted=0;
        for(var shop:content.shops().values()) for(var product:shop.products()) {
            ItemStack stock=(ItemStack)create.invoke(null,core.items(),product,product.amount());
            ItemStack matching=(ItemStack)create.invoke(null,core.items(),product,1);
            String id=shop.id()+"/"+product.id();
            check(stock.getAmount()==product.amount() && stock.getAmount()<=stock.getMaxStackSize()
                && !stock.getType().isAir() && stock.isSimilar(matching),"real item template and bundle for "+id);
            if(product.material()!=null) check(stock.getType()==product.material(),"vanilla material retained for "+id);
            if(core.items().isCraftedEquipment(product.material())) {
                var meta=stock.getItemMeta();
                check(core.items().isAuthentic(stock) && core.items().isQualityUpgradable(stock)
                    && core.items().gear(stock).isPresent() && core.items().hasValidSockets(stock)
                    && meta.hasLore() && meta.itemName()!=null && !meta.isHideTooltip()
                    && meta.hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES)
                    && meta.getItemModel()==null && (meta.getAttributeModifiers()==null || meta.getAttributeModifiers().isEmpty()),
                    "gear presentation, sockets, and vanilla texture for "+id);
                crafted++;
            } else if(product.material()!=null) check(!stock.hasItemMeta(),"ordinary supplies stay ordinary for "+id);
            checked++;
        }
        check(checked==226 && crafted>0,"all 226 products create valid stock and equipment templates");

        var vendorField=core.npcs().getClass().getDeclaredField("vendorTrading"); vendorField.setAccessible(true);
        Object trading=vendorField.get(core.npcs());
        var marketField=trading.getClass().getDeclaredField("market"); marketField.setAccessible(true);
        Object market=marketField.get(trading);
        var recipesField=market.getClass().getDeclaredField("recipeRules"); recipesField.setAccessible(true);
        List<?> recipeRules=(List<?>)recipesField.get(market);
        check(recipeRules.size()>100,"production merchant captures recognised vanilla crafting recipes");
        var stockField=market.getClass().getDeclaredField("stock"); stockField.setAccessible(true);
        var tradesField=market.getClass().getDeclaredField("trades"); tradesField.setAccessible(true);
        Map<?,?> stockBefore=new HashMap<>((Map<?,?>)stockField.get(market));
        Map<?,?> tradesBefore=new HashMap<>((Map<?,?>)tradesField.get(market));
        Method snapshot=trading.getClass().getDeclaredMethod("snapshot",net.nerdsmp.paper.npc.NpcContent.ShopProduct.class,int.class);
        snapshot.setAccessible(true);
        for(var shop:content.shops().values()) for(var product:shop.products()) {
            Object reference=snapshot.invoke(trading,product,product.amount());
            Method units=reference.getClass().getDeclaredMethod("units"); units.setAccessible(true);
            Method ask=reference.getClass().getDeclaredMethod("buyMarks"); ask.setAccessible(true);
            Method bid=reference.getClass().getDeclaredMethod("sellMarks"); bid.setAccessible(true);
            if((int)units.invoke(reference)!=product.amount() || (long)ask.invoke(reference)<0 || (long)bid.invoke(reference)<0)
                throw new AssertionError("Invalid read-only stock reference: "+shop.id()+"/"+product.id());
            if(product.sellPrice()>0 && (long)bid.invoke(reference)==0)
                throw new AssertionError("Sellable bundle has no neutral payout: "+shop.id()+"/"+product.id());
        }
        check(stockBefore.equals(stockField.get(market)) && tradesBefore.equals(tradesField.get(market)),
            "browsing all shop reference prices cannot create trades or change supply and demand");

        Class<?> vendor=Class.forName("net.nerdsmp.paper.npc.NpcVendorTrading",true,loader);
        Class<?> side=Class.forName("net.nerdsmp.paper.npc.NpcMarket$Side",true,loader);
        Object buy=Arrays.stream(side.getEnumConstants()).filter(value->value.toString().equals("BUY")).findFirst().orElseThrow();
        Object sell=Arrays.stream(side.getEnumConstants()).filter(value->value.toString().equals("SELL")).findFirst().orElseThrow();
        Method plan=vendor.getDeclaredMethod("plan",ItemStack[].class,ItemStack.class,side); plan.setAccessible(true);
        var stoneProduct=content.shops().values().stream().flatMap(shop->shop.products().stream())
            .filter(product->product.material()==Material.COBBLESTONE).findFirst().orElseThrow();
        ItemStack bundle=(ItemStack)create.invoke(null,core.items(),stoneProduct,16);
        ItemStack[] storage=new ItemStack[36]; Arrays.setAll(storage,index->new ItemStack(Material.STONE,64));
        ItemStack[] before=Arrays.stream(storage).map(ItemStack::clone).toArray(ItemStack[]::new);
        check(plan.invoke(null,storage,bundle,buy)==null && Arrays.equals(before,storage),
            "vendor refuses full storage without changing real item stacks");
        ItemStack partial=bundle.clone(); partial.setAmount(60); storage[4]=partial; storage[7]=null;
        ItemStack[] delivered=(ItemStack[])plan.invoke(null,storage,bundle,buy);
        check(delivered!=null && delivered[4].getAmount()==64 && delivered[7].getAmount()==12
            && storage[4].getAmount()==60 && storage[7]==null,"vendor plans an entire bundle with detached real item stacks");
        ItemStack named=bundle.clone(); named.setAmount(64); var namedMeta=named.getItemMeta();
        namedMeta.displayName(net.kyori.adventure.text.Component.text("Keep this stone")); named.setItemMeta(namedMeta);
        ItemStack[] sale=new ItemStack[36]; sale[0]=named; sale[1]=bundle.clone(); sale[1].setAmount(8); sale[2]=bundle.clone(); sale[2].setAmount(8);
        ItemStack[] removed=(ItemStack[])plan.invoke(null,sale,bundle,sell);
        check(removed!=null && removed[0].equals(named) && removed[1]==null && removed[2]==null
            && sale[1].getAmount()==8 && sale[2].getAmount()==8,"vendor sale leaves named supplies untouched and consumes only matching stock");
        var gearProduct=content.shops().values().stream().flatMap(shop->shop.products().stream())
            .filter(product->core.items().isCraftedEquipment(product.material())).findFirst().orElseThrow();
        ItemStack gear=(ItemStack)create.invoke(null,core.items(),gearProduct,1);
        ItemStack improved=gear.clone(); check(core.items().improveQuality(improved)==net.nerdsmp.paper.item.PaperItemService.QualityUpgradeResult.APPLIED,
            "shop gear is upgradeable through the shared equipment system");
        ItemStack[] gearSale=new ItemStack[36]; gearSale[0]=improved;
        check(plan.invoke(null,gearSale,gear,sell)==null && gearSale[0].equals(improved),"vendor cannot buy back upgraded gear as base stock");
    }
    private void checkRemoteShopRoutingAndRepairMetadata() throws Exception {
        var manager=core.npcs();
        var content=manager.content();
        var anchorsField=manager.getClass().getDeclaredField("instances"); anchorsField.setAccessible(true);
        Map<?,?> anchorsBefore=new HashMap<>((Map<?,?>)anchorsField.get(manager));
        check(anchorsBefore.isEmpty(),"remote shops can be tested with no placed merchant instances");
        ClassLoader loader=core.getClass().getClassLoader();
        Class<?> resolver=Class.forName("net.nerdsmp.paper.npc.ShopMerchantResolver",true,loader);
        Method resolve=resolver.getDeclaredMethod("resolve",net.nerdsmp.paper.npc.NpcContent.class,String.class);
        resolve.setAccessible(true);
        Method available=resolver.getDeclaredMethod("availableShop",net.nerdsmp.paper.npc.NpcContent.class,
            net.nerdsmp.paper.npc.NpcContent.NpcDefinition.class,net.nerdsmp.paper.npc.NpcContent.ShopDefinition.class);
        available.setAccessible(true);

        Class<?> localEconomy=Class.forName("net.nerdsmp.paper.service.InMemoryEconomyService",true,loader);
        check(localEconomy.isInstance(core.economy()),"disposable shop/repair smoke uses local economic persistence");
        var balancesField=localEconomy.getDeclaredField("balances"); balancesField.setAccessible(true);
        var operationsField=localEconomy.getDeclaredField("appliedOperations"); operationsField.setAccessible(true);
        Map<?,?> balancesBefore=new HashMap<>((Map<?,?>)balancesField.get(core.economy()));
        Set<?> operationsBefore=new HashSet<>((Set<?>)operationsField.get(core.economy()));
        var vendorField=manager.getClass().getDeclaredField("vendorTrading"); vendorField.setAccessible(true);
        Object trading=vendorField.get(manager);
        var marketField=trading.getClass().getDeclaredField("market"); marketField.setAccessible(true);
        Object market=marketField.get(trading);
        var stockField=market.getClass().getDeclaredField("stock"); stockField.setAccessible(true);
        var tradesField=market.getClass().getDeclaredField("trades"); tradesField.setAccessible(true);
        Map<?,?> stockBefore=new HashMap<>((Map<?,?>)stockField.get(market));
        Map<?,?> tradesBefore=new HashMap<>((Map<?,?>)tradesField.get(market));
        for(var shop:content.shops().values()) {
            var merchant=(net.nerdsmp.paper.npc.NpcContent.NpcDefinition)resolve.invoke(null,content,shop.id());
            check(merchant!=null && content.npcs().get(merchant.id())==merchant
                && content.factions().containsKey(merchant.factionId())
                && (boolean)available.invoke(null,content,merchant,shop),
                "remote GUI routes "+shop.id()+" to its loaded merchant and faction without a placed NPC");
            check(resolve.invoke(null,content,shop.id())==merchant,"remote merchant route remains deterministic for "+shop.id());
        }
        check(resolve.invoke(null,content,"not_a_shop")==null,"unknown remote shop cannot resolve a merchant");
        checkRepairMetadata();
        check(anchorsBefore.equals(anchorsField.get(manager)) && manager.content()==content,
            "remote routing and repair preview never create or move merchant anchors");
        check(balancesBefore.equals(balancesField.get(core.economy())) && operationsBefore.equals(operationsField.get(core.economy())),
            "remote routing and repair preview spend no Marks and create no economic operations");
        check(stockBefore.equals(stockField.get(market)) && tradesBefore.equals(tradesField.get(market)),
            "remote routing and repair preview leave market stock and trades unchanged");
    }
    private void checkRepairMetadata() throws Exception {
        var items=core.items();
        var repairs=core.repairs();
        check(repairs!=null,"shared repair service is available to forge inputs");
        ItemStack gear=items.createCrafted(Material.DIAMOND_SWORD);
        check(items.improveQuality(gear)==net.nerdsmp.paper.item.PaperItemService.QualityUpgradeResult.APPLIED,
            "repair fixture uses genuinely improved equipment");
        check(items.socket(gear,net.nerdsmp.paper.forge.GemType.RUBY,2)
            ==net.nerdsmp.paper.item.PaperItemService.UpgradeResult.APPLIED,"repair fixture accepts a tier II socket gem");
        check(items.enchant(gear,net.nerdsmp.paper.forge.ArcaneEnchantment.KEEN_EDGE,3)
            ==net.nerdsmp.paper.item.PaperItemService.UpgradeResult.APPLIED,"repair fixture uses shared arcane enchantment metadata");
        check(items.coat(gear,net.nerdsmp.paper.alchemy.PoisonType.NIGHTSHADE.id(),2,9),
            "repair fixture has a real weapon poison coating");
        var meta=gear.getItemMeta();
        ((org.bukkit.inventory.meta.Damageable)meta).setDamage(137);
        meta.getPersistentDataContainer().set(new NamespacedKey(this,"coating_provenance"),PersistentDataType.STRING,"Keeper's blend");
        meta.getPersistentDataContainer().set(new NamespacedKey(this,"coating_payload"),PersistentDataType.BYTE_ARRAY,new byte[]{1,3,5,7});
        meta.displayName(net.kyori.adventure.text.Component.text("Oathkeeper"));
        gear.setItemMeta(meta);
        ItemStack damagedBefore=gear.clone();
        var offered=repairs.quoteItem(gear);
        check(offered.ready() && offered.state()==net.nerdsmp.paper.item.RepairService.ItemState.READY
            && offered.missingDurability()==137 && offered.cost()>0 && offered.matches(gear),
            "damaged forged gear receives an exact positive-cost repair quote");
        var snapshotField=offered.getClass().getDeclaredField("snapshot"); snapshotField.setAccessible(true);
        ItemStack snapshot=(ItemStack)snapshotField.get(offered);
        check(snapshot!=gear && snapshot.equals(gear),"repair quote owns a detached exact item snapshot");
        Method repairCopy=net.nerdsmp.paper.item.RepairService.class.getDeclaredMethod("repairCopy",ItemStack.class);
        repairCopy.setAccessible(true);
        ItemStack repaired=(ItemStack)repairCopy.invoke(null,snapshot);
        ItemStack expected=snapshot.clone(); var expectedMeta=expected.getItemMeta();
        ((org.bukkit.inventory.meta.Damageable)expectedMeta).setDamage(0); expected.setItemMeta(expectedMeta);
        check(((org.bukkit.inventory.meta.Damageable)repaired.getItemMeta()).getDamage()==0 && repaired.equals(expected)
            && gear.equals(damagedBefore) && snapshot.equals(damagedBefore),
            "repair restores durability only and preserves every other item field without mutating input or snapshot");
        check(items.quality(repaired)==items.quality(damagedBefore) && items.sockets(repaired).equals(items.sockets(damagedBefore))
            && items.enchantments(repaired).equals(items.enchantments(damagedBefore)) && items.coating(repaired).equals(items.coating(damagedBefore)),
            "repair preserves improvement tier, gem tier, arcane enchantments, and poison potency/charges");
        check(repairs.quoteItem(repaired).state()==net.nerdsmp.paper.item.RepairService.ItemState.UNDAMAGED,
            "repaired equipment cannot be charged for another durability repair");
        ItemStack changed=gear.clone(); var changedMeta=changed.getItemMeta();
        changedMeta.getPersistentDataContainer().set(new NamespacedKey(this,"coating_provenance"),PersistentDataType.STRING,"Different blend");
        changed.setItemMeta(changedMeta);
        check(!offered.matches(changed) && offered.matches(damagedBefore),"changed metadata invalidates a repair quote while its snapshot stays intact");
        ItemStack stacked=gear.clone(); stacked.setAmount(2);
        check(repairs.quoteItem(stacked).state()==net.nerdsmp.paper.item.RepairService.ItemState.STACKED,
            "repair rejects stacked equipment without splitting or duplicating gear");
        check(repairs.quoteItem(null).state()==net.nerdsmp.paper.item.RepairService.ItemState.NO_ITEM
            && repairs.quoteItem(new ItemStack(Material.AIR)).state()==net.nerdsmp.paper.item.RepairService.ItemState.NO_ITEM,
            "empty forge repair input receives a no-item state");
        check(repairs.quoteItem(new ItemStack(Material.STONE)).state()==net.nerdsmp.paper.item.RepairService.ItemState.NOT_REPAIRABLE
            && repairs.quoteItem(new ItemStack(Material.BREAD)).state()==net.nerdsmp.paper.item.RepairService.ItemState.NOT_REPAIRABLE,
            "ordinary blocks and food cannot receive repair charges");
        ItemStack unbreakable=gear.clone(); var unbreakableMeta=unbreakable.getItemMeta();
        unbreakableMeta.setUnbreakable(true); unbreakable.setItemMeta(unbreakableMeta);
        check(repairs.quoteItem(unbreakable).state()==net.nerdsmp.paper.item.RepairService.ItemState.NOT_REPAIRABLE,
            "unbreakable equipment is not accepted as repair stock");
    }
    private void startDigSiteCheck(World world) throws Exception {
        check(core.digSites()!=null,"dig-site controller is integrated in Skyblock");
        check(core.getCommand("digsite")!=null && core.getCommand("digsite").getExecutor()!=null
            && core.getCommand("digsite").getTabCompleter()!=null,"dig-site staff command and completion are registered");
        // Stop only this disposable server's automatic sweep so test pulses are deterministic.
        core.digSites().close();
        DigSmoke fixture=new DigSmoke(); fixture.world=world; fixture.service=core.digSites();
        fixture.playerId=UUID.randomUUID();
        fixture.player=digPlayer(fixture.playerId);
        core.sessions().join(fixture.playerId,new net.nerdsmp.api.model.AccountId(fixture.playerId));
        fixture.block=world.getBlockAt(6,121,6);
        fixture.block.getChunk().addPluginChunkTicket(this);
        var previous=fixture.block.getState();
        fixture.block.setType(Material.DIRT,false);
        core.resources().onPlace(new org.bukkit.event.block.BlockPlaceEvent(fixture.block,previous,
            fixture.block.getRelative(org.bukkit.block.BlockFace.DOWN),new ItemStack(Material.DIRT),
            fixture.player,true,org.bukkit.inventory.EquipmentSlot.HAND));
        var unregistered=digBreak(fixture);
        check(!fixture.service.mayHarvest(unregistered),"unregistered public soil gains no dig-site exemption");
        check(core.resources().claim(unregistered,net.nerdsmp.paper.resource.HarvestClaims.Channel.SKILLS).isEmpty(),
            "ordinary placed soil still earns no resource XP");
        fixture.zone=new ZoneDefinition("dig_smoke","Digging fixture",world.getUID(),
            new ZoneDefinition.Bounds(0,120,0,15,130,15),true,true,3,5,32,List.of(),List.of());
        core.zones().put(fixture.zone);
        fixture.site=new net.nerdsmp.paper.digsite.DigSiteDefinition("dig_smoke",fixture.zone.id(),world.getUID(),
            UUID.randomUUID(),true,5,List.of(new net.nerdsmp.paper.digsite.DigSiteDefinition.Node(
                fixture.block.getX(),fixture.block.getY(),fixture.block.getZ(),Material.DIRT.name(),
                fixture.block.getBlockData().getAsString())));
        fixture.marker=digMarker(fixture);
        fixture.pulse=fixture.service.getClass().getDeclaredMethod("pulse"); fixture.pulse.setAccessible(true);
        String captured=fixture.block.getBlockData().getAsString();
        fixture.service.register(fixture.site);
        check(captured.equals(fixture.block.getBlockData().getAsString()),"staff capture releases provenance without rebuilding terrain");
        var first=digBreak(fixture);
        check(fixture.service.mayHarvest(first) && fixture.service.mayHarvest(first)
            && !fixture.block.getChunk().getPersistentDataContainer().has(fixture.marker),
            "repeated harvesting checks are read-only and keep a ready node available");
        fixture.firstOperation=claimDigChannels(fixture,first,"first dig");
        fixture.service.recordBreak(first);
        var data=fixture.block.getChunk().getPersistentDataContainer();
        Long due=data.get(fixture.marker,PersistentDataType.LONG);
        check(due!=null && due>System.currentTimeMillis(),"accepted dig persists its future cooldown in the real chunk");
        fixture.block.setType(Material.AIR,false); // Simulate Minecraft committing the accepted break.
        check(!fixture.service.mayHarvest(digBreak(fixture)),"cooling node cannot be harvested twice");
        var reloadedStore=new net.nerdsmp.paper.digsite.DigSiteStore(
            core.getDataFolder().toPath().resolve("dig-sites.yml").toFile());
        check(reloadedStore.find(fixture.site.id()).orElseThrow().equals(fixture.site),"authored dig-site catalog roundtrips on disk");
        fixture.reloaded=new net.nerdsmp.paper.digsite.DigSiteService(core,reloadedStore);
        check(fixture.reloaded.status(fixture.site.id()).cooling()==1
            && due.equals(data.get(fixture.marker,PersistentDataType.LONG)),
            "in-process controller reconstruction retains the original chunk cooldown (not a process-crash test)");
        laterDig(()->checkDigRestoration(fixture));
    }
    private void checkDigRestoration(DigSmoke fixture) throws Exception {
        fixture.pulse.invoke(fixture.reloaded);
        check(fixture.block.getType()==Material.AIR
            && fixture.block.getChunk().getPersistentDataContainer().has(fixture.marker,PersistentDataType.LONG),
            "reloaded controller waits rather than renewing a future cooldown");
        expireDig(fixture);
        Cow occupant=fixture.world.spawn(fixture.block.getLocation().add(.5,0,.5),Cow.class);
        check(occupant.isValid(),"real living entity occupies the depleted dig node");
        fixture.pulse.invoke(fixture.reloaded);
        check(fixture.block.getType()==Material.AIR
            && fixture.block.getChunk().getPersistentDataContainer().has(fixture.marker),
            "an occupied due node remains air and keeps its renewal marker");
        occupant.remove(); fixture.pulse.invoke(fixture.reloaded);
        check(fixture.block.getBlockData().getAsString().equals(fixture.site.nodes().getFirst().blockData())
            && !fixture.block.getChunk().getPersistentDataContainer().has(fixture.marker),
            "unoccupied due air restores the exact captured dirt and clears cooldown");
        fixture.pulse.invoke(fixture.service); // Publish ready provenance in the original controller too.
        fixture.reloaded.close();
        var second=digBreak(fixture);
        check(fixture.service.mayHarvest(second),"renewed dig node becomes eligible again");
        check(!fixture.firstOperation.equals(claimDigChannels(fixture,second,"renewed dig")),
            "each actual dig cycle receives a distinct resource operation");
        fixture.service.recordBreak(second);
        fixture.block.setType(Material.STONE,false); // An unrelated build is never replacement soil.
        fixture.foreignSnapshot=fixture.block.getBlockData().getAsString();
        laterDig(()->checkDigForeignAndCancellation(fixture));
    }
    private void checkDigForeignAndCancellation(DigSmoke fixture) throws Exception {
        expireDig(fixture); fixture.pulse.invoke(fixture.service);
        check(fixture.foreignSnapshot.equals(fixture.block.getBlockData().getAsString())
            && fixture.block.getChunk().getPersistentDataContainer().has(fixture.marker)
            && !fixture.service.mayHarvest(digBreak(fixture)),
            "due renewal preserves a foreign stone build and refuses rewards");
        fixture.block.setType(Material.AIR,false); fixture.pulse.invoke(fixture.service);
        check(fixture.block.getType()==Material.DIRT,"fixture can resume once foreign terrain is explicitly cleared");
        var cancelled=digBreak(fixture);
        claimDigChannels(fixture,cancelled,"cancelled dig attempt");
        fixture.cancelSnapshot=fixture.block.getBlockData().getAsString();
        fixture.service.recordBreak(cancelled);
        cancelled.setCancelled(true); // A later listener vetoes the original break.
        laterDig(()->checkDigCancellation(fixture));
    }
    private void checkDigCancellation(DigSmoke fixture) throws Exception {
        check(fixture.cancelSnapshot.equals(fixture.block.getBlockData().getAsString())
            && !fixture.block.getChunk().getPersistentDataContainer().has(fixture.marker)
            && fixture.service.mayHarvest(digBreak(fixture)),
            "late cancellation keeps the snapshot exact and releases the cooldown after a tick");
        var suppressed=digBreak(fixture);
        claimDigChannels(fixture,suppressed,"post-cancellation dig");
        fixture.service.recordBreak(suppressed);
        suppressed.setDropItems(false); // Suppressed drops must not consume the retained node.
        laterDig(()->checkDigQuarantineAndLifecycle(fixture));
    }
    private void checkDigQuarantineAndLifecycle(DigSmoke fixture) throws Exception {
        check(fixture.cancelSnapshot.equals(fixture.block.getBlockData().getAsString())
            && !fixture.block.getChunk().getPersistentDataContainer().has(fixture.marker)
            && fixture.service.mayHarvest(digBreak(fixture)),
            "late drop suppression retains the node and restores ready provenance");
        claimDigChannels(fixture,digBreak(fixture),"post-drop-suppression dig");
        var data=fixture.block.getChunk().getPersistentDataContainer();
        NamespacedKey corruptMarker=fixture.marker;
        data.set(corruptMarker,PersistentDataType.STRING,"corrupt-fixture-marker");
        getLogger().info("ZONE_SMOKE_EXPECTED_QUARANTINE testing a deliberately corrupt dig cooldown");
        check(!fixture.service.mayHarvest(digBreak(fixture)),"wrong-type dig cooldown fails closed");
        fixture.pulse.invoke(fixture.service);
        check(fixture.service.status(fixture.site.id()).blocked()==1
            && "corrupt-fixture-marker".equals(data.get(corruptMarker,PersistentDataType.STRING))
            && fixture.cancelSnapshot.equals(fixture.block.getBlockData().getAsString()),
            "quarantined node keeps both its corrupt evidence and captured terrain unchanged");
        fixture.site=new net.nerdsmp.paper.digsite.DigSiteDefinition(fixture.site.id(),fixture.site.zoneId(),
            fixture.site.worldId(),UUID.randomUUID(),true,5,fixture.site.nodes());
        fixture.marker=digMarker(fixture); fixture.service.register(fixture.site);
        check(fixture.service.mayHarvest(digBreak(fixture)) && data.has(corruptMarker,PersistentDataType.STRING),
            "explicit fresh-generation recapture does not erase old quarantine evidence");
        fixture.block.setType(Material.AIR,false); expireDig(fixture);
        fixture.service.setEnabled(fixture.site.id(),false); fixture.pulse.invoke(fixture.service);
        check(fixture.block.getType()==Material.AIR && data.has(fixture.marker)
            && !fixture.service.mayHarvest(digBreak(fixture)),"disabling a dig site pauses renewal without terrain changes");
        fixture.service.setEnabled(fixture.site.id(),true); fixture.pulse.invoke(fixture.service);
        check(fixture.block.getType()==Material.DIRT && !data.has(fixture.marker),"re-enabled dig site resumes due renewal");
        fixture.block.setType(Material.AIR,false); expireDig(fixture);
        var disabledZone=new ZoneDefinition(fixture.zone.id(),fixture.zone.title(),fixture.zone.worldId(),fixture.zone.bounds(),
            false,fixture.zone.safe(),fixture.zone.cap(),fixture.zone.respawnSeconds(),fixture.zone.activationRadius(),
            fixture.zone.points(),fixture.zone.mobs());
        core.zones().put(disabledZone); fixture.pulse.invoke(fixture.service);
        check(fixture.block.getType()==Material.AIR && data.has(fixture.marker),"disabled parent zone pauses dig-node renewal");
        core.zones().put(fixture.zone); fixture.pulse.invoke(fixture.service);
        check(fixture.block.getType()==Material.DIRT && !data.has(fixture.marker),"enabling the parent zone resumes its dig nodes");
        fixture.block.setType(Material.AIR,false); expireDig(fixture);
        core.zones().remove(fixture.zone.id()); fixture.pulse.invoke(fixture.service);
        check(fixture.block.getType()==Material.AIR && data.has(fixture.marker),"deleting the parent zone never rebuilds its old terrain");
        core.zones().put(fixture.zone); fixture.pulse.invoke(fixture.service);
        check(fixture.block.getType()==Material.DIRT,"restoring the authored zone permits due renewal again");
        fixture.block.setType(Material.AIR,false); expireDig(fixture);
        fixture.service.remove(fixture.site.id()); fixture.pulse.invoke(fixture.service);
        check(fixture.block.getType()==Material.AIR && data.has(fixture.marker)
            && !fixture.service.mayHarvest(digBreak(fixture))
            && new net.nerdsmp.paper.digsite.DigSiteStore(core.getDataFolder().toPath().resolve("dig-sites.yml").toFile())
                .find(fixture.site.id()).isEmpty(),"deleted dig site is durably removed and never changes leftover terrain");
        core.zones().remove(fixture.zone.id()); core.sessions().quit(fixture.playerId);
        fixture.block.getChunk().removePluginChunkTicket(this);
        getLogger().info("ZONE_SMOKE_DIGSITE_COMPLETE PASS");
        startGeneratorCheck();
    }
    private String claimDigChannels(DigSmoke fixture,org.bukkit.event.block.BlockBreakEvent event,String label) {
        var skills=core.resources().claim(event,net.nerdsmp.paper.resource.HarvestClaims.Channel.SKILLS);
        var excavation=core.resources().claim(event,net.nerdsmp.paper.resource.HarvestClaims.Channel.EXCAVATION);
        check(skills.isPresent() && excavation.isPresent()
            && skills.orElseThrow().operationId().equals(excavation.orElseThrow().operationId())
            && core.resources().claim(event,net.nerdsmp.paper.resource.HarvestClaims.Channel.SKILLS).isEmpty()
            && core.resources().claim(event,net.nerdsmp.paper.resource.HarvestClaims.Channel.EXCAVATION).isEmpty(),
            label+" shares one eligible operation across XP and finds, exactly once per channel");
        return skills.orElseThrow().operationId();
    }
    private static Player digPlayer(UUID id) {
        return (Player)java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},
            (proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> id;
                case "getGameMode" -> GameMode.SURVIVAL;
                case "isDead" -> false;
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy==args[0];
                case "toString" -> "DigsiteFixturePlayer";
                default -> throw new UnsupportedOperationException("Fixture proxy must not execute gameplay: "+method.getName());
            });
    }
    private static org.bukkit.event.block.BlockBreakEvent digBreak(DigSmoke fixture) {
        return new org.bukkit.event.block.BlockBreakEvent(fixture.block,fixture.player);
    }
    private NamespacedKey digMarker(DigSmoke fixture) {
        var block=fixture.block;
        return new NamespacedKey(core,"digsite_"+fixture.site.generation().toString().replace("-","")+"_"
            +(block.getX()&15)+"_"+(block.getY()+64)+"_"+(block.getZ()&15));
    }
    private static void expireDig(DigSmoke fixture) {
        // Advance only this fixture's persisted clock boundary; no production timer or system clock changes.
        fixture.block.getChunk().getPersistentDataContainer().set(fixture.marker,PersistentDataType.LONG,System.currentTimeMillis()-1L);
    }
    private void laterDig(DigStep step) {
        Bukkit.getScheduler().runTaskLater(this,()-> {
            try { step.run(); }
            catch(Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE,"ZONE_SMOKE_FAILED",failure);
                try { Files.writeString(Path.of("zone-smoke-result.txt"),"FAIL: "+failure+"\n"); } catch(Exception ignored) { }
                Bukkit.shutdown();
            }
        },2L);
    }
    @FunctionalInterface private interface DigStep { void run() throws Exception; }
    private static final class DigSmoke {
        World world;
        net.nerdsmp.paper.digsite.DigSiteService service,reloaded;
        net.nerdsmp.paper.digsite.DigSiteDefinition site;
        ZoneDefinition zone;
        org.bukkit.block.Block block;
        Player player;
        UUID playerId;
        NamespacedKey marker;
        Method pulse;
        String firstOperation,foreignSnapshot,cancelSnapshot;
    }
    private void startGeneratorCheck() {
        var owner = new net.nerdsmp.api.model.AccountId(UUID.randomUUID());
        var island = core.skyblock().islands().create(owner);
        core.playerData().bind(owner.value(), owner,
            new net.nerdsmp.api.model.PlatformIdentity(net.nerdsmp.api.model.PlatformIdentity.Platform.JAVA, owner.toString()), "IslandSmoke");
        World world = core.skyblock().world();
        int x=net.nerdsmp.paper.skyblock.IslandGrid.blockX(island.slot());
        int z=net.nerdsmp.paper.skyblock.IslandGrid.blockZ(island.slot())-4;
        int y=net.nerdsmp.paper.skyblock.IslandGrid.SURFACE_Y;
        world.getChunkAt(x>>4,z>>4).addPluginChunkTicket(this);
        world.setChunkForceLoaded(x>>4,z>>4,true);
        check(world.getBlockAt(x,y,z).getBlockData() instanceof org.bukkit.block.data.Levelled water && water.getLevel()!=0,"generator outlet is flowing water");
        world.getBlockAt(x,y,z).setBlockData(Material.WATER.createBlockData(),false);
        check(net.nerdsmp.paper.skyblock.StarterGeneratorRepair.repair(world,island.slot()),"unchanged historical fixture can be repaired");
        check(!net.nerdsmp.paper.skyblock.StarterGeneratorRepair.repair(world,island.slot()),"generator repair is idempotent");
        var bank=world.getBlockAt(x,y,z-1); var originalBank=bank.getBlockData();
        world.getBlockAt(x,y,z).setBlockData(Material.WATER.createBlockData(),false); bank.setType(Material.DIAMOND_BLOCK,false);
        check(!net.nerdsmp.paper.skyblock.StarterGeneratorRepair.repair(world,island.slot()),"player-edited fixture is left alone");
        check(bank.getType()==Material.DIAMOND_BLOCK,"player block survives repair attempt");
        bank.setBlockData(originalBank,false);
        check(net.nerdsmp.paper.skyblock.StarterGeneratorRepair.repair(world,island.slot()),"restored fixture repairs without rebuilding island");
        core.skyblock().islands().rename(owner,"Smoke Hearth");
        check(core.skyblock().islands().owned(owner).orElseThrow().name().equals("Smoke Hearth"),"island rename retains ownership");
        try { checkIslandWorkshop(owner, island, world); }
        catch(Exception failure) { throw new IllegalStateException("Island workshop fixture failed", failure); }
        for(String id:List.of("ironroot_shovel","ironroot_shield","wildwood_rod","riftglass_bow")) {
            var item=core.items().create(new net.nerdsmp.api.model.ItemId(id));
            check(item.getItemMeta().getItemModel()==null,"vanilla texture for "+id);
        }
        try { checkOreSmelting(world,owner,()-> {
            try {
                StackingSmoke.run(core,island,world,this::check);
                checkIslandMobStacks(island,world,()->cycleGenerator(world,x,y,z,0));
            }
            catch(Exception failure) { throw new IllegalStateException("Island mob fixture failed",failure); }
        }); }
        catch(Exception failure) { throw new IllegalStateException("Ore-smelting fixture failed",failure); }
    }

    private void checkOreSmelting(World world,net.nerdsmp.api.model.AccountId owner,Runnable complete) throws Exception {
        var island=core.skyblock().islands().owned(owner).orElseThrow();
        int x=net.nerdsmp.paper.skyblock.IslandGrid.blockX(island.slot())+4;
        int z=net.nerdsmp.paper.skyblock.IslandGrid.blockZ(island.slot())-2;
        int y=net.nerdsmp.paper.skyblock.IslandGrid.SURFACE_Y+2;
        check(world.isChunkLoaded(x>>4,z>>4),"ore fixture uses already-loaded island chunk");
        OreSmoke fixture=new OreSmoke(); fixture.owner=owner; fixture.complete=complete;
        fixture.block=world.getBlockAt(x,y,z); fixture.hopper=fixture.block.getRelative(org.bukkit.block.BlockFace.DOWN);
        fixture.original=fixture.block.getBlockData(); fixture.originalHopper=fixture.hopper.getBlockData();
        fixture.marker=new NamespacedKey(core,"ore_smelting_v1"); fixture.playerId=UUID.randomUUID();
        fixture.player=areaPlayer(fixture.playerId,world,new boolean[]{false});
        fixture.beforeXp=core.progression().snapshot(owner).toCompletableFuture().join().skills().get(net.nerdsmp.api.model.Skill.SMITHING).experience();
        core.sessions().join(fixture.playerId,owner);
        oreFurnace=fixture.block; oreNativeSmelts=0; oreHopperMoves=0; oreCanceledSmelts=0;
        fixture.block.setType(Material.FURNACE,false);
        var furnace=oreTile(fixture);
        check(!furnace.isSnapshot(),"Paper non-snapshot furnace exposes live PDC");
        furnace.setCookSpeedMultiplier(100D);
        furnace.getInventory().setFuel(new ItemStack(Material.COAL,2));
        furnace.getInventory().setSmelting(new ItemStack(Material.RAW_IRON));
        pollOre(fixture,()->oreAmount(oreTile(fixture).getInventory().getResult())==1,()-> {
            laterDig(()-> {
                var current=oreTile(fixture); var first=oreReceipt(fixture);
                check(oreNativeSmelts==1 && current.getInventory().getSmelting()==null
                    && current.getInventory().getResult().getType()==Material.IRON_INGOT
                    && oreAmount(current.getInventory().getFuel())==1 && current.getBurnTime()>0,
                    "actual native furnace consumes ore and fuel and creates one ingot");
                getLogger().info("ORE_SMELT_AFTER source="+lastOreSmelt.getSource().getType()+" amount="+lastOreSmelt.getSource().getAmount()
                    +" saved="+first.encode());
                check(first.credits()==1 && first.observed()==1 && first.output().equals("IRON_INGOT"),
                    "actual finalized native smelt creates one persistent verified receipt without inventory overwrite");
                var item=current.getInventory().getResult().clone(); current.getInventory().setResult(null);
                var extracted=new org.bukkit.event.inventory.FurnaceExtractEvent(fixture.player,fixture.block,item,1,1);
                Bukkit.getPluginManager().callEvent(extracted);
                var consumed=oreReceipt(fixture);
                check(consumed.credits()==0 && consumed.observed()==0 && consumed.sequence()==first.sequence()+1,
                    "controlled post-removal collection spends the real native smelt receipt once");
                Bukkit.getPluginManager().callEvent(extracted);
                check(oreReceipt(fixture).equals(consumed),"same extraction event cannot replay a smelting receipt");
                check(core.progression().snapshot(fixture.owner).toCompletableFuture().join().skills().get(net.nerdsmp.api.model.Skill.SMITHING).experience()==fixture.beforeXp,
                    "unregistered offline fixture cannot turn a real furnace receipt into player XP");
                // Exercise the immediate-extraction ordering using controlled events. Native
                // smelting itself was proved above; this is not presented as a real client action.
                current.getInventory().setSmelting(new ItemStack(Material.RAW_COPPER));
                var immediate=new org.bukkit.event.inventory.FurnaceSmeltEvent(fixture.block,new ItemStack(Material.RAW_COPPER),new ItemStack(Material.COPPER_INGOT));
                Bukkit.getPluginManager().callEvent(immediate);
                current.getInventory().setSmelting(null);
                var immediateExtract=new org.bukkit.event.inventory.FurnaceExtractEvent(fixture.player,fixture.block,new ItemStack(Material.COPPER_INGOT),1,0);
                Bukkit.getPluginManager().callEvent(immediateExtract);
                var immediateReceipt=oreReceipt(fixture);
                check(immediateReceipt.output().equals("COPPER_INGOT") && immediateReceipt.credits()==0
                    && immediateReceipt.observed()==0 && immediateReceipt.sequence()==consumed.sequence()+1,
                    "controlled same-tick extraction proves consumed input and retires the new unit without an XP-bank shortcut");
                current.getInventory().setResult(new ItemStack(Material.IRON_INGOT,8));
                current.getInventory().setResult(null);
                Bukkit.getPluginManager().callEvent(new org.bukkit.event.inventory.FurnaceExtractEvent(fixture.player,fixture.block,new ItemStack(Material.IRON_INGOT,8),8,100));
                check(oreReceipt(fixture).credits()==0,"inserted legacy output and a large vanilla XP bank create no receipts");
                current.setCookTime((short)0); current.getInventory().setSmelting(new ItemStack(Material.RAW_GOLD));
                pollOre(fixture,()->oreAmount(oreTile(fixture).getInventory().getResult())==1,()-> {
                    laterDig(()->checkOreHopper(fixture));
                });
            });
        });
    }

    private void checkOreHopper(OreSmoke fixture) throws Exception {
        var furnace=oreTile(fixture);
        check(furnace.getInventory().getResult().getType()==Material.GOLD_INGOT
            && oreReceipt(fixture).credits()==1,"second native material smelt replaces the empty output type safely");
        fixture.hopper.setType(Material.HOPPER,false);
        var hopper=(org.bukkit.block.Hopper)fixture.hopper.getState(false);
        hopper.setTransferCooldown(0);
        pollOre(fixture,()->oreAmount(oreTile(fixture).getInventory().getResult())==0,()-> {
            laterDig(()-> {
                var actual=(org.bukkit.block.Hopper)fixture.hopper.getState(false);
                check(oreHopperMoves>0 && actual.getInventory().contains(Material.GOLD_INGOT)
                    && oreReceipt(fixture).credits()==0 && oreReceipt(fixture).observed()==0,
                    "actual native hopper moves the ingot unchanged and retires its smelting work without XP");
                fixture.hopper.setBlockData(fixture.originalHopper,false);
                cancelOreSmelts=true; oreCanceledSmelts=0;
                var current=oreTile(fixture); current.setCookTime((short)0);
                current.getInventory().setSmelting(new ItemStack(Material.RAW_COPPER));
                pollOre(fixture,()->oreCanceledSmelts>0,()-> {
                    laterDig(()-> {
                        var canceled=oreTile(fixture);
                        check(oreReceipt(fixture).credits()==0 && oreAmount(canceled.getInventory().getResult())==0
                            && canceled.getInventory().getSmelting().getType()==Material.RAW_COPPER,
                            "native smelt canceled after production MONITOR grants no receipt and preserves the input");
                        canceled.getInventory().setSmelting(null); cancelOreSmelts=false;
                        check(core.progression().snapshot(fixture.owner).toCompletableFuture().join().skills().get(net.nerdsmp.api.model.Skill.SMITHING).experience()==fixture.beforeXp,
                            "native hopper and canceled smelts leave player progression unchanged");
                        core.sessions().quit(fixture.playerId); oreFurnace=null;
                        fixture.block.setBlockData(fixture.original,false);
                        fixture.hopper.setBlockData(fixture.originalHopper,false);
                        getLogger().info("ZONE_SMOKE_ORE_SMELTING_COMPLETE PASS"); fixture.complete.run();
                    });
                });
            });
        });
    }

    private void pollOre(OreSmoke fixture,java.util.function.BooleanSupplier ready,DigStep complete) {
        laterDig(()-> {
            fixture.ticks+=2;
            if(ready.getAsBoolean()) { complete.run(); return; }
            if(fixture.ticks>=200) throw new AssertionError("Native ore-smelting fixture timed out after 200 ticks");
            pollOre(fixture,ready,complete);
        });
    }
    private org.bukkit.block.Furnace oreTile(OreSmoke fixture) {
        return (org.bukkit.block.Furnace)fixture.block.getState(false);
    }
    private net.nerdsmp.paper.gathering.OreSmeltCredits oreReceipt(OreSmoke fixture) {
        String serialized=oreTile(fixture).getPersistentDataContainer().get(fixture.marker,PersistentDataType.STRING);
        check(serialized!=null,"live furnace receipt is present");
        return net.nerdsmp.paper.gathering.OreSmeltCredits.decode(serialized);
    }
    private static int oreAmount(ItemStack item) { return item==null || item.getType().isAir()?0:item.getAmount(); }
    private static final class OreSmoke {
        org.bukkit.block.Block block,hopper;
        org.bukkit.block.data.BlockData original,originalHopper;
        NamespacedKey marker; UUID playerId; Player player; net.nerdsmp.api.model.AccountId owner;
        long beforeXp; int ticks; Runnable complete;
    }

    @SuppressWarnings("unchecked")
    private void checkIslandWorkshop(net.nerdsmp.api.model.AccountId owner,
                                    net.nerdsmp.paper.skyblock.IslandService.Island original, World world) throws Exception {
        var sky = core.skyblock();
        var resources = sky.resources();
        String season = core.getConfig().getString("season.id", "season-1");
        String before = core.playerData().read(owner, org.bukkit.configuration.file.YamlConfiguration::saveToString);
        check(resources.effectiveStage(owner) == 0 && resources.snapshot(owner).stage() == 0,
            "legacy island inherits resource stage without migration");
        check(before.equals(core.playerData().read(owner, org.bukkit.configuration.file.YamlConfiguration::saveToString)),
            "reading resource progression does not rewrite saved player data");
        core.playerData().update(owner, yaml -> {
            String path="seasons."+season+".island-development";
            yaml.set(path+".schema",1); yaml.set(path+".xp",net.nerdsmp.paper.skyblock.IslandDevelopment.experienceForLevel(25));
            yaml.set(path+".day",java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()); yaml.set(path+".earned-today",0);
        });
        // Discard the fixture's already-read zero-XP development cache, not any live player record.
        var savedField=sky.development().getClass().getDeclaredField("saved"); savedField.setAccessible(true);
        ((Map<?,?>)savedField.get(sky.development())).remove(owner);
        Map<net.nerdsmp.api.model.Skill,net.nerdsmp.api.service.ProgressionService.SkillProgress> skills=
            new EnumMap<>(net.nerdsmp.api.model.Skill.class);
        for(var skill:net.nerdsmp.api.model.Skill.values()) skills.put(skill,new net.nerdsmp.api.service.ProgressionService.SkillProgress(
            net.nerdsmp.api.service.ProgressionService.experienceForLevel(35),35));
        var trained=new net.nerdsmp.api.service.ProgressionService.ProgressionSnapshot(skills,Set.of(),Set.of(),java.time.Instant.EPOCH);
        for(int chapter=0;chapter<3;chapter++) check(sky.islands().advance(owner,owner,chapter,trained),"original chapter " + chapter + " still advances");
        var legacy=sky.islands().owned(owner).orElseThrow();
        check(resources.effectiveStage(owner)==3,"all original chapter ores immediately grandfathered");
        check(resources.contribute(owner,owner,3,trained) && resources.effectiveStage(owner)==4,"Foundry resource milestone unlocks");
        check(resources.contribute(owner,owner,4,trained) && resources.effectiveStage(owner)==5,"Aetherworks resource milestone unlocks");
        check(legacy.equals(sky.islands().owned(owner).orElseThrow()),"new resource stages never change island layout, name, team, proofs or chapter");
        checkIslandBordersAndSpawners(owner,world);
        var fresh=new net.nerdsmp.paper.skyblock.IslandResourceProgression(core.playerData(),sky.islands(),sky.development(),season);
        check(fresh.snapshot(owner).equals(resources.snapshot(owner)),"advanced stage and certifications survive a fresh persistence load");
        checkIslandPondResources(owner, original, world);
        var machines=sky.processingMachines();
        for(var kind:net.nerdsmp.paper.skyblock.machine.MachineKind.values()) {
            ItemStack item=machines.item(kind);
            check(machines.kind(item)==kind && item.getType()==Material.DROPPER && item.getItemMeta().getItemModel()==null,
                "authentic vanilla-texture machine item " + kind);
            check(Bukkit.getRecipe(new NamespacedKey(core,"island_machine_"+kind.id()))!=null,"native machine crafting recipe " + kind);
        }
        int x=net.nerdsmp.paper.skyblock.IslandGrid.blockX(original.slot())+5;
        int z=net.nerdsmp.paper.skyblock.IslandGrid.blockZ(original.slot())+5;
        org.bukkit.block.Block block=world.getBlockAt(x,net.nerdsmp.paper.skyblock.IslandGrid.SURFACE_Y+2,z);
        block.setType(Material.DROPPER,false);
        var entry=machines.index().add(owner,owner,new net.nerdsmp.paper.skyblock.machine.MachineIndex.Position(
            world.getUID(),block.getX(),block.getY(),block.getZ()),net.nerdsmp.paper.skyblock.machine.MachineKind.CRUSHER);
        var tile=(org.bukkit.block.Dropper)block.getState();
        tile.getPersistentDataContainer().set(new NamespacedKey(core,"island_machine_kind"),PersistentDataType.STRING,entry.kind().id());
        tile.getPersistentDataContainer().set(new NamespacedKey(core,"island_machine_schema"),PersistentDataType.INTEGER,1);
        tile.getPersistentDataContainer().set(new NamespacedKey(core,"island_machine_instance"),PersistentDataType.STRING,entry.id().toString());
        check(tile.update(false,false),"machine block marker is written");
        var storage=((org.bukkit.block.Dropper)block.getState()).getInventory();
        storage.setItem(0,new ItemStack(Material.COBBLESTONE,16)); storage.setItem(3,new ItemStack(Material.COAL,2));
        var process=machines.getClass().getDeclaredMethod("process"); process.setAccessible(true); process.invoke(machines);
        check(storage.getItem(0).getAmount()==8 && storage.getItem(3).getAmount()==1
            && storage.getItem(6).getType()==Material.GRAVEL && storage.getItem(6).getAmount()==8,
            "real machine consumes one bounded batch and puts results in native output row");
        process.invoke(machines);
        check(storage.getItem(0).getAmount()==8,"machine cooldown prevents immediate second processing");
        storage=checkMachineViews(machines,storage,owner,block);
        checkMachineHopperTransfers(machines,storage);
        var dispense=new org.bukkit.event.block.BlockDispenseEvent(block,new ItemStack(Material.GRAVEL),new org.bukkit.util.Vector());
        machines.onDispense(dispense); check(dispense.isCancelled(),"redstone cannot dispense machine contents");
        var piston=new org.bukkit.event.block.BlockPistonExtendEvent(block.getRelative(org.bukkit.block.BlockFace.WEST),List.of(block),org.bukkit.block.BlockFace.EAST);
        machines.onPiston(piston); check(piston.isCancelled(),"pistons cannot move processing machine identity");
        var blast=new org.bukkit.event.block.BlockExplodeEvent(block,block.getState(),new ArrayList<>(List.of(block)),1F,org.bukkit.ExplosionResult.DESTROY);
        machines.onExplosion(blast); check(!blast.blockList().contains(block),"explosions keep processing storage in place");
        var clockField=machines.getClass().getDeclaredField("readyAt"); clockField.setAccessible(true);
        ((Map<?,?>)clockField.get(machines)).clear();
        storage.clear(); var named=new ItemStack(Material.COBBLESTONE,8);
        named.editMeta(meta->meta.displayName(net.kyori.adventure.text.Component.text("Do not consume")));
        storage.setItem(0,named); storage.setItem(3,new ItemStack(Material.COAL)); process.invoke(machines);
        check(storage.getItem(0).equals(named) && storage.getItem(3).getAmount()==1 && storage.getItem(6)==null,
            "processing ignores named ingredients without consuming fuel");
        storage.clear(); storage.setItem(0,new ItemStack(Material.COBBLESTONE,8)); storage.setItem(3,new ItemStack(Material.COAL));
        var mismatched=(org.bukkit.block.Dropper)block.getState();
        mismatched.getPersistentDataContainer().set(new NamespacedKey(core,"island_machine_instance"),PersistentDataType.STRING,UUID.randomUUID().toString());
        mismatched.update(false,false); process.invoke(machines);
        check(storage.getItem(0).getAmount()==8 && storage.getItem(6)==null,"stale world/index identity never processes or deletes user contents");
        var reloaded=new net.nerdsmp.paper.skyblock.machine.MachineIndex(core.playerData(),sky.islands(),season);
        check(entry.equals(reloaded.at(entry.position())),"machine index survives reload independently of existing island saves");
        machines.index().remove(entry); block.setType(Material.AIR,false);
        getLogger().info("ISLAND_WORKSHOP_SMOKE_PASSED");
    }
    @SuppressWarnings("unchecked")
    private void checkIslandPondResources(net.nerdsmp.api.model.AccountId owner,
            net.nerdsmp.paper.skyblock.IslandService.Island original, World world) throws Exception {
        var fishing=core.fishing();
        var island=core.skyblock().islands().owned(owner).orElseThrow();
        int x=net.nerdsmp.paper.skyblock.IslandGrid.blockX(original.slot())+10;
        int z=net.nerdsmp.paper.skyblock.IslandGrid.blockZ(original.slot())+10;
        int y=net.nerdsmp.paper.skyblock.IslandGrid.SURFACE_Y+6;
        Map<org.bukkit.block.Block,org.bukkit.block.data.BlockData> terrain=new LinkedHashMap<>();
        for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
            world.getChunkAt((x+dx)>>4,(z+dz)>>4).addPluginChunkTicket(this);
            for(int dy=-2;dy<=1;dy++) {
                var block=world.getBlockAt(x+dx,y+dy,z+dz); terrain.put(block,block.getBlockData().clone());
                block.setType(dy==-2?Material.STONE:dy<=0?Material.WATER:Material.AIR,false);
            }
        }
        var currentView=new java.util.concurrent.atomic.AtomicReference<org.bukkit.inventory.InventoryView>();
        UUID playerId=UUID.randomUUID(); Location point=new Location(world,x+.5,y+.1,z+.5);
        Player actor=pondPlayer(playerId,point,currentView);
        core.sessions().join(playerId,owner);
        String dataBefore=core.playerData().read(owner,org.bukkit.configuration.file.YamlConfiguration::saveToString);
        var access=pondAccess(world,island,false);
        var context=fishing.getClass().getDeclaredMethod("catchContext",Player.class,Location.class);context.setAccessible(true);
        var selected=fishing.getClass().getDeclaredMethod("pondHabitat",net.nerdsmp.api.model.AccountId.class);selected.setAccessible(true);
        String habitatPath="seasons."+core.getConfig().getString("season.id","season-1")+".island-fishing.pond-habitat";
        Object originalHabitat=core.playerData().read(owner,yaml->yaml.get(habitatPath));
        try {
            check(net.nerdsmp.paper.activity.IslandFishingPolicy.unlocked(core.skyblock().resources().effectiveStage(owner)).size()==5,
                "earned Aetherworks island exposes all five pond habitats");
            check(selected.invoke(fishing,owner)==net.nerdsmp.paper.activity.IslandFishingPolicy.PondHabitat.FRESHWATER
                && dataBefore.equals(core.playerData().read(owner,org.bukkit.configuration.file.YamlConfiguration::saveToString)),
                "legacy island pond defaults to freshwater without rewriting progress");
            check(net.nerdsmp.paper.activity.IslandFishingPolicy.inspect(access,x,y,z)
                ==net.nerdsmp.paper.activity.IslandFishingPolicy.PondStatus.VALID,
                "a real Paper catch location qualifies without a surrounding terrain scan");
            Object eligible=context.invoke(fishing,actor,point);
            var eligibleAccessor=eligible.getClass().getDeclaredMethod("eligible");eligibleAccessor.setAccessible(true);
            var ownerAccessor=eligible.getClass().getDeclaredMethod("islandOwner");ownerAccessor.setAccessible(true);
            check((boolean)eligibleAccessor.invoke(eligible) && owner.equals(ownerAccessor.invoke(eligible)),
                "production catch context attributes the bobber pond to its canonical island owner");
            for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
                world.getBlockAt(x+dx,y-1,z+dz).setType(Material.STONE,false);
                if(dx!=0||dz!=0) world.getBlockAt(x+dx,y,z+dz).setType(Material.STONE,false);
            }
            check((boolean)eligibleAccessor.invoke(context.invoke(fishing,actor,point)),
                "production catch context accepts a one-block-wide, one-block-deep pool");
            checkPondCatchPreparation(fishing,actor,point,"one-block shallow pool");
            var flowing=(org.bukkit.block.data.Levelled)Material.WATER.createBlockData();flowing.setLevel(1);
            world.getBlockAt(x,y,z).setBlockData(flowing,false);
            check((boolean)eligibleAccessor.invoke(context.invoke(fishing,actor,point)),
                "production catch context accepts flowing water when a valid catch event occurs");
            checkPondCatchPreparation(fishing,actor,point,"flowing-water catch");
            world.getBlockAt(x,y,z).setType(Material.WATER,false);
            world.getBlockAt(x,y+1,z).setType(Material.STONE,false);
            check((boolean)eligibleAccessor.invoke(context.invoke(fishing,actor,point)),
                "production catch context does not impose an extra overhead-clearance requirement");
            checkPondCatchPreparation(fishing,actor,point,"covered-pool catch");
            world.getBlockAt(x,y+1,z).setType(Material.LILY_PAD,false);
            check(net.nerdsmp.paper.activity.IslandFishingPolicy.inspect(access,x,y,z)
                ==net.nerdsmp.paper.activity.IslandFishingPolicy.PondStatus.VALID,
                "native lily pads remain compatible with island fishing");
            world.getBlockAt(x,y+1,z).setType(Material.AIR,false);
            int easternEdge=(net.nerdsmp.paper.skyblock.IslandGrid.chunkX(island.slot())+island.tier()+2)*16-1;
            // Keep the inside column loaded so this fixture isolates the border check rather than UNLOADED.
            world.getChunkAt(easternEdge>>4,z>>4).addPluginChunkTicket(this);
            var borderCheck=net.nerdsmp.paper.activity.IslandFishingPolicy.inspect(access,easternEdge,y,z);
            check(borderCheck==net.nerdsmp.paper.activity.IslandFishingPolicy.PondStatus.VALID
                &&(boolean)eligibleAccessor.invoke(context.invoke(fishing,actor,new Location(world,easternEdge+.5,y,z+.5))),
                "bobbers at the last claimed block remain eligible without a water-footprint border buffer: "+borderCheck);
            check(net.nerdsmp.paper.activity.IslandFishingPolicy.inspect(pondAccess(world,island,true),x,y,z)
                ==net.nerdsmp.paper.activity.IslandFishingPolicy.PondStatus.UNLOADED,
                "an unloaded bobber chunk is still rejected without loading it");
            var outsider=net.nerdsmp.api.model.AccountId.random();core.sessions().join(playerId,outsider);
            check(!(boolean)eligibleAccessor.invoke(context.invoke(fishing,actor,point)),
                "canonical visitors cannot fish for another island's rewards");
            core.skyblock().islands().invite(owner,outsider,java.time.Instant.now());
            core.skyblock().islands().accept(outsider,owner,java.time.Instant.now());
            check((boolean)eligibleAccessor.invoke(context.invoke(fishing,actor,point)),
                "accepted co-op members can fish the owner's pond");
            core.skyblock().islands().remove(owner,owner,outsider);
            check(!(boolean)eligibleAccessor.invoke(context.invoke(fishing,actor,point)),
                "removing co-op membership immediately revokes pond reward eligibility");
            core.sessions().join(playerId,owner);
            check(!(boolean)eligibleAccessor.invoke(context.invoke(fishing,actor,new Location(world,easternEdge+4.5,y,z+.5))),
                "casting outside the claimed island cannot borrow its habitat or rewards");
            core.playerData().update(owner,yaml->yaml.set(habitatPath,"ocean"));
            var skillField=core.getClass().getDeclaredField("skillListener");skillField.setAccessible(true);
            var freshFishing=new net.nerdsmp.paper.activity.FishingService(core,(net.nerdsmp.paper.listener.SkillListener)skillField.get(core));
            check(selected.invoke(freshFishing,owner)==net.nerdsmp.paper.activity.IslandFishingPolicy.PondHabitat.OCEAN,
                "earned selected pond habitat survives a fresh fishing service load");
            core.playerData().update(owner,yaml->yaml.set(habitatPath,originalHabitat));
            fishing.open(actor);
            var view=currentView.get();
            check(view!=null && view.getTopInventory().getItem(46).getType()==Material.WATER_BUCKET,
                "actual fishing journal renders the island pond habitat control");
            String pondLore=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                view.getTopInventory().getItem(46).getItemMeta().lore().get(1));
            check(pondLore.contains("Fish anywhere") && view.getTopInventory().getItem(46).getItemMeta().lore().stream()
                .map(line->net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line))
                .noneMatch(line->line.contains("5 x 5")||line.contains("2 blocks deep")),
                "actual journal allows any catchable spot and no longer advertises the removed pond dimensions");
            for(var click:List.of(org.bukkit.event.inventory.ClickType.NUMBER_KEY,org.bukkit.event.inventory.ClickType.SHIFT_LEFT,
                    org.bukkit.event.inventory.ClickType.DOUBLE_CLICK)) {
                var blocked=new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                    46,click,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL,0);
                fishing.onClick(blocked);check(blocked.isCancelled(),"pond journal cannot extract icons with "+click);
            }
            var drag=new org.bukkit.event.inventory.InventoryDragEvent(view,new ItemStack(Material.STONE),new ItemStack(Material.STONE),false,
                Map.of(46,new ItemStack(Material.STONE)));
            fishing.onDrag(drag);check(drag.isCancelled(),"pond habitat journal remains read-only under drags");
            var change=fishing.getClass().getDeclaredMethod("changePondHabitat",Player.class,view.getTopInventory().getHolder().getClass());
            change.setAccessible(true);
            String beforeForged=core.playerData().read(owner,org.bukkit.configuration.file.YamlConfiguration::saveToString);
            change.invoke(fishing,actor,view.getTopInventory().getHolder());
            check(beforeForged.equals(core.playerData().read(owner,org.bukkit.configuration.file.YamlConfiguration::saveToString)),
                "nonregistered player proxies cannot mutate another account's pond preference");
            checkCancelledPondCatch(fishing,actor,owner,point,eligible);
            getLogger().info("ISLAND_FISHING_SMOKE_PASSED");
        } finally {
            core.sessions().quit(playerId);
            core.playerData().update(owner,yaml->yaml.set(habitatPath,originalHabitat));
            terrain.forEach((block,data)->block.setBlockData(data,false));
        }
    }
    private net.nerdsmp.paper.activity.IslandFishingPolicy.PondAccess pondAccess(World world,
            net.nerdsmp.paper.skyblock.IslandService.Island island,boolean simulateUnloaded) {
        return new net.nerdsmp.paper.activity.IslandFishingPolicy.PondAccess() {
            public int minHeight() { return world.getMinHeight(); }
            public int maxHeight() { return world.getMaxHeight(); }
            public boolean loaded(int x,int z) { return !simulateUnloaded && world.isChunkLoaded(x>>4,z>>4); }
            public boolean belongsToIsland(int x,int z) {
                return net.nerdsmp.paper.skyblock.IslandGrid.contains(island.slot(),island.tier(),x>>4,z>>4);
            }
        };
    }
    @SuppressWarnings("unchecked")
    private void checkPondCatchPreparation(net.nerdsmp.paper.activity.FishingService fishing,Player actor,Location point,String label) throws Exception {
        UUID hookId=UUID.randomUUID();
        var hook=(FishHook)java.lang.reflect.Proxy.newProxyInstance(FishHook.class.getClassLoader(),new Class<?>[]{FishHook.class},
            (proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> hookId;
                case "getLocation" -> point.clone();
                default -> throw new UnsupportedOperationException("Eligible catch hook fixture: "+method.getName());
            });
        var field=fishing.getClass().getDeclaredField("preparedCatches");field.setAccessible(true);
        var pending=(Map<org.bukkit.event.player.PlayerFishEvent,Object>)field.get(fishing);
        Item caught=point.getWorld().dropItem(point,new ItemStack(Material.COD));
        var event=new org.bukkit.event.player.PlayerFishEvent(actor,caught,hook,org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH);
        try {
            fishing.onFish(event);
            check(!event.isCancelled()&&pending.containsKey(event),"eligible "+label+" prepares its real fishing reward instead of being canceled by geometry");
            event.setCancelled(true);fishing.completeCatch(event);
            check(!pending.containsKey(event),"cancelled "+label+" still discards prepared rewards safely");
            var reel=new org.bukkit.event.player.PlayerFishEvent(actor,caught,hook,org.bukkit.event.player.PlayerFishEvent.State.REEL_IN);
            fishing.onFish(reel);check(!pending.containsKey(reel),"ordinary reel-in at a "+label+" cannot manufacture a catch");
        } finally {pending.remove(event);caught.remove();}
    }
    private static Player pondPlayer(UUID id,Location location,
            java.util.concurrent.atomic.AtomicReference<org.bukkit.inventory.InventoryView> openView) {
        var inventory=(org.bukkit.inventory.PlayerInventory)java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{org.bukkit.inventory.PlayerInventory.class},(proxy,method,args)->switch(method.getName()) {
                case "getItemInMainHand","getItemInOffHand" -> new ItemStack(Material.AIR);
                case "getHelmet","getChestplate","getLeggings","getBoots" -> null;
                default -> throw new UnsupportedOperationException("Pond equipment fixture must not call "+method.getName());
            });
        return (Player)java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},
            (proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> id;
                case "getWorld" -> location.getWorld();
                case "getLocation" -> location.clone();
                case "getGameMode" -> GameMode.SURVIVAL;
                case "getInventory" -> inventory;
                case "isOnline" -> true;
                case "getOpenInventory" -> openView.get();
                case "openInventory" -> {
                    var view=editorView((Player)proxy,(org.bukkit.inventory.Inventory)args[0]);openView.set(view);yield view;
                }
                case "sendMessage","sendRichMessage","sendActionBar","playSound" -> null;
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy==args[0];
                default -> throw new UnsupportedOperationException("Pond player fixture must not call "+method.getName());
            });
    }
    @SuppressWarnings("unchecked")
    private void checkCancelledPondCatch(net.nerdsmp.paper.activity.FishingService fishing,Player actor,
            net.nerdsmp.api.model.AccountId owner,Location point,Object context) throws Exception {
        UUID hookId=UUID.randomUUID();
        var hook=(FishHook)java.lang.reflect.Proxy.newProxyInstance(FishHook.class.getClassLoader(),new Class<?>[]{FishHook.class},
            (proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> hookId;
                case "getLocation" -> point.clone();
                default -> throw new UnsupportedOperationException("Pond hook fixture must not call "+method.getName());
            });
        var rewardItem=core.items().create(new net.nerdsmp.api.model.ItemId("river_sprat"));
        Item caught=point.getWorld().dropItem(point,rewardItem);
        try {
            Class<?> rewardClass=Class.forName("net.nerdsmp.paper.activity.FishingService$CatchReward");
            var rewardConstructor=rewardClass.getDeclaredConstructors()[0];rewardConstructor.setAccessible(true);
            Object reward=rewardConstructor.newInstance(rewardItem,null);
            Class<?> preparedClass=Class.forName("net.nerdsmp.paper.activity.FishingService$PreparedCatch");
            var preparedConstructor=preparedClass.getDeclaredConstructors()[0];preparedConstructor.setAccessible(true);
            Object prepared=preparedConstructor.newInstance(owner,hookId,point.clone(),caught,
                net.nerdsmp.paper.activity.FishingRolls.Rarity.COMMON,reward,context);
            var preparedField=fishing.getClass().getDeclaredField("preparedCatches");preparedField.setAccessible(true);
            var hooksField=fishing.getClass().getDeclaredField("acceptedHooks");hooksField.setAccessible(true);
            var pending=(Map<org.bukkit.event.player.PlayerFishEvent,Object>)preparedField.get(fishing);
            Map<?,?> hooks=(Map<?,?>)hooksField.get(fishing);
            String data=core.playerData().read(owner,org.bukkit.configuration.file.YamlConfiguration::saveToString);
            var cancelled=new org.bukkit.event.player.PlayerFishEvent(actor,caught,hook,org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH);
            pending.put(cancelled,prepared);cancelled.setCancelled(true);fishing.completeCatch(cancelled);
            check(!pending.containsKey(cancelled) && !hooks.containsKey(hookId)
                && data.equals(core.playerData().read(owner,org.bukkit.configuration.file.YamlConfiguration::saveToString)),
                "cancelled prepared catches release their context without XP, job, streak, island or saved-data writes");
            var forged=new org.bukkit.event.player.PlayerFishEvent(actor,caught,hook,org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH);
            pending.put(forged,prepared);fishing.completeCatch(forged);
            var commit=fishing.getClass().getDeclaredMethod("commitCatch",org.bukkit.event.player.PlayerFishEvent.class,preparedClass);
            commit.setAccessible(true);commit.invoke(fishing,forged,prepared);
            check(!pending.containsKey(forged) && !hooks.containsKey(hookId)
                && data.equals(core.playerData().read(owner,org.bukkit.configuration.file.YamlConfiguration::saveToString)),
                "unregistered player identities cannot commit prepared catch rewards or mutate legacy progress");
            var lateCancelled=new org.bukkit.event.player.PlayerFishEvent(actor,caught,hook,
                org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH);
            pending.put(lateCancelled,prepared);fishing.completeCatch(lateCancelled);lateCancelled.setCancelled(true);
            commit.invoke(fishing,lateCancelled,prepared);
            check(!pending.containsKey(lateCancelled) && !hooks.containsKey(hookId)
                && data.equals(core.playerData().read(owner,org.bukkit.configuration.file.YamlConfiguration::saveToString)),
                "cancellation after the fishing MONITOR handler still prevents deferred catch receipts and saved rewards");
        } finally { caught.remove(); }
    }
    private void checkMachineHopperTransfers(net.nerdsmp.paper.skyblock.machine.IslandMachines machines,
                                            org.bukkit.inventory.Inventory storage) {
        org.bukkit.inventory.Inventory destination=Bukkit.createInventory(null,9);
        var extract=new org.bukkit.event.inventory.InventoryMoveItemEvent(storage,new ItemStack(Material.GRAVEL),destination,true);
        machines.onMove(extract); check(extract.isCancelled(),"hoppers cannot extract machine input or fuel");
        var insert=new org.bukkit.event.inventory.InventoryMoveItemEvent(destination,new ItemStack(Material.COAL),storage,true);
        machines.onMove(insert); check(insert.isCancelled(),"hopper insertion cannot bypass machine input-row rules");
    }
    private void checkIslandBordersAndSpawners(net.nerdsmp.api.model.AccountId owner,World world) throws Exception {
        var sky=core.skyblock();var island=sky.islands().owned(owner).orElseThrow();
        int x=net.nerdsmp.paper.skyblock.IslandGrid.blockX(island.slot());
        int z=net.nerdsmp.paper.skyblock.IslandGrid.blockZ(island.slot());
        UUID viewerId=UUID.randomUUID();
        Player viewer=(Player)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Player.class},
            (proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> viewerId;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world,x+.5,net.nerdsmp.paper.skyblock.IslandGrid.SURFACE_Y+1,z+.5);
                case "sendMessage", "sendRichMessage" -> null;
                case "isOnline" -> true;
                case "hashCode" -> viewerId.hashCode();
                case "equals" -> proxy==args[0];
                default -> throw new UnsupportedOperationException("Border fixture must not call "+method.getName());
            });
        core.sessions().join(viewerId,owner);
        String dataBefore=core.playerData().read(owner,org.bukkit.configuration.file.YamlConfiguration::saveToString);
        double globalBorder=world.getWorldBorder().getSize();
        try {
            sky.borders().set(viewer,owner,true);check(sky.borders().enabled(viewer,owner),"island border toggle is privately bound to its viewer and owner");
            check(world.getWorldBorder().getSize()==globalBorder,"island outline never changes the real world border");
            check(dataBefore.equals(core.playerData().read(owner,org.bukkit.configuration.file.YamlConfiguration::saveToString)),"border viewing never rewrites island/player progress");
            sky.borders().forget(viewer);check(!sky.borders().enabled(viewer,owner),"border teardown removes the private viewer");
        } finally {core.sessions().quit(viewerId);}
        var bounds=net.nerdsmp.paper.skyblock.IslandBoundary.bounds(island.slot(),island.tier());
        check(bounds.width()==island.width() && net.nerdsmp.paper.skyblock.IslandBoundary.sample(bounds,bounds.minX()+1,100,bounds.minZ()+1).size()<=64,
            "runtime border geometry matches actual protected land and its particle budget");
        var automation=sky.automation();
        var create=automation.getClass().getDeclaredMethod("spawnerItem",org.bukkit.entity.EntityType.class,int.class);create.setAccessible(true);
        check(net.nerdsmp.paper.skyblock.IslandSpawnerCatalog.all().size()==13,"expanded spawner catalog contains thirteen species");
        for(var definition:net.nerdsmp.paper.skyblock.IslandSpawnerCatalog.all()) {
            var recipe=(org.bukkit.inventory.ShapedRecipe)Bukkit.getRecipe(new NamespacedKey(core,definition.recipeKey()));
            boolean correct=recipe!=null && recipe.getShape().length==3;
            if(correct) for(int row=0;row<3;row++) {
                String line=recipe.getShape()[row];if(line.length()!=3) {correct=false;break;}
                for(int column=0;column<3;column++) {
                    Material expected=row==1 && column==1?Material.DIAMOND:row!=1 && column!=1?Material.IRON_BLOCK:definition.ingredient();
                    ItemStack actual=recipe.getIngredientMap().get(line.charAt(column));
                    if(actual==null || actual.getType()!=expected) correct=false;
                }
            }
            check(correct,"native recipe preserves cost and species ingredient: "+definition.title());
            ItemStack item=(ItemStack)create.invoke(automation,definition.type(),1);
            check(item.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta meta
                && meta.getBlockState() instanceof org.bukkit.block.CreatureSpawner state && state.getSpawnedType()==definition.type(),
                "crafted spawner embeds correct native body: "+definition.title());
            check(definition.legacy() || !net.nerdsmp.paper.skyblock.IslandSpawnerCatalog.unlocked(definition.type(),definition.minimumResourceStage()-1),
                "new spawner resource gate: "+definition.title());
        }
        var recipe=(org.bukkit.inventory.CraftingRecipe)Bukkit.getRecipe(new NamespacedKey(core,"island_spawner_creeper"));
        var crafter=world.getBlockAt(x+5,net.nerdsmp.paper.skyblock.IslandGrid.SURFACE_Y+6,z+6);crafter.setType(Material.CRAFTER,false);
        var automatic=new org.bukkit.event.block.CrafterCraftEvent(crafter,recipe,recipe.getResult());
        automation.onAutomatedCraft(automatic);check(automatic.isCancelled(),"automated crafting cannot bypass spawner ingredient safeguards");
        crafter.setType(Material.AIR,false);
        Location location=new Location(world,x+3.5,net.nerdsmp.paper.skyblock.IslandGrid.SURFACE_Y+7,z+6.5);
        var normal=world.spawn(location,Creeper.class,CreatureSpawnEvent.SpawnReason.CUSTOM,false,entity->entity.setCanPickupItems(false));
        var charged=world.spawn(location.clone().add(1,0,0),Creeper.class,CreatureSpawnEvent.SpawnReason.CUSTOM,false,entity->{entity.setCanPickupItems(false);entity.setPowered(true);});
        automation.queueNatural(new CreatureSpawnEvent(normal,CreatureSpawnEvent.SpawnReason.NATURAL));
        automation.queueNatural(new CreatureSpawnEvent(charged,CreatureSpawnEvent.SpawnReason.NATURAL));automation.processMobStacks();
        check(normal.isValid() && charged.isValid() && automation.mobCount(normal)==1 && automation.mobCount(charged)==1,
            "charged creepers never merge into ordinary island stacks");
        var carrier=world.spawn(location.clone().add(2,0,0),Enderman.class,CreatureSpawnEvent.SpawnReason.CUSTOM,false,
            entity->{entity.setCanPickupItems(false);entity.setCarriedBlock(Material.STONE.createBlockData());});
        automation.queueNatural(new CreatureSpawnEvent(carrier,CreatureSpawnEvent.SpawnReason.NATURAL));automation.processMobStacks();
        check(carrier.isValid() && automation.mobCount(carrier)==1,"block-carrying endermen stay individual");
        normal.remove();charged.remove();carrier.remove();
        getLogger().info("ISLAND_BORDERS_SPAWNERS_SMOKE_PASSED");
    }
    private org.bukkit.inventory.Inventory checkMachineViews(net.nerdsmp.paper.skyblock.machine.IslandMachines machines,
                                   org.bukkit.inventory.Inventory storage, net.nerdsmp.api.model.AccountId owner,
                                   org.bukkit.block.Block block) {
        UUID playerId=UUID.randomUUID();
        ItemStack[] cursor={new ItemStack(Material.COAL)};
        Location[] location={block.getLocation().add(.5,0,2)};
        var openView=new java.util.concurrent.atomic.AtomicReference<org.bukkit.inventory.InventoryView>();
        Player actor=(Player)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Player.class},
            (proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> playerId;
                case "getName" -> "MachineSmoke";
                case "getWorld" -> location[0].getWorld();
                case "getLocation" -> location[0].clone();
                case "isOnline" -> true;
                case "getOpenInventory" -> openView.get();
                case "getItemOnCursor" -> cursor[0];
                case "sendMessage", "sendRichMessage" -> null;
                case "hashCode" -> playerId.hashCode();
                case "equals" -> proxy==args[0];
                default -> throw new UnsupportedOperationException("Machine fixture must not call "+method.getName());
            });
        org.bukkit.inventory.Inventory bottom=Bukkit.createInventory(null,36);
        var view=new org.bukkit.inventory.InventoryView() {
            public org.bukkit.inventory.Inventory getTopInventory() { return storage; }
            public org.bukkit.inventory.Inventory getBottomInventory() { return bottom; }
            public org.bukkit.entity.HumanEntity getPlayer() { return actor; }
            public org.bukkit.event.inventory.InventoryType getType() { return org.bukkit.event.inventory.InventoryType.DROPPER; }
            public org.bukkit.inventory.MenuType getMenuType() { return org.bukkit.inventory.MenuType.GENERIC_3X3; }
            public org.bukkit.inventory.Inventory getInventory(int raw) { return raw<0?null:raw<9?storage:raw<45?bottom:null; }
            public int convertSlot(int raw) { return raw<9?raw:raw-9; }
            public ItemStack getItem(int raw) { var inv=getInventory(raw);return inv==null?null:inv.getItem(convertSlot(raw)); }
            public void setItem(int raw,ItemStack item) { var inv=getInventory(raw);if(inv!=null) inv.setItem(convertSlot(raw),item); }
            public ItemStack getCursor() { return cursor[0]; }
            public void setCursor(ItemStack item) { cursor[0]=item; }
            public org.bukkit.event.inventory.InventoryType.SlotType getSlotType(int raw) { return org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER; }
            public int countSlots() { return 45; }
            public boolean setProperty(org.bukkit.inventory.InventoryView.Property property,int value) { return false; }
            public void open() { }
            public void close() { }
            public String getTitle() { return "Machine smoke"; }
            public String getOriginalTitle() { return "Machine smoke"; }
            public void setTitle(String title) { }
        };
        openView.set(view); core.sessions().join(playerId,owner);
        try {
            var opening=new org.bukkit.event.inventory.InventoryOpenEvent(view); machines.onOpen(opening);
            check(!opening.isCancelled(),"co-op can open authenticated native machine storage");
            var output=new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                6,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
            machines.onClick(output); check(output.isCancelled(),"native output row rejects inserted items");
            cursor[0]=new ItemStack(Material.COBBLESTONE);
            var input=new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                0,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
            machines.onClick(input); check(!input.isCancelled(),"authenticated input row accepts plain recipe material");
            var drag=new org.bukkit.event.inventory.InventoryDragEvent(view,new ItemStack(Material.COBBLESTONE),new ItemStack(Material.COBBLESTONE),false,
                Map.of(6,new ItemStack(Material.COBBLESTONE)));
            machines.onDrag(drag); check(drag.isCancelled(),"dragging cannot place ingredients into the output row");
            location[0]=location[0].clone().add(20,0,0);
            var distant=new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                0,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
            machines.onClick(distant); check(distant.isCancelled(),"machine views cannot be used after moving out of range");
            location[0]=block.getLocation().add(.5,0,2);
            org.bukkit.block.Dropper tile=(org.bukkit.block.Dropper)block.getState();
            var key=new NamespacedKey(core,"island_machine_instance"); String original=tile.getPersistentDataContainer().get(key,PersistentDataType.STRING);
            tile.getPersistentDataContainer().set(key,PersistentDataType.STRING,UUID.randomUUID().toString()); tile.update(false,false);
            var stale=new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                0,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
            machines.onClick(stale); check(stale.isCancelled(),"replaced tile identity invalidates old native container views");
            var restored=(org.bukkit.block.Dropper)block.getState();
            restored.getPersistentDataContainer().set(key,PersistentDataType.STRING,original);restored.update(false,false);
            var preserved=storage.getContents();
            block.setType(Material.AIR,false); block.setType(Material.DROPPER,false);
            var replacement=(org.bukkit.block.Dropper)block.getState();
            replacement.getPersistentDataContainer().set(new NamespacedKey(core,"island_machine_kind"),PersistentDataType.STRING,"crusher");
            replacement.getPersistentDataContainer().set(new NamespacedKey(core,"island_machine_schema"),PersistentDataType.INTEGER,1);
            replacement.getPersistentDataContainer().set(key,PersistentDataType.STRING,original);replacement.update(false,false);
            ((org.bukkit.block.Dropper)block.getState()).getInventory().setContents(preserved);
            var detached=new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                0,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
            machines.onClick(detached); check(detached.isCancelled(),"same-token world replacement cannot reuse a detached backing inventory");
            machines.onClose(new org.bukkit.event.inventory.InventoryCloseEvent(view));
            var closed=new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                0,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
            machines.onClick(closed); check(closed.isCancelled(),"closed view does not inherit a later machine session");
        } finally { core.sessions().quit(playerId); }
        var recipe=Bukkit.getRecipe(new NamespacedKey(core,"island_machine_crusher"));
        var crafter=block.getRelative(org.bukkit.block.BlockFace.EAST);crafter.setType(Material.CRAFTER,false);
        var automatic=new org.bukkit.event.block.CrafterCraftEvent(crafter,(org.bukkit.inventory.CraftingRecipe)recipe,recipe.getResult());
        machines.onCrafter(automatic); check(automatic.isCancelled(),"automatic crafters cannot bypass machine ingredient safeguards");
        crafter.setType(Material.AIR,false);
        return ((org.bukkit.block.Dropper)block.getState()).getInventory();
    }
    private void checkIslandMobStacks(net.nerdsmp.paper.skyblock.IslandService.Island island,World world,Runnable complete) throws Exception {
        var automation=core.skyblock().automation();
        int x=net.nerdsmp.paper.skyblock.IslandGrid.blockX(island.slot())-3;
        int z=net.nerdsmp.paper.skyblock.IslandGrid.blockZ(island.slot())-3;
        int y=net.nerdsmp.paper.skyblock.IslandGrid.SURFACE_Y+5;
        for(int dx=-1;dx<=5;dx++) for(int dz=-1;dz<=5;dz++) {
            world.getBlockAt(x+dx,y-1,z+dz).setType(Material.STONE,false);
            for(int dy=0;dy<=3;dy++) world.getBlockAt(x+dx,y+dy,z+dz).setType(Material.AIR,false);
        }
        var first=stackSpider(world,new Location(world,x+.5,y,z+.5));
        var second=stackSpider(world,new Location(world,x+2.5,y,z+.5));
        automation.queueNatural(new CreatureSpawnEvent(first,CreatureSpawnEvent.SpawnReason.NATURAL));
        automation.queueNatural(new CreatureSpawnEvent(second,CreatureSpawnEvent.SpawnReason.NATURAL));
        drainMobStacks();
        check(first.isValid()!=second.isValid(),"compatible ordinary island mobs share one body");
        LivingEntity stack=first.isValid()?first:second;
        check(automation.mobCount(stack)==2 && stack.isPersistent() && !stack.getRemoveWhenFarAway()
            && !stack.getCanPickupItems(),"natural island stack preserves two members and cannot despawn or steal gear");
        String label=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(core.mobs().displayName(stack));
        check(label.contains("×2"),"existing personal health display receives the island stack count");
        var changed=stackSpider(world,new Location(world,x+2.5,y,z+.5));
        changed.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(
            changed.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getBaseValue()+1);
        automation.queueNatural(new CreatureSpawnEvent(changed,CreatureSpawnEvent.SpawnReason.NATURAL)); drainMobStacks();
        check(changed.isValid() && automation.mobCount(stack)==2 && automation.mobCount(changed)==1,
            "mobs with different combat stats cannot merge"); changed.remove();
        var named=stackSpider(world,new Location(world,x+2.5,y,z+.5));
        named.customName(net.kyori.adventure.text.Component.text("Keep me"));
        automation.queueNatural(new CreatureSpawnEvent(named,CreatureSpawnEvent.SpawnReason.NATURAL)); drainMobStacks();
        check(named.isValid() && automation.mobCount(stack)==2,"named mobs stay individual"); named.remove();
        var inert=stackSpider(world,new Location(world,x+2.5,y,z+.5)); inert.setAI(false);
        automation.queueNatural(new CreatureSpawnEvent(inert,CreatureSpawnEvent.SpawnReason.NATURAL)); drainMobStacks();
        check(inert.isValid() && automation.mobCount(stack)==2,"mobs with disabled AI stay individual"); inert.remove();
        var canceled=stackSpider(world,new Location(world,x+2.5,y,z+.5));
        var canceledSpawn=new CreatureSpawnEvent(canceled,CreatureSpawnEvent.SpawnReason.NATURAL); canceledSpawn.setCancelled(true);
        automation.queueNatural(canceledSpawn); drainMobStacks();
        check(canceled.isValid() && automation.mobCount(stack)==2,"canceled natural spawn never contributes a stack member"); canceled.remove();
        var staffMob=stackSpider(world,new Location(world,x+2.5,y,z+.5));
        automation.queueLoaded(new org.bukkit.event.world.ChunkLoadEvent(staffMob.getChunk(),false)); drainMobStacks();
        check(staffMob.isValid() && automation.mobCount(stack)==2 && !staffMob.getPersistentDataContainer()
            .has(new NamespacedKey(core,"island_mob_source")),"loaded staff-created mobs are not silently absorbed into island stacks"); staffMob.remove();
        var cow=world.spawn(new Location(world,x+2.5,y,z+2.5),Cow.class,CreatureSpawnEvent.SpawnReason.CUSTOM,false,entity->{});
        automation.queueNatural(new CreatureSpawnEvent(cow,CreatureSpawnEvent.SpawnReason.NATURAL)); drainMobStacks();
        check(cow.isValid() && automation.mobCount(stack)==2,"livestock remains separate for breeding and other interactions"); cow.remove();
        var incoming=stackSpider(world,new Location(world,x+2.5,y,z+.5));
        automation.queueNatural(new CreatureSpawnEvent(incoming,CreatureSpawnEvent.SpawnReason.NATURAL));
        automation.queueLoaded(new org.bukkit.event.world.ChunkLoadEvent(stack.getChunk(),false));
        drainMobStacks();
        check(!incoming.isValid() && automation.mobCount(stack)==3,
            "a queued saved stack retains newly merged members instead of restoring a stale count");
        var malformed=stackSpider(world,new Location(world,x+2.5,y,z+.5));
        malformed.getPersistentDataContainer().set(new NamespacedKey(core,"island_mob_count"),PersistentDataType.STRING,"bad");
        automation.queueNatural(new CreatureSpawnEvent(malformed,CreatureSpawnEvent.SpawnReason.NATURAL)); drainMobStacks();
        check(malformed.isValid() && automation.mobCount(malformed)==1 && automation.mobCount(stack)==3
            && !net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(core.mobs().displayName(malformed)).contains("×"),
            "malformed stack metadata fails closed without breaking personal health labels"); malformed.remove();
        var profiles=core.rewardDrops().profiles();
        var profile=new net.nerdsmp.paper.loot.profile.LootProfileDefinition("stack_rewards",
            net.nerdsmp.paper.loot.profile.LootProfileDefinition.Mode.REPLACE,List.of(
                new net.nerdsmp.paper.loot.profile.LootProfileDefinition.Entry("bones",
                    net.nerdsmp.paper.loot.profile.LootProfileDefinition.Kind.MATERIAL,"BONE",1,2,2,false,0),
                new net.nerdsmp.paper.loot.profile.LootProfileDefinition.Entry("purse",
                    net.nerdsmp.paper.loot.profile.LootProfileDefinition.Kind.MARKS,"marks",1,5,5,false,0)));
        profiles.put(profile); profiles.assignMob("vanilla:spider",profile.id());
        var memberLoot=lootDeath(withKiller(stack,areaPlayer(UUID.randomUUID(),world,new boolean[]{false})));
        core.rewardDrops().onDeath(memberLoot);
        check(memberLoot.getDrops().size()==2 && memberLoot.getDrops().stream().filter(item->item.getType()==Material.BONE)
            .mapToInt(ItemStack::getAmount).sum()==2,"one represented member rolls its table once, not once per saved stack size");
        profiles.unassignMob("vanilla:spider"); profiles.remove(profile.id());
        int memberLevel=core.mobs().level(stack);
        double memberHealth=stack.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue();
        watchedStack=stack.getUniqueId(); watchedStackDeaths=0;
        stack.setHealth(0);
        laterDig(()-> {
            var source=new NamespacedKey(core,"island_mob_source");
            List<LivingEntity> survivors=world.getLivingEntities().stream().filter(entity->entity.getType()==EntityType.SPIDER
                && island.id().toString().equals(entity.getPersistentDataContainer().get(source,PersistentDataType.STRING))
                && !entity.getUniqueId().equals(watchedStack) && !entity.isDead()).toList();
            check(watchedStackDeaths==1 && survivors.size()==1 && automation.mobCount(survivors.getFirst())==2,
                "actual Paper death fires once and removes exactly one represented member");
            var successor=survivors.getFirst();
            check(core.mobs().level(successor)==memberLevel
                && successor.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue()==memberHealth
                && successor.getHealth()==memberHealth && successor.isPersistent() && !successor.getCanPickupItems(),
                "next stack member retains its level, health and safe persistence policy");
            successor.remove(); watchedStack=null; complete.run();
        });
    }
    private void drainMobStacks() {
        // Each call keeps the real production budgets; the fixture allows paced work to settle.
        for(int pass=0;pass<16;pass++) core.skyblock().automation().processMobStacks();
    }
    private Spider stackSpider(World world,Location position) {
        return world.spawn(position,Spider.class,CreatureSpawnEvent.SpawnReason.CUSTOM,false,entity->entity.setCanPickupItems(false));
    }
    private void cycleGenerator(World world,int x,int y,int z,int cycle) {
        try {
            if(cycle>0) {
                Material result=world.getBlockAt(x+1,y,z).getType();
                check(net.nerdsmp.paper.skyblock.GeneratorLoot.resourceTable(5).containsKey(result),"advanced generator reforms on cycle "+cycle+" ("+result+")");
                check(world.getBlockAt(x+2,y,z).getType()==Material.LAVA && ((org.bukkit.block.data.Levelled)world.getBlockAt(x+2,y,z).getBlockData()).getLevel()==0,"lava source survives cycle "+cycle);
                check(world.getBlockAt(x-1,y,z).getType()==Material.WATER && ((org.bukkit.block.data.Levelled)world.getBlockAt(x-1,y,z).getBlockData()).getLevel()==0,"water source survives cycle "+cycle);
                check(((org.bukkit.block.data.Levelled)world.getBlockAt(x,y,z).getBlockData()).getLevel()!=0,"outlet stays non-source on cycle "+cycle);
            }
            if(cycle==3) {
                Files.writeString(Path.of("zone-smoke-result.txt"),"PASS\n");
                getLogger().info("ZONE_SMOKE_COMPLETE PASS"); Bukkit.shutdown(); return;
            }
            world.getBlockAt(x+1,y,z).setType(Material.AIR,true);
            Bukkit.getScheduler().runTaskLater(this,()->cycleGenerator(world,x,y,z,cycle+1),80);
        } catch(Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE,"ZONE_SMOKE_FAILED",failure);
            try { Files.writeString(Path.of("zone-smoke-result.txt"),"FAIL: "+failure+"\n"); } catch(Exception ignored) { }
            Bukkit.shutdown();
        }
    }
    private void check(boolean condition,String label) {
        if(!condition) throw new AssertionError(label);
        getLogger().info("ZONE_SMOKE_PASS "+label);
    }
}
