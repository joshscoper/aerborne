package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

final class NpcShopLedger implements AutoCloseable {
    private final File file;
    private final Supplier<LocalDate> today;
    private final Map<SaleKey, DailySale> sales = new HashMap<>();
    private final Map<UUID, Reservation> reservations = new HashMap<>();

    NpcShopLedger(JavaPlugin plugin, String seasonId) {
        this(new File(plugin.getDataFolder(), "npc-shop-sales-"
            + seasonId.replaceAll("[^a-zA-Z0-9_-]", "_") + ".yml"),
            () -> LocalDate.now(ZoneOffset.UTC));
    }

    NpcShopLedger(File file, Supplier<LocalDate> today) {
        this.file = file;
        this.today = today;
        load();
    }

    synchronized int remaining(AccountId account, String shopId, String productId, int limit) {
        SaleKey key = new SaleKey(account, shopId, productId);
        DailySale sale = current(key);
        return Math.max(0, limit - sale.bundles);
    }

    synchronized boolean reserve(AccountId account, String shopId, String productId, int limit) {
        SaleKey key = new SaleKey(account, shopId, productId);
        DailySale previous = current(key);
        if (previous.bundles >= limit) {
            return false;
        }
        sales.put(key, new DailySale(previous.date, previous.bundles + 1));
        try {
            save();
            return true;
        } catch (RuntimeException exception) {
            sales.put(key, previous);
            throw exception;
        }
    }

    /** One configured sell bundle, durably linked to its market transaction. */
    synchronized boolean reserve(UUID tradeId, AccountId account, String shopId, String productId, int limit) {
        Objects.requireNonNull(tradeId);
        SaleKey key = new SaleKey(account, shopId, productId);
        Reservation existing = reservations.get(tradeId);
        if (existing != null) {
            if (!existing.key.equals(key)) throw new IllegalArgumentException("Vendor reservation identity changed");
            return true;
        }
        DailySale previous = current(key);
        if (previous.bundles >= limit) return false;
        sales.put(key, new DailySale(previous.date, previous.bundles + 1));
        reservations.put(tradeId, new Reservation(key, previous.date));
        try { save(); return true; }
        catch (RuntimeException error) { sales.put(key, previous); reservations.remove(tradeId); throw error; }
    }

    /** Releases only a known new-style reservation. Never guesses about legacy bundle counts. */
    synchronized boolean release(UUID tradeId) {
        Reservation reservation = reservations.get(tradeId);
        if (reservation == null) return false;
        DailySale previous = sales.get(reservation.key);
        if (previous != null && previous.date.equals(reservation.date)) {
            if (previous.bundles < 1) throw new IllegalStateException("Vendor reservation exceeds its saved quota");
            sales.put(reservation.key, new DailySale(previous.date, previous.bundles - 1));
        }
        reservations.remove(tradeId);
        try { save(); return true; }
        catch (RuntimeException error) {
            if (previous != null) sales.put(reservation.key, previous);
            else sales.remove(reservation.key);
            reservations.put(tradeId, reservation);
            throw error;
        }
    }

    synchronized void rollback(AccountId account, String shopId, String productId) {
        SaleKey key = new SaleKey(account, shopId, productId);
        DailySale current = current(key);
        sales.put(key, new DailySale(current.date, Math.max(0, current.bundles - 1)));
        try { save(); }
        catch (RuntimeException error) { sales.put(key, current); throw error; }
    }

