package net.nerdsmp.paper.session;

import net.nerdsmp.api.model.AccountId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionRegistry {
    private final Map<UUID, AccountId> onlineAccounts = new ConcurrentHashMap<>();

    public void join(UUID playerId, AccountId accountId) {
        onlineAccounts.put(playerId, accountId);
    }

    public void quit(UUID playerId) {
        onlineAccounts.remove(playerId);
    }

    public Optional<AccountId> account(UUID playerId) {
        return Optional.ofNullable(onlineAccounts.get(playerId));
    }

    public Optional<UUID> player(AccountId accountId) {
        return onlineAccounts.entrySet().stream()
            .filter(entry -> entry.getValue().equals(accountId))
            .map(Map.Entry::getKey)
            .findFirst();
    }
}
