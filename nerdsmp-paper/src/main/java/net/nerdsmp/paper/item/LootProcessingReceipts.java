package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

/** One bounded receipt per account. Interrupted item writes require review, never automatic replay. */
final class LootProcessingReceipts {
    enum Phase { PREPARED, APPLYING, COMPLETE, DECLINED, REFUNDED }
    record Receipt(UUID operation, AccountId account, String action, long marks, int dust, int count,
                   Instant created, List<String> instances, List<String> before, List<String> after, Phase phase) {
        Receipt {
            Objects.requireNonNull(operation); Objects.requireNonNull(account); Objects.requireNonNull(created); Objects.requireNonNull(phase);
            instances = List.copyOf(instances); before = List.copyOf(before); after = List.copyOf(after);
            if (!Set.of("held", "all", "scrap").contains(action) || marks < 0 || marks > 1_000_000_000_000L
                || dust < 0 || dust > 100_000 || count < 1 || count > 36 || instances.size() != count
                || new HashSet<>(instances).size() != count || before.size() != 36 || after.size() != 36)
                throw new IllegalArgumentException("Invalid loot receipt");
            instances.forEach(UUID::fromString);
            if (java.util.stream.Stream.concat(before.stream(), after.stream()).anyMatch(value -> value.length() > 24_000))
                throw new IllegalArgumentException("Oversized loot receipt item");
        }
        boolean pending() { return phase == Phase.PREPARED || phase == Phase.APPLYING; }
        Receipt at(Phase next) { return new Receipt(operation, account, action, marks, dust, count, created, instances, before, after, next); }
    }

    private final File directory;
    private final Map<AccountId, Receipt> cache = new LinkedHashMap<>(128, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<AccountId, Receipt> eldest) { return size() > 128; }
    };
    LootProcessingReceipts(File directory) { this.directory = directory; }

    synchronized Optional<Receipt> find(AccountId account) {
        if (cache.containsKey(account)) return Optional.of(cache.get(account));
        File file = file(account);
        if (!file.exists()) return Optional.empty();
        try {
            if (!file.isFile() || file.length() > 2_000_000) throw new IllegalArgumentException("Invalid receipt file");
            String text = Files.readString(file.toPath());
            LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0); options.setCodePointLimit(2_000_000);
            new Yaml(new SafeConstructor(options)).load(text);
            var yaml = new YamlConfiguration(); yaml.loadFromString(text);
            if (!yaml.isInt("schema") || yaml.getInt("schema") != 1 || !account.toString().equals(yaml.getString("account"))
                || !(yaml.get("marks") instanceof Integer || yaml.get("marks") instanceof Long)
                || !yaml.isInt("dust") || !yaml.isInt("count")) throw new IllegalArgumentException("Invalid receipt fields");
            Receipt receipt = new Receipt(UUID.fromString(yaml.getString("operation")), account, yaml.getString("action"), yaml.getLong("marks"),
                yaml.getInt("dust"), yaml.getInt("count"), Instant.parse(yaml.getString("created")),
                strings(yaml, "instances"), strings(yaml, "before"), strings(yaml, "after"), Phase.valueOf(yaml.getString("phase")));
            cache.put(account, receipt);
            return Optional.of(receipt);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot read loot receipt; refusing to replace " + file.getName(), failure);
        }
    }

    synchronized void begin(Receipt receipt) {
        if (receipt.phase() != Phase.PREPARED || find(receipt.account()).filter(Receipt::pending).isPresent())
            throw new IllegalStateException("An item-processing receipt already needs review");
        save(receipt);
    }

    synchronized void advance(Receipt receipt, Phase phase) {
        Receipt current = find(receipt.account()).orElseThrow();
        if (!current.equals(receipt) || !current.pending()
            || !(current.phase() == Phase.PREPARED && Set.of(Phase.APPLYING, Phase.DECLINED, Phase.REFUNDED).contains(phase)
                || current.phase() == Phase.APPLYING && Set.of(Phase.COMPLETE, Phase.REFUNDED).contains(phase)))
            throw new IllegalStateException("Invalid loot receipt transition");
        save(receipt.at(phase));
    }

    private void save(Receipt receipt) {
        var yaml = new YamlConfiguration();
        yaml.set("schema", 1); yaml.set("operation", receipt.operation().toString()); yaml.set("account", receipt.account().toString());
        yaml.set("action", receipt.action()); yaml.set("marks", receipt.marks()); yaml.set("dust", receipt.dust()); yaml.set("count", receipt.count());
        yaml.set("created", receipt.created().toString()); yaml.set("phase", receipt.phase().name());
        yaml.set("instances", receipt.instances()); yaml.set("before", receipt.before()); yaml.set("after", receipt.after());
        try { AtomicYamlFiles.save(yaml, file(receipt.account())); }
        catch (Exception failure) { throw new IllegalStateException("Could not save loot receipt " + receipt.operation(), failure); }
        cache.put(receipt.account(), receipt);
    }

    private File file(AccountId account) { return new File(directory, account + ".yml"); }
    private static List<String> strings(YamlConfiguration yaml, String key) {
        List<?> values = yaml.getList(key);
        if (values == null || values.stream().anyMatch(value -> !(value instanceof String))) throw new IllegalArgumentException("Invalid receipt list");
        return values.stream().map(String.class::cast).toList();
    }
}
