package net.nerdsmp.paper.claim;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ClaimRole;
import net.nerdsmp.api.service.ClaimService.ClaimView;
import net.nerdsmp.api.service.ClaimService.HomesteadView;

final class HomesteadMenuPolicy {
    static final int PAGE_SIZE = 21;
    private HomesteadMenuPolicy() { }
    static int page(int requested, int count) { return Math.max(0, Math.min(requested, Math.max(0, (count - 1) / PAGE_SIZE))); }
    static int slot(int index) {
        if (index < 0 || index >= PAGE_SIZE) throw new IllegalArgumentException("Invalid entry slot");
        return 10 + index / 7 * 9 + index % 7;
    }
    static boolean member(AccountId actor, HomesteadView home) {
        return actor != null && (actor.equals(home.owner()) || home.members().getOrDefault(actor, ClaimRole.NONE) != ClaimRole.NONE);
    }
    static boolean manager(AccountId actor, HomesteadView home) {
        return actor != null && (actor.equals(home.owner()) || home.members().getOrDefault(actor, ClaimRole.NONE).allows(ClaimRole.MANAGE));
    }
    static boolean guests(AccountId actor, ClaimView plot) {
        return actor != null && (actor.equals(plot.owner()) || plot.type().privateAccess() && actor.equals(plot.steward()));
    }
    static boolean unchanged(ClaimView expected, ClaimView current) {
        return expected.equals(current);
    }
}
