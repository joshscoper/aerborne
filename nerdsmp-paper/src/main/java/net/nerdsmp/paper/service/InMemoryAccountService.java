package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.api.service.AccountService;
import net.nerdsmp.paper.storage.PlayerDataStore;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAccountService implements AccountService {
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final Map<PlatformIdentity, AccountId> identities = new ConcurrentHashMap<>();
    private final Map<String, LinkCode> linkCodes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final PlayerDataStore persistence;

    public InMemoryAccountService() {
        this(null);
    }

    public InMemoryAccountService(PlayerDataStore persistence) {
        this.persistence = persistence;
        if (persistence != null) {
            identities.putAll(persistence.identityMappings());
        }
    }

    @Override
    public CompletionStage<AccountId> resolveOrCreate(PlatformIdentity identity) {
        return CompletableFuture.completedFuture(identities.computeIfAbsent(identity, this::localAccount));
    }

    @Override
    public CompletionStage<Optional<AccountId>> find(PlatformIdentity identity) {
        return CompletableFuture.completedFuture(Optional.ofNullable(identities.get(identity)));
    }

    @Override
    public CompletionStage<String> createLinkCode(AccountId source, Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofMinutes(30)) > 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Link code lifetime must be 1 second to 30 minutes"));
        }

        String code;
        do {
            StringBuilder candidate = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                candidate.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
            }
            code = candidate.toString();
        } while (linkCodes.putIfAbsent(code, new LinkCode(source, Instant.now().plus(lifetime))) != null);

        return CompletableFuture.completedFuture(code);
    }

    @Override
    public synchronized CompletionStage<LinkResult> redeemLinkCode(
        PlatformIdentity target,
        String rawCode,
        boolean targetIsFresh
    ) {
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        LinkCode linkCode = linkCodes.remove(code);
        if (linkCode == null || linkCode.expiresAt().isBefore(Instant.now())) {
            return CompletableFuture.completedFuture(LinkResult.INVALID_OR_EXPIRED);
        }

        AccountId existing = identities.get(target);
        if (linkCode.source().equals(existing)) {
            return CompletableFuture.completedFuture(LinkResult.ALREADY_LINKED);
        }
        if (existing != null && !targetIsFresh) {
            linkCodes.put(code, linkCode);
            return CompletableFuture.completedFuture(LinkResult.TARGET_HAS_PROGRESS);
        }

        identities.put(target, linkCode.source());
        if (persistence != null) {
            persistence.linkIdentity(linkCode.source(), target);
        }
        return CompletableFuture.completedFuture(LinkResult.LINKED);
    }

    private record LinkCode(AccountId source, Instant expiresAt) {
    }

    private AccountId localAccount(PlatformIdentity identity) {
        String key = "nerdsmp-local-account:" + identity.platform().name() + ":" + identity.externalId();
        return new AccountId(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
    }
}
