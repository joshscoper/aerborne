package net.nerdsmp.paper.loot;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;

/** Bounded local consolidation. Never deletes rewards or uses oversized/virtual item stacks. */
public final class DropStackingService implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final LinkedHashMap<UUID, Integer> pending = new LinkedHashMap<>();
    private final ArrayDeque<Chunk> chunks = new ArrayDeque<>();
    private final double radius;
    private final int budget;
    private BukkitTask task;
    public DropStackingService(JavaPlugin plugin) {
        this.plugin = plugin;
        radius = Math.clamp(plugin.getConfig().getDouble("drops.stacking.radius", 2.5), .5, 4);
        budget = Math.clamp(plugin.getConfig().getInt("drops.stacking.items-per-pass", 128), 8, 256);
    }
    public void start() {
        if (!plugin.getConfig().getBoolean("drops.stacking.enabled", true)) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (var world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) enqueue(chunk);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::drain, 2, 5);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void spawn(ItemSpawnEvent event) { enqueue(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void load(ChunkLoadEvent event) { enqueue(event.getChunk()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void death(EntityDeathEvent event) {
        // Preserve player death/grave handling and pack the final mob reward list.
        if (event.getEntity() instanceof org.bukkit.entity.Player) return;
        var compact = DropStacks.compact(event.getDrops());
        event.getDrops().clear(); event.getDrops().addAll(compact);
    }
    private void enqueue(Chunk chunk) { if (chunks.size() < 1024) chunks.add(chunk); }
    private void enqueue(Item item) {
        if (pending.size() < 8192) pending.putIfAbsent(item.getUniqueId(), Bukkit.getCurrentTick());
    }
    private void drain() {
        Chunk chunk = chunks.poll();
        if (chunk != null && chunk.isLoaded()) for (var entity : chunk.getEntities()) if (entity instanceof Item item) enqueue(item);
        int examined = 0;
        while (!pending.isEmpty() && examined++ < budget) {
            var entry = pending.entrySet().iterator().next();
            if (Bukkit.getCurrentTick() - entry.getValue() < 2) break; // Let native drop/auto-smelt receipts settle first.
            UUID id = entry.getKey(); pending.remove(id);
            if (Bukkit.getEntity(id) instanceof Item item && eligible(item)) consolidate(item);
        }
    }
    private boolean eligible(Item item) {
        return item.isValid() && item.getPickupDelay() < 32767 && !item.isUnlimitedLifetime() && item.willAge()
            && item.customName() == null && item.getPersistentDataContainer().getKeys().isEmpty()
            && item.getScoreboardTags().isEmpty() && item.getFireTicks() <= 0;
    }
    private boolean compatible(Item source, Item target) {
        return eligible(target) && Objects.equals(source.getOwner(), target.getOwner())
            && Objects.equals(source.getThrower(), target.getThrower()) && source.canMobPickup() == target.canMobPickup()
            && source.canPlayerPickup() == target.canPlayerPickup() && source.hasGravity() == target.hasGravity()
            && source.isInvulnerable() == target.isInvulnerable() && source.getHealth() == target.getHealth()
            && source.getItemStack().isSimilar(target.getItemStack());
    }
    private void consolidate(Item source) {
        int examined = 0;
        for (var entity : source.getNearbyEntities(radius, radius, radius)) {
            if (++examined > 64 || !source.isValid()) break;
            if (!(entity instanceof Item target) || source.getLocation().distanceSquared(target.getLocation()) > radius * radius
                || !compatible(source, target)) continue;
            var from = source.getItemStack(); var into = target.getItemStack();
            if (into.getAmount() >= into.getMaxStackSize()) continue;
            ItemMergeEvent merge = new ItemMergeEvent(source, target);
            Bukkit.getPluginManager().callEvent(merge);
            if (merge.isCancelled() || !eligible(source) || !compatible(source, target)) continue;
            from = source.getItemStack(); into = target.getItemStack();
            int moved = Math.min(from.getAmount(), into.getMaxStackSize() - into.getAmount());
            if (moved <= 0) continue;
            target.setPickupDelay(Math.max(source.getPickupDelay(), target.getPickupDelay()));
            int oldest = Math.max(source.getTicksLived(), target.getTicksLived());
            if (oldest > target.getTicksLived()) target.setTicksLived(oldest); // Paper rejects setting age zero on fresh entities.
            into.setAmount(into.getAmount() + moved); target.setItemStack(into);
            if (moved == from.getAmount()) { source.remove(); break; }
            from.setAmount(from.getAmount() - moved); source.setItemStack(from);
        }
    }
    @Override public void close() { if (task != null) task.cancel(); pending.clear(); chunks.clear(); }
}
