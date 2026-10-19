package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.session.SessionRegistry;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

/** Quotes are inventory snapshots, never promises to process whatever happens to be there later. */
public final class IdentificationService implements Listener, AutoCloseable {
    public static final List<String> SUBCOMMANDS = List.of("held", "all", "scrap");
    private final NerdSmpPlugin plugin;
    private final SessionRegistry sessions;
    private final EconomyService economy;
    private final PaperItemService items;
    private final LootProcessingRules rules;
    private final LootProcessingReceipts receipts;
    private final Set<AccountId> pending = new HashSet<>();
    private final Set<AccountId> reviewRequired = new HashSet<>();
    private volatile boolean closed;

    public IdentificationService(NerdSmpPlugin plugin, SessionRegistry sessions, EconomyService economy, PaperItemService items) {
        this.plugin = plugin; this.sessions = sessions; this.economy = economy; this.items = items;
        Map<String, Integer> dust = new HashMap<>();
        LootProcessingRules.DEFAULT_DUST.forEach((rarity, amount) -> dust.put(rarity,
            plugin.getConfig().getInt("identification.scrapping.dust-by-rarity." + rarity.toLowerCase(Locale.ROOT), amount)));
        rules = new LootProcessingRules(plugin.getConfig().getLong("identification.base-cost-marks", 75),
            plugin.getConfig().getLong("identification.per-affix-cost-marks", 25),
            plugin.getConfig().getInt("identification.scrapping.dust-per-affix", 1), dust);
        receipts = new LootProcessingReceipts(new File(plugin.getDataFolder(), "item-processing"));
    }

    public void open(Player player) {
        AccountId account = ready(player);
        if (account == null) return;
        try {
            ItemStack[] storage = player.getInventory().getStorageContents();
            int held = player.getInventory().getHeldItemSlot();
            BatchQuote one = quote("held", storage, held), all = quote("all", storage, held), scrap = quote("scrap", storage, held);
            Menu holder = new Menu(player.getUniqueId(), account);
            holder.inventory = Bukkit.createInventory(holder, 45, Chat.render("<yellow><bold>Arcane Identification</bold></yellow>"));
            MenuKit.frame(holder.inventory);
            holder.inventory.setItem(4, MenuKit.item(Material.SPYGLASS, "<yellow>Sealed equipment</yellow>", List.of(
                "<gray>" + all.count + " items in your backpack and hotbar.</gray>",
                "<gray>Armor, off-hand and cursor items stay untouched.</gray>",
                "<gray>Review the batch before anything is spent.</gray>")));
            option(holder, 20, Material.SPYGLASS, "Identify Held", one, "held");
            option(holder, 22, Material.ENDER_EYE, "Identify All", all, "all");
            option(holder, 24, Material.GRINDSTONE, "Scrap All", scrap, "scrap");
            holder.inventory.setItem(MenuKit.closeSlot(45), MenuKit.close());
            player.openInventory(holder.inventory);
        } catch (RuntimeException failure) {
            quoteFailure(player, failure);
        }
    }

    /** Command shortcuts still open the same explicit confirmation as Nyra's menu. */
    public void openAction(Player player, String action) {
        AccountId account = ready(player);
        if (account == null) return;
        if (!SUBCOMMANDS.contains(action)) { Chat.usage(player, "/identify [held|all|scrap]"); return; }
        try {
            BatchQuote quote = quote(action, player.getInventory().getStorageContents(), player.getInventory().getHeldItemSlot());
            if (quote.count == 0) {
                Chat.notice(player, action.equals("held") ? "Hold a sealed piece of equipment in your main hand."
                    : "You have no sealed equipment in your backpack or hotbar.");
                return;
            }
            if (quote.plan == null) { Chat.error(player, "Make room for the Arcane Dust before scrapping this batch."); return; }
            boolean scrap = action.equals("scrap");
            List<String> summary = new ArrayList<>();
            summary.add("<gray>Sealed equipment</gray> <white>" + quote.count + "</white>");
            quote.rarities.forEach((rarity, count) -> summary.add("<gray>" + Chat.escape(title(rarity)) + "</gray> <white>" + count + "</white>"));
            summary.add("");
            summary.add(scrap ? "<red>These items will be destroyed. This cannot be undone.</red>"
                : "<gray>Reveals the existing affixes, sockets and item names.</gray>");
            List<String> detail = scrap ? List.of("<gray>Receive</gray> <light_purple>" + quote.dust + " Arcane Dust</light_purple>",
                "<red>Destroys all " + quote.count + " quoted items.</red>", "<gray>No Marks are spent.</gray>")
                : List.of("<gray>Items</gray> <white>" + quote.count + "</white>", "<gray>Cost</gray> " + Chat.marksTag(quote.marks),
                    "<gray>One payment for the whole batch.</gray>");
            MenuKit.openConfirm(plugin, player, scrap ? "<red><bold>Scrap Sealed Gear</bold></red>"
                    : "<yellow><bold>Arcane Identification</bold></yellow>",
                MenuKit.item(scrap ? Material.GRINDSTONE : Material.SPYGLASS, scrap ? "<red>Scrap " + quote.count + " items</red>"
                    : "<yellow>Identify " + quote.count + " items</yellow>", summary), detail,
                confirmed -> process(confirmed, account, quote), this::open);
        } catch (RuntimeException failure) {
            quoteFailure(player, failure);
        }
    }

