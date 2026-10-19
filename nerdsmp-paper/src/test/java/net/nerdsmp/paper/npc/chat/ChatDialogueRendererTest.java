package net.nerdsmp.paper.npc.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatDialogueRendererTest {
    private static final UUID TOKEN = UUID.randomUUID();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test void availableRepliesUseTokenBoundRunCommandsAndHelpfulHoverDetails() {
        List<Component> lines = render(List.of(new ChatDialogueRenderer.Choice("Show me your wares.", true,
            List.of("Opens the general store", "Prices depend on your standing"), List.of())));
        Component reply = find(lines, "[1]");
        assertEquals(NamedTextColor.AQUA, reply.color());
        assertEquals(ClickEvent.Action.RUN_COMMAND, reply.clickEvent().action());
        assertEquals("/npc reply " + TOKEN + " 1", command(reply));
        String hover = PLAIN.serialize((Component) reply.hoverEvent().value());
        assertTrue(hover.contains("Opens the general store"));
        assertTrue(hover.contains("Prices depend on your standing"));
        assertTrue(hover.contains("/npc reply 1"));
        assertFalse(PLAIN.serialize(reply).contains(TOKEN.toString()));
    }

    @Test void lockedRepliesHaveNoClickableCommandAndShowTheirReasons() {
        List<Component> lines = render(List.of(new ChatDialogueRenderer.Choice("I need your help.", false,
            List.of("Reward: 100 Marks"), List.of("Requires 50 reputation", "Already chosen"))));
        Component reply = find(lines, "[1]");
        assertEquals(NamedTextColor.DARK_GRAY, reply.color());
        assertNull(reply.clickEvent());
        String hover = PLAIN.serialize((Component) reply.hoverEvent().value());
        assertTrue(hover.contains("Requires 50 reputation"));
        assertTrue(hover.contains("Already chosen"));
        assertFalse(hover.contains("100 Marks"));
    }

    @Test void namesTitlesAndDialogueAreLiteralEvenIfTheyContainMiniMessageTags() {
        List<Component> lines = ChatDialogueRenderer.render(TOKEN, "<click:run_command:/op me>Merra</click>",
            "<red>Trader</red>", List.of("<hover:show_text:'bad'>Welcome.</hover>"), List.of());
        String rendered = String.join("\n", lines.stream().map(PLAIN::serialize).toList());
        assertTrue(rendered.contains("<click:run_command:/op me>"));
        assertTrue(rendered.contains("<red>Trader</red>"));
        assertTrue(rendered.contains("<hover:show_text:'bad'>Welcome.</hover>"));
        for (Component line : lines.subList(0, lines.size() - 1)) assertNull(line.clickEvent());
    }

    @Test void exitIsPresentAndTokenScopedEvenForANodeWithoutChoices() {
        Component exit = render(List.of()).getLast();
        assertEquals("  [Leave conversation]", PLAIN.serialize(exit));
        assertEquals("/npc bye " + TOKEN, command(exit));
        assertEquals(ClickEvent.Action.RUN_COMMAND, exit.clickEvent().action());
    }

    @Test void chatBodyIsBoundedToEightLinesAndFullTextIsAvailableOnHover() {
        String longParagraph = "The sky lanes carry rumors of travelers who never returned. ".repeat(30);
        List<Component> lines = ChatDialogueRenderer.render(TOKEN, "Merra", "Trader", List.of(longParagraph), List.of());
        List<Component> body = lines.subList(2, 2 + ChatDialogueRenderer.MAX_BODY_LINES);
        assertEquals(8, body.size());
        assertEquals(12, lines.size());
        assertTrue(PLAIN.serialize(body.getLast()).endsWith(" ..."));
        for (Component line : body) {
            assertTrue(PLAIN.serialize(line).length() <= ChatDialogueRenderer.WRAP_WIDTH + 2);
            assertNotNull(line.hoverEvent());
        }
        String fullText = PLAIN.serialize((Component) body.getFirst().hoverEvent().value());
        assertTrue(fullText.length() > 1500);
        assertTrue(fullText.contains("never returned."));
    }

    @Test void wrapSplitsWordsAndLongUnbrokenTextWithoutLosingCharacters() {
        assertEquals(List.of("abc de", "fgh ij"), ChatDialogueRenderer.wrap("abc de fgh ij", 6));
        assertEquals(List.of("abcd", "efgh", "ijk"), ChatDialogueRenderer.wrap("abcdefghijk", 4));
        assertEquals(List.of(""), ChatDialogueRenderer.wrap(" ", 4));
        assertThrows(IllegalArgumentException.class, () -> ChatDialogueRenderer.wrap("word", 0));
    }

    @Test void everyResponseIsNumberedAndTheResultIsImmutable() {
        List<ChatDialogueRenderer.Choice> choices = List.of("One", "Two", "Three", "Four", "Five").stream()
            .map(label -> new ChatDialogueRenderer.Choice(label, true, List.of(), List.of())).toList();
        List<Component> lines = render(choices);
        for (int number = 1; number <= 5; number++) {
            assertEquals("/npc reply " + TOKEN + " " + number, command(find(lines, "[" + number + "]")));
        }
        assertThrows(UnsupportedOperationException.class, () -> lines.clear());
        assertThrows(IllegalArgumentException.class, () -> ChatDialogueRenderer.render(TOKEN, "Merra", "Trader",
            List.of("Welcome"), List.of(choices.getFirst(), choices.getFirst(), choices.getFirst(),
                choices.getFirst(), choices.getFirst(), choices.getFirst())));
    }

    @Test void wrappingNeverSplitsSupplementaryCharacters() {
        String word = "abc\uD83D\uDC1Fdef\uD83D\uDC1Fghi";
        List<String> wrapped = ChatDialogueRenderer.wrap(word, 4);
        assertEquals(word, String.join("", wrapped));
        for (String line : wrapped) {
            assertFalse(Character.isLowSurrogate(line.charAt(0)));
            assertFalse(Character.isHighSurrogate(line.charAt(line.length() - 1)));
        }
        assertEquals(List.of("\uD83D\uDC1F", "\uD83D\uDC1F"), ChatDialogueRenderer.wrap("\uD83D\uDC1F\uD83D\uDC1F", 1));
    }

    @Test void controlsAndBlankTitlesDoNotCreateNewChatMessages() {
        List<Component> lines = ChatDialogueRenderer.render(TOKEN, "Merra\nTrader", " ",
            List.of("Welcome\n traveler.\u0000"), List.of());
        assertEquals("Merra Trader", PLAIN.serialize(lines.get(1)));
        assertEquals("  Welcome traveler.", PLAIN.serialize(lines.get(2)));
        assertFalse(PLAIN.serialize(lines.get(1)).contains("|"));
    }

    private static List<Component> render(List<ChatDialogueRenderer.Choice> choices) {
        return ChatDialogueRenderer.render(TOKEN, "Merra", "General Trader", List.of("What brings you here?"), choices);
    }

    private static Component find(List<Component> lines, String label) {
        return lines.stream().filter(line -> PLAIN.serialize(line).contains(label)).findFirst().orElseThrow();
    }

    private static String command(Component component) {
        return ((ClickEvent.Payload.Text) component.clickEvent().payload()).value();
    }
}
