package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.*;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static net.nerdsmp.paper.npc.NpcMarket.*;
import static org.junit.jupiter.api.Assertions.*;

final class NpcMarketTest {
    @TempDir Path folder;
    final AccountId buyer = AccountId.random(), seller = AccountId.random();
    final Instant now = Instant.parse("2026-09-29T12:00:00Z");
    NpcContent.ShopProduct product(int amount, long buy, long sell) { return new NpcContent.ShopProduct("stone", Material.COBBLESTONE, null, amount, buy, sell, sell > 0 ? 100 : 0, 0, ""); }
    NpcContent content(NpcContent.ShopProduct... products) {
        var faction = new NpcContent.FactionDefinition(new FactionId("test"), "Test", "", List.of(new NpcContent.TierDefinition("neutral", "Neutral", "gray", 0, 1, true), new NpcContent.TierDefinition("honored", "Honored", "gold", 600, .76, true)));
        Map<String, NpcContent.ShopDefinition> shops = new HashMap<>(); for (int n = 0; n < products.length; n++) shops.put("shop" + n, new NpcContent.ShopDefinition("shop" + n, "Shop", List.of(products[n])));
        return new NpcContent(Map.of(faction.id(), faction), Map.of(), Map.of(), shops);
    }
    NpcContent.ReputationView standing(NpcContent content) { var f = content.factions().values().iterator().next(); return new NpcContent.ReputationView(f, f.tiers().getLast(), 600); }
    NpcMarket market() { return new NpcMarket(folder.resolve("market.yml").toFile()); }
    void finish(NpcMarket market, Trade trade) {
        if (trade.quote().side() == Side.BUY) { market.transition(trade.id(), Phase.RESERVED, Phase.PAID); market.transition(trade.id(), Phase.PAID, Phase.INVENTORY); }
        else { market.transition(trade.id(), Phase.RESERVED, Phase.INVENTORY); market.transition(trade.id(), Phase.INVENTORY, Phase.ESCROWED); }
        market.complete(trade.id());
    }
    @Test void demandRaisesAskAndSupplyLowersItAcrossShops() {
        var m = market(); var p = product(64, 1000, 300); var c = content(p); var s = standing(c);
        var first = m.quote(c, p, s, Side.BUY, now); finish(m, m.begin(buyer, "a", "stone", first, now));
        var second = m.quote(c, p, s, Side.BUY, now); assertTrue(second.marks() > first.marks());
        finish(m, m.begin(seller, "b", "stone", m.quote(c, p, s, Side.SELL, now), now));
        assertEquals(first.marks(), m.quote(c, p, s, Side.BUY, now).marks());
        assertEquals(m.trend(p), market().trend(p));
    }
    @Test void sameCommodityAndAccountCannotSettleConcurrently() {
        var m = market(); var p = product(16, 160, 50); var c = content(p); var q = m.quote(c, p, standing(c), Side.BUY, now);
        var trade = m.begin(buyer, "a", "stone", q, now);
        assertThrows(IllegalArgumentException.class, () -> m.begin(seller, "b", "stone", q, now));
        assertEquals(List.of(trade), market().pending()); m.cancel(trade.id());
        assertDoesNotThrow(() -> m.begin(seller, "b", "stone", q, now));
    }
    @Test void staleAndExpiredQuotesAreRejectedBeforePayment() {
        var m = market(); var p = product(16, 160, 50); var c = content(p); var q = m.quote(c, p, standing(c), Side.BUY, now);
        assertThrows(IllegalArgumentException.class, () -> m.begin(buyer, "a", "stone", q, now.plusSeconds(30)));
        finish(m, m.begin(buyer, "a", "stone", q, now));
        assertThrows(IllegalArgumentException.class, () -> m.begin(buyer, "a", "stone", q, now));
    }
    @Test void crossingVendorsAndBundleSizesCannotTurnRoundTripsIntoProfit() {
        var m = market(); var cheap = product(64, 100, 40); var expensive = product(16, 1000, 900); var c = content(cheap, expensive); var s = standing(c);
        for (int iteration = 0; iteration < 8; iteration++) {
            var ask = m.quote(c, cheap, s, Side.BUY, now); finish(m, m.begin(buyer, "cheap", "stone", ask, now));
            long returned = 0;
            for (int n = 0; n < 4; n++) { var bid = m.quote(c, expensive, s, Side.SELL, now); returned += bid.marks(); finish(m, m.begin(seller, "expensive", "stone", bid, now)); }
            assertTrue(returned < ask.marks());
        }
    }
    @Test void completedTradeCannotApplyPressureTwiceAndRefundDoesNotChangeIt() {
        var m = market(); var p = product(64, 1000, 300); var c = content(p); var s = standing(c);
        var trade = m.begin(buyer, "a", "stone", m.quote(c, p, s, Side.BUY, now), now); finish(m, trade);
        long price = m.quote(c, p, s, Side.BUY, now).marks(); m.complete(trade.id()); assertEquals(price, m.quote(c, p, s, Side.BUY, now).marks());
        var cancelled = m.begin(buyer, "a", "stone", m.quote(c, p, s, Side.BUY, now), now);
        m.transition(cancelled.id(), Phase.RESERVED, Phase.PAID); m.transition(cancelled.id(), Phase.PAID, Phase.REFUNDING); m.cancel(cancelled.id());
        assertEquals(price, m.quote(c, p, s, Side.BUY, now).marks()); assertTrue(market().pending().isEmpty());
    }
    @Test void inventoryBoundariesStayHeldAcrossRestart() {
        var m = market(); var p = product(16, 160, 50); var c = content(p); var q = m.quote(c, p, standing(c), Side.SELL, now);
        var trade = m.begin(seller, "a", "stone", q, now); m.transition(trade.id(), Phase.RESERVED, Phase.INVENTORY);
        var reloaded = market(); assertEquals(Phase.INVENTORY, reloaded.require(trade.id()).phase());
        assertThrows(IllegalStateException.class, () -> reloaded.complete(trade.id()));
        reloaded.transition(trade.id(), Phase.INVENTORY, Phase.ESCROWED); reloaded.complete(trade.id()); assertTrue(market().pending().isEmpty());
    }
    @Test void failedBeginAndCompletionNeverPublishNewState() throws Exception {
        var m = market(); var p = product(16, 160, 50); var c = content(p); var q = m.quote(c, p, standing(c), Side.BUY, now);
        Path target = folder.resolve("market.yml"); Files.createDirectory(target); Files.createFile(target.resolve("occupied"));
        assertThrows(IllegalStateException.class, () -> m.begin(buyer, "a", "stone", q, now)); assertTrue(m.pending().isEmpty());
        Files.delete(target.resolve("occupied")); Files.delete(target);
        var trade = m.begin(buyer, "a", "stone", q, now); m.transition(trade.id(), Phase.RESERVED, Phase.PAID); m.transition(trade.id(), Phase.PAID, Phase.INVENTORY);
        Files.delete(target); Files.createDirectory(target); Files.createFile(target.resolve("occupied"));
        assertThrows(IllegalStateException.class, () -> m.complete(trade.id())); assertEquals(Phase.INVENTORY, m.require(trade.id()).phase()); assertEquals(q.marks(), m.quote(c, p, standing(c), Side.BUY, now).marks());
    }
    @Test void pricingIsBoundedAndMalformedStateFailsClosed() throws Exception {
        assertEquals(.5, NpcMarket.multiplier(Integer.MIN_VALUE)); assertEquals(1.5, NpcMarket.multiplier(Integer.MAX_VALUE));
        Files.writeString(folder.resolve("market.yml"), "schema: 1\nstock:\n  vanilla_cobblestone:\n    pressure: 999999\n    revision: 1\n");
        assertThrows(IllegalStateException.class, this::market);
    }
    @Test void variableQuantitiesUseConfiguredUnitPricesAndKeepLegacyBundleQuotes() {
        var m = market(); var p = product(16, 160, 50); var c = content(p); var s = standing(c);
        assertEquals(m.quote(c, p, s, Side.BUY, 16, now), m.quote(c, p, s, Side.BUY, now));
        var one = m.quote(c, p, s, Side.BUY, 1, now);
        var stack = m.quote(c, p, s, Side.BUY, 64, now);
        assertEquals(1, one.units()); assertEquals(8, one.marks()); assertEquals(64, stack.units());
        double total = 0;
        for (int index = 0; index < 64; index++) total += 160D * .76 / 16 * multiplier(index);
        assertEquals((long) Math.ceil(total), stack.marks());
        assertEquals(now.plusSeconds(30), stack.expires());
        assertThrows(IllegalArgumentException.class, () -> m.quote(c, p, s, Side.BUY, 0, now));
        assertThrows(IllegalArgumentException.class, () -> m.quote(c, p, s, Side.BUY, 65, now));
        assertThrows(IllegalArgumentException.class, () -> m.snapshot(c, p, -1, now));
    }
    @Test void variableQuantitiesMovePressureByActualItemsRatherThanConfiguredBundles() {
        var m = market(); var p = product(64, 1000, 300); var c = content(p); var s = standing(c);
        var initial = m.snapshot(c, p, 16, now);
        var buy = m.begin(buyer, "a", "stone", m.quote(c, p, s, Side.BUY, 16, now), now);
        assertEquals(initial, m.snapshot(c, p, 16, now));
        finish(m, buy);
        assertEquals(16, m.snapshot(c, p, 16, now).pressure());
        assertEquals(1, m.snapshot(c, p, 16, now).revision());
        finish(m, m.begin(seller, "b", "stone", m.quote(c, p, s, Side.SELL, 16, now), now));
        var after = market().snapshot(c, p, 16, now);
        assertEquals(0, after.pressure()); assertEquals(initial.buyMarks(), after.buyMarks());
        assertEquals(initial.sellMarks(), after.sellMarks()); assertEquals(2, after.revision());
    }
    @Test void snapshotsAreNeutralReadOnlyAndShowUnavailableSidesWithoutOfferingATrade() {
        var m = market(); var p = product(16, 160, 50); var c = content(p);
        var f = c.factions().values().iterator().next();
        var neutral = new NpcContent.ReputationView(f, f.tiers().getFirst(), 0);
        var snapshot = m.snapshot(c, p, 16, now);
        assertEquals(m.quote(c, p, neutral, Side.BUY, now).marks(), snapshot.buyMarks());
        assertEquals(m.quote(c, p, neutral, Side.SELL, now).marks(), snapshot.sellMarks());
        assertTrue(m.quote(c, p, standing(c), Side.BUY, now).marks() < snapshot.buyMarks());
        assertEquals(0, snapshot.pressure()); assertEquals(0, snapshot.revision()); assertTrue(m.pending().isEmpty());
        assertFalse(Files.exists(folder.resolve("market.yml")));
        var buyOnly = product(1, 10, 0); var buyContent = content(buyOnly);
        assertEquals(0, m.snapshot(buyContent, buyOnly, 1, now).sellMarks());
        assertThrows(IllegalArgumentException.class, () -> m.quote(buyContent, buyOnly, standing(buyContent), Side.SELL, now));
        var sellOnly = product(1, 0, 10); var sellContent = content(sellOnly);
        assertEquals(0, m.snapshot(sellContent, sellOnly, 1, now).buyMarks());
        assertThrows(IllegalArgumentException.class, () -> m.quote(sellContent, sellOnly, standing(sellContent), Side.BUY, now));
    }
    @Test void reputationAndTradeLocksCannotBeBypassedByChangingQuantity() {
        var m = market(); var p = new NpcContent.ShopProduct("stone", Material.COBBLESTONE, null, 16, 160, 50, 100, 200, "");
        var c = content(p); var f = c.factions().values().iterator().next();
        var low = new NpcContent.ReputationView(f, f.tiers().getFirst(), 0);
        var hostile = new NpcContent.ReputationView(f, new NpcContent.TierDefinition("blocked", "Blocked", "red", -1000, 1, false), 600);
        for (Side side : Side.values()) for (int units : List.of(1, 16, 64)) {
            assertThrows(IllegalArgumentException.class, () -> m.quote(c, p, low, side, units, now));
            assertThrows(IllegalArgumentException.class, () -> m.quote(c, p, hostile, side, units, now));
            assertDoesNotThrow(() -> m.quote(c, p, standing(c), side, units, now));
        }
        assertTrue(m.snapshot(c, p, 16, now).buyMarks() > 0);
    }
    @Test void splitQuantitiesAndDifferentReputationTiersCannotProfitFromRoundTrips() {
        var m = market(); var cheap = product(64, 10000, 4000); var expensive = product(16, 100000, 90000);
        var c = content(cheap, expensive); var f = c.factions().values().iterator().next();
        var neutral = new NpcContent.ReputationView(f, f.tiers().getFirst(), 0); var honored = standing(c);
        for (int units : List.of(1, 16, 64)) for (NpcContent.ReputationView buyStanding : List.of(neutral, honored)) {
            var ask = m.quote(c, cheap, buyStanding, Side.BUY, units, now);
            finish(m, m.begin(buyer, "cheap", "stone", ask, now));
            long returned = 0;
            for (int index = 0; index < units; index++) {
                var bid = m.quote(c, expensive, honored, Side.SELL, 1, now);
                if (bid.marks() == 0) {
                    assertThrows(IllegalArgumentException.class, () -> m.begin(seller, "expensive", "stone", bid, now));
                    continue;
                }
                returned += bid.marks(); finish(m, m.begin(seller, "expensive", "stone", bid, now));
            }
            assertTrue(returned < ask.marks(), "Faction and bundle differences must not generate free Marks");
        }
    }
    @Test void antiArbitragePricingRefreshesWhenContentIsReloaded() {
        var m = market(); var expensive = product(16, 1000, 900); var c = content(expensive);
        long before = m.quote(c, expensive, standing(c), Side.SELL, now).marks();
        var reloaded = content(expensive, product(64, 100, 40));
        long after = m.quote(reloaded, expensive, standing(reloaded), Side.SELL, now).marks();
        assertTrue(after < before); assertTrue(after < m.quote(reloaded, expensive, standing(reloaded), Side.BUY, now).marks());
        assertEquals(before, m.quote(c, expensive, standing(c), Side.SELL, now).marks());
    }
    @Test void pendingAccountIsHeldEvenForAnotherCommodity() {
        var m = market(); var p = product(16, 160, 50);
        var other = new NpcContent.ShopProduct("dirt", Material.DIRT, null, 16, 160, 50, 100, 0, "");
        var c = content(p, other); var s = standing(c);
        var first = m.begin(buyer, "a", "stone", m.quote(c, p, s, Side.BUY, now), now);
        assertThrows(IllegalArgumentException.class, () -> m.begin(buyer, "a", "dirt", m.quote(c, other, s, Side.BUY, now), now));
        assertDoesNotThrow(() -> m.begin(seller, "a", "dirt", m.quote(c, other, s, Side.BUY, now), now));
        m.cancel(first.id()); assertEquals(1, market().pending().size());
    }
    @Test void boundedPressureRemainsBoundedAndPersistsWithQuantityTrades() throws Exception {
        Files.writeString(folder.resolve("market.yml"), "schema: 1\nstock:\n  vanilla_cobblestone:\n    pressure: 2048\n    revision: 3\n");
        var m = market(); var p = product(16, 160, 50); var c = content(p); var s = standing(c);
        long first = m.quote(c, p, s, Side.BUY, 64, now).marks();
        finish(m, m.begin(buyer, "a", "stone", m.quote(c, p, s, Side.BUY, 64, now), now));
        assertEquals(PRESSURE_LIMIT, m.snapshot(c, p, 64, now).pressure());
        assertEquals(first, market().quote(c, p, s, Side.BUY, 64, now).marks());
        assertEquals(4, market().snapshot(c, p, 64, now).revision());
    }
    @Test void invalidIdentifiersAndUnpriceableAmountsFailBeforeCreatingAJournal() {
        var m = market(); var p = product(16, 160, 50); var c = content(p); var s = standing(c);
        var q = m.quote(c, p, s, Side.BUY, 1, now);
        assertThrows(IllegalArgumentException.class, () -> m.begin(buyer, "../shop", "stone", q, now));
        assertThrows(IllegalArgumentException.class, () -> m.begin(buyer, "shop", "stone.invalid", q, now));
        assertThrows(IllegalArgumentException.class, () -> product(1, Long.MAX_VALUE, 0));
        assertFalse(Files.exists(folder.resolve("market.yml"))); assertTrue(m.pending().isEmpty());
    }
    @Test void exhaustedRevisionFailsBeforePaymentRatherThanStrandingANewTrade() throws Exception {
        Files.writeString(folder.resolve("market.yml"), "schema: 1\nstock:\n  vanilla_cobblestone:\n    pressure: 0\n    revision: 9223372036854775807\n");
        var m = market(); var p = product(16, 160, 50); var c = content(p);
        var q = m.quote(c, p, standing(c), Side.BUY, now);
        assertThrows(IllegalStateException.class, () -> m.begin(buyer, "a", "stone", q, now));
        assertTrue(m.pending().isEmpty());
    }
    @Test void craftingOutputsCannotProfitFromCheapInputsAndHighDemandOutputs() throws Exception {
        var cases = List.of(
            new ConversionCase(Material.BONE, 8, 32, Material.BONE_MEAL, 16, 40, 12, 3),
            new ConversionCase(Material.OAK_LOG, 16, 72, Material.OAK_PLANKS, 32, 48, 12, 4),
            new ConversionCase(Material.RAW_COPPER, 16, 80, Material.COPPER_INGOT, 8, 56, 21, 1)
        );
        for (var example : cases) {
            var input = new NpcContent.ShopProduct("input", example.input(), null, example.inputBundle(), example.inputPrice(), 0, 0, 0, "");
            var output = new NpcContent.ShopProduct("output", example.output(), null, example.outputBundle(), example.outputPrice(), example.outputSell(), 100, 0, "");
            var c = content(input, output); var s = standing(c);
            Files.writeString(folder.resolve("market.yml"), "schema: 1\nstock:\n  " + commodity(input)
                + ":\n    pressure: -2048\n    revision: 0\n  " + commodity(output) + ":\n    pressure: 2048\n    revision: 0\n");
            var rule = new NpcRecipePriceGuard.RecipeRule(example.output(), example.yield(), List.of(new NpcRecipePriceGuard.Ingredient(example.input(), 1)));
            var m = new NpcMarket(folder.resolve("market.yml").toFile(), List.of(rule));
            var ask = m.quote(c, input, s, Side.BUY, 64, now);
            finish(m, m.begin(buyer, "input", "input", ask, now));
            long returned = 0;
            for (int batch = 0; batch < example.yield(); batch++) {
                var bid = m.quote(c, output, s, Side.SELL, 64, now);
                returned += bid.marks(); finish(m, m.begin(seller, "output", "output", bid, now));
            }
            assertTrue(returned < ask.marks(), example.input() + " -> " + example.output() + " cannot mint Marks");
            assertEquals(m.quote(c, output, s, Side.SELL, now).marks(),
                new NpcMarket(folder.resolve("market.yml").toFile(), List.of(rule)).quote(c, output, s, Side.SELL, now).marks());
        }
    }
    @Test void conversionCapsRespectContentReloadAndLeaveUnrelatedBuybackPricesAlone() {
        var input = new NpcContent.ShopProduct("raw", Material.RAW_COPPER, null, 16, 80, 0, 0, 0, "");
        var output = new NpcContent.ShopProduct("ingots", Material.COPPER_INGOT, null, 8, 56, 21, 100, 0, "");
        var unrelated = product(16, 160, 50);
        var c = content(input, output, unrelated); var s = standing(c);
        var rule = new NpcRecipePriceGuard.RecipeRule(Material.COPPER_INGOT, 1, List.of(new NpcRecipePriceGuard.Ingredient(Material.RAW_COPPER, 1)));
        var m = new NpcMarket(folder.resolve("market.yml").toFile(), List.of(rule));
        assertEquals(market().quote(c, unrelated, s, Side.SELL, now), m.quote(c, unrelated, s, Side.SELL, now));
        long before = m.quote(c, output, s, Side.SELL, now).marks();
        var cheaper = new NpcContent.ShopProduct("raw", Material.RAW_COPPER, null, 16, 40, 0, 0, 0, "");
        var reloaded = content(cheaper, output, unrelated);
        assertTrue(m.quote(reloaded, output, standing(reloaded), Side.SELL, now).marks() < before);
    }
    @Test void gravelCannotBeConvertedToFlintAndFlippedAtOppositeMarketPressures() throws Exception {
        var gravel = new NpcContent.ShopProduct("gravel", Material.GRAVEL, null, 16, 40, 0, 0, 0, "");
        var flint = new NpcContent.ShopProduct("flint", Material.FLINT, null, 16, 40, 12, 100, 0, "");
        var c = content(gravel, flint); var s = standing(c);
        Files.writeString(folder.resolve("market.yml"), "schema: 1\nstock:\n  vanilla_gravel:\n    pressure: -2048\n    revision: 0\n  vanilla_flint:\n    pressure: 2048\n    revision: 0\n");
        var unguarded = market();
        assertTrue(unguarded.quote(c, flint, s, Side.SELL, now).marks()
            > unguarded.quote(c, gravel, s, Side.BUY, now).marks(), "The fixture reproduces the non-recipe flip");
        var guarded = new NpcMarket(folder.resolve("market.yml").toFile(), NpcRecipePriceGuard.environmentalConversions());
        var ask = guarded.quote(c, gravel, s, Side.BUY, now);
        finish(guarded, guarded.begin(buyer, "mason", "gravel", ask, now));
        var bid = guarded.quote(c, flint, s, Side.SELL, now);
        assertTrue(bid.marks() > 0 && bid.marks() < ask.marks(), "Flint remains sellable without minting Marks");
        finish(guarded, guarded.begin(seller, "quarry", "flint", bid, now));
        assertEquals(guarded.quote(c, flint, s, Side.SELL, now),
            new NpcMarket(folder.resolve("market.yml").toFile(), NpcRecipePriceGuard.environmentalConversions()).quote(c, flint, s, Side.SELL, now));
    }

