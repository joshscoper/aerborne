package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.CampaignService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class InMemoryCampaignServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void contributionIsCappedAndIdempotent() {
        Instant now = Instant.now();
        InMemoryCampaignService service = new InMemoryCampaignService(
            "test", "Test", now.minusSeconds(1), now.plus(Duration.ofDays(1)),
            100L, Set.of("gathering"), Map.of("first", 100L)
        );
        AccountId account = AccountId.random();

        assertEquals(CampaignService.ContributionResult.APPLIED,
            service.contribute(account, "gathering", 75L, "one").toCompletableFuture().join());
        assertEquals(CampaignService.ContributionResult.DUPLICATE,
            service.contribute(account, "gathering", 75L, "one").toCompletableFuture().join());
        assertEquals(CampaignService.ContributionResult.DAILY_CAP_REACHED,
            service.contribute(account, "gathering", 50L, "two").toCompletableFuture().join());
    }

    @Test
    void campaignTotalsClaimsAndReplayProtectionSurviveRestart() {
        Instant now = Instant.now();
        Path state = temporaryDirectory.resolve("campaign-state.yml");
        AccountId account = AccountId.random();
        InMemoryCampaignService first = service(now, state);

        assertEquals(CampaignService.ContributionResult.APPLIED,
            first.contribute(account, "gathering", 75L, "one").toCompletableFuture().join());
        assertEquals(true, first.claimReward(account, "first").toCompletableFuture().join());

        InMemoryCampaignService restarted = service(now, state);
        assertEquals(75L, restarted.activeCampaign().toCompletableFuture().join().orElseThrow().totalPoints());
        assertEquals(CampaignService.ContributionResult.DUPLICATE,
            restarted.contribute(account, "gathering", 75L, "one").toCompletableFuture().join());
        assertEquals(false, restarted.claimReward(account, "first").toCompletableFuture().join());
    }

    private InMemoryCampaignService service(Instant now, Path state) {
        return new InMemoryCampaignService(
            "test", "Test", now.minusSeconds(1), now.plus(Duration.ofDays(1)),
            100L, Set.of("gathering"), Map.of("first", 50L), state.toFile()
        );
    }
}
