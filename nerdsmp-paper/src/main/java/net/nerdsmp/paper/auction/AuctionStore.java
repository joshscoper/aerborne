package net.nerdsmp.paper.auction;

import net.nerdsmp.api.model.AccountId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Strict, versioned and atomically replaced seasonal auction journal. Never recreates a corrupt journal. */
public class AuctionStore {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_AUCTIONS = 100_000;
    public static final int MAX_AUDIT = 10_000;
    private static final int MAX_FILE_BYTES = 100 * 1024 * 1024;
    private final Path file;

    public AuctionStore(File file) {
        this.file = Objects.requireNonNull(file).toPath().toAbsolutePath().normalize();
    }

    public Snapshot load() throws IOException {
        if (!Files.exists(file)) return new Snapshot(Map.of(), List.of());
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) throw new IOException("Invalid auction journal path or size: " + file);
        try {
            String contents = Files.readString(file, StandardCharsets.UTF_8);
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setCodePointLimit(MAX_FILE_BYTES);
            new Yaml(new SafeConstructor(options)).load(contents);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().codePointLimit(MAX_FILE_BYTES);
            yaml.loadFromString(contents);
            keys(yaml, Set.of("schema-version", "auctions", "audit"));
            if (number(yaml, "schema-version") != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported auction schema");
            ConfigurationSection entries = section(yaml, "auctions");
            Map<UUID, Auction> auctions = new LinkedHashMap<>();
            for (String key : entries.getKeys(false)) {
                UUID id = UUID.fromString(key);
                ConfigurationSection value = section(entries, key);
                keys(value, Set.of("seller", "starting-bid", "buy-now", "created-at", "ends-at", "tax-percent", "status", "revision",
                    "high-bidder", "high-bid", "pending", "settlement-id", "review-reason"));
                Auction.PendingBid pending = null;
                if (value.get("pending") != null) {
                    ConfigurationSection bid = section(value, "pending");
                    keys(bid, Set.of("operation-id", "bidder", "amount", "previous-bidder", "previous-amount", "buy-now",
                        "debit-confirmed", "refund-confirmed"));
                    pending = new Auction.PendingBid(uuidRequired(bid, "operation-id"), AccountId.parse(string(bid, "bidder")),
                        number(bid, "amount"), account(bid, "previous-bidder"), number(bid, "previous-amount"),
                        bool(bid, "buy-now"), bool(bid, "debit-confirmed"), bool(bid, "refund-confirmed"));
                }
                auctions.put(id, new Auction(id, AccountId.parse(string(value, "seller")), number(value, "starting-bid"),
                    value.get("buy-now") == null ? null : number(value, "buy-now"), instant(value, "created-at"), instant(value, "ends-at"),
                    Math.toIntExact(number(value, "tax-percent")), Auction.Status.valueOf(string(value, "status")), number(value, "revision"),
                    account(value, "high-bidder"), number(value, "high-bid"), pending, uuid(value, "settlement-id"), string(value, "review-reason")));
            }
            if (!(yaml.get("audit") instanceof List<?> rawEvents)) throw new IllegalArgumentException("Missing auction audit");
            List<Audit> audit = new ArrayList<>();
            for (Object raw : rawEvents) {
                if (!(raw instanceof Map<?, ?> value) || !value.keySet().equals(Set.of("id", "at", "auction-id", "actor", "action", "detail"))) {
                    throw new IllegalArgumentException("Invalid auction audit fields");
                }
                audit.add(new Audit(UUID.fromString(mapString(value, "id")), Instant.parse(mapString(value, "at")),
                    UUID.fromString(mapString(value, "auction-id")), mapString(value, "actor"), mapString(value, "action"), mapString(value, "detail")));
            }
            Snapshot snapshot = new Snapshot(auctions, audit);
            validate(snapshot);
            return snapshot;
        } catch (InvalidConfigurationException | RuntimeException exception) {
            throw new IOException("Invalid auction journal: " + file + ": " + exception.getMessage(), exception);
        }
    }

