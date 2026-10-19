package net.nerdsmp.paper.chat;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.skyblock.IslandService;
import java.util.*;

final class IslandChatPolicy {
    record Selection(AccountId actor, AccountId owner, UUID islandId) {
        boolean permits(AccountId current, IslandService.Island island) {
            return actor.equals(current) && island != null && owner.equals(island.owner()) && islandId.equals(island.id()) && island.member(current);
        }
    }
    static Set<AccountId> audience(IslandService.Island island) {
        Set<AccountId> result = new HashSet<>(island.members()); result.add(island.owner()); return Set.copyOf(result);
    }
}
