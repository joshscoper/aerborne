package net.nerdsmp.paper.item;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemTooltipFormattingTest {
    @Test
    void colorScopesCloseNamedAndGradientTags() {
        assertEquals("<red>[*] Ruby IV</red>", PaperItemService.colored("<red>", "[*] Ruby IV"));
        assertEquals(
            "<gradient:#ff70f6:#67e8f9>[*] Prismatic Gem IV</gradient>",
            PaperItemService.colored("<gradient:#ff70f6:#67e8f9>", "[*] Prismatic Gem IV")
        );

        MiniMessage strict = MiniMessage.builder().strict(true).build();
        assertDoesNotThrow(() -> strict.deserialize(PaperItemService.TOOLTIP_DIVIDER));
        assertDoesNotThrow(() -> strict.deserialize(PaperItemService.colored("<red>", "[*] Ruby IV")));
        assertDoesNotThrow(() -> strict.deserialize(
            PaperItemService.colored("<gradient:#ff70f6:#67e8f9>", "[*] Prismatic Gem IV")
        ));
    }

    @Test
    void layoutFurnitureStaysInsideAscii() {
        List<String> furniture = List.of("----------------------------", "[ ]", "[*]", "[!]", "+", ">", "|");
        assertTrue(furniture.stream().flatMapToInt(String::chars).allMatch(character -> character <= 127));
    }

    @Test
    void generatedMaterialNamesDoNotLeakEnumFormatting() {
        assertEquals("Diamond Sword", PaperItemService.titleCase("DIAMOND_SWORD"));
        assertEquals("Riftglass Edge", PaperItemService.titleCase("riftglass-edge"));
    }

    @Test
    void statLabelsUsePlayerLanguageAndSingleUnits() {
        assertTrue(PaperItemService.statLine("skill-xp-percent", 12).contains("Skill XP:</gray> <white>+12%"));
        assertTrue(PaperItemService.statLine("durability-save-percent", 36).contains("Durability Protection"));
        assertTrue(PaperItemService.statLine("life-steal-percent", 2.4).contains("Life Steal:</gray> <white>+2.4%"));
        assertTrue(PaperItemService.statLine("movement-speed-percent", 5).contains("Movement Speed:</gray> <white>+5%"));
        assertTrue(PaperItemService.statLine("damage", 8).contains("Damage:</gray> <white>8</white>"));
    }
}
