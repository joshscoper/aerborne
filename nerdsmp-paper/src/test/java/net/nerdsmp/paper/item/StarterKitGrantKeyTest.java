package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class StarterKitGrantKeyTest {
    @Test
    void memoryModeSurvivesTransientAccountIdsWithoutGrantingAgain() {
        UUID playerId = UUID.randomUUID();
        String first = StarterKitService.grantKey("season-1", "memory", AccountId.random(), playerId, 2);
        String afterRestart = StarterKitService.grantKey("season-1", "memory", AccountId.random(), playerId, 2);

        assertEquals(first, afterRestart);
        assertEquals(first, StarterKitService.grantKey("season-1", "yaml", AccountId.random(), playerId, 2),
            "Renaming the local storage option must not grant another starter kit");
    }

    @Test
    void postgresModeUsesCanonicalAccountsAcrossLinkedIdentities() {
        AccountId accountId = AccountId.random();
        String java = StarterKitService.grantKey("season-1", "postgres", accountId, UUID.randomUUID(), 2);
        String bedrock = StarterKitService.grantKey("season-1", "postgres", accountId, UUID.randomUUID(), 2);

        assertEquals(java, bedrock);
        assertNotEquals(java, StarterKitService.grantKey("season-2", "postgres", accountId, UUID.randomUUID(), 2));
        assertNotEquals(java, StarterKitService.grantKey("season-1", "postgres", accountId, UUID.randomUUID(), 3));
    }

    @Test
    void legacyPlayerWithCompleteKitIsRecognizedWithoutDuplicateDelivery() {
        var hood = new ItemId("frontier_hood");
        var blade = new ItemId("frontier_blade");

        assertEquals(true, StarterKitService.hasEveryKitItem(
            java.util.List.of(hood, blade), java.util.List.of(blade, hood, hood)));
        assertEquals(false, StarterKitService.hasEveryKitItem(
            java.util.List.of(hood, blade), java.util.List.of(hood)));
    }
}
