package net.nerdsmp.api.service;

import net.nerdsmp.api.model.AccountId;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface CampaignService {
    CompletionStage<Optional<CampaignSnapshot>> activeCampaign();

    CompletionStage<ContributionResult> contribute(AccountId accountId, String category, long points, String idempotencyKey);

    CompletionStage<Boolean> claimReward(AccountId accountId, String milestoneId);

    record CampaignSnapshot(String id, String title, Instant startsAt, Instant endsAt, long totalPoints,
                            Map<String, Long> categoryPoints, Map<String, Long> milestones) {
        public CampaignSnapshot {
            categoryPoints = Map.copyOf(categoryPoints);
            milestones = Map.copyOf(milestones);
        }
    }

    enum ContributionResult {
        APPLIED,
        DUPLICATE,
        NO_ACTIVE_CAMPAIGN,
        INVALID_CATEGORY,
        DAILY_CAP_REACHED,
        INVALID_POINTS
    }
}

