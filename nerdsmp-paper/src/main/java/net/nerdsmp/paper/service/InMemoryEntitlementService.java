package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EntitlementService;
import net.nerdsmp.paper.storage.PlayerDataStore;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class InMemoryEntitlementService implements EntitlementService {
    private final Map<String, StoredEntitlement> byTransaction = new ConcurrentHashMap<>();
    private final Set<AccountId> loadedAccounts = new HashSet<>();
    private final PlayerDataStore persistence;

    public InMemoryEntitlementService() {
        this(null);
    }

    public InMemoryEntitlementService(PlayerDataStore persistence) {
        this.persistence = persistence;
        if (persistence != null) {
            persistence.knownAccounts().forEach(this::ensureLoaded);
        }
    }

    @Override
    public synchronized CompletionStage<GrantResult> grant(
        AccountId accountId,
        String productId,
        String transactionId,
        Instant expiresAt
    ) {
        ensureLoaded(accountId);
        if (!productId.matches("[a-z0-9_.-]{1,96}")) {
            return CompletableFuture.completedFuture(GrantResult.INVALID_PRODUCT);
        }
        StoredEntitlement created = new StoredEntitlement(
            accountId,
            new Entitlement(productId, transactionId, Instant.now(), expiresAt),
            false
        );
        if (byTransaction.putIfAbsent(transactionId, created) != null) {
            return CompletableFuture.completedFuture(GrantResult.DUPLICATE);
        }
        persist(accountId);
        return CompletableFuture.completedFuture(GrantResult.APPLIED);
    }

    @Override
    public synchronized CompletionStage<GrantResult> revoke(String transactionId, String reason) {
        StoredEntitlement stored = byTransaction.get(transactionId);
        if (stored == null) {
            return CompletableFuture.completedFuture(GrantResult.NOT_FOUND);
        }
        if (stored.revoked) {
            return CompletableFuture.completedFuture(GrantResult.DUPLICATE);
        }
        byTransaction.put(transactionId, new StoredEntitlement(stored.accountId, stored.entitlement, true));
        persist(stored.accountId);
        return CompletableFuture.completedFuture(GrantResult.REVOKED);
    }

    @Override
    public synchronized CompletionStage<Set<Entitlement>> activeEntitlements(AccountId accountId, Instant now) {
        ensureLoaded(accountId);
        return CompletableFuture.completedFuture(byTransaction.values().stream()
            .filter(stored -> !stored.revoked)
            .filter(stored -> stored.accountId.equals(accountId))
            .map(stored -> stored.entitlement)
            .filter(entitlement -> entitlement.activeAt(now))
            .collect(Collectors.toUnmodifiableSet()));
    }

    private void ensureLoaded(AccountId accountId) {
        if (persistence == null || !loadedAccounts.add(accountId)) {
            return;
        }
        persistence.read(accountId, yaml -> {
            List<Map<?, ?>> records = yaml.getMapList("entitlements.records");
            for (Map<?, ?> record : records) {
                try {
                    String product = Objects.toString(record.get("product"));
                    String transaction = Objects.toString(record.get("transaction"));
                    Instant grantedAt = Instant.parse(Objects.toString(record.get("granted-at")));
                    Object rawExpiry = record.get("expires-at");
                    Instant expiresAt = rawExpiry == null || rawExpiry.toString().isBlank()
                        ? null
                        : Instant.parse(rawExpiry.toString());
                    boolean revoked = Boolean.parseBoolean(Objects.toString(record.get("revoked"), "false"));
                    byTransaction.putIfAbsent(transaction, new StoredEntitlement(
                        accountId,
                        new Entitlement(product, transaction, grantedAt, expiresAt),
                        revoked
                    ));
                } catch (DateTimeParseException | NullPointerException ignored) {
                    // Ignore only the damaged entitlement record, not the player's entire document.
                }
            }
            return null;
        });
    }

    private void persist(AccountId accountId) {
        if (persistence == null) {
            return;
        }
        List<Map<String, Object>> records = byTransaction.values().stream()
            .filter(stored -> stored.accountId.equals(accountId))
            .sorted((left, right) -> left.entitlement.transactionId()
                .compareTo(right.entitlement.transactionId()))
            .map(stored -> {
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("product", stored.entitlement.productId());
                record.put("transaction", stored.entitlement.transactionId());
                record.put("granted-at", stored.entitlement.grantedAt().toString());
                if (stored.entitlement.expiresAt() != null) {
                    record.put("expires-at", stored.entitlement.expiresAt().toString());
                }
                record.put("revoked", stored.revoked);
                return record;
            })
            .toList();
        persistence.update(accountId, yaml -> yaml.set("entitlements.records", records));
    }

    private record StoredEntitlement(AccountId accountId, Entitlement entitlement, boolean revoked) {
    }
}
