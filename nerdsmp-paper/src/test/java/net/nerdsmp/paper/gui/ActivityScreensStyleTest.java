package net.nerdsmp.paper.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Activities, stations and social screens stay on the shared chat and menu kit (docs/STYLE_GUIDE.md). */
final class ActivityScreensStyleTest {
    private static final List<String> FILES = List.of(
        "activity/FishingService.java", "activity/ExcavationService.java", "alchemy/AlchemyService.java",
        "item/KitchenService.java", "item/RepairService.java", "item/IdentificationService.java",
        "player/BankService.java", "achievement/AchievementService.java", "leaderboard/LeaderboardService.java",
        "npc/NpcManager.java", "social/SocialManager.java", "trade/TradeManager.java", "chat/ChannelChatManager.java");
    /** {@code + " Marks"} builds an amount without thousands separators; Chat.marks does it once. */
    private static final Pattern RAW_MARKS = Pattern.compile("\\+ ?\" Marks");
    /** Unstyled white chat text. */
    private static final Pattern PLAIN_CHAT = Pattern.compile("\\.sendMessage\\(\"");

    @Test
    void chatUsesTheSharedPaletteAndRefusals() throws IOException {
        for (String file : FILES) {
            String source = source(file);
            assertFalse(RAW_MARKS.matcher(source).find(), file + " formats Marks by hand");
            assertFalse(PLAIN_CHAT.matcher(source).find(), file + " sends unstyled chat");
            assertFalse(source.contains("account is still loading"), file + " words the loading refusal itself");
            assertFalse(source.contains("NerdSMP"), file + " hard-codes the brand");
        }
    }

    @Test
    void screensOfferARouteBackToTheirHubPage() throws IOException {
        assertTrue(source("activity/FishingService.java").contains("MenuKit.back(\"Adventure\")"));
        assertTrue(source("activity/ExcavationService.java").contains("MenuKit.back(\"Adventure\")"));
        assertTrue(source("achievement/AchievementService.java").contains("MenuKit.back(\"Adventure\")"));
        assertTrue(source("alchemy/AlchemyService.java").contains("MenuKit.back(\"Workshop\")"));
        assertTrue(source("item/KitchenService.java").contains("MenuKit.back(\"Workshop\")"));
        assertTrue(source("player/BankService.java").contains("MenuKit.back(\"World & Trade\")"));
        assertTrue(source("leaderboard/LeaderboardService.java").contains("MenuKit.back(\"Character\")"));
        assertTrue(source("npc/NpcManager.java").contains("MenuKit.back(\"World & Trade\")"));
    }

    @Test
    void spendingMarksOrHandingOverItemsGoesThroughTheConfirmScreen() throws IOException {
        for (String file : List.of("item/IdentificationService.java", "player/BankService.java",
                "activity/FishingService.java", "activity/ExcavationService.java")) {
            assertTrue(source(file).contains("MenuKit.openConfirm("), file + " skips the shared confirm screen");
        }
        // Repair All keeps its own holder, because payment checks that the screen stays open, but uses the same slots.
        String repair = source("item/RepairService.java");
        assertTrue(repair.contains("MenuKit.CONFIRM_SLOT") && repair.contains("MenuKit.CONFIRM_CANCEL_SLOT"));
    }

    private static String source(String file) throws IOException {
        return Files.readString(Path.of("src", "main", "java", "net", "nerdsmp", "paper").resolve(file));
    }
}
