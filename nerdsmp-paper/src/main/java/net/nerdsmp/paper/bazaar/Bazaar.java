package net.nerdsmp.paper.bazaar;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.item.FoodItems;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The Bazaar, after Hypixel SkyBlock's: a market for bulk goods, crops, ore, logs, fish, mob drops and expedition
 * materials, run as an order book. Buy instantly from the cheapest offer, sell instantly to the best bid, or post your
 * own buy order (Marks held in escrow) or sell offer (items held). Every sale pays a 1.25% tax: a Marks sink.
 * Single, unique items (gear and the like) still go through the auction house and market stalls.
 */
public final class Bazaar implements Listener, AutoCloseable {
    public enum Category {
        FARMING("Farming", Material.GOLDEN_HOE), MINING("Mining", Material.IRON_PICKAXE), WOODS("Woods", Material.IRON_AXE),
        FISHING("Fishing", Material.FISHING_ROD), COMBAT("Combat", Material.IRON_SWORD), EXPEDITION("Expedition Materials", Material.RECOVERY_COMPASS);
        final String title; final Material icon;
        Category(String title, Material icon) { this.title = title; this.icon = icon; }
    }

    /** A tradeable good: a plain vanilla item, or a catalog item by id. */
    public record Product(String id, String name, Material icon, Category category, String itemId) { }

    static final List<Product> PRODUCTS = new ArrayList<>();
    static {
        vanilla(Category.FARMING, Material.WHEAT, Material.WHEAT_SEEDS, Material.CARROT, Material.POTATO, Material.BEETROOT, Material.MELON_SLICE,
            Material.PUMPKIN, Material.SUGAR_CANE, Material.NETHER_WART, Material.COCOA_BEANS, Material.APPLE, Material.SWEET_BERRIES, Material.CACTUS, Material.EGG);
        vanilla(Category.MINING, Material.COBBLESTONE, Material.COAL, Material.RAW_COPPER, Material.COPPER_INGOT, Material.RAW_IRON, Material.IRON_INGOT,
            Material.RAW_GOLD, Material.GOLD_INGOT, Material.LAPIS_LAZULI, Material.REDSTONE, Material.QUARTZ, Material.AMETHYST_SHARD, Material.OBSIDIAN,
            Material.DIAMOND, Material.EMERALD);
        vanilla(Category.WOODS, Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.CHERRY_LOG, Material.MANGROVE_LOG);
        vanilla(Category.FISHING, Material.COD, Material.SALMON, Material.PUFFERFISH, Material.TROPICAL_FISH, Material.INK_SAC, Material.PRISMARINE_SHARD, Material.KELP);
        vanilla(Category.COMBAT, Material.ROTTEN_FLESH, Material.BONE, Material.STRING, Material.SPIDER_EYE, Material.GUNPOWDER, Material.ENDER_PEARL,
            Material.SLIME_BALL, Material.BLAZE_ROD, Material.LEATHER, Material.FEATHER);
        custom("tidepearl", "Tidepearl", Material.HEART_OF_THE_SEA);
        custom("thornheart_sap", "Thornheart Sap", Material.HONEY_BOTTLE);
        custom("gloomsteel_ore", "Gloomsteel Ore", Material.RAW_IRON);
        custom("cinder_shard", "Cinder Shard", Material.BLAZE_POWDER);
        custom("sigil_shard", "Sigil Shard", Material.AMETHYST_SHARD);
        custom("sovereign_ash", "Sovereign Ash", Material.GUNPOWDER);
    }
    private static void vanilla(Category category, Material... materials) {
        for (Material material : materials)
            PRODUCTS.add(new Product(material.name().toLowerCase(Locale.ROOT), net.nerdsmp.paper.presentation.VisualFormat.titleCase(material.name().toLowerCase(Locale.ROOT).replace('_', ' ')), material, category, null));
    }
    private static void custom(String id, String name, Material icon) { PRODUCTS.add(new Product("x_" + id, name, icon, Category.EXPEDITION, id)); }

