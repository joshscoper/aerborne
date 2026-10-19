package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.market.MarketController;
import net.nerdsmp.paper.market.MarketPlayerSave;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import static net.nerdsmp.paper.npc.NpcMarket.*;

/** Journaled NPC trades; uncertain inventory boundaries require explicit staff inspection. */
final class NpcVendorTrading {
    private final NerdSmpPlugin plugin;
    private final Supplier<NpcContent> content;
    private final NpcShopLedger limits;
    private final NpcMarket market;
    private final MarketPlayerSave playerSave;
    private final Set<UUID> processing = new HashSet<>();
    NpcVendorTrading(NerdSmpPlugin plugin, Supplier<NpcContent> content, NpcShopLedger limits, String season) {
        this.plugin = plugin; this.content = content; this.limits = limits;
        market = new NpcMarket(new File(plugin.getDataFolder(), "npc-market-" + season.replaceAll("[^a-zA-Z0-9_-]", "_") + ".yml"),
            NpcRecipePriceGuard.captureVanillaRecipes());
        playerSave = new MarketPlayerSave(plugin);
        // Stuck vendor trades are listed and resolved through /market review with the other queues.
        if (plugin.marketController() != null) plugin.marketController().registerVendorReview(new Review());
    }
    Quote quote(NpcContent.ShopProduct product, NpcContent.ReputationView standing, Side side) { return market.quote(content.get(), product, standing, side, Instant.now()); }
    Quote quote(NpcContent.ShopProduct product, NpcContent.ReputationView standing, Side side, int units) {
        return market.quote(content.get(), product, standing, side, units, Instant.now());
    }
    String trend(NpcContent.ShopProduct product) { return market.trend(product); }
    Snapshot snapshot(NpcContent.ShopProduct product, int units) {
        return market.snapshot(content.get(), product, units, Instant.now());
    }
    void trade(Player player, AccountId account, NpcContent.ShopDefinition shop, NpcContent.ShopProduct product,
               Quote quote, BooleanSupplier current, Runnable reopen) {
        // Inventory-click callbacks must not mutate/open inventories during the originating event.
        sync(() -> executeTrade(player, account, shop, product, quote, current, reopen));
    }
    private void executeTrade(Player player, AccountId account, NpcContent.ShopDefinition shop, NpcContent.ShopProduct product,
                              Quote quote, BooleanSupplier current, Runnable reopen) {
        BooleanSupplier session = () -> currentSession(player, account, current);
        if (!session.getAsBoolean()) return;
        NpcContent.ShopDefinition latest = content.get().shops().get(shop.id());
        if (!shop.equals(latest) || !latest.products().contains(product) || !commodity(product).equals(quote.commodity())
            || quote.side() == Side.SELL && (quote.units() != product.amount() || product.sellPrice() < 1)
            || quote.side() == Side.BUY && product.basePrice() < 1) {
            Chat.error(player, "The shop has changed. Browse its wares again."); refresh(session, reopen); return;
        }
        ItemStack item;
        try { item = ShopItems.create(plugin.items(), product, quote.units()); }
        catch (IllegalArgumentException error) { Chat.error(player, "That bundle is not available."); refresh(session, reopen); return; }
        ItemStack[] planned = plan(player.getInventory().getStorageContents(), item, quote.side());
        if (planned == null) { Chat.error(player, quote.side() == Side.BUY ? "Make room for the whole bundle first." : "You need a matching bundle in your inventory."); return; }
        Trade trade;
        try {
            trade = market.begin(account, shop.id(), product.id(), quote, Instant.now());
        } catch (RuntimeException error) { Chat.error(player, Chat.escape(String.valueOf(error.getMessage()))); refresh(session, reopen); return; }
        processing.add(trade.id());
        try {
            if (quote.side() == Side.SELL) {
                if (!limits.reserve(trade.id(), account, shop.id(), product.id(), product.dailySellLimit())) {
                    market.cancel(trade.id()); processing.remove(trade.id()); Chat.error(player, "You have reached today's limit for this bundle."); refresh(session, reopen); return;
                }
                market.transition(trade.id(), Phase.RESERVED, Phase.INVENTORY);
                player.getInventory().setStorageContents(planned); playerSave.saveAndVerify(player);
                market.transition(trade.id(), Phase.INVENTORY, Phase.ESCROWED);
                settleSale(trade.id(), () -> { if (session.getAsBoolean()) { Chat.success(player, "Sold <white>" + quote.units() + "</white> for " + Chat.marksTag(quote.marks()) + "."); refresh(session, reopen); } });
            } else {
                payment(trade, false).whenComplete((result, error) -> sync(() -> {
                    if (error != null || result == null) { hold(trade.id(), error); return; }
                    try {
                        if (!paid(result)) { market.cancel(trade.id()); processing.remove(trade.id()); if (session.getAsBoolean()) { Chat.error(player, "You need " + Chat.marksTag(quote.marks()) + " for this."); refresh(session, reopen); } return; }
                        market.transition(trade.id(), Phase.RESERVED, Phase.PAID);
                        ItemStack[] delivery = session.getAsBoolean() ? plan(player.getInventory().getStorageContents(), item, Side.BUY) : null;
                        if (delivery == null) { refund(trade.id(), () -> { if (sameAccount(player, account)) Chat.notice(player, "Refunded " + Chat.marksTag(quote.marks()) + ": your inventory was full. Make room and reopen the shop."); }); return; }
                        market.transition(trade.id(), Phase.PAID, Phase.INVENTORY);
                        player.getInventory().setStorageContents(delivery); playerSave.saveAndVerify(player);
                        market.complete(trade.id()); processing.remove(trade.id());
                        Chat.success(player, "Bought <white>" + quote.units() + "</white> for " + Chat.marksTag(quote.marks()) + "."); refresh(session, reopen);
                    } catch (RuntimeException failure) { hold(trade.id(), failure); }
                }));
            }
        } catch (RuntimeException error) { hold(trade.id(), error); }
    }
    void recover() {
        for (Trade trade : market.pending()) recover(trade.id());
    }
    private void recover(UUID id) {
        if (!processing.add(id)) return;
        try {
            Trade trade = market.require(id);
            switch (VendorRecovery.action(trade.quote().side(), trade.phase())) {
                case NONE -> processing.remove(id);
                case REVIEW_INVENTORY -> hold(id, null);
                case CANCEL_UNTOUCHED_SALE -> { limits.release(id); market.cancel(id); processing.remove(id); }
                case SETTLE_SALE -> settleSale(id, () -> { });
                case REFUND_PURCHASE -> refund(id, () -> { });
                case RETRY_PURCHASE -> {
                    // A lost response is retried with exactly the same economic operation ID.
                    payment(trade, false).whenComplete((result, error) -> sync(() -> {
                        try {
                            if (error != null || result == null) { hold(id, error); return; }
                            if (!paid(result)) { market.cancel(id); processing.remove(id); return; }
                            market.transition(id, Phase.RESERVED, Phase.PAID); refund(id, () -> { });
                        } catch (RuntimeException failure) { hold(id, failure); }
                    }));
                }
            }
        } catch (RuntimeException error) { hold(id, error); }
    }
    private CompletionStage<EconomyService.TransactionResult> payment(Trade trade, boolean refund) {
        var reason = new EconomyService.TransactionReason(refund ? "npc.market.refund" : trade.quote().side() == Side.BUY ? "npc.market.buy" : "npc.market.sell", trade.shop() + "/" + trade.product());
        String key = refund ? "npc-market:" + trade.id() + ":refund" : trade.paymentKey();
        return refund || trade.quote().side() == Side.SELL ? plugin.economy().credit(trade.account(), trade.quote().marks(), reason, key)
            : plugin.economy().debit(trade.account(), trade.quote().marks(), reason, key);
    }
    private void settleSale(UUID id, Runnable after) {
        Trade trade = market.require(id);
        payment(trade, false).whenComplete((result, error) -> sync(() -> {
            try {
                if (error != null || result == null || !paid(result)) { hold(id, error); return; }
                market.complete(id); processing.remove(id); after.run();
            } catch (RuntimeException failure) { hold(id, failure); }
        }));
    }
    private void refund(UUID id, Runnable after) {
        Trade trade = market.require(id);
        if (trade.phase() != Phase.REFUNDING) market.transition(id, trade.phase(), Phase.REFUNDING);
        payment(market.require(id), true).whenComplete((result, error) -> sync(() -> {
            try {
                if (error != null || result == null || !paid(result)) { hold(id, error); return; }
                market.cancel(id); processing.remove(id); after.run();
            } catch (RuntimeException failure) { hold(id, failure); }
        }));
    }
    private static boolean paid(EconomyService.TransactionResult result) { return result.status() == EconomyService.TransactionResult.Status.APPLIED || result.status() == EconomyService.TransactionResult.Status.DUPLICATE; }
    private void hold(UUID id, Throwable error) {
        processing.remove(id);
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "NPC trade needs recovery or inventory review: " + id, error);
        Trade trade = market.require(id);
        for (Player player : Bukkit.getOnlinePlayers()) if (plugin.sessions().account(player.getUniqueId()).filter(trade.account()::equals).isPresent())
            Chat.notice(player, "Your vendor trade is held safely for staff review. Give staff this reference: "
                + "<click:copy_to_clipboard:'" + id + "'><hover:show_text:'<gray>Click to copy</gray>'><white>" + id + "</white></hover></click>");
    }
    /** The old {@code /npc shopreview|shopretry|shopresolve} commands, kept as aliases of {@code /market review} and {@code /market resolve}. */
    boolean command(CommandSender sender, String[] args) {
        MarketController review = plugin.marketController();
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("shopreview")) return review.handle(sender, new String[]{"review"});
        if (action.equals("shopretry") && args.length == 2) return review.handle(sender, new String[]{"resolve", args[1], "retry"});
        if (action.equals("shopresolve") && args.length >= 3) {
            String[] forwarded = new String[args.length];
            forwarded[0] = "resolve";
            System.arraycopy(args, 1, forwarded, 1, args.length - 1);
            return review.handle(sender, forwarded);
        }
        if (!sender.hasPermission("nerdsmp.npc.admin")) { Chat.noPermission(sender); return true; }
        Chat.usage(sender, "/market resolve <id> <received|missing|removed|kept|retry> [reason]");
        Chat.info(sender, "Stuck vendor trades are listed in " + Chat.command("market review") + ".");
        return true;
    }
    /** What {@code /market review} sees of this journal. */
    private final class Review implements MarketController.VendorReview {
        @Override public List<MarketController.PendingTrade> pending() {
            return market.pending().stream().sorted(Comparator.comparing(Trade::id)).map(t -> new MarketController.PendingTrade(t.id(),
                (t.quote().side() == Side.BUY ? "Purchase" : "Sale") + " · " + Chat.escape(accountName(t.account())) + " · "
                    + Chat.escape(t.shop() + "/" + t.product()) + " · " + t.quote().units() + " units · " + Chat.marks(t.quote().marks())
                    + " · " + phaseName(t.phase()))).toList();
        }
        @Override public boolean contains(UUID id) { return market.pending().stream().anyMatch(t -> t.id().equals(id)); }
        @Override public void retry(UUID id) { market.require(id); recover(id); }
        @Override public void resolve(UUID id, String resolution, String reason, String staff) {
            Trade trade = market.require(id);
            if (trade.phase() != Phase.INVENTORY || !processing.add(id)) throw new IllegalArgumentException("That trade is not waiting for an inventory check. Try retry instead.");
            try {
                if (trade.quote().side() == Side.BUY && resolution.equals("received")) { market.complete(id); processing.remove(id); }
                else if (trade.quote().side() == Side.BUY && resolution.equals("missing")) refund(id, () -> { });
                else if (trade.quote().side() == Side.SELL && resolution.equals("removed")) { market.transition(id, Phase.INVENTORY, Phase.ESCROWED); settleSale(id, () -> { }); }
                else if (trade.quote().side() == Side.SELL && resolution.equals("kept")) { limits.release(id); market.cancel(id); processing.remove(id); }
                else throw new IllegalArgumentException(trade.quote().side() == Side.BUY
                    ? "This is a purchase: resolve it as received or missing after checking the saved inventory."
                    : "This is a sale: resolve it as removed or kept after checking the saved inventory.");
                plugin.getLogger().warning("Vendor review " + id + " by " + staff + ": " + resolution + " -- " + reason);
            } catch (RuntimeException error) { processing.remove(id); throw error; }
        }
    }
    private String accountName(AccountId account) {
        return plugin.playerData().read(account, yaml -> yaml.getString("identity.last-known-name", account.value().toString().substring(0, 8)));
    }
    static String phaseName(Phase phase) {
        return switch (phase) {
            case RESERVED -> "reserved";
            case PAID -> "paid, not delivered";
            case INVENTORY -> "inventory check needed";
            case ESCROWED -> "items taken, payout settling";
            case REFUNDING -> "refund settling";
            case COMPLETE -> "complete";
            case CANCELLED -> "cancelled";
        };
    }
    private void sync(Runnable action) { if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, action); }
    private void refresh(BooleanSupplier current, Runnable reopen) {
        sync(() -> { if (current.getAsBoolean()) reopen.run(); });
    }
    private boolean sameAccount(Player player, AccountId account) {
        return plugin.isEnabled() && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player
            && plugin.sessions().account(player.getUniqueId()).filter(account::equals).isPresent();
    }
    private boolean currentSession(Player player, AccountId account, BooleanSupplier current) {
        return Bukkit.isPrimaryThread() && sameAccount(player, account) && !player.isDead()
            && player.getGameMode() == GameMode.SURVIVAL && current.getAsBoolean();
    }
    /** Storage slots only: never consume worn gear, the cursor, or an offhand item. */
    static ItemStack[] plan(ItemStack[] contents, ItemStack product, Side side) {
        var planned = VendorInventoryPlan.plan(Arrays.asList(contents), product, product.getAmount(), side == Side.BUY, ITEMS);
        return planned == null ? null : planned.toArray(ItemStack[]::new);
    }
    private static final VendorInventoryPlan.Items<ItemStack> ITEMS = new VendorInventoryPlan.Items<>() {
        public boolean empty(ItemStack item) { return item == null || item.getType().isAir(); }
        public boolean same(ItemStack left, ItemStack right) { return left.isSimilar(right); }
        public int amount(ItemStack item) { return item.getAmount(); }
        public int maximum(ItemStack item) { return item.getMaxStackSize(); }
        public ItemStack copy(ItemStack item, int amount) { ItemStack copy = item.clone(); copy.setAmount(amount); return copy; }
    };
}
