package net.nerdsmp.paper.economy;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Recoverable debit/credit receipts. No two-file transfer primitive or ambiguous automatic refunds. */
public final class PlayerPayments {
    public static final long MAX_PAYMENT = 1_000_000_000L;
    public enum Phase { PREPARED, DEBITED, COMPLETE, DECLINED }
    public record Payment(UUID id, AccountId source, AccountId destination, long amount, Instant created, Phase phase) {
        public Payment {
            Objects.requireNonNull(id); Objects.requireNonNull(source); Objects.requireNonNull(destination); Objects.requireNonNull(created); Objects.requireNonNull(phase);
            if (source.equals(destination) || amount < 1 || amount > MAX_PAYMENT) throw new IllegalArgumentException("Invalid payment");
        }
        public boolean pending() { return phase == Phase.PREPARED || phase == Phase.DEBITED; }
        Payment at(Phase next) { return new Payment(id, source, destination, amount, created, next); }
    }
    private final Store store;
    private final EconomyService economy;
    private final Map<UUID, Payment> payments;
    private final Map<UUID, CompletableFuture<Payment>> running = new HashMap<>();
    public PlayerPayments(Store store, EconomyService economy) {
        this.store = store; this.economy = economy; payments = new HashMap<>(store.load());
        Set<AccountId> pending = new HashSet<>();
        for (Payment payment : payments.values()) if (payment.pending() && !pending.add(payment.source())) throw new IllegalStateException("Multiple pending payments for one source; review payment receipts");
    }
    public synchronized List<Payment> all() { return List.copyOf(payments.values()); }
    public synchronized Optional<Payment> find(UUID id) { return Optional.ofNullable(payments.get(id)); }
    public synchronized CompletionStage<Payment> pay(UUID id, AccountId source, AccountId destination, long amount) {
        Payment existing = payments.get(id);
        if (existing != null) {
            if (!existing.source().equals(source) || !existing.destination().equals(destination) || existing.amount() != amount) throw new IllegalArgumentException("Payment receipt does not match this request");
            return resume(id);
        }
        Payment payment = new Payment(id, source, destination, amount, Instant.now(), Phase.PREPARED);
        if (payments.values().stream().anyMatch(p -> p.source().equals(source) && p.pending())) throw new IllegalArgumentException("Your previous payment is still settling. Check /pay status; do not send it again.");
        if (payments.size() >= 50_000) throw new IllegalStateException("Payment archive is full; contact staff");
        commit(payment);
        return resume(id);
    }
    public synchronized CompletionStage<Payment> resume(UUID id) {
        Payment payment = Optional.ofNullable(payments.get(id)).orElseThrow(() -> new IllegalArgumentException("Unknown payment receipt"));
        if (!payment.pending()) return CompletableFuture.completedFuture(payment);
        if (running.containsKey(id)) return running.get(id);
        CompletableFuture<Payment> result = new CompletableFuture<>(); running.put(id, result);
        try {
            settle(payment).whenComplete((settled, error) -> {
                synchronized (this) { running.remove(id); }
                if (error == null) result.complete(settled); else result.completeExceptionally(error);
            });
        } catch (RuntimeException error) { running.remove(id); result.completeExceptionally(error); }
        return result;
    }
    private CompletionStage<Payment> settle(Payment payment) {
        var reason = new EconomyService.TransactionReason("player.payment", "Receipt " + payment.id());
        if (payment.phase() == Phase.PREPARED) {
            return economy.debit(payment.source(), payment.amount(), reason, "payment:" + payment.id() + ":debit").thenCompose(result -> {
                if (result.status() == EconomyService.TransactionResult.Status.INSUFFICIENT_FUNDS) return CompletableFuture.completedFuture(commit(payment.at(Phase.DECLINED)));
                requireApplied(result);
                return settle(commit(payment.at(Phase.DEBITED)));
            });
        }
        return economy.credit(payment.destination(), payment.amount(), reason, "payment:" + payment.id() + ":credit")
            .thenApply(result -> { requireApplied(result); return commit(payment.at(Phase.COMPLETE)); });
    }
    private static void requireApplied(EconomyService.TransactionResult result) {
        if (result == null || result.status() != EconomyService.TransactionResult.Status.APPLIED && result.status() != EconomyService.TransactionResult.Status.DUPLICATE)
            throw new IllegalStateException("Payment is awaiting reconciliation; keep its receipt");
    }
    private synchronized Payment commit(Payment payment) { store.save(payment); payments.put(payment.id(), payment); return payment; }

    /** One atomically replaced file per receipt avoids rewriting the whole payment history on every send. */
    public static class Store {
        private final File directory;
        public Store(File directory) { this.directory = directory; }
        public Map<UUID, Payment> load() {
            if (!directory.exists()) return Map.of();
            File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null || files.length > 50_000) throw new IllegalStateException("Invalid or oversized payment archive");
            Map<UUID, Payment> result = new HashMap<>();
            for (File file : files) try {
                if (!file.isFile() || file.length() > 4096) throw new IllegalArgumentException("Invalid payment file");
                UUID id = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                String text = Files.readString(file.toPath());
                LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(0); options.setCodePointLimit(4096);
                new Yaml(new SafeConstructor(options)).load(text);
                var yaml = new YamlConfiguration(); yaml.loadFromString(text);
                if (!yaml.getKeys(false).equals(Set.of("schema", "source", "destination", "amount", "created", "phase"))
                    || !yaml.isInt("schema") || yaml.getInt("schema") != 1 || !(yaml.get("amount") instanceof Integer || yaml.get("amount") instanceof Long)) throw new IllegalArgumentException("Invalid payment fields");
                result.put(id, new Payment(id, AccountId.parse(yaml.getString("source")), AccountId.parse(yaml.getString("destination")), yaml.getLong("amount"),
                    Instant.parse(yaml.getString("created")), Phase.valueOf(yaml.getString("phase"))));
            } catch (Exception error) { throw new IllegalStateException("Cannot read payment receipt; refusing to replace " + file.getName(), error); }
            return Map.copyOf(result);
        }
        public void save(Payment payment) {
            var yaml = new YamlConfiguration(); yaml.set("schema", 1); yaml.set("source", payment.source().toString()); yaml.set("destination", payment.destination().toString());
            yaml.set("amount", payment.amount()); yaml.set("created", payment.created().toString()); yaml.set("phase", payment.phase().name());
            try { AtomicYamlFiles.save(yaml, new File(directory, payment.id() + ".yml")); }
            catch (Exception error) { throw new IllegalStateException("Could not save payment receipt " + payment.id(), error); }
        }
    }
}
