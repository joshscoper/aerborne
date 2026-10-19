package net.nerdsmp.paper.job;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerOrderService {
    private final EconomyService economy;
    private final boolean enabled;
    private final Map<String, OrderDefinition> definitions;
    private final Map<String, Long> fulfilled = new ConcurrentHashMap<>();
    private final Set<String> idempotency = ConcurrentHashMap.newKeySet();

    public ServerOrderService(JavaPlugin plugin, EconomyService economy) {
        this.economy = economy;
        enabled = !(plugin instanceof net.nerdsmp.paper.NerdSmpPlugin nerd && nerd.skyblockEnabled());
        definitions = enabled ? load(plugin) : Map.of();
    }

    public List<OrderSnapshot> activeOrders() {
        return definitions.values().stream()
            .map(definition -> new OrderSnapshot(
                definition.id,
                definition.title,
                definition.material,
                definition.unitSize,
                definition.marksPerUnit,
                Math.max(0L, definition.itemCap - fulfilled.getOrDefault(definition.id, 0L))
            ))
            .filter(snapshot -> snapshot.remainingItems > 0L)
            .sorted(Comparator.comparing(OrderSnapshot::id))
            .toList();
    }

    public OrderSnapshot order(String id) {
        return activeOrders().stream().filter(order -> order.id.equals(id.toLowerCase(Locale.ROOT))).findFirst().orElse(null);
    }

    public synchronized CompletionStage<FulfillmentResult> fulfill(
        AccountId accountId,
        String rawId,
        long itemAmount,
        String idempotencyKey
    ) {
        String id = rawId.toLowerCase(Locale.ROOT);
        OrderDefinition definition = definitions.get(id);
        if (definition == null) {
            return completed(Status.UNKNOWN_ORDER, 0L, 0L);
        }
        if (itemAmount <= 0L || itemAmount % definition.unitSize != 0L) {
            return completed(Status.INVALID_AMOUNT, 0L, 0L);
        }
        if (!idempotency.add(idempotencyKey)) {
            return completed(Status.DUPLICATE, 0L, 0L);
        }
        long current = fulfilled.getOrDefault(id, 0L);
        if (current + itemAmount > definition.itemCap) {
            idempotency.remove(idempotencyKey);
            return completed(Status.CAP_REACHED, 0L, definition.itemCap - current);
        }
        long marks = Math.multiplyExact(itemAmount / definition.unitSize, definition.marksPerUnit);
        fulfilled.put(id, current + itemAmount);
        return economy.credit(
            accountId,
            marks,
            new EconomyService.TransactionReason("server_order." + id, definition.title),
            "order:" + idempotencyKey
        ).thenApply(result -> {
            if (result.status() == EconomyService.TransactionResult.Status.APPLIED) {
                return new FulfillmentResult(Status.APPLIED, marks, definition.itemCap - current - itemAmount);
            }
            synchronized (this) {
                fulfilled.put(id, current);
            }
            return new FulfillmentResult(Status.ECONOMY_FAILURE, 0L, definition.itemCap - current);
        });
    }

    private CompletionStage<FulfillmentResult> completed(Status status, long marks, long remaining) {
        return CompletableFuture.completedFuture(new FulfillmentResult(status, marks, remaining));
    }

    private Map<String, OrderDefinition> load(JavaPlugin plugin) {
        File target = new File(plugin.getDataFolder(), "content/orders.yml");
        if (!target.exists()) {
            plugin.saveResource("content/orders.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(target);
        ConfigurationSection orders = yaml.getConfigurationSection("orders");
        if (orders == null) {
            throw new IllegalStateException("content/orders.yml is missing the orders section");
        }
        Map<String, OrderDefinition> loaded = new HashMap<>();
        for (String id : orders.getKeys(false)) {
            ConfigurationSection section = orders.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            Material material = Material.matchMaterial(section.getString("material", ""));
            if (material == null || material.isAir()) {
                throw new IllegalStateException("Invalid server order material for " + id);
            }
            loaded.put(id.toLowerCase(Locale.ROOT), new OrderDefinition(
                id.toLowerCase(Locale.ROOT),
                section.getString("title", id),
                material,
                section.getLong("unit-size", 1L),
                section.getLong("marks-per-unit", 1L),
                section.getLong("item-cap", 1L)
            ));
        }
        return Map.copyOf(loaded);
    }

    public enum Status {
        APPLIED,
        UNKNOWN_ORDER,
        INVALID_AMOUNT,
        CAP_REACHED,
        DUPLICATE,
        ECONOMY_FAILURE
    }

    public record FulfillmentResult(Status status, long marks, long remainingItems) {
    }

    public record OrderSnapshot(
        String id,
        String title,
        Material material,
        long unitSize,
        long marksPerUnit,
        long remainingItems
    ) {
    }

    private record OrderDefinition(
        String id,
        String title,
        Material material,
        long unitSize,
        long marksPerUnit,
        long itemCap
    ) {
        private OrderDefinition {
            if (unitSize <= 0L || marksPerUnit <= 0L || itemCap <= 0L || itemCap % unitSize != 0L) {
                throw new IllegalArgumentException("Invalid order limits for " + id);
            }
        }
    }
}
