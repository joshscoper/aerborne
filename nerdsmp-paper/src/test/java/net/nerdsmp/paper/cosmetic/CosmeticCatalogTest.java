package net.nerdsmp.paper.cosmetic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The wardrobe: ten store companions, eleven trails, three ranks, three bundles, and titles, auras and finishers sold or earned. */
class CosmeticCatalogTest {
    private final CosmeticCatalog catalog = CosmeticCatalog.defaults();

    @Test void matchesTheWebsiteCatalog() {
        assertEquals(List.of("pufflet", "kitekit", "mossbun", "cindlet", "moonmoth", "starwhisk", "lilypip", "glimmerbee", "bramblehog", "driftfin"),
            catalog.companions().stream().filter(companion -> companion.source() == null).map(CosmeticCatalog.Companion::slug).toList());
        assertEquals(List.of("rattlebones", "weepling", "hollow-hound", "silkling", "thistlewing", "sproutling", "windpip", "pipling", "siltpup", "tidelet", "drakeling", "ashling", "cogling", "embermutt", "lampwick", "smeltling", "kilnling", "rimepup", "pebblewarden", "inkling"),
            catalog.companions().stream().filter(companion -> companion.source() != null).map(CosmeticCatalog.Companion::slug).toList());
        assertEquals(List.of("cloudstep", "petal-skip", "firefly-drift", "rune-ripple", "bubblehop", "comet-tail",
            "dandelion-drift", "stardust-waltz", "snowdrift", "ember-wake", "glowspore"), catalog.trailSlugs());
        assertEquals(List.of("skyfarer", "cloudkeeper", "starweaver"), catalog.tiers().stream().map(CosmeticCatalog.Tier::slug).toList());
        assertEquals(3, catalog.bundles().size());
    }

    @Test void titlesAurasAndFinishersAreProductsLikeTheRest() {
        assertTrue(catalog.known("cosmetic.title.skyborne"));
        assertTrue(catalog.known("cosmetic.aura.glimmer-halo"));
        assertTrue(catalog.known("cosmetic.finisher.starburst"));
        assertFalse(catalog.known("cosmetic.title.emperor"), "an unknown title is refused");
        var owned = catalog.ownership(List.of("cosmetic.title.skyborne", "cosmetic.aura.snow-globe", "cosmetic.finisher.starburst"));
        assertEquals(Set.of("skyborne"), owned.titles());
        assertEquals(Set.of("snow-globe"), owned.auras());
        assertEquals(Set.of("starburst"), owned.finishers());
        assertEquals("Skyborne title", catalog.describe("cosmetic.title.skyborne"));
        for (var title : catalog.titles()) assertNotNull(net.kyori.adventure.text.format.NamedTextColor.NAMES.value(title.color()), title.slug() + " has a real colour");
    }

    @Test void aServerWithItsOwnCosmeticSettingsStillHasEveryKind() {
        var settings = new org.bukkit.configuration.file.YamlConfiguration();
        settings.set("companions.pufflet.scale", 1.5);
        var loaded = CosmeticService.load(settings, null);
        assertEquals(catalog.titles().size(), loaded.titles().size());
        assertEquals(catalog.auras().size(), loaded.auras().size());
        assertEquals(catalog.finishers().size(), loaded.finishers().size());
        assertTrue(loaded.known("cosmetic.title.skyborne"), "the store can grant a title on a configured server");
    }

    @Test void clearsEarnTheRightThingsAndNothingSold() {
        assertEquals(List.of("cosmetic.title.sovereigns-bane"), catalog.earnedBy(CosmeticCatalog.clearKeys("cinder_keep", "normal")));
        assertEquals(List.of("cosmetic.title.sovereigns-bane", "cosmetic.aura.sovereigns-embers"),
            catalog.earnedBy(CosmeticCatalog.clearKeys("cinder_keep", "heroic")));
        assertEquals(List.of("cosmetic.title.sovereigns-bane", "cosmetic.title.keystone-master", "cosmetic.aura.sovereigns-embers", "cosmetic.finisher.sovereigns-wrath"),
            catalog.earnedBy(CosmeticCatalog.clearKeys("cinder_keep", "mythic")));
        for (var title : catalog.titles()) if (title.earned() == null) assertTrue(catalog.earnedBy(List.of("expedition:" + title.slug())).isEmpty());
        for (String expedition : List.of("first_flight", "hollow_barrow", "thornwood_canopy", "sunken_aqueduct", "gloomforge_mines", "hall_of_wardens", "cinder_keep"))
            assertFalse(catalog.earnedBy(CosmeticCatalog.clearKeys(expedition, "normal")).isEmpty(), expedition + " earns a title");
    }

    @Test void aurasAndFinishersStaySmall() {
        for (var aura : catalog.auras()) {
            for (long tick = 0; tick < 40; tick++) {
                var puffs = AuraPatterns.aura(aura.pattern(), tick);
                assertTrue(puffs.stream().mapToInt(TrailPatterns.Puff::count).sum() <= 6, aura.slug() + " frame size");
            }
            assertTrue(java.util.stream.LongStream.range(0, 8).anyMatch(tick -> !AuraPatterns.aura(aura.pattern(), tick).isEmpty()), aura.slug() + " draws");
            assertEquals(aura.particle(), CosmeticService.particle(aura.particle(), null) == null ? null : aura.particle(), aura.slug() + " particle needs no data");
        }
        for (var finisher : catalog.finishers()) {
            assertFalse(AuraPatterns.finisher(finisher.pattern()).isEmpty(), finisher.slug() + " draws");
            assertNotNull(CosmeticService.particle(finisher.particle(), null), finisher.slug() + " particle needs no data");
        }
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

    @Test void everyTebexBundleIncludesSkyfarerWithoutDowngradingOtherRanks() {
        for (var bundle : catalog.bundles()) {
            var owned = catalog.ownership(List.of(bundle.product()));
            assertEquals("skyfarer", owned.tier().orElseThrow().slug());
            assertEquals(1, owned.companions().size());
            assertEquals(1, owned.trails().size());
            var upgraded = catalog.ownership(List.of(bundle.product(), "cosmetic.rank.starweaver"));
            assertEquals("starweaver", upgraded.tier().orElseThrow().slug());
            var refunded = catalog.ownership(List.of("cosmetic.rank.cloudkeeper"));
            assertEquals("cloudkeeper", refunded.tier().orElseThrow().slug());
            assertTrue(refunded.companions().isEmpty());
        }
    }

    @Test void formerBundleConfigDefaultsUpgradeWhileCustomContentsRemain() {
        for (var bundle : catalog.bundles()) {
            var former = bundle.contents().stream().filter(id -> !id.equals("cosmetic.rank.skyfarer")).toList();
            assertEquals(bundle.contents(), CosmeticCatalog.configuredBundleContents(bundle, former));
            assertEquals(bundle.contents(), CosmeticCatalog.configuredBundleContents(bundle, bundle.contents()));
            var custom = List.of("cosmetic.companion.moonmoth", "cosmetic.rank.cloudkeeper");
            assertEquals(custom, CosmeticCatalog.configuredBundleContents(bundle, custom));
        }
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
        var free = new CosmeticCatalog(List.of(new CosmeticCatalog.Companion("pufflet", "Pufflet", "Cloud puff", "pufflet", 1.0D, true, "", "white", true)),
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
