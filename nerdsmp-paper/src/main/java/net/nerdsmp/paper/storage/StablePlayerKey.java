package net.nerdsmp.paper.storage;

import net.nerdsmp.api.model.AccountId;

import java.util.Locale;
import java.util.UUID;

public final class StablePlayerKey {
    private StablePlayerKey() {
    }

    public static String forStorage(String storageMode, AccountId accountId, UUID authenticatedPlayerId) {
        String mode = storageMode == null ? "memory" : storageMode.toLowerCase(Locale.ROOT);
        return mode.equals("postgres")
            ? "account-" + accountId.value()
            : "identity-" + authenticatedPlayerId;
    }
}
