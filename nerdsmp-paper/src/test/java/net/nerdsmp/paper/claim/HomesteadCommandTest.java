package net.nerdsmp.paper.claim;

import net.nerdsmp.api.service.ClaimService.ClaimResult;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

final class HomesteadCommandTest {
    @Test void usageLineListsEverySubcommandOnce() {
        assertEquals(HomesteadController.SUBCOMMANDS.size(), new HashSet<>(HomesteadController.SUBCOMMANDS).size());
        assertEquals("/claim [" + String.join("|", HomesteadController.SUBCOMMANDS) + "]", HomesteadController.usage());
        assertTrue(HomesteadController.SUBCOMMANDS.contains("deny"));
        assertFalse(HomesteadController.SUBCOMMANDS.contains("decline"));
        assertTrue(HomesteadController.SUBCOMMANDS.containsAll(HomesteadController.HOME_ACTIONS.stream()
            .filter(action -> !action.equals("history") && !action.equals("applications")).toList()));
    }

    @Test void refusalsAreNeverShownAsSuccess() {
        assertEquals(1, HomesteadController.outcome(ClaimResult.CREATED));
        assertEquals(1, HomesteadController.outcome(ClaimResult.INVITATION_CANCELLED));
        assertEquals(0, HomesteadController.outcome(ClaimResult.APPLIED));
        for (ClaimResult refusal : new ClaimResult[]{ClaimResult.NOT_OWNER, ClaimResult.ALREADY_CLAIMED, ClaimResult.RESERVED,
            ClaimResult.NO_INVITATION, ClaimResult.CAPACITY_REACHED, ClaimResult.INVALID_NAME, ClaimResult.WOULD_SPLIT})
            assertEquals(-1, HomesteadController.outcome(refusal), refusal.name());
    }
}
