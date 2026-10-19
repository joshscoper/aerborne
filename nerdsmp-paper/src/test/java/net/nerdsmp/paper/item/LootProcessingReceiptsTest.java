package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.AccountId;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static net.nerdsmp.paper.item.LootProcessingReceipts.Phase.*;
import static org.junit.jupiter.api.Assertions.*;

class LootProcessingReceiptsTest {
    @TempDir Path directory;
    final AccountId account = AccountId.random();
    LootProcessingReceipts store() { return new LootProcessingReceipts(directory.toFile()); }
    LootProcessingReceipts.Receipt receipt() {
        return new LootProcessingReceipts.Receipt(UUID.randomUUID(), account,"all",150,0,1,Instant.parse("2026-10-17T12:00:00Z"),
            List.of(UUID.randomUUID().toString()), Collections.nCopies(36,"before"), Collections.nCopies(36,"after"), PREPARED);
    }
    @Test void preparedAndApplyingBatchesRemainPendingAcrossRestart() {
        var original = receipt(); store().begin(original);
        assertEquals(original, store().find(account).orElseThrow());
        assertTrue(store().find(account).orElseThrow().pending());
        store().advance(original, APPLYING);
        assertEquals(original.at(APPLYING),store().find(account).orElseThrow());
        assertThrows(IllegalStateException.class, () -> store().begin(receipt()));
    }
    @Test void onlyCompletedDeclinedOrRefundedReceiptsAllowTheNextBatch() {
        for (var terminal : List.of(COMPLETE, DECLINED, REFUNDED)) {
            var original = receipt(); var store = store(); store.begin(original);
            if (terminal == COMPLETE) { store.advance(original,APPLYING); original = original.at(APPLYING); }
            store.advance(original,terminal);
            assertFalse(store().find(account).orElseThrow().pending());
            store().begin(receipt());
            var pending = store().find(account).orElseThrow(); store().advance(pending,REFUNDED);
        }
    }
    @Test void staleOrUnpreparedReceiptTransitionsCannotOverwriteARecordedOperation() throws Exception {
        var original = receipt(); store().begin(original);
        byte[] before = Files.readAllBytes(directory.resolve(account+".yml"));
        assertThrows(IllegalStateException.class, () -> store().advance(receipt(),APPLYING));
        assertThrows(IllegalStateException.class, () -> store().advance(original,COMPLETE));
        assertThrows(IllegalStateException.class, () -> store().begin(receipt().at(COMPLETE)));
        assertArrayEquals(before,Files.readAllBytes(directory.resolve(account+".yml")));
    }
    @Test void corruptDuplicateKeysAndWrongAccountsFailClosedWithoutWriting() throws Exception {
        var original = receipt(); store().begin(original);
        Path file = directory.resolve(account+".yml");
        String valid = Files.readString(file);
        for (String broken : List.of(valid+"schema: 1\n",valid.replace("PREPARED","UNKNOWN"), valid.replace(account.toString(),AccountId.random().toString()),
                valid.replace("marks: 150","marks: 1.5"), "schema: [broken")) {
            Files.writeString(file,broken);
            assertThrows(IllegalStateException.class, () -> store().find(account));
            assertThrows(IllegalStateException.class, () -> store().begin(receipt()));
            assertEquals(broken,Files.readString(file));
        }
    }
    @Test void malformedListsAndInvalidInventoryLengthsAreRejected() throws Exception {
        var original = receipt(); store().begin(original); Path file = directory.resolve(account+".yml");
        var yaml = new YamlConfiguration(); yaml.load(file.toFile()); yaml.set("instances",List.of(3)); yaml.save(file.toFile());
        assertThrows(IllegalStateException.class, () -> store().find(account));
        assertThrows(IllegalArgumentException.class, () -> new LootProcessingReceipts.Receipt(original.operation(),account,"scrap",0,3,1,
            original.created(),original.instances(),List.of("one"),original.after(),PREPARED));
    }
    @Test void failedSaveDoesNotPublishTheNewReceipt() throws Exception {
        Files.writeString(directory.resolve(account+".yml.tmp"),"blocked");
        // Use a directory for the atomic target so the final move is guaranteed to fail.
        Path blocked=directory.resolve("blocked"); Files.writeString(blocked,"not a directory");
        var store = new LootProcessingReceipts(blocked.toFile());
        assertThrows(IllegalStateException.class, () -> store.begin(receipt()));
        assertTrue(store.find(account).isEmpty());
    }
}
