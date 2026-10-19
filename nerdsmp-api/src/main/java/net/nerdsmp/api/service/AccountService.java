package net.nerdsmp.api.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface AccountService {
    CompletionStage<AccountId> resolveOrCreate(PlatformIdentity identity);

    CompletionStage<Optional<AccountId>> find(PlatformIdentity identity);

    CompletionStage<String> createLinkCode(AccountId source, Duration lifetime);

    CompletionStage<LinkResult> redeemLinkCode(PlatformIdentity target, String code, boolean targetIsFresh);

    enum LinkResult {
        LINKED,
        INVALID_OR_EXPIRED,
        TARGET_HAS_PROGRESS,
        ALREADY_LINKED
    }
}

