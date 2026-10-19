package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class InMemoryEconomyServiceTest {
    private static final EconomyService.TransactionReason TEST_REASON =
        new EconomyService.TransactionReason("test", "Automated test");

    @Test
    void operationsAreAtomicAndIdempotent() {
        InMemoryEconomyService service = new InMemoryEconomyService();
        AccountId source = AccountId.random();
        AccountId target = AccountId.random();

        assertEquals(EconomyService.TransactionResult.Status.APPLIED,
            service.credit(source, 1_000L, TEST_REASON, "credit-1").toCompletableFuture().join().status());
        assertEquals(EconomyService.TransactionResult.Status.DUPLICATE,
            service.credit(source, 1_000L, TEST_REASON, "credit-1").toCompletableFuture().join().status());
        assertEquals(EconomyService.TransactionResult.Status.APPLIED,
            service.transfer(source, target, 250L, TEST_REASON, "transfer-1").toCompletableFuture().join().status());

        assertEquals(750L, service.balance(source).toCompletableFuture().join());
        assertEquals(250L, service.balance(target).toCompletableFuture().join());
    }

    @Test
    void insufficientDebitNeverCreatesNegativeMoney() {
        InMemoryEconomyService service = new InMemoryEconomyService();
        AccountId account = AccountId.random();

        assertEquals(EconomyService.TransactionResult.Status.INSUFFICIENT_FUNDS,
            service.debit(account, 1L, TEST_REASON, "debit-1").toCompletableFuture().join().status());
        assertEquals(0L, service.balance(account).toCompletableFuture().join());
    }
}

