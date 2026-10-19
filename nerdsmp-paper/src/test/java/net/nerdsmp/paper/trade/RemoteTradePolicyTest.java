package net.nerdsmp.paper.trade;

import net.nerdsmp.api.model.AccountId;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class RemoteTradePolicyTest {
    private static final UUID LEFT = UUID.fromString("51074c41-76be-47e9-b24b-cd3e882e37d1");
    private static final UUID RIGHT = UUID.fromString("d399b25f-3256-48cb-9ca1-a4089c379277");
    private static final AccountId LEFT_ACCOUNT = AccountId.parse("2bdfbf32-f1e4-4a3e-9e60-d1d6939181cb");
    private static final AccountId RIGHT_ACCOUNT = AccountId.parse("0f4a1d77-58cb-4781-8c10-00f80598eff1");

    private RemoteTradePolicy.Participant ready(UUID player, AccountId account) {
        return new RemoteTradePolicy.Participant(player, account, true, true, true, true, true, false);
    }

    private RemoteTradePolicy.Participant unavailable(int flag) {
        return new RemoteTradePolicy.Participant(LEFT, flag == 0 ? null : LEFT_ACCOUNT,
            flag != 1, flag != 2, flag != 3, flag != 4, flag != 5, flag == 6);
    }

    @Test void remoteEligibilityHasNoWorldOrDistanceDependency() {
        AtomicInteger visibility = new AtomicInteger(), social = new AtomicInteger();
        assertTrue(RemoteTradePolicy.available(ready(LEFT, LEFT_ACCOUNT), ready(RIGHT, RIGHT_ACCOUNT),
            () -> { visibility.incrementAndGet(); return true; },
            () -> { social.incrementAndGet(); return true; }));
        assertEquals(1, visibility.get());
        assertEquals(1, social.get());
        assertEquals(8, RemoteTradePolicy.Participant.class.getRecordComponents().length);
        for (var component : RemoteTradePolicy.Participant.class.getRecordComponents()) {
            assertFalse(component.getName().toLowerCase().contains("world"));
            assertFalse(component.getName().toLowerCase().contains("location"));
            assertFalse(component.getName().toLowerCase().contains("distance"));
        }
    }

    @Test void everyEligibilityGuardAppliesToBothParticipantsBeforeSocialCallbacks() {
        BooleanSupplier never = () -> { fail("Invalid participants must not reach relationship callbacks"); return true; };
        for (int flag = 0; flag <= 6; flag++) {
            assertFalse(RemoteTradePolicy.available(unavailable(flag), ready(RIGHT, RIGHT_ACCOUNT), never, never), "left guard " + flag);
            assertFalse(RemoteTradePolicy.available(ready(RIGHT, RIGHT_ACCOUNT), unavailable(flag), never, never), "right guard " + flag);
        }
    }

    @Test void nullSamePlayerAndLinkedAccountsCannotTrade() {
        BooleanSupplier never = () -> { fail("Invalid participants must not reach relationship callbacks"); return true; };
        var left = ready(LEFT, LEFT_ACCOUNT);
        assertFalse(RemoteTradePolicy.available(null, left, never, never));
        assertFalse(RemoteTradePolicy.available(left, null, never, never));
        assertFalse(RemoteTradePolicy.available(left, ready(LEFT, RIGHT_ACCOUNT), never, never));
        assertFalse(RemoteTradePolicy.available(left, ready(RIGHT, LEFT_ACCOUNT), never, never));
    }

    @Test void hiddenPlayersCannotTradeAndIgnoreChecksAreNotInvoked() {
        assertFalse(RemoteTradePolicy.available(ready(LEFT, LEFT_ACCOUNT), ready(RIGHT, RIGHT_ACCOUNT),
            () -> false, () -> { fail("Hidden participants must not reach social checks"); return true; }));
    }

    @Test void ignoredRelationshipsCannotTrade() {
        assertFalse(RemoteTradePolicy.available(ready(LEFT, LEFT_ACCOUNT), ready(RIGHT, RIGHT_ACCOUNT), () -> true, () -> false));
    }

    @Test void onlyPlainLeftAndRightClicksCanEditAnOffer() {
        for (ClickType click : ClickType.values()) {
            assertEquals(click == ClickType.LEFT || click == ClickType.RIGHT, RemoteTradePolicy.acceptsClick(click), click.name());
        }
        assertFalse(RemoteTradePolicy.acceptsClick(null));
    }
}
