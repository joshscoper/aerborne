package net.nerdsmp.paper.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.MarketService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.auction.Auction;
import net.nerdsmp.paper.auction.DurableAuctionService;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
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
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Read-only auction menus; item transfers use the market's saved-inventory delivery journal. */
public final class AuctionController implements Listener, AutoCloseable {
    private static final Set<Auction.Status> HELD = Set.of(Auction.Status.REVIEW, Auction.Status.BID_PENDING,
        Auction.Status.SETTLEMENT_PENDING, Auction.Status.CANCEL_PENDING, Auction.Status.WAITING_FOR_ITEM);
    private final NerdSmpPlugin plugin;
    private final DurableAuctionService auctions;
    private BukkitTask task;
    private volatile boolean closed;
    private final AtomicBoolean recoveryQueued = new AtomicBoolean();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(128), runnable -> { Thread thread = new Thread(runnable, "NerdSMP-Auctions"); thread.setDaemon(true); return thread; },
        new ThreadPoolExecutor.AbortPolicy());

    public AuctionController(NerdSmpPlugin plugin) { this.plugin = plugin; auctions = plugin.auctions(); }
    public void start() {
        runRecovery();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::runRecovery, 100L, 100L);
    }
    private void runRecovery() {
        if (closed || !recoveryQueued.compareAndSet(false, true)) return;
        CompletableFuture<List<DurableAuctionService.Result>> recovery = new CompletableFuture<>();
        try { worker.execute(() -> {
            try { auctions.tick().whenComplete((result, error) -> { if (error == null) recovery.complete(result); else recovery.completeExceptionally(error); }); }
            catch (RuntimeException error) { recovery.completeExceptionally(error); }
            finally { recoveryQueued.set(false); }
        }); } catch (java.util.concurrent.RejectedExecutionException full) { recoveryQueued.set(false); return; }
        recovery.whenComplete((results, error) -> sync(() -> {
            if (error != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Auction recovery failed; pending funds stay locked", error);
            else results.forEach(result -> {
                if (result.outcome() != DurableAuctionService.Outcome.COMPLETE && result.outcome() != DurableAuctionService.Outcome.CANCELLED) return;
                result.auction().ifPresent(auction -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        AccountId actor = account(player);
                        if (actor != null && (actor.equals(auction.seller()) || actor.equals(auction.highBidder())))
                            Chat.notice(player, "Auction <white>" + shortId(auction.id()) + "</white> has finished. See " + Chat.command("ah mine")
                                + " and collect from " + Chat.command("market inbox") + ".");
                    }
                });
            });
        }));
    }
    @Override public void close() {
        closed = true; if (task != null) task.cancel(); worker.shutdown();
        try { if (!worker.awaitTermination(5, TimeUnit.SECONDS)) plugin.getLogger().warning("Auction worker still finishing saved transactions; recovery will replay their original receipts."); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
    public void open(Player player) { browse(player, false, 0); }

    /** Auctions whose funds or items are locked. {@code /market review} lists them with the other queues. */
    public List<Auction> heldForReview() {
        return auctions.all().stream().filter(a -> HELD.contains(a.status())).sorted(Comparator.comparing(Auction::id)).toList();
    }

    public boolean handle(CommandSender sender, String[] args) {
        // Stuck transactions have one staff entry point; this keeps the old command working.
        if (args.length > 0 && args[0].equalsIgnoreCase("review")) return plugin.marketController().handle(sender, new String[]{"review"});
        if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
        AccountId actor = account(player);
        if (!current(player, actor)) { Chat.accountLoading(player); return true; }
        try {
            String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "list" -> open(player);
                case "mine" -> browse(player, true, 0);
                case "inbox" -> plugin.marketController().showInbox(player);
                case "help" -> help(player);
                case "sell" -> {
                    if (args.length == 1) { sellMenu(player, 100, null, 24, 1); break; }
                    if (args.length > 5) { Chat.usage(player, "/ah sell <starting bid> [buy now or 0] [hours] [amount]"); break; }
                    long starting = Long.parseLong(args[1]);
                    Long buy = args.length < 3 || Set.of("0", "none").contains(args[2].toLowerCase(Locale.ROOT)) ? null : Long.parseLong(args[2]);
                    int hours = args.length > 3 ? Integer.parseInt(args[3]) : 24;
                    int amount = args.length > 4 ? Integer.parseInt(args[4]) : 1;
                    plugin.marketController().auctionOffer(player, starting, buy, hours, amount);
                }
                case "inspect", "bid", "buy", "cancel", "retry" -> {
                    UUID id = args.length > 1 ? resolveId(actor, args[1]) : null;
                    Auction auction = id == null ? null : auctions.auction(id).orElse(null);
                    if (auction == null) {
                        Chat.error(player, "No single auction matches that ID. Short IDs must be unique.");
                        Chat.usage(player, action.equals("bid") ? "/ah bid <auction ID> <Marks>" : "/ah " + action + " <auction ID>");
                        break;
                    }
                    switch (action) {
                        case "inspect" -> detail(player, id);
                        case "bid" -> {
                            if (args.length != 3) { Chat.usage(player, "/ah bid <auction ID> <Marks>"); break; }
                            offer(player, auction, Long.parseLong(args[2]), false);
                        }
                        case "buy" -> offer(player, auction, auction.buyNowPrice() == null ? 0 : auction.buyNowPrice(), true);
                        case "cancel" -> cancel(player, auction);
                        case "retry" -> operate(player, actor, () -> auctions.retry(id, actor));
                        default -> { }
                    }
                }
                default -> help(player);
            }
        } catch (NumberFormatException invalid) { Chat.error(player, "Enter whole numbers for prices, hours and amounts."); }
        catch (IllegalArgumentException invalid) { Chat.error(player, Chat.escape(invalid.getMessage())); }
        catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Auction action failed", error);
            Chat.notice(player, "This auction could not be confirmed yet. Check " + Chat.command("ah mine") + " before retrying.");
        }
        return true;
    }

    private void offer(Player player, Auction auction, long amount, boolean buyNow) {
        AccountId actor = account(player);
        if (!canPay(player, actor)) { Chat.error(player, "Use Survival mode with an empty cursor before bidding."); return; }
        if (buyNow && auction.buyNowPrice() == null) { Chat.error(player, "This auction has no Buy Now price."); return; }
        if (auction.status() != Auction.Status.ACTIVE || actor.equals(auction.seller()) || amount <= 0 || amount > Auction.MAX_PRICE
            || amount < auctions.minimumBid(auction) && !Objects.equals(auction.buyNowPrice(), amount)) {
            Chat.error(player, "That offer is not available. Reopen the auction and check its minimum bid."); return;
        }
        long debit = actor.equals(auction.highBidder()) ? amount - auction.highBid() : amount;
        List<String> detail = List.of(
            "<gray>" + (buyNow ? "Price" : "Your bid") + "</gray> " + Chat.marksTag(amount),
            "<gray>Held now</gray> " + Chat.marksTag(debit),
            buyNow ? "<gray>The item goes to Deliveries once it settles.</gray>"
                : "<gray>Bids cannot be withdrawn. If you are outbid, your Marks come back.</gray>");
        confirm(player, buyNow ? "<gold><bold>Buy Now</bold></gold>" : "<gold><bold>Place Bid</bold></gold>", auction, detail, () -> {
            if (!canPay(player, actor)) return;
            operate(player, actor, () -> buyNow ? auctions.buyNow(auction.id(), actor, auction.revision())
                : auctions.bid(auction.id(), actor, amount, auction.revision()));
        });
    }
    private void cancel(Player player, Auction auction) {
        AccountId actor = account(player);
        if (!actor.equals(auction.seller()) || auction.highBidder() != null || auction.status() != Auction.Status.ACTIVE) {
            Chat.error(player, "You can only cancel your own auctions that have no bids."); return;
        }
        confirm(player, "<gold><bold>Cancel Auction</bold></gold>", auction,
            List.of("<gray>Your item goes back to Deliveries.</gray>", "<gray>The listing fee is not refunded.</gray>"),
            () -> operate(player, actor, () -> auctions.cancel(auction.id(), actor, auction.revision())));
    }
    private void operate(Player player, AccountId actor, Supplier<CompletionStage<DurableAuctionService.Result>> action) {
        if (!current(player, actor)) return;
        Inventory origin = player.getOpenInventory().getTopInventory();
        CompletableFuture<DurableAuctionService.Result> stage = new CompletableFuture<>();
        try { worker.execute(() -> {
            try { action.get().whenComplete((value, error) -> { if (error == null) stage.complete(value); else stage.completeExceptionally(error); }); }
            catch (RuntimeException error) { stage.completeExceptionally(error); }
        }); } catch (java.util.concurrent.RejectedExecutionException full) { Chat.error(player, "The auction house is busy. Nothing was submitted; try again shortly."); return; }
        stage.whenComplete((result, error) -> sync(() -> {
            if (error != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Auction operation failed for " + actor, error);
            if (!current(player, actor)) return;
            if (error != null) Chat.notice(player, "Confirmation is pending. Check " + Chat.command("ah mine") + " before retrying.");
            else { MarketController.tone(result.outcome()).send(player, Chat.escape(result.message())); if (player.getOpenInventory().getTopInventory() == origin)
                result.auction().ifPresent(auction -> detail(player, auction.id())); }
        }));
    }

    private void browse(Player player, boolean mine, int requestedPage) {
        AccountId actor = account(player); if (!current(player, actor)) return;
        List<Auction> entries = mine ? auctions.forAccount(actor) : auctions.all().stream().filter(a -> a.status() == Auction.Status.ACTIVE || a.status() == Auction.Status.BID_PENDING).toList();
        int pages = MarketController.pages(entries.size());
        int index = Math.max(0, Math.min(requestedPage, pages - 1));
        Page page = page(player, mine ? "<gold><bold>My Auctions</bold></gold>" : "<gold><bold>Auction House</bold></gold>");
        for (int n = 0; n < 21 && index * 21 + n < entries.size(); n++) {
            Auction auction = entries.get(index * 21 + n);
            var escrow = plugin.market().entry(auction.id()).orElse(null); if (escrow == null) continue;
            ItemStack item = MarketItems.decode(escrow.listing().item());
            append(item, MenuKit.withHint(List.of("", "<gray>Seller</gray> <white>" + Chat.escape(name(auction.seller())) + "</white>", price(auction), remaining(auction),
                statusLine(auction.status()), "<gray>Auction</gray> <white>" + shortId(auction.id()) + "</white>"), "inspect or bid"));
            button(page, 10 + n / 7 * 9 + n % 7, item, () -> detail(player, auction.id()));
        }
        button(page, 2, tile(Material.CLOCK, "<gold>Live Auctions</gold>", List.of(), "browse"), () -> browse(player, false, 0));
        button(page, 3, tile(Material.BOOK, "<gold>My Auctions</gold>", List.of("<gray>Your sales and bids.</gray>"), "open"), () -> browse(player, true, 0));
        button(page, 4, tile(Material.ENDER_CHEST, "<gold>Deliveries</gold>", List.of("<gray>Wins and returned items.</gray>"), "collect"), () -> plugin.marketController().showInbox(player));
        button(page, 5, tile(Material.GOLD_INGOT, "<gold>Auction Held Item</gold>", List.of(), "set up an auction"), () -> sellMenu(player, 100, null, 24, 1));
        button(page, 6, tile(Material.CHEST, "<gold>Player Stores</gold>", List.of(), "browse stores"), () -> plugin.marketController().handleStore(player, new String[0]));
        if (index > 0) button(page, MenuKit.previousSlot(54), MenuKit.previous(index, pages), () -> browse(player, mine, index - 1));
        if (index + 1 < pages) button(page, MenuKit.nextSlot(54), MenuKit.next(index, pages), () -> browse(player, mine, index + 1));
        if (entries.isEmpty()) page.inventory.setItem(22, MenuKit.item(Material.GLASS_BOTTLE, "<gray>No auctions here</gray>",
            List.of("<gray>Hold an item and choose Auction Held Item.</gray>")));
        button(page, 46, tile(Material.WRITABLE_BOOK, "<gold>Auction Help</gold>", List.of(), "show the auction commands"), () -> { player.closeInventory(); help(player); });
        button(page, MenuKit.backSlot(54), MenuKit.back("World & Trade"), () -> plugin.menus().open(player, MenuManager.View.WORLD));
        button(page, MenuKit.closeSlot(54), MenuKit.close(), player::closeInventory); show(player, page);
    }
    public void detail(Player player, UUID id) {
        AccountId actor = account(player); Auction auction = auctions.auction(id).orElse(null);
        var escrow = plugin.market().entry(id).orElse(null);
        if (!current(player, actor) || auction == null || escrow == null || !visible(actor, auction)) { Chat.error(player, "That auction is not available."); return; }
        Page page = page(player, "<gold><bold>Auction " + shortId(id) + "</bold></gold>");
        page.inventory.setItem(13, summary(auction));
        if (auction.status() == Auction.Status.ACTIVE && !actor.equals(auction.seller())) {
            long minimum = auctions.minimumBid(auction);
            if (auction.buyNowPrice() == null || minimum < auction.buyNowPrice()) button(page, 20, tile(Material.EMERALD, "<green>Bid " + Chat.marks(minimum) + "</green>",
                List.of("<gray>Bid more with</gray> <white>" + Chat.escape("/ah bid " + shortId(id) + " <Marks>") + "</white>",
                    "<gray>Your bid is held until you are outbid or it settles.</gray>"), "bid"), () -> offer(player, auction, minimum, false));
            if (auction.buyNowPrice() != null) button(page, 24, tile(Material.DIAMOND, "<green>Buy Now</green>",
                List.of("<gray>Price</gray> " + Chat.marksTag(auction.buyNowPrice())), "buy now"), () -> offer(player, auction, auction.buyNowPrice(), true));
        }
        if (actor.equals(auction.seller()) && auction.status() == Auction.Status.ACTIVE && auction.highBidder() == null)
            button(page, 31, tile(Material.RED_DYE, "<red>Cancel Auction</red>", List.of("<gray>Your item goes back to Deliveries.</gray>"), "cancel"), () -> cancel(player, auction));
        if (actor.equals(escrow.recipient()) && escrow.listing().status() == MarketService.Status.READY)
            button(page, 22, tile(Material.ENDER_CHEST, "<green>Collect Item</green>", List.of("<gray>Make room for the whole stack first.</gray>"), "collect"), () -> plugin.marketController().collectDelivery(player, id));
        if (Set.of(Auction.Status.WAITING_FOR_ITEM, Auction.Status.BID_PENDING, Auction.Status.SETTLEMENT_PENDING, Auction.Status.CANCEL_PENDING).contains(auction.status()) && participant(actor, auction))
            button(page, 31, tile(Material.COMPARATOR, "<yellow>Retry Confirmation</yellow>", List.of("<gray>Uses the original receipts, so nothing is paid twice.</gray>"), "retry"), () -> operate(player, actor, () -> auctions.retry(id, actor)));
        if (auction.status() == Auction.Status.REVIEW) page.inventory.setItem(31, MenuKit.item(Material.COMPARATOR, "<yellow>Held for Staff Review</yellow>",
            List.of("<gray>Marks and items stay locked until staff check them.</gray>", "<gray>Reference</gray> <white>" + id + "</white>")));
        button(page, MenuKit.backSlot(54), MenuKit.back("Auction House"), () -> browse(player, false, 0));
        button(page, MenuKit.closeSlot(54), MenuKit.close(), player::closeInventory); show(player, page);
    }
    private ItemStack summary(Auction auction) {
        var escrow = plugin.market().entry(auction.id()).orElse(null);
        ItemStack item = escrow == null ? MenuKit.item(Material.PAPER, "<gold>Auction " + shortId(auction.id()) + "</gold>", List.of())
            : MarketItems.decode(escrow.listing().item());
        append(item, List.of("", "<gray>Seller</gray> <white>" + Chat.escape(name(auction.seller())) + "</white>", price(auction), remaining(auction), statusLine(auction.status()),
            "<gray>High bidder</gray> <white>" + (auction.highBidder() == null ? "None" : Chat.escape(name(auction.highBidder()))) + "</white>",
            "<gray>Sales tax</gray> <white>" + auction.taxPercent() + "%</white> <gray>(paid by the seller)</gray>"));
        return item;
    }

    private void sellMenu(Player player, long starting, Long buyNow, int hours, int amount) {
        if (!current(player, account(player))) return;
        ItemStack held = player.getInventory().getItemInMainHand(); if (held.getType().isAir()) { Chat.error(player, "Hold the item you want to auction first."); return; }
        int count = Math.max(1, Math.min(amount, held.getAmount())); Page page = page(player, "<gold><bold>Create Auction</bold></gold>");
        ItemStack preview = held.clone(); preview.setAmount(count); page.inventory.setItem(13, preview);
        String bid = "<gray>Starting bid</gray> " + Chat.marksTag(starting);
        String buy = "<gray>Buy Now</gray> " + (buyNow == null ? "<white>None</white>" : Chat.marksTag(buyNow));
        button(page, 19, tile(Material.RED_DYE, "<yellow>Lower Starting Bid</yellow>", List.of(bid), "halve it"), () -> sellMenu(player, Math.max(1, starting / 2), buyNow, hours, count));
        button(page, 21, tile(Material.LIME_DYE, "<yellow>Raise Starting Bid</yellow>", List.of(bid), "double it"), () -> {
            long next = Math.min(Auction.MAX_PRICE, starting * 2); sellMenu(player, next, buyNow == null ? null : Math.max(buyNow, next), hours, count);
        });
        button(page, 23, tile(Material.DIAMOND, buyNow == null ? "<yellow>Enable Buy Now</yellow>" : "<yellow>Disable Buy Now</yellow>",
            buyNow == null ? List.of("<gray>Sets it to twice the starting bid.</gray>") : List.of(buy), buyNow == null ? "enable" : "disable"),
            () -> sellMenu(player, starting, buyNow == null ? Math.min(Auction.MAX_PRICE, starting * 2) : null, hours, count));
        button(page, 25, tile(Material.CLOCK, "<yellow>Duration</yellow>", List.of("<gray>Lasts</gray> <white>" + hours + " " + (hours == 1 ? "hour" : "hours") + "</white>",
            "<gray>1, 6, 12, 24, 48 or 72 hours</gray>"), "change the duration"), () -> {
            List<Integer> choices = List.of(1, 6, 12, 24, 48, 72); sellMenu(player, starting, buyNow, choices.get((choices.indexOf(hours) + 1) % choices.size()), count);
        });
        button(page, 29, tile(Material.ARROW, "<yellow>One Item</yellow>", List.of(), "auction one item"), () -> sellMenu(player, starting, buyNow, hours, 1));
        button(page, 33, tile(Material.CHEST, "<yellow>Whole Stack</yellow>", List.of("<gray>Items</gray> <white>" + held.getAmount() + "</white>"), "auction the whole stack"),
            () -> sellMenu(player, starting, buyNow, hours, held.getAmount()));
        button(page, 31, tile(Material.GOLD_INGOT, "<green>Review Auction</green>", List.of(bid, buy, "<gray>Items</gray> <white>" + count + "</white>",
            "<gray>Exact terms:</gray> <white>" + Chat.escape("/ah sell <bid> [buy now or 0] [hours] [amount]") + "</white>"), "review and confirm"),
            () -> plugin.marketController().auctionOffer(player, starting, buyNow, hours, count));
        button(page, MenuKit.backSlot(54), MenuKit.back("Auction House"), () -> open(player));
        button(page, MenuKit.closeSlot(54), MenuKit.close(), player::closeInventory); show(player, page);
    }
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) return List.of("list", "mine", "sell", "inbox", "bid", "buy", "inspect", "cancel", "retry", "help");
        if (args.length == 2 && Set.of("bid", "buy", "inspect", "cancel", "retry").contains(args[0].toLowerCase(Locale.ROOT)) && sender instanceof Player player) {
            AccountId actor = account(player); if (actor == null) return List.of();
            return auctions.all().stream().filter(a -> visible(actor, a)).map(a -> a.id().toString()).toList();
        }
        if (args[0].equalsIgnoreCase("sell") && args.length == 3) return List.of("0", "none");
        if (args[0].equalsIgnoreCase("sell") && args.length == 4) return List.of("1", "6", "12", "24", "48", "72");
        if (args[0].equalsIgnoreCase("bid") && args.length == 3 && sender instanceof Player player) {
            UUID id = resolveId(account(player), args[1]);
            return id == null ? List.of() : auctions.auction(id).map(a -> List.of(Long.toString(auctions.minimumBid(a)))).orElse(List.of());
        }
        return List.of();
    }
    private UUID resolveId(AccountId actor, String raw) {
        if (actor == null || raw.length() < 4) return null;
        List<UUID> matches = auctions.all().stream().filter(a -> visible(actor, a)).map(Auction::id).filter(id -> id.toString().startsWith(raw.toLowerCase(Locale.ROOT))).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }
    private boolean visible(AccountId actor, Auction auction) { return actor != null && (auction.status() == Auction.Status.ACTIVE || auction.status() == Auction.Status.BID_PENDING || participant(actor, auction)); }
    private boolean participant(AccountId actor, Auction a) { return actor.equals(a.seller()) || actor.equals(a.highBidder()) || a.pending() != null && (actor.equals(a.pending().bidder()) || actor.equals(a.pending().previousBidder())); }
    private String price(Auction a) { return a.highBidder() == null ? "<gray>Starting bid</gray> " + Chat.marksTag(a.startingBid()) : "<gray>High bid</gray> " + Chat.marksTag(a.highBid()); }
    private String remaining(Auction a) {
        long seconds = Math.max(0, Duration.between(Instant.now(), a.endsAt()).getSeconds());
        if (seconds == 0) return "<gray>Auction ended</gray>";
        return "<gray>Time left</gray> <white>" + (seconds < 60 ? "under a minute" : Chat.duration(Duration.ofMinutes(seconds / 60))) + "</white>";
    }
    /** A player-facing status, so no enum name reaches lore. */
    static String statusName(Auction.Status status) {
        return switch (status) {
            case WAITING_FOR_ITEM -> "Waiting for the item";
            case ACTIVE -> "Open for bids";
            case BID_PENDING -> "Bid settling";
            case SETTLEMENT_PENDING -> "Sale settling";
            case CANCEL_PENDING -> "Cancelling";
            case COMPLETE -> "Finished";
            case CANCELLED -> "Cancelled";
            case REVIEW -> "Held for staff review";
        };
    }
    private static String statusLine(Auction.Status status) {
        String colour = switch (status) {
            case ACTIVE, COMPLETE -> "green";
            case CANCELLED -> "gray";
            default -> "yellow";
        };
        return "<gray>Status</gray> <" + colour + ">" + statusName(status) + "</" + colour + ">";
    }
    private String name(AccountId id) { return plugin.playerData().read(id, yaml -> yaml.getString("identity.last-known-name", shortId(id.value()))); }
    private String shortId(UUID id) { return id.toString().substring(0, 8); }
    private void help(Player player) {
        Chat.send(player, "<gold><bold>Auction House</bold></gold>");
        Chat.info(player, Chat.command("ah sell") + " opens the auction editor. Exact terms: " + Chat.syntax("/ah sell <bid> [buy now or 0] [hours] [amount]") + ".");
        Chat.info(player, Chat.syntax("/ah bid <id> <Marks>") + " places a bid. " + Chat.syntax("/ah buy <id>") + " uses Buy Now. Auctions last 1-72 hours.");
        Chat.info(player, "Your Marks are held while you lead and come back if you are outbid. Bids cannot be withdrawn.");
        Chat.info(player, Chat.command("ah mine") + " tracks your sales and bids. " + Chat.command("ah inbox") + " collects wins and returns from anywhere.");
        Chat.info(player, "You can only cancel an auction with no bids. The listing fee is not refunded.");
    }
    /** Shared confirm screen; Cancel returns to the auction. */
    private void confirm(Player player, String title, Auction auction, List<String> detail, Runnable action) {
        MenuKit.openConfirm(plugin, player, title, summary(auction), detail,
            confirmed -> guarded(player, action), cancelled -> guarded(player, () -> detail(player, auction.id())));
    }
    private void guarded(Player player, Runnable action) {
        if (!current(player, account(player))) return;
        try { action.run(); }
        catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Auction menu action failed", error);
            Chat.notice(player, "This action could not be confirmed yet. Check " + Chat.command("ah mine") + ".");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Page page)) return;
        boolean denied = event.isCancelled(); event.setCancelled(true);
        if (denied || closed || !(event.getWhoClicked() instanceof Player player) || !page.viewer.equals(player.getUniqueId()) || !current(player, page.account)
            || page.busy || event.getRawSlot() < 0 || event.getRawSlot() >= 54 || event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        Runnable action = page.actions.get(event.getRawSlot()); if (action == null) return; page.busy = true;
        MenuKit.clickSound(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!current(player, page.account) || player.getOpenInventory().getTopInventory() != page.inventory) return;
            try { guarded(player, action); }
            finally { page.busy = false; }
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder(false) instanceof Page) event.setCancelled(true); }
    private Page page(Player player, String title) {
        Page page = new Page(player.getUniqueId(), Objects.requireNonNull(account(player))); page.inventory = Bukkit.createInventory(page, 54, Chat.render(title));
        MenuKit.frame(page.inventory); return page;
    }
    private static ItemStack tile(Material material, String name, List<String> lore, String action) {
        return MenuKit.item(material, name, MenuKit.withHint(lore, action));
    }
    private static void append(ItemStack item, List<String> lines) {
        var meta = item.getItemMeta(); List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        for (String line : lines) lore.add(Chat.render(line).decoration(TextDecoration.ITALIC, false)); meta.lore(lore); meta.addItemFlags(ItemFlag.values()); item.setItemMeta(meta);
    }
    private void button(Page page, int slot, ItemStack item, Runnable action) { page.inventory.setItem(slot, item); page.actions.put(slot, action); }
    private void show(Player player, Page page) { if (current(player, page.account)) player.openInventory(page.inventory); }
    private AccountId account(Player player) { return plugin.sessions().account(player.getUniqueId()).orElse(null); }
    private boolean current(Player player, AccountId account) { return !closed && plugin.isEnabled() && account != null && player.isOnline() && !player.isDead() && Bukkit.getPlayer(player.getUniqueId()) == player && account.equals(account(player)); }
    private boolean canPay(Player player, AccountId account) { return current(player, account) && player.getGameMode() == GameMode.SURVIVAL && player.getItemOnCursor().getType().isAir() && !plugin.trade().hasPending(account); }
    private void sync(Runnable action) { if (closed || !plugin.isEnabled()) return; if (Bukkit.isPrimaryThread()) action.run(); else Bukkit.getScheduler().runTask(plugin, action); }
    private static final class Page implements InventoryHolder {
        private final UUID viewer; private final AccountId account; private final Map<Integer, Runnable> actions = new HashMap<>(); private Inventory inventory; private boolean busy;
        private Page(UUID viewer, AccountId account) { this.viewer = viewer; this.account = account; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
