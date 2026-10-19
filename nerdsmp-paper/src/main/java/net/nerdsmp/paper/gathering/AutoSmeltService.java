package net.nerdsmp.paper.gathering;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.item.SignatureAffix;
import net.nerdsmp.paper.listener.SkillListener;
import net.nerdsmp.paper.resource.HarvestClaims;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Furnace Heart transforms native drops only. It never invents drops, re-breaks blocks or pays another job. */
public final class AutoSmeltService implements Listener, AutoCloseable {
    private static final int MAX_PENDING = 512;
    private final NerdSmpPlugin plugin;
    private final SkillListener skills;
    private final Predicate<Player> currentPlayer;
    private final Map<Cell, Pending> pending = new HashMap<>();
    private final Map<UUID, Batch> batches = new HashMap<>();
    private final Set<UUID> lockedItems = new HashSet<>();
    private final Set<Object> captured = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<Object> seenDrops = Collections.newSetFromMap(new WeakHashMap<>());
    private BukkitTask task;
    private boolean closed;

    public AutoSmeltService(NerdSmpPlugin plugin, SkillListener skills) {
        this(plugin, skills, player -> Bukkit.getPlayer(player.getUniqueId()) == player);
    }

    /** The disposable Paper fixture supplies a controlled actor; production always checks Bukkit's live player instance. */
    AutoSmeltService(NerdSmpPlugin plugin, SkillListener skills, Predicate<Player> currentPlayer) {
        this.plugin = plugin;
        this.skills = skills;
        this.currentPlayer = currentPlayer;
    }

