package net.nerdsmp.paper.item;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TooltipWrapTest {
    private static String plain(String markup) {
        return markup.replaceAll("<[^>]+>", "").replace("\\", "");
    }

    @Test void aShortLineIsLeftAlone() {
        assertEquals(List.of("<gray>Still warm.</gray>"), TooltipWrap.wrap("<gray>Still warm.</gray>"));
    }

    @Test void aLongLineBreaksBetweenWordsAndKeepsItsColour() {
        var lines = TooltipWrap.wrap("<gray>The Warden's own axe. There are still chips of the last visitor in the edge.</gray>", 30);
        assertTrue(lines.size() > 1);
        for (String line : lines) {
            assertTrue(plain(line).length() <= 30, line);
            assertTrue(line.startsWith("<gray>") && line.endsWith("</gray>"), line);
            assertTrue(!plain(line).startsWith(" "), line);
        }
        assertEquals("The Warden's own axe. There are still chips of the last visitor in the edge.", String.join(" ", lines.stream().map(TooltipWrapTest::plain).toList()));
    }

    @Test void nestedAndArgumentTagsCloseAndReopenInOrder() {
        var lines = TooltipWrap.wrap("<color:#ff8000><bold>one two three four</bold></color> <gray>af</gray>", 9);
        assertEquals("<color:#ff8000><bold>one two</bold></color>", lines.getFirst());
        assertEquals("<color:#ff8000><bold>three</bold></color>", lines.get(1));
        assertEquals("<color:#ff8000><bold>four</bold></color> <gray>af</gray>", lines.get(2));
    }

    @Test void escapedTagsAreTextNotMarkup() {
        var lines = TooltipWrap.wrap("<gray>a \\<red> b</gray>", 20);
        assertEquals(List.of("<gray>a \\<red> b</gray>"), lines);
    }

    @Test void aWordLongerThanTheLineStaysWhole() {
        assertEquals(List.of("<gray>tiny</gray>", "<gray>extraordinarily</gray>"), TooltipWrap.wrap("<gray>tiny extraordinarily</gray>", 6));
    }

    @Test void plainBlankLinesSurvive() {
        assertEquals(List.of(""), TooltipWrap.wrap(""));
    }
}
