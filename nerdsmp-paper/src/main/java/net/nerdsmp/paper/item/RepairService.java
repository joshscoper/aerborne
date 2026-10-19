package net.nerdsmp.paper.item;

import com.google.common.collect.HashMultimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.session.SessionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class RepairService implements Listener {
    private static final int REFRESH_SLOT = 4;
    private final JavaPlugin plugin;
    private final SessionRegistry sessions;
    private final EconomyService economy;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Set<AccountId> pending = new HashSet<>();
    private final Set<AccountId> reviewRequired = new HashSet<>();
    private final long baseCost;
    private final long perItemCost;
    private final double perDurabilityCost;
    private final long maximumCost;

    public RepairService(JavaPlugin plugin, SessionRegistry sessions, EconomyService economy) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.economy = economy;
        baseCost = Math.max(0L, plugin.getConfig().getLong("repairs.base-cost", 20L));
        perItemCost = Math.max(0L, plugin.getConfig().getLong("repairs.per-item-cost", 15L));
        perDurabilityCost = Math.max(0.0D,
            plugin.getConfig().getDouble("repairs.per-durability-point", 0.20D));
        maximumCost = Math.max(1L, plugin.getConfig().getLong("repairs.maximum-cost", 25_000L));
    }

    public void open(Player player) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) {
            Chat.accountLoading(player);
            return;
        }
        if (!session(player, account)) {
            Chat.error(player, "Switch to Survival to repair your equipment.");
            return;
        }
        if (reviewRequired.contains(account)) {
            player.sendRichMessage("<red>Your last repair needs a staff review before you can repair again.</red>");
            return;
        }
        if (pending.contains(account)) {
            Chat.info(player, "Your repair is still being processed.");
            return;
        }
        BatchQuote quote = quoteAll(player);
        RepairInventory holder = new RepairInventory(player.getUniqueId(), account, quote);
        Inventory inventory = Bukkit.createInventory(holder, 27, message("<gold><bold>Repair All</bold></gold>"));
        holder.inventory = inventory;
        // Laid out like MenuKit.openConfirm, but kept as its own screen: payment checks that it stays open.
        MenuKit.frame(inventory);
        if (quote.items() == 0) {
            inventory.setItem(MenuKit.CONFIRM_SUMMARY_SLOT, icon(Material.BARRIER, "<gray>Everything is in good condition</gray>", List.of(
                "<gray>Checks your inventory, hotbar, armor and off-hand.</gray>",
                "<gray>Place damaged equipment in the forge to repair one item.</gray>"
            )));
            inventory.setItem(MenuKit.closeSlot(27), MenuKit.close());
        } else {
            inventory.setItem(MenuKit.CONFIRM_SUMMARY_SLOT, icon(Material.ANVIL, "<gold>Repair All</gold>", List.of(
                "<gray>Items</gray> <white>" + quote.items() + "</white>",
                "<gray>Durability restored</gray> <white>" + quote.missingDurability() + "</white>",
                "",
                "<gray>Includes inventory, hotbar, armor and off-hand.</gray>",
                "<gray>Gems, enchantments and improvements stay intact.</gray>"
            )));
            inventory.setItem(MenuKit.CONFIRM_CANCEL_SLOT, MenuKit.cancel());
            inventory.setItem(MenuKit.CONFIRM_SLOT, MenuKit.confirm(MenuKit.withHint(List.of(
                "<gray>Cost</gray> " + Chat.marksTag(quote.cost())), "pay and repair")));
        }
        inventory.setItem(REFRESH_SLOT, icon(Material.CLOCK, "<aqua>Refresh Quote</aqua>", MenuKit.withHint(List.of(
            "<gray>Check your equipment again. Nothing is spent.</gray>"), "refresh")));
        player.openInventory(inventory);
    }

    /** A defensive snapshot and a quote for exactly one item, regardless of its gear system. */
    public ItemQuote quoteItem(ItemStack item) {
        if (item == null || item.getAmount() < 1 || item.getType().isAir()) {
            return new ItemQuote(ItemState.NO_ITEM, null, 0, 0);
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()
            || (damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability()) <= 0) {
            return new ItemQuote(ItemState.NOT_REPAIRABLE, null, 0, 0);
        }
        if (item.getAmount() != 1) return new ItemQuote(ItemState.STACKED, null, 0, 0);
        int damage = damageable.getDamage();
        if (damage <= 0) return new ItemQuote(ItemState.UNDAMAGED, item.clone(), 0, 0);
        return new ItemQuote(ItemState.READY, item.clone(), damage, price(1, damage));
    }

    /** Keeps the item in its input slot. GUI callbacks must guard their current owner and view. */
    public void repairInput(Player player, Inventory input, int slot, ItemQuote offered,
                            BooleanSupplier currentContext, Consumer<Result> completed) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            AccountId account = sessions.account(player.getUniqueId()).orElse(null);
            if (account == null || !session(player, account) || !safeContext(currentContext)) {
                completed.accept(new Result(State.CONTEXT_CHANGED, 0, 0));
                return;
            }
            if (offered == null || !offered.ready()) {
                completed.accept(new Result(State.NOTHING_TO_REPAIR, 0, 0));
                return;
            }
            if (!offered.matches(input.getItem(slot))) {
                completed.accept(new Result(State.INPUT_CHANGED, 0, 0));
                return;
            }
            ItemStack repaired = repairCopy(offered.snapshot);
            RepairPlan<ItemStack> plan = new RepairPlan<>(List.of(
                new RepairPlan.Entry<>(slot, offered.snapshot.clone(), repaired)));
            execute(player, account, input, plan, offered.cost(), "item", currentContext, completed);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RepairInventory holder)) return;
        boolean previouslyCancelled = event.isCancelled();
        event.setCancelled(true);
        if (previouslyCancelled || event.getClick() != ClickType.LEFT || holder.actionPending
            || !(event.getWhoClicked() instanceof Player player) || !holder.playerId.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        boolean ready = holder.quote.items() > 0;
        boolean button = ready ? slot == MenuKit.CONFIRM_SLOT || slot == MenuKit.CONFIRM_CANCEL_SLOT : slot == MenuKit.closeSlot(27);
        if (!button && slot != REFRESH_SLOT) return;
        if (slot != MenuKit.closeSlot(27)) MenuKit.clickSound(player);
        holder.actionPending = true;
        // Inventory transitions and payment always run after the click transaction ends.
        Bukkit.getScheduler().runTask(plugin, () -> {
            holder.actionPending = false;
            if (!current(player, holder)) return;
            if (slot == REFRESH_SLOT) open(player);
            else if (ready && slot == MenuKit.CONFIRM_SLOT) confirmAll(player, holder);
            else player.closeInventory();
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RepairInventory) event.setCancelled(true);
    }

    private void confirmAll(Player player, RepairInventory holder) {
        BatchQuote offered = holder.quote;
        if (!offered.plan().matches(player.getInventory()::getItem)) {
            Chat.notice(player, "Your equipment changed. Review the updated quote.");
            open(player);
            return;
        }
        execute(player, holder.account, player.getInventory(), offered.plan(), offered.cost(), "all",
            () -> current(player, holder), result -> {
                if (result.state() == State.REPAIRED) {
                    if (current(player, holder)) player.closeInventory();
                    Chat.success(player, "Repaired " + result.items() + " items for " + Chat.marksTag(result.cost()) + ".");
                } else {
                    tellResult(player, result);
                    if (current(player, holder) && result.state() != State.BUSY) open(player);
                }
            });
    }

    private void execute(Player player, AccountId account, Inventory input, RepairPlan<ItemStack> plan,
                         long cost, String scope, BooleanSupplier currentContext, Consumer<Result> completed) {
        if (!session(player, account) || !safeContext(currentContext)) {
            completed.accept(new Result(State.CONTEXT_CHANGED, 0, 0));
            return;
        }
        if (!plan.matches(input::getItem)) {
            completed.accept(new Result(State.INPUT_CHANGED, 0, 0));
            return;
        }
        if (reviewRequired.contains(account)) {
            completed.accept(new Result(State.REVIEW_REQUIRED, 0, 0));
            return;
        }
        if (!pending.add(account)) {
            completed.accept(new Result(State.BUSY, 0, 0));
            return;
        }
        String operation = "repair:" + scope + ":" + UUID.randomUUID();
        CompletionStage<EconomyService.TransactionResult> debit;
        try {
            debit = economy.debit(account, cost,
                new EconomyService.TransactionReason("repair." + scope, plan.size() + " items"), operation);
            if (debit == null) throw new IllegalStateException("Repair payment returned no result stage");
        } catch (RuntimeException error) {
            review(operation, error);
            finish(account, completed, new Result(State.REVIEW_REQUIRED, plan.size(), cost));
            return;
        }
        debit.whenComplete((result, error) -> sync(() -> {
                if (error != null || result == null) {
                    review(operation, error);
                    finish(account, completed, new Result(State.REVIEW_REQUIRED, plan.size(), cost));
                    return;
                }
                if (result.status() != EconomyService.TransactionResult.Status.APPLIED
                    && result.status() != EconomyService.TransactionResult.Status.DUPLICATE) {
                    finish(account, completed, new Result(State.INSUFFICIENT_FUNDS, 0, 0));
                    return;
                }
                if (!session(player, account) || !safeContext(currentContext)) {
                    refund(account, cost, operation, scope, completed);
                    return;
                }
                RepairPlan.ApplyResult applied = plan.apply(input::getItem, (slot, item) -> input.setItem(slot, item.clone()));
                switch (applied) {
                    case APPLIED -> finish(account, completed, new Result(State.REPAIRED, plan.size(), cost));
                    case INPUT_CHANGED, UNAVAILABLE -> refund(account, cost, operation, scope, completed);
                    case ALREADY_ATTEMPTED, REVIEW_REQUIRED -> {
                        review(operation, null);
                        finish(account, completed, new Result(State.REVIEW_REQUIRED, plan.size(), cost));
                    }
                }
            }));
    }

    private void refund(AccountId account, long cost, String operation, String scope, Consumer<Result> completed) {
        CompletionStage<EconomyService.TransactionResult> credit;
        try {
            credit = economy.credit(account, cost,
                new EconomyService.TransactionReason("repair." + scope + ".refund", "Repair view or equipment changed before application"),
                operation + ":refund");
            if (credit == null) throw new IllegalStateException("Repair refund returned no result stage");
        } catch (RuntimeException error) {
            review(operation + ":refund", error);
            finish(account, completed, new Result(State.REVIEW_REQUIRED, 0, cost));
            return;
        }
        credit.whenComplete((result, error) -> sync(() -> {
                if (error != null || result == null
                    || (result.status() != EconomyService.TransactionResult.Status.APPLIED
                    && result.status() != EconomyService.TransactionResult.Status.DUPLICATE)) {
                    review(operation + ":refund", error);
                    finish(account, completed, new Result(State.REVIEW_REQUIRED, 0, cost));
                } else {
                    finish(account, completed, new Result(State.REFUNDED, 0, cost));
                }
            }));
    }

    private void finish(AccountId account, Consumer<Result> completed, Result result) {
        pending.remove(account);
        if (result.state() == State.REVIEW_REQUIRED) reviewRequired.add(account);
        completed.accept(result);
    }

    private boolean safeContext(BooleanSupplier currentContext) {
        try {
            return currentContext.getAsBoolean();
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Repair context is no longer available", error);
            return false;
        }
    }

    private void review(String operation, Throwable error) {
        plugin.getLogger().log(Level.SEVERE,
            "Repair requires staff review; do not replay or automatically refund: " + operation, error);
    }

    public void tellResult(Player player, Result result) {
        if (!player.isOnline()) return;
        player.sendRichMessage(switch (result.state()) {
            case REPAIRED -> "<green>Repaired for " + Chat.marksTag(result.cost()) + ".</green>";
            case NOTHING_TO_REPAIR -> "<gray>That item does not need repair.</gray>";
            case INPUT_CHANGED -> "<yellow>The item changed. Review it again. Nothing was spent.</yellow>";
            case CONTEXT_CHANGED -> "<yellow>Reopen the forge to repair your equipment. Nothing was spent.</yellow>";
            case INSUFFICIENT_FUNDS -> "<red>You do not have enough Marks for that repair.</red>";
            case BUSY -> "<gray>Your repair is already being processed.</gray>";
            case REFUNDED -> "<yellow>The view or equipment changed, so " + Chat.marksTag(result.cost()) + " went back to you.</yellow>";
            case REVIEW_REQUIRED -> "<red>That repair needs a staff review. Please do not retry it yet.</red>";
        });
    }

    private BatchQuote quoteAll(Player player) {
        List<RepairPlan.Entry<ItemStack>> entries = new ArrayList<>();
        long missing = 0L;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemQuote item = quoteItem(contents[slot]);
            if (!item.ready()) continue;
            entries.add(new RepairPlan.Entry<>(slot, item.snapshot.clone(), repairCopy(item.snapshot)));
            missing += item.missingDurability();
        }
        return new BatchQuote(entries.isEmpty() ? null : new RepairPlan<>(entries), missing, price(entries.size(), missing));
    }

    private long price(int count, long missing) {
        return RepairPricing.quote(count, missing, baseCost, perItemCost, perDurabilityCost, maximumCost);
    }

    private static ItemStack repairCopy(ItemStack snapshot) {
        ItemStack repaired = snapshot.clone();
        ItemMeta meta = repaired.getItemMeta();
        ((Damageable) meta).setDamage(0);
        repaired.setItemMeta(meta);
        return repaired;
    }

    private boolean current(Player player, RepairInventory holder) {
        return session(player, holder.account) && player.getOpenInventory().getTopInventory() == holder.inventory;
    }

    private boolean session(Player player, AccountId account) {
        return player.isOnline() && !player.isDead() && Bukkit.getPlayer(player.getUniqueId()) == player
            && player.getGameMode() == GameMode.SURVIVAL
            && sessions.account(player.getUniqueId()).filter(account::equals).isPresent();
    }

    private void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(message(name));
        meta.displayName(null);
        meta.lore(lore.stream().map(this::message).toList());
        meta.removeEnchantments();
        meta.setAttributeModifiers(HashMultimap.create());
        meta.addItemFlags(ItemFlag.values());
        meta.setEnchantmentGlintOverride(false);
        item.setItemMeta(meta);
        return item;
    }

    private Component message(String markup) {
        return miniMessage.deserialize(markup).decoration(TextDecoration.ITALIC, false);
    }

    public enum ItemState { READY, NO_ITEM, NOT_REPAIRABLE, STACKED, UNDAMAGED }
    public enum State { REPAIRED, NOTHING_TO_REPAIR, INPUT_CHANGED, CONTEXT_CHANGED, INSUFFICIENT_FUNDS, BUSY, REFUNDED, REVIEW_REQUIRED }
    public record Result(State state, int items, long cost) { }

    public static final class ItemQuote {
        private final ItemState state;
        private final ItemStack snapshot;
        private final int missingDurability;
        private final long cost;

        private ItemQuote(ItemState state, ItemStack snapshot, int missingDurability, long cost) {
            this.state = state;
            this.snapshot = snapshot;
            this.missingDurability = missingDurability;
            this.cost = cost;
        }

        public ItemState state() { return state; }
        public boolean ready() { return state == ItemState.READY; }
        public int missingDurability() { return missingDurability; }
        public long cost() { return cost; }
        public boolean matches(ItemStack item) { return ready() && snapshot.equals(item); }
    }

    private record BatchQuote(RepairPlan<ItemStack> plan, long missingDurability, long cost) {
        int items() { return plan == null ? 0 : plan.size(); }
    }

    private static final class RepairInventory implements InventoryHolder {
        private final UUID playerId;
        private final AccountId account;
        private final BatchQuote quote;
        private Inventory inventory;
        private boolean actionPending;

        private RepairInventory(UUID playerId, AccountId account, BatchQuote quote) {
            this.playerId = playerId;
            this.account = account;
            this.quote = quote;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
