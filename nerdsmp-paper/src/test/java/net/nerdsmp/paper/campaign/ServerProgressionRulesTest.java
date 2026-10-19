package net.nerdsmp.paper.campaign;

import net.nerdsmp.api.service.CampaignService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ServerProgressionRulesTest {
    @Test
    void campaignTotalsUnlockOnlyReachedServerMilestones() {
        Map<String, Long> milestones = Map.of(
            "first-ripple", 10_000L,
            "broken-seal", 30_000L,
            "rift-opened", 60_000L
        );
        CampaignService.CampaignSnapshot snapshot = new CampaignService.CampaignSnapshot(
            "veil-awakening", "The Veil Awakens", Instant.EPOCH, Instant.MAX,
            30_000L, Map.of("gathering", 30_000L), milestones
        );
        Map<ServerProgressionService.Unlock, String> mapping = Map.of(
            ServerProgressionService.Unlock.WORLD_EVENTS, "first-ripple",
            ServerProgressionService.Unlock.NETHER, "broken-seal",
            ServerProgressionService.Unlock.END, "rift-opened"
        );

        assertEquals(Set.of(
            ServerProgressionService.Unlock.WORLD_EVENTS,
            ServerProgressionService.Unlock.NETHER
        ), ServerProgressionService.earnedUnlocks(snapshot, mapping));
    }
}
