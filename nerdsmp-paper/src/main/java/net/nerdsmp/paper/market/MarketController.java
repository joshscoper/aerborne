package net.nerdsmp.paper.market;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.MarketService;
import net.nerdsmp.api.service.MarketService.*;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.auction.Auction;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.presentation.Chat;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Inventory mutations live here, on the main thread; the market owns durable state and payments. */
public final class MarketController implements Listener {
    /** The one staff entry point for stuck transactions: market listings, auctions and NPC vendor trades. */
    static final String REVIEW_PERMISSION = "nerdsmp.market.review";
    static final String VENDOR_PERMISSION = "nerdsmp.npc.admin";
    /** Staff resolution reasons: the same rule as {@code /staff} and the Staff Console ({@code StaffManager.validReason}). */
    public static final int REVIEW_REASON_MIN = 3;
    public static final int REVIEW_REASON_MAX = 180;
    static final List<String> VENDOR_RESOLUTIONS = List.of("received", "missing", "removed", "kept", "retry");
    private static final int PER_PAGE = 21;
    private static final String HUB = "World & Trade";

    private final NerdSmpPlugin plugin;
    private final DurableMarketService market;
    private final MarketPlayerSave playerSave;
    private final PlayerStoreProfiles profiles;
    private final Map<UUID, String> searchCache = new LinkedHashMap<>(128, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, String> entry) { return size() > 1024; }
    };
    private final Set<AccountId> inventoryOperations = new HashSet<>();
    private VendorReview vendorReview;
    private boolean closed;

    /** NPC vendor trades held for review. The NPC system registers its journal so {@code /market review} covers it too. */
    public interface VendorReview {
        List<PendingTrade> pending();
        boolean contains(UUID id);
        /** Re-runs recovery. Trades stuck at the inventory boundary stay held. */
        void retry(UUID id);
        /** Throws {@link IllegalArgumentException} with a sentence for staff when the resolution doesn't apply. */
        void resolve(UUID id, String resolution, String reason, String staff);
    }
    /** {@code summary} is MiniMessage markup. */
    public record PendingTrade(UUID id, String summary) { }

    public MarketController(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        this.market = plugin.market();
        this.playerSave = new MarketPlayerSave(plugin);
        this.profiles = new PlayerStoreProfiles(plugin.playerData(), plugin.getConfig().getString("season.id", "season-1"));
        // Fail before enabling trading if a stored snapshot cannot be read by this Paper version.
        market.allEntries().forEach(entry -> MarketItems.decode(entry.listing().item()));
    }

    public void registerVendorReview(VendorReview review) { vendorReview = review; }

    public void start() {
        market.recover().whenComplete((results, error) -> {
            if (error != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Market recovery failed; pending transactions remain locked", error);
            else {
                long pending = results.stream().filter(r -> r.outcome() == Outcome.PENDING || r.outcome() == Outcome.SAVE_FAILED).count();
                long review = market.allEntries().stream().filter(e -> e.listing().status() == Status.REVIEW).count();
                plugin.getLogger().info("Durable market loaded " + market.allEntries().size() + " listings; "
                    + pending + " pending payment recoveries, " + review + " inventory reviews.");
            }
        });
    }

    public void close() { closed = true; }

    public void onSessionReady(Player player) {
        AccountId actor = account(player);
        if (actor == null) return;
        long ready = market.entriesFor(actor).stream().filter(e -> e.listing().status() == Status.READY && actor.equals(e.recipient())).count();
        long held = market.entriesFor(actor).stream().filter(e -> Set.of(Status.REVIEW, Status.CHECKOUT, Status.FEE_PENDING).contains(e.listing().status())).count();
        if (ready > 0) Chat.notice(player, "You have <white>" + ready + "</white> market " + plural(ready, "delivery", "deliveries")
            + " waiting. Collect with " + Chat.command("market inbox") + ".");
        if (held > 0) Chat.notice(player, "You have <white>" + held + "</white> pending market " + plural(held, "transaction", "transactions")
            + ". See their status in " + Chat.command("market mine") + ".");
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (closed) { Chat.error(sender, "The market is shutting down. Try again after the restart."); return true; }
        try {
            if (args.length > 0 && args[0].equalsIgnoreCase("resolve")) return resolve(sender, args);
            if (args.length > 0 && args[0].equalsIgnoreCase("review")) return review(sender);
            if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
            AccountId actor = account(player);
            if (actor == null) { Chat.accountLoading(player); return true; }
            String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "list" -> open(player);
                case "stores" -> storeDirectory(player, "", 0);
                case "mine" -> browse(player, "mine", 0);
                case "inbox" -> browse(player, "inbox", 0);
                case "stall" -> register(player, actor);
                case "sell" -> { if (args.length == 1) sellMenu(player, 100, 1); else offer(player, actor, args); }
                case "help" -> help(player);
                case "reserve", "buy", "collect", "cancel", "retry", "inspect" -> {
                    UUID id = args.length == 2 ? resolveId(player, actor, args[1]) : null;
                    if (id == null) {
                        Chat.error(player, "No single listing matches that ID. Short IDs must be unique.");
                        Chat.usage(player, "/market " + action + " <listing ID>");
                        break;
                    }
                    switch (action) {
                        case "inspect" -> detail(player, id);
                        case "reserve" -> respond(player, actor, () -> market.reserve(id, actor, Instant.now().plusSeconds(300)));
                        case "buy" -> purchase(player, actor, id);
                        case "collect" -> collect(player, actor, id);
                        case "cancel" -> confirm(player, "<gold><bold>Withdraw Listing</bold></gold>", listingSummary(id),
                            List.of("<gray>The item goes back to your Deliveries.</gray>", "<gray>The listing fee is not refunded.</gray>"),
                            () -> respond(player, actor, () -> market.cancel(id, actor)), () -> detail(player, id));
                        case "retry" -> respond(player, actor, () -> market.retry(id, actor));
                        default -> { }
                    }
                }
                default -> help(player);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Market command failed", exception);
            Chat.error(sender, "The market could not complete that action. Your transaction reference is kept for staff review.");
        }
        return true;
    }

    public void open(Player player) { browse(player, "list", 0); }

    public boolean handleStore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
        AccountId actor = account(player);
        if (actor == null) { Chat.accountLoading(player); return true; }
        if (!current(player, actor)) return true;
        try {
            String action = args.length == 0 ? "directory" : args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "directory", "list" -> storeDirectory(player, args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "", 0);
                case "mine", "manage" -> storePage(player, actor, 0);
                case "sell" -> { if (args.length == 1) sellMenu(player, 100, 1); else offer(player, actor, args); }
                case "inbox" -> browse(player, "inbox", 0);
                case "search" -> {
                    String query = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
                    browseFiltered(player, "list", null, query, 0);
                }
                case "name", "description" -> {
                    if (args.length < 2) { Chat.usage(player, "/store " + action + " <text>"); break; }
                    var before = profiles.profile(actor); String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
                    profiles.update(actor, before, new PlayerStoreProfiles.Profile(action.equals("name") ? text : before.name(),
                        action.equals("description") ? text : before.description()));
                    Chat.success(player, action.equals("name") ? "Store name saved." : "Store description saved.");
                    storePage(player, actor, 0);
                }
                case "buy", "collect", "cancel", "inspect", "retry" -> handle(player, args);
                case "help" -> help(player);
                default -> {
                    AccountId seller = seller(action);
                    if (seller == null) {
                        Chat.error(player, "No store belongs to <white>" + Chat.escape(args[0]) + "</white>.");
                        Chat.usage(player, "/store [mine|sell|search|inbox|name|description|<player>]");
                    }
                    else storePage(player, seller, 0);
                }
            }
        } catch (IllegalArgumentException invalid) { Chat.error(player, Chat.escape(invalid.getMessage())); }
        catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Player store action failed", error);
            Chat.error(player, "The store could not complete that action. Your items stay safe in Deliveries.");
        }
        return true;
    }

    public void collectDelivery(Player player, UUID id) { AccountId actor = account(player); if (actor != null) collect(player, actor, id); }
    public void showInbox(Player player) { browse(player, "inbox", 0); }
    public void auctionOffer(Player player, long startingPrice, Long buyNow, int hours, int amount) {
        AccountId actor = account(player);
        if (!current(player, actor)) return;
        if (startingPrice < 1 || startingPrice > 1_000_000_000_000L || buyNow != null && (buyNow < startingPrice || buyNow > 1_000_000_000_000L)
            || hours < 1 || hours > 72) throw new IllegalArgumentException("Use positive prices up to one trillion Marks and a duration of 1-72 hours.");
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!canTransfer(player, actor) || held.getType().isAir() || amount < 1 || amount > held.getAmount()
            || amount > held.getMaxStackSize() || !eligible(held)) throw new IllegalArgumentException("Hold an eligible item stack in Survival with an empty cursor.");
        Stall stall = ensureStall(player, actor);
        ItemStack offered = held.clone(); offered.setAmount(amount);
        long fee = Math.max(0, plugin.getConfig().getLong("economy.market-listing-fee", 10));
        AuctionTerms terms = new AuctionTerms(buyNow, hours);
        List<String> detail = new ArrayList<>(List.of(
            "<gray>Starting bid</gray> " + Chat.marksTag(startingPrice),
            "<gray>Buy Now</gray> " + (buyNow == null ? "<white>None</white>" : Chat.marksTag(buyNow)),
            "<gray>Duration</gray> <white>" + hours + " " + plural(hours, "hour", "hours") + "</white>",
            "<gray>Items</gray> <white>" + amount + "</white>",
            "<gray>Listing fee</gray> " + Chat.marksTag(fee)));
        confirm(player, "<gold><bold>Start Auction</bold></gold>", offered.clone(), detail,
            () -> deposit(player, actor, stall, offered, MarketItems.encode(offered), startingPrice, fee, terms),
            () -> plugin.auctionController().open(player));
    }

    private Stall ensureStall(Player player, AccountId actor) {
        return market.stallFor(actor).orElseGet(() -> {
            Location location = player.getLocation();
            return read(market.registerStall(actor, new StallLocation(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ())));
        });
    }
    private boolean canTransfer(Player player, AccountId actor) {
        return current(player, actor) && player.getGameMode() == GameMode.SURVIVAL
            && player.getItemOnCursor().getType().isAir() && !plugin.trade().hasPending(actor);
    }

    private void register(Player player, AccountId actor) {
        Location location = player.getLocation();
        ChunkKey chunk = new ChunkKey(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
        ClaimRole role = read(plugin.claims().roleAt(actor, chunk));
        if ((!role.allows(ClaimRole.BUILD) || !role.allows(ClaimRole.CONTAINER)) && !player.hasPermission("nerdsmp.claim.bypass")) {
            Chat.error(player, "Register your store on land where you have building and storage access."); return;
        }
        StallLocation proposed = new StallLocation(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (blocksStallMove(market.stallFor(actor).orElse(null), proposed, market.entriesFor(actor))) {
            Chat.error(player, "Collect or finish all listings before moving your store. Check " + Chat.command("market mine") + "."); return;
        }
        read(market.registerStall(actor, proposed));
        Chat.success(player, "Store anchor saved. Use " + Chat.command("store sell") + " to list your held item from anywhere.");
    }

    private void offer(Player player, AccountId actor, String[] args) {
        long price;
        int amount;
        try { price = args.length >= 2 ? Long.parseLong(args[1]) : 0; amount = args.length == 3 ? Integer.parseInt(args[2]) : 1; }
        catch (NumberFormatException error) {
            Chat.error(player, "Use a whole-number total price and item amount.");
            Chat.usage(player, "/store sell <total price> [amount]"); return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        var stall = Optional.of(ensureStall(player, actor));
        long fee = plugin.getConfig().getLong("economy.market-listing-fee", 10L);
        if (args.length < 2 || args.length > 3 || price <= 0 || price > 1_000_000_000_000L || fee < 0 || held.getType().isAir()
            || amount < 1 || amount > held.getAmount() || amount > held.getMaxStackSize() || stall.isEmpty()) {
            Chat.error(player, "Hold the item you want to sell, with a price from 1 Mark to one trillion Marks.");
            Chat.usage(player, "/store sell <total price> [amount]"); return;
        }
        if (!canTransfer(player, actor)) { Chat.error(player, "Use Survival mode with an empty cursor and no pending trade."); return; }
        if (!eligible(held)) { Chat.error(player, "That item cannot be listed."); return; }
        ItemStack offered = held.clone();
        offered.setAmount(amount);
        String encoded = MarketItems.encode(offered);
        confirm(player, "<gold><bold>List for Sale</bold></gold>", offered.clone(),
            List.of("<gray>Price</gray> " + Chat.marksTag(price), "<gray>Items</gray> <white>" + amount + "</white>",
                "<gray>Listing fee</gray> " + Chat.marksTag(fee)),
            () -> deposit(player, actor, stall.get(), offered, encoded, price, fee), () -> sellMenu(player, price, amount));
    }

    private boolean eligible(ItemStack item) {
        if (!item.hasItemMeta()) return true;
        var data = item.getItemMeta().getPersistentDataContainer();
        boolean nerdItem = data.has(new NamespacedKey(plugin, "item_id"), PersistentDataType.STRING);
        if (nerdItem && !plugin.items().isAuthentic(item)) return false;
        // Entitlement cosmetics are not items; also reject explicitly bound items if introduced.
        return data.getKeys().stream().noneMatch(key -> key.getNamespace().equals("nerdsmp")
            && Set.of("account_bound", "bound_owner", "soulbound", "paid_cosmetic").contains(key.getKey()));
    }

    private void deposit(Player player, AccountId actor, Stall stall, ItemStack offered, String encoded, long price, long fee) {
        deposit(player, actor, stall, offered, encoded, price, fee, null);
    }
    private void deposit(Player player, AccountId actor, Stall stall, ItemStack offered, String encoded, long price, long fee, AuctionTerms terms) {
        if (!canTransfer(player, actor) || !market.stall(stall.id()).filter(stall::equals).isPresent()) return;
        if (!inventoryOperations.add(actor)) { Chat.error(player, "Finish the current inventory transfer first."); return; }
        UUID id = null;
        boolean touchingInventory = false;
        try {
            int slot = player.getInventory().getHeldItemSlot();
            ItemStack held = player.getInventory().getItem(slot);
            if (held == null || !held.isSimilar(offered) || held.getAmount() < offered.getAmount()) {
                Chat.error(player, "Your held item changed. Nothing was listed or charged."); return;
            }
            Result<String> prepared = read(terms == null ? market.prepareListing(actor, stall.id(), encoded, price, fee)
                : market.prepareAuctionListing(actor, stall.id(), encoded, price, fee));
            if (prepared.outcome() != Outcome.PREPARED || prepared.entry().isEmpty()) { result(player, prepared); return; }
            id = prepared.entry().get().listing().id();
            if (terms != null) {
                var planned = read(plugin.auctions().prepare(id, actor, price, terms.buyNow(), terms.hours()));
                if (planned.outcome() != net.nerdsmp.paper.auction.DurableAuctionService.Outcome.PREPARED) {
                    read(market.abandonDeposit(id, actor)); tone(planned.outcome()).send(player, Chat.escape(planned.message())); return;
                }
            }
            ItemStack remainder = held.clone();
            remainder.setAmount(held.getAmount() - offered.getAmount());
            touchingInventory = true;
            player.getInventory().setItem(slot, remainder.getAmount() == 0 ? null : remainder);
            playerSave.saveAndVerify(player);
            UUID reference = id;
            var depositConfirmation = market.confirmDeposit(reference, actor).thenCompose(confirmation -> {
                if (terms != null && market.entry(reference).map(entry -> entry.listing().status() == Status.AUCTION).orElse(false))
                    return plugin.auctions().activate(reference, actor).thenApply(activation -> new Result<String>(
                        activation.outcome() == net.nerdsmp.paper.auction.DurableAuctionService.Outcome.ACTIVE ? Outcome.ACTIVE : Outcome.PENDING,
                        confirmation.entry(), activation.message()));
                return java.util.concurrent.CompletableFuture.completedFuture(confirmation);
            });
            respond(player, actor, () -> depositConfirmation.handle((confirmation, error) -> {
                if (market.entry(reference).map(e -> e.listing().status() == Status.DEPOSIT_PENDING).orElse(false)) {
                    return read(market.quarantineDeposit(reference, actor, "Verified inventory save; deposit acknowledgement failed"));
                }
                if (error != null) throw new java.util.concurrent.CompletionException(error);
                return confirmation;
            }));
        } catch (RuntimeException error) {
            // Once mutation began, never restore blindly: the saved inventory may already exclude the item.
            if (id != null) {
                try {
                    if (touchingInventory) read(market.quarantineDeposit(id, actor, "Inventory removal interrupted: " + error.getClass().getSimpleName()));
                    else read(market.abandonDeposit(id, actor));
                } catch (RuntimeException saveError) { error.addSuppressed(saveError); }
            }
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Market deposit requires review: " + id + " seller " + actor, error);
            Chat.notice(player, "This listing is held for staff review. Reference: " + reference(id) + ". Do not list the same item again.");
        } finally { inventoryOperations.remove(actor); }
    }

    private void purchase(Player player, AccountId actor, UUID id) {
        var entry = market.entry(id);
        if (entry.isEmpty()) { Chat.error(player, "That listing no longer exists."); return; }
        if (!canTransfer(player, actor)) { Chat.error(player, "Use Survival mode with an empty cursor before buying."); return; }
        int tax = plugin.getConfig().getInt("economy.market-sales-tax-percent", 5);
        if (tax < 0 || tax > 100) { Chat.error(player, "The market tax setting is invalid. Please tell staff."); return; }
        respond(player, actor, () -> market.checkout(id, actor, tax));
        // Collection is deliberately explicit. Payment can finish offline or after leaving the stall.
    }

    private void collect(Player player, AccountId actor, UUID id) {
        if (!current(player, actor) || !inventoryOperations.add(actor)) return;
        UUID token = null;
        try {
            var optional = market.entry(id);
            if (optional.isEmpty() || optional.get().listing().status() != Status.READY || !actor.equals(optional.get().recipient())) {
                Chat.error(player, "This listing has no delivery ready for you."); return;
            }
            Entry<String> entry = optional.get();
            if (!canTransfer(player, actor)) { Chat.error(player, "Use Survival mode with an empty cursor before collecting."); return; }
            ItemStack item = MarketItems.decode(entry.listing().item());
            ItemStack[] storage = player.getInventory().getStorageContents();
            List<MarketInventoryPlan.Stack<ItemStack>> original = new ArrayList<>();
            for (ItemStack stack : storage) original.add(stack == null || stack.getType().isAir() ? null
                : new MarketInventoryPlan.Stack<>(stack.clone(), stack.getAmount(), Math.max(stack.getAmount(), stack.getMaxStackSize())));
            var planned = MarketInventoryPlan.insert(original,
                new MarketInventoryPlan.Stack<>(item, item.getAmount(), item.getMaxStackSize()), ItemStack::isSimilar);
            if (planned.isEmpty()) { Chat.error(player, "Make room for the whole delivery first. It stays safe in the market until then."); return; }
            Result<String> beginning = read(market.beginDelivery(id, actor));
            if (beginning.outcome() != Outcome.DELIVERING || beginning.entry().isEmpty()) { result(player, beginning); return; }
            token = beginning.entry().get().deliveryId();
            ItemStack[] next = new ItemStack[storage.length];
            for (int i = 0; i < next.length; i++) {
                var stack = planned.get().get(i);
                if (stack != null) { next[i] = stack.item().clone(); next[i].setAmount(stack.amount()); }
            }
            player.getInventory().setStorageContents(next);
            playerSave.saveAndVerify(player);
            Result<String> finished = read(market.finishDelivery(id, actor, token));
            if (finished.outcome() == Outcome.SOLD || finished.outcome() == Outcome.CANCELLED) Chat.success(player, "Collected. Your inventory has been saved.");
            else {
                read(market.quarantineDelivery(id, actor, token, "Inventory saved but delivery acknowledgement failed"));
                Chat.notice(player, "Your delivery is held for staff to check. Do not collect it again. Reference: " + reference(id) + ".");
            }
        } catch (RuntimeException error) {
            if (token != null) {
                try { read(market.quarantineDelivery(id, actor, token, "Inventory transfer interrupted: " + error.getClass().getSimpleName())); }
                catch (RuntimeException saveError) { error.addSuppressed(saveError); }
            }
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Market delivery requires review: " + id + " recipient " + actor, error);
            Chat.notice(player, "Collection was interrupted. Staff will check your inventory before you retry. Reference: " + reference(id) + ".");
        } finally { inventoryOperations.remove(actor); }
    }

    /** {@code /market review}: every stuck transaction, from every queue the sender may resolve. */
    private boolean review(CommandSender sender) {
        boolean listings = sender.hasPermission(REVIEW_PERMISSION);
        boolean vendors = vendorReview != null && sender.hasPermission(VENDOR_PERMISSION);
        if (!listings && !vendors) { Chat.noPermission(sender); return true; }
        List<String> lines = new ArrayList<>();
        int total = 0;
        if (listings) {
            var held = market.allEntries().stream().filter(this::needsAttention).sorted(Comparator.comparing(e -> e.listing().id())).toList();
            total += held.size();
            lines.add(sectionLine("Market listings", held.size()));
            held.forEach(e -> lines.add(reviewLine(e.listing().id(), statusName(e.listing().status())
                + (e.reviewKind() == ReviewKind.NONE ? "" : " · " + Chat.humanize(e.reviewKind().name()) + " check")
                + (e.reviewReason() == null || e.reviewReason().isBlank() ? "" : " · " + Chat.escape(e.reviewReason())),
                e.reviewKind() != ReviewKind.NONE ? "market resolve " + e.listing().id() + " " : null, "market inspect " + e.listing().id())));
            List<Auction> auctions = plugin.auctionController().heldForReview();
            total += auctions.size();
            lines.add(sectionLine("Auctions", auctions.size()));
            auctions.forEach(a -> lines.add(reviewLine(a.id(), AuctionController.statusName(a.status())
                + " · seller " + Chat.escape(name(a.seller())) + " · high bid " + Chat.marks(a.highBid())
                + (a.highBidder() == null ? "" : " by " + Chat.escape(name(a.highBidder())))
                + (a.reviewReason() == null || a.reviewReason().isBlank() ? "" : " · " + Chat.escape(a.reviewReason())),
                null, "ah inspect " + a.id())));
        }
        if (vendors) {
            var trades = vendorReview.pending();
            total += trades.size();
            lines.add(sectionLine("Vendor trades", trades.size()));
            trades.forEach(t -> lines.add(reviewLine(t.id(), t.summary(), "market resolve " + t.id() + " ", null)));
        }
        Chat.send(sender, "<yellow><bold>Stuck Transactions</bold></yellow> <gray>· " + total + " waiting</gray>");
        lines.forEach(line -> Chat.send(sender, line));
        if (total == 0) return true;
        Chat.usage(sender, "/market resolve <id> <resolution> <reason>");
        if (listings) Chat.info(sender, "Listings: <white>deposit-kept</white>, <white>deposit-taken</white>, <white>delivery-received</white> or <white>delivery-missing</white>. Auction escrow appears as a listing.");
        if (vendors) Chat.info(sender, "Vendor trades: <white>received</white> or <white>missing</white> for a purchase, <white>removed</white> or <white>kept</white> for a sale, or <white>retry</white>.");
        Chat.info(sender, "Check the saved inventory first, and give a reason of " + REVIEW_REASON_MIN + "-" + REVIEW_REASON_MAX + " characters.");
        return true;
    }
    private static String sectionLine(String name, int count) {
        return "<gold>" + name + "</gold> <gray>(" + count + ")</gray>";
    }
    /** Clicking a resolvable line types its resolve command; other lines open the listing. Console sees the full ID. */
    private static String reviewLine(UUID id, String detail, String resolve, String inspect) {
        String click = resolve != null ? "<click:suggest_command:'/" + resolve + "'>" : inspect != null ? "<click:run_command:'/" + inspect + "'>" : "";
        String hover = "<hover:show_text:'<gray>" + id + (resolve != null ? "<newline>Click to resolve" : inspect != null ? "<newline>Click to inspect" : "") + "</gray>'>";
        return " " + click + hover + "<white>" + id + "</white></hover>" + (click.isEmpty() ? "" : "</click>") + " <gray>" + detail + "</gray>";
    }

    private boolean resolve(CommandSender sender, String[] args) {
        boolean listings = sender.hasPermission(REVIEW_PERMISSION);
        boolean vendors = vendorReview != null && sender.hasPermission(VENDOR_PERMISSION);
        if (!listings && !vendors) { Chat.noPermission(sender); return true; }
        String syntax = "/market resolve <id> <resolution> <reason>";
        if (args.length < 3) { Chat.usage(sender, syntax); return true; }
        UUID id;
        try { id = UUID.fromString(args[1]); }
        catch (IllegalArgumentException error) { Chat.error(sender, "Use the full transaction ID from " + Chat.command("market review") + "."); return true; }
        if (!sender.equals(Bukkit.getConsoleSender()) && !(sender instanceof Player)) { Chat.error(sender, "Resolve transactions as a staff player or from the server console."); return true; }
        String reason = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
        if (vendorReview != null && vendorReview.contains(id)) {
            if (!vendors) { Chat.noPermission(sender); return true; }
            String resolution = args[2].toLowerCase(Locale.ROOT);
            try {
                if (resolution.equals("retry")) {
                    vendorReview.retry(id);
                    Chat.notice(sender, "Recovery checked. A trade stuck at the inventory step still needs a resolution.");
                    return true;
                }
                if (!validReason(reason)) { Chat.error(sender, "Give a reason of " + REVIEW_REASON_MIN + "-" + REVIEW_REASON_MAX + " characters saying what you checked."); return true; }
                vendorReview.resolve(id, resolution, reason, sender.getName());
                Chat.success(sender, "Resolution recorded. Run " + Chat.command("market review") + " to check for any payment still settling.");
            } catch (IllegalArgumentException invalid) { Chat.error(sender, Chat.escape(invalid.getMessage())); }
            return true;
        }
        if (!listings) { Chat.noPermission(sender); return true; }
        Resolution resolution;
        try { resolution = Resolution.valueOf(args[2].toUpperCase(Locale.ROOT).replace('-', '_')); }
        catch (IllegalArgumentException error) {
            Chat.error(sender, "Resolve a listing with deposit-kept, deposit-taken, delivery-received or delivery-missing."); return true;
        }
        if (!validReason(reason)) { Chat.error(sender, "Give a reason of " + REVIEW_REASON_MIN + "-" + REVIEW_REASON_MAX + " characters saying what you checked."); return true; }
        market.resolve(id, resolution, sender instanceof Player p ? p.getUniqueId().toString() : "CONSOLE", reason)
            .whenComplete((result, error) -> sync(() -> {
                if (error != null) Chat.error(sender, "The resolution could not be saved. The transaction stays locked.");
                else {
                    tone(result.outcome()).send(sender, Chat.escape(result.message()));
                    plugin.getLogger().info("Market review " + id + ": " + resolution + " by " + sender.getName() + " -- " + reason);
                }
            }));
        return true;
    }
    static boolean validReason(String reason) {
        return reason != null && reason.length() >= REVIEW_REASON_MIN && reason.length() <= REVIEW_REASON_MAX
            && reason.chars().noneMatch(Character::isISOControl);
    }
    private AccountId seller(String input) {
        List<AccountId> matches = plugin.playerData().knownAccounts().stream()
            .filter(id -> name(id).equalsIgnoreCase(input) || id.toString().equalsIgnoreCase(input)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }
    public List<String> completeStore(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> choices = new ArrayList<>(List.of("directory", "mine", "sell", "search", "inbox", "name", "description", "buy", "collect", "cancel", "inspect", "retry", "help"));
            market.allEntries().stream().filter(e -> Set.of(Status.ACTIVE, Status.RESERVED).contains(e.listing().status()))
                .map(e -> name(e.listing().seller())).distinct().forEach(choices::add);
            return choices;
        }
        return tabComplete(sender, args);
    }
    private void storeDirectory(Player player, String query, int requestedPage) {
        if (!current(player, account(player))) return;
        String search = query.trim().toLowerCase(Locale.ROOT);
        Map<AccountId, List<Entry<String>>> stores = new HashMap<>();
        read(market.activeListings()).forEach(listing -> stores.computeIfAbsent(listing.seller(), ignored -> new ArrayList<>()).add(market.entry(listing.id()).orElseThrow()));
        List<AccountId> owners = stores.keySet().stream().filter(owner -> search.isEmpty()
            || (name(owner) + " " + profiles.profile(owner).name() + " " + profiles.profile(owner).description()).toLowerCase(Locale.ROOT).contains(search))
            .sorted(Comparator.comparing(owner -> profiles.profile(owner).name(), String.CASE_INSENSITIVE_ORDER)).toList();
        int pages = pages(owners.size());
        int index = Math.max(0, Math.min(requestedPage, pages - 1));
        Page page = page(player, "<gold><bold>Player Stores</bold></gold>");
        for (int n = 0; n < PER_PAGE && index * PER_PAGE + n < owners.size(); n++) {
            AccountId owner = owners.get(index * PER_PAGE + n); var profile = profiles.profile(owner);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Owner</gray> <white>" + Chat.escape(name(owner)) + "</white>");
            if (profile.description() != null && !profile.description().isBlank()) lore.add("<gray>" + Chat.escape(profile.description()) + "</gray>");
            int count = stores.get(owner).size();
            lore.add("<gray>Listings</gray> <white>" + count + "</white>");
            button(page, slot(n), tile(Material.CHEST, "<gold>" + Chat.escape(profile.name()) + "</gold>", lore, "browse this store"), () -> storePage(player, owner, 0));
        }
        navigation(player, page);
        if (owners.isEmpty()) page.inventory.setItem(22, MenuKit.item(Material.GLASS_BOTTLE, "<gray>No stores found</gray>",
            List.of("<gray>List a held item with</gray> <white>/store sell</white><gray>.</gray>")));
        paging(page, index, pages, next -> storeDirectory(player, query, next));
        button(page, 46, tile(Material.OAK_SIGN, "<gold>Find a Store</gold>", List.of("<gray>Search by name or description.</gray>"), "see the search commands"), () -> {
            player.closeInventory();
            Chat.info(player, "Search with " + Chat.syntax("/store directory <text>") + ", or open one store with " + Chat.syntax("/store <player>") + ".");
        });
        backToHub(player, page);
        show(player, page);
    }
    private void storePage(Player player, AccountId owner, int requestedPage) {
        browseFiltered(player, "store", owner, "", requestedPage);
    }
    private String searchable(Entry<String> entry) {
        return searchCache.computeIfAbsent(entry.listing().id(), ignored -> {
            ItemStack item = MarketItems.decode(entry.listing().item());
            String display = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()) : "";
            return (item.getType().name().replace('_', ' ') + " " + display + " " + name(entry.listing().seller())).toLowerCase(Locale.ROOT);
        });
    }
    private void sellMenu(Player player, long price, int amount) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) { Chat.error(player, "Hold the item you want to sell, then open " + Chat.command("store sell") + "."); return; }
        int count = Math.max(1, Math.min(amount, held.getAmount()));
        long fee = plugin.getConfig().getLong("economy.market-listing-fee", 10);
        Page page = page(player, "<gold><bold>Create Store Listing</bold></gold>");
        ItemStack preview = held.clone(); preview.setAmount(count); page.inventory.setItem(13, preview);
        String current = "<gray>Price</gray> " + Chat.marksTag(price);
        button(page, 20, tile(Material.RED_DYE, "<yellow>Lower Price</yellow>", List.of(current), "halve the price"), () -> sellMenu(player, Math.max(1, price / 2), count));
        button(page, 24, tile(Material.LIME_DYE, "<yellow>Raise Price</yellow>", List.of(current), "double the price"), () -> sellMenu(player, Math.min(1_000_000_000_000L, price * 2), count));
        button(page, 29, tile(Material.ARROW, "<yellow>One Item</yellow>", List.of(), "sell one item"), () -> sellMenu(player, price, 1));
        button(page, 33, tile(Material.CHEST, "<yellow>Whole Stack</yellow>", List.of("<gray>Items</gray> <white>" + held.getAmount() + "</white>"), "sell the whole stack"), () -> sellMenu(player, price, held.getAmount()));
        button(page, 31, tile(Material.EMERALD, "<green>List for Sale</green>", List.of(current, "<gray>Items</gray> <white>" + count + "</white>",
                "<gray>Listing fee</gray> " + Chat.marksTag(fee), "<gray>You confirm before anything moves.</gray>",
                "<gray>Exact price:</gray> <white>" + Chat.escape("/store sell <price> [amount]") + "</white>"), "review the listing"),
            () -> offer(player, account(player), new String[]{"sell", Long.toString(price), Integer.toString(count)}));
        button(page, MenuKit.backSlot(54), MenuKit.back("My Store"), () -> storePage(player, account(player), 0));
        button(page, MenuKit.closeSlot(54), MenuKit.close(), player::closeInventory); show(player, page);
    }
    private void navigation(Player player, Page page) {
        button(page, 2, tile(Material.EMERALD, "<gold>All Listings</gold>", List.of(), "browse every listing"), () -> browse(player, "list", 0));
        button(page, 3, tile(Material.CHEST, "<gold>Player Stores</gold>", List.of(), "browse stores"), () -> storeDirectory(player, "", 0));
        button(page, 4, tile(Material.ENDER_CHEST, "<gold>Deliveries</gold>", List.of("<gray>Purchases, auction wins and returned listings.</gray>"), "collect"), () -> browse(player, "inbox", 0));
        button(page, 5, tile(Material.BOOK, "<gold>My Store</gold>", List.of(), "manage your store"), () -> storePage(player, account(player), 0));
        button(page, 6, tile(Material.CLOCK, "<gold>Auction House</gold>", List.of(), "open"), () -> plugin.auctionController().open(player));
        button(page, MenuKit.closeSlot(54), MenuKit.close(), player::closeInventory);
    }
    /** Top-level market pages return to the hub page that opens them. */
    private void backToHub(Player player, Page page) {
        button(page, MenuKit.backSlot(54), MenuKit.back(HUB), () -> plugin.menus().open(player, MenuManager.View.WORLD));
    }
    private void paging(Page page, int index, int pages, java.util.function.IntConsumer open) {
        if (index > 0) button(page, MenuKit.previousSlot(54), MenuKit.previous(index, pages), () -> open.accept(index - 1));
        if (index + 1 < pages) button(page, MenuKit.nextSlot(54), MenuKit.next(index, pages), () -> open.accept(index + 1));
    }
    private void browse(Player player, String mode, int requestedPage) {
        browseFiltered(player, mode, null, "", requestedPage);
    }
    private void browseFiltered(Player player, String mode, AccountId owner, String query, int requestedPage) {
        AccountId actor = account(player);
        if (actor == null || closed) return;
        List<Entry<String>> entries = mode.equals("list") || mode.equals("store") && !actor.equals(owner) ? read(market.activeListings()).stream().map(l -> market.entry(l.id()).orElseThrow()).toList()
            : mode.equals("review") && player.hasPermission(REVIEW_PERMISSION) ? market.allEntries().stream().filter(this::needsAttention).toList()
            : market.entriesFor(actor).stream().filter(e -> !mode.equals("inbox") || actor.equals(e.recipient()) && e.listing().status() == Status.READY).toList();
        if (mode.equals("store")) entries = entries.stream().filter(e -> owner.equals(e.listing().seller()) && !market.isAuction(e.listing().id())).toList();
        String search = query.trim().toLowerCase(Locale.ROOT);
        if (!search.isEmpty()) entries = entries.stream().filter(e -> searchable(e).contains(search)).toList();
        if (!mode.equals("list")) entries = entries.stream().sorted(Comparator.comparing(e -> e.listing().id())).toList();
        int pages = pages(entries.size());
        int pageIndex = Math.max(0, Math.min(requestedPage, pages - 1));
        Page page = page(player, title(mode, owner, search));
        for (int n = 0; n < PER_PAGE && pageIndex * PER_PAGE + n < entries.size(); n++) {
            Entry<String> entry = entries.get(pageIndex * PER_PAGE + n);
            ItemStack item = MarketItems.decode(entry.listing().item());
            appendLore(item, MenuKit.withHint(List.of("", "<gray>Price</gray> " + Chat.marksTag(entry.listing().price()), statusLine(entry.listing().status()),
                "<gray>Seller</gray> <white>" + Chat.escape(name(entry.listing().seller())) + "</white>",
                "<gray>Listing</gray> <white>" + shortId(entry.listing().id()) + "</white>"), "inspect, buy or collect"));
            button(page, slot(n), item, () -> detail(player, entry.listing().id()));
        }
        navigation(player, page);
        paging(page, pageIndex, pages, next -> browseFiltered(player, mode, owner, query, next));
        if (entries.isEmpty()) page.inventory.setItem(22, MenuKit.item(Material.GLASS_BOTTLE, "<gray>Nothing here yet</gray>",
            List.of("<gray>See</gray> <white>/market help</white> <gray>to get started.</gray>")));
        button(page, 46, tile(Material.WRITABLE_BOOK, "<gold>Market Help</gold>", List.of(), "show the market commands"), () -> { player.closeInventory(); help(player); });
        if (mode.equals("store") && actor.equals(owner)) {
            button(page, 47, tile(Material.GOLD_INGOT, "<gold>Sell Held Item</gold>", List.of(), "create a listing"), () -> sellMenu(player, 100, 1));
            button(page, 50, tile(Material.OAK_SIGN, "<gold>Store Identity</gold>", List.of("<white>" + Chat.escape("/store name <name>") + "</white>",
                "<white>" + Chat.escape("/store description <text>") + "</white>"), "see how to rename your store"), () -> {
                player.closeInventory();
                Chat.info(player, Chat.syntax("/store name <name>") + " sets your store's name. " + Chat.syntax("/store description <text>") + " adds a short introduction.");
                Chat.info(player, "Auction listings, payments and delivery history are in " + Chat.command("market mine") + ".");
            });
        }
        if (mode.equals("store") && !actor.equals(owner)) button(page, MenuKit.backSlot(54), MenuKit.back("Player Stores"), () -> storeDirectory(player, "", 0));
        else backToHub(player, page);
        if (player.hasPermission(REVIEW_PERMISSION)) button(page, 52, tile(Material.COMPARATOR, "<yellow>Stuck Transactions</yellow>",
            List.of("<gray>Listings held for staff review.</gray>"), "review"), () -> browse(player, "review", 0));
        show(player, page);
    }
    private String title(String mode, AccountId owner, String search) {
        return switch (mode) {
            case "store" -> "<gold><bold>" + Chat.escape(profiles.profile(owner).name()) + "</bold></gold>";
            case "mine" -> "<gold><bold>My Listings</bold></gold>";
            case "inbox" -> "<gold><bold>Deliveries</bold></gold>";
            case "review" -> "<yellow><bold>Stuck Transactions</bold></yellow>";
            default -> search.isEmpty() ? "<gold><bold>Market</bold></gold>" : "<gold><bold>Market Search</bold></gold>";
        };
    }

    private void detail(Player player, UUID id) {
        AccountId actor = account(player);
        Entry<String> entry = market.entry(id).orElse(null);
        if (actor == null || entry == null || !visible(player, actor, entry)) { Chat.error(player, "That listing is not available."); return; }
        Listing<String> listing = entry.listing();
        if (market.isAuction(id) && listing.status() != Status.READY && listing.status() != Status.REVIEW && !needsAttention(entry)) {
            plugin.auctionController().detail(player, id); return;
        }
        Page page = page(player, "<gold><bold>Listing " + shortId(id) + "</bold></gold>");
        ItemStack item = MarketItems.decode(listing.item());
        appendLore(item, MenuKit.withHint(List.of("", "<gray>Price</gray> " + Chat.marksTag(listing.price()),
            "<gray>Seller</gray> <white>" + Chat.escape(name(listing.seller())) + "</white>", statusLine(listing.status()),
            "<gray>Buy and collect from anywhere.</gray>"), "show its reference"));
        button(page, 13, item, () -> Chat.info(player, "Listing reference " + reference(id) + "."));
        if ((listing.status() == Status.ACTIVE || listing.status() == Status.RESERVED) && !listing.seller().equals(actor)) {
            button(page, 20, tile(Material.CLOCK, "<yellow>Reserve</yellow>", List.of("<gray>Hold this listing for five minutes. No charge.</gray>"), "reserve"),
                () -> respond(player, actor, () -> market.reserve(id, actor, Instant.now().plusSeconds(300))));
            button(page, 24, tile(Material.EMERALD, "<green>Buy</green>", List.of("<gray>Cost</gray> " + Chat.marksTag(listing.price()),
                "<gray>Collect it from Deliveries once paid.</gray>"), "buy"),
                () -> confirm(player, "<gold><bold>Buy Listing</bold></gold>", listingSummary(id), List.of("<gray>Cost</gray> " + Chat.marksTag(listing.price())),
                    () -> purchase(player, actor, id), () -> detail(player, id)));
        }
        if (actor.equals(entry.recipient()) && listing.status() == Status.READY) button(page, 22,
            tile(Material.CHEST, "<green>Collect</green>", List.of("<gray>Make room for the full stack.</gray>"), "collect"), () -> collect(player, actor, id));
        if (listing.seller().equals(actor) && Set.of(Status.ACTIVE, Status.RESERVED).contains(listing.status())) button(page, 31,
            tile(Material.RED_DYE, "<red>Withdraw Listing</red>", List.of("<gray>The item goes back to Deliveries.</gray>", "<gray>The listing fee is kept.</gray>"), "withdraw"),
            () -> confirm(player, "<gold><bold>Withdraw Listing</bold></gold>", listingSummary(id),
                List.of("<gray>The item goes back to your Deliveries.</gray>", "<gray>The listing fee is not refunded.</gray>"),
                () -> respond(player, actor, () -> market.cancel(id, actor)), () -> detail(player, id)));
        if (needsAttention(entry)) {
            if (canRetryPayment(actor, entry)) {
                button(page, 31, tile(Material.COMPARATOR, "<yellow>Retry Payment</yellow>",
                    List.of("<gray>Uses the original receipt, so you are never charged twice.</gray>"), "ask for payment confirmation again"),
                    () -> respond(player, actor, () -> market.retry(id, actor)));
            } else {
                boolean paymentPending = listing.status() == Status.FEE_PENDING || listing.status() == Status.CHECKOUT;
                page.inventory.setItem(31, MenuKit.item(Material.COMPARATOR, "<yellow>Pending Transaction</yellow>", paymentPending
                    ? List.of("<gray>Only the buyer and seller can retry payment.</gray>", "<gray>Pending payments also retry when the server starts.</gray>")
                    : List.of("<gray>Waiting for staff to check the saved inventory.</gray>")));
            }
        }
        if (player.hasPermission(REVIEW_PERMISSION) && entry.reviewKind() != ReviewKind.NONE) {
            button(page, 33, tile(Material.PAPER, "<yellow>Review Evidence</yellow>", List.of("<gray>Shows the reference and how to resolve it.</gray>"), "show the evidence"),
                () -> {
                    if (!player.hasPermission(REVIEW_PERMISSION)) return;
                    player.closeInventory();
                    Chat.info(player, "Seller <white>" + Chat.escape(name(listing.seller())) + "</white> (" + listing.seller().value() + ") · recipient "
                        + (entry.recipient() == null ? "<white>none</white>" : "<white>" + Chat.escape(name(entry.recipient())) + "</white> (" + entry.recipient().value() + ")"));
                    Chat.info(player, "Review <white>" + Chat.humanize(entry.reviewKind().name()) + "</white> · " + Chat.escape(String.valueOf(entry.reviewReason())));
                    Chat.usage(player, "/market resolve " + id + " <resolution> <reason>");
                    Chat.info(player, "Check the saved inventory before choosing deposit-kept, deposit-taken, delivery-received or delivery-missing.");
                });
        }
        button(page, MenuKit.backSlot(54), MenuKit.back("Market"), () -> open(player));
        button(page, MenuKit.closeSlot(54), MenuKit.close(), player::closeInventory);
        show(player, page);
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        boolean staff = sender.hasPermission(REVIEW_PERMISSION) || vendorReview != null && sender.hasPermission(VENDOR_PERMISSION);
        if (args.length <= 1) return staff
            ? List.of("list", "stores", "stall", "sell", "mine", "inbox", "inspect", "reserve", "buy", "collect", "cancel", "retry", "help", "review", "resolve")
            : List.of("list", "stores", "stall", "sell", "mine", "inbox", "inspect", "reserve", "buy", "collect", "cancel", "retry", "help");
        if (args[0].equalsIgnoreCase("resolve")) {
            if (!staff) return List.of();
            boolean vendors = vendorReview != null && sender.hasPermission(VENDOR_PERMISSION);
            if (args.length == 2) {
                List<String> ids = new ArrayList<>();
                if (sender.hasPermission(REVIEW_PERMISSION)) market.allEntries().stream().filter(e -> e.reviewKind() != ReviewKind.NONE).map(e -> e.listing().id().toString()).forEach(ids::add);
                if (vendors) vendorReview.pending().forEach(t -> ids.add(t.id().toString()));
                return ids;
            }
            if (args.length == 3) {
                try { if (vendors && vendorReview.contains(UUID.fromString(args[1]))) return VENDOR_RESOLUTIONS; }
                catch (IllegalArgumentException ignored) { }
                return Arrays.stream(Resolution.values()).map(e -> e.name().toLowerCase(Locale.ROOT).replace('_', '-')).toList();
            }
        }
        if (args.length == 2 && Set.of("inspect", "reserve", "buy", "collect", "cancel", "retry").contains(args[0].toLowerCase(Locale.ROOT))
            && sender instanceof Player player && account(player) != null) return market.allEntries().stream()
                .filter(e -> visible(player, account(player), e)).map(e -> e.listing().id().toString()).toList();
        return List.of();
    }

    private UUID resolveId(Player player, AccountId actor, String raw) {
        List<UUID> matches = market.allEntries().stream().filter(e -> visible(player, actor, e)).map(e -> e.listing().id())
            .filter(id -> id.toString().startsWith(raw.toLowerCase(Locale.ROOT))).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }
    private boolean visible(Player player, AccountId actor, Entry<String> entry) {
        return Set.of(Status.ACTIVE, Status.RESERVED, Status.AUCTION).contains(entry.listing().status()) || actor.equals(entry.listing().seller())
            || actor.equals(entry.listing().reservedBy()) || actor.equals(entry.recipient()) || player.hasPermission(REVIEW_PERMISSION);
    }
    private boolean needsAttention(Entry<String> entry) {
        return Set.of(Status.DEPOSIT_PENDING, Status.FEE_PENDING, Status.CHECKOUT, Status.DELIVERING, Status.REVIEW).contains(entry.listing().status());
    }
    static boolean blocksStallMove(Stall existing, StallLocation proposed, List<Entry<String>> entries) {
        return existing != null && !existing.location().equals(proposed) && entries.stream().anyMatch(entry ->
            entry.listing().stallId().equals(existing.id())
                && entry.listing().status() != Status.SOLD && entry.listing().status() != Status.CANCELLED);
    }
    static boolean canRetryPayment(AccountId actor, Entry<String> entry) {
        return actor != null && (entry.listing().status() == Status.FEE_PENDING || entry.listing().status() == Status.CHECKOUT)
            && (actor.equals(entry.listing().seller()) || actor.equals(entry.recipient()) || actor.equals(entry.listing().reservedBy()));
    }
    private void respond(Player player, AccountId actor, Supplier<CompletionStage<Result<String>>> operation) {
        if (!current(player, actor)) return;
        CompletionStage<Result<String>> stage;
        try { stage = operation.get(); } catch (RuntimeException error) { stage = java.util.concurrent.CompletableFuture.failedFuture(error); }
        stage.whenComplete((value, error) -> sync(() -> {
            if (error != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Market operation failed for " + actor, error);
            if (!current(player, actor)) return;
            if (error != null) Chat.notice(player, "The market could not confirm this yet. Check " + Chat.command("market mine") + " before retrying.");
            else result(player, value);
        }));
    }
    private void result(Player player, Result<String> result) {
        String reference = result.entry().map(e -> " <gray>[" + shortId(e.listing().id()) + "]</gray>").orElse("");
        tone(result.outcome()).send(player, Chat.escape(result.message()) + reference);
        if (result.outcome() == Outcome.READY) result.entry().ifPresent(e ->
            Chat.info(player, "Collect it from anywhere with " + Chat.command("market collect " + e.listing().id()) + "."));
    }
    private void help(Player player) {
        Chat.send(player, "<gold><bold>Market & Player Stores</bold></gold>");
        Chat.info(player, Chat.command("store") + " opens player stores. " + Chat.command("store sell") + " opens the listing editor for your held item.");
        Chat.info(player, Chat.syntax("/store sell <total price> [amount]") + " lists items. " + Chat.syntax("/store search <item>") + " finds supplies.");
        Chat.info(player, Chat.syntax("/store name <name>") + " and " + Chat.syntax("/store description <text>") + " personalise your store.");
        Chat.info(player, Chat.syntax("/market reserve <id>") + " holds a listing for five minutes. Buy and collect from anywhere.");
        Chat.info(player, Chat.command("market inbox") + " shows purchases and returns. " + Chat.syntax("/market collect <id>") + " collects one.");
        Chat.info(player, Chat.command("market mine") + " shows your history. " + Chat.syntax("/market cancel <id>") + " withdraws an unsold listing.");
        Chat.info(player, "Items stay safe when you log out or run out of room. Pending payments retry with the original receipt.");
        Chat.info(player, "If a transfer is interrupted, staff check it first. Do not list the same item twice.");
    }

    /** Shared confirm screen; Cancel reopens the screen the player came from. */
    private void confirm(Player player, String title, ItemStack summary, List<String> detail, Runnable action, Runnable back) {
        MenuKit.openConfirm(plugin, player, title, summary, detail, confirmed -> guarded(player, action), cancelled -> guarded(player, back));
    }
    private ItemStack listingSummary(UUID id) {
        return market.entry(id).map(entry -> {
            ItemStack item = MarketItems.decode(entry.listing().item());
            appendLore(item, List.of("", "<gray>Price</gray> " + Chat.marksTag(entry.listing().price()),
                "<gray>Seller</gray> <white>" + Chat.escape(name(entry.listing().seller())) + "</white>"));
            return item;
        }).orElseGet(() -> MenuKit.item(Material.PAPER, "<gold>Listing " + shortId(id) + "</gold>", List.of()));
    }
    private void guarded(Player player, Runnable action) {
        if (!current(player, account(player))) return;
        try { action.run(); }
        catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Market menu action failed", error);
            Chat.notice(player, "This action is held for staff review. Check " + Chat.command("market mine") + ".");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Page page)) return;
        boolean denied = event.isCancelled();
        event.setCancelled(true);
        if (denied || !(event.getWhoClicked() instanceof Player player) || !page.viewer.equals(player.getUniqueId()) || !current(player, page.account)
            || page.busy || event.getRawSlot() < 0 || event.getRawSlot() >= 54 || event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        Runnable action = page.actions.get(event.getRawSlot());
        if (action == null) return;
        page.busy = true;
        MenuKit.clickSound(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!current(player, page.account) || player.getOpenInventory().getTopInventory() != page.inventory) return;
            try { guarded(player, action); }
            finally { page.busy = false; }
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Page) event.setCancelled(true);
    }
    private Page page(Player player, String title) {
        Page page = new Page(player.getUniqueId(), Objects.requireNonNull(account(player)));
        page.inventory = Bukkit.createInventory(page, 54, Chat.render(title));
        MenuKit.frame(page.inventory);
        return page;
    }
    /** A clickable menu item: name and lore are markup, and the lore ends with the click hint. */
    private static ItemStack tile(Material material, String name, List<String> lore, String action) {
        return MenuKit.item(material, name, MenuKit.withHint(lore, action));
    }
    private static void appendLore(ItemStack item, List<String> lines) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lines.forEach(line -> lore.add(Chat.render(line).decoration(TextDecoration.ITALIC, false)));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
    }
    private static int slot(int n) { return 10 + n / 7 * 9 + n % 7; }
    static int pages(int entries) { return Math.max(1, (entries + PER_PAGE - 1) / PER_PAGE); }
    private void button(Page page, int slot, ItemStack item, Runnable action) { page.inventory.setItem(slot, item); page.actions.put(slot, action); }
    private void show(Player player, Page page) { if (current(player, page.account)) player.openInventory(page.inventory); }
    private boolean current(Player player, AccountId actor) {
        return actor != null && !closed && plugin.isEnabled() && player.isOnline() && !player.isDead()
            && Bukkit.getPlayer(player.getUniqueId()) == player && actor.equals(account(player));
    }
    private AccountId account(Player player) { return plugin.sessions().account(player.getUniqueId()).orElse(null); }
    private String name(AccountId actor) { return plugin.playerData().read(actor, yaml -> yaml.getString("identity.last-known-name", shortId(actor.value()))); }
    private static String shortId(UUID id) { return id.toString().substring(0, 8); }
    /** A full reference that copies when clicked, for players to give staff. */
    private static String reference(UUID id) {
        if (id == null) return "<white>none</white>";
        return "<click:copy_to_clipboard:'" + id + "'><hover:show_text:'<gray>Click to copy</gray>'><white>" + id + "</white></hover></click>";
    }
    private static String plural(long count, String one, String many) { return count == 1 ? one : many; }

    /** A player-facing status, so no enum name reaches lore. */
    static String statusName(Status status) {
        return switch (status) {
            case DEPOSIT_PENDING -> "Listing in progress";
            case FEE_PENDING -> "Listing fee settling";
            case ACTIVE -> "For sale";
            case AUCTION -> "On auction";
            case RESERVED -> "Reserved";
            case CHECKOUT -> "Payment settling";
            case READY -> "Ready to collect";
            case DELIVERING -> "Delivery in progress";
            case SOLD -> "Sold";
            case CANCELLED -> "Withdrawn";
            case REVIEW -> "Held for staff review";
        };
    }
    static String statusLine(Status status) {
        String colour = switch (status) {
            case ACTIVE, AUCTION, READY, SOLD -> "green";
            case CANCELLED -> "gray";
            default -> "yellow";
        };
        return "<gray>Status</gray> <" + colour + ">" + statusName(status) + "</" + colour + ">";
    }

    /** How an outcome reads in chat: done, still settling, or refused. */
    enum Tone {
        SUCCESS, NOTICE, ERROR;
        void send(Audience audience, String markup) {
            switch (this) {
                case SUCCESS -> Chat.success(audience, markup);
                case NOTICE -> Chat.notice(audience, markup);
                case ERROR -> Chat.error(audience, markup);
            }
        }
    }
    static Tone tone(Outcome outcome) {
        return switch (outcome) {
            case PREPARED, ACTIVE, RESERVED, READY, SOLD, CANCELLED -> Tone.SUCCESS;
            case DELIVERING, REVIEW, PENDING, SAVE_FAILED -> Tone.NOTICE;
            case NOT_FOUND, DENIED, UNAVAILABLE, INVALID, INSUFFICIENT_FUNDS -> Tone.ERROR;
        };
    }
    static Tone tone(net.nerdsmp.paper.auction.DurableAuctionService.Outcome outcome) {
        return switch (outcome) {
            case PREPARED, ACTIVE, BID_ACCEPTED, COMPLETE, CANCELLED -> Tone.SUCCESS;
            case PENDING, REVIEW, SAVE_FAILED -> Tone.NOTICE;
            case NOT_FOUND, DENIED, UNAVAILABLE, INVALID, INSUFFICIENT_FUNDS, STALE -> Tone.ERROR;
        };
    }

    private void sync(Runnable action) {
        if (closed || !plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) action.run(); else Bukkit.getScheduler().runTask(plugin, action);
    }
    private static <T> T read(CompletionStage<T> stage) {
        var future = stage.toCompletableFuture();
        if (!future.isDone()) throw new IllegalStateException("Expected a synchronous market snapshot");
        return future.join();
    }
    private static final class Page implements InventoryHolder {
        private final UUID viewer;
        private final AccountId account;
        private final Map<Integer, Runnable> actions = new HashMap<>();
        private Inventory inventory;
        private boolean busy;
        private Page(UUID viewer, AccountId account) { this.viewer = viewer; this.account = account; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private record AuctionTerms(Long buyNow, int hours) { }
}
