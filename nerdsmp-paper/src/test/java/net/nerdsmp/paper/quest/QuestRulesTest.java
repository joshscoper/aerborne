package net.nerdsmp.paper.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QuestRulesTest {
    private static final String CHAIN = """
        schema-version: 1
        quests:
          first:
            chain: farm
            title: First
            giver: odo
            offer: ["Help."]
            complete: ["Thanks."]
            objectives:
              - {type: kill, targets: ["custom:gnawer"], zone: fields, count: 3, description: Defeat gnawers}
              - {type: deliver, targets: [wheat], count: 2, description: Bring wheat}
            rewards: {marks: 10, xp: {hunting: 5}, reputation: {wardens: 3}, items: ["material:bread 2"]}
          second:
            chain: farm
            title: Second
            giver: odo
            turn-in: brenna
            level: 3
            requires: [first]
            offer: ["More."]
            complete: ["Done."]
            objectives:
              - {type: visit, zone: fields, description: Reach the fields}
        """;

    @Test
    void parsesObjectivesRewardsAndChains() {
        QuestCatalog catalog = QuestCatalog.parse(CHAIN);
        QuestDefinition first = catalog.quests().get("first");
        assertEquals("odo", first.turnIn());
        assertEquals(QuestDefinition.Type.DELIVER, first.objectives().get(1).type());
        assertTrue(first.objectives().get(1).targets().contains("WHEAT"), "materials are normalised to upper case");
        assertEquals(10, first.rewards().marks());
        assertEquals(5L, first.rewards().experience().get("HUNTING"));
        assertEquals(2, first.rewards().items().getFirst().amount());
        assertEquals("brenna", catalog.quests().get("second").turnIn());
        assertEquals(2, catalog.offeredBy("odo").size());
        assertEquals(1, catalog.offeredBy("brenna").size());
    }

    @Test
    void progressFollowsPrerequisitesLevelsAndObjectives() {
        QuestCatalog catalog = QuestCatalog.parse(CHAIN);
        QuestDefinition first = catalog.quests().get("first"), second = catalog.quests().get("second");
        QuestProgress log = new QuestProgress();
        assertEquals(QuestProgress.Status.AVAILABLE, log.status(first, 1));
        assertEquals(QuestProgress.Status.LOCKED, log.status(second, 5));
        assertTrue(log.accept(first));
        assertFalse(log.accept(first));
        assertFalse(log.advance(first, 0, 2));
        assertEquals(QuestProgress.Status.ACTIVE, log.status(first, 1));
        assertTrue(log.advance(first, 0, 5));
        assertEquals(3, log.counts(first)[0], "progress never overshoots the target");
        // Deliveries are checked against the inventory at turn-in, so they never block readiness.
        assertEquals(QuestProgress.Status.READY, log.status(first, 1));
        log.complete(first);
        assertEquals(QuestProgress.Status.COMPLETED, log.status(first, 1));
        assertEquals(QuestProgress.Status.LOCKED, log.status(second, 2), "level 3 is required");
        assertEquals(QuestProgress.Status.AVAILABLE, log.status(second, 3));
    }

    @Test
    void dailyQuestsComeBackEachUtcDay() {
        QuestCatalog catalog = QuestCatalog.parse(CHAIN + """
              cull:
                chain: farm
                title: Cull
                giver: odo
                repeat: daily
                requires: [first]
                offer: ["Again."]
                complete: ["Again tomorrow."]
                objectives:
                  - {type: kill, targets: ["custom:gnawer"], count: 2, description: Cull gnawers}
            """);
        QuestDefinition first = catalog.quests().get("first"), cull = catalog.quests().get("cull");
        assertTrue(cull.daily());
        assertFalse(first.daily());
        java.time.LocalDate monday = java.time.LocalDate.of(2026, 10, 19), tuesday = monday.plusDays(1);
        QuestProgress log = new QuestProgress();
        assertEquals(QuestProgress.Status.LOCKED, log.status(cull, 1, monday), "it still needs its story quest");
        log.accept(first);
        log.complete(first);
        assertTrue(log.accept(cull, monday));
        log.advance(cull, 0, 2);
        log.complete(cull, monday);
        assertEquals(QuestProgress.Status.COMPLETED, log.status(cull, 1, monday));
        assertFalse(log.accept(cull, monday), "once per day");
        assertEquals(QuestProgress.Status.AVAILABLE, log.status(cull, 1, tuesday));
        assertTrue(log.accept(cull, tuesday));
        assertFalse(log.completed().contains("cull"), "dailies never join the permanent record");
    }

    @Test
    void rejectsBrokenContent() {
        assertThrows(IllegalArgumentException.class, () -> QuestCatalog.parse(CHAIN.replace("requires: [first]", "requires: [missing]")));
        assertThrows(IllegalArgumentException.class, () -> QuestCatalog.parse(CHAIN.replace("type: visit", "type: teleport")));
        assertThrows(IllegalArgumentException.class, () -> QuestCatalog.parse(CHAIN.replace("{type: visit, zone: fields,", "{type: visit,")));
        assertThrows(IllegalArgumentException.class, () -> QuestCatalog.parse(CHAIN.replace("xp: {hunting: 5}", "xp: {juggling: 5}")));
        assertThrows(IllegalArgumentException.class, () -> QuestCatalog.parse(CHAIN.replace("    chain: farm\n    title: First", "    chain: farm\n    title: First\n    requires: [second]")));
        assertThrows(IllegalArgumentException.class, () -> QuestCatalog.parse(CHAIN.replace("description: Reach the fields", "description: Reach the fields, colour: red")));
        assertThrows(IllegalArgumentException.class, () -> QuestCatalog.parse(CHAIN.replace("    chain: farm\n    title: First", "    chain: farm\n    title: First\n    repeat: weekly")));
        assertThrows(IllegalArgumentException.class, () -> QuestCatalog.parse(CHAIN.replace("    chain: farm\n    title: First", "    chain: farm\n    title: First\n    repeat: daily")),
            "a story quest can't require a daily one");
    }
}
