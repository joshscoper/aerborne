package net.nerdsmp.paper.gathering;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ChunkKey;
import net.nerdsmp.api.model.ClaimFlag;
import net.nerdsmp.api.model.ClaimRole;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.listener.SkillListener;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Verified native ore/refining/cooking output only; taking inserted items or banked vanilla XP earns nothing. */
public final class OreSmeltingService implements Listener, AutoCloseable {
    private static final int MAX_PENDING = 2048;
    private final NerdSmpPlugin plugin;
    private final SkillListener skills;
    private final NamespacedKey receiptKey;
    private final Map<Cell, PendingSmelt> pending = new HashMap<>();
    private final Map<Cell, UUID> moving = new HashMap<>();
    private final Set<Object> seen = Collections.newSetFromMap(new WeakHashMap<>());
    private boolean closed;

    public OreSmeltingService(NerdSmpPlugin plugin, SkillListener skills) {
        this.plugin = plugin;
        this.skills = skills;
        receiptKey = new NamespacedKey(plugin, "ore_smelting_v1");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        if (closed || !seen.add(event)) return;
        Furnace furnace = live(event.getBlock());
        if (furnace == null) return;
        Cell cell = Cell.of(event.getBlock());
        finish(cell);
        Work output = work(event.getSource(), event.getResult());
        ItemStack source = furnace.getInventory().getSmelting();
        ItemStack result = event.getResult();
        if (output == null || source == null || !event.getSource().isSimilar(source)
            || result.getAmount() != 1 || pending.size() >= MAX_PENDING) return;
        ItemStack existing = furnace.getInventory().getResult();
        int before = count(existing);
        if (before > 0 && !existing.isSimilar(result) || before >= 64) return;
        OreSmeltCredits ledger = read(furnace, output.id(), before).reconcile(output.id(), before);
        write(furnace, ledger);
        PendingSmelt work = new PendingSmelt(event, ledger.furnaceId(), output.id(), result.clone(),
            source.clone(), source.getAmount(), before);
        pending.put(cell, work);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (pending.get(cell) == work) finish(cell);
        });
    }

    private void finish(Cell cell) { finish(cell, null); }

    private void finish(Cell cell, FurnaceExtractEvent extraction) {
        PendingSmelt work = pending.remove(cell);
        if (work == null || work.event().isCancelled()) return;
        Furnace furnace = live(cell);
        if (furnace == null) return;
        ItemStack result = furnace.getInventory().getResult();
        ItemStack source = furnace.getInventory().getSmelting();
        ItemStack finalResult = work.event().getResult();
        OreSmeltCredits ledger = existing(furnace);
        if (ledger == null || !ledger.furnaceId().equals(work.furnaceId())) return;
        boolean consumed = work.beforeInput() == 1 ? count(source) == 0
            : source != null && source.isSimilar(work.input()) && source.getAmount() == work.beforeInput() - 1;
        int after = count(result);
        boolean matchingOutput = after == 0 || result.isSimilar(work.output());
        if (extraction != null && extraction.getItemStack().isSimilar(work.output())
            && extraction.getItemAmount() > 0 && extraction.getItemAmount() <= 64)
            after += extraction.getItemAmount();
        // Paper exposes the source as a live mirror of the native stack. Successful smelting
        // shrinks it after listeners return, so a one-item source may now report AIR/count 0.
        // Validate the captured input against the actual consumed slot, not that stale mirror.
        if (consumed && matchingOutput && after == work.beforeOutput() + 1
            && finalResult.isSimilar(work.output()) && finalResult.getAmount() == 1)
            write(furnace, ledger.smelt(work.outputId(), work.beforeOutput(), after));
        else write(furnace, ledger.unverified(type(result), count(result)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExtract(FurnaceExtractEvent event) {
        if (closed || !seen.add(event)) return;
        Cell cell = Cell.of(event.getBlock());
        // Collection may happen in the same tick as the smelt. Its native post-removal
        // count supplies the output delta, but never the furnace's accumulated vanilla XP.
        if (moving.containsKey(cell)) pending.remove(cell); else finish(cell, event);
        Furnace furnace = live(event.getBlock());
        if (furnace == null) return;
        OreSmeltCredits ledger = existing(furnace);
        if (ledger == null) return;
        ItemStack remaining = furnace.getInventory().getResult();
        ItemStack extracted = event.getItemStack();
        Work work = outputWork(extracted);
        if (moving.containsKey(cell) || work == null
            || count(remaining) > 0 && !remaining.isSimilar(extracted)) {
            write(furnace, ledger.unverified(type(remaining), count(remaining)));
            return;
        }
        OreSmeltCredits.Withdrawal withdrawal = ledger.extract(work.id(), event.getItemAmount(), count(remaining));
        write(furnace, withdrawal.next()); // Retire work before any asynchronous award or callback.
        Player player = event.getPlayer();
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (withdrawal.creditedItems() <= 0 || account == null || !authorized(player, account, furnace)) return;
        long perItem = work.experience();
        if (perItem <= 0) return;
        skills.awardActivity(player, work.skill(), perItem * withdrawal.creditedItems(),
            "ore-smelt:" + cell.world() + ":" + cell.x() + ":" + cell.y() + ":" + cell.z()
                + ":" + ledger.furnaceId() + ":" + withdrawal.operationSequence());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (closed || !seen.add(event) || !(event.getSource() instanceof FurnaceInventory inventory)
            || !(inventory.getHolder(false) instanceof Furnace owner)) return;
        Furnace furnace = live(owner.getBlock());
        if (furnace == null || existing(furnace) == null || outputWork(event.getItem()) == null) return;
        Cell cell = Cell.of(owner.getBlock());
        // Event entry can be before OR after the native source slot removal. Fence claims
        // until final counts are available; canceled/full-destination transfers lose no work.
        pending.remove(cell);
        if (moving.size() >= MAX_PENDING && !moving.containsKey(cell)) {
            OreSmeltCredits ledger = existing(furnace);
            write(furnace, ledger.unverified(type(furnace.getInventory().getResult()), count(furnace.getInventory().getResult())));
            return;
        }
        UUID token = UUID.randomUUID();
        moving.put(cell, token);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!moving.remove(cell, token)) return;
            Furnace current = live(cell);
            if (current == null) return;
            OreSmeltCredits ledger = existing(current);
            if (ledger == null) return;
            ItemStack output = current.getInventory().getResult();
            write(current, outputWork(output) != null ? ledger.reconcile(type(output), count(output))
                : ledger.unverified(type(output), count(output)));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Furnace furnace = live(event.getBlockPlaced());
        if (furnace == null) return;
        pending.remove(Cell.of(event.getBlockPlaced()));
        moving.remove(Cell.of(event.getBlockPlaced()));
        furnace.getPersistentDataContainer().remove(receiptKey);
    }

    private boolean authorized(Player player, AccountId account, Furnace furnace) {
        if (!player.isOnline() || !plugin.isEnabled() || Bukkit.getPlayer(player.getUniqueId()) != player
            || player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE
            || !plugin.sessions().account(player.getUniqueId()).filter(account::equals).isPresent()
            || !player.getWorld().equals(furnace.getWorld())
            || player.getLocation().distanceSquared(furnace.getLocation().add(.5, .5, .5)) > 64
            || !(player.getOpenInventory().getTopInventory().getHolder(false) instanceof Furnace opened)
            || !opened.getLocation().equals(furnace.getLocation())) return false;
        if (player.hasPermission("nerdsmp.claim.bypass")) return true;
        ChunkKey chunk = new ChunkKey(furnace.getWorld().getUID(), furnace.getX() >> 4, furnace.getZ() >> 4);
        if (plugin.claims().isReserved(chunk)) return false;
        return plugin.claims().roleAt(account, chunk).toCompletableFuture().getNow(ClaimRole.NONE).allows(ClaimRole.CONTAINER)
            || plugin.claims().flagAt(chunk, ClaimFlag.PUBLIC_CONTAINERS).toCompletableFuture().getNow(false);
    }

    private long experience(Material output) {
        String metal = switch (output) {
            case IRON_INGOT -> "iron";
            case COPPER_INGOT -> "copper";
            case GOLD_INGOT -> "gold";
            case NETHERITE_SCRAP -> "netherite";
            default -> "";
        };
        long fallback = output == Material.NETHERITE_SCRAP ? 12 : output == Material.GOLD_INGOT ? 6 : 4;
        return metal.isEmpty() ? 0 : Math.clamp(plugin.getConfig().getLong("progression.xp.smithing-smelt-" + metal, fallback), 0, 1000);
    }

    private Work work(ItemStack source, ItemStack result) {
        var custom = plugin.recipes().refiningWork(source, result);
        if (custom.isPresent()) return new Work("item:" + custom.get().result().value(), Skill.SMITHING, custom.get().experience());
        Material metal = outputFor(source.getType());
        if (metal != null && plain(source) && plain(result) && result.getType() == metal)
            return new Work(metal.name(), Skill.SMITHING, experience(metal));
        Material cooked = cookedOutputFor(source.getType());
        return cooked != null && net.nerdsmp.paper.item.CustomRecipeService.ordinaryIngredient(source)
            && net.nerdsmp.paper.item.CustomRecipeService.ordinaryIngredient(result) && result.getType() == cooked
            ? new Work(cooked.name(), Skill.COOKING, Math.clamp(plugin.getConfig().getLong("progression.xp.cooking-smelt", 6), 1, 1000)) : null;
    }

    private Work outputWork(ItemStack result) {
        if (count(result) == 0) return null;
        var custom = plugin.recipes().refiningOutput(result);
        if (custom.isPresent()) return new Work("item:" + custom.get().result().value(), Skill.SMITHING, custom.get().experience());
        if (plain(result) && OreSmeltCredits.metalOutput(result.getType().name()))
            return new Work(result.getType().name(), Skill.SMITHING, experience(result.getType()));
        return net.nerdsmp.paper.item.CustomRecipeService.ordinaryIngredient(result) && OreSmeltCredits.validOutput(result.getType().name())
            ? new Work(result.getType().name(), Skill.COOKING, Math.clamp(plugin.getConfig().getLong("progression.xp.cooking-smelt", 6), 1, 1000)) : null;
    }

    public static Material cookedOutputFor(Material source) {
        return switch (source) {
            case BEEF -> Material.COOKED_BEEF; case PORKCHOP -> Material.COOKED_PORKCHOP;
            case CHICKEN -> Material.COOKED_CHICKEN; case RABBIT -> Material.COOKED_RABBIT;
            case MUTTON -> Material.COOKED_MUTTON; case COD -> Material.COOKED_COD;
            case SALMON -> Material.COOKED_SALMON; case POTATO -> Material.BAKED_POTATO;
            case KELP -> Material.DRIED_KELP; default -> null;
        };
    }

    public static Material outputFor(Material source) {
        return switch (source) {
            case RAW_IRON, IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case RAW_COPPER, COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case RAW_GOLD, GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> Material.GOLD_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            default -> null;
        };
    }

    private OreSmeltCredits read(Furnace furnace, String output, int amount) {
        OreSmeltCredits ledger = existing(furnace);
        return ledger == null ? OreSmeltCredits.fresh(output, amount) : ledger;
    }

    private OreSmeltCredits existing(Furnace furnace) {
        try {
            String data = furnace.getPersistentDataContainer().get(receiptKey, PersistentDataType.STRING);
            return data == null ? null : OreSmeltCredits.decode(data);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private void write(Furnace furnace, OreSmeltCredits credits) {
        // getState(false) is Paper's live TileState; update() on a detached inventory snapshot
        // could overwrite a smelt or extraction. This writes only our live PDC namespace.
        if (furnace.isSnapshot()) throw new IllegalStateException("Ore receipt requires a live furnace tile");
        furnace.getPersistentDataContainer().set(receiptKey, PersistentDataType.STRING, credits.encode());
    }

    private Furnace live(Block block) {
        if (closed || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
            || block.getType() != Material.FURNACE && block.getType() != Material.BLAST_FURNACE && block.getType() != Material.SMOKER) return null;
        return block.getState(false) instanceof Furnace furnace && !furnace.isSnapshot() ? furnace : null;
    }

    private Furnace live(Cell cell) {
        World world = Bukkit.getWorld(cell.world());
        return world != null && world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4)
            ? live(world.getBlockAt(cell.x(), cell.y(), cell.z())) : null;
    }

    private static boolean plain(ItemStack item) { return item != null && !item.getType().isAir() && net.nerdsmp.paper.item.FoodItems.ordinary(item); }
    private static int count(ItemStack item) { return item == null || item.getType().isAir() ? 0 : item.getAmount(); }
    private String type(ItemStack item) { Work work = outputWork(item); return work == null ? "" : work.id(); }

    @Override public void close() { closed = true; pending.clear(); moving.clear(); seen.clear(); }
    private record Cell(UUID world, int x, int y, int z) {
        static Cell of(Block block) { return new Cell(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
    }
    private record PendingSmelt(FurnaceSmeltEvent event, UUID furnaceId, String outputId, ItemStack output,
                                ItemStack input, int beforeInput, int beforeOutput) { }
    private record Work(String id, Skill skill, long experience) { }
}
