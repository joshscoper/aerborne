package net.nerdsmp.paper.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MenuKitTest {
    @Test
    void navigationUsesTheBottomRowOfEverySize() {
        assertEquals(List.of(45, 48, 49, 53), slots(54));
        assertEquals(List.of(36, 39, 40, 44), slots(45));
        assertEquals(List.of(18, 21, 22, 26), slots(27));
        assertThrows(IllegalArgumentException.class, () -> MenuKit.closeSlot(50));
    }

    @Test
    void hintsEndTheLoreAfterABlankLine() {
        assertEquals(List.of("<gray>Detail</gray>", "", "<yellow>Click to open.</yellow>"),
            MenuKit.withHint(List.of("<gray>Detail</gray>"), "open"));
        assertEquals(List.of("<yellow>Click to open.</yellow>"), MenuKit.withHint(List.of(), "open"));
    }

    @Test
    void pageLinesCountFromOne() {
        assertEquals("<gray>Page 1/3</gray>", MenuKit.pageLine(0, 3));
        assertEquals("<gray>Page 1/1</gray>", MenuKit.pageLine(0, 0));
    }

    private static List<Integer> slots(int size) {
        return List.of(MenuKit.previousSlot(size), MenuKit.backSlot(size), MenuKit.closeSlot(size), MenuKit.nextSlot(size));
    }
}
