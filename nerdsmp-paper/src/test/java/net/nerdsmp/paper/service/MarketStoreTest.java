package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.MarketService.*;
import net.nerdsmp.paper.market.MarketStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class MarketStoreTest {
    @TempDir Path directory;
    private final UUID id = UUID.randomUUID();
    private final AccountId seller = AccountId.random();
    private final Stall stall = new Stall(UUID.randomUUID(), seller,
        new StallLocation(UUID.randomUUID(), 4, 70, -5));
    private final String item = Base64.getEncoder().encodeToString("item-a".getBytes(StandardCharsets.UTF_8));

    @Test
    void checksumMismatchFailsClosedWithoutOverwritingSource() throws Exception {
        MarketStore store = store();
        store.save(snapshot(entry(Status.ACTIVE, null, null, null), false, false));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file().toFile());
        yaml.set("entries." + id + ".item",
            Base64.getEncoder().encodeToString("different-item".getBytes(StandardCharsets.UTF_8)));
        yaml.save(file().toFile());
        byte[] tampered = Files.readAllBytes(file());
        assertThrows(IOException.class, store::load);
        assertArrayEquals(tampered, Files.readAllBytes(file()));
    }

    @Test
    void legacyJournalWithoutVenueLoadsAsStoreWithoutRewritingTheSource() throws Exception {
        MarketStore store = store();
        var original = snapshot(entry(Status.ACTIVE, null, null, null), false, false);
        store.save(original);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file().toFile());
        yaml.set("entries." + id + ".venue", null);
        yaml.save(file().toFile());
        byte[] legacy = Files.readAllBytes(file());
        assertEquals(original, store.load());
        assertEquals(MarketStore.Venue.STORE, store.load().entries().get(id).venue());
        assertArrayEquals(legacy, Files.readAllBytes(file()));
        assertEquals(1, MarketStore.SCHEMA_VERSION);
    }

    @Test
    void malformedVenueFailsClosedWithoutReplacingTheJournal() throws Exception {
        store().save(snapshot(entry(Status.ACTIVE, null, null, null), false, false));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file().toFile());
        yaml.set("entries." + id + ".venue", "unknown");
        yaml.save(file().toFile());
        byte[] before = Files.readAllBytes(file());
        assertThrows(IOException.class, () -> store().load());
        assertArrayEquals(before, Files.readAllBytes(file()));
    }

    @Test
    void explicitNullVenueIsMalformedRatherThanTreatedAsLegacyMissingProvenance() throws Exception {
        store().save(snapshot(entry(Status.ACTIVE, null, null, null), false, false));
        String original = Files.readString(file());
        assertTrue(original.contains("venue: STORE"));
        Files.writeString(file(), original.replace("venue: STORE", "venue: null"));
        byte[] before = Files.readAllBytes(file());
        assertThrows(IOException.class, () -> store().load());
        assertArrayEquals(before, Files.readAllBytes(file()));
    }

    @Test
    void auctionVenueCannotExposeFixedPriceStatesOrUnfundedCheckout() {
        var active = new MarketStore.StoredEntry(entry(Status.ACTIVE, null, null, null), MarketStore.checksum(item),
            false, false, MarketStore.Venue.AUCTION);
        assertThrows(IOException.class, () -> store().save(new MarketStore.Snapshot(Map.of(stall.id(), stall), Map.of(id, active), List.of())));
        AccountId buyer = AccountId.random();
        var checkout = new Entry<>(new Listing<>(id, seller, stall.id(), item, 200, Status.CHECKOUT, buyer, null),
            10, 10, UUID.randomUUID(), null, buyer, ReviewKind.NONE, "");
        var unfunded = new MarketStore.StoredEntry(checkout, MarketStore.checksum(item), false, false, MarketStore.Venue.AUCTION);
        assertThrows(IOException.class, () -> store().save(new MarketStore.Snapshot(Map.of(stall.id(), stall), Map.of(id, unfunded), List.of())));
        assertThrows(IOException.class, () -> store().save(snapshot(entry(Status.AUCTION, null, null, null), false, false)));
    }

    @Test
    void aJournalLargerThanThreeMiBRoundTripsWithoutAnImplicitYamlLimit() throws Exception {
        String largeItem = Base64.getEncoder().encodeToString(new byte[2_500_000]);
        var listing = new Listing<>(id, seller, stall.id(), largeItem, 100, Status.ACTIVE, null, null);
        var value = new Entry<>(listing, 10, 0, null, null, null, ReviewKind.NONE, "");
        var large = new MarketStore.Snapshot(Map.of(stall.id(), stall), Map.of(id,
            new MarketStore.StoredEntry(value, MarketStore.checksum(largeItem), false, false)), List.of());
        MarketStore store = store();

        store.save(large);

        assertTrue(Files.size(file()) > 3L * 1024 * 1024);
        assertEquals(large, store.load());
    }

    @Test
    void invalidSnapshotCannotReplaceAValidJournal() throws Exception {
        MarketStore store = store();
        var original = snapshot(entry(Status.ACTIVE, null, null, null), false, false);
        store.save(original);
        byte[] before = Files.readAllBytes(file());
        var invalid = new MarketStore.Snapshot(Map.of(stall.id(), stall), Map.of(id,
            new MarketStore.StoredEntry(entry(Status.ACTIVE, null, null, null), "bad-checksum", false, false)), List.of());
        assertThrows(IOException.class, () -> store.save(invalid));
        assertArrayEquals(before, Files.readAllBytes(file()));
        assertEquals(original, store.load());
    }

    @Test
    void payoutWithoutBuyerDebitIsRejected() {
        AccountId buyer = AccountId.random();
        var listing = new Listing<>(id, seller, stall.id(), item, 100, Status.CHECKOUT, buyer, null);
        var entry = new Entry<>(listing, 10, 10, UUID.randomUUID(), null, buyer, ReviewKind.NONE, "");
        assertThrows(IOException.class, () -> store().save(snapshot(entry, false, true)));
        assertFalse(Files.exists(file()));
    }

    @Test
    void sellerReturnCannotContainABuyersPayment() {
        AccountId buyer = AccountId.random();
        var listing = new Listing<>(id, seller, stall.id(), item, 100, Status.READY, buyer, null);
        var entry = new Entry<>(listing, 10, 10, UUID.randomUUID(), null, seller, ReviewKind.NONE, "");
        assertThrows(IOException.class, () -> store().save(snapshot(entry, true, true)));
    }

    @Test
    void readyDeliveryCannotReuseAnAlreadyIssuedToken() {
        var entry = entry(Status.READY, null, UUID.randomUUID(), seller);
        assertThrows(IOException.class, () -> store().save(snapshot(entry, false, false)));
    }

    @Test
    void unsupportedSchemaIsNotTreatedAsAnEmptyMarket() throws Exception {
        store().save(snapshot(entry(Status.ACTIVE, null, null, null), false, false));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file().toFile());
        yaml.set("schema-version", 999);
        yaml.save(file().toFile());
        byte[] before = Files.readAllBytes(file());
        assertThrows(IOException.class, () -> store().load());
        assertArrayEquals(before, Files.readAllBytes(file()));
    }

    @Test
    void failedAtomicMovePreservesExistingTargetAndCleansTemporaryFile() throws Exception {
        Files.createDirectory(file());
        Files.writeString(file().resolve("sentinel"), "keep");
        assertThrows(IOException.class,
            () -> store().save(snapshot(entry(Status.ACTIVE, null, null, null), false, false)));
        assertEquals("keep", Files.readString(file().resolve("sentinel")));
        try (var children = Files.list(directory)) {
            assertEquals(List.of(file()), children.toList());
        }
    }

    private Entry<String> entry(Status status, UUID checkout, UUID delivery, AccountId recipient) {
        return new Entry<>(new Listing<>(id, seller, stall.id(), item, 100, status, null, null),
            10, 0, checkout, delivery, recipient, ReviewKind.NONE, "");
    }

    private MarketStore.Snapshot snapshot(Entry<String> entry, boolean debited, boolean paid) {
        return new MarketStore.Snapshot(Map.of(stall.id(), stall), Map.of(id,
            new MarketStore.StoredEntry(entry, MarketStore.checksum(item), debited, paid)), List.of());
    }

    private MarketStore store() { return new MarketStore(file().toFile()); }
    private Path file() { return directory.resolve("market.yml"); }
}
