package net.nerdsmp.paper.chat;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.skyblock.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IslandChatPolicyTest {
    private final AccountId owner = AccountId.random(), member = AccountId.random(), visitor = AccountId.random();
    private final UUID islandId = UUID.randomUUID();
    private IslandService.Island island(UUID id, Set<AccountId> members) { return new IslandService.Island(owner, id, 0, 0, "Hearth", true, members, Map.of(visitor, java.time.Instant.MAX), Map.of(), IslandShowcase.HEARTH); }
    @Test void onlyOwnerAndCurrentMembersReceiveMessages() {
        assertEquals(Set.of(owner, member), IslandChatPolicy.audience(island(islandId, Set.of(member))));
        assertFalse(new IslandChatPolicy.Selection(visitor, owner, islandId).permits(visitor, island(islandId, Set.of(member))));
    }
    @Test void leavingOrBeingKickedRevokesTheSelectedChannel() {
        var selection = new IslandChatPolicy.Selection(member, owner, islandId);
        assertTrue(selection.permits(member, island(islandId, Set.of(member))));
        assertFalse(selection.permits(member, island(islandId, Set.of())));
    }
    @Test void AccountRelinkingOrReplacedIslandCannotReuseOldSelection() {
        var selection = new IslandChatPolicy.Selection(member, owner, islandId);
        assertFalse(selection.permits(owner, island(islandId, Set.of(member))));
        assertFalse(selection.permits(member, island(UUID.randomUUID(), Set.of(member))));
        assertFalse(selection.permits(member, null));
    }
}
