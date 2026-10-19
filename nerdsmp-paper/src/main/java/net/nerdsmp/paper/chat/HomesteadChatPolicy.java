package net.nerdsmp.paper.chat;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ClaimRole;
import net.nerdsmp.api.service.ClaimService.HomesteadView;
import java.util.HashSet;
import java.util.Set;

/** Plot guests, applicants and invitees are not settlement chat members. */
final class HomesteadChatPolicy {
    private HomesteadChatPolicy() { }

    static Set<AccountId> audience(HomesteadView home) {
        if (home.chunks() == 0) return Set.of();
        Set<AccountId> members = new HashSet<>();
        members.add(home.owner());
        home.members().forEach((member, role) -> { if (role != ClaimRole.NONE) members.add(member); });
        return Set.copyOf(members);
    }

    record Selection(AccountId speaker, AccountId owner) {
        boolean permits(AccountId current, HomesteadView home) {
            return speaker.equals(current) && owner.equals(home.owner()) && audience(home).contains(current);
        }
    }
}
