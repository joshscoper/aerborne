package net.nerdsmp.probe;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.nerdsmp.api.model.*;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.item.*;
import net.nerdsmp.paper.forge.*;
import net.nerdsmp.paper.alchemy.*;
import net.nerdsmp.paper.skyblock.machine.MachineKind;
import org.bukkit.*;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Exhaustive presentation coverage plus actual native crafting, smelting, extraction and mining. */
public final class ItemTemplateProbe extends JavaPlugin implements org.bukkit.event.Listener {
    private NerdSmpPlugin core; private Player player; private World world; private int waits, assertions;
    private final List<String> checks = new ArrayList<>();
    private final Map<String,Integer> counts = new LinkedHashMap<>();
    private final Map<String,Object> samples = new LinkedHashMap<>();
    private final List<Furnace> furnaces = new ArrayList<>();
    @Override public void onEnable() {
        if (!Boolean.getBoolean("nerdsmp.itemTemplateProbe") || !Bukkit.getIp().equals("127.0.0.1")) throw new IllegalStateException("Disposable fixture only");
        core=(NerdSmpPlugin)Bukkit.getPluginManager().getPlugin("NerdSMP");Bukkit.getPluginManager().registerEvents(this,this);later(100,this::ready);
    }
    private void ready() throws Exception {
        player=Bukkit.getPlayerExact("I_Player"); if(++waits>120)throw new AssertionError("Client deadline");
        if(player==null||!core.presentation().progressionReady(player)){later(20,this::ready);return;}
        player.setGameMode(GameMode.SURVIVAL);player.setInvulnerable(true);player.getInventory().clear();
        player.addAttachment(this,"nerdsmp.claim.bypass",true);
        player.addAttachment(this,"nerdsmp.anticheat.bypass",true);
        world=new WorldCreator("ItemTemplateArena_"+Long.toUnsignedString(System.nanoTime(),36)).type(WorldType.FLAT).generateStructures(false).createWorld();world.setDifficulty(Difficulty.PEACEFUL);
        for(int x=-5;x<=6;x++)for(int z=-4;z<=4;z++){world.getBlockAt(x,159,z).setType(Material.STONE);for(int y=160;y<164;y++)world.getBlockAt(x,y,z).setType(Material.AIR);}
        player.teleport(new Location(world,.5,160,.5));core.watchdog().grantGrace(player,120000);
        check(core.getConfig().getString("anti-cheat.mode").equals("alerts-only")&&!core.getConfig().getBoolean("anti-cheat.ml.autonomous"),"Watchdog configuration remains alert-only");
        for(Material material:Material.values()){
            if(material.name().startsWith("LEGACY_")||!material.isItem()||material.isAir())continue;
            ItemStack before=new ItemStack(material),after=verify("native",before);
            if(before.getData(DataComponentTypes.FOOD)==null&&!ItemPresentation.original(after).isSimilar(before)){
                Files.writeString(Path.of("semantic-diagnostic.txt"),"BEFORE "+before+"\nAFTER "+after+"\nORIGINAL "+ItemPresentation.original(after)+"\nBEFORE SERIAL "+before.serialize()+"\nORIGINAL SERIAL "+ItemPresentation.original(after).serialize());
            }
            if(before.getData(DataComponentTypes.FOOD)==null)check(ItemPresentation.original(after).isSimilar(before),"Native semantic round-trip "+material);
            else check(FoodItems.ordinary(after),"Food remains ordinary "+material);
            check(FoodItems.ordinary(after),"Ordinary ingredient preserved "+material);
            if(Set.of(Material.STONE,Material.IRON_INGOT,Material.COAL,Material.OAK_SAPLING,Material.REDSTONE,Material.CHEST,Material.IRON_SWORD,Material.IRON_PICKAXE,Material.BREAD,Material.EXPERIENCE_BOTTLE).contains(material))snapshot(material.name(),after);
        }
        for(ItemId id:core.items().itemIds()){
            ItemStack item=core.items().create(id),after=verify("catalog",item);
            check(core.items().isAuthentic(after)&&after.isSimilar(FoodItems.upgrade(item).orElse(item)),"Catalog identity and stats unchanged "+id);
        }
        for(GeodeGrade grade:GeodeGrade.values())verify("geode",core.forge().createGeode(grade));
        for(GemType type:GemType.values())for(int tier=1;tier<=GemMergeRules.MAXIMUM_TIER;tier++)verify("gem",core.forge().createGem(type,tier));
        for(ArcaneEnchantment enchant:ArcaneEnchantment.values())for(int level=1;level<=5;level++)verify("tome",core.forge().createTome(enchant,level));
        for(SkillLamps.Grade grade:SkillLamps.Grade.values()){
            ItemStack item=core.skillLamps().bottle(grade,1),after=verify("skill-lamp",item);check(core.skillLamps().grade(after)==grade,"Skill lamp grade retained "+grade);snapshot("lamp-"+grade,after);
        }
        for(MachineKind kind:MachineKind.values())verify("machine",core.skyblock().processingMachines().item(kind));
        verify("collector",invoke(core.skyblock().automation(),"collectorItem",new Class<?>[]{}));
        ItemStack purse=invoke(core.rewardDrops(),"createCoinPurse",new Class<?>[]{long.class},25L);
        ItemStack shownPurse=verify("coin-purse",purse);
        check(core.items().isAuthentic(shownPurse)&&shownPurse.isSimilar(purse),"Coin purse amount, unique identity and reward instructions stay intact");
        var supplyFactory=Class.forName("net.nerdsmp.paper.expedition.RunSupplies").getDeclaredMethod("tagged",org.bukkit.plugin.Plugin.class,ItemStack.class,String.class);supplyFactory.setAccessible(true);
        for(Material material:List.of(Material.BOW,Material.ARROW,Material.COBBLESTONE)){
            ItemStack supply=(ItemStack)supplyFactory.invoke(null,core,new ItemStack(material),"<aqua>Expedition Supply</aqua>");
            ItemStack shown=verify("expedition-supply",supply);
            check(ItemPresentation.original(shown).isSimilar(supply)&&ItemPresentation.classify(shown)==ItemPresentation.Kind.SUPPLY,"Expedition supply cleanup tag and original instructions retained "+material);
        }
        for(PotionType type:PotionType.values())for(Material material:List.of(Material.POTION,Material.SPLASH_POTION,Material.LINGERING_POTION)){
            ItemStack item=new ItemStack(material);item.editMeta(PotionMeta.class,meta->meta.setBasePotionType(type));
            ItemStack after=verify("native-potion",item);check(ItemPresentation.original(after).isSimilar(item),"Potion contents retained "+type+"/"+material);
        }
        for(ArcaneBrew brew:ArcaneBrew.values())verify("arcane-brew",invoke(core.alchemy(),"createArcanePotion",new Class<?>[]{ArcaneBrew.class},brew));
        for(PoisonType poison:PoisonType.values()){
            verify("poison-vial",invoke(core.alchemy(),"createVial",new Class<?>[]{PoisonType.class,int.class},poison,1));
            verify("poison-arrows",invoke(core.alchemy(),"createArrows",new Class<?>[]{PoisonType.class,int.class,int.class},poison,1,8));
        }
        verify("antidote",invoke(core.alchemy(),"createAntidote",new Class<?>[]{List.class},List.of("<gray>Drink to clear poison.</gray>")));
        var iterator=Bukkit.recipeIterator();
        while(iterator.hasNext()){
            Recipe recipe=iterator.next();if(recipe.getResult()==null||recipe.getResult().getType().isAir())continue;verify("recipe-result",recipe.getResult());
            if(recipe instanceof CookingRecipe<?> cooking)check(ItemPresentation.covered(cooking.getResult()),"Registered cooking result is canonical "+cooking.getKey());
        }
        integrity();
        world.getBlockAt(1,160,0).setType(Material.CRAFTING_TABLE);
        player.getInventory().setItem(0,new ItemStack(Material.OAK_LOG));
        later(15,()->{check(ItemPresentation.covered(player.getInventory().getItem(0)),"Directly granted raw items gain their template automatically");player.sendMessage("I_CRAFT oak_planks");later(100,this::crafted);});
    }
    private ItemStack verify(String family,ItemStack before){
        ItemStack untouched=before.clone();ItemStack after=ItemUpdater.current(before);check(before.isSimilar(untouched),"Input unchanged "+family+"/"+before.getType());check(ItemPresentation.covered(after),"Template present "+family+"/"+before.getType());
        check(ItemUpdater.current(after).isSimilar(after),"Idempotent "+family+"/"+before.getType());
        ItemStack round=ItemStack.deserializeBytes(after.serializeAsBytes());check(round.isSimilar(after)&&ItemUpdater.current(round).isSimilar(after),"Persistent "+family+"/"+before.getType());
        check(after.getAmount()==before.getAmount(),"Quantity retained "+family+"/"+before.getType());
        counts.merge(family,1,Integer::sum);return after;
    }
    private void integrity()throws Exception{
        ItemStack enchanted=new ItemStack(Material.DIAMOND_PICKAXE);enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.EFFICIENCY,4);enchanted.editMeta(Damageable.class,meta->meta.setDamage(42));
        ItemStack after=ItemUpdater.current(enchanted);check(ItemPresentation.original(after).isSimilar(enchanted),"Enchantment and durability survive presentation exactly");
        ItemStack named=new ItemStack(Material.STONE);named.editMeta(meta->meta.displayName(Component.text("Josh's keepsake")));
        check(ItemPresentation.original(ItemUpdater.current(named)).isSimilar(named),"Player custom names survive presentation exactly");
        ItemStack book=new ItemStack(Material.WRITTEN_BOOK);book.editMeta(BookMeta.class,meta->{meta.setTitle("Aerborne Journal");meta.setAuthor("Josh");meta.addPages(Component.text("An adventurer's first page."),Component.text("The second page stays intact."));});
        check(ItemPresentation.original(ItemUpdater.current(book)).isSimilar(book),"Signed book author, title and every page survive presentation exactly");
        ItemStack custom=new ItemStack(Material.IRON_INGOT);custom.editMeta(meta->{meta.lore(List.of(Component.text("Special quest reagent")));meta.getPersistentDataContainer().set(new NamespacedKey(core,"quest_token"),PersistentDataType.INTEGER,1);});
        ItemStack styled=ItemUpdater.current(custom);check(ItemPresentation.original(styled).isSimilar(custom)&&CraftingGuard.protectedIngredient(styled),"Special metadata remains protected after framing");
        ItemStack normal=ItemUpdater.current(new ItemStack(Material.IRON_INGOT));
        check(FoodItems.ordinary(normal)&&!CraftingGuard.protectedIngredient(normal),"Ordinary metal remains usable in every guarded recipe");
        ItemStack altered=normal.clone();altered.editMeta(meta->{var lore=new ArrayList<>(meta.lore());lore.add(Component.text("Tampered bonus"));meta.lore(lore);});
        check(!FoodItems.ordinary(altered)&&CraftingGuard.protectedIngredient(altered),"Edited presentation cannot bypass special-item checks");
        ItemStack box=new ItemStack(Material.SHULKER_BOX);BlockStateMeta meta=(BlockStateMeta)box.getItemMeta();org.bukkit.block.ShulkerBox state=(org.bukkit.block.ShulkerBox)meta.getBlockState();state.getInventory().setItem(0,new ItemStack(Material.DIAMOND,3));meta.setBlockState(state);box.setItemMeta(meta);
        ItemStack updated=ItemUpdater.current(box);var stored=(org.bukkit.block.ShulkerBox)((BlockStateMeta)updated.getItemMeta()).getBlockState();
        check(ItemPresentation.covered(stored.getInventory().getItem(0))&&stored.getInventory().getItem(0).getAmount()==3,"Nested storage contents gain templates without losing quantity");
        ItemStack healing=new ItemStack(Material.POTION);healing.editMeta(PotionMeta.class,m->m.setBasePotionType(PotionType.HEALING));
        ItemStack next=ItemUpdater.current(healing);next.editMeta(PotionMeta.class,m->m.setBasePotionType(PotionType.SWIFTNESS));next=ItemUpdater.current(next);
        check(((PotionMeta)next.getItemMeta()).getBasePotionType()==PotionType.SWIFTNESS&&next.effectiveName().toString().contains("swiftness"),"Changed potion contents refresh the native potion name");snapshot("potion",next);
        ItemStack sword=ItemUpdater.current(new ItemStack(Material.IRON_SWORD)).withType(Material.DIAMOND_SWORD);sword=ItemUpdater.current(sword);
        check(sword.effectiveName().toString().contains("diamond_sword"),"Native material changes do not leave stale item names");
        var one=core.alchemy().getClass().getDeclaredMethod("one",ItemStack.class);one.setAccessible(true);
        check(((ItemStack)one.invoke(null,normal)).isSimilar(new ItemStack(Material.IRON_INGOT)),"Alchemy compares semantic ingredients rather than tooltip metadata");
        player.getInventory().clear();player.getInventory().setItem(0,ItemUpdater.current(new ItemStack(Material.SUGAR)));player.getInventory().setItem(1,ItemUpdater.current(new ItemStack(Material.GLASS_BOTTLE)));
        var brew=core.alchemy().getClass().getDeclaredMethod("brewTransaction",Player.class,List.class,ItemStack.class,int.class);brew.setAccessible(true);
        check((boolean)brew.invoke(core.alchemy(),player,List.of(Material.SUGAR,Material.GLASS_BOTTLE),healing,0),"Actual alchemy inventory transaction accepts templated reagents");
        check(Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).anyMatch(item->item.getType()==Material.POTION),"Alchemy delivers its exact potion output");
        player.getInventory().clear();var dropped=world.dropItem(new Location(world,4.5,161,3.5),new ItemStack(Material.DIAMOND,2));dropped.setPickupDelay(1000);
        check(ItemPresentation.covered(dropped.getItemStack())&&dropped.getItemStack().getAmount()==2,"Spawned reward drops gain templates immediately without duplication");dropped.remove();
    }
    private void crafted()throws Exception{
        check(count(Material.OAK_PLANKS)==4&&count(Material.OAK_LOG)==0,"Real native crafting consumes one templated log for four planks");
        check(Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).filter(item->item.getType()==Material.OAK_PLANKS).allMatch(ItemPresentation::covered),"Native crafting output has the correct block template");
        int i=0;for(Material machine:List.of(Material.FURNACE,Material.BLAST_FURNACE,Material.SMOKER)){
            var block=world.getBlockAt(i++,160,2);block.setType(machine);Furnace furnace=(Furnace)block.getState(false);furnace.setCookSpeedMultiplier(20);furnace.update(true,false);
            furnace.getInventory().setSmelting(ItemUpdater.current(new ItemStack(machine==Material.SMOKER?Material.BEEF:Material.RAW_IRON,3)));
            furnace.getInventory().setFuel(ItemUpdater.current(new ItemStack(Material.COAL,1)));furnaces.add(furnace);
        }
        later(120,this::smelted);
    }
    private void smelted()throws Exception{
        Files.writeString(Path.of("furnace-diagnostic.txt"),furnaces.stream().map(f->f.getType()+" burn="+f.getBurnTime()+" cook="+f.getCookTime()+" total="+f.getCookTimeTotal()+" speed="+f.getCookSpeedMultiplier()+" slots="+Arrays.toString(f.getInventory().getContents())).collect(java.util.stream.Collectors.joining("\n")));
        for(Furnace furnace:furnaces){var result=furnace.getInventory().getResult();check(result!=null&&result.getAmount()==3&&ItemPresentation.covered(result),"Three consecutive native smelts complete and merge in "+furnace.getType());}
        player.sendMessage("I_TAKE");later(140,this::extracted);
    }
    private void extracted()throws Exception{
        check(count(Material.IRON_INGOT)==3,"Native furnace extraction gives exactly three ingots");
        int toolSlot=player.getInventory().firstEmpty();player.getInventory().setItem(toolSlot,new ItemStack(Material.WOODEN_PICKAXE));player.getInventory().setHeldItemSlot(toolSlot);world.getBlockAt(-1,160,0).setType(Material.STONE);
        later(15,()->{player.sendMessage("I_DIG");later(100,this::mined);});
    }
    private void mined()throws Exception{
        check(world.getBlockAt(-1,160,0).getType()==Material.AIR,"A native client mines normally with a templated tool");
        check(count(Material.COBBLESTONE)==1,"Native mining delivers exactly one templated block drop");
        check(Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).allMatch(ItemPresentation::covered),"Every final inventory stack has its category template");
        Files.writeString(Path.of("item-template-samples.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(samples));
        Files.writeString(Path.of("item-template-counts.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(counts));
        Files.writeString(Path.of("item-template-result.txt"),"PASS "+assertions+"\n"+String.join("\n",checks));Bukkit.shutdown();
    }
    private int count(Material material){return Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).filter(item->item.getType()==material).mapToInt(ItemStack::getAmount).sum();}
    private static ItemStack invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{var method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return(ItemStack)method.invoke(target,args);}
    private void snapshot(String id,ItemStack item){var meta=item.getItemMeta();samples.put(id,Map.of("material",item.getType().name(),"name",GsonComponentSerializer.gson().serialize(item.effectiveName()),"lore",meta.lore().stream().map(GsonComponentSerializer.gson()::serialize).toList()));}
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
    public void interaction(org.bukkit.event.player.PlayerInteractEvent event){if(event.getPlayer().getName().equals("I_Player"))getLogger().info("Native interaction "+event.getAction()+" block="+(event.getClickedBlock()==null?"none":event.getClickedBlock().getType())+" blockUse="+event.useInteractedBlock()+" itemUse="+event.useItemInHand());}
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.LOWEST)
    public void beforeInteraction(org.bukkit.event.player.PlayerInteractEvent event){if(event.getPlayer().getName().equals("I_Player"))getLogger().info("Initial interaction block="+(event.getClickedBlock()==null?"none":event.getClickedBlock().getType())+" blockUse="+event.useInteractedBlock()+" itemUse="+event.useItemInHand()+" mode="+event.getPlayer().getGameMode());}
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
    public void opened(org.bukkit.event.inventory.InventoryOpenEvent event){if(event.getPlayer().getName().equals("I_Player"))getLogger().info("Native window "+event.getInventory().getType()+" cancelled="+event.isCancelled());}
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
    public void broken(org.bukkit.event.block.BlockBreakEvent event){if(event.getPlayer().getName().equals("I_Player"))getLogger().info("Native break "+event.getBlock().getType()+" cancelled="+event.isCancelled()+" drops="+event.isDropItems()+" tool="+event.getPlayer().getInventory().getItemInMainHand().getType());}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);assertions++;if(checks.size()<100||!message.startsWith("Input")&&!message.startsWith("Native")&&!message.startsWith("Template")&&!message.startsWith("Idempotent")&&!message.startsWith("Persistent")&&!message.startsWith("Quantity")&&!message.startsWith("Ordinary")&&!message.startsWith("Catalog"))checks.add(message);}
    private void later(long ticks,Step step){Bukkit.getScheduler().runTaskLater(this,()->{try{step.run();}catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"Item template probe failed",error);try{Files.writeString(Path.of("item-template-result.txt"),"FAIL "+error+"\n"+String.join("\n",checks));}catch(Exception ignored){}Bukkit.shutdown();}},ticks);}
    @FunctionalInterface private interface Step{void run()throws Exception;}
}
