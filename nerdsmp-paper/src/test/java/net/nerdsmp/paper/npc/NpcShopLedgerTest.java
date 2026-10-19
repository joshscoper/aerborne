package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.AccountId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class NpcShopLedgerTest {
    @TempDir
    Path temporary;

    @Test
    void quotasPersistRollbackAndResetAtUtcDayBoundaries() {
        File file = temporary.resolve("sales.yml").toFile();
        AtomicReference<LocalDate> date = new AtomicReference<>(LocalDate.of(2026, 9, 1));
        AccountId account = new AccountId(UUID.fromString("00000000-0000-0000-0000-000000000123"));
        NpcShopLedger ledger = new NpcShopLedger(file, date::get);

        assertTrue(ledger.reserve(account, "warden_supplies", "repair_iron", 2));
        assertTrue(ledger.reserve(account, "warden_supplies", "repair_iron", 2));
        assertFalse(ledger.reserve(account, "warden_supplies", "repair_iron", 2));
        assertEquals(0, ledger.remaining(account, "warden_supplies", "repair_iron", 2));
        ledger.rollback(account, "warden_supplies", "repair_iron");
        assertEquals(1, ledger.remaining(account, "warden_supplies", "repair_iron", 2));
        ledger.close();

        NpcShopLedger reloaded = new NpcShopLedger(file, date::get);
        assertEquals(1, reloaded.remaining(account, "warden_supplies", "repair_iron", 2));
        date.set(LocalDate.of(2026, 9, 2));
        assertEquals(2, reloaded.remaining(account, "warden_supplies", "repair_iron", 2));
    }

    @Test
    void transactionReservationsPersistAndReleaseOnceWithoutTouchingLegacySales() {
        File file = temporary.resolve("receipts.yml").toFile();
        LocalDate date = LocalDate.of(2026, 10, 2);
        AccountId account = new AccountId(UUID.randomUUID());
        UUID trade = UUID.randomUUID();
        NpcShopLedger ledger = new NpcShopLedger(file, () -> date);
        assertTrue(ledger.reserve(account, "provisions", "bread", 3));
        assertTrue(ledger.reserve(trade, account, "provisions", "bread", 3));
        assertTrue(ledger.reserve(trade, account, "provisions", "bread", 3));
        assertEquals(1, ledger.remaining(account, "provisions", "bread", 3));
        assertThrows(IllegalArgumentException.class, () -> ledger.reserve(trade, account, "provisions", "apples", 3));
        NpcShopLedger reloaded = new NpcShopLedger(file, () -> date);
        assertTrue(reloaded.release(trade));
        assertFalse(reloaded.release(trade));
        assertFalse(reloaded.release(UUID.randomUUID()));
        assertEquals(2, reloaded.remaining(account, "provisions", "bread", 3));
        assertEquals(2, new NpcShopLedger(file, () -> date).remaining(account, "provisions", "bread", 3));
    }

    @Test
    void releasingYesterdayCannotReduceTodaysQuota() {
        File file = temporary.resolve("days.yml").toFile();
        AtomicReference<LocalDate> date = new AtomicReference<>(LocalDate.of(2026, 10, 2));
        NpcShopLedger ledger = new NpcShopLedger(file, date::get);
        AccountId account = new AccountId(UUID.randomUUID());
        UUID yesterday = UUID.randomUUID(), today = UUID.randomUUID();
        assertTrue(ledger.reserve(yesterday, account, "provisions", "bread", 2));
        date.set(date.get().plusDays(1));
        assertTrue(ledger.reserve(today, account, "provisions", "bread", 2));
        assertFalse(ledger.release(yesterday)); // expired receipts have no current-day effect
        assertEquals(1, ledger.remaining(account, "provisions", "bread", 2));
        assertTrue(ledger.release(today));
        assertEquals(2, ledger.remaining(account, "provisions", "bread", 2));
    }

    @Test
    void failedReservationSaveDoesNotConsumeTheQuota() throws Exception {
        File file = temporary.resolve("blocked.yml").toFile();
        NpcShopLedger ledger = new NpcShopLedger(file, () -> LocalDate.of(2026, 10, 2));
        AccountId account = new AccountId(UUID.randomUUID());
        UUID trade = UUID.randomUUID();
        Files.createDirectory(file.toPath());
        Files.writeString(file.toPath().resolve("keep"), "not a replaceable empty directory");
        assertThrows(IllegalStateException.class, () -> ledger.reserve(trade, account, "provisions", "bread", 1));
        assertEquals(1, ledger.remaining(account, "provisions", "bread", 1));
        assertFalse(ledger.release(trade));
    }

    @Test
    void corruptedQuotaFilesCannotSilentlyResetSellLimits() throws Exception {
        File file = temporary.resolve("bad.yml").toFile();
        Files.writeString(file.toPath(), "sales:\n  not-an-account:\n    provisions:\n      bread:\n        date: 2026-10-02\n        bundles: 1\n");
        assertThrows(IllegalStateException.class, () -> new NpcShopLedger(file, () -> LocalDate.of(2026, 10, 2)));
    }
}
