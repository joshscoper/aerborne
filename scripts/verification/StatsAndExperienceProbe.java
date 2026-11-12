package net.nerdsmp.probe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.item.CraftingReceipt;
import net.nerdsmp.paper.item.PlayerStatsService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Controlled player facade backed by real Paper health attributes and item metadata; no client login is simulated. */
final class StatsAndExperienceProbe {
    @SuppressWarnings("unchecked")
    static Runnable begin(NerdSmpPlugin core, World world, BiConsumer<Boolean,String> check) throws Exception {
        UUID id = UUID.randomUUID();
        ArmorStand body = world.spawn(new org.bukkit.Location(world, 1, 122, 1), ArmorStand.class, stand -> {
            stand.setGravity(false); stand.setInvulnerable(true); stand.setVisible(false);
        });
        body.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20); body.setHealth(15);
        ItemStack[] storage = new ItemStack[36], armor = new ItemStack[4];
        double[] scale = {20}; boolean[] scaled = {false}; Component[] action = {Component.empty()};
        org.bukkit.inventory.Inventory[] opened = {null};
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(), new Class<?>[]{PlayerInventory.class},
            (proxy,method,args) -> switch (method.getName()) {
                case "getItemInMainHand" -> storage[0]; case "getItemInOffHand" -> null;
                case "getHelmet" -> armor[0]; case "getChestplate" -> armor[1]; case "getLeggings" -> armor[2]; case "getBoots" -> armor[3];
                case "getStorageContents", "getContents" -> storage.clone();
                default -> throw new UnsupportedOperationException("Stats inventory: " + method.getName());
            });
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy,method,args) -> switch (method.getName()) {
                case "getUniqueId" -> id; case "getName" -> "VitalityProbe"; case "getInventory" -> inventory;
                case "getGameMode" -> GameMode.SURVIVAL; case "isOnline" -> true; case "isDead" -> false;
                case "getWorld" -> world; case "getLocation" -> body.getLocation();
                case "getAttribute" -> body.getAttribute((Attribute) args[0]);
                case "getHealth" -> body.getHealth(); case "setHealth" -> { body.setHealth((Double) args[0]); yield null; }
                case "getAbsorptionAmount" -> body.getAbsorptionAmount();
                case "isHealthScaled" -> scaled[0]; case "getHealthScale" -> scale[0];
                case "setHealthScaled" -> { scaled[0]=(Boolean)args[0]; yield null; }
                case "setHealthScale" -> { scale[0]=(Double)args[0]; yield null; }
                case "sendActionBar" -> { action[0]=(Component)args[0]; yield null; }
                case "sendMessage" -> null;
                case "openInventory" -> { opened[0]=(org.bukkit.inventory.Inventory)args[0]; yield null; }
                case "getItemOnCursor" -> new ItemStack(Material.AIR);
                case "hashCode" -> id.hashCode(); case "equals" -> proxy==args[0];
                case "toString" -> "VitalityProbe";
                default -> throw new UnsupportedOperationException("Stats actor: " + method.getName());
            });
        var field = core.presentation().getClass().getDeclaredField("progressionSnapshots"); field.setAccessible(true);
        var snapshots = (Map<UUID,ProgressionService.ProgressionSnapshot>) field.get(core.presentation());
        var skills = new EnumMap<Skill,ProgressionService.SkillProgress>(Skill.class);
        skills.putAll(ProgressionService.ProgressionSnapshot.empty().skills());
        skills.put(Skill.SMITHING, new ProgressionService.SkillProgress(115_200,33)); // Adventurer 25.
        snapshots.put(id, new ProgressionService.ProgressionSnapshot(skills, Set.of(), Set.of(), Instant.EPOCH));
        armor[1] = core.items().create(new ItemId("gloomforged_plate"));
        var service = core.playerStats(); service.refresh(player);
        var health = body.getAttribute(Attribute.MAX_HEALTH);
        check.accept(Math.abs(health.getValue()-74)<1E-9 && Math.abs(body.getHealth()-55.5)<1E-9,
            "real max health combines Adventurer vitality and item-level armor; initial login preserves health ratio");
        check.accept(scaled[0] && scale[0]==20, "native player display remains ten scaled hearts above twenty real health");
        check.accept(core.items().gear(armor[1]).orElseThrow().stats().get("max-health")==6,
            "armor vitality is a real rendered equipment stat");
        var foreign = new NamespacedKey("probe_other_plugin","health");
        health.addTransientModifier(new AttributeModifier(foreign,10,AttributeModifier.Operation.ADD_NUMBER));
        body.setHealth(10); armor[1]=null; service.refresh(player);
        check.accept(Math.abs(health.getValue()-78)<1E-9 && body.getHealth()==10 && health.getModifier(foreign)!=null,
            "gear changes preserve other health modifiers and never heal");
        armor[1]=core.items().create(new ItemId("gloomforged_plate")); service.refresh(player);
        check.accept(Math.abs(health.getValue()-84)<1E-9 && body.getHealth()==10, "reequipping armor does not refill health");
        PlayerStatsService.message(player, Component.text("Smithing +16 XP"));
        String text = PlainTextComponentSerializer.plainText().serialize(action[0]);
        check.accept(text.contains("10/84") && text.contains("6 Defense") && text.contains("Smithing +16 XP"),
            "action bar combines exact health, current defense and a temporary XP notice");
        check.accept(Math.abs(service.snapshot(player).effectiveHealth()-89.04)<1E-9, "stats effective health matches active attack defense");
        core.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.STATS);
        check.accept(opened[0] != null && opened[0].getSize()==54 && opened[0].getItem(10).getType()==Material.APPLE
            && opened[0].getItem(12).getType()==Material.IRON_CHESTPLATE && opened[0].getItem(32).getType()==Material.AMETHYST_SHARD,
            "stats view creates the native vitality, defense and set panels");
        core.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.CHARACTER);
        check.accept(opened[0].getItem(24).getType()==Material.APPLE && core.getCommand("stats")!=null,
            "stats command is registered and Character navigation retains its stats tile");
        for (var definition : core.recipes().recipes()) {
            var recipe = org.bukkit.Bukkit.getRecipe(new NamespacedKey(core,"crafted_"+definition.id()));
            if (definition.id().startsWith("metallurgy_")) check.accept(core.recipes().craftingExperienceSkill(recipe).orElseThrow()==Skill.SMITHING,
                "metallurgy crafting trains Smithing regardless of its unlock skill: " + definition.id());
        }
        ItemStack raw = core.items().create(new ItemId("gloomsteel_ore"));
        ItemStack ingot = core.items().create(new ItemId("gloomsteel_ingot"));
        check.accept(core.recipes().refiningWork(raw,ingot).orElseThrow().experience()>0,
            "authenticated custom refining resolves positive Smithing XP");
        check.accept(core.recipes().refiningWork(new ItemStack(Material.RAW_IRON),ingot).isEmpty(),
            "ordinary ores cannot borrow custom refining XP");
        ItemStack qualityGear=core.items().create(new ItemId("gloomsteel_hauberk"));
        ItemStack catalyst=new ItemStack(Material.IRON_INGOT,8);
        check.accept(core.forge().improveQuality(player,qualityGear,catalyst)==net.nerdsmp.paper.forge.ArcaneForgeService.QualityResult.APPLIED
            && catalyst.getAmount()==0 && core.items().quality(qualityGear)==net.nerdsmp.paper.item.GearQuality.TEMPERED,
            "quality improvement reaches its XP commit only after the real catalyst is consumed and quality advances");

        ItemStack[] matrix = {new ItemStack(Material.IRON_INGOT,5),null,null,null,null,null,null,null,null};
        CraftingInventory crafting = (CraftingInventory) Proxy.newProxyInstance(CraftingInventory.class.getClassLoader(),new Class<?>[]{CraftingInventory.class},
            (proxy,method,args)-> { if(method.getName().equals("getMatrix"))return matrix.clone(); throw new UnsupportedOperationException(method.getName()); });
        ItemStack result = core.items().create(new ItemId("gloomsteel_alloy"));
        var receipt = new CraftingReceipt(crafting,player,result,true);
        matrix[0].setAmount(2); storage[2]=result.asQuantity(3);
        check.accept(receipt.crafts(crafting,player)==3, "confirmed bulk crafting counts three consumed operations and actual output");
        var full = new CraftingReceipt(crafting,player,result,true);
        check.accept(full.crafts(crafting,player)==0, "unchanged ingredients and a full/failed output path cannot award crafting XP");

        return () -> {
            service.refresh(player);
            check.accept(!PlainTextComponentSerializer.plainText().serialize(action[0]).contains("Smithing +16 XP"), "temporary XP notices expire without removing vitals");
            double ratio=body.getHealth()/health.getValue();
            service.quit(new org.bukkit.event.player.PlayerQuitEvent(player,Component.empty()));
            check.accept(health.getModifier(new NamespacedKey(core,"player_vitality"))==null && health.getModifier(foreign)!=null,
                "quit restores only this plugin's modifier");
            check.accept(!scaled[0] && scale[0]==20 && Math.abs(body.getHealth()/health.getValue()-ratio)<1E-6,
                "quit restores previous display and preserves health fraction for reconnect (native float health)");
            snapshots.remove(id); body.remove();
        };
    }
}
