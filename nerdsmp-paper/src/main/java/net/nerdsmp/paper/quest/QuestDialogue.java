package net.nerdsmp.paper.quest;

import net.nerdsmp.paper.npc.NpcContent;
import net.nerdsmp.paper.presentation.Chat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Generated quest pages share the normal NPC nonce, distance and line-of-sight checks. */
public final class QuestDialogue {
    public static final String DETAIL = "quest_detail:";
    public static final String PAGE = "quest_page:";
    public static final String REPORT = "quest_report:";
    public static final String SERVICES = "quest_services";
    public static final String ACTION = "quest_action:";

    public record Topic(String id, String title, QuestProgress.Status status, boolean giver, boolean turnIn,
                        boolean talk, List<String> body, List<String> report) {
        String label() {
            return switch (status) {
                case AVAILABLE -> "! " + title;
                case READY -> (turnIn ? "? Turn in: " : "About: ") + title;
                case COMPLETED -> "After: " + title;
                default -> (talk ? "Discuss: " : "Progress: ") + title;
            };
        }
    }

    /** NPC chat and the client feed take literal text, unlike the inventory board's MiniMessage lore. */
    static List<String> plain(List<String> richLines) {
        return richLines.stream().map(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(Chat.render(line))).toList();
    }

    public static NpcContent.DialogueNode root(NpcContent.DialogueNode authored, List<Topic> topics) {
        if (topics.isEmpty()) return authored;
        List<NpcContent.DialogueChoice> choices = new ArrayList<>();
        topics.stream().limit(3).forEach(topic -> choices.add(link(DETAIL + topic.id(), topic.label(), DETAIL + topic.id())));
        if (topics.size() > 3) choices.add(link("more_quests", "Other quests...", PAGE + "1"));
        choices.add(link("other_topics", "Let's talk about something else.", SERVICES));
        return new NpcContent.DialogueNode(authored.id(), authored.text(), choices);
    }

    public static NpcContent.DialogueNode page(String id, String root, List<Topic> topics) {
        if (id.startsWith(PAGE)) {
            int page;
            try { page = Math.max(0, Integer.parseInt(id.substring(PAGE.length()))); }
            catch (NumberFormatException invalid) { return null; }
            int pages = Math.max(1, (topics.size() + 2) / 3);
            page = Math.min(page, pages - 1);
            List<NpcContent.DialogueChoice> choices = new ArrayList<>();
            topics.stream().skip(page * 3L).limit(3).forEach(topic ->
                choices.add(link(DETAIL + topic.id(), topic.label(), DETAIL + topic.id())));
            if (pages > 1) choices.add(link("more_quests", "More quests...", PAGE + ((page + 1) % pages)));
            choices.add(link("back", "Back to our conversation.", root));
            return new NpcContent.DialogueNode(id, List.of("Which task did you want to discuss?"), choices);
        }
        boolean report = id.startsWith(REPORT);
        if (!report && !id.startsWith(DETAIL)) return null;
        Topic topic = topics.stream().filter(candidate -> candidate.id().equals(id.substring((report ? REPORT : DETAIL).length()))).findFirst().orElse(null);
        if (topic == null) return null;
        if (report) return new NpcContent.DialogueNode(id, topic.report(), List.of(
            link("continue", "I understand. About that task...", DETAIL + topic.id()),
            link("back", "Let's discuss something else.", root)));
        List<NpcContent.DialogueChoice> choices = new ArrayList<>();
        if (topic.status() == QuestProgress.Status.AVAILABLE && topic.giver())
            choices.add(action("accept", topic.id(), "I'll take care of it."));
        if (topic.talk()) choices.add(action("talk", topic.id(), "Tell me what you know."));
        if (topic.status() == QuestProgress.Status.READY && topic.turnIn())
            choices.add(action("complete", topic.id(), "It's done. Here's my report."));
        if (topic.status() == QuestProgress.Status.ACTIVE || topic.status() == QuestProgress.Status.READY)
            choices.add(action("track", topic.id(), "Show me the way."));
        choices.add(link("back", "Let's discuss something else.", root));
        return new NpcContent.DialogueNode(id, topic.body(), choices);
    }

    private static NpcContent.DialogueChoice action(String action, String id, String label) {
        return link(ACTION + action + ":" + id, label, null);
    }

    private static NpcContent.DialogueChoice link(String id, String label, String next) {
        return new NpcContent.DialogueChoice(id, label, next, false, false, Duration.ZERO,
            NpcContent.Requirements.none(), List.of());
    }

    private QuestDialogue() { }
}
