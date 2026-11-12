package net.nerdsmp.probe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.forge.GemType;
import net.nerdsmp.paper.item.PaperItemService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Real Paper metadata, recipe predicates and native furnace transactions. Disposable loopback server only. */
public final class MetallurgyProbe extends JavaPlugin implements Listener {
    private NerdSmpPlugin core;
    private final List<Block> furnaces = new ArrayList<>();
    private Block cancelled;
    private int checks;
    private Runnable statsCheck;
    private Runnable metricsCheck;
    private Runnable behaviorCheck;

    @Override public void onEnable() {
        if (!Boolean.getBoolean("nerdsmp.metallurgyProbe") || !Bukkit.getIp().equals("127.0.0.1"))
            throw new IllegalStateException("Disposable loopback probe only");
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, this::run, 40);
    }
    @EventHandler public void accelerate(FurnaceStartSmeltEvent event) {
        if (furnaces.contains(event.getBlock())) event.setTotalCookTime(2);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void cancel(FurnaceSmeltEvent event) {
        if (event.getBlock().equals(cancelled)) event.setCancelled(true);
    }
    private ItemStack item(String id, int amount) {
        var item = core.items().create(new ItemId(id)); item.setAmount(amount); return item;
    }
    private void run() {
        try {
            core = (NerdSmpPlugin) Bukkit.getPluginManager().getPlugin("NerdSMP");
            check(core != null && core.isEnabled(), "core plugin enabled");
            var items = core.items();
            var plain = PlainTextComponentSerializer.plainText();
            for (ItemId id : items.itemIds()) {
                ItemStack item = items.create(id);
                if (items.gear(item).orElseThrow().slot() != PaperItemService.GearSlot.NONE) continue;
                check(items.socketCapacity(item) == 0 && items.slotTypes(item).isEmpty(), "no sockets on " + id.value());
                check(item.getItemMeta().lore().stream().map(plain::serialize).noneMatch(line -> line.contains("Gemstone Slots") || line.contains("Empty Combat Slot")),
                    "material tooltip has no socket section: " + id.value());
            }
            ItemStack legacy = item("gloomsteel_ore", 17); var meta = legacy.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(core, "socket_capacity"), PersistentDataType.INTEGER, 2);
            meta.getPersistentDataContainer().set(new NamespacedKey(core, "item_render"), PersistentDataType.INTEGER, 0);
            legacy.setItemMeta(meta);
            ItemStack upgraded = items.upgrade(legacy).orElseThrow();
            check(upgraded.getAmount() == 17 && upgraded.getType() == Material.RAW_IRON && items.hasValidSockets(upgraded), "legacy ore refresh preserves quantity and normalizes empty capacity");
            check(items.socket(upgraded, GemType.RUBY, 1) == PaperItemService.UpgradeResult.NOT_GEAR, "ore cannot be socketed");
            check(items.socketCapacity(item("gloomforged_pick", 1)) == 2 && items.socketCapacity(item("sunforge_pickaxe", 1)) == 3,
                "existing equipment capacities preserved");

            var furnaceRecipe = (FurnaceRecipe) Bukkit.getRecipe(new NamespacedKey(core, "metallurgy_gloomsteel_smelting"));
            var blastRecipe = (BlastingRecipe) Bukkit.getRecipe(new NamespacedKey(core, "metallurgy_gloomsteel_blasting"));
            check(furnaceRecipe != null && blastRecipe != null && furnaceRecipe.getCookingTime() == blastRecipe.getCookingTime() * 2, "registered furnace and faster blasting routes");
            check(furnaceRecipe.getInputChoice().test(upgraded), "custom recipe accepts an upgraded legacy stack");
            check(!furnaceRecipe.getInputChoice().test(new ItemStack(Material.RAW_IRON)), "ordinary raw iron cannot refine into Gloomsteel");
            ItemStack renamed = item("gloomsteel_ore", 3); meta = renamed.getItemMeta();
            meta.displayName(Component.text("Old player's name")); renamed.setItemMeta(meta);
            check(furnaceRecipe.getInputChoice().test(renamed), "identity survives changed display metadata");

            var alloy = (ShapedRecipe) Bukkit.getRecipe(new NamespacedKey(core, "crafted_metallurgy_gloomsteel_alloy"));
            // Bukkit reconstructs native recipes with fresh symbol names; address ingredients by their cells.
            var customChoice = alloy.getChoiceMap().get(alloy.getShape()[0].charAt(0));
            var ordinaryChoice = alloy.getChoiceMap().get(alloy.getShape()[0].charAt(1));
            check(customChoice.test(item("gloomsteel_ingot", 1)), "alloy accepts authenticated custom ingots");
            check(!customChoice.test(new ItemStack(Material.IRON_INGOT)), "ordinary ingots cannot replace Gloomsteel");
            check(!customChoice.test(item("tideforged_alloy", 1)), "a different custom item sharing iron's base cannot substitute");
            check(!ordinaryChoice.test(item("gloomsteel_ingot", 1)), "custom ingots cannot replace an ordinary ingredient");
            var world = Bukkit.getWorlds().getFirst();
            DropStackingProbe.run(core, world, this::check);
            HostileSpawnProbe.run(core, world, this::check);
            MobNameplateProbe.run(core, world, this::check);
            behaviorCheck = EncounterBehaviorProbe.begin(core, world, this::check);
            statsCheck = StatsAndExperienceProbe.begin(core, world, this::check);
            metricsCheck = MetricsProbe.begin(core, this::check);
            ItemStack[] grid = {item("gloomsteel_ingot",1), new ItemStack(Material.IRON_INGOT), item("gloomsteel_ingot",1),
                null, item("blasting_powder",1), null, null, null, null};
            check(Bukkit.getCraftingRecipe(grid, world) instanceof org.bukkit.Keyed keyed && keyed.getKey().equals(alloy.getKey()), "native crafting lookup resolves the alloy pattern");
            grid[0] = new ItemStack(Material.IRON_INGOT);
            var refused = Bukkit.getCraftingRecipe(grid, world);
            check(!(refused instanceof org.bukkit.Keyed keyed) || !keyed.getKey().equals(alloy.getKey()), "native crafting lookup rejects substituted ore");

            world.setChunkForceLoaded(0, 0, true);
            furnace(Material.FURNACE, renamed, null);
            furnace(Material.BLAST_FURNACE, item("cinder_shard", 3), null);
            furnace(Material.FURNACE, new ItemStack(Material.RAW_IRON, 3), null);
            furnace(Material.FURNACE, item("gloomsteel_ore", 3), item("gloomsteel_ingot", 64));
            furnace(Material.FURNACE, item("gloomsteel_ore", 3), item("tideforged_alloy", 1));
            cancelled = furnace(Material.FURNACE, item("gloomsteel_ore", 3), null);
            ItemStack counterfeit = item("gloomsteel_ore", 3); meta = counterfeit.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(core, "item_origin"), PersistentDataType.STRING, "editor-preview"); counterfeit.setItemMeta(meta);
            furnace(Material.FURNACE, counterfeit, null);
            furnace(Material.FURNACE, item("tidepearl", 3), null);
            furnace(Material.FURNACE, item("sigil_shard", 3), null);
            furnace(Material.FURNACE, item("thornheart_sap", 3), null);
            furnace(Material.SMOKER, new ItemStack(Material.BEEF,3), null);
            Bukkit.getScheduler().runTaskLater(this, this::finish, 100);
        } catch (Throwable error) { fail(error); }
    }
    private Block furnace(Material kind, ItemStack source, ItemStack result) {
        Block block = Bukkit.getWorlds().getFirst().getBlockAt(2 + furnaces.size(), 121, 2);
        block.setType(kind, false); furnaces.add(block);
        Furnace furnace = (Furnace) block.getState();
        furnace.getInventory().setSmelting(source); furnace.getInventory().setFuel(new ItemStack(Material.COAL));
        furnace.getInventory().setResult(result);
        return block;
    }
    private void finish() {
        try {
            checkOutput(0, "gloomsteel_ingot"); checkOutput(1, "cindersteel_ingot");
            var ordinary = ((Furnace) furnaces.get(2).getState()).getInventory();
            check(ordinary.getSmelting() == null && ordinary.getResult().equals(new ItemStack(Material.IRON_INGOT, 3)), "native ordinary iron smelting remains vanilla");
            for (int index : new int[]{3,4,5,6}) {
                var blocked = ((Furnace) furnaces.get(index).getState()).getInventory();
                check(blocked.getSmelting().getAmount() == 3, "blocked/full/canceled/refused smelt spends no input: " + index);
            }
            checkOutput(7, "tideglass"); checkOutput(8, "tempered_sigil"); checkOutput(9, "briar_resin");
            var cooked = ((Furnace) furnaces.get(10).getState()).getInventory();
            check(cooked.getResult()!=null && cooked.getResult().getType()==Material.COOKED_BEEF && cooked.getResult().getAmount()==3,
                "native smoker produces cooked food for verified Cooking XP");
            for (int index : new int[]{0,1,2,7,8,9,10}) {
                var furnace=(Furnace)furnaces.get(index).getState(false);
                var encoded=furnace.getPersistentDataContainer().get(new NamespacedKey(core,"ore_smelting_v1"),PersistentDataType.STRING);
                var receipt=net.nerdsmp.paper.gathering.OreSmeltCredits.decode(encoded);
                check(receipt.credits()==3 && receipt.observed()==3, "native processing persists exactly three collectible skill-XP credits: "+index);
                if(index==0)check(receipt.output().equals("item:gloomsteel_ingot"),"custom Smithing receipt retains catalog identity");
                if(index==10)check(receipt.output().equals("COOKED_BEEF"),"cooking receipt remains distinct from Smithing");
            }
            if(statsCheck!=null)statsCheck.run();
            if(metricsCheck!=null)metricsCheck.run();
            if(behaviorCheck!=null)behaviorCheck.run();
            Files.writeString(Path.of("metallurgy-probe-result.txt"), "PASS " + checks + " checks\n");
            getLogger().info("METALLURGY_PROBE_PASS " + checks + " checks"); Bukkit.shutdown();
        } catch (Throwable error) { fail(error); }
    }
    private void checkOutput(int index, String id) {
        var inventory = ((Furnace) furnaces.get(index).getState()).getInventory();
        ItemStack result = inventory.getResult();
        check(inventory.getSmelting() == null && result != null && result.getAmount() == 3
            && core.items().isAuthentic(result) && core.items().describe(result).orElseThrow().itemId().equals(new ItemId(id)),
            "native furnace consumes exactly three inputs and emits three " + id);
    }
    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description); checks++;
    }
    private void fail(Throwable error) {
        getLogger().log(java.util.logging.Level.SEVERE, "METALLURGY_PROBE_FAILED", error);
        try { Files.writeString(Path.of("metallurgy-probe-result.txt"), "FAIL " + error + "\n"); } catch (Exception ignored) { }
        Bukkit.shutdown();
    }
}
