package net.nerdsmp.paper.bazaar;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class BazaarBookTest {
    private static final UUID ALICE = UUID.randomUUID(), BOB = UUID.randomUUID(), CARA = UUID.randomUUID();

    private static BazaarBook.Order order(BazaarBook.Side side, UUID who, long price, int amount, long at) {
        return new BazaarBook.Order(UUID.randomUUID(), "wheat", side, who, price, amount, at, 0, 0);
    }

    @Test void instantBuysTakeTheCheapestOffersFirstAndSkipYourOwn() {
        BazaarBook book = new BazaarBook();
        book.add(order(BazaarBook.Side.SELL, ALICE, 5, 10, 1));
        book.add(order(BazaarBook.Side.SELL, BOB, 3, 4, 2));
        book.add(order(BazaarBook.Side.SELL, CARA, 3, 4, 1));
        var fills = book.plan("wheat", BazaarBook.Side.SELL, 10, ALICE);
        assertEquals(8, BazaarBook.units(fills), "Alice's own offer is skipped");
        assertEquals(CARA, fills.getFirst().order().account(), "same price: the older offer fills first");
        assertEquals(24, BazaarBook.total(fills));
        BazaarBook.reserve(fills);
        assertEquals(5, book.bestOffer("wheat"), "reserved units are off the book");
        BazaarBook.settleBuy(fills);
        assertEquals(5, book.bestOffer("wheat"));
        assertEquals(10, book.depth("wheat", BazaarBook.Side.SELL));
    }

    @Test void releasedReservationsGoBackOnTheBook() {
        BazaarBook book = new BazaarBook();
        book.add(order(BazaarBook.Side.SELL, BOB, 3, 4, 1));
        var fills = book.plan("wheat", BazaarBook.Side.SELL, 4, ALICE);
        BazaarBook.reserve(fills);
        assertEquals(0, book.depth("wheat", BazaarBook.Side.SELL));
        BazaarBook.release(fills);
        assertEquals(4, book.depth("wheat", BazaarBook.Side.SELL));
    }

    @Test void instantSellsFillTheBestBidsAndLeaveItemsToClaim() {
        BazaarBook book = new BazaarBook();
        var low = order(BazaarBook.Side.BUY, BOB, 2, 100, 1);
        var high = order(BazaarBook.Side.BUY, CARA, 4, 10, 2);
        book.add(low);
        book.add(high);
        var fills = book.plan("wheat", BazaarBook.Side.BUY, 15, ALICE);
        assertEquals(10 * 4 + 5 * 2, BazaarBook.total(fills));
        BazaarBook.settleSell(fills);
        assertEquals(10, high.claimable());
        assertEquals(5, low.claimable());
        assertFalse(high.done(), "filled but not yet claimed");
    }

    @Test void ordersNeverCrossTheBook() {
        BazaarBook book = new BazaarBook();
        book.add(order(BazaarBook.Side.SELL, BOB, 5, 10, 1));
        book.add(order(BazaarBook.Side.BUY, CARA, 3, 10, 1));
        assertNotNull(book.refuse("wheat", BazaarBook.Side.BUY, 5, 1, ALICE));
        assertNull(book.refuse("wheat", BazaarBook.Side.BUY, 4, 1, ALICE));
        assertNotNull(book.refuse("wheat", BazaarBook.Side.SELL, 3, 1, ALICE));
        assertNull(book.refuse("wheat", BazaarBook.Side.SELL, 4, 1, ALICE));
        assertNotNull(book.refuse("wheat", BazaarBook.Side.SELL, 0, 1, ALICE));
        assertNotNull(book.refuse("wheat", BazaarBook.Side.SELL, 9, BazaarBook.MAX_AMOUNT + 1, ALICE));
    }

    @Test void theTaxRoundsInTheBazaarsFavour() {
        assertEquals(98, BazaarBook.afterTax(100));
        assertEquals(0, BazaarBook.afterTax(1));
        assertEquals(99, BazaarBook.afterTax(100, BazaarBook.TAX / 2));
    }
}