    public void start() {
        if (task == null && !closed) task = Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 10, 10);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreakCapture(BlockBreakEvent event) {
        if (closed || !captured.add(event)) return;
        Block block = event.getBlock();
        Cell cell = Cell.of(block);
        // A new break at this coordinate can never inherit an earlier tool or reward receipt.
        Pending previous = pending.remove(cell);
        if (previous != null) { restoreCanceled(previous); previous.receipt.refuse(); }
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (event.isCancelled() || !validPlayer(player) || !event.isDropItems() || !AutoSmeltRules.ore(block.getType())
            || !signatureTool(tool) || !block.isPreferredTool(tool) || pending.size() >= MAX_PENDING) return;
        SkillListener.ProcessingContext context = skills.processingContext(player);
        if (context == null) return;
        var identity = identity(player, context, cell, block.getBlockData().getAsString());
        pending.put(cell, new Pending(event, block.getType(), normalize(tool), context, new AutoSmeltReceipt(identity), identity));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreakProof(BlockBreakEvent event) {
        Pending work = pending.get(Cell.of(event.getBlock()));
        if (work == null || work.event != event) return;
        if (closed || event.isCancelled() || !event.isDropItems() || !sameActor(work)
            || !work.tool.equals(normalize(event.getPlayer().getInventory().getItemInMainHand()))
            || !work.identity.blockData().equals(event.getBlock().getBlockData().getAsString())) {
            refuse(work);
            return;
        }
        var harvest = plugin.resources().claim(event, HarvestClaims.Channel.SIGNATURES);
        if (harvest.isEmpty() || !work.receipt.prove(harvest.get().operationId())) refuse(work);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrops(BlockDropItemEvent event) {
        if (closed || !seenDrops.add(event)) return;
        Cell cell = Cell.of(event.getBlock());
        Pending work = pending.remove(cell);
        if (work == null) return;
        if (event.isCancelled() || work.event.isCancelled() || !work.event.isDropItems() || event.getPlayer() != work.event.getPlayer()
            || !sameActor(work) || event.getBlockState().getType() != work.ore
            || !work.identity.blockData().equals(event.getBlockState().getBlockData().getAsString())
            || event.getItems().size() > AutoSmeltRules.MAX_DROP_ENTITIES
            || !work.receipt.convert(identity(event.getPlayer(), work.context, cell,
                event.getBlockState().getBlockData().getAsString()))) {
            work.receipt.refuse();
            return;
        }
        List<Conversion> conversions = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        int units = 0;
        for (Item item : event.getItems()) {
            ItemStack original = item.getItemStack();
            Material output = AutoSmeltRules.output(work.ore, original.getType(), false);
            if (output == null || !plain(original)) continue;
            int amount = AutoSmeltRules.units(original.getAmount());
            if (amount == 0 || !ids.add(item.getUniqueId()) || lockedItems.contains(item.getUniqueId())
                || !item.getWorld().getUID().equals(cell.world) || (units += amount) > AutoSmeltRules.MAX_UNITS) {
                work.receipt.refuse();
                return;
            }
            conversions.add(new Conversion(item, original.clone(), new ItemStack(output, amount)));
        }
        if (conversions.isEmpty() || batches.size() >= MAX_PENDING) {
            work.receipt.refuse();
            return;
        }
        UUID token = UUID.randomUUID();
        Batch batch = new Batch(work, event, List.copyOf(conversions));
        batches.put(token, batch);
        try {
            for (Conversion conversion : conversions) {
                lockedItems.add(conversion.entity.getUniqueId());
                conversion.entity.setItemStack(conversion.result.clone());
            }
            Bukkit.getScheduler().runTask(plugin, () -> finish(token));
        } catch (RuntimeException failure) {
            rollback(batch);
            batches.remove(token);
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Auto-smelting stopped safely for " + work.receipt.operation(), failure);
        }
    }

    private void finish(UUID token) {
        Batch batch = batches.remove(token);
        if (batch == null) return;
        Pending work = batch.work;
        boolean finalBreak = !closed && !work.event.isCancelled() && work.event.isDropItems() && sameActor(work);
        boolean finalDrop = !batch.event.isCancelled();
        int units = 0;
        long experience = 0;
        for (Conversion conversion : batch.conversions) {
            if (conversion.entity.isValid() && batch.event.getItems().contains(conversion.entity)
                && conversion.entity.getWorld().getUID().equals(work.identity.world())
                && conversion.result.equals(conversion.entity.getItemStack())) {
                units += conversion.result.getAmount();
                experience += perItemExperience(conversion.result.getType()) * conversion.result.getAmount();
            } else finalDrop = false;
        }
        if (!work.receipt.settle(finalBreak, finalDrop, units)) {
            rollback(batch);
            return;
        }
        unlock(batch);
        if (experience > 0) skills.awardProcessing(work.event.getPlayer(), work.context, experience, work.receipt.operation());
    }

    private long perItemExperience(Material output) {
        String metal = AutoSmeltRules.metal(output);
        return metal.isEmpty() ? 0 : Math.clamp(plugin.getConfig().getLong("progression.xp.smithing-smelt-" + metal,
            AutoSmeltRules.defaultExperience(output)), 0, 1000);
    }

    // One tick's verification fence also prevents hopper collection, mob pickup and merging from erasing the proof.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (lockedItems.contains(event.getItem().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent event) {
        if (lockedItems.contains(event.getItem().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        if (lockedItems.contains(event.getEntity().getUniqueId()) || lockedItems.contains(event.getTarget().getUniqueId()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        pending.values().removeIf(work -> {
            if (!work.identity.player().equals(player)) return false;
            restoreCanceled(work);
            work.receipt.refuse();
            return true;
        });
        for (var entry : List.copyOf(batches.entrySet())) if (entry.getValue().work.identity.player().equals(player)) {
            batches.remove(entry.getKey());
            rollback(entry.getValue());
        }
    }

    private boolean signatureTool(ItemStack tool) {
        return tool != null && tool.getAmount() == 1 && Tag.ITEMS_PICKAXES.isTagged(tool.getType())
            && !tool.containsEnchantment(Enchantment.SILK_TOUCH)
            && plugin.items().signatureAffix(tool).filter(affix -> affix == SignatureAffix.FURNACE_HEART).isPresent();
    }
    private boolean validPlayer(Player player) {
        return player.isOnline() && currentPlayer.test(player) && !player.isDead()
            && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            && plugin.sessions().account(player.getUniqueId()).isPresent();
    }
    private boolean sameActor(Pending work) {
        Player player = work.event.getPlayer();
        return validPlayer(player) && player.getWorld().getUID().equals(work.identity.world())
            && plugin.sessions().account(player.getUniqueId()).filter(work.context.account()::equals).isPresent();
    }
    private AutoSmeltReceipt.Identity identity(Player player, SkillListener.ProcessingContext context, Cell cell, String data) {
        return new AutoSmeltReceipt.Identity(player.getUniqueId(), context.account().toString(), cell.world,
            cell.x, cell.y, cell.z, data, Bukkit.getCurrentTick());
    }
    private static ItemStack normalize(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        ItemStack copy = stack.clone();
        var meta = copy.getItemMeta();
        if (meta instanceof Damageable damage) {
            damage.setDamage(0);
            copy.setItemMeta(meta);
        }
        return copy;
    }
    private static boolean plain(ItemStack item) {
        return item != null && !item.getType().isAir() && !item.hasItemMeta();
    }
    private void refuse(Pending work) {
        pending.remove(Cell.of(work.event.getBlock()), work);
        restoreCanceled(work);
        work.receipt.refuse();
    }
    private void restoreCanceled(Pending work) {
        // A vetoed break must not permanently consume the natural ore's proven harvest cycle.
        // Never renew a placed ore or a successful break whose coordinate has since been reused.
        if (work.receipt.state() != AutoSmeltReceipt.State.PROVEN || !work.event.isCancelled()) return;
        Block original = work.event.getBlock();
        if (original.getWorld().isChunkLoaded(original.getX() >> 4, original.getZ() >> 4)
            && original.getBlockData().getAsString().equals(work.identity.blockData()))
            plugin.resources().renewManaged(original, work.ore);
    }
    private void rollback(Batch batch) {
        batch.work.receipt.refuse();
        try {
            for (Conversion conversion : batch.conversions) {
                try {
                    if (conversion.entity.isValid() && conversion.result.equals(conversion.entity.getItemStack()))
                        conversion.entity.setItemStack(conversion.original.clone());
                } catch (RuntimeException failure) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "An auto-smelt entity could not be restored safely", failure);
                }
            }
        } finally { unlock(batch); }
    }
    private void unlock(Batch batch) {
        for (Conversion conversion : batch.conversions) lockedItems.remove(conversion.entity.getUniqueId());
    }
    private void expire() {
        int tick = Bukkit.getCurrentTick();
        pending.values().removeIf(work -> {
            if (tick == work.identity.tick()) return false;
            restoreCanceled(work);
            work.receipt.refuse();
            return true;
        });
    }
    @Override public void close() {
        closed = true;
        if (task != null) task.cancel();
        for (Batch batch : List.copyOf(batches.values())) rollback(batch);
        for (Pending work : pending.values()) { restoreCanceled(work); work.receipt.refuse(); }
        batches.clear(); pending.clear(); lockedItems.clear(); captured.clear(); seenDrops.clear();
    }
    private record Cell(UUID world, int x, int y, int z) {
        static Cell of(Block block) { return new Cell(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
    }
    private record Pending(BlockBreakEvent event, Material ore, ItemStack tool, SkillListener.ProcessingContext context,
                           AutoSmeltReceipt receipt, AutoSmeltReceipt.Identity identity) { }
    private record Conversion(Item entity, ItemStack original, ItemStack result) { }
    private record Batch(Pending work, BlockDropItemEvent event, List<Conversion> conversions) { }
}
