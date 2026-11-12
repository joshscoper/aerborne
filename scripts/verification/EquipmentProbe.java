package net.nerdsmp.probe;

import com.google.gson.GsonBuilder;
import net.nerdsmp.api.model.*;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.item.*;
import net.nerdsmp.paper.engine.ItemBlueprint;
import net.nerdsmp.paper.forge.GemType;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.nio.file.*;
import java.util.*;

/** Destructive fixture setup is guarded to a disposable loopback instance. Never ship this probe. */
public final class EquipmentProbe extends JavaPlugin {
    private NerdSmpPlugin core;
    private Player player;
    private AccountId account;
    private int waits;
    private final List<String> checks=new ArrayList<>();
    private final Map<String,Object> screens=new LinkedHashMap<>();
    private ItemStack socketedRing;
    private static final PlainTextComponentSerializer PLAIN=PlainTextComponentSerializer.plainText();
    private static final String[] IDS={"travelers_pendant","trail_shoulders","trail_cloak","copper_bracers","leather_handguards","trail_belt","copper_band","amethyst_band","surveyors_charm","tide_charm"};
    private static final CharacterEquipmentSlot[] SLOTS={CharacterEquipmentSlot.NECK,CharacterEquipmentSlot.SHOULDERS,CharacterEquipmentSlot.BACK,CharacterEquipmentSlot.WRISTS,CharacterEquipmentSlot.HANDS,CharacterEquipmentSlot.WAIST,CharacterEquipmentSlot.RING_1,CharacterEquipmentSlot.RING_2,CharacterEquipmentSlot.TRINKET_1,CharacterEquipmentSlot.TRINKET_2,CharacterEquipmentSlot.HEAD,CharacterEquipmentSlot.CHEST,CharacterEquipmentSlot.LEGS,CharacterEquipmentSlot.FEET,CharacterEquipmentSlot.MAIN_HAND,CharacterEquipmentSlot.OFF_HAND};
    @Override public void onEnable() {
        if(!Boolean.getBoolean("nerdsmp.equipmentProbe")||!Bukkit.getIp().equals("127.0.0.1"))throw new IllegalStateException("Disposable loopback fixture only");
        core=(NerdSmpPlugin)Bukkit.getPluginManager().getPlugin("NerdSMP");later(20,this::ready);
    }
    private void ready() throws Exception {
        player=Bukkit.getPlayerExact("E_Gear");
        if(++waits>240)throw new AssertionError("Player deadline");
        if(player==null||core.sessions().account(player.getUniqueId()).isEmpty()){later(20,this::ready);return;}
        account=core.sessions().account(player.getUniqueId()).orElseThrow();player.setInvulnerable(true);player.setGameMode(GameMode.SURVIVAL);
        player.closeInventory();player.setItemOnCursor(null);player.getInventory().clear();player.getInventory().setHeldItemSlot(0);
        for(int i=0;i<IDS.length;i++) {
            ItemStack item=core.items().create(new ItemId(IDS[i]));
            check(core.items().gear(item).orElseThrow().slot()==SLOTS[i].type(),"Catalog slot authenticated: "+IDS[i]);
            check(item.getMaxStackSize()==1,"Accessory is non-stackable: "+IDS[i]);
            check(Bukkit.getRecipe(new NamespacedKey(core,"crafted_"+IDS[i]))!=null,"Crafting recipe registered: "+IDS[i]);
            if(i==6) {check(core.items().socket(item,GemType.EMERALD,1)==PaperItemService.UpgradeResult.APPLIED,"Utility gem can be socketed into a ring");socketedRing=item.clone();}
            player.getInventory().setItem(9+i,item);
        }
        Material[] nativeItems={Material.IRON_HELMET,Material.IRON_CHESTPLATE,Material.IRON_LEGGINGS,Material.IRON_BOOTS,Material.IRON_SWORD,Material.SHIELD};
        for(int i=0;i<nativeItems.length;i++) player.getInventory().setItem(19+i,new ItemStack(nativeItems[i]));
        player.performCommand("equipment");later(15,()->{
            check(title().equals("Character Equipment"),"Equipment command reaches native client menu");
            check(player.getOpenInventory().getTopInventory().getSize()==54,"Full equipment screen uses supported inventory type");
            for(var slot:SLOTS) check(player.getOpenInventory().getTopInventory().getItem(slot.menuSlot())!=null,"Slot visible: "+slot.title());
            screen("EMPTY");buy(0);
        });
    }
    private void buy(int index) throws Exception {
        if(index==SLOTS.length){allEquipped();return;}
        player.performCommand("equipment");later(8,()->{
            click(54+index,0,0);
            later(12,()->{
                ItemStack item=core.equipment().equipped(player,SLOTS[index]);
                check(item!=null,"Native bag click equips "+SLOTS[index].title());
                check(player.getInventory().getItem(9+index)==null||player.getInventory().getItem(9+index).getType().isAir(),"Source bag slot emptied once: "+SLOTS[index].title());
                check(total()==16,"Conserved sixteen owned items after "+SLOTS[index].title());
                check(new EquipmentLedger(core.playerData()).read(account).pending()==null,"Verified save acknowledged: "+SLOTS[index].title());
                buy(index+1);
            });
        });
    }
    private void allEquipped() throws Exception {
        screen("ALL_EQUIPPED");
        var profile=core.gearSets().profile(player);
        check(profile.stat("defense")==5,"Five accessory Defense points reach the live gear profile once");
        check(profile.stat("max-health")==6,"Three vitality accessories add six real maximum health points");
        check(profile.stat("skill-xp-percent")==3,"Socketed ring's XP bonus reaches the live profile");
        check(profile.stat("geode-find-percent")==2 && profile.stat("fishing-luck-percent")==1,"Both distinct trinkets contribute their gathering bonuses");
        core.playerStats().refresh(player);check(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()==26,"Accessory vitality changes the actual player health attribute");
        var disk=new PlayerDataStore(core.getDataFolder(),getLogger());
        check(new EquipmentLedger(disk).read(account).equals(new EquipmentLedger(core.playerData()).read(account)),"Ten accessory records reload from a new disk store exactly");
        check(core.equipment().equipped(player,CharacterEquipmentSlot.RING_1).equals(socketedRing),"Socket metadata preserved while equipping");
        core.itemEngine().publishSet(new net.nerdsmp.paper.engine.ItemSetBlueprint("probe_jewels","Probe Jewels",2,Map.of(2,new net.nerdsmp.paper.engine.ItemSetBlueprint.Bonus("Two-piece Defense",Map.of("defense",2D)))));
        for(String id:List.of("probe_set_ring","probe_set_neck")) core.itemEngine().publish(new ItemBlueprint(id,id,Material.AMETHYST_SHARD,"UNCOMMON",1,5,"probe_jewels",List.of(),Map.of("defense",1D),id.endsWith("ring")?PaperItemService.GearSlot.RING:PaperItemService.GearSlot.NECK));
        var ring=core.items().create(new ItemId("probe_set_ring"));var neck=core.items().create(new ItemId("probe_set_neck"));
        var combined=core.gearSets().profile(Map.of(),Map.of(CharacterEquipmentSlot.RING_1,ring,CharacterEquipmentSlot.NECK,neck));
        check(combined.stat("defense")==4&&combined.setPieces().get("probe_jewels")==2,"Authored accessories activate distinct-piece set bonuses");
        check(core.gearSets().profile(Map.of(),Map.of(CharacterEquipmentSlot.RING_1,ring,CharacterEquipmentSlot.RING_2,ring)).stat("defense")==1,"Duplicated catalog accessory cannot double-count stats or set membership");
        check(core.gearSets().profile(Map.of(org.bukkit.inventory.EquipmentSlot.HAND,ring)).stats().isEmpty(),"Holding an accessory does not grant equipped bonuses");
        check(core.gearSets().profile(Map.of(),Map.of(CharacterEquipmentSlot.NECK,ring)).stats().isEmpty(),"Wrong-slot accessory contributes no stats");
        invalidItems();
    }
    private void invalidItems() throws Exception {
        var source=core.items().create(new ItemId("copper_band"));player.getInventory().setItem(27,source);
        denied(()->core.equipment().equip(player,CharacterEquipmentSlot.RING_2,27,source),"Duplicate accessory cannot occupy both rings");
        ItemStack fake=new ItemStack(Material.GOLD_NUGGET);player.getInventory().setItem(28,fake);
        denied(()->core.equipment().equip(player,CharacterEquipmentSlot.NECK,28,fake),"Ordinary matching-looking materials cannot become accessories");
        ItemStack sealed=core.items().createLoot(new ItemId("copper_band"),0,true,17);player.getInventory().setItem(29,sealed);
        denied(()->core.equipment().equip(player,CharacterEquipmentSlot.RING_1,29,sealed),"Unidentified accessories cannot grant stats");
        source.setAmount(2);player.getInventory().setItem(27,source);
        denied(()->core.equipment().equip(player,CharacterEquipmentSlot.RING_1,27,source),"Oversized equipment stacks are rejected");
        for(int i=27;i<=29;i++)player.getInventory().setItem(i,null);
        ItemStack old=core.equipment().equipped(player,CharacterEquipmentSlot.RING_1);
        for(int i=0;i<36;i++)if(player.getInventory().getItem(i)==null||player.getInventory().getItem(i).getType().isAir()) player.getInventory().setItem(i,new ItemStack(Material.STONE,64));
        denied(()->core.equipment().unequip(player,CharacterEquipmentSlot.RING_1,old),"Full bags block unequip without dropping or deleting gear");
        check(core.equipment().equipped(player,CharacterEquipmentSlot.RING_1).equals(old),"Full-bag rejection preserves the equipped item exactly");
        for(int i=0;i<36;i++)if(player.getInventory().getItem(i)!=null&&player.getInventory().getItem(i).getType()==Material.STONE)player.getInventory().setItem(i,null);
        ItemStack helm=player.getInventory().getHelmet();helm.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.BINDING_CURSE,1);player.getInventory().setHelmet(helm);
        denied(()->core.equipment().unequip(player,CharacterEquipmentSlot.HEAD,helm),"Native Curse of Binding remains enforced");
        helm.removeEnchantment(org.bukkit.enchantments.Enchantment.BINDING_CURSE);player.getInventory().setHelmet(helm);
        core.itemEngine().publish(new ItemBlueprint("probe_band","Probe Band",Material.GOLD_NUGGET,"UNCOMMON",1,5,null,List.of(),Map.of("max-health",3D),PaperItemService.GearSlot.RING));
        player.getInventory().setItem(26,core.items().create(new ItemId("probe_band")));
        player.performCommand("equipment");later(10,()->{
            click(32,0,0);later(8,()->{click(71,0,0);later(15,this::swapped);});
        });
    }
    private void swapped() throws Exception {
        check(id(core.equipment().equipped(player,CharacterEquipmentSlot.RING_1)).equals("probe_band"),"Selected occupied ring slot accepts a native swap");
        check(player.getInventory().getItem(26).equals(socketedRing),"Swap returns the old socketed ring to the same source slot");
        check(total()==17,"Native swap conserves all owned items");
        click(32,1,0);later(12,()->{
            check(core.equipment().equipped(player,CharacterEquipmentSlot.RING_1)==null,"Native right-click unequips a ring");
            check(total()==17,"Unequip conserves all items");
            click(71,0,0);later(12,()->{
                check(core.equipment().equipped(player,CharacterEquipmentSlot.RING_1).equals(socketedRing),"Ring metadata survives native swap, unequip and re-equip");
                check(core.gearSets().profile(player).stat("max-health")==6,"Bagged ring adds no passive vitality");
                forbiddenClicks(0);
            });
        });
    }
    private void forbiddenClicks(int index) throws Exception {
        int[][] actions={{32,0,2},{32,0,4},{32,0,1},{-999,0,0}};
        if(index==actions.length){recovery();return;}
        long drops=ringDrops();
        var action=actions[index];click(action[0],action[1],action[2]);
        later(12,()->{check(total()==17&&core.equipment().equipped(player,CharacterEquipmentSlot.RING_1).equals(socketedRing)
            &&ringDrops()==drops&&(player.getItemOnCursor()==null||player.getItemOnCursor().getType().isAir()),"Menu copy cannot be extracted by native click mode "+action[2]);forbiddenClicks(index+1);});
    }
    private void recovery() throws Exception {
        player.closeInventory();
        var ledger=new EquipmentLedger(core.playerData());var old=ledger.read(account);
        ItemStack before=player.getInventory().getItem(26);check(before==null||before.getType().isAir(),"Recovery test source is empty");
        // Model an interrupted unequip: plugin intent saved, Minecraft still has the old empty bag.
        var after=new EnumMap<CharacterEquipmentSlot,String>(CharacterEquipmentSlot.class);after.putAll(old.accessories());
        after.remove(CharacterEquipmentSlot.TRINKET_2);
        ItemStack charm=core.equipment().equipped(player,CharacterEquipmentSlot.TRINKET_2);
        var pending=new EquipmentLedger.State(after,new EquipmentLedger.Transfer(player.getUniqueId(),List.of(new EquipmentLedger.Change(26,null,encode(charm)))));
        ledger.begin(account,old,pending);
        var fresh=new CharacterEquipmentService(core);
        check(fresh.recover(player),"Fresh service recovers a durable transfer before new inventory grants");
        check(ledger.read(account).pending()==null&&id(player.getInventory().getItem(26)).equals("tide_charm"),"Interrupted unequip delivers exactly one item and acknowledges verified save");
        check(!fresh.accessories(player).containsKey(CharacterEquipmentSlot.TRINKET_2),"Recovered item is no longer equipped");
        // A reconnect drops the old live-service cache and exercises normal SessionListener recovery.
        player.kick(net.kyori.adventure.text.Component.text("EQ_RECONNECT"));
        later(40,this::reconnected);
    }
    private void reconnected() throws Exception {
        player=Bukkit.getPlayerExact("E_Gear");
        if(player==null||core.sessions().account(player.getUniqueId()).isEmpty()){if(++waits>280)throw new AssertionError("Reconnect deadline");later(10,this::reconnected);return;}
        check(!core.equipment().accessories(player).containsKey(CharacterEquipmentSlot.TRINKET_2),"Normal reconnect loads recovered equipment state");
        check(id(player.getInventory().getItem(26)).equals("tide_charm"),"Verified native inventory persists through reconnect");
        core.equipment().equip(player,CharacterEquipmentSlot.TRINKET_2,26,player.getInventory().getItem(26).clone());
        check(core.equipment().accessories(player).size()==10,"All ten accessories can be restored after recovery");
        Map<CharacterEquipmentSlot,String> kept=new HashMap<>();core.equipment().accessories(player).forEach((slot,item)->kept.put(slot,encode(item)));
        player.closeInventory();player.setHealth(0);
        later(20,()->{player.spigot().respawn();later(25,()->{
            check(!player.isDead(),"Fixture player respawns after an actual death");
            var now=new HashMap<CharacterEquipmentSlot,String>();core.equipment().accessories(player).forEach((slot,item)->now.put(slot,encode(item)));
            check(now.equals(kept),"All accessory choices and metadata survive actual death/respawn");
            player.performCommand("equipment");later(12,this::finish);
        });});
    }
    private void finish() throws Exception {
        screen("AFTER_RESPAWN");
        click(8,0,0);later(10,()->{
            check(title().contains("Player Stats"),"Equipment Stats button opens live player stats");
            player.performCommand("gear");later(10,()->{
                check(title().equals("Character Equipment"),"Gear alias opens Equipment");
                click(48,0,0);later(10,()->{
                    check(title().equals("Character"),"Equipment Back button opens Character");
                    click(25,0,0);later(10,()->{
                        check(title().equals("Character Equipment"),"Character Equipment button opens Equipment");
                        click(49,0,0);later(10,()->{
                            check(player.getOpenInventory().getType()==org.bukkit.event.inventory.InventoryType.CRAFTING,"Close returns to the unchanged survival inventory");
                            player.performCommand("equip");later(10,()->{
                                check(title().equals("Character Equipment"),"Equip alias opens Equipment");
                                player.closeInventory();player.sendMessage("EQ_SHORTCUT");
                                later(30,()->{check(title().equals("Character Equipment"),"Native Sneak + F packet opens Equipment");writeResult();});
                            });
                        });
                    });
                });
            });
        });
    }
    private void writeResult() throws Exception {
        Files.writeString(Path.of("equipment-screens.json"),new GsonBuilder().setPrettyPrinting().create().toJson(screens));
        Files.writeString(Path.of("equipment-result.txt"),"PASS "+checks.size()+" checks\n"+String.join("\n",checks)+"\n");Bukkit.shutdown();
    }
    private void denied(Runnable action,String message){int before=total();boolean denied=false;try{action.run();}catch(IllegalArgumentException expected){denied=true;}check(denied&&total()==before,message);}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks.add("PASS "+message);getLogger().info(checks.getLast());}
    private String id(ItemStack item){return core.items().describe(item).map(d->d.itemId().value()).orElse("");}
    private String title(){return PLAIN.serialize(player.getOpenInventory().title());}
    private void click(int slot,int button,int mode){player.sendMessage("EQ_CLICK "+slot+" "+button+" "+mode);}
    private int total(){int count=0;for(ItemStack item:player.getInventory().getContents())if(item!=null&&!item.getType().isAir())count+=item.getAmount();for(var item:core.equipment().accessories(player).values())count+=item.getAmount();return count;}
    private long ringDrops(){return player.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class).stream().filter(item->id(item.getItemStack()).equals("copper_band")).count();}
    private static String encode(ItemStack item){return Base64.getEncoder().encodeToString(item.serializeAsBytes());}
    private void screen(String label){List<Map<String,Object>> list=new ArrayList<>();var inv=player.getOpenInventory().getTopInventory();for(int i=0;i<inv.getSize();i++){var item=inv.getItem(i);if(item==null)continue;var meta=item.getItemMeta();list.add(Map.of("slot",i,"material",item.getType().name(),"name",meta.hasItemName()?PLAIN.serialize(meta.itemName()):item.getType().name(),"lore",meta.hasLore()?meta.lore().stream().map(PLAIN::serialize).toList():List.of()));}screens.put(label,list);}
    @FunctionalInterface interface Step{void run()throws Exception;}
    private void later(long ticks,Step step){Bukkit.getScheduler().runTaskLater(this,()->{try{step.run();}catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"Equipment probe failed",error);try{Files.writeString(Path.of("equipment-result.txt"),"FAIL "+error+"\n"+String.join("\n",checks));}catch(Exception ignored){}Bukkit.shutdown();}},ticks);}
}
