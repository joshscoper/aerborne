package net.nerdsmp.paper.market;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.MarketService.*;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** One atomic journal snapshot per season. A malformed file must never become an empty market. */
public class MarketStore {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ITEM_LENGTH = 4 * 1024 * 1024;
    private final Path file;

    public MarketStore(File file) {
        this.file = Objects.requireNonNull(file).toPath().toAbsolutePath().normalize();
    }

    public Snapshot load() throws IOException {
        if (!Files.exists(file)) return new Snapshot(Map.of(), Map.of(), List.of());
        if (!Files.isRegularFile(file)) throw new IOException("Market path is not a regular file: " + file);
        YamlConfiguration yaml = new YamlConfiguration();
        // Match the strict validation pass, independently of future Paper option defaults.
        yaml.options().codePointLimit(Integer.MAX_VALUE);
        try {
            String contents = Files.readString(file, StandardCharsets.UTF_8);
            LoaderOptions strict = new LoaderOptions();
            strict.setAllowDuplicateKeys(false);
            strict.setMaxAliasesForCollections(0);
            strict.setCodePointLimit(Integer.MAX_VALUE);
            validateOptionalVenues(new Yaml(new SafeConstructor(strict)).load(contents));
            yaml.loadFromString(contents);
            if (number(yaml, "schema-version") != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported market schema");
            Map<UUID, Stall> stalls = new LinkedHashMap<>();
            ConfigurationSection stallSection = section(yaml, "stalls");
            for (String key : stallSection.getKeys(false)) {
                ConfigurationSection value = section(stallSection, key);
                UUID id = UUID.fromString(key);
                stalls.put(id, new Stall(id, AccountId.parse(string(value, "owner")), new StallLocation(
                    UUID.fromString(string(value, "world")), integer(value, "x"), integer(value, "y"), integer(value, "z"))));
            }
            Map<UUID, StoredEntry> entries = new LinkedHashMap<>();
            ConfigurationSection entrySection = section(yaml, "entries");
            for (String key : entrySection.getKeys(false)) {
                ConfigurationSection value = section(entrySection, key);
                UUID id = UUID.fromString(key);
                Listing<String> listing = new Listing<>(id, AccountId.parse(string(value, "seller")),
                    UUID.fromString(string(value, "stall")), string(value, "item"), number(value, "price"),
                    Status.valueOf(string(value, "status")), account(value, "reserved-by"), instant(value, "reserved-until"));
                Entry<String> entry = new Entry<>(listing, number(value, "listing-fee"), integer(value, "tax-percent"),
                    uuid(value, "checkout-id"), uuid(value, "delivery-id"), account(value, "recipient"),
                    ReviewKind.valueOf(string(value, "review-kind")), string(value, "review-reason"));
                entries.put(id, new StoredEntry(entry, string(value, "checksum"), bool(value, "debit-confirmed"),
                    bool(value, "payout-confirmed"), value.contains("venue")
                        ? Venue.valueOf(string(value, "venue")) : Venue.STORE));
            }
            Object rawAudit = yaml.get("audit");
            if (!(rawAudit instanceof List<?> values)) throw new IllegalArgumentException("Missing audit history");
            List<Audit> audit = new ArrayList<>();
            for (Object raw : values) {
                if (!(raw instanceof Map<?, ?> value)) throw new IllegalArgumentException("Invalid audit entry");
                audit.add(new Audit(UUID.fromString(mapString(value, "id")), Instant.parse(mapString(value, "at")),
                    value.get("listing-id") == null ? null : UUID.fromString(mapString(value, "listing-id")),
                    mapString(value, "actor"), mapString(value, "action"), mapString(value, "detail")));
            }
            Snapshot result = new Snapshot(stalls, entries, audit);
            validate(result);
            return result;
        } catch (InvalidConfigurationException | RuntimeException exception) {
            throw new IOException("Invalid market journal: " + file + ": " + exception.getMessage(), exception);
        }
    }

    public void save(Snapshot snapshot) throws IOException {
        try {
            validate(snapshot);
        } catch (RuntimeException exception) {
            throw new IOException("Refusing invalid market snapshot", exception);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        ConfigurationSection stalls = yaml.createSection("stalls");
        snapshot.stalls().values().stream().sorted(Comparator.comparing(stall -> stall.id().toString())).forEach(stall -> {
            ConfigurationSection value = stalls.createSection(stall.id().toString());
            value.set("owner", stall.owner().toString());
            value.set("world", stall.location().worldId().toString());
            value.set("x", stall.location().x());
            value.set("y", stall.location().y());
            value.set("z", stall.location().z());
        });
        ConfigurationSection entries = yaml.createSection("entries");
        snapshot.entries().values().stream().sorted(Comparator.comparing(stored -> stored.entry().listing().id().toString()))
            .forEach(stored -> {
                Entry<String> entry = stored.entry();
                Listing<String> listing = entry.listing();
                ConfigurationSection value = entries.createSection(listing.id().toString());
                value.set("seller", listing.seller().toString());
                value.set("stall", listing.stallId().toString());
                value.set("item", listing.item());
                value.set("checksum", stored.checksum());
                value.set("price", listing.price());
                value.set("status", listing.status().name());
                value.set("venue", stored.venue().name());
                value.set("reserved-by", text(listing.reservedBy()));
                value.set("reserved-until", text(listing.reservedUntil()));
                value.set("listing-fee", entry.listingFee());
                value.set("tax-percent", entry.taxPercent());
                value.set("checkout-id", text(entry.checkoutId()));
                value.set("delivery-id", text(entry.deliveryId()));
                value.set("recipient", text(entry.recipient()));
                value.set("review-kind", entry.reviewKind().name());
                value.set("review-reason", entry.reviewReason());
                value.set("debit-confirmed", stored.debitConfirmed());
                value.set("payout-confirmed", stored.payoutConfirmed());
            });
        List<Map<String, Object>> audit = new ArrayList<>();
        for (Audit event : snapshot.audit()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", event.id().toString());
            value.put("at", event.at().toString());
            value.put("listing-id", text(event.listingId()));
            value.put("actor", event.actor());
            value.put("action", event.action());
            value.put("detail", event.detail());
            audit.add(value);
        }
        yaml.set("audit", audit);
        byte[] bytes = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".pending");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // No non-atomic fallback: a failed write must leave the published snapshot intact.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* Never mask the commit result. */ }
        }
    }

