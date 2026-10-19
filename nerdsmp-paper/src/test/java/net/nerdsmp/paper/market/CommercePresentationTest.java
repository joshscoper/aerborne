package net.nerdsmp.paper.market;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.api.service.MarketService.Outcome;
import net.nerdsmp.api.service.MarketService.Status;
import net.nerdsmp.paper.auction.Auction;
import net.nerdsmp.paper.auction.DurableAuctionService;
import net.nerdsmp.paper.presentation.Chat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** The commerce screens and messages follow docs/STYLE_GUIDE.md. */
final class CommercePresentationTest {
    private static final List<Path> COMMERCE_SOURCES = List.of(
        source("market", "MarketController.java"), source("market", "AuctionController.java"),
        source("economy", "PaymentController.java"), source("npc", "NpcVendorTrading.java"));

    private static Path source(String folder, String file) {
        return Path.of("src", "main", "java", "net", "nerdsmp", "paper", folder, file);
    }

    @Test
    void outcomesReadAsDonePendingOrRefused() {
        assertEquals(MarketController.Tone.SUCCESS, MarketController.tone(Outcome.SOLD));
        assertEquals(MarketController.Tone.SUCCESS, MarketController.tone(Outcome.RESERVED));
        assertEquals(MarketController.Tone.NOTICE, MarketController.tone(Outcome.PENDING));
        assertEquals(MarketController.Tone.NOTICE, MarketController.tone(Outcome.SAVE_FAILED));
        assertEquals(MarketController.Tone.ERROR, MarketController.tone(Outcome.INSUFFICIENT_FUNDS));
        assertEquals(MarketController.Tone.ERROR, MarketController.tone(Outcome.DENIED));
        assertEquals(MarketController.Tone.SUCCESS, MarketController.tone(DurableAuctionService.Outcome.BID_ACCEPTED));
        assertEquals(MarketController.Tone.NOTICE, MarketController.tone(DurableAuctionService.Outcome.REVIEW));
        assertEquals(MarketController.Tone.ERROR, MarketController.tone(DurableAuctionService.Outcome.STALE));
    }

    @Test
    void statusesAreSentencesNotEnumNames() {
        for (Status status : Status.values()) {
            String name = MarketController.statusName(status);
            assertFalse(name.contains("_") || name.equals(name.toUpperCase()), status.name());
            assertTrue(plain(MarketController.statusLine(status)).startsWith("Status "), status.name());
        }
        for (Auction.Status status : Auction.Status.values()) {
            String name = AuctionController.statusName(status);
            assertFalse(name.contains("_") || name.equals(name.toUpperCase()), status.name());
        }
    }

    @Test
    void reviewReasonsUseOneLengthRule() {
        assertFalse(MarketController.validReason(null));
        assertFalse(MarketController.validReason("x".repeat(MarketController.REVIEW_REASON_MIN - 1)));
        assertTrue(MarketController.validReason("x".repeat(MarketController.REVIEW_REASON_MIN)));
        assertTrue(MarketController.validReason("x".repeat(MarketController.REVIEW_REASON_MAX)));
        assertFalse(MarketController.validReason("x".repeat(MarketController.REVIEW_REASON_MAX + 1)));
        assertEquals(3, MarketController.REVIEW_REASON_MIN);
        assertEquals(180, MarketController.REVIEW_REASON_MAX);
        assertFalse(MarketController.validReason("checked\nlogs"));
    }

    @Test
    void vendorResolutionsIncludeRetry() {
        assertEquals(List.of("received", "missing", "removed", "kept", "retry"), MarketController.VENDOR_RESOLUTIONS);
    }

    @Test
    void syntaxShowsArgumentsAndTypesTheCommand() {
        String markup = Chat.syntax("/store sell <total price> [amount]");
        assertEquals("/store sell <total price> [amount]", plain(markup));
        assertTrue(markup.startsWith("<click:suggest_command:'/store sell '>"), markup);
    }

    @Test
    void pagesAlwaysHaveAtLeastOne() {
        assertEquals(1, MarketController.pages(0));
        assertEquals(1, MarketController.pages(21));
        assertEquals(2, MarketController.pages(22));
    }

    @Test
    void commerceCodeUsesTheChatKitAndOneMarksFormat() throws IOException {
        Pattern rawSend = Pattern.compile("\\.sendMessage\\(\"");
        Pattern rawMarks = Pattern.compile("\\+ \" Marks");
        Pattern plainColour = Pattern.compile("NamedTextColor");
        for (Path path : COMMERCE_SOURCES) {
            String code = Files.readString(path);
            assertFalse(rawSend.matcher(code).find(), path + " sends raw text; use Chat");
            assertFalse(rawMarks.matcher(code).find(), path + " formats Marks by hand; use Chat.marks");
            assertFalse(plainColour.matcher(code).find(), path + " uses a flat colour; use Chat and MenuKit");
        }
    }

    @Test
    void marketScreensUseTheSharedNavigation() throws IOException {
        for (Path path : List.of(source("market", "MarketController.java"), source("market", "AuctionController.java"))) {
            String code = Files.readString(path);
            assertFalse(code.contains("\"Close\"") || code.contains("\"Back\"") || code.contains("\"Previous\"") || code.contains("\"Next\""), path.toString());
            assertTrue(code.contains("MenuKit.back(\"World & Trade\")") || code.contains("MenuKit.back(HUB)"), path + " has no route back to the hub");
            assertTrue(code.contains("MenuKit.openConfirm"), path + " should confirm with the shared screen");
        }
    }

    private static String plain(String markup) {
        return PlainTextComponentSerializer.plainText().serialize(Chat.render(markup));
    }
}
