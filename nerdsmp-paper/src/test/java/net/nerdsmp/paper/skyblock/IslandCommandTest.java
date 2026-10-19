package net.nerdsmp.paper.skyblock;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class IslandCommandTest {
    @Test void usageLineListsEverySubcommandOnce() {
        assertEquals(SkyblockController.SUBCOMMANDS.size(), new HashSet<>(SkyblockController.SUBCOMMANDS).size());
        assertEquals("/island [" + String.join("|", SkyblockController.SUBCOMMANDS) + "]", SkyblockController.usage());
        for (String action : List.of("visit", "border", "fixgenerator", "invitations")) assertTrue(SkyblockController.SUBCOMMANDS.contains(action), action);
    }

    @Test void requestsAreAnsweredWithAcceptOrDeny() {
        assertTrue(SkyblockController.SUBCOMMANDS.containsAll(List.of("accept", "deny", "invite", "kick", "leave")));
        // decline and cancel still work in the handler, but are not suggested.
        assertFalse(SkyblockController.SUBCOMMANDS.contains("decline"));
        assertFalse(SkyblockController.SUBCOMMANDS.contains("cancel"));
    }

    @Test void completionMatchesWhatWasTyped() {
        assertEquals(List.of("directory", "deny"), SkyblockController.matching(SkyblockController.SUBCOMMANDS, "d"));
        assertEquals(List.of("Steve"), SkyblockController.matching(List.of("Alex", "Steve"), "st"));
        assertEquals(SkyblockController.SUBCOMMANDS, SkyblockController.matching(SkyblockController.SUBCOMMANDS, ""));
    }
}