    private DailySale current(SaleKey key) {
        LocalDate date = today.get();
        DailySale sale = sales.get(key);
        if (sale == null || !sale.date.equals(date)) {
            sale = new DailySale(date, 0);
            sales.put(key, sale);
        }
        return sale;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(file); }
        catch (Exception error) { throw new IllegalStateException("Cannot load vendor quotas; refusing to reset sale limits", error); }
        if (yaml.contains("schema") && yaml.getInt("schema") != 2) throw new IllegalStateException("Unknown vendor quota schema");
        ConfigurationSection root = yaml.getConfigurationSection("sales");
        if (yaml.contains("sales") && root == null) throw new IllegalStateException("Invalid vendor quota data");
        if (root != null) for (String rawAccount : root.getKeys(false)) {
            try {
                AccountId account = new AccountId(UUID.fromString(rawAccount));
                ConfigurationSection shops = root.getConfigurationSection(rawAccount);
                if (shops == null) {
                    throw new IllegalArgumentException("Invalid account quota section");
                }
                for (String shop : shops.getKeys(false)) {
                    ConfigurationSection products = shops.getConfigurationSection(shop);
                    if (products == null) {
                        throw new IllegalArgumentException("Invalid shop quota section");
                    }
                    for (String product : products.getKeys(false)) {
                        ConfigurationSection section = products.getConfigurationSection(product);
                        if (section == null) {
                            throw new IllegalArgumentException("Invalid product quota section");
                        }
                        LocalDate date = LocalDate.parse(section.getString("date", today.get().toString()));
                        if (!section.isInt("bundles")) throw new IllegalArgumentException("Invalid saved bundle count");
                        int bundles = section.getInt("bundles");
                        if (bundles < 0 || bundles > 1_000_000) throw new IllegalArgumentException("Invalid saved bundle count");
                        if (date.equals(today.get()) && bundles > 0) {
                            sales.put(new SaleKey(account, shop, product), new DailySale(date, bundles));
                        }
                    }
                }
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException("Cannot load vendor quotas; refusing to reset sale limits", error);
            }
        }
        ConfigurationSection receipts = yaml.getConfigurationSection("reservations");
        if (yaml.contains("reservations") && receipts == null) throw new IllegalStateException("Invalid vendor quota reservations");
        if (receipts != null) for (String rawId : receipts.getKeys(false)) {
            try {
                ConfigurationSection receipt = Objects.requireNonNull(receipts.getConfigurationSection(rawId));
                UUID id = UUID.fromString(rawId);
                AccountId account = AccountId.parse(receipt.getString("account", ""));
                String shop = Objects.requireNonNull(receipt.getString("shop"));
                String product = Objects.requireNonNull(receipt.getString("product"));
                if (!shop.matches("[a-z0-9_-]{1,96}") || !product.matches("[a-z0-9_-]{1,96}")) throw new IllegalArgumentException("Invalid reservation identity");
                LocalDate date = LocalDate.parse(receipt.getString("date", ""));
                if (date.equals(today.get())) reservations.put(id, new Reservation(new SaleKey(account, shop, product), date));
            } catch (RuntimeException error) { throw new IllegalStateException("Cannot load vendor quota reservations", error); }
        }
        Map<SaleKey, Integer> reserved = new HashMap<>();
        reservations.values().forEach(r -> reserved.merge(r.key, 1, Integer::sum));
        reserved.forEach((key, count) -> {
            DailySale sale = sales.get(key);
            if (sale == null || sale.bundles < count) throw new IllegalStateException("Vendor reservations exceed saved quotas");
        });
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 2);
        LocalDate date = today.get();
        sales.entrySet().stream()
            .filter(entry -> entry.getValue().date.equals(date) && entry.getValue().bundles > 0)
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                SaleKey key = entry.getKey();
                String path = "sales." + key.account + "." + key.shopId + "." + key.productId;
                yaml.set(path + ".date", entry.getValue().date.toString());
                yaml.set(path + ".bundles", entry.getValue().bundles);
            });
        reservations.entrySet().stream().filter(entry -> entry.getValue().date.equals(date))
            .sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                Reservation reservation = entry.getValue();
                String path = "reservations." + entry.getKey();
                yaml.set(path + ".account", reservation.key.account.toString());
                yaml.set(path + ".shop", reservation.key.shopId);
                yaml.set(path + ".product", reservation.key.productId);
                yaml.set(path + ".date", reservation.date.toString());
            });
        try {
            AtomicYamlFiles.save(yaml, file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist NPC shop sale limits", exception);
        }
        reservations.entrySet().removeIf(entry -> !entry.getValue().date.equals(date));
    }

    @Override
    public synchronized void close() {
        save();
    }

    private record SaleKey(AccountId account, String shopId, String productId)
        implements Comparable<SaleKey> {
        @Override
        public int compareTo(SaleKey other) {
            return (account + "/" + shopId + "/" + productId)
                .compareTo(other.account + "/" + other.shopId + "/" + other.productId);
        }
    }

    private record DailySale(LocalDate date, int bundles) {
    }
    private record Reservation(SaleKey key, LocalDate date) { }
}
