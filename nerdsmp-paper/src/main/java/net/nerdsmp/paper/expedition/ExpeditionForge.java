package net.nerdsmp.paper.expedition;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionLoot;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Expedition Forge: trade an expedition's materials and some Marks for a piece of its signature
 * set, so steady play earns the gear even when the drops don't. Pieces come out sealed (unidentified,
 * with two affixes). An expedition's set opens once you've cleared it.
 */
public final class ExpeditionForge implements Listener {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private final NerdSmpPlugin plugin;
    private final ExpeditionService service;

    public ExpeditionForge(NerdSmpPlugin plugin, ExpeditionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /** The forge's front page: every expedition with a set, locked until cleared. */
    public void open(Player player) {
        Page page = new Page(player.getUniqueId());
        page.inventory = Bukkit.createInventory(page, 36, Chat.render("<dark_red><bold>Expedition Forge</bold></dark_red>"));
        MenuKit.frame(page.inventory);
        int slot = 0;
        for (ExpeditionDefinition definition : service.definitions()) {
            ExpeditionLoot.Table table = service.lootTable(definition.id());
            if (table.gear().isEmpty() || !service.playable(definition) || slot >= SLOTS.length) continue;
            boolean open = service.clears(player, definition) > 0 || player.hasPermission(ExpeditionService.ADMIN);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Forge pieces of this expedition's set</gray>");
            lore.add("<gray>from its materials.</gray>");
            if (!open) {
                lore.add("");
                lore.add("<red>Clear " + Chat.escape(definition.title()) + " to unlock.</red>");
            }
            ItemStack icon = Optional.ofNullable(Material.matchMaterial(definition.icon())).filter(Material::isItem)
                .map(material -> MenuKit.item(material, "<gold>" + Chat.escape(definition.title()) + "</gold>", open ? MenuKit.withHint(lore, "see its set") : lore))
                .orElseGet(() -> MenuKit.item(Material.ANVIL, "<gold>" + Chat.escape(definition.title()) + "</gold>", lore));
            page.set(SLOTS[slot++], open ? icon : MenuKit.item(Material.GRAY_DYE, "<gray>" + Chat.escape(definition.title()) + "</gray>", lore),
                who -> { if (open) openSet(who, definition); });
        }
        page.set(4, MenuKit.item(Material.SMITHING_TABLE, "<gold>The Expedition Forge</gold>", List.of(
            "<gray>Bring materials home from expeditions and</gray>",
            "<gray>forge the expedition's set. Pieces come out</gray>",
            "<gray>sealed: identify them to reveal their affixes.</gray>")), who -> { });
        page.set(MenuKit.closeSlot(36), MenuKit.close(), Player::closeInventory);
        player.openInventory(page.inventory);
    }

    private void openSet(Player player, ExpeditionDefinition definition) {
        ExpeditionLoot.Table table = service.lootTable(definition.id());
        Page page = new Page(player.getUniqueId());
        page.inventory = Bukkit.createInventory(page, 36, Chat.render("<dark_red><bold>Forge: " + Chat.escape(definition.title()) + "</bold></dark_red>"));
        MenuKit.frame(page.inventory);
        for (int index = 0; index < table.gear().size() && index < 7; index++) {
            String piece = table.gear().get(index);
            ExpeditionLoot.Cost cost = ExpeditionLoot.forgeCost(table, piece, definition.level());
            ItemStack shown;
            try {
                shown = plugin.items().create(new ItemId(piece));
            } catch (IllegalArgumentException unknown) {
                continue;
            }
            var meta = shown.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(line("<gold>Forge cost:</gold>"));
            cost.items().forEach((item, amount) -> {
                int have = count(player, item);
                lore.add(line((have >= amount ? "<green>✔ " : "<red>✘ ") + amount + " × " + name(item) + " <dark_gray>(" + have + ")</dark_gray>"));
            });
            lore.add(line("<gray>" + cost.marks() + " Marks</gray>"));
            lore.add(line("<yellow>Click to forge</yellow>"));
            meta.lore(lore);
            shown.setItemMeta(meta);
            page.set(SLOTS[index], shown, who -> forge(who, definition, piece, cost));
        }
        page.set(MenuKit.backSlot(36), MenuKit.back("Expedition Forge"), this::open);
        page.set(MenuKit.closeSlot(36), MenuKit.close(), Player::closeInventory);
        player.openInventory(page.inventory);
    }

    private void forge(Player player, ExpeditionDefinition definition, String piece, ExpeditionLoot.Cost cost) {
        for (var entry : cost.items().entrySet())
            if (count(player, entry.getKey()) < entry.getValue()) {
                Chat.error(player, "You need " + entry.getValue() + " " + plain(entry.getKey()) + " to forge this.");
                return;
            }
        Optional<AccountId> account = plugin.sessions().account(player.getUniqueId());
        if (account.isEmpty()) { Chat.accountLoading(player); return; }
        plugin.economy().debit(account.get(), cost.marks(), new EconomyService.TransactionReason("expedition.forge", piece),
            "expedition-forge:" + UUID.randomUUID()).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.status() != EconomyService.TransactionResult.Status.APPLIED) {
                    Chat.error(player, result.status() == EconomyService.TransactionResult.Status.INSUFFICIENT_FUNDS
                        ? "You need " + cost.marks() + " Marks to forge this." : "The forge couldn't take your Marks. Try again.");
                    return;
                }
                // The materials may have moved while the payment went through: check again, refund if so.
                for (var entry : cost.items().entrySet())
                    if (count(player, entry.getKey()) < entry.getValue()) {
                        plugin.economy().credit(account.get(), cost.marks(), new EconomyService.TransactionReason("expedition.forge-refund", piece),
                            "expedition-forge-refund:" + UUID.randomUUID());
                        Chat.error(player, "Your materials changed while forging. Your Marks were returned.");
                        return;
                    }
                cost.items().forEach((item, amount) -> take(player, item, amount));
                ItemStack made = plugin.items().createLoot(new ItemId(piece), 2, true, java.util.concurrent.ThreadLocalRandom.current().nextLong());
                player.getInventory().addItem(made).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1F, 1F);
                Chat.success(player, "The forge hands you a sealed " + name(piece) + ". Identify it to reveal its affixes.");
                openSet(player, definition);
            }));
    }

    private int count(Player player, String item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents())
            if (is(stack, item)) total += stack.getAmount();
        return total;
    }

    private void take(Player player, String item, int amount) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize() && amount > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!is(stack, item)) continue;
            int used = Math.min(amount, stack.getAmount());
            stack.setAmount(stack.getAmount() - used);
            inventory.setItem(slot, stack.getAmount() <= 0 ? null : stack);
            amount -= used;
        }
    }

    private boolean is(ItemStack stack, String item) {
        if (stack == null || stack.getType().isAir()) return false;
        return plugin.items().describe(stack).map(descriptor -> descriptor.itemId().value().equals(item)).orElse(false);
    }

    private String name(String item) {
        try {
            var meta = plugin.items().create(new ItemId(item)).getItemMeta();
            if (meta.hasItemName()) return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(meta.itemName());
        } catch (IllegalArgumentException unknown) { }
        return Chat.escape(item);
    }

    private String plain(String item) {
        try {
            var meta = plugin.items().create(new ItemId(item)).getItemMeta();
            if (meta.hasItemName()) return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.itemName());
        } catch (IllegalArgumentException unknown) { }
        return item;
    }

    private static net.kyori.adventure.text.Component line(String markup) {
        return Chat.render(markup).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Page page)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !page.owner.equals(player.getUniqueId())) return;
        Consumer<Player> action = page.actions.get(event.getRawSlot());
        if (action == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != page.inventory) return;
            MenuKit.clickSound(player);
            action.accept(player);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Page) event.setCancelled(true);
    }

    private static final class Page implements InventoryHolder {
        private final UUID owner;
        private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        private Inventory inventory;

        private Page(UUID owner) { this.owner = owner; }

        void set(int slot, ItemStack item, Consumer<Player> action) {
            inventory.setItem(slot, item);
            actions.put(slot, action);
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
