package net.nerdsmp.paper.chat;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ClaimRole;
import net.nerdsmp.api.service.ClaimService.HomesteadView;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class HomesteadChatPolicyTest {
    private final AccountId owner = AccountId.random(), resident = AccountId.random();
    private HomesteadView home(Map<AccountId, ClaimRole> members, int chunks) {
        return new HomesteadView(owner, "Hollow", members, Set.of(), chunks, 4);
    }
    @Test void onlyActualResidentsAndOwnerReceiveChat() {
        var visitor = AccountId.random();
        var audience = HomesteadChatPolicy.audience(home(Map.of(resident, ClaimRole.BUILD, visitor, ClaimRole.NONE), 1));
        assertEquals(Set.of(owner, resident), audience);
        assertThrows(UnsupportedOperationException.class, () -> audience.add(visitor));
    }
    @Test void removalImmediatelyInvalidatesSelection() {
        var selection = new HomesteadChatPolicy.Selection(resident, owner);
        assertTrue(selection.permits(resident, home(Map.of(resident, ClaimRole.RESIDENT), 1)));
        assertFalse(selection.permits(resident, home(Map.of(), 1)));
    }
    @Test void relinkingCannotReusePreviousAccountsChannel() {
        var other = AccountId.random();
        var home = home(Map.of(resident, ClaimRole.RESIDENT, other, ClaimRole.RESIDENT), 1);
        assertFalse(new HomesteadChatPolicy.Selection(resident, owner).permits(other, home));
    }
    @Test void emptySettlementCannotSendOrReceive() {
        var home = home(Map.of(resident, ClaimRole.MANAGE), 0);
        assertTrue(HomesteadChatPolicy.audience(home).isEmpty());
        assertFalse(new HomesteadChatPolicy.Selection(owner, owner).permits(owner, home));
    }
    @Test void selectedHomeCannotBeReplacedByAnotherMembership() {
        assertFalse(new HomesteadChatPolicy.Selection(resident, AccountId.random())
            .permits(resident, home(Map.of(resident, ClaimRole.RESIDENT), 1)));
    }
}
