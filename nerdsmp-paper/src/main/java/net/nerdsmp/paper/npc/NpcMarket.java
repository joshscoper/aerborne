package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.time.Instant;
import java.util.*;

/** One server-wide market per season. Quotes integrate marginal prices, not bundle prices. */
final class NpcMarket {
    static final int PRESSURE_LIMIT = 2048;
    enum Side { BUY, SELL }
    enum Phase { RESERVED, PAID, INVENTORY, ESCROWED, REFUNDING, COMPLETE, CANCELLED }
    /** Browse-only prices at neutral standing. A zero price means that side is unavailable. */
    record Snapshot(int units, long buyMarks, long sellMarks, long revision, int pressure, String trend) { }
    record Quote(String commodity, int units, long marks, long revision, Side side, Instant expires) {
        Quote {
            if (commodity == null || !commodity.matches("[a-z0-9_-]{1,200}") || units < 1 || units > 64 || marks < 0 || marks > 1_000_000_000L || revision < 0)
                throw new IllegalArgumentException("Invalid market quote");
            Objects.requireNonNull(side); Objects.requireNonNull(expires);
        }
    }
    record Trade(UUID id, AccountId account, String shop, String product, Quote quote, Phase phase) {
        Trade {
            Objects.requireNonNull(id); Objects.requireNonNull(account); Objects.requireNonNull(quote); Objects.requireNonNull(phase);
            if (shop == null || !shop.matches("[a-z0-9_-]{1,96}") || product == null || !product.matches("[a-z0-9_-]{1,96}"))
                throw new IllegalArgumentException("Invalid vendor trade identifiers");
        }
        boolean pending() { return phase != Phase.COMPLETE && phase != Phase.CANCELLED; }
        Trade phase(Phase value) { return new Trade(id, account, shop, product, quote, value); }
        String paymentKey() { return "npc-market:" + id + ":" + quote.side().name().toLowerCase(Locale.ROOT); }
    }
    private record Stock(int pressure, long revision) { }
    private final File file;
    private Map<String, Stock> stock = new HashMap<>();
    private Map<UUID, Trade> trades = new LinkedHashMap<>();
    private NpcContent pricedContent;
    private Map<String, Double> cheapestAsks = Map.of();
    private final List<NpcRecipePriceGuard.RecipeRule> recipeRules;
    private Map<org.bukkit.Material, Double> recipeSellCaps = Map.of();
    NpcMarket(File file) { this(file, List.of()); }
    NpcMarket(File file, List<NpcRecipePriceGuard.RecipeRule> recipeRules) {
        this.file = Objects.requireNonNull(file); this.recipeRules = List.copyOf(recipeRules); load();
    }
    static String commodity(NpcContent.ShopProduct product) {
        return product.material() != null ? "vanilla_" + product.material().name().toLowerCase(Locale.ROOT)
            : "item_" + HexFormat.of().formatHex(product.customItem().value().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    synchronized Quote quote(NpcContent content, NpcContent.ShopProduct product, NpcContent.ReputationView standing, Side side, Instant now) {
        return quote(content, product, standing, side, product.amount(), now);
    }
    /** Units are actual items, independent of the bundle used to configure the base price. */
    synchronized Quote quote(NpcContent content, NpcContent.ShopProduct product, NpcContent.ReputationView standing, Side side, int units, Instant now) {
        Objects.requireNonNull(standing);
        if (!standing.tier().canTrade()) throw new IllegalArgumentException("This faction is not willing to trade yet.");
        if (standing.points() < product.minimumReputation()) throw new IllegalArgumentException("Your standing is too low for that item.");
        if (side == Side.BUY && product.basePrice() == 0 || side == Side.SELL && product.sellPrice() == 0)
            throw new IllegalArgumentException("This vendor does not offer that trade.");
        return price(content, product, side, units, standing.tier().shopPriceMultiplier(), now);
    }
    synchronized Snapshot snapshot(NpcContent content, NpcContent.ShopProduct product, int units, Instant now) {
        Objects.requireNonNull(product); Objects.requireNonNull(now);
        checkUnits(units); refreshPricing(content);
        String key = commodity(product); Stock current = stock.getOrDefault(key, new Stock(0, 0));
        long buy = product.basePrice() > 0 ? price(content, product, Side.BUY, units, 1, now).marks() : 0;
        long sell = product.sellPrice() > 0 ? price(content, product, Side.SELL, units, 1, now).marks() : 0;
        return new Snapshot(units, buy, sell, current.revision, current.pressure, trend(product));
    }
    private Quote price(NpcContent content, NpcContent.ShopProduct product, Side side, int units, double standingMultiplier, Instant now) {
        Objects.requireNonNull(product); Objects.requireNonNull(side); Objects.requireNonNull(now);
        checkUnits(units); refreshPricing(content);
        String key = commodity(product); Stock current = stock.getOrDefault(key, new Stock(0, 0));
        double unit;
        if (side == Side.BUY) unit = product.basePrice() * standingMultiplier / product.amount();
        else {
            unit = product.sellPrice() * Math.clamp(2 - standingMultiplier, .5, 1.25) / product.amount();
            // A shared commodity cannot be bought cheaply at one faction and flipped to another.
            double cheapestAsk = cheapestAsks.getOrDefault(key, Double.POSITIVE_INFINITY);
            unit = Math.min(unit, cheapestAsk * .60);
            if (product.material() != null) unit = Math.min(unit, recipeSellCaps.getOrDefault(product.material(), Double.POSITIVE_INFINITY));
        }
        double total = 0;
        for (int index = 0; index < units; index++) {
            int at = current.pressure + (side == Side.BUY ? index : -index - 1);
            total += unit * multiplier(at);
        }
        long marks = side == Side.BUY ? (long) Math.ceil(total) : (long) Math.floor(total);
        if (!Double.isFinite(total) || marks < 0 || marks > 1_000_000_000L) throw new IllegalArgumentException("Vendor price is outside safe bounds");
        return new Quote(key, units, marks, current.revision, side, now.plusSeconds(30));
    }
    private static void checkUnits(int units) {
        if (units < 1 || units > 64) throw new IllegalArgumentException("Choose between 1 and 64 items.");
    }
    private void refreshPricing(NpcContent content) {
        Objects.requireNonNull(content);
        if (content == pricedContent) return;
        double bestStanding = content.factions().values().stream().flatMap(f -> f.tiers().stream())
            .filter(NpcContent.TierDefinition::canTrade).mapToDouble(NpcContent.TierDefinition::shopPriceMultiplier).min().orElse(.25);
        Map<String, Double> next = new HashMap<>();
        Map<org.bukkit.Material, Double> ingredients = new EnumMap<>(org.bukkit.Material.class);
        Set<org.bukkit.Material> sold = new HashSet<>();
        content.shops().values().stream().flatMap(s -> s.products().stream()).filter(p -> p.basePrice() > 0)
            .forEach(p -> {
                double ask = p.basePrice() * bestStanding / p.amount();
                next.merge(commodity(p), ask, Math::min);
                if (p.material() != null) ingredients.merge(p.material(), ask, Math::min);
            });
        content.shops().values().stream().flatMap(s -> s.products().stream()).filter(p -> p.material() != null && p.sellPrice() > 0)
            .forEach(p -> sold.add(p.material()));
        Map<org.bukkit.Material, Double> caps = NpcRecipePriceGuard.sellCaps(ingredients, sold, recipeRules);
        cheapestAsks = Map.copyOf(next); recipeSellCaps = caps; pricedContent = content;
    }
    static double multiplier(int pressure) { return 1 + Math.clamp(pressure, -PRESSURE_LIMIT, PRESSURE_LIMIT) / 4096.0; }
    synchronized String trend(NpcContent.ShopProduct product) {
        int pressure = stock.getOrDefault(commodity(product), new Stock(0, 0)).pressure;
        return (pressure > 128 ? "In demand" : pressure < -128 ? "Well supplied" : "Balanced") + " • " + Math.round(multiplier(pressure) * 100) + "% market rate";
    }
    synchronized Trade begin(AccountId account, String shop, String product, Quote quote, Instant now) {
        Objects.requireNonNull(account); Objects.requireNonNull(quote); Objects.requireNonNull(now);
        Stock current = stock.getOrDefault(quote.commodity, new Stock(0, 0));
        if (quote.marks <= 0 || !quote.expires.isAfter(now) || current.revision != quote.revision) throw new IllegalArgumentException("That quote changed. Open the shop again.");
        if (current.revision == Long.MAX_VALUE) throw new IllegalStateException("This commodity needs staff review before trading.");
        if (trades.values().stream().anyMatch(t -> t.pending() && (t.account.equals(account) || t.quote.commodity.equals(quote.commodity))))
            throw new IllegalArgumentException("A trade for this account or commodity is still being settled. Try again shortly, or ask staff to review it.");
        Trade trade = new Trade(UUID.randomUUID(), account, shop, product, quote, Phase.RESERVED);
        Map<UUID, Trade> next = new LinkedHashMap<>(trades); next.put(trade.id, trade); commit(stock, next); return trade;
    }
    synchronized Trade transition(UUID id, Phase expected, Phase nextPhase) {
        Trade before = require(id);
        if (before.phase != expected) throw new IllegalStateException("Vendor trade state changed");
        boolean allowed = expected == Phase.RESERVED && (nextPhase == Phase.PAID && before.quote.side == Side.BUY || nextPhase == Phase.INVENTORY && before.quote.side == Side.SELL)
            || expected == Phase.PAID && nextPhase == Phase.INVENTORY
            || expected == Phase.INVENTORY && nextPhase == Phase.ESCROWED && before.quote.side == Side.SELL
            || (expected == Phase.PAID || expected == Phase.INVENTORY) && nextPhase == Phase.REFUNDING && before.quote.side == Side.BUY;
        if (!allowed) throw new IllegalStateException("Invalid vendor trade transition");
        Trade next = before.phase(nextPhase); Map<UUID, Trade> entries = new LinkedHashMap<>(trades); entries.put(id, next); commit(stock, entries); return next;
    }
    synchronized void complete(UUID id) {
        Trade trade = require(id); if (trade.phase == Phase.COMPLETE) return;
        if (!(trade.quote.side == Side.BUY && trade.phase == Phase.INVENTORY || trade.quote.side == Side.SELL && trade.phase == Phase.ESCROWED)) throw new IllegalStateException("Inventory transfer is not confirmed");
        Stock before = stock.getOrDefault(trade.quote.commodity, new Stock(0, 0));
        if (before.revision != trade.quote.revision) throw new IllegalStateException("Vendor stock changed under an active trade");
        Map<String, Stock> stocks = new HashMap<>(stock);
        stocks.put(trade.quote.commodity, new Stock(Math.clamp(before.pressure + (trade.quote.side == Side.BUY ? trade.quote.units : -trade.quote.units), -PRESSURE_LIMIT, PRESSURE_LIMIT), Math.addExact(before.revision, 1)));
        Map<UUID, Trade> entries = new LinkedHashMap<>(trades); entries.put(id, trade.phase(Phase.COMPLETE)); commit(stocks, entries);
    }
    synchronized void cancel(UUID id) {
        Trade trade = require(id); if (trade.phase == Phase.CANCELLED) return;
        if (trade.phase == Phase.COMPLETE || trade.phase == Phase.ESCROWED) throw new IllegalStateException("Trade cannot be cancelled");
        Map<UUID, Trade> entries = new LinkedHashMap<>(trades); entries.put(id, trade.phase(Phase.CANCELLED)); commit(stock, entries);
    }
    synchronized Trade require(UUID id) { Trade trade = trades.get(id); if (trade == null) throw new IllegalArgumentException("Vendor trade not found"); return trade; }
    synchronized List<Trade> pending() { return trades.values().stream().filter(Trade::pending).toList(); }
    private void commit(Map<String, Stock> stocks, Map<UUID, Trade> entries) {
        Map<UUID, Trade> bounded = new LinkedHashMap<>(entries);
        int completed = (int) bounded.values().stream().filter(t -> !t.pending()).count();
        for (Iterator<Trade> iterator = bounded.values().iterator(); iterator.hasNext() && completed > 1000;) {
            if (!iterator.next().pending()) { iterator.remove(); completed--; }
        }
        YamlConfiguration yaml = new YamlConfiguration(); yaml.set("schema", 1);
        stocks.forEach((key, value) -> { yaml.set("stock." + key + ".pressure", value.pressure); yaml.set("stock." + key + ".revision", value.revision); });
        bounded.values().forEach(t -> {
            String p = "trades." + t.id; yaml.set(p + ".account", t.account.toString()); yaml.set(p + ".shop", t.shop); yaml.set(p + ".product", t.product);
            yaml.set(p + ".commodity", t.quote.commodity); yaml.set(p + ".units", t.quote.units); yaml.set(p + ".marks", t.quote.marks);
            yaml.set(p + ".revision", t.quote.revision); yaml.set(p + ".side", t.quote.side.name()); yaml.set(p + ".expires", t.quote.expires.toString()); yaml.set(p + ".phase", t.phase.name());
        });
        try { AtomicYamlFiles.save(yaml, file); }
        catch (java.io.IOException error) { throw new IllegalStateException("Vendor market save failed; no state published", error); }
        stock = new HashMap<>(stocks); trades = bounded;
    }
    private void load() {
        if (!file.exists()) return;
        try {
            YamlConfiguration yaml = new YamlConfiguration(); yaml.load(file);
            if (yaml.getInt("schema") != 1) throw new IllegalArgumentException("Unknown vendor schema");
            if (yaml.contains("stock") && !yaml.isConfigurationSection("stock") || yaml.contains("trades") && !yaml.isConfigurationSection("trades")) throw new IllegalArgumentException("Invalid vendor data");
            var stocks = yaml.getConfigurationSection("stock");
            if (stocks != null) for (String key : stocks.getKeys(false)) {
                if (!key.matches("[a-z0-9_-]{1,200}") || !stocks.isInt(key + ".pressure")) throw new IllegalArgumentException("Invalid commodity");
                int pressure = stocks.getInt(key + ".pressure"); long revision = number(stocks.get(key + ".revision"));
                if (Math.abs((long) pressure) > PRESSURE_LIMIT || revision < 0) throw new IllegalArgumentException("Invalid market pressure");
                stock.put(key, new Stock(pressure, revision));
            }
            var entries = yaml.getConfigurationSection("trades");
            Set<String> lockedCommodities = new HashSet<>(); Set<AccountId> lockedAccounts = new HashSet<>();
            if (entries != null) for (String key : entries.getKeys(false)) {
                var t = Objects.requireNonNull(entries.getConfigurationSection(key));
                String commodity = Objects.requireNonNull(t.getString("commodity"));
                long units = number(t.get("units")), marks = number(t.get("marks")), revision = number(t.get("revision"));
                if (!commodity.matches("[a-z0-9_-]{1,200}") || units < 1 || units > 64 || marks < 1 || marks > 1_000_000_000L || revision < 0) throw new IllegalArgumentException("Invalid vendor trade");
                Quote quote = new Quote(commodity, (int) units, marks, revision, Side.valueOf(t.getString("side", "")), Instant.parse(t.getString("expires", "")));
                Trade trade = new Trade(UUID.fromString(key), AccountId.parse(t.getString("account", "")), Objects.requireNonNull(t.getString("shop")), Objects.requireNonNull(t.getString("product")), quote, Phase.valueOf(t.getString("phase", "")));
                if (trade.shop.isBlank() || trade.product.isBlank() || trade.quote.side == Side.SELL && (trade.phase == Phase.PAID || trade.phase == Phase.REFUNDING)
                    || trade.quote.side == Side.BUY && trade.phase == Phase.ESCROWED) throw new IllegalArgumentException("Invalid vendor phase");
                if (trade.pending() && (!lockedAccounts.add(trade.account) || !lockedCommodities.add(commodity) || stock.getOrDefault(commodity, new Stock(0, 0)).revision != revision)) throw new IllegalArgumentException("Conflicting pending vendor trade");
                trades.put(trade.id, trade);
            }
        } catch (Exception error) { throw new IllegalStateException("Cannot load vendor market; refusing to reset prices or pending trades", error); }
    }
    private static long number(Object value) { if (!(value instanceof Integer) && !(value instanceof Long)) throw new IllegalArgumentException("Invalid market number"); return ((Number) value).longValue(); }
}
