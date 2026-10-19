package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.AccountId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class NpcDialogueRulesTest {
    @Test
    void responseSlotsStayCenteredWithoutWrappingIntoTheExitRow() {
        assertArrayEquals(new int[]{31}, NpcManager.dialogueChoiceSlots(1));
        assertArrayEquals(new int[]{30, 32}, NpcManager.dialogueChoiceSlots(2));
        assertArrayEquals(new int[]{29, 31, 33}, NpcManager.dialogueChoiceSlots(3));
        assertArrayEquals(new int[]{28, 30, 32, 34}, NpcManager.dialogueChoiceSlots(4));
        assertArrayEquals(new int[]{28, 30, 32, 34, 40}, NpcManager.dialogueChoiceSlots(5));
    }

    @Test
    void requirementsAndStoryFlagsGateChoices() {
        var choice = choice(new NpcContent.Requirements(100, 1000, 75, 5, Set.of("introduced"), Set.of("banned")));
        var locked = context(50, 20, 3, Set.of(), Set.of(), Map.of());
        var open = context(150, 100, 5, Set.of("introduced"), Set.of(), Map.of());

        assertFalse(NpcManager.access(choice, "npc/node/choice", locked, Instant.EPOCH).available());
        assertTrue(NpcManager.access(choice, "npc/node/choice", open, Instant.EPOCH).available());
    }

    @Test
    void oneTimeAndCooldownChoicesCannotBeReplayed() {
        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        var choice = new NpcContent.DialogueChoice(
            "choice", "Choice", null, true, true, Duration.ofHours(24),
            NpcContent.Requirements.none(), List.of()
        );
        var used = context(0, 0, 1, Set.of(), Set.of("npc/node/choice"),
            Map.of("npc/node/choice", now.minus(Duration.ofMinutes(5))));

        assertFalse(NpcManager.access(choice, "npc/node/choice", used, now).available());
    }

    @Test
    void dialogueNodesRejectDuplicateResponseLabels() {
        var first = new NpcContent.DialogueChoice(
            "first", "Show me the store", null, true, false, Duration.ZERO,
            NpcContent.Requirements.none(), List.of()
        );
        var duplicate = new NpcContent.DialogueChoice(
            "second", "  SHOW ME THE STORE  ", null, true, false, Duration.ZERO,
            NpcContent.Requirements.none(), List.of()
        );

        assertThrows(IllegalArgumentException.class,
            () -> new NpcContent.DialogueNode("merchant", List.of("Welcome."), List.of(first, duplicate)));
    }

    private NpcContent.DialogueChoice choice(NpcContent.Requirements requirements) {
        return new NpcContent.DialogueChoice(
            "choice", "Choice", null, true, false, Duration.ZERO, requirements, List.of()
        );
    }

    private NpcManager.DialogueContext context(
        int reputation,
        long balance,
        int level,
        Set<String> flags,
        Set<String> completed,
        Map<String, Instant> used
    ) {
        return new NpcManager.DialogueContext(
            AccountId.random(), reputation, balance, level, new NpcProgressStore.Snapshot(flags, completed, used)
        );
    }
}
