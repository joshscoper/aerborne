package net.nerdsmp.paper.player;

import com.google.common.collect.HashMultimap;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.session.SessionRegistry;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;

public final class BankService implements Listener, AutoCloseable {
    private static final int UNLOCK_SLOT = 50;
    public static final int PAGE_SIZE = 45;
    private final NerdSmpPlugin plugin;
    private final SessionRegistry sessions;
    private final EconomyService economy;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final File file;
    private final Map<AccountId, Vault> vaults = new HashMap<>();
    private final Map<AccountId, BankHolder> openVaults = new HashMap<>();
    private final Set<AccountId> pending = ConcurrentHashMap.newKeySet();
    private final int defaultSlots;
    private final int maximumSlots;
    private final long baseCost;
    private final long costIncrement;

    public BankService(NerdSmpPlugin plugin, SessionRegistry sessions, EconomyService economy) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.economy = economy;
        defaultSlots = Math.max(9, Math.min(54, plugin.getConfig().getInt("bank.default-slots", 9)));
        maximumSlots = Math.max(defaultSlots, Math.min(54, plugin.getConfig().getInt("bank.maximum-slots", 54)));
        baseCost = Math.max(1L, plugin.getConfig().getLong("bank.slot-base-cost-marks", 250L));
        costIncrement = Math.max(0L, plugin.getConfig().getLong("bank.slot-cost-increment-marks", 125L));
        file = new File(plugin.getDataFolder(), "bank.yml");
        load();
    }

    public void open(Player player) {
        open(player, 0);
    }

    public int unlocked(AccountId account) {
        return vault(account).unlocked;
    }

    public long nextSlotCost(AccountId account) {
        return slotCost(unlocked(account), defaultSlots, baseCost, costIncrement);
    }

    public static long slotCost(int unlockedSlots, int defaultSlots, long baseCost, long increment) {
        long upgradeIndex = Math.max(0L, (long) unlockedSlots - Math.max(0, defaultSlots));
        try {
            return Math.addExact(Math.max(1L, baseCost), Math.multiplyExact(Math.max(0L, increment), upgradeIndex));
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BankHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || !isCurrentView(holder, player)) {
            event.setCancelled(true);
            return;
        }
        // These actions search the whole inventory and can collect the decorative controls.
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR
            || event.getAction() == InventoryAction.CLONE_STACK
            || event.getAction() == InventoryAction.UNKNOWN) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        if (raw >= 0 && raw < event.getView().getTopInventory().getSize()) {
            if (holder.layout.writableIndex(raw) >= 0) {
                return;
            }
            event.setCancelled(true);
            if (raw == MenuKit.previousSlot(54) || raw == MenuKit.backSlot(54) || raw == MenuKit.closeSlot(54)
                || raw == UNLOCK_SLOT || raw == MenuKit.nextSlot(54)) {
                if (raw != MenuKit.closeSlot(54)) MenuKit.clickSound(player);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || !isCurrentView(holder, player)
                        || player.getOpenInventory().getTopInventory().getHolder() != holder) {
                        return;
                    }
                    if (raw == MenuKit.previousSlot(54) && holder.page > 0) {
                        open(player, holder.page - 1);
                    } else if (raw == MenuKit.backSlot(54)) {
                        plugin.menus().open(player, MenuManager.View.WORLD);
                    } else if (raw == MenuKit.closeSlot(54)) {
                        player.closeInventory();
                    } else if (raw == UNLOCK_SLOT) {
                        syncPage(holder);
                        confirmUnlock(player, holder);
                    } else if (raw == MenuKit.nextSlot(54) && holder.page < 1) {
                        open(player, holder.page + 1);
                    }
                });
            }
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // Only actual storage slots may receive a shift-clicked stack.
            event.setCancelled(true);
            if (player.getInventory().equals(event.getClickedInventory())) {
                moveIntoVault(player, holder, event.getSlot());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BankHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || !isCurrentView(holder, player)) {
            event.setCancelled(true);
            return;
        }
        boolean invalid = event.getRawSlots().stream().filter(slot -> slot < 54)
            .anyMatch(slot -> holder.layout.writableIndex(slot) < 0);
        if (invalid) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BankHolder holder) {
            syncPage(holder);
            openVaults.remove(holder.account, holder);
            save();
        }
    }

    @Override
    public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BankHolder holder) {
                syncPage(holder);
                player.closeInventory();
                openVaults.remove(holder.account, holder);
            }
        }
        save();
    }

    private void open(Player player, int requestedPage) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) {
            Chat.accountLoading(player);
            return;
        }
        int page = Math.max(0, Math.min(1, requestedPage));
        BankHolder active = openVaults.get(account);
        if (active != null && !active.playerId.equals(player.getUniqueId())) {
            Chat.notice(player, "Your bank is already open on another linked character.");
            return;
        }
        // Closing commits the old page before the replacement takes its snapshot.
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BankHolder previous) {
            syncPage(previous);
            player.closeInventory();
            openVaults.remove(previous.account, previous);
        }
        Vault vault = vault(account);
        BankHolder holder = new BankHolder(player.getUniqueId(), account, page, vault.unlocked);
        Inventory inventory = Bukkit.createInventory(holder, 54,
            miniMessage.deserialize("<gold><bold>Frontier Bank</bold></gold> <gray>" + (page + 1) + "/2</gray>"));
        holder.inventory = inventory;
        ItemStack locked = icon(Material.RED_STAINED_GLASS_PANE, "<red>Locked Bank Slot</red>", List.of(
            "<gray>Unlock slots one at a time with Marks.</gray>"
        ));
        ItemStack unavailable = icon(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Not used on this page</dark_gray>", List.of());
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = holder.layout.index(slot);
            if (index < 0 || index >= Math.max(maximumSlots, vault.unlocked)) {
                inventory.setItem(slot, unavailable);
            } else if (index < vault.unlocked) {
                inventory.setItem(slot, clone(vault.items[index]));
            } else {
                inventory.setItem(slot, locked);
            }
        }
        ItemStack border = icon(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, border);
        }
        if (page > 0) {
            inventory.setItem(MenuKit.previousSlot(54), MenuKit.previous(page, 2));
        }
        if (page < 1) {
            inventory.setItem(MenuKit.nextSlot(54), MenuKit.next(page, 2));
        }
        inventory.setItem(MenuKit.backSlot(54), MenuKit.back("World & Trade"));
        inventory.setItem(MenuKit.closeSlot(54), MenuKit.close());
        if (vault.unlocked < maximumSlots) {
            long cost = nextSlotCost(account);
            inventory.setItem(UNLOCK_SLOT, icon(Material.GOLD_INGOT, "<gold><bold>Unlock Slot "
                + (vault.unlocked + 1) + "</bold></gold>", MenuKit.withHint(List.of(
                "<gray>Cost</gray> " + Chat.marksTag(cost),
                "<gray>Unlocked</gray> <white>" + vault.unlocked + "/" + maximumSlots + "</white>"
            ), "review the purchase")));
        } else {
            inventory.setItem(UNLOCK_SLOT, icon(Material.NETHER_STAR, "<green>Vault Fully Unlocked</green>", List.of(
                "<gray>All " + maximumSlots + " storage slots are available.</gray>"
            )));
        }
        openVaults.put(account, holder);
        player.openInventory(inventory);
        if (player.getOpenInventory().getTopInventory() != inventory) {
            openVaults.remove(account, holder);
        }
    }

    /** Unlocking spends Marks, so it goes through the shared confirm screen; buySlot re-checks everything. */
    private void confirmUnlock(Player player, BankHolder holder) {
        Vault vault = vault(holder.account);
        if (vault.unlocked >= maximumSlots) {
            Chat.info(player, "Your bank is already fully unlocked.");
            return;
        }
        AccountId account = holder.account;
        int page = holder.page;
        long cost = slotCost(vault.unlocked, defaultSlots, baseCost, costIncrement);
        MenuKit.openConfirm(plugin, player, "<gold><bold>Unlock Bank Slot</bold></gold>",
            icon(Material.GOLD_INGOT, "<gold>Bank Slot " + (vault.unlocked + 1) + "</gold>", List.of(
                "<gray>Unlocked</gray> <white>" + vault.unlocked + "/" + maximumSlots + "</white>",
                "<gray>The slot is yours on every linked character.</gray>")),
            List.of("<gray>Cost</gray> " + Chat.marksTag(cost)),
            confirmed -> {
                open(confirmed, page);
                BankHolder reopened = openVaults.get(account);
                if (reopened != null && reopened.playerId.equals(confirmed.getUniqueId())
                    && confirmed.getOpenInventory().getTopInventory().getHolder() == reopened) {
                    buySlot(confirmed, reopened);
                }
            },
            cancelled -> open(cancelled, page));
    }

    private void buySlot(Player player, BankHolder holder) {
        Vault vault = vault(holder.account);
        if (vault.unlocked >= maximumSlots) {
            Chat.info(player, "Your bank is already fully unlocked.");
            return;
        }
        if (!pending.add(holder.account)) {
            Chat.info(player, "A bank upgrade is already being processed.");
            return;
        }
        int expectedUnlocked = vault.unlocked;
        long cost = slotCost(expectedUnlocked, defaultSlots, baseCost, costIncrement);
        String operation = "bank-slot:" + plugin.getConfig().getString("season.id", "unknown-season")
            + ":" + holder.account + ":" + (expectedUnlocked + 1);
        CompletionStage<EconomyService.TransactionResult> debit;
        try {
            debit = economy.debit(holder.account, cost,
                new EconomyService.TransactionReason("bank.slot", "Unlock slot " + (expectedUnlocked + 1)), operation);
        } catch (RuntimeException exception) {
            pending.remove(holder.account);
            plugin.getLogger().severe("Bank slot purchase failed for " + holder.account + ": " + exception);
            Chat.error(player, "The bank could not finish that purchase. You can safely try again.");
            return;
        }
        debit.whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (failure != null) {
                    plugin.getLogger().severe("Bank slot purchase failed for " + holder.account + ": " + failure);
                    if (player.isOnline()) {
                        Chat.error(player, "The bank could not process that purchase. Please try again.");
                    }
                    return;
                }
                if (result.status() == EconomyService.TransactionResult.Status.INSUFFICIENT_FUNDS) {
                    Chat.error(player, "You need " + Chat.marksTag(cost) + " to unlock that slot.");
                } else if (result.status() == EconomyService.TransactionResult.Status.APPLIED
                    || result.status() == EconomyService.TransactionResult.Status.DUPLICATE) {
                    BankHolder active = openVaults.get(holder.account);
                    if (active != null) {
                        syncPage(active);
                    }
                    Vault current = vault(holder.account);
                    current.unlocked = Math.max(current.unlocked, Math.min(maximumSlots, expectedUnlocked + 1));
                    save();
                    if (player.isOnline()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_VAULT_OPEN_SHUTTER, 0.8F, 1.2F);
                        Chat.success(player, "Bank slot " + current.unlocked + " unlocked"
                            + (result.status() == EconomyService.TransactionResult.Status.APPLIED
                                ? " for " + Chat.marksTag(cost) + "." : ". Your earlier payment was confirmed."));
                    }
                } else {
                    Chat.error(player, "The bank upgrade was rejected. Nothing was spent.");
                }
                if (player.isOnline()
                    && player.getOpenInventory().getTopInventory().getHolder() instanceof BankHolder currentView
                    && currentView.account.equals(holder.account)) {
                    open(player, currentView.page);
                }
            } finally {
                pending.remove(holder.account);
            }
        }));
    }

    private boolean isCurrentView(BankHolder holder, Player player) {
        return holder.playerId.equals(player.getUniqueId())
            && openVaults.get(holder.account) == holder
            && sessions.account(player.getUniqueId()).filter(holder.account::equals).isPresent();
    }

    private void moveIntoVault(Player player, BankHolder holder, int sourceSlot) {
        ItemStack source = player.getInventory().getItem(sourceSlot);
        if (source == null || source.getType().isAir()) {
            return;
        }
        int remaining = source.getAmount();
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot = 0; slot < PAGE_SIZE && remaining > 0; slot++) {
                if (holder.layout.writableIndex(slot) < 0) {
                    continue;
                }
                ItemStack target = holder.inventory.getItem(slot);
                boolean empty = target == null || target.getType().isAir();
                if ((pass == 0 && (empty || !target.isSimilar(source))) || (pass == 1 && !empty)) {
                    continue;
                }
                int current = empty ? 0 : target.getAmount();
                int moved = Math.min(remaining, Math.max(0,
                    Math.min(source.getMaxStackSize(), holder.inventory.getMaxStackSize()) - current));
                if (moved > 0) {
                    ItemStack replacement = source.clone();
                    replacement.setAmount(current + moved);
                    holder.inventory.setItem(slot, replacement);
                    remaining -= moved;
                }
            }
        }
        ItemStack remainder = remaining == 0 ? null : source.clone();
        if (remainder != null) {
            remainder.setAmount(remaining);
        }
        player.getInventory().setItem(sourceSlot, remainder);
    }

    private void syncPage(BankHolder holder) {
        if (openVaults.get(holder.account) != holder) {
            return;
        }
        Vault vault = vault(holder.account);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = holder.layout.writableIndex(slot);
            if (index >= 0) {
                vault.items[index] = clone(holder.inventory.getItem(slot));
            }
        }
    }

    private Vault vault(AccountId account) {
        return vaults.computeIfAbsent(account, ignored -> new Vault(defaultSlots, new ItemStack[54]));
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not read bank storage; refusing to replace existing vaults", exception);
        }
        ConfigurationSection accounts = yaml.getConfigurationSection("accounts");
        if (accounts == null) {
            return;
        }
        for (String raw : accounts.getKeys(false)) {
            try {
                AccountId account = AccountId.parse(raw);
                String path = "accounts." + raw;
                int unlocked = Math.max(defaultSlots, Math.min(54, yaml.getInt(path + ".unlocked", defaultSlots)));
                ItemStack[] stored = new ItemStack[54];
                for (int index = 0; index < unlocked; index++) {
                    stored[index] = clone(yaml.getItemStack(path + ".items." + index));
                }
                vaults.put(account, new Vault(unlocked, stored));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid bank account " + raw + "; stored vaults were not replaced", exception);
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        vaults.forEach((account, vault) -> {
            String path = "accounts." + account;
            yaml.set(path + ".unlocked", vault.unlocked);
            for (int index = 0; index < vault.unlocked; index++) {
                if (vault.items[index] != null && !vault.items[index].getType().isAir()) {
                    yaml.set(path + ".items." + index, vault.items[index]);
                }
            }
        });
        try {
            AtomicYamlFiles.save(yaml, file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save bank storage: " + exception.getMessage());
        }
    }

    private ItemStack clone(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(miniMessage.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false)).toList());
        meta.setAttributeModifiers(HashMultimap.create());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private static final class Vault {
        private int unlocked;
        private final ItemStack[] items;

        private Vault(int unlocked, ItemStack[] items) {
            this.unlocked = unlocked;
            this.items = items;
        }
    }

    private static final class BankHolder implements InventoryHolder {
        private final UUID playerId;
        private final AccountId account;
        private final int page;
        private final BankPageLayout layout;
        private Inventory inventory;

        private BankHolder(UUID playerId, AccountId account, int page, int unlocked) {
            this.playerId = playerId;
            this.account = account;
            this.page = page;
            this.layout = new BankPageLayout(page, unlocked);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