    public static Product product(String id) { return PRODUCTS.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null); }

    /** A trade, for prices and the website's price board. */
    public record Trade(String product, long price, int amount, long at) { }

    private final NerdSmpPlugin plugin;
    private final File file;
    private final BazaarBook book = new BazaarBook();
    private final Deque<Trade> trades = new ArrayDeque<>();
    private final Map<String, Long> last = new HashMap<>();
    /** An order a player has clicked once to cancel; the next click on it cancels. */
    private final Map<UUID, UUID> armedCancel = new HashMap<>();
    private boolean dirty;
    private org.bukkit.scheduler.BukkitTask saver;

    public Bazaar(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bazaar.yml");
        load();
        saver = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 200L, 200L);
    }

    public BazaarBook book() { return book; }

    /** The sales tax now: halved while the Free Market councillor is seated. */
    public double tax() { return plugin.perk(net.nerdsmp.paper.calendar.WardensCouncil.Perk.FREE_MARKET) ? BazaarBook.TAX / 2 : BazaarBook.TAX; }
    private long afterTax(long gross) { return BazaarBook.afterTax(gross, tax()); }

    // ------------------------------------------------------------------ storage

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection orders = yaml.getConfigurationSection("orders");
        if (orders != null) for (String id : orders.getKeys(false)) {
            ConfigurationSection o = orders.getConfigurationSection(id);
            try {
                book.add(new BazaarBook.Order(UUID.fromString(id), o.getString("product"), BazaarBook.Side.valueOf(o.getString("side")),
                    UUID.fromString(o.getString("account")), o.getLong("price"), o.getInt("amount"), o.getLong("created"), o.getInt("filled"), o.getInt("claimable")));
            } catch (RuntimeException bad) { plugin.getLogger().warning("Bazaar order " + id + " could not be read: " + bad); }
        }
        for (String raw : yaml.getStringList("trades")) {
            String[] p = raw.split(":");
            try { record(new Trade(p[0], Long.parseLong(p[1]), Integer.parseInt(p[2]), Long.parseLong(p[3]))); } catch (RuntimeException ignored) { }
        }
    }

    /** Writes the book. Called soon after every change (and on shutdown); a crash loses at most ten seconds of trades. */
    void save() {
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        for (BazaarBook.Order o : book.all()) {
            String key = "orders." + o.id();
            yaml.set(key + ".product", o.product());
            yaml.set(key + ".side", o.side().name());
            yaml.set(key + ".account", o.account().toString());
            yaml.set(key + ".price", o.price());
            // An unpaid reservation is saved as still open: if the server stops mid-payment, the trade simply didn't happen.
            yaml.set(key + ".amount", o.amount());
            yaml.set(key + ".filled", o.filled());
            yaml.set(key + ".claimable", o.claimable());
            yaml.set(key + ".created", o.createdAt());
        }
        yaml.set("trades", trades.stream().map(t -> t.product() + ":" + t.price() + ":" + t.amount() + ":" + t.at()).toList());
        try { AtomicYamlFiles.save(yaml, file); }
        catch (IOException failure) { dirty = true; plugin.getLogger().warning("The Bazaar could not be saved: " + failure); }
    }

    private void changed() { dirty = true; }

    private void record(Trade trade) {
        trades.addLast(trade);
        last.put(trade.product(), trade.price());
        long cutoff = System.currentTimeMillis() - Duration.ofDays(1).toMillis();
        while (!trades.isEmpty() && trades.peekFirst().at() < cutoff) trades.removeFirst();
    }

    /** Units of a product traded in the last day. */
    public int volume(String product) {
        long cutoff = System.currentTimeMillis() - Duration.ofDays(1).toMillis();
        return trades.stream().filter(t -> t.product().equals(product) && t.at() >= cutoff).mapToInt(Trade::amount).sum();
    }
    public long lastPrice(String product) { return last.getOrDefault(product, -1L); }

    // ------------------------------------------------------------------ items

    private boolean matches(Product product, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        if (product.itemId() == null) return stack.getType() == product.icon() && FoodItems.ordinary(stack);
        return plugin.items().describe(stack).map(d -> d.itemId().value().equals(product.itemId())).orElse(false);
    }

    /** Whether this server can make the product at all (expedition materials need their catalog entry). */
    boolean available(Product product) {
        if (product.itemId() == null) return true;
        try { return plugin.items().itemName(new ItemId(product.itemId())).isPresent(); } catch (RuntimeException missing) { return false; }
    }

    private ItemStack make(Product product, int amount) {
        ItemStack stack = product.itemId() == null ? new ItemStack(product.icon()) : plugin.items().create(new ItemId(product.itemId()));
        stack.setAmount(amount);
        return stack;
    }

    int count(Player player, Product product) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) if (matches(product, stack)) n += stack.getAmount();
        return n;
    }

    private void take(Player player, Product product, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            if (!matches(product, contents[i])) continue;
            int used = Math.min(amount, contents[i].getAmount());
            contents[i].setAmount(contents[i].getAmount() - used);
            if (contents[i].getAmount() <= 0) contents[i] = null;
            amount -= used;
        }
        player.getInventory().setStorageContents(contents);
    }

    /** How many of a product fit in the player's inventory right now. */
    int room(Player player, Product product) {
        ItemStack sample = make(product, 1);
        int max = sample.getMaxStackSize(), room = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) room += max;
            else if (stack.isSimilar(sample)) room += Math.max(0, max - stack.getAmount());
        }
        return room;
    }

    private void give(Player player, Product product, int amount) {
        int max = make(product, 1).getMaxStackSize();
        while (amount > 0) {
            int n = Math.min(max, amount);
            amount -= n;
            player.getInventory().addItem(make(product, n)).values().forEach(spill -> player.getWorld().dropItemNaturally(player.getLocation(), spill));
        }
    }

    private AccountId account(Player player) {
        return plugin.sessions().account(player.getUniqueId()).orElseThrow(() -> new IllegalArgumentException("Your account is still loading."));
    }

    // ------------------------------------------------------------------ trading

    /** Buys up to {@code amount} instantly from the cheapest offers, paying no more than {@code expected} in all. */
    void instantBuy(Player player, Product product, int amount, long expected) {
        AccountId buyer = account(player);
        int fit = Math.min(amount, room(player, product));
        if (fit <= 0) { Chat.error(player, "Make room in your inventory first."); return; }
        List<BazaarBook.Fill> fills = book.plan(product.id(), BazaarBook.Side.SELL, fit, buyer.value());
        if (fills.isEmpty()) { Chat.error(player, "Nobody is selling " + product.name() + " right now. Post a buy order instead."); return; }
        long cost = BazaarBook.total(fills);
        int units = BazaarBook.units(fills);
        if (expected > 0 && cost > expected && units >= Math.min(amount, fit)) { Chat.error(player, "The price moved. Have another look."); openProduct(player, product); return; }
        BazaarBook.reserve(fills);
        UUID trade = UUID.randomUUID();
        plugin.economy().debit(buyer, cost, new EconomyService.TransactionReason("bazaar-buy", units + " " + product.name()), "bazaar-buy:" + trade)
            .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || result == null || result.status() != EconomyService.TransactionResult.Status.APPLIED) {
                    BazaarBook.release(fills);
                    if (player.isOnline()) Chat.error(player, "That costs " + Chat.marks(cost) + ". You don't have enough Marks.");
                    return;
                }
                BazaarBook.settleBuy(fills);
                long now = System.currentTimeMillis();
                for (BazaarBook.Fill fill : fills) {
                    long gross = fill.cost();
                    plugin.economy().credit(new AccountId(fill.order().account()), afterTax(gross),
                        new EconomyService.TransactionReason("bazaar-sale", fill.amount() + " " + product.name() + " sold"), "bazaar-sale:" + trade + ":" + fill.order().id());
                    record(new Trade(product.id(), fill.order().price(), fill.amount(), now));
                    notifyOwner(fill.order(), "<green>Bazaar:</green> <gray>sold</gray> <white>" + fill.amount() + " " + Chat.escape(product.name()) + "</white> <gray>for</gray> <gold>" + Chat.marks(afterTax(gross)) + "</gold>");
                }
                book.all().stream().filter(BazaarBook.Order::done).toList().forEach(book::remove);
                changed();
                if (player.isOnline()) {
                    give(player, product, units);
                    Chat.success(player, "Bought <white>" + units + " " + Chat.escape(product.name()) + "</white> for <gold>" + Chat.marks(cost) + "</gold>.");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.2F);
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) openProduct(player, product);
                } else {
                    // They left mid-payment: the goods wait as a filled buy order to claim.
                    book.add(new BazaarBook.Order(UUID.randomUUID(), product.id(), BazaarBook.Side.BUY, buyer.value(), Math.max(1, cost / units), units, now, units, units));
                }
            }));
    }

    /** Sells up to {@code amount} from the player's inventory instantly to the best bids. */
    void instantSell(Player player, Product product, int amount) {
        AccountId seller = account(player);
        int have = Math.min(amount, count(player, product));
        if (have <= 0) { Chat.error(player, "You have no " + product.name() + " to sell."); return; }
        List<BazaarBook.Fill> fills = book.plan(product.id(), BazaarBook.Side.BUY, have, seller.value());
        if (fills.isEmpty()) { Chat.error(player, "Nobody is buying " + product.name() + " right now. Post a sell offer instead."); return; }
        int units = BazaarBook.units(fills);
        long gross = BazaarBook.total(fills), net = afterTax(gross);
        take(player, product, units);
        BazaarBook.settleSell(fills);
        UUID trade = UUID.randomUUID();
        long now = System.currentTimeMillis();
        for (BazaarBook.Fill fill : fills) {
            record(new Trade(product.id(), fill.order().price(), fill.amount(), now));
            notifyOwner(fill.order(), "<green>Bazaar:</green> <gray>your buy order got</gray> <white>" + fill.amount() + " " + Chat.escape(product.name()) + "</white><gray>. Claim it in</gray> " + Chat.command("bazaar orders"));
        }
        plugin.economy().credit(seller, net, new EconomyService.TransactionReason("bazaar-sale", units + " " + product.name() + " sold"), "bazaar-sell:" + trade);
        changed();
        Chat.success(player, "Sold <white>" + units + " " + Chat.escape(product.name()) + "</white> for <gold>" + Chat.marks(net) + "</gold> <dark_gray>(after " + (tax() * 100) + "% tax)</dark_gray>.");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.0F);
        openProduct(player, product);
    }

    void placeBuy(Player player, Product product, int amount, long price) {
        AccountId buyer = account(player);
        String refused = book.refuse(product.id(), BazaarBook.Side.BUY, price, amount, buyer.value());
        if (refused != null) { Chat.error(player, refused); return; }
        long escrow = price * amount;
        UUID id = UUID.randomUUID();
        plugin.economy().debit(buyer, escrow, new EconomyService.TransactionReason("bazaar-order", "Buy order: " + amount + " " + product.name()), "bazaar-escrow:" + id)
            .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || result == null || result.status() != EconomyService.TransactionResult.Status.APPLIED) {
                    if (player.isOnline()) Chat.error(player, "That order holds " + Chat.marks(escrow) + ". You don't have enough Marks.");
                    return;
                }
                book.add(new BazaarBook.Order(id, product.id(), BazaarBook.Side.BUY, buyer.value(), price, amount, System.currentTimeMillis(), 0, 0));
                changed();
                if (player.isOnline()) {
                    Chat.success(player, "Buy order placed: <white>" + amount + " " + Chat.escape(product.name()) + "</white> at <gold>" + Chat.marks(price) + "</gold> each. <gray>" + Chat.marks(escrow) + " held until it fills or you cancel.</gray>");
                    openProduct(player, product);
                }
            }));
    }

    void placeSell(Player player, Product product, int amount, long price) {
        AccountId seller = account(player);
        String refused = book.refuse(product.id(), BazaarBook.Side.SELL, price, amount, seller.value());
        if (refused != null) { Chat.error(player, refused); return; }
        if (count(player, product) < amount) { Chat.error(player, "You only have " + count(player, product) + " " + product.name() + "."); return; }
        take(player, product, amount);
        book.add(new BazaarBook.Order(UUID.randomUUID(), product.id(), BazaarBook.Side.SELL, seller.value(), price, amount, System.currentTimeMillis(), 0, 0));
        changed();
        Chat.success(player, "Sell offer placed: <white>" + amount + " " + Chat.escape(product.name()) + "</white> at <gold>" + Chat.marks(price) + "</gold> each.");
        openProduct(player, product);
    }

    /** Claims what a buy order has bought, and finishes a cancelled or filled order. */
    void claim(Player player, BazaarBook.Order order) {
        Product product = product(order.product());
        if (product == null || order.claimable() <= 0) return;
        int n = Math.min(order.claimable(), room(player, product));
        if (n <= 0) { Chat.error(player, "Make room in your inventory first."); return; }
        order.claimable -= n;
        give(player, product, n);
        if (order.done()) book.remove(order);
        changed();
        Chat.success(player, "Claimed <white>" + n + " " + Chat.escape(product.name()) + "</white>.");
    }

    void cancel(Player player, BazaarBook.Order order) {
        Product product = product(order.product());
        if (product == null) return;
        if (order.reserved > 0) { Chat.error(player, "A sale is going through on that order. Try again in a moment."); return; }
        int open = order.amount() - order.filled();
        if (order.side() == BazaarBook.Side.SELL) {
            book.remove(order);
            give(player, product, open);
            Chat.success(player, "Sell offer cancelled. <white>" + open + " " + Chat.escape(product.name()) + "</white> returned.");
        } else {
            long refund = order.price() * open;
            // The order closes at what's filled; anything bought waits to be claimed.
            BazaarBook.Order closed = new BazaarBook.Order(order.id(), order.product(), order.side(), order.account(), order.price(), order.filled(), order.createdAt(), order.filled(), order.claimable());
            book.remove(order);
            if (closed.claimable() > 0) book.add(closed);
            if (refund > 0) plugin.economy().credit(account(player), refund, new EconomyService.TransactionReason("bazaar-refund", "Buy order cancelled"), "bazaar-refund:" + order.id());
            Chat.success(player, "Buy order cancelled. <gold>" + Chat.marks(refund) + "</gold> returned." + (closed.claimable() > 0 ? " <gray>Claim what it bought below.</gray>" : ""));
            if (closed.claimable() > 0) claim(player, closed);
        }
        changed();
    }

    private void notifyOwner(BazaarBook.Order order, String message) {
        Player owner = null;
        for (Player online : Bukkit.getOnlinePlayers())
            if (plugin.sessions().account(online.getUniqueId()).map(a -> a.value().equals(order.account())).orElse(false)) { owner = online; break; }
        if (owner != null) Chat.send(owner, message);
    }

    // ------------------------------------------------------------------ menus

    private static final int[] CATEGORY_SLOTS = {19, 20, 21, 22, 23, 24};
    private static final int[] PRODUCT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int[] ORDER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public void open(Player player) {
        Holder holder = new Holder(player.getUniqueId(), Holder.Page.MAIN);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<gold>Bazaar</gold>"));
        MenuKit.frame(holder.inventory);
        holder.inventory.setItem(4, MenuKit.item(Material.EMERALD, "<gold>Bazaar</gold>", List.of(
            "<gray>A market for bulk goods. Buy and sell instantly</gray>",
            "<gray>against the book, or post your own orders.</gray>",
            "<gray>Sales pay a " + (tax() * 100) + "% tax. Gear goes to the</gray> <white>/ah</white><gray>.</gray>")));
        for (int i = 0; i < Category.values().length; i++) {
            Category category = Category.values()[i];
            long count = PRODUCTS.stream().filter(p -> p.category() == category && available(p)).count();
            holder.inventory.setItem(CATEGORY_SLOTS[i], MenuKit.item(category.icon, "<yellow>" + category.title + "</yellow>", List.of("<gray>" + count + " goods</gray>", "<yellow>Click to browse.</yellow>")));
        }
        ordersButton(holder, player, 47);
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    private void ordersButton(Holder holder, Player player, int slot) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        List<BazaarBook.Order> mine = account == null ? List.of() : book.of(account.value());
        int claim = mine.stream().mapToInt(BazaarBook.Order::claimable).sum();
        holder.inventory.setItem(slot, MenuKit.item(Material.BOOK, "<aqua>Your orders</aqua>", List.of(
            "<white>" + mine.size() + "/" + BazaarBook.MAX_ORDERS + "</white> <gray>orders</gray>",
            claim > 0 ? "<green>" + claim + " items to claim</green>" : "<gray>Nothing to claim.</gray>", "<yellow>Click to manage.</yellow>")));
    }

    void openCategory(Player player, Category category) {
        Holder holder = new Holder(player.getUniqueId(), Holder.Page.CATEGORY);
        holder.category = category;
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<gold>Bazaar · " + category.title + "</gold>"));
        MenuKit.frame(holder.inventory);
        holder.inventory.setItem(4, MenuKit.item(category.icon, "<gold>" + category.title + "</gold>", List.of("<gray>Click a good to trade it.</gray>")));
        List<Product> products = PRODUCTS.stream().filter(p -> p.category() == category && available(p)).toList();
        for (int i = 0; i < products.size() && i < PRODUCT_SLOTS.length; i++) {
            Product product = products.get(i);
            holder.inventory.setItem(PRODUCT_SLOTS[i], MenuKit.item(product.icon(), "<white>" + Chat.escape(product.name()) + "</white>", summary(product)));
            holder.products.put(PRODUCT_SLOTS[i], product);
        }
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>Bazaar</yellow>", List.of()));
        ordersButton(holder, player, 47);
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    private List<String> summary(Product product) {
        long offer = book.bestOffer(product.id()), bid = book.bestBid(product.id());
        return List.of(
            "<gray>Buy price:</gray> " + (offer > 0 ? "<gold>" + Chat.marks(offer) + "</gold>" : "<dark_gray>none on offer</dark_gray>"),
            "<gray>Sell price:</gray> " + (bid > 0 ? "<gold>" + Chat.marks(bid) + "</gold>" : "<dark_gray>nobody buying</dark_gray>"),
            "<gray>Traded today:</gray> <white>" + volume(product.id()) + "</white>",
            "<yellow>Click to trade.</yellow>");
    }

    void openProduct(Player player, Product product) {
        Holder holder = new Holder(player.getUniqueId(), Holder.Page.PRODUCT);
        holder.product = product;
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<gold>Bazaar · " + Chat.escape(product.name()) + "</gold>"));
        MenuKit.frame(holder.inventory);
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        UUID me = account == null ? null : account.value();
        holder.inventory.setItem(4, MenuKit.item(product.icon(), "<white>" + Chat.escape(product.name()) + "</white>", summary(product).subList(0, 3)));
        int[] sizes = {1, 16, 64};
        int[] slots = {19, 20, 21};
        for (int i = 0; i < sizes.length; i++) {
            List<BazaarBook.Fill> fills = me == null ? List.of() : book.plan(product.id(), BazaarBook.Side.SELL, sizes[i], me);
            long cost = BazaarBook.total(fills);
            int units = BazaarBook.units(fills);
            holder.costs.put(slots[i], cost);
            holder.inventory.setItem(slots[i], MenuKit.item(Material.LIME_DYE, "<green>Buy " + sizes[i] + "</green>", units == 0 ? List.of("<dark_gray>None on offer.</dark_gray>")
                : List.of("<gray>Cost:</gray> <gold>" + Chat.marks(cost) + "</gold>" + (units < sizes[i] ? " <gray>(only " + units + " on offer)</gray>" : ""), "<yellow>Click to buy now.</yellow>")));
        }
        holder.inventory.setItem(22, MenuKit.item(Material.LIME_STAINED_GLASS_PANE, "<green>Buy an amount</green>", List.of(
            "<gray>Choose how many to buy now.</gray>", "<yellow>Click to choose.</yellow>")));
        int have = count(player, product);
        List<BazaarBook.Fill> sale = me == null || have == 0 ? List.of() : book.plan(product.id(), BazaarBook.Side.BUY, have, me);
        holder.inventory.setItem(23, MenuKit.item(Material.GOLD_INGOT, "<gold>Sell inventory</gold>", have == 0 ? List.of("<dark_gray>You have none.</dark_gray>")
            : sale.isEmpty() ? List.of("<gray>You have</gray> <white>" + have + "</white><gray>.</gray>", "<dark_gray>Nobody is buying.</dark_gray>")
            : List.of("<gray>Sell</gray> <white>" + BazaarBook.units(sale) + "</white> <gray>for</gray> <gold>" + Chat.marks(afterTax(BazaarBook.total(sale))) + "</gold>", "<yellow>Click to sell now.</yellow>")));
        holder.inventory.setItem(24, MenuKit.item(Material.WRITABLE_BOOK, "<aqua>Create buy order</aqua>", List.of(
            "<gray>Bid for goods; your Marks are held</gray>", "<gray>until sellers fill it.</gray>", "<yellow>Click to set amount and price.</yellow>")));
        holder.inventory.setItem(25, MenuKit.item(Material.PAPER, "<aqua>Create sell offer</aqua>", List.of(
            "<gray>Put goods up at your price;</gray>", "<gray>Marks arrive as they sell.</gray>", "<yellow>Click to set amount and price.</yellow>")));
        holder.inventory.setItem(30, MenuKit.item(Material.CHEST, "<white>Top sell offers</white>", depthLines(product, BazaarBook.Side.SELL)));
        holder.inventory.setItem(32, MenuKit.item(Material.HOPPER, "<white>Top buy orders</white>", depthLines(product, BazaarBook.Side.BUY)));
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>" + product.category().title + "</yellow>", List.of()));
        ordersButton(holder, player, 47);
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    private List<String> depthLines(Product product, BazaarBook.Side side) {
        Map<Long, Integer> levels = new LinkedHashMap<>();
        for (BazaarBook.Order order : book.side(product.id(), side)) levels.merge(order.price(), order.open(), Integer::sum);
        List<String> lines = new ArrayList<>();
        levels.entrySet().stream().limit(6).forEach(e -> lines.add("<gold>" + Chat.marks(e.getKey()) + "</gold> <gray>×</gray> <white>" + e.getValue() + "</white>"));
        if (lines.isEmpty()) lines.add("<dark_gray>None yet.</dark_gray>");
        return lines;
    }

    public void openOrders(Player player) {
        AccountId account = account(player);
        Holder holder = new Holder(player.getUniqueId(), Holder.Page.ORDERS);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<aqua>Your Bazaar orders</aqua>"));
        MenuKit.frame(holder.inventory);
        List<BazaarBook.Order> mine = book.of(account.value());
        holder.inventory.setItem(4, MenuKit.item(Material.BOOK, "<aqua>Your orders</aqua>", List.of(
            "<white>" + mine.size() + "/" + BazaarBook.MAX_ORDERS + "</white> <gray>orders.</gray>", "<gray>Click to claim; click twice to cancel.</gray>")));
        for (int i = 0; i < mine.size() && i < ORDER_SLOTS.length; i++) {
            BazaarBook.Order order = mine.get(i);
            Product product = product(order.product());
            if (product == null) continue;
            boolean buy = order.side() == BazaarBook.Side.BUY;
            // The action comes second: the companion's card view shows only the first two lines.
            boolean armed = order.id().equals(armedCancel.get(player.getUniqueId()));
            List<String> lore = new ArrayList<>();
            lore.add((buy ? "<aqua>Buying" : "<gold>Selling") + "</" + (buy ? "aqua" : "gold") + "> <white>" + order.filled() + "/" + order.amount() + "</white> <gray>at</gray> <gold>" + Chat.marks(order.price()) + "</gold>");
            lore.add(order.claimable() > 0 ? "<green>Click to claim " + order.claimable() + ".</green>"
                : armed ? "<red>Click again to cancel" + (buy ? " (Marks back)." : " (items back).") + "</red>"
                : order.filled() < order.amount() ? "<yellow>Click to cancel.</yellow>" : "<gray>Done.</gray>");
            ItemStack icon = MenuKit.item(product.icon(), (buy ? "<aqua>" : "<gold>") + Chat.escape(product.name()) + (buy ? "</aqua>" : "</gold>"), lore);
            if (order.claimable() > 0) icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            holder.inventory.setItem(ORDER_SLOTS[i], icon);
            holder.orders.put(ORDER_SLOTS[i], order.id());
        }
        holder.inventory.setItem(40, MenuKit.item(Material.HOPPER_MINECART, "<green>Claim everything</green>", List.of("<gray>Collect every bought item that fits.</gray>")));
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>Bazaar</yellow>", List.of()));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    // ------------------------------------------------------------------ amount and price

    private void ask(Player player, Product product, boolean buy) {
        long offer = book.bestOffer(product.id()), bid = book.bestBid(product.id());
        long suggested = buy ? (bid > 0 ? bid + 1 : offer > 1 ? offer - 1 : Math.max(1, lastPrice(product.id())))
            : (offer > 0 ? Math.max(1, offer - 1) : bid > 0 ? bid + 1 : Math.max(1, lastPrice(product.id())));
        if (buy && offer > 0 && suggested >= offer) suggested = Math.max(1, offer - 1);
        if (!buy && bid > 0 && suggested <= bid) suggested = bid + 1;
        String amount = buy ? "64" : String.valueOf(Math.max(1, count(player, product)));
        List<String> lines = List.of(
            "<gray>Best offer:</gray> " + (offer > 0 ? "<gold>" + Chat.marks(offer) + "</gold>" : "<dark_gray>none</dark_gray>") + " <gray>· Best bid:</gray> " + (bid > 0 ? "<gold>" + Chat.marks(bid) + "</gold>" : "<dark_gray>none</dark_gray>"),
            buy ? "<gray>Your Marks are held until the order fills or you cancel it.</gray>" : "<gray>The items leave your inventory now and come back if you cancel.</gray>");
        dialog(player, (buy ? "<aqua>Buy order: " : "<gold>Sell offer: ") + Chat.escape(product.name()) + (buy ? "</aqua>" : "</gold>"), lines, amount, String.valueOf(suggested), (a, p) -> {
            int n; long each;
            try { n = Integer.parseInt(a.trim()); each = Long.parseLong(p.trim().replace(",", "")); }
            catch (NumberFormatException bad) { Chat.error(player, "Amount and price must be whole numbers."); return; }
            if (buy) placeBuy(player, product, n, each); else placeSell(player, product, n, each);
        });
    }

    private void ask(Player player, Product product) {
        dialog(player, "<green>Buy " + Chat.escape(product.name()) + "</green>", List.of("<gray>Buys now from the cheapest offers.</gray>"), "128", null, (a, ignored) -> {
            try { instantBuy(player, product, Math.clamp(Integer.parseInt(a.trim()), 1, BazaarBook.MAX_AMOUNT), -1); }
            catch (NumberFormatException bad) { Chat.error(player, "The amount must be a whole number."); }
        });
    }

    private void dialog(Player player, String title, List<String> lines, String amount, String price, BiConsumer<String, String> done) {
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text("amount", Chat.render("<gray>Amount</gray>")).initial(amount).maxLength(6).width(200).build());
        if (price != null) inputs.add(DialogInput.text("price", Chat.render("<gray>Price each (Marks)</gray>")).initial(price).maxLength(8).width(200).build());
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(Chat.render(title)).canCloseWithEscape(true)
                .body(lines.stream().map(line -> (DialogBody) DialogBody.plainMessage(Chat.render(line))).toList())
                .inputs(inputs).build())
            .type(DialogType.confirmation(
                ActionButton.create(Chat.render("<green>Confirm</green>"), null, 110, DialogAction.customClick((view, audience) -> {
                    String a = view.getText("amount"), p = price == null ? "" : view.getText("price");
                    Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) done.accept(a == null ? "" : a, p == null ? "" : p); });
                }, once)),
                ActionButton.create(Chat.render("<gray>Cancel</gray>"), null, 110, null))));
        player.closeInventory();
        player.showDialog(dialog);
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        boolean shift = event.isShiftClick();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            try {
                if (slot == 49) { player.closeInventory(); return; }
                if (slot == 47 && holder.page != Holder.Page.ORDERS) { openOrders(player); return; }
                switch (holder.page) {
                    case MAIN -> { for (int i = 0; i < CATEGORY_SLOTS.length; i++) if (CATEGORY_SLOTS[i] == slot) openCategory(player, Category.values()[i]); }
                    case CATEGORY -> {
                        if (slot == 45) open(player);
                        else if (holder.products.containsKey(slot)) openProduct(player, holder.products.get(slot));
                    }
                    case PRODUCT -> {
                        Product product = holder.product;
                        if (slot == 45) openCategory(player, product.category());
                        else if (slot == 19) instantBuy(player, product, 1, holder.costs.getOrDefault(19, 0L));
                        else if (slot == 20) instantBuy(player, product, 16, holder.costs.getOrDefault(20, 0L));
                        else if (slot == 21) instantBuy(player, product, 64, holder.costs.getOrDefault(21, 0L));
                        else if (slot == 22) ask(player, product);
                        else if (slot == 23) instantSell(player, product, Integer.MAX_VALUE);
                        else if (slot == 24) ask(player, product, true);
                        else if (slot == 25) ask(player, product, false);
                    }
                    case ORDERS -> {
                        if (slot == 45) { open(player); return; }
                        if (slot == 40) {
                            for (BazaarBook.Order order : book.of(account(player).value())) if (order.claimable() > 0) claim(player, order);
                            openOrders(player);
                            return;
                        }
                        UUID id = holder.orders.get(slot);
                        BazaarBook.Order order = id == null ? null : book.find(id);
                        if (order == null) return;
                        if (order.claimable() > 0) claim(player, order);
                        else if (order.filled() < order.amount()) {
                            // Cancelling takes two clicks, so a stray click never pulls an order.
                            if (order.id().equals(armedCancel.get(player.getUniqueId()))) { armedCancel.remove(player.getUniqueId()); cancel(player, order); }
                            else armedCancel.put(player.getUniqueId(), order.id());
                        }
                        openOrders(player);
                    }
                }
            } catch (IllegalArgumentException refused) { Chat.error(player, Chat.escape(refused.getMessage())); }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }

    @Override public void close() {
        if (saver != null) saver.cancel();
        save();
    }

    // ------------------------------------------------------------------ the website's price board

    /** Prices for the website: every product with a book or a trade, as JSON. */
    public com.google.gson.JsonArray prices() {
        com.google.gson.JsonArray list = new com.google.gson.JsonArray();
        for (Product product : PRODUCTS) {
            long offer = book.bestOffer(product.id()), bid = book.bestBid(product.id()), lastPrice = lastPrice(product.id());
            int volume = volume(product.id());
            if (offer < 0 && bid < 0 && lastPrice < 0) continue;
            com.google.gson.JsonObject row = new com.google.gson.JsonObject();
            row.addProperty("id", product.id());
            row.addProperty("name", product.name());
            row.addProperty("category", product.category().title);
            row.addProperty("buy", offer);
            row.addProperty("sell", bid);
            row.addProperty("last", lastPrice);
            row.addProperty("volume24h", volume);
            list.add(row);
        }
        return list;
    }

    private static final class Holder implements InventoryHolder {
        enum Page { MAIN, CATEGORY, PRODUCT, ORDERS }
        final UUID viewer; final Page page; Category category; Product product; Inventory inventory;
        final Map<Integer, Product> products = new HashMap<>();
        final Map<Integer, Long> costs = new HashMap<>();
        final Map<Integer, UUID> orders = new HashMap<>();
        Holder(UUID viewer, Page page) { this.viewer = viewer; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
