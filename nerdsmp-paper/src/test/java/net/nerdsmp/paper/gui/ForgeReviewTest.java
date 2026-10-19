package net.nerdsmp.paper.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForgeReviewTest {
    @Test void changingEitherInputOrSelectedGemRequiresANewQuote() {
        var quote = new ForgeReview<>("ruby+onyx sword", "20 dust", 1, "UNSOCKET", 100);
        assertTrue(quote.matches("ruby+onyx sword", "20 dust", 1, "UNSOCKET", 200));
        assertFalse(quote.matches("ruby sword", "20 dust", 1, "UNSOCKET", 200));
        assertFalse(quote.matches("ruby+onyx sword", "19 dust", 1, "UNSOCKET", 200));
        assertFalse(quote.matches("ruby+onyx sword", "20 dust", 0, "UNSOCKET", 200));
        assertFalse(quote.matches("ruby+onyx sword", "20 dust", 1, "APPLY", 200));
    }
    @Test void anExpiredReviewCannotAuthorizeAnOperation() {
        var quote = new ForgeReview<>("gear", "dust", 0, "UNSOCKET", 100);
        assertTrue(quote.matches("gear", "dust", 0, "UNSOCKET", 15_099));
        assertFalse(quote.matches("gear", "dust", 0, "UNSOCKET", 15_100));
    }
    @Test void paginationAndSelectionSurviveSocketRemovalWithoutEscapingLayout() {
        assertEquals(-1, ForgeSocketLayout.selected(2, 0));
        assertEquals(0, ForgeSocketLayout.selected(2, 1));
        assertEquals(1, ForgeSocketLayout.page(99, 8));
        assertEquals(0, ForgeSocketLayout.page(1, 7));
        for (int i=0;i<7;i++) {
            int slot=ForgeSocketLayout.slot(i);
            assertTrue(slot>=28 && slot<=34);
            assertNotEquals(ForgeSlotPolicy.PRIMARY_SLOT,slot);
            assertNotEquals(ForgeSlotPolicy.CATALYST_SLOT,slot);
            assertNotEquals(22,slot);
        }
        assertThrows(IllegalArgumentException.class,()->ForgeSocketLayout.slot(7));
    }
}
