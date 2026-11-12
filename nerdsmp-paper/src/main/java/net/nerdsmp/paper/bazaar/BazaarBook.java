package net.nerdsmp.paper.bazaar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Bazaar's order book, kept apart from Bukkit so its rules can be tested: buy orders (Marks held in escrow) and sell
 * offers (items held), per product. Orders never cross: a buy order must bid under the cheapest offer and a sell offer
 * must ask over the best bid, so trades only happen when someone buys or sells instantly against the book.
 */
public final class BazaarBook {
    public enum Side { BUY, SELL }

    /** One standing order. {@code reserved} is held by an instant trade still waiting on its payment. */
    public static final class Order {
        final UUID id; final String product; final Side side; final UUID account; final long price; final int amount; final long createdAt;
        int filled, reserved, claimable;
        public Order(UUID id, String product, Side side, UUID account, long price, int amount, long createdAt, int filled, int claimable) {
            this.id = id; this.product = product; this.side = side; this.account = account; this.price = price; this.amount = amount;
            this.createdAt = createdAt; this.filled = filled; this.claimable = claimable;
        }
        public UUID id() { return id; }
        public String product() { return product; }
        public Side side() { return side; }
        public UUID account() { return account; }
        public long price() { return price; }
        public int amount() { return amount; }
        public int filled() { return filled; }
        public int claimable() { return claimable; }
        public long createdAt() { return createdAt; }
        /** How much of the order is still open to trade. */
        public int open() { return amount - filled - reserved; }
        public boolean done() { return filled >= amount && claimable <= 0 && reserved <= 0; }
    }

    /** One part of an instant trade: so many from one order at its price. */
    public record Fill(Order order, int amount) {
        public long cost() { return order.price * amount; }
    }

    /** The Bazaar's cut of every sale, as a fraction: a Marks sink. */
    public static final double TAX = 0.0125;
    public static final int MAX_ORDERS = 14;
    public static final int MAX_AMOUNT = 71_680;
    public static final long MAX_PRICE = 1_000_000;

    private final Map<String, List<Order>> book = new LinkedHashMap<>();

    public List<Order> orders(String product) { return book.computeIfAbsent(product, ignored -> new ArrayList<>()); }

    public List<Order> all() {
        List<Order> all = new ArrayList<>();
        book.values().forEach(all::addAll);
        return all;
    }

    public List<Order> of(UUID account) { return all().stream().filter(o -> o.account.equals(account)).toList(); }

    public Order find(UUID id) { return all().stream().filter(o -> o.id.equals(id)).findFirst().orElse(null); }

    public void add(Order order) { orders(order.product).add(order); }

    public void remove(Order order) { orders(order.product).remove(order); }

    /** Sell offers, cheapest (then oldest) first; buy orders, highest (then oldest) first. */
    public List<Order> side(String product, Side side) {
        Comparator<Order> byPrice = side == Side.SELL ? Comparator.comparingLong(Order::price) : Comparator.comparingLong(Order::price).reversed();
        return orders(product).stream().filter(o -> o.side == side && o.open() > 0).sorted(byPrice.thenComparingLong(Order::createdAt)).toList();
    }

    /** The cheapest a unit can be bought for right now, or -1. */
    public long bestOffer(String product) { return side(product, Side.SELL).stream().mapToLong(Order::price).findFirst().orElse(-1); }
    /** The most a unit can be sold for right now, or -1. */
    public long bestBid(String product) { return side(product, Side.BUY).stream().mapToLong(Order::price).findFirst().orElse(-1); }
    /** How many units are on offer, or wanted, in all. */
    public int depth(String product, Side side) { return side(product, side).stream().mapToInt(Order::open).sum(); }

    /** The fills an instant buy (against offers) or sell (against bids) of up to {@code amount} would make. */
    public List<Fill> plan(String product, Side against, int amount, UUID trader) {
        List<Fill> fills = new ArrayList<>();
        int left = amount;
        for (Order order : side(product, against)) {
            if (left <= 0) break;
            if (order.account.equals(trader)) continue; // nobody trades with themself
            int take = Math.min(left, order.open());
            fills.add(new Fill(order, take));
            left -= take;
        }
        return fills;
    }

    public static long total(List<Fill> fills) { return fills.stream().mapToLong(Fill::cost).sum(); }
    public static int units(List<Fill> fills) { return fills.stream().mapToInt(Fill::amount).sum(); }
    /** What a seller keeps from a sale after the Bazaar's cut (rounded in the Bazaar's favour, never below zero). */
    public static long afterTax(long gross) { return afterTax(gross, TAX); }
    public static long afterTax(long gross, double tax) { return Math.max(0, gross - (long) Math.ceil(gross * tax)); }

    public static void reserve(List<Fill> fills) { fills.forEach(f -> f.order.reserved += f.amount); }
    public static void release(List<Fill> fills) { fills.forEach(f -> f.order.reserved = Math.max(0, f.order.reserved - f.amount)); }
    /** Settles an instant buy that's been paid for: offers fill, their reservation lifts. */
    public static void settleBuy(List<Fill> fills) { fills.forEach(f -> { f.order.reserved = Math.max(0, f.order.reserved - f.amount); f.order.filled += f.amount; }); }
    /** Settles an instant sell: bids fill and their buyers have the items to claim. */
    public static void settleSell(List<Fill> fills) { fills.forEach(f -> { f.order.filled += f.amount; f.order.claimable += f.amount; }); }

    /** Why a new order can't stand, or null: it would cross the book, or its numbers are out of range. */
    public String refuse(String product, Side side, long price, int amount, UUID account) {
        if (price < 1 || price > MAX_PRICE) return "Price each must be between 1 and " + MAX_PRICE + " Marks.";
        if (amount < 1 || amount > MAX_AMOUNT) return "Amount must be between 1 and " + MAX_AMOUNT + ".";
        if (of(account).size() >= MAX_ORDERS) return "You already have " + MAX_ORDERS + " orders. Claim or cancel some first.";
        if (side == Side.BUY) {
            long offer = bestOffer(product);
            if (offer > 0 && price >= offer) return "Someone sells for " + offer + " each. Buy instantly instead, or bid lower.";
        } else {
            long bid = bestBid(product);
            if (bid > 0 && price <= bid) return "Someone buys for " + bid + " each. Sell instantly instead, or ask higher.";
        }
        return null;
    }
}
