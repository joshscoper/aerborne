package net.nerdsmp.paper.cosmetic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The wardrobe matches the website: six companions, eight trails, three supporter ranks and three bundles. */
class CosmeticCatalogTest {
    private final CosmeticCatalog catalog = CosmeticCatalog.defaults();

    @Test void matchesTheWebsiteCatalog() {
        assertEquals(List.of("pufflet", "kitekit", "mossbun", "cindlet", "moonmoth", "starwhisk"), catalog.companionSlugs());
        assertEquals(List.of("cloudstep", "petal-skip", "firefly-drift", "rune-ripple", "bubblehop", "comet-tail",
            "dandelion-drift", "stardust-waltz"), catalog.trailSlugs());
        assertEquals(List.of("skyfarer", "cloudkeeper", "starweaver"), catalog.tiers().stream().map(CosmeticCatalog.Tier::slug).toList());
        assertEquals(3, catalog.bundles().size());
    }

    @Test void onlyKnownStoreProductsCanBeGranted() {
        assertTrue(catalog.known("cosmetic.companion.pufflet"));
        assertTrue(catalog.known("COSMETIC.TRAIL.COMET-TAIL"));
        assertTrue(catalog.known("cosmetic.rank.starweaver"));
        assertTrue(catalog.known("cosmetic.bundle.cloudberry"));
        assertTrue(catalog.known("supporter_monthly"));
        assertFalse(catalog.known("cosmetic.companion.dragon"), "A typo in the store is refused, not stored");
        assertFalse(catalog.known("marks.1000"), "Gameplay items can never be sold");
        assertFalse(catalog.known(null));
    }

    @Test void ownershipExpandsBundlesAndPicksTheHighestTier() {
        var owned = catalog.ownership(List.of("cosmetic.bundle.celestial-dreams", "cosmetic.rank.skyfarer", "cosmetic.rank.starweaver",
            "cosmetic.trail.bubblehop"));
        assertEquals(Set.of("starwhisk"), owned.companions());
        assertEquals(Set.of("stardust-waltz", "bubblehop"), owned.trails());
        assertEquals("starweaver", owned.tier().orElseThrow().slug());
        assertTrue(owned.emotes() && owned.presets() && owned.keepsake());
        assertEquals(7, owned.palette().size());
    }

    @Test void tiersUnlockTheirPerksInOrder() {
        var skyfarer = catalog.ownership(List.of("cosmetic.rank.skyfarer"));
        assertTrue(skyfarer.supporter());
        assertFalse(skyfarer.emotes(), "Emotes start at Cloudkeeper");
        var cloudkeeper = catalog.ownership(List.of("cosmetic.rank.cloudkeeper"));
        assertTrue(cloudkeeper.emotes());
        assertFalse(cloudkeeper.presets(), "Presets and the keepsake are Starweaver perks");
        assertFalse(cloudkeeper.keepsake());
    }

    @Test void olderSubscriptionsKeepEveryPerkWithTheGoldStar() {
        var legacy = catalog.ownership(List.of("supporter_annual"));
        assertEquals("supporter", legacy.tier().orElseThrow().slug());
        assertEquals("<gold>✦</gold>", legacy.tier().get().badge());
        assertTrue(legacy.emotes() && legacy.presets() && legacy.keepsake());
    }

    @Test void nothingOwnedWithoutPurchasesUnlessMadeFree() {
        assertEquals(CosmeticCatalog.Ownership.NONE.companions(), catalog.ownership(List.of()).companions());
        assertFalse(catalog.ownership(List.of()).supporter());
        var free = new CosmeticCatalog(List.of(new CosmeticCatalog.Companion("pufflet", "Pufflet", "Cloud puff", "HAPPY_GHAST", "", true, true, "", "white", true)),
            List.of(), List.of(), List.of(), List.of());
        assertEquals(Set.of("pufflet"), free.ownership(List.of()).companions());
    }

    @Test void bundlesMustOnlyContainRealCosmetics() {
        assertThrows(IllegalArgumentException.class, () -> new CosmeticCatalog(List.of(), List.of(), List.of(),
            List.of(new CosmeticCatalog.Bundle("odd", "Odd", List.of("cosmetic.companion.dragon"))), List.of()));
    }

    @Test void receiptsUseReadableNames() {
        assertEquals("Pufflet companion", catalog.describe("cosmetic.companion.pufflet"));
        assertEquals("Comet Tail trail", catalog.describe("cosmetic.trail.comet-tail"));
        assertEquals("Starweaver rank", catalog.describe("cosmetic.rank.starweaver"));
        assertEquals("Cloudberry bundle", catalog.describe("cosmetic.bundle.cloudberry"));
        assertTrue(catalog.companion("Star Whisk").isEmpty());
        assertTrue(catalog.trail("Comet_Tail").isPresent(), "Names are forgiving about spaces and underscores");
    }
}
