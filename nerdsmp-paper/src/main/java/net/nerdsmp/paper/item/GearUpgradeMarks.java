package net.nerdsmp.paper.item;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.item.PaperItemService.GearDescriptor;
import net.nerdsmp.paper.item.PaperItemService.GearSlot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Marks the gear in a player's bags that beats what they're wearing: a green arrow on its name, a glint
 * so it shows in the grid, and what it would gain in the tooltip. Swords and shields on the hotbar are
 * already to hand, so they aren't marked. An upgrade depends on who's looking, so marks are redone as a
 * player's gear changes and come off whenever an item leaves their hands.
 */
public final class GearUpgradeMarks implements Listener {
    private static final int HOTBAR = 9, BAGS = 36, WORN_END = 41;

    private final NerdSmpPlugin plugin;
    private final PaperItemService items;
    private final Set<UUID> queued = new HashSet<>();
    private BiFunction<Player, GearSlot, List<ItemStack>> worn = GearUpgradeMarks::vanillaSlots;

    public GearUpgradeMarks(NerdSmpPlugin plugin, PaperItemService items) {
        this.plugin = plugin;
        this.items = items;
    }

    /** Where a player's gear is worn, one entry per place that slot type can go. */
    public void wornWith(BiFunction<Player, GearSlot, List<ItemStack>> places) { worn = places; }

    /** A player's equipment isn't there to read until their character has loaded. */
    private boolean loaded(Player player) { return plugin.sessions().account(player.getUniqueId()).isPresent(); }

    /** Everything a player has on, and whatever's in their hand. */
    public List<ItemStack> worn(Player player) {
        if (!loaded(player)) return List.of();
        List<ItemStack> all = new ArrayList<>();
        all.add(player.getInventory().getItemInMainHand());
        for (GearSlot slot : GearSlot.values())
            if (slot != GearSlot.MAIN_HAND && slot != GearSlot.NONE)
                for (ItemStack item : worn.apply(player, slot)) if (item != null) all.add(item);
        return all;
    }

    private static List<ItemStack> vanillaSlots(Player player, GearSlot slot) {
        var inventory = player.getInventory();
        List<ItemStack> places = new ArrayList<>();
        switch (slot) {
            case HEAD -> places.add(inventory.getHelmet());
            case CHEST -> places.add(inventory.getChestplate());
            case LEGS -> places.add(inventory.getLeggings());
            case FEET -> places.add(inventory.getBoots());
            case OFF_HAND -> places.add(inventory.getItemInOffHand());
            default -> { }
        }
        return places;
    }

    private void queue(Player player) {
        if (!queued.add(player.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            queued.remove(player.getUniqueId());
            if (player.isOnline()) refresh(player);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSlot(PlayerInventorySlotChangeEvent event) { queue(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) { queue(event.getPlayer()); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { queue(event.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { queued.remove(event.getPlayer().getUniqueId()); }

    /** A mark means nothing to the next person, so it doesn't go with the item into a chest or onto the ground. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (holder == null || holder instanceof Player) return;
        for (int slot = 0; slot < top.getSize(); slot++) {
            ItemStack item = top.getItem(slot);
            if (item != null && items.upgradeNote(item) != null) top.setItem(slot, items.withoutUpgradeNote(item));
        }
        queue((Player) event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (items.upgradeNote(item) != null) event.getItemDrop().setItemStack(items.withoutUpgradeNote(item));
    }

    private void refresh(Player player) {
        if (!loaded(player)) {
            queue(player);
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < WORN_END; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            set(inventory, slot, item, slot < BAGS ? note(player, item, slot) : null);
        }
    }

    /** Marks the items in a window only this player sees, such as a loot chest. */
    public void mark(Player player, Inventory window) {
        for (int slot = 0; slot < window.getSize(); slot++) {
            ItemStack item = window.getItem(slot);
            if (item != null && !item.getType().isAir()) set(window, slot, item, note(player, item, -1));
        }
    }

    private void set(Inventory inventory, int slot, ItemStack item, String note) {
        if (Objects.equals(note, items.upgradeNote(item))) return;
        ItemStack marked = item.clone();
        items.markUpgrade(marked, note);
        inventory.setItem(slot, marked);
    }

    /** What this item would gain over what it replaces, or null when it isn't an upgrade. */
    private String note(Player player, ItemStack item, int slot) {
        GearDescriptor gear = items.gear(item).orElse(null);
        if (gear == null || item.getAmount() != 1 || inHandReach(gear, slot)) return null;
        Map<String, Double> current = current(player, gear);
        if (current == null || !GearComparison.upgrade(gear.stats(), current)) return null;
        List<String> gains = new ArrayList<>();
        for (String stat : GearComparison.gains(gear.stats(), current, 3))
            gains.add(PaperItemService.statChange(stat, GearComparison.gain(gear.stats(), current, stat)));
        return String.join(", ", gains);
    }

    private static boolean inHandReach(GearDescriptor gear, int slot) {
        return slot >= 0 && slot < HOTBAR && (gear.slot() == GearSlot.MAIN_HAND || gear.slot() == GearSlot.OFF_HAND);
    }

    /** The stats of what this item would replace: the weakest ring or trinket, or the best weapon to hand. Null when it can't be compared. */
    private Map<String, Double> current(Player player, GearDescriptor gear) {
        if (gear.slot() == GearSlot.MAIN_HAND) return weapon(player, gear);
        Map<String, Double> weakest = null;
        for (ItemStack place : worn.apply(player, gear.slot())) {
            Map<String, Double> stats = stats(place);
            if (weakest == null || GearComparison.score(stats) < GearComparison.score(weakest)) weakest = stats;
        }
        return weakest;
    }

    /** Tools don't compete with swords: only weapons are marked, against the one in hand or the best on the hotbar. */
    private Map<String, Double> weapon(Player player, GearDescriptor gear) {
        if (gear.stat("damage") <= 0 && gear.stat("projectile-damage") <= 0) return null;
        Map<String, Double> inHand = weaponStats(player.getInventory().getItemInMainHand());
        if (inHand != null) return inHand;
        Map<String, Double> best = Map.of();
        for (int hotbar = 0; hotbar < HOTBAR; hotbar++) {
            Map<String, Double> other = weaponStats(player.getInventory().getItem(hotbar));
            if (other != null && GearComparison.score(other) > GearComparison.score(best)) best = other;
        }
        return best;
    }

    private Map<String, Double> weaponStats(ItemStack item) {
        Optional<GearDescriptor> gear = item == null ? Optional.empty() : items.gear(item);
        return gear.filter(found -> found.stat("damage") > 0 || found.stat("projectile-damage") > 0).map(GearDescriptor::stats).orElse(null);
    }

    private Map<String, Double> stats(ItemStack item) {
        return item == null || item.getType().isAir() ? new HashMap<>() : items.gear(item).map(GearDescriptor::stats).orElse(Map.of());
    }
}
