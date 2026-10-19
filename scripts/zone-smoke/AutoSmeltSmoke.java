package net.nerdsmp.smoke;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gathering.AutoSmeltService;
import net.nerdsmp.paper.listener.SkillListener;
import net.nerdsmp.paper.resource.HarvestClaims;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Controlled native-event receipt exercise on real Paper blocks/entities; not an authenticated-client mining test. */
public final class AutoSmeltSmoke {
    private AutoSmeltSmoke() { }
    public static void run(NerdSmpPlugin core, JavaPlugin host, World world, Runnable completion) throws Exception {
        Fixture fixture = new Fixture(core, world);
        try {
            var iron = fixture.harvest(Material.IRON_ORE, new ItemStack(Material.RAW_IRON, 4), fixture.pick, false);
            fixture.service.onDrops(iron.drops);
            check(iron.item.getItemStack().equals(new ItemStack(Material.IRON_INGOT, 4)), "Fortune iron quantity is retained");
            fixture.service.onDrops(iron.drops); // Exact event replay.
            fixture.service.onDrops(new BlockDropItemEvent(iron.block, iron.before, fixture.player, List.of(iron.item)));
            // The same original receipt still serves Mining; the signature channel never steals it.
            var mining = core.resources().claim(iron.broken, HarvestClaims.Channel.SKILLS).orElseThrow();
            core.skills().awardActivity(fixture.player, Skill.MINING,
                core.getConfig().getLong("progression.xp.mining-ore", 12), mining.operationId());

            var pickup = new EntityPickupItemEvent(fixture.player, iron.item, 0);
            fixture.service.onPickup(pickup);
            var hopper = new InventoryPickupItemEvent(Bukkit.createInventory(null, 9), iron.item);
            fixture.service.onHopper(hopper);
            var merge = new ItemMergeEvent(iron.item, iron.item);
            fixture.service.onMerge(merge);
            check(pickup.isCancelled() && hopper.isCancelled() && merge.isCancelled(), "unverified outputs are fenced from pickup and merging");

            var copper = fixture.harvest(Material.DEEPSLATE_COPPER_ORE, new ItemStack(Material.RAW_COPPER, 20), fixture.pick, false);
            fixture.service.onDrops(copper.drops);
            check(copper.item.getItemStack().equals(new ItemStack(Material.COPPER_INGOT, 20)), "Fortune copper quantity is retained");
            var gold = fixture.harvest(Material.GOLD_ORE, new ItemStack(Material.RAW_GOLD, 4), fixture.pick, false);
            fixture.service.onDrops(gold.drops);
            var debris = fixture.harvest(Material.ANCIENT_DEBRIS, new ItemStack(Material.ANCIENT_DEBRIS), fixture.pick, false);
            fixture.service.onDrops(debris.drops);
            check(debris.item.getItemStack().getType() == Material.NETHERITE_SCRAP, "legitimate debris becomes scrap");

            var placed = fixture.harvest(Material.IRON_ORE, new ItemStack(Material.RAW_IRON), fixture.pick, true);
            fixture.service.onDrops(placed.drops);
            check(placed.item.getItemStack().getType() == Material.RAW_IRON, "placed ore never converts or grants processing XP");
            ItemStack silk = fixture.pick.clone(); silk.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
            var silkDrop = fixture.harvest(Material.IRON_ORE, new ItemStack(Material.IRON_ORE), silk, false);
            fixture.service.onDrops(silkDrop.drops);
            check(silkDrop.item.getItemStack().getType() == Material.IRON_ORE, "Silk Touch keeps the actual block drop");
            ItemStack named = new ItemStack(Material.RAW_IRON); var meta = named.getItemMeta();
            meta.itemName(net.kyori.adventure.text.Component.text("Authored metal")); named.setItemMeta(meta);
            var authored = fixture.harvest(Material.IRON_ORE, named, fixture.pick, false);
            fixture.service.onDrops(authored.drops);
            check(authored.item.getItemStack().equals(named), "named or authored drops remain untouched");
            var ordinary = fixture.harvest(Material.IRON_ORE, new ItemStack(Material.RAW_IRON), new ItemStack(Material.NETHERITE_PICKAXE), false);
            fixture.service.onDrops(ordinary.drops);
            var sealed = fixture.harvest(Material.IRON_ORE, new ItemStack(Material.RAW_IRON),
                core.items().createLoot(new ItemId("sunforge_pickaxe"), 3, true, 44), false);
            fixture.service.onDrops(sealed.drops);
            check(ordinary.item.getItemStack().getType() == Material.RAW_IRON
                && sealed.item.getItemStack().getType() == Material.RAW_IRON, "ordinary and unidentified picks have no Furnace Heart ability");
            var canceled = fixture.harvest(Material.IRON_ORE, new ItemStack(Material.RAW_IRON), fixture.pick, false);
            canceled.drops.setCancelled(true); fixture.service.onDrops(canceled.drops);
            check(canceled.item.getItemStack().getType() == Material.RAW_IRON, "already-canceled drop is not transformed");
            var late = fixture.harvest(Material.IRON_ORE, new ItemStack(Material.RAW_IRON), fixture.pick, false);
            fixture.service.onDrops(late.drops); late.drops.setCancelled(true);
            var lateBreak = fixture.harvest(Material.IRON_ORE, new ItemStack(Material.RAW_IRON), fixture.pick, false);
            lateBreak.broken.setCancelled(true); fixture.service.onDrops(lateBreak.drops);
            check(lateBreak.item.getItemStack().getType() == Material.RAW_IRON, "late break cancellation prevents conversion");

            var lastDurability = fixture.harvest(Material.IRON_ORE, new ItemStack(Material.RAW_IRON), fixture.pick, false);
            fixture.storage[0] = new ItemStack(Material.AIR); // Native wear may consume the captured pick before drop dispatch.
            fixture.service.onDrops(lastDurability.drops);
            check(lastDurability.item.getItemStack().getType() == Material.IRON_INGOT, "last-durability break retains the authenticated captured tool");
            ItemStack scholarlyPick = fixture.pick.clone();
            core.items().enchant(scholarlyPick, net.nerdsmp.paper.forge.ArcaneEnchantment.SCHOLAR, 2);
            var scholarly = fixture.harvest(Material.IRON_ORE, new ItemStack(Material.RAW_IRON, 4), scholarlyPick, false);
            fixture.service.onDrops(scholarly.drops);
            var delayed = fixture.harvest(Material.IRON_ORE, new ItemStack(Material.RAW_IRON), fixture.pick, false);

            Bukkit.getScheduler().runTaskLater(host, () -> {
                try {
                    fixture.service.onDrops(delayed.drops);
                    check(delayed.item.getItemStack().getType() == Material.RAW_IRON, "later-tick drops cannot borrow an old tool receipt");
                    check(late.item.getItemStack().getType() == Material.RAW_IRON, "late drop cancellation restores only our unchanged conversion");
                    var released = new InventoryPickupItemEvent(Bukkit.createInventory(null, 9), iron.item);
                    fixture.service.onHopper(released);
                    check(!released.isCancelled(), "verified output is released after one tick");
                    var progress = core.progression().snapshot(fixture.account).toCompletableFuture().join();
                    long smithing = 5 * xp(core, "iron", 4) + 20 * xp(core, "copper", 4)
                        + 4 * xp(core, "gold", 6) + xp(core, "netherite", 12)
                        + (long)Math.floor(4 * xp(core, "iron", 4) * 1.12);
                    check(progress.skills().get(Skill.SMITHING).experience() == smithing,
                        "Smithing uses captured Scholar bonus once and counts only surviving proven units, not duplicates or refused events");
                    long expectedMining = (long)Math.floor(core.getConfig().getLong("progression.xp.mining-ore", 12) * 1.25);
                    check(progress.skills().get(Skill.MINING).experience() == expectedMining,
                        "ordinary Mining award still applies the Sunforge mining-power bonus");
                    var jobs = core.jobs().snapshot(fixture.account).toCompletableFuture().join();
                    check(jobs.workUnits().get(Skill.MINING) == 1 && jobs.workUnits().get(Skill.SMITHING) == 0,
                        "processing never creates another Smithing job payout");

                    var quitting = fixture.harvest(Material.IRON_ORE, new ItemStack(Material.RAW_IRON), fixture.pick, false);
                    fixture.service.onDrops(quitting.drops);
                    fixture.service.onQuit(new PlayerQuitEvent(fixture.player, net.kyori.adventure.text.Component.empty()));
                    check(quitting.item.getItemStack().getType() == Material.RAW_IRON, "disconnect rolls back unverified converted entities");
                    var closing = fixture.harvest(Material.IRON_ORE, new ItemStack(Material.RAW_IRON), fixture.pick, false);
                    fixture.service.onDrops(closing.drops); fixture.service.close();
                    check(closing.item.getItemStack().getType() == Material.RAW_IRON, "shutdown rolls back unverified converted entities");
                    fixture.close();
                    host.getLogger().info("ZONE_SMOKE_AUTO_SMELT_COMPLETE PASS");
                    completion.run();
                } catch (Throwable failure) {
                    fixture.close();
                    fail(host, failure);
                }
            }, 3);
        } catch (Throwable failure) {
            fixture.close();
            throw failure;
        }
    }
    private static long xp(NerdSmpPlugin core, String metal, long fallback) {
        return Math.clamp(core.getConfig().getLong("progression.xp.smithing-smelt-" + metal, fallback), 0, 1000);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void fail(JavaPlugin host, Throwable failure) {
        host.getLogger().log(java.util.logging.Level.SEVERE, "ZONE_SMOKE_FAILED", failure);
        try { Files.writeString(Path.of("zone-smoke-result.txt"), "FAIL: " + failure + "\n"); }
        catch (Exception ignored) { }
        Bukkit.shutdown();
    }
    private static final class Fixture implements AutoCloseable {
        final NerdSmpPlugin core;
        final World world;
        final AccountId account = AccountId.random();
        final Player player;
        final ItemStack[] storage;
        final ItemStack pick;
        final AutoSmeltService service;
        final List<BlockState> blocks = new ArrayList<>();
        final List<Item> items = new ArrayList<>();
        Fixture(NerdSmpPlugin core, World world) throws Exception {
            this.core = core; this.world = world;
            // Reuse the existing controlled actor, without exposing it to production code.
            Class<?> type = Class.forName("net.nerdsmp.smoke.ZoneSmokePlugin$HoeFixture");
            var constructor = type.getDeclaredConstructor(World.class); constructor.setAccessible(true);
            Object actor = constructor.newInstance(world);
            var playerField = type.getDeclaredField("player"); playerField.setAccessible(true); player = (Player)playerField.get(actor);
            var storageField = type.getDeclaredField("storage"); storageField.setAccessible(true); storage = (ItemStack[])storageField.get(actor);
            core.sessions().join(player.getUniqueId(), account);
            pick = core.items().create(new ItemId("sunforge_pickaxe")); pick.addUnsafeEnchantment(Enchantment.FORTUNE, 3);
            var factory = AutoSmeltService.class.getDeclaredConstructor(NerdSmpPlugin.class, SkillListener.class, Predicate.class);
            factory.setAccessible(true);
            service = factory.newInstance(core, core.skills(), (Predicate<Player>)(candidate -> candidate == player));
        }
        Harvest harvest(Material ore, ItemStack dropped, ItemStack tool, boolean placed) {
            Block block = world.getBlockAt(40 + blocks.size(), 121, 40);
            blocks.add(block.getState()); block.setType(ore, false);
            core.resources().renewManaged(block, ore);
            if (placed) core.resources().onPlace(new BlockPlaceEvent(block, blocks.getLast(), block.getRelative(0, -1, 0),
                new ItemStack(ore), player, true, EquipmentSlot.HAND));
            BlockState before = block.getState(); storage[0] = tool.clone();
            BlockBreakEvent broken = new BlockBreakEvent(block, player);
            service.onBreakCapture(broken); service.onBreakProof(broken);
            block.setType(Material.AIR, false);
            Item item = world.dropItem(block.getLocation().add(.5, .5, .5), dropped.clone()); item.setPickupDelay(100);
            items.add(item);
            return new Harvest(block, before, broken, item, new BlockDropItemEvent(block, before, player, List.of(item)));
        }
        @Override public void close() {
            service.close(); core.sessions().quit(player.getUniqueId());
            for (Item item : items) if (item.isValid()) item.remove();
            for (BlockState block : blocks) block.update(true, false);
        }
    }
    private record Harvest(Block block, BlockState before, BlockBreakEvent broken, Item item, BlockDropItemEvent drops) { }
}
