package net.nerdsmp.api.service;

import net.nerdsmp.api.model.AccountId;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public interface EntitlementService {
    CompletionStage<GrantResult> grant(AccountId accountId, String productId, String transactionId, Instant expiresAt);

    CompletionStage<GrantResult> revoke(String transactionId, String reason);

    CompletionStage<Set<Entitlement>> activeEntitlements(AccountId accountId, Instant now);

    record Entitlement(String productId, String transactionId, Instant grantedAt, Instant expiresAt) {
        public boolean activeAt(Instant instant) {
            return expiresAt == null || expiresAt.isAfter(instant);
        }
    }

    enum GrantResult {
        APPLIED,
        DUPLICATE,
        REVOKED,
        NOT_FOUND,
        INVALID_PRODUCT
    }
}

