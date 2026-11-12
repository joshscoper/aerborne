package net.nerdsmp.probe;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.item.*;
import net.nerdsmp.paper.npc.NpcContent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Seeded, disposable UI and compatibility verification; no production mutations. */
public final class FoodTooltipProbe extends JavaPlugin {
    private NerdSmpPlugin core; private Player player; private World world; private int waits;
    private final List<String> checks = new ArrayList<>();
    private final Map<String,Object> samples = new LinkedHashMap<>();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    @Override public void onEnable() {
        if (!Boolean.getBoolean("nerdsmp.foodTooltipProbe") || !Bukkit.getIp().equals("127.0.0.1")) throw new IllegalStateException("Disposable loopback fixture only");
        core = (NerdSmpPlugin) Bukkit.getPluginManager().getPlugin("NerdSMP"); later(100, this::ready);
    }
    private void ready() throws Exception {
        player = Bukkit.getPlayerExact("T_Player");
        if (++waits > 120) throw new AssertionError("Client deadline");
        if (player == null || !core.presentation().progressionReady(player)) { later(20, this::ready); return; }
        player.setGameMode(GameMode.SURVIVAL); player.setInvulnerable(true); player.getInventory().clear();
        world = new WorldCreator("TooltipArena").type(WorldType.FLAT).generateStructures(false).createWorld();
        world.setDifficulty(Difficulty.PEACEFUL); world.setTime(6000);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) { world.getBlockAt(x,159,z).setType(Material.STONE); for (int y=160;y<=163;y++) world.getBlockAt(x,y,z).setType(Material.AIR); }
        player.teleport(new Location(world,.5,160,.5)); core.watchdog().grantGrace(player,60000);
        check(core.getConfig().getString("anti-cheat.mode").equals("alerts-only") && !core.getConfig().getBoolean("anti-cheat.ml.autonomous"), "Watchdog configuration remains alert-only with model autonomy disabled");
        int foods=0;
        for (Material material : Material.values()) {
            if (!material.isItem() || material.getDefaultData(DataComponentTypes.FOOD) == null) continue;
            ItemStack styled = FoodItems.upgrade(new ItemStack(material)).orElseThrow(); var meta=styled.getItemMeta();
            check(meta.itemName().color().equals(NamedTextColor.GOLD) && meta.lore().get(1).color().equals(NamedTextColor.GREEN), material+" uses the gold name and green provisions category");
            check(text(styled).contains("Well Fed | 5m") && text(styled).contains("+25% recovery outside combat."), material+" shows actual configured benefit and duration");
            check(meta.lore().stream().allMatch(line->line.decoration(TextDecoration.ITALIC)==TextDecoration.State.FALSE), material+" has non-italic lore");
            check(FoodItems.ordinary(styled) && !CraftingGuard.protectedIngredient(styled), material+" remains an ordinary ingredient");
            check(FoodItems.upgrade(styled).isEmpty(), material+" template is idempotent");
            ItemStack persisted=ItemStack.deserializeBytes(styled.serializeAsBytes());
            check(persisted.isSimilar(styled) && FoodItems.ordinary(persisted) && FoodItems.upgrade(persisted).isEmpty(), material+" survives native item serialization without template drift");
            foods++;
        }
        check(foods>30,"Every native food material was covered ("+foods+")"); samples.put("food-material-count",foods);
        ItemStack carrot=styled(Material.CARROT,8); ItemStack other=styled(Material.CARROT,8);
        check(carrot.isSimilar(other),"Separately styled food stacks merge normally");
        ItemStack renamed=carrot.clone(); renamed.editMeta(meta->meta.displayName(Component.text("Josh's carrot")));
        check(!FoodItems.ordinary(renamed) && CraftingGuard.protectedIngredient(renamed) && FoodItems.upgrade(renamed).isEmpty(),"Renamed provisions stay protected and retain their custom name");
        ItemStack extra=carrot.clone(); extra.editMeta(meta->{var lore=new ArrayList<>(meta.lore());lore.add(Component.text("Custom bonus"));meta.lore(lore);});
        check(!FoodItems.ordinary(extra) && CraftingGuard.protectedIngredient(extra),"Additional custom lore cannot bypass ingredient protection");
        ItemStack tagged=carrot.clone(); tagged.editMeta(meta->meta.getPersistentDataContainer().set(new NamespacedKey(core,"special_item"),PersistentDataType.INTEGER,1));
        check(!FoodItems.ordinary(tagged) && CraftingGuard.protectedIngredient(tagged),"Unrelated custom tags are not stripped or treated as ordinary food");
        ItemStack rawName=new ItemStack(Material.APPLE); rawName.editMeta(meta->meta.displayName(Component.text("Keepsake")));
        var kept=FoodItems.upgrade(rawName).orElseThrow(); check(PLAIN.serialize(kept.getItemMeta().displayName()).equals("Keepsake") && !text(kept).contains("PROVISIONS"),"Existing custom food presentation is preserved");
        check(net.nerdsmp.paper.gathering.CropReplanting.seedSlot(new ItemStack[]{carrot},Material.CARROT)==0,"Templated carrots remain valid replanting seeds");
        var shopClass=Class.forName("net.nerdsmp.paper.npc.ShopItems"); var create=shopClass.getDeclaredMethod("create",PaperItemService.class,NpcContent.ShopProduct.class,int.class);create.setAccessible(true);
        var product=new NpcContent.ShopProduct("carrot",Material.CARROT,null,1,10,1,10,0,"Test carrot");
        var vendor=(ItemStack)create.invoke(null,core.items(),product,8); check(vendor.isSimilar(carrot),"Vendor preview, delivery and buyback use the same styled food identity");
        var ingredientClass=Class.forName("net.nerdsmp.paper.item.RecipeIngredient"); var constructor=ingredientClass.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var ingredient=constructor.newInstance(Material.CARROT,null); var matches=ingredientClass.getDeclaredMethod("matches",ItemStack.class,PaperItemService.class);matches.setAccessible(true);
        check((boolean)matches.invoke(ingredient,carrot,core.items()) && !(boolean)matches.invoke(ingredient,renamed,core.items()),"Predicate recipes accept ordinary provisions but reject renamed ones");
        var configure=FoodItems.class.getDeclaredMethod("configure",boolean.class,RecoveryRules.class); configure.setAccessible(true);
        configure.invoke(null,true,new RecoveryRules(8,5,90,50)); var refreshed=FoodItems.upgrade(carrot).orElseThrow();
        check(FoodItems.ordinary(carrot) && text(refreshed).contains("Well Fed | 1m 30s") && text(refreshed).contains("+50%"),"Old stamped food refreshes safely when configured buffs change");
        configure.invoke(null,true,core.recovery().rules()); check(FoodItems.upgrade(refreshed).orElseThrow().isSimilar(carrot),"Refreshing presentation never stacks stale lines or changes food quality");
        for(Material material:List.of(Material.BREAD,Material.CARROT,Material.COOKED_BEEF,Material.GOLDEN_APPLE))snapshot(styled(material,1));
        ItemStack meal=Bukkit.getRecipe(new NamespacedKey(core,"meal_orchard_loaf")).getResult();
        check(text(meal).contains("HARVEST MEAL") && !text(meal).contains("PROVISIONS") && !FoodItems.ordinary(meal),"Prepared Cooking meals retain their own distinct template");
        snapshot(meal); player.getInventory().setItem(0,styled(Material.BREAD,2));player.getInventory().setHeldItemSlot(0);
        later(15,()->{player.sendMessage("T_EAT");later(55,this::eaten);});
    }
    private void eaten() throws Exception {
        check(player.getInventory().getItemInMainHand().getAmount()==1 && core.recovery().food(player).active(System.currentTimeMillis()),"A native full-hunger right-click consumes one templated bread and grants Well Fed");
        check(player.getFoodLevel()==20,"Eating templated food preserves full hunger");
        player.getInventory().clear(); player.getInventory().setItem(0,styled(Material.CARROT,1));player.getInventory().setItem(1,new ItemStack(Material.GOLD_NUGGET,8));
        world.getBlockAt(1,160,0).setType(Material.CRAFTING_TABLE);
        later(15,()->{player.sendMessage("T_CRAFT");later(100,this::crafted);});
    }
    private void crafted() throws Exception {
        ItemStack crafted=Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).filter(item->item.getType()==Material.GOLDEN_CARROT).findFirst().orElseThrow();
        check(text(crafted).contains("HARVEST MEAL") && !text(crafted).contains("PROVISIONS"),"Real crafting from a templated carrot produces the Cooking staple template");
        check(CustomRecipeService.ordinaryIngredient(crafted) && !CraftingGuard.protectedIngredient(crafted),"Crafted staple does not inherit provision tags that would block later recipes");
        check(player.getInventory().getContents().length>0 && Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).noneMatch(item->item.getType()==Material.CARROT||item.getType()==Material.GOLD_NUGGET),"Native crafting consumes the exact carrot and eight nuggets");
        Files.writeString(Path.of("tooltip-samples.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(samples));
        Files.writeString(Path.of("tooltip-result.txt"),"PASS "+checks.size()+"\n"+String.join("\n",checks)); Bukkit.shutdown();
    }
    private static ItemStack styled(Material material,int amount){return FoodItems.upgrade(new ItemStack(material,amount)).orElseThrow();}
    private static String text(ItemStack item){var lore=item.getItemMeta().lore();return lore==null?"":String.join("\n",lore.stream().map(PLAIN::serialize).toList());}
    private void snapshot(ItemStack item){var meta=item.getItemMeta();samples.put(item.getType()+"-"+samples.size(),Map.of("material",item.getType().name(),"name",GsonComponentSerializer.gson().serialize(meta.displayName()),"lore",meta.lore().stream().map(GsonComponentSerializer.gson()::serialize).toList()));}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks.add(message);getLogger().info("PASS "+message);}
    private void later(long ticks,Step step){Bukkit.getScheduler().runTaskLater(this,()->{try{step.run();}catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"Tooltip probe failed",error);try{Files.writeString(Path.of("tooltip-result.txt"),"FAIL "+error+"\n"+String.join("\n",checks));}catch(Exception ignored){}Bukkit.shutdown();}},ticks);}
    @FunctionalInterface private interface Step{void run()throws Exception;}
}
