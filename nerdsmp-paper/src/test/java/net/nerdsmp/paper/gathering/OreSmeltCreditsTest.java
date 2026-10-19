package net.nerdsmp.paper.gathering;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class OreSmeltCreditsTest {
    @Test void existingItemsAreNeverRetroactivelyCredited() {
        OreSmeltCredits old = OreSmeltCredits.fresh("IRON_INGOT", 32);
        assertEquals(0, old.extract("IRON_INGOT", 32, 0).creditedItems());
        assertEquals(1, old.smelt("IRON_INGOT", 32, 33).extract("IRON_INGOT", 33, 0).creditedItems());
    }
    @Test void provenSmeltsAccumulateWithinOneNativeStack() {
        OreSmeltCredits ledger = OreSmeltCredits.fresh("IRON_INGOT", 0);
        for (int i = 0; i < 64; i++) ledger = ledger.smelt("IRON_INGOT", i, i + 1);
        assertEquals(64, ledger.credits());
        OreSmeltCredits full = ledger;
        assertThrows(IllegalArgumentException.class, () -> full.smelt("IRON_INGOT", 64, 65));
    }
    @Test void partialExtractionConsumesExactlyRemovedProvenItems() {
        var first = credits(8, 8).extract("IRON_INGOT", 3, 5);
        assertEquals(3, first.creditedItems());
        assertEquals(5, first.next().credits());
        var second = first.next().extract("IRON_INGOT", 5, 0);
        assertEquals(5, second.creditedItems());
        assertEquals(0, second.next().credits());
        assertTrue(second.operationSequence() > first.operationSequence());
    }
    @Test void replayedOrFabricatedExtractionCannotSpendAgain() {
        var first = credits(8, 8).extract("IRON_INGOT", 3, 5);
        assertEquals(0, first.next().extract("IRON_INGOT", 3, 5).creditedItems());
        assertEquals(0, credits(8, 8).extract("IRON_INGOT", 9, 0).creditedItems());
        assertEquals(0, credits(8, 8).extract("GOLD_INGOT", 8, 0).creditedItems());
    }
    @Test void hopperRemovalRetiresWorkAndVanillaXpBankCannotRestoreIt() {
        OreSmeltCredits afterHopper = credits(8, 8).reconcile("IRON_INGOT", 0);
        assertEquals(0, afterHopper.credits());
        assertEquals(0, afterHopper.reconcile("IRON_INGOT", 8).extract("IRON_INGOT", 8, 0).creditedItems());
        assertEquals(1, afterHopper.smelt("IRON_INGOT", 0, 1).extract("IRON_INGOT", 1, 0).creditedItems());
    }
    @Test void partialHopperTransferRetiresCorrespondingCredits() {
        var remaining = credits(8, 10).reconcile("IRON_INGOT", 7);
        assertEquals(5, remaining.credits());
        assertEquals(5, remaining.extract("IRON_INGOT", 7, 0).creditedItems());
    }
    @Test void canceledOrUnsuccessfulHopperTransferLeavesWorkIntact() {
        assertEquals(credits(8, 8), credits(8, 8).reconcile("IRON_INGOT", 8));
    }
    @Test void UnexpectedOutputAdditionOrReplacementClearsUnprovenBudget() {
        assertEquals(0, credits(8, 8).reconcile("IRON_INGOT", 9).credits());
        assertEquals(0, credits(8, 8).reconcile("GOLD_INGOT", 8).credits());
        assertEquals(0, credits(8, 8).reconcile("COOKED_BEEF", 8).credits());
    }
    @Test void unverifiedWorkNeverGrantsExperience() {
        assertEquals(0, credits(8, 8).unverified("IRON_INGOT", 8).extract("IRON_INGOT", 8, 0).creditedItems());
        assertThrows(IllegalArgumentException.class, () -> credits(8, 8).smelt("IRON_INGOT", 8, 10));
    }
    @Test void persistenceRoundTripKeepsFurnaceIdentityAndSpentSequence() {
        OreSmeltCredits saved = credits(8, 8).extract("IRON_INGOT", 3, 5).next();
        assertEquals(saved, OreSmeltCredits.decode(saved.encode()));
        assertEquals(0, OreSmeltCredits.decode(saved.encode()).extract("IRON_INGOT", 3, 5).creditedItems());
    }
    @Test void malformedReceiptsFailClosed() {
        for (String bad : new String[] {"", "2|abc", "1|abc|0|IRON_INGOT|1|1",
            "1|00000000-0000-0000-0000-000000000000|0|IRON_INGOT|65|65",
            "1|00000000-0000-0000-0000-000000000000|0|IRON_INGOT|2|1",
            "1|00000000-0000-0000-0000-000000000000|-1|IRON_INGOT|1|1",
            "1|00000000-0000-0000-0000-000000000000|0|COOKED_BEEF|1|1"})
            assertThrows(IllegalArgumentException.class, () -> OreSmeltCredits.decode(bad));
    }
    @Test void onlyEligibleOreInputsProduceMetalReceipts() {
        assertEquals(Material.IRON_INGOT, OreSmeltingService.outputFor(Material.RAW_IRON));
        assertEquals(Material.IRON_INGOT, OreSmeltingService.outputFor(Material.DEEPSLATE_IRON_ORE));
        assertEquals(Material.COPPER_INGOT, OreSmeltingService.outputFor(Material.COPPER_ORE));
        assertEquals(Material.GOLD_INGOT, OreSmeltingService.outputFor(Material.NETHER_GOLD_ORE));
        assertEquals(Material.NETHERITE_SCRAP, OreSmeltingService.outputFor(Material.ANCIENT_DEBRIS));
        for (Material ineligible : new Material[] {Material.IRON_SWORD, Material.IRON_NUGGET,
            Material.GOLDEN_BOOTS, Material.DIAMOND_ORE, Material.BEEF, Material.SAND, Material.CACTUS})
            assertNull(OreSmeltingService.outputFor(ineligible));
    }
    private static OreSmeltCredits credits(int credits, int observed) {
        return new OreSmeltCredits(new UUID(0, 1), 0, "IRON_INGOT", credits, observed);
    }
}