    public static String checksum(String item) {
        if (item == null || item.isBlank() || item.length() > MAX_ITEM_LENGTH) {
            throw new IllegalArgumentException("Invalid serialized market item size");
        }
        byte[] bytes = Base64.getDecoder().decode(item);
        if (bytes.length == 0) throw new IllegalArgumentException("Serialized market item is empty");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void validate(Snapshot snapshot) {
        Set<AccountId> owners = new HashSet<>();
        snapshot.stalls().forEach((id, stall) -> {
            require(id.equals(stall.id()) && owners.add(stall.owner()), "Duplicate or invalid stall");
        });
        snapshot.entries().forEach((id, stored) -> {
            Entry<String> entry = stored.entry();
            Listing<String> listing = entry.listing();
            Stall stall = snapshot.stalls().get(listing.stallId());
            require(id.equals(listing.id()) && stall != null && stall.owner().equals(listing.seller()), "Invalid listing stall");
            require(checksum(listing.item()).equals(stored.checksum()), "Item checksum mismatch");
            require(listing.status() != Status.AUCTION || stored.venue() == Venue.AUCTION, "Auction state without auction venue");
            require(stored.venue() != Venue.AUCTION || listing.status() != Status.ACTIVE && listing.status() != Status.RESERVED,
                "Auction item exposed as a fixed-price listing");
            require(!stored.payoutConfirmed() || stored.debitConfirmed(), "Payout without debit");
            require(entry.reviewReason().length() <= 512, "Review reason is too long");
            boolean payment = entry.checkoutId() != null;
            require(payment || !stored.debitConfirmed() && !stored.payoutConfirmed(), "Payment state without checkout");
            if (payment) require(listing.reservedBy() != null && !listing.seller().equals(listing.reservedBy())
                && listing.reservedUntil() == null, "Invalid locked buyer");
            if (listing.status() == Status.RESERVED) require(listing.reservedBy() != null
                && !listing.seller().equals(listing.reservedBy()) && listing.reservedUntil() != null && !payment,
                "Invalid reservation");
            if (listing.status() == Status.RESERVED) require(entry.recipient() == null && entry.deliveryId() == null,
                "Reservation contains delivery metadata");
            if (entry.recipient() != null && entry.recipient().equals(listing.seller())) {
                require(!payment && !stored.debitConfirmed() && !stored.payoutConfirmed(), "Paid checkout cannot become a seller return");
            }
            if (listing.status() == Status.READY) require(entry.deliveryId() == null, "Ready entry retains delivery token");
            if (listing.status() == Status.CANCELLED) require(!payment && !stored.debitConfirmed() && !stored.payoutConfirmed()
                && (entry.recipient() == null && entry.deliveryId() == null
                    || listing.seller().equals(entry.recipient()) && entry.deliveryId() != null), "Invalid cancelled entry");
            if (Set.of(Status.DEPOSIT_PENDING, Status.FEE_PENDING, Status.ACTIVE, Status.AUCTION).contains(listing.status())) {
                require(listing.reservedBy() == null && listing.reservedUntil() == null && !payment
                    && entry.recipient() == null && entry.deliveryId() == null, "Unexpected active listing metadata");
            }
            if (listing.status() == Status.CHECKOUT) require(payment && entry.recipient() != null
                && entry.recipient().equals(listing.reservedBy()) && entry.deliveryId() == null, "Invalid checkout");
            if (stored.venue() == Venue.AUCTION && payment) require(stored.debitConfirmed(), "Auction settlement without escrowed bid");
            if (Set.of(Status.READY, Status.DELIVERING, Status.SOLD).contains(listing.status())
                || entry.reviewKind() == ReviewKind.DELIVERY) {
                require(entry.recipient() != null, "Delivery without recipient");
                if (!entry.recipient().equals(listing.seller())) require(payment && stored.debitConfirmed()
                    && stored.payoutConfirmed() && entry.recipient().equals(listing.reservedBy()), "Unpaid delivery");
            }
            if (listing.status() == Status.DELIVERING || listing.status() == Status.SOLD
                || entry.reviewKind() == ReviewKind.DELIVERY) require(entry.deliveryId() != null, "Missing delivery token");
            if (listing.status() == Status.REVIEW) {
                require(entry.reviewKind() != ReviewKind.NONE && !entry.reviewReason().isBlank(), "Missing review reason");
                if (entry.reviewKind() == ReviewKind.DEPOSIT) require(!payment && entry.deliveryId() == null
                    && entry.recipient() == null, "Deposit review has payment metadata");
            } else require(entry.reviewKind() == ReviewKind.NONE, "Review marker outside review state");
            if (listing.status() == Status.SOLD) require(!entry.recipient().equals(listing.seller()), "Seller return marked sold");
        });
        Set<UUID> audits = new HashSet<>();
        for (Audit event : snapshot.audit()) {
            require(audits.add(event.id()) && !event.actor().isBlank() && !event.action().isBlank(), "Invalid audit record");
            require(event.listingId() == null || snapshot.entries().containsKey(event.listingId()), "Unknown audit listing");
        }
    }

    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection value = parent.getConfigurationSection(path);
        if (value == null) throw new IllegalArgumentException("Missing section: " + path);
        return value;
    }
    private static void validateOptionalVenues(Object document) {
        // Bukkit drops explicit YAML nulls, so validate optional provenance before that conversion.
        if (!(document instanceof Map<?, ?> root) || !(root.get("entries") instanceof Map<?, ?> entries)) return;
        for (Object raw : entries.values()) {
            if (!(raw instanceof Map<?, ?> entry) || !entry.containsKey("venue")) continue;
            Object venue = entry.get("venue");
            require(venue instanceof String text && (text.equals("STORE") || text.equals("AUCTION")), "Invalid market venue");
        }
    }
    private static String string(ConfigurationSection section, String path) {
        Object value = section.get(path);
        if (!(value instanceof String text)) throw new IllegalArgumentException("Expected text: " + path);
        return text;
    }
    private static long number(ConfigurationSection section, String path) {
        Object value = section.get(path);
        if (!(value instanceof Integer) && !(value instanceof Long)) throw new IllegalArgumentException("Expected integer: " + path);
        return ((Number) value).longValue();
    }
    private static int integer(ConfigurationSection section, String path) { return Math.toIntExact(number(section, path)); }
    private static boolean bool(ConfigurationSection section, String path) {
        if (!(section.get(path) instanceof Boolean value)) throw new IllegalArgumentException("Expected boolean: " + path);
        return value;
    }
    private static UUID uuid(ConfigurationSection value, String key) { return value.get(key) == null ? null : UUID.fromString(string(value, key)); }
    private static AccountId account(ConfigurationSection value, String key) { return value.get(key) == null ? null : AccountId.parse(string(value, key)); }
    private static Instant instant(ConfigurationSection value, String key) { return value.get(key) == null ? null : Instant.parse(string(value, key)); }
    private static String mapString(Map<?, ?> value, String key) {
        if (!(value.get(key) instanceof String text)) throw new IllegalArgumentException("Missing audit field: " + key);
        return text;
    }
    private static String text(Object value) { return value == null ? null : value.toString(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }

    public enum Venue { STORE, AUCTION }
    public record StoredEntry(Entry<String> entry, String checksum, boolean debitConfirmed, boolean payoutConfirmed, Venue venue) {
        public StoredEntry { Objects.requireNonNull(entry); Objects.requireNonNull(checksum); Objects.requireNonNull(venue); }
        /** Compatibility for existing fixed-price snapshots and callers. */
        public StoredEntry(Entry<String> entry, String checksum, boolean debitConfirmed, boolean payoutConfirmed) {
            this(entry, checksum, debitConfirmed, payoutConfirmed, Venue.STORE);
        }
    }
    public record Audit(UUID id, Instant at, UUID listingId, String actor, String action, String detail) {
        public Audit {
            Objects.requireNonNull(id); Objects.requireNonNull(at); Objects.requireNonNull(actor);
            Objects.requireNonNull(action); Objects.requireNonNull(detail);
        }
    }
    public record Snapshot(Map<UUID, Stall> stalls, Map<UUID, StoredEntry> entries, List<Audit> audit) {
        public Snapshot { stalls = Map.copyOf(stalls); entries = Map.copyOf(entries); audit = List.copyOf(audit); }
    }
}
