package net.nerdsmp.paper.auction;

import net.nerdsmp.api.model.AccountId;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class AuctionStoreTest {
    @TempDir Path directory;
    private final UUID id = UUID.randomUUID();
    private final AccountId seller = AccountId.random();
    private final AccountId buyer = AccountId.random();
    private final Instant now = Instant.parse("2026-10-12T12:00:00Z");

    @Test void missingFileCreatesOnlyAnEmptyInMemoryState() throws Exception {
        assertEquals(new AuctionStore.Snapshot(Map.of(), List.of()), store().load());
        assertFalse(Files.exists(file()));
    }

    @Test void activeAndPendingStatesRoundTripWithTheirOperationKeys() throws Exception {
        Auction.PendingBid pending = new Auction.PendingBid(UUID.randomUUID(), buyer, 200, null, 0, false, true, false);
        Auction auction = value(Auction.Status.BID_PENDING, null, 0, pending, null, "");
        AuctionStore.Audit audit = new AuctionStore.Audit(UUID.randomUUID(), now, id, buyer.toString(), "BID_FUNDED", "Funds are held.");
        AuctionStore.Snapshot snapshot = new AuctionStore.Snapshot(Map.of(id, auction), List.of(audit));
        store().save(snapshot);
        assertEquals(snapshot, store().load());
    }

    @Test void unsupportedSchemaDoesNotReplaceTheJournal() throws Exception {
        saveActive();
        YamlConfiguration yaml = yaml();
        yaml.set("schema-version", 99);
        yaml.save(file().toFile());
        byte[] original = Files.readAllBytes(file());
        assertThrows(IOException.class, store()::load);
        assertArrayEquals(original, Files.readAllBytes(file()));
    }

    @Test void malformedSourceIsNotSilentlyAnEmptyAuctionHouse() throws Exception {
        Files.writeString(file(), "schema-version: 1\nauctions: [broken\n");
        byte[] original = Files.readAllBytes(file());
        assertThrows(IOException.class, store()::load);
        assertArrayEquals(original, Files.readAllBytes(file()));
    }

    @Test void duplicateKeysAndAliasesAreRejected() throws Exception {
        Files.writeString(file(), "schema-version: 1\nschema-version: 1\nauctions: {}\naudit: []\n");
        assertThrows(IOException.class, store()::load);
        Files.writeString(file(), "schema-version: 1\nauctions: &auctions {}\naudit: *auctions\n");
        assertThrows(IOException.class, store()::load);
    }

    @Test void unknownFieldsDoNotBecomeIgnoredPaymentState() throws Exception {
        saveActive();
        YamlConfiguration yaml = yaml();
        yaml.set("auctions." + id + ".pending-credit", 123);
        yaml.save(file().toFile());
        assertThrows(IOException.class, store()::load);
    }

    @Test void scalarTypeCoercionAndMissingStateFieldsAreRejected() throws Exception {
        saveActive();
        YamlConfiguration yaml = yaml();
        yaml.set("auctions." + id + ".high-bid", "0");
        yaml.save(file().toFile());
        assertThrows(IOException.class, store()::load);
        saveActive();
        yaml = yaml();
        yaml.set("auctions." + id + ".revision", null);
        yaml.save(file().toFile());
        assertThrows(IOException.class, store()::load);
    }

    @Test void forgedHighBidOwnerAndImpossibleSettlementFailClosed() throws Exception {
        saveActive();
        YamlConfiguration yaml = yaml();
        yaml.set("auctions." + id + ".high-bidder", seller.toString());
        yaml.set("auctions." + id + ".high-bid", 100);
        yaml.save(file().toFile());
        assertThrows(IOException.class, store()::load);
        saveActive();
        yaml = yaml();
        yaml.set("auctions." + id + ".status", "COMPLETE");
        yaml.save(file().toFile());
        assertThrows(IOException.class, store()::load);
    }

    @Test void auditReferenceMismatchCannotReplaceAGoodJournal() throws Exception {
        AuctionStore.Snapshot original = saveActive();
        byte[] before = Files.readAllBytes(file());
        AuctionStore.Audit invalid = new AuctionStore.Audit(UUID.randomUUID(), now, UUID.randomUUID(), "system", "BOGUS", "Unknown auction.");
        assertThrows(IOException.class, () -> store().save(new AuctionStore.Snapshot(original.auctions(), List.of(invalid))));
        assertArrayEquals(before, Files.readAllBytes(file()));
        assertEquals(original, store().load());
    }

    @Test void duplicatedAuditIdsFailClosed() throws Exception {
        Auction active = value(Auction.Status.ACTIVE, null, 0, null, null, "");
        AuctionStore.Audit event = new AuctionStore.Audit(UUID.randomUUID(), now, id, "system", "ACTIVE", "Opened.");
        assertThrows(IOException.class, () -> store().save(new AuctionStore.Snapshot(Map.of(id, active), List.of(event, event))));
        assertFalse(Files.exists(file()));
    }

    @Test void atomicMoveFailureLeavesTheTargetUntouchedAndCleansTemporaryFiles() throws Exception {
        Files.createDirectory(file());
        Files.writeString(file().resolve("keep"), "keep");
        assertThrows(IOException.class, () -> store().save(new AuctionStore.Snapshot(Map.of(id,
            value(Auction.Status.ACTIVE, null, 0, null, null, "")), List.of())));
        assertEquals("keep", Files.readString(file().resolve("keep")));
        try (var contents = Files.list(directory)) { assertEquals(List.of(file()), contents.toList()); }
    }

    @Test void pendingPaymentFlagsAndPreviousBidMustBeConsistent() {
        assertThrows(IllegalArgumentException.class, () -> new Auction.PendingBid(UUID.randomUUID(), buyer, 200, null, 0, false, false, true));
        Auction.PendingBid pending = new Auction.PendingBid(UUID.randomUUID(), buyer, 200, null, 0, false, false, false);
        assertThrows(IllegalArgumentException.class, () -> value(Auction.Status.BID_PENDING, buyer, 100, pending, null, ""));
        assertThrows(IllegalArgumentException.class, () -> value(Auction.Status.ACTIVE, null, 0, pending, null, ""));
    }

    @Test void timestampsPricesAndReviewFlagsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new Auction(id, seller, 0, null, now, now.plus(Duration.ofHours(1)), 5,
            Auction.Status.ACTIVE, 0, null, 0, null, null, ""));
        assertThrows(IllegalArgumentException.class, () -> new Auction(id, seller, 100, 99L, now, now.plus(Duration.ofHours(1)), 5,
            Auction.Status.ACTIVE, 0, null, 0, null, null, ""));
        assertThrows(IllegalArgumentException.class, () -> value(Auction.Status.REVIEW, null, 0, null, null, ""));
        assertThrows(IllegalArgumentException.class, () -> value(Auction.Status.ACTIVE, null, 0, null, null, "Should not be here"));
        assertThrows(IllegalArgumentException.class, () -> value(Auction.Status.SETTLEMENT_PENDING, buyer, 100, null, null, ""));
    }

    private AuctionStore.Snapshot saveActive() throws IOException {
        AuctionStore.Snapshot result = new AuctionStore.Snapshot(Map.of(id, value(Auction.Status.ACTIVE, null, 0, null, null, "")), List.of());
        store().save(result);
        return result;
    }
    private Auction value(Auction.Status status, AccountId bidder, long amount, Auction.PendingBid pending, UUID settlement, String review) {
        return new Auction(id, seller, 100, 1_000L, now, now.plus(Duration.ofHours(24)), 5, status, 1, bidder, amount, pending, settlement, review);
    }
    private YamlConfiguration yaml() throws Exception { YamlConfiguration value = new YamlConfiguration(); value.load(file().toFile()); return value; }
    private AuctionStore store() { return new AuctionStore(file().toFile()); }
    private Path file() { return directory.resolve("auctions.yml"); }
}
