package net.nerdsmp.paper.npc;

import java.util.List;
import java.util.stream.IntStream;

/** Pure display decisions. The transaction layer still rechecks inventory, price, and limits. */
final class ShopControls {
    private static final long MAX_PRICE = 1_000_000_000L;

    enum Availability {
        READY("Ready to trade"),
        NOT_FOR_SALE("Not sold here"),
        NOT_ACCEPTED("Not bought here"),
        INSUFFICIENT_MARKS("Not enough Marks"),
        NO_INVENTORY_ROOM("Make room in your inventory"),
        INSUFFICIENT_ITEMS("You need a matching bundle"),
        DAILY_LIMIT_REACHED("Daily limit reached"),
        INVALID("This trade is unavailable");

        private final String reason;

        Availability(String reason) { this.reason = reason; }
        boolean available() { return this == READY; }
        String reason() { return reason; }
    }

    private ShopControls() { }

    static List<Integer> quantities(int configuredBundle, int itemMaximum) {
        if (configuredBundle < 1 || configuredBundle > 64 || itemMaximum < 1 || itemMaximum > 64) {
            throw new IllegalArgumentException("Shop quantity bounds must be 1..64");
        }
        return IntStream.of(1, configuredBundle, 16, 64).filter(amount -> amount <= itemMaximum)
            .distinct().sorted().boxed().toList();
    }

    static int quantitySlot(int index, int options) {
        if (options < 1 || options > 4 || index < 0 || index >= options) {
            throw new IllegalArgumentException("Shop quantity controls need one to four options");
        }
        return 22 - (options - 1) + index * 2;
    }

    static Availability buyAvailability(long balance, long price, boolean hasRoom) {
        if (balance < 0 || price < 0 || price > MAX_PRICE) return Availability.INVALID;
        if (price == 0) return Availability.NOT_FOR_SALE;
        if (balance < price) return Availability.INSUFFICIENT_MARKS;
        if (!hasRoom) return Availability.NO_INVENTORY_ROOM;
        return Availability.READY;
    }

    static Availability sellAvailability(int matchingItems, int bundle, int quota, long price) {
        if (matchingItems < 0 || bundle < 1 || bundle > 64 || quota < 0 || price < 0 || price > MAX_PRICE) {
            return Availability.INVALID;
        }
        if (price == 0) return Availability.NOT_ACCEPTED;
        if (quota == 0) return Availability.DAILY_LIMIT_REACHED;
        if (matchingItems < bundle) return Availability.INSUFFICIENT_ITEMS;
        return Availability.READY;
    }
}