    @Test void refillingWaterBottlesCannotTurnCheapDirtAndWheatIntoProfitableMudBrickBuybacks() throws Exception {
        var dirt = new NpcContent.ShopProduct("dirt", Material.DIRT, null, 16, 32, 0, 0, 0, "");
        var wheat = new NpcContent.ShopProduct("wheat", Material.WHEAT, null, 16, 32, 0, 0, 0, "");
        var bricks = new NpcContent.ShopProduct("bricks", Material.MUD_BRICKS, null, 16, 72, 18, 100, 0, "");
        var c = content(dirt, wheat, bricks); var s = standing(c);
        var recipes = List.of(
            new NpcRecipePriceGuard.RecipeRule(Material.PACKED_MUD, 1, List.of(
                new NpcRecipePriceGuard.Ingredient(Material.MUD, 1), new NpcRecipePriceGuard.Ingredient(Material.WHEAT, 1))),
            new NpcRecipePriceGuard.RecipeRule(Material.MUD_BRICKS, 4, List.of(new NpcRecipePriceGuard.Ingredient(Material.PACKED_MUD, 4))));
        Files.writeString(folder.resolve("market.yml"), "schema: 1\nstock:\n  vanilla_dirt:\n    pressure: -2048\n    revision: 0\n  vanilla_wheat:\n    pressure: -2048\n    revision: 0\n  vanilla_mud_bricks:\n    pressure: 2048\n    revision: 0\n");
        var unguarded = new NpcMarket(folder.resolve("market.yml").toFile(), recipes);
        assertTrue(unguarded.quote(c, bricks, s, Side.SELL, now).marks()
            > unguarded.quote(c, dirt, s, Side.BUY, now).marks() + unguarded.quote(c, wheat, s, Side.BUY, now).marks(),
            "Recipe capture alone cannot see the dirt interaction");
        var guardedRules = new ArrayList<>(NpcRecipePriceGuard.environmentalConversions()); guardedRules.addAll(recipes);
        var guarded = new NpcMarket(folder.resolve("market.yml").toFile(), guardedRules);
        var dirtAsk = guarded.quote(c, dirt, s, Side.BUY, now);
        finish(guarded, guarded.begin(buyer, "mason", "dirt", dirtAsk, now));
        var wheatAsk = guarded.quote(c, wheat, s, Side.BUY, now);
        finish(guarded, guarded.begin(buyer, "farm", "wheat", wheatAsk, now));
        var bid = guarded.quote(c, bricks, s, Side.SELL, now);
        assertTrue(bid.marks() > 0 && bid.marks() < dirtAsk.marks() + wheatAsk.marks(),
            "Mud bricks remain sellable without returning more than their purchased ingredients");
    }

    record ConversionCase(Material input, int inputBundle, long inputPrice,
                          Material output, int outputBundle, long outputPrice, long outputSell, int yield) { }
}
