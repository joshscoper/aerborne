package net.nerdsmp.api.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.FactionId;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public interface ReputationService {
    int MINIMUM = -1000;
    int MAXIMUM = 1000;

    CompletionStage<Integer> value(AccountId accountId, FactionId factionId);

    CompletionStage<Map<FactionId, Integer>> standings(AccountId accountId);

    CompletionStage<AdjustmentResult> adjust(
        AccountId accountId,
        FactionId factionId,
        int amount,
        ReputationReason reason,
        String idempotencyKey
    );

    record ReputationReason(String code, String detail) {
        public ReputationReason {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(detail, "detail");
            if (!code.matches("[a-z0-9_.-]{1,64}")) {
                throw new IllegalArgumentException("Invalid reputation reason code");
            }
            if (detail.length() > 256) {
                throw new IllegalArgumentException("Reputation reason detail is too long");
            }
        }
    }

    record AdjustmentResult(Status status, int previousValue, int value, int appliedAmount) {
        public enum Status {
            APPLIED,
            DUPLICATE,
            INVALID_AMOUNT
        }
    }
}
