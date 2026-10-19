package net.nerdsmp.paper.gui;

import net.kyori.adventure.text.format.TextDecoration;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shared menu parts, so every screen puts navigation in the same place and words it the same
 * way. Names and lore are MiniMessage markup. See docs/STYLE_GUIDE.md.
 *
 * <p>Navigation sits on the bottom row: Previous in the first column, Back in the fourth,
 * Close in the middle and Next in the last. On a 54-slot screen that is 45, 48, 49 and 53.
 * Confirmation screens put Cancel at 20 and Confirm at 24.
 */
public final class MenuKit {
    public static final int CONFIRM_CANCEL_SLOT = 20;
    public static final int CONFIRM_SLOT = 24;
    /** Where {@link #openConfirm} shows what the player is agreeing to. */
    public static final int CONFIRM_SUMMARY_SLOT = 13;

    private static Plugin confirmListenerOwner;

    private MenuKit() {
    }

    public static int previousSlot(int size) {
        return bottomRow(size);
    }

    public static int backSlot(int size) {
        return bottomRow(size) + 3;
    }

    public static int closeSlot(int size) {
        return bottomRow(size) + 4;
    }

    public static int nextSlot(int size) {
        return bottomRow(size) + 8;
    }

    private static int bottomRow(int size) {
        if (size < 9 || size % 9 != 0) throw new IllegalArgumentException("Menu size must be a multiple of nine: " + size);
        return size - 9;
    }

    /** An item with a non-italic name and lore. */
    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(Chat.render(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> Chat.render(line).decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** {@code lore} followed by a blank line and {@code <yellow>Click to <action>.</yellow>}. */
    public static List<String> withHint(List<String> lore, String action) {
        List<String> lines = new ArrayList<>(lore);
        if (!lines.isEmpty()) lines.add("");
        lines.add(hint(action));
        return lines;
    }

    public static String hint(String action) {
        return "<yellow>Click to " + action + ".</yellow>";
    }

    public static ItemStack back(String parent) {
        return item(Material.ARROW, "<yellow>← Back to " + parent + "</yellow>", List.of());
    }

    public static ItemStack close() {
        return item(Material.BARRIER, "<red>Close</red>", List.of());
    }

    /** {@code page} counts from zero. */
    public static ItemStack previous(int page, int pages) {
        return item(Material.ARROW, "<yellow>Previous page</yellow>", List.of(pageLine(page, pages)));
    }

    /** {@code page} counts from zero. */
    public static ItemStack next(int page, int pages) {
        return item(Material.ARROW, "<yellow>Next page</yellow>", List.of(pageLine(page, pages)));
    }

    public static String pageLine(int page, int pages) {
        return "<gray>Page " + (page + 1) + "/" + Math.max(1, pages) + "</gray>";
    }

    public static ItemStack confirm(List<String> detail) {
        return item(Material.LIME_CONCRETE, "<green>Confirm</green>", detail);
    }

    public static ItemStack cancel() {
        return item(Material.ARROW, "<yellow>Cancel</yellow>", List.of("<gray>Keep everything as it is.</gray>"));
    }

    /** A feature the player can't open from here: grey name and the reason in lore. Clicking it does nothing. */
    public static ItemStack locked(Material material, String plainName, List<String> reason) {
        return item(material, "<gray>" + Chat.escape(plainName) + "</gray>", reason);
    }

    /**
     * The glass frame {@code MenuManager.decorate} draws: black border, sky-blue accents on the top
     * row, grey field. Fill it first, then place buttons over it.
     */
    public static void frame(Inventory inventory) {
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        ItemStack accent = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        ItemStack field = item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        int size = inventory.getSize();
        for (int slot = 0; slot < size; slot++) {
            int column = slot % 9;
            boolean edge = slot < 9 || slot >= size - 9 || column == 0 || column == 8;
            inventory.setItem(slot, slot < 9 && slot % 2 == 1 ? accent : edge ? border : field);
        }
    }

    /**
     * Opens the shared 27-slot confirm screen. {@code summary} is the item at the top (what is
     * being bought or moved), {@code detail} is the Confirm button's lore, for example
     * {@code <gray>Cost</gray> <gold>250 Marks</gold>}. Left-clicking Confirm closes the screen and
     * runs {@code onConfirm} next tick. Cancel closes it and runs {@code onCancel} if it isn't null,
     * which is how a caller reopens the screen the player came from; closing with Escape runs
     * nothing. Callers still re-check balances and items inside {@code onConfirm}: the screen can
     * sit open for a while.
     */
    public static void openConfirm(Plugin plugin, Player player, String title, ItemStack summary,
                                   List<String> detail, Consumer<Player> onConfirm, Consumer<Player> onCancel) {
        registerConfirmListener(plugin);
        ConfirmHolder holder = new ConfirmHolder(plugin, player.getUniqueId(), onConfirm, onCancel);
        Inventory inventory = Bukkit.createInventory(holder, 27, Chat.render(title));
        holder.inventory = inventory;
        frame(inventory);
        inventory.setItem(CONFIRM_SUMMARY_SLOT, summary);
        inventory.setItem(CONFIRM_CANCEL_SLOT, cancel());
        inventory.setItem(CONFIRM_SLOT, confirm(withHint(detail, "confirm")));
        player.openInventory(inventory);
    }

    private static synchronized void registerConfirmListener(Plugin plugin) {
        if (confirmListenerOwner == plugin) return;
        // A reload brings a new plugin instance; the old registration died with the old one.
        confirmListenerOwner = plugin;
        Bukkit.getPluginManager().registerEvents(new ConfirmListener(), plugin);
    }

    public static void clickSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4F, 1.0F);
    }

    private static final class ConfirmHolder implements InventoryHolder {
        private final Plugin plugin;
        private final UUID viewer;
        private final Consumer<Player> onConfirm;
        private final Consumer<Player> onCancel;
        private Inventory inventory;
        private boolean answered;

        private ConfirmHolder(Plugin plugin, UUID viewer, Consumer<Player> onConfirm, Consumer<Player> onCancel) {
            this.plugin = plugin;
            this.viewer = viewer;
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class ConfirmListener implements Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onClick(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder() instanceof ConfirmHolder holder)) return;
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.viewer)
                || event.getClick() != ClickType.LEFT || holder.answered) return;
            int slot = event.getRawSlot();
            if (slot != CONFIRM_SLOT && slot != CONFIRM_CANCEL_SLOT) return;
            Consumer<Player> action = slot == CONFIRM_SLOT ? holder.onConfirm : holder.onCancel;
            holder.answered = true;
            clickSound(player);
            Bukkit.getScheduler().runTask(holder.plugin, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
                player.closeInventory();
                if (action != null) action.accept(player);
            });
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onDrag(InventoryDragEvent event) {
            if (event.getView().getTopInventory().getHolder() instanceof ConfirmHolder) event.setCancelled(true);
        }
    }
}
