package net.nerdsmp.paper.quest;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class QuestContentTest {
    @Test void mainStoryPaysEnoughExperienceToUnlockItsNextChapterWithoutGrinding() throws Exception {
        QuestCatalog catalog = read("reach-quests.yml");
        QuestProgress log = new QuestProgress();
        long experience = 0;
        for (String id : java.util.List.of("reach_report_to_steward", "reach_the_guild_test", "reach_through_the_kingsgate",
            "quiet_fields_close_to_the_gate", "quiet_fields_wrong_scarecrows", "quiet_fields_warm_coal",
            "quiet_fields_three_marks_a_wheel", "quiet_fields_the_toll_captain", "quiet_fields_the_black_granary",
            "quiet_fields_down_the_grain_stair", "quiet_fields_the_sower_went_west")) {
            var quest = catalog.quests().get(id);
            int level = net.nerdsmp.api.model.AdventurerProgression.levelForExperience(experience);
            assertEquals(QuestProgress.Status.AVAILABLE, log.status(quest, level), id + " at level " + level);
            log.accept(quest);
            for (int i = 0; i < quest.objectives().size(); i++) log.advance(quest, i, quest.objectives().get(i).count());
            assertEquals(QuestProgress.Status.READY, log.status(quest, level));
            log.complete(quest);
            experience += quest.rewards().experience().values().stream().mapToLong(Long::longValue).sum();
        }
    }

    @Test void bundledQuestsParseAndReachConversationsHaveAuthoredResponses() throws Exception {
        QuestCatalog world = read("world-quests.yml"), reach = read("reach-quests.yml");
        assertTrue(world.quests().size() > 50);
        assertEquals(world.quests().size() + reach.quests().size(), world.merge(reach).quests().size());
        for (var quest : reach.quests().values()) {
            for (var objective : quest.objectives())
                if (objective.type() == QuestDefinition.Type.TALK) assertFalse(objective.dialogue().isEmpty(), quest.id());
            if (!quest.daily()) assertFalse(quest.rewards().items().isEmpty(), quest.id() + " needs a tangible payoff");
        }
        var grist = reach.quests().get("grist_for_the_mill");
        assertEquals(QuestDefinition.Type.DELIVER, grist.objectives().getFirst().type());
        var finale = reach.quests().get("quiet_fields_down_the_grain_stair");
        assertEquals("Grain-Stair Treads", finale.rewards().items().getFirst().name());
        assertFalse(finale.rewards().items().getFirst().lore().isEmpty());
    }

    @Test void rejectsBadDialogueAndRewardMetadata() throws Exception {
        String source = text("reach-quests.yml");
        assertThrows(IllegalArgumentException.class, () -> QuestCatalog.parse(source.replace("type: talk", "type: kill")));
        assertThrows(IllegalArgumentException.class, () -> QuestCatalog.parse(source.replace("\"name\":\"Grain-Stair Treads\"", "\"typo\":\"Grain-Stair Treads\"")));
    }

    private QuestCatalog read(String name) throws Exception { return QuestCatalog.parse(text(name)); }
    private String text(String name) throws Exception {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/content/" + name))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