    public long cost(ItemStack item) { return rules.cost(items.affixes(item).size()); }

    private void option(Menu menu, int slot, Material material, String name, BatchQuote quote, String action) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Items</gray> <white>" + quote.count + "</white>");
        lore.add(action.equals("scrap") ? "<gray>Receive</gray> <light_purple>" + quote.dust + " Arcane Dust</light_purple>"
            : "<gray>Cost</gray> " + Chat.marksTag(quote.marks));
        if (action.equals("scrap")) lore.add("<gray>Only unidentified equipment. Scrapping is permanent.</gray>");
        if (quote.count == 0 || quote.plan == null) {
            lore.add(quote.count == 0 ? "<gray>No eligible sealed equipment.</gray>" : "<red>Make room for the dust first.</red>");
            menu.inventory.setItem(slot, MenuKit.locked(material, name, lore));
        } else {
            menu.inventory.setItem(slot, MenuKit.item(material, (action.equals("scrap") ? "<red>" : "<yellow>") + name
                + (action.equals("scrap") ? "</red>" : "</yellow>"), MenuKit.withHint(lore, "review")));
            menu.actions.put(slot, action);
        }
    }

    private BatchQuote quote(String action, ItemStack[] contents, int heldSlot) {
        if (!SUBCOMMANDS.contains(action) || contents.length != 36 || heldSlot < 0 || heldSlot > 8)
            throw new IllegalArgumentException("Invalid item-processing request");
        List<ItemStack> before = copy(Arrays.asList(contents)), after = copy(before);
        Map<UUID, Integer> identities = new HashMap<>();
        for (ItemStack item : before) if (items.isAuthentic(item))
            items.lootInstance(item).ifPresent(id -> identities.merge(id, 1, Integer::sum));
        Map<String, Integer> rarities = new TreeMap<>();
        List<String> instances = new ArrayList<>();
        long marks = 0; int dust = 0;
        for (int slot = 0; slot < before.size(); slot++) {
            if (action.equals("held") && slot != heldSlot) continue;
            ItemStack original = before.get(slot);
            PaperItemService.SealedGear gear = items.sealedGear(original).orElse(null);
            if (gear == null) continue;
            if (identities.getOrDefault(gear.instance(), 0) != 1) throw new IllegalStateException("Duplicate loot identity in batch");
            instances.add(gear.instance().toString()); rarities.merge(gear.rarity(), 1, Integer::sum);
            if (action.equals("scrap")) { dust = Math.addExact(dust, rules.dust(gear.rarity(), gear.affixes())); after.set(slot, null); }
            else {
                marks = Math.addExact(marks, rules.cost(gear.affixes()));
                if (!items.identify(after.get(slot))) throw new IllegalStateException("Unable to prepare sealed item");
            }
        }
        boolean room = dust == 0 || insertDust(after, dust);
        InventoryBatchPlan<ItemStack> plan = instances.isEmpty() || !room ? null : new InventoryBatchPlan<>(before, after);
        return new BatchQuote(action, before, after, instances.size(), marks, dust, Map.copyOf(rarities), List.copyOf(instances), plan);
    }

    private boolean insertDust(List<ItemStack> storage, int amount) {
        ItemStack template = plugin.enchanting().createArcaneDust(1);
        int remaining = amount;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) for (int slot = 0; slot < storage.size() && remaining > 0; slot++) {
            ItemStack current = storage.get(slot);
            if (pass == 0 && (current == null || !current.isSimilar(template)) || pass == 1 && current != null) continue;
            int held = current == null ? 0 : current.getAmount();
            int maximum = current == null ? template.getMaxStackSize() : Math.min(current.getMaxStackSize(), template.getMaxStackSize());
            int moved = Math.min(remaining, Math.max(0, maximum - held));
            if (moved == 0) continue;
            ItemStack next = current == null ? template.clone() : current.clone(); next.setAmount(held + moved);
            storage.set(slot, next); remaining -= moved;
        }
        return remaining == 0;
    }

    private void process(Player player, AccountId account, BatchQuote quote) {
        if (!session(player, account) || ready(player) == null) return;
        if (quote.plan == null || !quote.plan.matches(storage(player))) {
            Chat.notice(player, "Your inventory changed. Review the batch again. Nothing was spent."); return;
        }
        if (!pending.add(account)) { Chat.info(player, "Your items are already being processed."); return; }
        LootProcessingReceipts.Receipt receipt;
        try {
            receipt = new LootProcessingReceipts.Receipt(UUID.randomUUID(), account, quote.action, quote.marks, quote.dust,
                quote.count, Instant.now(), quote.instances, encode(quote.before), encode(quote.after), LootProcessingReceipts.Phase.PREPARED);
            receipts.begin(receipt);
        } catch (RuntimeException failure) { hold(player, account, null, failure); return; }
        if (quote.marks == 0) { apply(player, quote, receipt); return; }
        CompletionStage<EconomyService.TransactionResult> debit;
        try {
            debit = economy.debit(account, quote.marks, new EconomyService.TransactionReason("item.identify", quote.count + " sealed items"),
                paymentKey(receipt));
            if (debit == null) throw new IllegalStateException("Identification debit returned no stage");
        } catch (RuntimeException failure) { hold(player, account, receipt, failure); return; }
        debit.whenComplete((result, failure) -> sync(() -> {
            if (failure != null || result == null) { hold(player, account, receipt, failure); return; }
            if (result.status() == EconomyService.TransactionResult.Status.INSUFFICIENT_FUNDS) {
                if (finish(player, receipt, LootProcessingReceipts.Phase.DECLINED))
                    tell(player, "You need " + Chat.marksTag(quote.marks) + " to identify this batch.", true);
            } else if (applied(result)) apply(player, quote, receipt);
            else hold(player, account, receipt, null);
        }));
    }

    private void apply(Player player, BatchQuote quote, LootProcessingReceipts.Receipt prepared) {
        if (!session(player, prepared.account()) || !quote.plan.matches(storage(player))) { refund(player, prepared); return; }
        try { receipts.advance(prepared, LootProcessingReceipts.Phase.APPLYING); }
        catch (RuntimeException failure) { hold(player, prepared.account(), prepared, failure); return; }
        var applying = prepared.at(LootProcessingReceipts.Phase.APPLYING);
        InventoryBatchPlan.Result result = quote.plan.apply(() -> storage(player), next -> player.getInventory().setStorageContents(copy(next).toArray(ItemStack[]::new)));
        if (result == InventoryBatchPlan.Result.INPUT_CHANGED || result == InventoryBatchPlan.Result.UNAVAILABLE) { refund(player, applying); return; }
        if (result != InventoryBatchPlan.Result.APPLIED) { hold(player, prepared.account(), applying, null); return; }
        try {
            // Save the native inventory before marking the receipt complete. An interruption
            // in this window stays pending for staff review; it is never blindly replayed.
            player.saveData();
        } catch (RuntimeException failure) { hold(player, prepared.account(), applying, failure); return; }
        if (!finish(player, applying, LootProcessingReceipts.Phase.COMPLETE)) return;
        plugin.gearSets().refreshInventory(player); plugin.presentation().refresh(player);
        player.playSound(player.getLocation(), quote.action.equals("scrap") ? Sound.BLOCK_GRINDSTONE_USE : Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.2F);
        Chat.success(player, quote.action.equals("scrap") ? "Scrapped " + quote.count + " items for <light_purple>" + quote.dust + " Arcane Dust</light_purple>."
            : "Identified " + quote.count + (quote.count == 1 ? " item" : " items") + " for " + Chat.marksTag(quote.marks) + ".");
        plugin.getLogger().info("Item processing complete: " + prepared.operation() + " account=" + prepared.account()
            + " action=" + quote.action + " items=" + quote.count + " marks=" + quote.marks + " dust=" + quote.dust);
    }

    private void refund(Player player, LootProcessingReceipts.Receipt receipt) {
        if (receipt.marks() == 0) {
            if (finish(player, receipt, LootProcessingReceipts.Phase.REFUNDED)) tell(player, "Your inventory or session changed. Nothing was processed.", false);
            return;
        }
        CompletionStage<EconomyService.TransactionResult> refund;
        try {
            refund = economy.credit(receipt.account(), receipt.marks(),
                new EconomyService.TransactionReason("item.identify.refund", "Inventory or session changed before identification"), paymentKey(receipt) + ":refund");
            if (refund == null) throw new IllegalStateException("Identification refund returned no stage");
        } catch (RuntimeException failure) { hold(player, receipt.account(), receipt, failure); return; }
        refund.whenComplete((result, failure) -> sync(() -> {
            if (failure != null || !applied(result)) { hold(player, receipt.account(), receipt, failure); return; }
            if (finish(player, receipt, LootProcessingReceipts.Phase.REFUNDED))
                tell(player, "Your inventory or session changed. Returned " + Chat.marksTag(receipt.marks()) + ".", false);
        }));
    }

    private boolean finish(Player player, LootProcessingReceipts.Receipt receipt, LootProcessingReceipts.Phase phase) {
        try { receipts.advance(receipt, phase); pending.remove(receipt.account()); return true; }
        catch (RuntimeException failure) { hold(player, receipt.account(), receipt, failure); return false; }
    }

    private AccountId ready(Player player) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) { Chat.accountLoading(player); return null; }
        if (!session(player, account)) { Chat.error(player, "Use Survival or Adventure mode to process equipment."); return null; }
        if (pending.contains(account)) { Chat.info(player, "Your items are still being processed."); return null; }
        try {
            if (reviewRequired.contains(account) || receipts.find(account).filter(LootProcessingReceipts.Receipt::pending).isPresent()) {
                Chat.error(player, "Your last item batch needs a staff review before you can process more gear."); return null;
            }
        } catch (RuntimeException failure) { hold(player, account, null, failure); return null; }
        return account;
    }

    private void hold(Player player, AccountId account, LootProcessingReceipts.Receipt receipt, Throwable failure) {
        pending.remove(account); reviewRequired.add(account);
        plugin.getLogger().log(Level.SEVERE, "Item processing held for review; do not replay or automatically refund: account=" + account
            + " operation=" + (receipt == null ? "unprepared" : receipt.operation()), failure);
        tell(player, "That item batch needs a staff review. Please do not retry it yet.", true);
    }

    private void quoteFailure(Player player, RuntimeException failure) {
        plugin.getLogger().log(Level.WARNING, "Cannot prepare sealed-item batch for " + player.getUniqueId(), failure);
        Chat.error(player, "Some sealed equipment could not be checked. Ask staff to inspect it; nothing was spent.");
    }

    private void tell(Player player, String message, boolean error) {
        if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) return;
        if (error) Chat.error(player, message); else Chat.notice(player, message);
    }

    private boolean session(Player player, AccountId account) {
        return !closed && player.isOnline() && !player.isDead() && Bukkit.getPlayer(player.getUniqueId()) == player
            && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            && sessions.account(player.getUniqueId()).filter(account::equals).isPresent();
    }

    private void sync(Runnable task) {
        if (closed || !plugin.isEnabled()) return; // The durable pending receipt remains for review on restart.
        if (Bukkit.isPrimaryThread()) task.run(); else Bukkit.getScheduler().runTask(plugin, () -> { if (!closed) task.run(); });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        boolean canceled = event.isCancelled(); event.setCancelled(true);
        if (canceled || event.getClick() != ClickType.LEFT || menu.answered || !(event.getWhoClicked() instanceof Player player)
            || !player.getUniqueId().equals(menu.owner)) return;
        String action = menu.actions.get(event.getRawSlot());
        if (action == null && event.getRawSlot() != MenuKit.closeSlot(45)) return;
        menu.answered = true; MenuKit.clickSound(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!session(player, menu.account) || player.getOpenInventory().getTopInventory() != menu.inventory) return;
            player.closeInventory(); if (action != null) openAction(player, action);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @Override public void close() { closed = true; }
    private static boolean applied(EconomyService.TransactionResult result) {
        return result != null && (result.status() == EconomyService.TransactionResult.Status.APPLIED || result.status() == EconomyService.TransactionResult.Status.DUPLICATE);
    }
    private static String paymentKey(LootProcessingReceipts.Receipt receipt) { return "identify:" + receipt.operation(); }
    private static String title(String text) { return text.substring(0, 1) + text.substring(1).toLowerCase(Locale.ROOT); }
    private static List<ItemStack> storage(Player player) { return copy(Arrays.asList(player.getInventory().getStorageContents())); }
    private static List<ItemStack> copy(List<ItemStack> contents) {
        List<ItemStack> result = new ArrayList<>(contents.size());
        for (ItemStack item : contents) result.add(item == null || item.getType().isAir() || item.getAmount() <= 0 ? null : item.clone());
        return result;
    }
    private static List<String> encode(List<ItemStack> contents) {
        return contents.stream().map(item -> item == null ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes())).toList();
    }
    private record BatchQuote(String action, List<ItemStack> before, List<ItemStack> after, int count, long marks, int dust,
                              Map<String, Integer> rarities, List<String> instances, InventoryBatchPlan<ItemStack> plan) { }
    private static final class Menu implements InventoryHolder {
        final UUID owner; final AccountId account; final Map<Integer, String> actions = new HashMap<>();
        Inventory inventory; boolean answered;
        Menu(UUID owner, AccountId account) { this.owner = owner; this.account = account; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