    public void save(Snapshot snapshot) throws IOException {
        try { validate(snapshot); }
        catch (RuntimeException exception) { throw new IOException("Refusing invalid auction journal", exception); }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        ConfigurationSection entries = yaml.createSection("auctions");
        snapshot.auctions().values().stream().sorted(Comparator.comparing(value -> value.id().toString())).forEach(auction -> {
            ConfigurationSection value = entries.createSection(auction.id().toString());
            value.set("seller", auction.seller().toString());
            value.set("starting-bid", auction.startingBid());
            value.set("buy-now", auction.buyNowPrice());
            value.set("created-at", auction.createdAt().toString());
            value.set("ends-at", auction.endsAt().toString());
            value.set("tax-percent", auction.taxPercent());
            value.set("status", auction.status().name());
            value.set("revision", auction.revision());
            value.set("high-bidder", text(auction.highBidder()));
            value.set("high-bid", auction.highBid());
            value.set("settlement-id", text(auction.settlementId()));
            value.set("review-reason", auction.reviewReason());
            if (auction.pending() != null) {
                Auction.PendingBid pending = auction.pending();
                ConfigurationSection bid = value.createSection("pending");
                bid.set("operation-id", pending.operationId().toString());
                bid.set("bidder", pending.bidder().toString());
                bid.set("amount", pending.amount());
                bid.set("previous-bidder", text(pending.previousBidder()));
                bid.set("previous-amount", pending.previousAmount());
                bid.set("buy-now", pending.buyNow());
                bid.set("debit-confirmed", pending.debitConfirmed());
                bid.set("refund-confirmed", pending.refundConfirmed());
            }
        });
        List<Map<String, Object>> audit = new ArrayList<>();
        for (Audit event : snapshot.audit()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", event.id().toString());
            value.put("at", event.at().toString());
            value.put("auction-id", event.auctionId().toString());
            value.put("actor", event.actor());
            value.put("action", event.action());
            value.put("detail", event.detail());
            audit.add(value);
        }
        yaml.set("audit", audit);
        byte[] bytes = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Auction journal exceeds its size limit");
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".pending");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* Do not obscure a successful commit. */ }
        }
    }

    private static void validate(Snapshot snapshot) {
        require(snapshot.auctions().size() <= MAX_AUCTIONS && snapshot.audit().size() <= MAX_AUDIT, "Auction journal limits exceeded");
        snapshot.auctions().forEach((id, auction) -> require(id.equals(auction.id()), "Auction ID does not match its key"));
        Set<UUID> ids = new HashSet<>();
        for (Audit event : snapshot.audit()) {
            require(ids.add(event.id()) && snapshot.auctions().containsKey(event.auctionId()), "Invalid auction audit reference");
            require(!event.actor().isBlank() && event.actor().length() <= 64 && !event.action().isBlank()
                && event.action().length() <= 64 && event.detail().length() <= 512, "Invalid auction audit text");
        }
    }

    private static void keys(ConfigurationSection value, Set<String> allowed) {
        require(allowed.containsAll(value.getKeys(false)), "Unknown auction journal field");
    }
    private static ConfigurationSection section(ConfigurationSection value, String key) {
        ConfigurationSection result = value.getConfigurationSection(key);
        if (result == null) throw new IllegalArgumentException("Missing auction section: " + key);
        return result;
    }
    private static String string(ConfigurationSection value, String key) {
        if (!(value.get(key) instanceof String result)) throw new IllegalArgumentException("Expected text: " + key);
        return result;
    }
    private static long number(ConfigurationSection value, String key) {
        Object result = value.get(key);
        if (!(result instanceof Integer) && !(result instanceof Long)) throw new IllegalArgumentException("Expected integer: " + key);
        return ((Number) result).longValue();
    }
    private static boolean bool(ConfigurationSection value, String key) {
        if (!(value.get(key) instanceof Boolean result)) throw new IllegalArgumentException("Expected boolean: " + key);
        return result;
    }
    private static UUID uuidRequired(ConfigurationSection value, String key) { return UUID.fromString(string(value, key)); }
    private static UUID uuid(ConfigurationSection value, String key) { return value.get(key) == null ? null : uuidRequired(value, key); }
    private static AccountId account(ConfigurationSection value, String key) { return value.get(key) == null ? null : AccountId.parse(string(value, key)); }
    private static Instant instant(ConfigurationSection value, String key) { return Instant.parse(string(value, key)); }
    private static String mapString(Map<?, ?> value, String key) {
        if (!(value.get(key) instanceof String result)) throw new IllegalArgumentException("Expected audit text: " + key);
        return result;
    }
    private static String text(Object value) { return value == null ? null : value.toString(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }

    public record Audit(UUID id, Instant at, UUID auctionId, String actor, String action, String detail) {
        public Audit { Objects.requireNonNull(id); Objects.requireNonNull(at); Objects.requireNonNull(auctionId);
            Objects.requireNonNull(actor); Objects.requireNonNull(action); Objects.requireNonNull(detail); }
    }
    public record Snapshot(Map<UUID, Auction> auctions, List<Audit> audit) {
        public Snapshot { auctions = Map.copyOf(auctions); audit = List.copyOf(audit); }
    }
}
