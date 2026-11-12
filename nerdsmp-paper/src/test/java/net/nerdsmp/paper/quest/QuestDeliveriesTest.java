package net.nerdsmp.paper.quest;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class QuestDeliveriesTest {
    @Test void oneStackCannotPayTwoObjectives() {
        var quest = quest(deliver(Set.of("WHEAT"), 3), deliver(Set.of("WHEAT"), 3));
        var result = QuestDeliveries.allocate(quest, Map.of("WHEAT", 3));
        assertFalse(QuestProgress.done(quest, result.counts()));
        assertEquals(3, java.util.Arrays.stream(result.counts()).sum());
        assertEquals(Map.of("WHEAT", 3), result.consumed());
    }

    @Test void alternativesLeaveEnoughForTheMoreSpecificDelivery() {
        var quest = quest(deliver(Set.of("OAK_SAPLING", "BIRCH_SAPLING"), 4), deliver(Set.of("BIRCH_SAPLING"), 3));
        // Sorted material order tries birch first; the allocation must reroute it to the specific objective.
        var result = QuestDeliveries.allocate(quest, Map.of("BIRCH_SAPLING", 3, "OAK_SAPLING", 5, "DIAMOND", 10));
        assertArrayEquals(new int[] {4, 3}, result.counts());
        assertEquals(Map.of("OAK_SAPLING", 4, "BIRCH_SAPLING", 3), result.consumed());
        assertTrue(QuestProgress.done(quest, result.counts()));
    }

    @Test void emptyOrPartialInventoryNeverBecomesReady() {
        var quest = quest(deliver(Set.of("WHEAT"), 8));
        assertArrayEquals(new int[] {0}, QuestDeliveries.allocate(quest, Map.of()).counts());
        assertArrayEquals(new int[] {3}, QuestDeliveries.allocate(quest, Map.of("WHEAT", 3)).counts());
        assertArrayEquals(new int[] {8}, QuestDeliveries.allocate(quest, Map.of("WHEAT", 64)).counts());
    }

    private static QuestDefinition.Objective deliver(Set<String> targets, int count) {
        return new QuestDefinition.Objective(QuestDefinition.Type.DELIVER, targets, null, count, "Bring supplies");
    }

    private static QuestDefinition quest(QuestDefinition.Objective... objectives) {
        return new QuestDefinition("test", "test", "Supplies", "giver", "giver", 1, List.of(), List.of("Help."),
            List.of("Thanks."), List.of(objectives), new QuestDefinition.Rewards(0, Map.of(), Map.of(), List.of()), false);
    }
}
