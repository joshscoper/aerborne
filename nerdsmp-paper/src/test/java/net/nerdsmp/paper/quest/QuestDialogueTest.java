package net.nerdsmp.paper.quest;

import net.nerdsmp.paper.npc.NpcContent;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class QuestDialogueTest {
    @Test void generatedPagesRenderAsLiteralDialogueWithoutExposingFormattingTags() {
        var lines = QuestDialogue.plain(List.of("<gold>Supplies</gold>", "<white>Bring wheat</white> <yellow>3/8</yellow>",
            " <white>1× Hale's Field Blade</white>"));
        var rendered = net.nerdsmp.paper.npc.chat.ChatDialogueRenderer.render(java.util.UUID.randomUUID(), "Hale", "Warden", lines, List.of());
        String text = rendered.stream().map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize)
            .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(text.contains("Bring wheat 3/8"));
        assertTrue(text.contains("Hale's Field Blade"));
        assertFalse(text.contains("<gold>"));
        assertFalse(text.contains("<white>"));
    }

    @Test void offersPreviewBeforeAcceptanceAndHandinsOnlyAppearAtTheRecipient() {
        var offer = topic("offer", QuestProgress.Status.AVAILABLE, true, false, false);
        var root = QuestDialogue.root(new NpcContent.DialogueNode("root", List.of("Hello."), List.of()), List.of(offer));
        assertEquals(QuestDialogue.DETAIL + "offer", root.choices().getFirst().nextNode());
        var page = QuestDialogue.page(QuestDialogue.DETAIL + "offer", "root", List.of(offer));
        assertTrue(page.choices().stream().anyMatch(c -> c.id().equals(QuestDialogue.ACTION + "accept:offer")));
        var ready = topic("ready", QuestProgress.Status.READY, true, false, false);
        assertFalse(QuestDialogue.page(QuestDialogue.DETAIL + "ready", "root", List.of(ready)).choices().stream()
            .anyMatch(c -> c.id().contains("complete:")));
        ready = topic("ready", QuestProgress.Status.READY, false, true, false);
        assertTrue(QuestDialogue.page(QuestDialogue.DETAIL + "ready", "root", List.of(ready)).choices().stream()
            .anyMatch(c -> c.id().equals(QuestDialogue.ACTION + "complete:ready")));
    }

    @Test void conversationObjectivesGetTheirOwnReplyAndReportPage() {
        var talk = topic("tam", QuestProgress.Status.ACTIVE, false, false, true);
        var page = QuestDialogue.page(QuestDialogue.DETAIL + "tam", "root", List.of(talk));
        assertTrue(page.choices().stream().anyMatch(c -> c.id().equals(QuestDialogue.ACTION + "talk:tam")));
        assertFalse(page.choices().stream().anyMatch(c -> c.id().contains("accept:")));
        assertEquals(List.of("I heard the singing."), QuestDialogue.page(QuestDialogue.REPORT + "tam", "root", List.of(talk)).text());
    }

    @Test void allTopicsStayReachableWithinFiveRepliesAndServicesArePreserved() {
        var topics = IntStream.range(0, 11).mapToObj(i -> topic("quest_" + i, QuestProgress.Status.ACTIVE, true, false, false)).toList();
        var authored = new NpcContent.DialogueNode("root", List.of("Hello."), List.of());
        var root = QuestDialogue.root(authored, topics);
        assertEquals(5, root.choices().size());
        assertTrue(root.choices().stream().anyMatch(c -> QuestDialogue.SERVICES.equals(c.nextNode())));
        var seen = new java.util.HashSet<String>();
        for (int page = 0; page < 4; page++) {
            var node = QuestDialogue.page(QuestDialogue.PAGE + page, "root", topics);
            assertTrue(node.choices().size() <= 5);
            node.choices().stream().map(NpcContent.DialogueChoice::nextNode).filter(id -> id.startsWith(QuestDialogue.DETAIL)).forEach(seen::add);
        }
        assertEquals(11, seen.size());
        assertNull(QuestDialogue.page(QuestDialogue.DETAIL + "missing", "root", topics));
        assertNull(QuestDialogue.page(QuestDialogue.PAGE + "bad", "root", topics));
    }

    private static QuestDialogue.Topic topic(String id, QuestProgress.Status status, boolean giver, boolean turnIn, boolean talk) {
        return new QuestDialogue.Topic(id, id, status, giver, turnIn, talk, List.of("A task", "Reward: shield"), List.of("I heard the singing."));
    }
}
