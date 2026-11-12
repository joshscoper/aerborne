package net.nerdsmp.probe;

import net.nerdsmp.api.model.*;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.death.*;
import net.nerdsmp.paper.item.*;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.nio.file.*;
import java.util.*;

/** Explicitly destructive disposable fixture: safe platforms and seeded inventory, never production. */
public final class SpiritDeathProbe extends JavaPlugin implements Listener {
    private NerdSmpPlugin core;private Player player,watcher;private AccountId account;private World world;private int waits,phase;private final List<String> checks=new ArrayList<>();private final Map<String,Object> screens=new LinkedHashMap<>();
    private static final PlainTextComponentSerializer PLAIN=PlainTextComponentSerializer.plainText();
    private static final int Y=160;
    private LivingEntity measuredTarget;
    private Location sanctuary(){return new Location(world,780.5,Y,-50.5);}
    private Location body(){return new Location(world,810.5,Y,-50.5);}
    @Override public void onEnable(){phase=Integer.getInteger("nerdsmp.spiritDeathProbe",0);if(phase<1||phase>2||!Bukkit.getIp().equals("127.0.0.1"))throw new IllegalStateException("Disposable loopback fixture only");core=(NerdSmpPlugin)Bukkit.getPluginManager().getPlugin("NerdSMP");Bukkit.getPluginManager().registerEvents(this,this);later(100,this::ready);}
    private void ready()throws Exception{
        player=Bukkit.getPlayerExact("D_Player");watcher=Bukkit.getPlayerExact("D_Watcher");world=Bukkit.getWorld("MMORPG");
        if(++waits>240)throw new AssertionError("Client deadline");if(player==null||watcher==null||core.sessions().account(player.getUniqueId()).isEmpty()||core.sessions().account(watcher.getUniqueId()).isEmpty()){later(20,this::ready);return;}
        account=core.sessions().account(player.getUniqueId()).orElseThrow();watcher.setInvulnerable(true);watcher.teleport(sanctuary().clone().add(0,0,4));
        watcher.addAttachment(this,"nerdsmp.admin",true);check(watcher.hasPermission("nerdsmp.death.admin"),"Existing admin permission inherits spirit recovery controls");
        if(phase==2){restartRecovered();return;}
        // Controlled walkable surface keeps this focused on lifecycle behavior rather than map pathfinding.
        for(int x=775;x<=817;x++)for(int z=-57;z<=-44;z++){world.getBlockAt(x,Y-1,z).setType(Material.STONE);for(int y=Y;y<Y+4;y++)world.getBlockAt(x,y,z).setType(Material.AIR);}
        core.spirits().sanctuaries().put(new SanctuaryStore.Sanctuary("probe","Test Sanctuary",SpiritService.point(sanctuary())));
        world.setGameRule(GameRules.KEEP_INVENTORY,false);player.setGameMode(GameMode.SURVIVAL);player.setInvulnerable(false);player.setAllowFlight(true);player.setFlying(false);player.teleport(body());player.closeInventory();player.getInventory().clear();player.setItemOnCursor(null);
        ItemStack helmet=new ItemStack(Material.IRON_HELMET);helmet.addUnsafeEnchantment(Enchantment.VANISHING_CURSE,1);var meta=helmet.getItemMeta();((Damageable)meta).setDamage(12);meta.itemName(net.kyori.adventure.text.Component.text("Death Test Helm"));helmet.setItemMeta(meta);player.getInventory().setHelmet(helmet);
        ItemStack sword=new ItemStack(Material.DIAMOND_SWORD);sword.addUnsafeEnchantment(Enchantment.SHARPNESS,2);player.getInventory().setItem(0,sword);player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));player.getInventory().setItem(9,core.items().create(new ItemId("copper_band")));player.getInventory().setItem(10,new ItemStack(Material.DIAMOND,13));player.getInventory().setHeldItemSlot(0);player.setCompassTarget(sanctuary().clone().add(2,0,1));player.setLevel(12);player.setTotalExperience(777);
        player.sendMessage("D_CLICK 9 0 1");later(15,()->{
            check(core.equipment().equipped(player,CharacterEquipmentSlot.RING_1)!=null,"Native E Shift-click equips the ring before death");
            player.setItemOnCursor(new ItemStack(Material.DIAMOND,3));check(diamonds()==16,"Fixture owns thirteen bag diamonds and three cursor diamonds");
            player.setHealth(0);later(60,this::released);
        });
    }
    @EventHandler(priority=EventPriority.MONITOR) public void deathReceipt(PlayerDeathEvent event){if(event.getPlayer().getName().equals("D_Player")){check(event.getKeepInventory()&&event.getKeepLevel(),"Actual death retains inventory and XP flags");check(event.getDrops().isEmpty()&&event.getDroppedExp()==0,"Actual death emits no item or XP drops");}}
    // Isolate the resurrection health assertion from the fixture's fast satiated-healing tick.
    @EventHandler public void fixtureRegeneration(org.bukkit.event.entity.EntityRegainHealthEvent event){if(event.getEntity() instanceof Player p&&p.getName().equals("D_Player"))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR) public void damageReceipt(org.bukkit.event.entity.EntityDamageByEntityEvent event){if(measuredTarget!=null&&event.getEntity().equals(measuredTarget))getLogger().info("DAMAGE RECEIPT actor="+event.getDamageSource().getCausingEntity()+" raw="+event.getDamage()+" final="+event.getFinalDamage()+" cancelled="+event.isCancelled());}
    private int diamonds(){int count=0;for(ItemStack item:player.getInventory().getContents())if(item!=null&&item.getType()==Material.DIAMOND)count+=item.getAmount();if(player.getItemOnCursor().getType()==Material.DIAMOND)count+=player.getItemOnCursor().getAmount();return count;}
    private void inventory()throws Exception{
        check(diamonds()==16,"Bag and cursor items survive death exactly once");
        ItemStack helmet=player.getInventory().getHelmet();check(helmet!=null&&helmet.containsEnchantment(Enchantment.VANISHING_CURSE),"Curse of Vanishing armor remains equipped");check(((Damageable)helmet.getItemMeta()).getDamage()==12,"Death adds no equipment durability penalty");check(PLAIN.serialize(helmet.getItemMeta().itemName()).equals("Death Test Helm"),"Armor metadata survives death");
        check(player.getInventory().getItem(0).getType()==Material.DIAMOND_SWORD&&player.getInventory().getItemInOffHand().getType()==Material.SHIELD,"Weapon and offhand remain equipped");check(core.equipment().equipped(player,CharacterEquipmentSlot.RING_1)!=null,"Saved accessory remains equipped");check(player.getLevel()==12&&player.getTotalExperience()==777,"Vanilla levels and total XP survive actual death");
    }
    private void released()throws Exception{
        check(!player.isDead()&&core.spirits().ghost(player.getUniqueId()),"Client respawn releases a persistent spirit");inventory();
        check(player.getGameMode()==GameMode.ADVENTURE&&player.isInvulnerable()&&!player.getAllowFlight(),"Ghost is non-flying Adventure mode with damage protection");check(player.getLocation().distance(sanctuary())<3,"Nearest safe sanctuary is used instead of a bed or ordinary spawn");check(!watcher.canSee((Entity)player),"Living observer cannot see the ghost entity");check(core.graves().latestLocation(account).isEmpty(),"No item-storage grave is created");
        screen("SPIRIT_MENU");check(PLAIN.serialize(player.getOpenInventory().title()).contains("Between Life"),"Native client receives resurrection choices");
        var chest=Bukkit.createInventory(null,27);player.openInventory(chest);check(player.getOpenInventory().getTopInventory()!=chest,"External containers cannot be opened while a ghost");check(!player.teleport(body()),"Ordinary plugin teleports cannot bypass the corpse run");
        player.closeInventory();player.sendMessage("D_SAY /equipment");player.sendMessage("D_SAY /shops");player.sendMessage("D_SAY /island home");player.sendMessage("D_DROP");
        ExperienceOrb orb=world.spawn(player.getLocation(),ExperienceOrb.class,e->e.setExperience(40));
        later(30,()->{
            check(player.getLocation().distance(sanctuary())<3,"Blocked gameplay commands cannot move the spirit");check(player.getOpenInventory().getType()==org.bukkit.event.inventory.InventoryType.CRAFTING,"Blocked equipment and shop commands open no gameplay menu");check(player.getInventory().getItem(0)!=null&&player.getInventory().getItem(0).getType()==Material.DIAMOND_SWORD,"Native Q cannot drop the retained weapon");check(orb.isValid()&&player.getTotalExperience()==777,"Ghost cannot consume nearby XP orbs");orb.remove();
            player.sendMessage("D_HEALER");later(15,()->{
                check(PLAIN.serialize(player.getOpenInventory().title()).contains("Between Life"),"Right-clicking the private Spirit Healer opens the choices");check(new SpiritRepository(new PlayerDataStore(core.getDataFolder(),getLogger())).load(account).ghost(),"Ghost state is committed in the canonical account file");
                player.closeInventory();finish();
            });
        });
    }
    private void restartRecovered()throws Exception{
        SpiritState.Point originalCompass=core.spirits().state(player.getUniqueId()).soul().abilities().compass();
        check(core.spirits().ghost(player.getUniqueId()),"Real server restart restores the ghost instead of reviving for free");inventory();check(player.getGameMode()==GameMode.ADVENTURE&&player.isInvulnerable(),"Restart restores ghost restrictions");check(!watcher.canSee((Entity)player),"Observer joining after restart still cannot see the ghost");
        player.closeInventory();player.sendMessage("D_WALK 810.5 160 -50.5");later(120,()->{
            check(player.getX()>795,"Native client can walk toward its body while a ghost");check(SpiritState.within(SpiritService.point(player.getLocation()),core.spirits().state(player.getUniqueId()).soul().corpse(),16),"Actual corpse run reaches resurrection range");
            player.sendMessage("D_SAY /spirit resurrect");later(20,()->{
                check(!core.spirits().ghost(player.getUniqueId()),"Corpse resurrection returns the player to life");check(player.getGameMode()==GameMode.SURVIVAL&&player.getAllowFlight()&&!player.isInvulnerable(),"Original game mode, flight permission and invulnerability are restored");check(watcher.canSee((Entity)player),"Living players can see the resurrected player again");
                double maximum=player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();check(Math.abs(player.getHealth()-maximum*.5)<.1,"Corpse resurrection restores half of actual maximum health");check(!core.spirits().state(player.getUniqueId()).sick(System.currentTimeMillis()),"Corpse run has no resurrection sickness");check(player.getCompassTarget().distance(SpiritService.resolve(originalCompass))<.1,"Original compass target is restored");inventory();
                secondDeath();
            });
        });
    }
    private void secondDeath()throws Exception{
        player.teleport(body());player.setHealth(0);later(220,()->{
            check(core.spirits().ghost(player.getUniqueId()),"A second adventure death creates a new spirit lifecycle");player.sendMessage("D_SAY /spirit healer");later(12,()->{
                screen("HEALER_CONFIRM");check(PLAIN.serialize(player.getOpenInventory().title()).contains("Confirm"),"Spirit Healer requires explicit confirmation");player.sendMessage("D_CLICK 20 0 0");later(10,()->{
                    check(core.spirits().ghost(player.getUniqueId()),"Cancelling the healer confirmation keeps the spirit unchanged");player.sendMessage("D_SAY /spirit healer");later(10,()->{player.sendMessage("D_CLICK 24 0 0");later(20,this::healerResurrected);});
                });
            });
        });
    }
    private void healerResurrected()throws Exception{
        check(!core.spirits().ghost(player.getUniqueId()),"Confirmed healer resurrection returns the player to life");check(core.spirits().state(player.getUniqueId()).sick(System.currentTimeMillis()),"Healer applies persistent short resurrection sickness");check(player.getHealth()==player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),"Healer restores full actual maximum health");inventory();
        check(world.getEntitiesByClass(Item.class).stream().noneMatch(e->e.getLocation().getY()>155&&e.getLocation().getX()>775&&e.getLocation().getX()<818),"Deaths leave no dropped inventory on the test platform");
        check(core.spirits().state(player.getUniqueId()).protectedNow(System.currentTimeMillis()),"Resurrection grants the configured short protection window");
        player.sendMessage("D_SAY /back");
        Zombie target=world.spawn(player.getLocation().clone().add(1,0,0),Zombie.class,e->{e.setAI(false);e.setSilent(true);e.setShouldBurnInDay(false);});player.sendMessage("D_ATTACK");later(15,()->{
            check(!core.spirits().state(player.getUniqueId()).protectedNow(System.currentTimeMillis()),"A real client attack ends resurrection protection");target.remove();
            player.sendMessage("D_SAY /equipment");later(10,()->{
                check(PLAIN.serialize(player.getOpenInventory().title()).equals("Character Equipment"),"Normal equipment flow is available after resurrection");player.closeInventory();
                player.sendMessage("D_SAY /island create");later(80,this::islandDeath);
            });
        });
    }
    private void islandDeath()throws Exception{
        check(core.skyblock().isIslandWorld(player.getWorld()),"Fixture enters an ordinary private island");player.setHealth(0);later(60,()->{
            check(!player.isDead()&&!core.spirits().ghost(player.getUniqueId()),"Island death uses direct safe respawn without a corpse run");check(core.skyblock().isIslandWorld(player.getWorld()),"Island respawn returns to the island");inventory();
            plainWorld();
        });
    }
    private void plainWorld()throws Exception{
        World plain=new WorldCreator("death_plain_probe").type(WorldType.FLAT).generateStructures(false).createWorld();Location spawn=plain.getSpawnLocation();Location safe=SpiritService.safe(spawn,4,12);if(safe==null){safe=new Location(plain,0.5,80,.5);plain.getBlockAt(0,79,0).setType(Material.STONE);}
        player.teleport(safe);watcher.teleport(safe.clone().add(0,0,3));player.setInvulnerable(false);Zombie target=plain.spawn(safe.clone().add(1,0,0),Zombie.class,e->{e.setAI(false);e.setSilent(true);e.setShouldBurnInDay(false);e.getAttribute(org.bukkit.attribute.Attribute.ARMOR).setBaseValue(0);});
        measuredTarget=target;ItemStack held=player.getInventory().getItemInMainHand().clone();player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));watcher.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        double starting=target.getHealth();var sickState=core.spirits().state(player.getUniqueId());target.damage(8,player);double sickDamage=starting-target.getHealth();target.setHealth(starting);target.setNoDamageTicks(0);
        // Same player and gear: temporarily clear the healer-generated deadline for a clean control measurement.
        core.spirits().repository().save(account,SpiritState.alive());core.spirits().ready(player);target.damage(8,player);double baseline=starting-target.getHealth();core.spirits().repository().save(account,sickState);core.spirits().ready(player);player.getInventory().setItemInMainHand(held);
        check(core.spirits().state(player.getUniqueId()).sick(System.currentTimeMillis())&&baseline>0&&Math.abs(sickDamage-baseline*.75)<.1,"Persistent resurrection sickness reduces actual outgoing damage by 25% (baseline="+baseline+", sick="+sickDamage+")");target.remove();
        player.setHealth(0);later(60,()->{check(!core.spirits().ghost(player.getUniqueId()),"A non-adventure world retains inventory with a normal respawn");inventory();unreachableBody();});
    }
    private void unreachableBody()throws Exception{
        player.teleport(new Location(world,780.5,world.getMinHeight()-30,-50.5));player.setHealth(0);later(220,()->{
            check(core.spirits().ghost(player.getUniqueId())&&!core.spirits().state(player.getUniqueId()).soul().recoverable(),"A body below the world is marked unreachable");
            player.sendMessage("D_SAY /spirit healer");later(10,()->{player.sendMessage("D_CLICK 24 0 0");later(15,()->{
                check(!core.spirits().ghost(player.getUniqueId())&&!core.spirits().state(player.getUniqueId()).sick(System.currentTimeMillis()),"Unreachable-body healer recovery waives resurrection sickness");inventory();finish();
            });});
        });
    }
    private void screen(String label){var list=new ArrayList<Map<String,Object>>();var inv=player.getOpenInventory().getTopInventory();for(int i=0;i<inv.getSize();i++){ItemStack item=inv.getItem(i);if(item==null)continue;var meta=item.getItemMeta();list.add(Map.of("slot",i,"material",item.getType().name(),"name",meta.hasItemName()?PLAIN.serialize(meta.itemName()):item.getType().name(),"lore",meta.hasLore()?meta.lore().stream().map(PLAIN::serialize).toList():List.of()));}screens.put(label,list);}
    private void finish()throws Exception{Files.writeString(Path.of("spirit-phase"+phase+"-screens.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(screens));Files.writeString(Path.of("spirit-phase"+phase+"-result.txt"),"PASS "+checks.size()+"\n"+String.join("\n",checks));Bukkit.shutdown();}
    private void check(boolean condition,String text){if(!condition)throw new AssertionError(text);checks.add(text);getLogger().info("PASS "+text);}
    @FunctionalInterface interface Step{void run()throws Exception;}
    private void later(long ticks,Step step){Bukkit.getScheduler().runTaskLater(this,()->{try{step.run();}catch(Throwable failure){getLogger().log(java.util.logging.Level.SEVERE,"Spirit probe failed",failure);try{Files.writeString(Path.of("spirit-phase"+phase+"-result.txt"),"FAIL "+failure+"\n"+String.join("\n",checks));}catch(Exception ignored){}Bukkit.shutdown();}},ticks);}
}
