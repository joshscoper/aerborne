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
        assertTrue(PaperItemService.statLine("skill-xp-percent", 12).contains("Skill XP:</gray> <green>+12%"));
        assertTrue(PaperItemService.statLine("durability-save-percent", 36).contains("Durability Protection"));
        assertTrue(PaperItemService.statLine("life-steal-percent", 2.4).contains("Life Steal:</gray> <red>+2.4%"));
        assertTrue(PaperItemService.statLine("movement-speed-percent", 5).contains("Movement Speed:</gray> <green>+5%"));
        assertEquals("<gray>Damage:</gray> <red>+8</red>", PaperItemService.statLine("damage", 8));
        assertEquals("<gray>Defense:</gray> <green>+6</green>", PaperItemService.statLine("defense", 6));
    }

    @Test
    void rollsShowWhereTheyLandedInTheirRange() {
        assertEquals("<red>[0%]</red>", PaperItemService.rollTag(0));
        assertEquals("<yellow>[50%]</yellow>", PaperItemService.rollTag(50));
        assertEquals("<green>[80%]</green>", PaperItemService.rollTag(80));
        assertEquals("<aqua>[100%]</aqua>", PaperItemService.rollTag(140));
        assertEquals(0, ItemAffix.BRUTAL.rollPercent(2.0D));
        assertEquals(50, ItemAffix.BRUTAL.rollPercent(4.0D));
        assertEquals(100, ItemAffix.BRUTAL.rollPercent(ItemAffix.BRUTAL.roll(1.0D)));
    }

    @Test
    void raritiesUseQualityColoursAndAHypixelFooter() {
        assertEquals("<color:#ff8000><bold>LEGENDARY HELMET</bold></color>",
            PaperItemService.rarityFooter("LEGENDARY", "Helmet"));
        assertEquals("<color:#0070dd>", PaperItemService.rarityColor("rare"));
        assertEquals("<color:#a335ee>", PaperItemService.rarityColor("EPIC"));
        var strict = net.kyori.adventure.text.minimessage.MiniMessage.builder().strict(true).build();
        for (String rarity : List.of("STARTER", "COMMON", "CRAFTED", "CURRENCY", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "RELIC"))
            assertDoesNotThrow(() -> strict.deserialize(PaperItemService.rarityFooter(rarity, "Sword")), rarity);
    }
}
