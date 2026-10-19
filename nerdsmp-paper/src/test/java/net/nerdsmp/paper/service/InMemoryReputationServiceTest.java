package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.api.service.ReputationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class InMemoryReputationServiceTest {
    private static final ReputationService.ReputationReason REASON =
        new ReputationService.ReputationReason("test", "Test adjustment");

    @Test
    void adjustmentsAreIdempotentAndClampAtFactionBounds() {
        InMemoryReputationService service = new InMemoryReputationService();
        AccountId account = AccountId.random();
        FactionId faction = new FactionId("frontier_wardens");

        var first = service.adjust(account, faction, 250, REASON, "operation-1").toCompletableFuture().join();
        var duplicate = service.adjust(account, faction, 250, REASON, "operation-1").toCompletableFuture().join();
        var capped = service.adjust(account, faction, 5000, REASON, "operation-2").toCompletableFuture().join();

        assertEquals(ReputationService.AdjustmentResult.Status.APPLIED, first.status());
        assertEquals(ReputationService.AdjustmentResult.Status.DUPLICATE, duplicate.status());
        assertEquals(1000, capped.value());
        assertEquals(750, capped.appliedAmount());
        assertEquals(1000, service.value(account, faction).toCompletableFuture().join());
    }

    @Test
    void standingsAreSeparatedByAccountAndFaction() {
        InMemoryReputationService service = new InMemoryReputationService();
        AccountId first = AccountId.random();
        AccountId second = AccountId.random();
        FactionId wardens = new FactionId("frontier_wardens");
        FactionId scholars = new FactionId("rift_scholars");

        service.adjust(first, wardens, 20, REASON, "first-wardens");
        service.adjust(first, scholars, -10, REASON, "first-scholars");
        service.adjust(second, wardens, 80, REASON, "second-wardens");

        assertEquals(20, service.value(first, wardens).toCompletableFuture().join());
        assertEquals(-10, service.value(first, scholars).toCompletableFuture().join());
        assertEquals(80, service.value(second, wardens).toCompletableFuture().join());
        assertEquals(2, service.standings(first).toCompletableFuture().join().size());
    }
}
