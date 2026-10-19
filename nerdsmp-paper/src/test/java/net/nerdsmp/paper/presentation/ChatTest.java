package net.nerdsmp.paper.presentation;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChatTest {
    private static String plain(String markup) {
        return PlainTextComponentSerializer.plainText().serialize(Chat.render(markup));
    }

    @Test
    void marksAreGroupedAndSingularForOne() {
        assertEquals("0 Marks", Chat.marks(0L));
        assertEquals("1 Mark", Chat.marks(1L));
        assertEquals("1,234 Marks", Chat.marks(1_234L));
        assertEquals("1,000,000,000 Marks", Chat.marks(1_000_000_000L));
        assertEquals("<gold>250 Marks</gold>", Chat.marksTag(250L));
        assertEquals("1.2K ✦", Chat.marksCompact(1_234L));
        assertEquals("999 ✦", Chat.marksCompact(999L));
    }

    @Test
    void quotesInCommandsDoNotBreakTheClickTag() {
        assertEquals("/msg O'Brien hi", plain(Chat.command("msg O'Brien hi")));
        assertEquals("Usage: /note O'Brien <text>", plain(Chat.usage("/note O'Brien <text>")));
    }

    @Test
    void permanentIsItsOwnAnswer() {
        assertTrue(Chat.isPermanent("perm"));
        assertTrue(Chat.isPermanent(" Forever "));
        assertFalse(Chat.isPermanent("7d"));
        assertFalse(Chat.isPermanent(null));
        assertEquals(Optional.empty(), Chat.duration("perm", ChronoUnit.MINUTES));
    }

    @Test
    void usageShowsAngleBracketsAndSuggestsTheBaseCommand() {
        String usage = Chat.usage("/jobs fulfill <order> <amount>");
        assertEquals("Usage: /jobs fulfill <order> <amount>", plain(usage));
        assertTrue(usage.contains("suggest_command:'/jobs fulfill '"), usage);
        assertTrue(Chat.usage("/grave [locate|recover]").contains("suggest_command:'/grave '"));
        assertTrue(Chat.usage("/menu").contains("suggest_command:'/menu '"));
    }

    @Test
    void syntaxIsUsageWithoutTheLabel() {
        String syntax = Chat.syntax("/store sell <total price> [amount]");
        assertEquals("/store sell <total price> [amount]", plain(syntax));
        assertTrue(syntax.startsWith("<click:suggest_command:'/store sell '>"), syntax);
        assertEquals("<gray>Usage:</gray> " + syntax, Chat.usage("/store sell <total price> [amount]"));
    }

    @Test
    void escapedPlayerTextIsShownLiterally() {
        assertEquals("<red>Steve</red>", plain(Chat.escape("<red>Steve</red>")));
    }

    @Test
    void commandsRenderAsTheirOwnText() {
        assertEquals("/island create", plain(Chat.command("island create")));
        assertEquals("/menu", plain(Chat.command("/menu")));
    }

    @Test
    void durationsAcceptUnitsAndBareNumbers() {
        assertEquals(Optional.of(Duration.ofMinutes(30)), Chat.duration("30m", ChronoUnit.MINUTES));
        assertEquals(Optional.of(Duration.ofHours(2)), Chat.duration("2H", ChronoUnit.MINUTES));
        assertEquals(Optional.of(Duration.ofDays(14)), Chat.duration("2w", ChronoUnit.MINUTES));
        assertEquals(Optional.of(Duration.ofMinutes(45)), Chat.duration("45", ChronoUnit.MINUTES));
        assertEquals(Optional.of(Duration.ofHours(24)), Chat.duration("24", ChronoUnit.HOURS));
        assertEquals(Optional.empty(), Chat.duration("0m", ChronoUnit.MINUTES));
        assertEquals(Optional.empty(), Chat.duration("-5", ChronoUnit.MINUTES));
        assertEquals(Optional.empty(), Chat.duration("soon", ChronoUnit.MINUTES));
        assertEquals(Optional.empty(), Chat.duration(null, ChronoUnit.MINUTES));
    }

    @Test
    void durationsPrintCompactly() {
        assertEquals("1h 30m", Chat.duration(Duration.ofMinutes(90)));
        assertEquals("1d 2h", Chat.duration(Duration.ofHours(26)));
        assertEquals("0s", Chat.duration(Duration.ZERO));
    }

    @Test
    void constantsAreHumanized() {
        assertEquals("not enough marks", Chat.humanize("NOT_ENOUGH_MARKS"));
    }
}
