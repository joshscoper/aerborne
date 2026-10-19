package net.nerdsmp.paper.engagement;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class DailyQuestRotationTest {
    @Test
    void rotationIsServerWideDeterministicAndCapped() {
        List<DailyQuestDefinition> pool = List.of(
            quest("one"), quest("two"), quest("three"), quest("four"), quest("five")
        );
        LocalDate date = LocalDate.of(2026, 9, 1);

        List<DailyQuestDefinition> first = DailyQuestRotation.select(pool, date, 3, 76123L);
        List<DailyQuestDefinition> repeated = DailyQuestRotation.select(pool, date, 3, 76123L);
        List<DailyQuestDefinition> nextDay = DailyQuestRotation.select(pool, date.plusDays(1), 3, 76123L);

        assertEquals(3, first.size());
        assertEquals(first, repeated);
        assertNotEquals(first, nextDay);
    }

    private DailyQuestDefinition quest(String id) {
        return new DailyQuestDefinition(id, id, "Do the thing", QuestAction.CRAFT_ITEM, 10, 100);
    }
}
