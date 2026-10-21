package net.nerdsmp.paper.economy;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ObservedEconomyServiceTest {
    private static final EconomyService.TransactionReason REASON = new EconomyService.TransactionReason("test", "Test");

    /** Applies everything except debits, which always lack funds. */
    private static final class FakeEconomy implements EconomyService {
        @Override public CompletionStage<Long> balance(AccountId accountId) { return CompletableFuture.completedFuture(0L); }
        @Override public CompletionStage<TransactionResult> credit(AccountId a, long m, TransactionReason r, String k) {
            return CompletableFuture.completedFuture(TransactionResult.applied(m));
        }
        @Override public CompletionStage<TransactionResult> debit(AccountId a, long m, TransactionReason r, String k) {
            return CompletableFuture.completedFuture(new TransactionResult(TransactionResult.Status.INSUFFICIENT_FUNDS, 0, OptionalLong.empty()));
        }
        @Override public CompletionStage<TransactionResult> transfer(AccountId s, AccountId d, long m, TransactionReason r, String k) {
            return CompletableFuture.completedFuture(new TransactionResult(TransactionResult.Status.APPLIED, 0, OptionalLong.of(m)));
        }
    }

    @Test void appliedTransactionsReportEveryAccountTheyTouch() {
        var economy = new ObservedEconomyService(new FakeEconomy());
        List<AccountId> changed = new ArrayList<>();
        economy.onChange(changed::add);
        var a = AccountId.random(); var b = AccountId.random();
        economy.credit(a, 10, REASON, "one").toCompletableFuture().join();
        economy.debit(a, 10, REASON, "two").toCompletableFuture().join();
        economy.transfer(a, b, 5, REASON, "three").toCompletableFuture().join();
        assertEquals(List.of(a, a, b), changed, "the refused debit reports nothing");
    }

    @Test void aFailingListenerNeverFailsTheTransaction() {
        var economy = new ObservedEconomyService(new FakeEconomy());
        economy.onChange(account -> { throw new IllegalStateException("display broke"); });
        assertEquals(EconomyService.TransactionResult.Status.APPLIED,
            economy.credit(AccountId.random(), 1, REASON, "k").toCompletableFuture().join().status());
    }
}
