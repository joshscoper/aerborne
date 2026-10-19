package net.nerdsmp.paper.trade;

import net.nerdsmp.api.model.AccountId;
import org.bukkit.event.inventory.ClickType;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Remote trades depend on authenticated participants, not locations or worlds. */
final class RemoteTradePolicy {
    private RemoteTradePolicy() {
    }

    record Participant(UUID playerId, AccountId account, boolean currentOnlineIdentity,
                       boolean alive, boolean survival, boolean cursorClear,
                       boolean requestsEnabled, boolean recoveryPending) {
        Participant {
            Objects.requireNonNull(playerId, "playerId");
        }

        private boolean eligible() {
            return account != null && currentOnlineIdentity && alive && survival && cursorClear
                && requestsEnabled && !recoveryPending;
        }
    }

    static boolean available(Participant left, Participant right,
                             BooleanSupplier mutuallyVisible, BooleanSupplier neitherIgnoring) {
        if (left == null || right == null || !left.eligible() || !right.eligible()
            || left.playerId().equals(right.playerId()) || left.account().equals(right.account())) return false;
        return mutuallyVisible.getAsBoolean() && neitherIgnoring.getAsBoolean();
    }

    static boolean acceptsClick(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT;
    }
}
