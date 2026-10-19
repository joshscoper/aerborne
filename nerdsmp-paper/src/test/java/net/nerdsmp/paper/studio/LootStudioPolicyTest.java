package net.nerdsmp.paper.studio;

import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.paper.loot.profile.LootProfileDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static net.nerdsmp.paper.loot.profile.LootProfileDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class LootStudioPolicyTest {
    private final Entry sword = new Entry("blade", Kind.ITEM, "trial_blade", .25, 1, 2, false, 0);
    private final Entry coins = new Entry("coins", Kind.MARKS, "marks", .5, 2, 6, false, 0);
    private final LootProfileDefinition profile = new LootProfileDefinition("trial_rewards", Mode.ADDITIVE, List.of(sword, coins));

    @Test void newProfilesDefaultToNonDestructiveAdditiveMode() {
        var fresh = LootStudioPolicy.create("forest_rewards");
        assertEquals("forest_rewards", fresh.id());
        assertEquals(Mode.ADDITIVE, fresh.mode());
        assertTrue(fresh.entries().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.create("../escape"));
    }

    @Test void copiesPreserveDefinitionsButNeverShareMutableEntryCollections() {
        var copied = LootStudioPolicy.copy("other_rewards", profile);
        assertEquals("other_rewards", copied.id());
        assertEquals(profile.entries(), copied.entries());
        assertThrows(UnsupportedOperationException.class, () -> copied.entries().clear());
        assertEquals(2, profile.entries().size());
    }

    @Test void modeChangesRetainEveryRewardWithoutChangingPublishedValue() {
        var changed = LootStudioPolicy.mode(profile, Mode.REPLACE);
        assertEquals(Mode.REPLACE, changed.mode());
        assertEquals(profile.entries(), changed.entries());
        assertEquals(Mode.ADDITIVE, profile.mode());
    }

    @Test void replacementsKeepRewardPositionAndRejectMissingOrDuplicateIds() {
        var changedSword = LootStudioPolicy.chance(sword, "60");
        var changed = LootStudioPolicy.entry(profile, changedSword, true);
        assertEquals(List.of(changedSword, coins), changed.entries());
        assertEquals(.25, profile.entries().getFirst().chance());
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.entry(profile, changedSword, false));
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.entry(LootStudioPolicy.create("empty"), changedSword, true));
    }

    @Test void additionsAndRemovalsAreImmutableAndValidated() {
        var added = LootStudioPolicy.entry(LootStudioPolicy.create("empty"), sword, false);
        assertEquals(List.of(sword), added.entries());
        assertTrue(LootStudioPolicy.remove(added, "blade").entries().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.remove(added, "not_here"));
        assertEquals(1, added.entries().size());
    }

    @Test void independentPercentagesAreExactIncludingFractionalPercentages() {
        assertEquals(.125, LootStudioPolicy.chance(sword, "12.5").chance());
        assertEquals(.125, LootStudioPolicy.chance(sword, " 12.5 % ").chance());
        assertEquals(0, LootStudioPolicy.chance(sword, "0").chance());
        assertEquals(1, LootStudioPolicy.chance(sword, "100%").chance());
        assertEquals(sword.minimum(), LootStudioPolicy.chance(sword, "10").minimum());
        assertEquals(sword.source(), LootStudioPolicy.chance(sword, "10").source());
    }

    @Test void invalidPercentagesNeverEnterDrafts() {
        for (String value : List.of("NaN", "Infinity", "-1", "100.01", "hello", "1%2", "5%%", ""))
            assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.chance(sword, value), value);
    }

    @Test void quantityControlsAcceptSinglesAndInclusiveRanges() {
        var fixed = LootStudioPolicy.quantity(sword, "4");
        assertEquals(4, fixed.minimum()); assertEquals(4, fixed.maximum());
        var range = LootStudioPolicy.quantity(sword, " 1   64 ");
        assertEquals(1, range.minimum()); assertEquals(64, range.maximum());
        assertEquals(sword.chance(), range.chance());
        assertEquals(10_000, LootStudioPolicy.quantity(coins, "1 10000").maximum());
    }

    @Test void quantityControlsRejectOverflowReversedRangesAndExtraArguments() {
        for (String value : List.of("0", "2 1", "1 65", "1 2 3", "1.5", "2147483648", "-1", ""))
            assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.quantity(sword, value), value);
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.quantity(coins, "10001"));
    }

    @Test void sourceControlsNormalizeIdsAndDoNotAllowPurseSpoofing() {
        assertEquals("new_blade", LootStudioPolicy.source(sword, " NEW_BLADE ").source());
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.source(sword, "coin_purse"));
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.source(coins, "diamonds"));
    }

    @Test void gearControlsCycleAffixesAndToggleIdentificationWithoutDroppingFields() {
        var unknown = LootStudioPolicy.unidentified(sword);
        assertTrue(unknown.unidentified()); assertFalse(LootStudioPolicy.unidentified(unknown).unidentified());
        var affixed = sword;
        for (int i = 1; i <= 4; i++) {
            affixed = LootStudioPolicy.affixes(affixed);
            assertEquals(i % 4, affixed.affixes());
            assertEquals(sword.source(), affixed.source()); assertEquals(sword.chance(), affixed.chance());
        }
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.unidentified(coins));
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.affixes(coins));
    }

    @Test void catalogValidationRequiresPublishedItemsAndActualGearForExtraProperties() {
        var item = new ItemId("trial_blade");
        assertDoesNotThrow(() -> LootStudioPolicy.validateCatalog(profile, Set.of(item), Set.of(item)));
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.validateCatalog(profile, Set.of(), Set.of()));
        var unknown = LootStudioPolicy.entry(profile, LootStudioPolicy.unidentified(sword), true);
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.validateCatalog(unknown, Set.of(item), Set.of()));
        var affixed = LootStudioPolicy.entry(profile, LootStudioPolicy.affixes(sword), true);
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.validateCatalog(affixed, Set.of(item), Set.of()));
        assertDoesNotThrow(() -> LootStudioPolicy.validateCatalog(LootStudioPolicy.create("empty"), Set.of(), Set.of()));
    }

    @Test void profilePublicationRejectsStaleAndCreatedByAnotherEditorContent() {
        assertDoesNotThrow(() -> LootStudioPolicy.expected(profile, profile));
        assertDoesNotThrow(() -> LootStudioPolicy.expected(null, null));
        assertThrows(IllegalStateException.class, () -> LootStudioPolicy.expected(null, profile));
        assertThrows(IllegalStateException.class, () -> LootStudioPolicy.expected(profile, null));
        assertThrows(IllegalStateException.class, () -> LootStudioPolicy.expected(profile, LootStudioPolicy.mode(profile, Mode.REPLACE)));
    }

    @Test void bindingConfirmationRejectsConcurrentReassignmentOrRemoval() {
        assertDoesNotThrow(() -> LootStudioPolicy.expectedBinding(null, null));
        assertDoesNotThrow(() -> LootStudioPolicy.expectedBinding("trial_rewards", "trial_rewards"));
        assertThrows(IllegalStateException.class, () -> LootStudioPolicy.expectedBinding(null, "another"));
        assertThrows(IllegalStateException.class, () -> LootStudioPolicy.expectedBinding("trial_rewards", null));
        assertThrows(IllegalStateException.class, () -> LootStudioPolicy.expectedBinding("trial_rewards", "another"));
    }

    @Test void balancePreviewIsAnalyticNotRandomOrAwarding() {
        var summary = LootStudioPolicy.summary(profile);
        assertEquals(.375, summary.expectedItemQuantity());
        assertEquals(2D, summary.expectedMarks());
        assertEquals(2, summary.maximumItems());
        assertEquals(6, summary.maximumMarks());
        assertEquals(2, summary.rewards());
        assertEquals(profile.entries(), List.of(sword, coins));
        var empty = LootStudioPolicy.summary(LootStudioPolicy.create("empty"));
        assertEquals(0, empty.expectedItemQuantity()); assertEquals(0, empty.maximumMarks());
    }

    @Test void paginationIsBoundedAndDetachedIncludingEmptyLibraries() {
        var all = new ArrayList<>(IntStream.range(0, 32).boxed().toList());
        assertEquals(0, LootStudioPolicy.page(-50, all.size()));
        assertEquals(1, LootStudioPolicy.page(Integer.MAX_VALUE, all.size()));
        assertEquals(28, LootStudioPolicy.entries(all, 0).size());
        assertEquals(List.of(28, 29, 30, 31), LootStudioPolicy.entries(all, 1));
        var visible = LootStudioPolicy.entries(all, 1); all.clear();
        assertEquals(4, visible.size());
        assertThrows(UnsupportedOperationException.class, () -> visible.clear());
        assertEquals(0, LootStudioPolicy.page(Integer.MAX_VALUE, 0));
        assertEquals(List.of(), LootStudioPolicy.entries(List.of(), Integer.MAX_VALUE));
    }

    @Test void draftOperationsCannotBypassAggregatePerKillLimits() {
        var large = new Entry("coins", Kind.MARKS, "marks", 1, 1, 10_000, false, 0);
        var full = LootStudioPolicy.entry(LootStudioPolicy.create("full"), large, false);
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.entry(full, new Entry("extra", Kind.MARKS, "marks", 1, 1, 1, false, 0), false));
        var entries = IntStream.range(0, 32).mapToObj(i -> new Entry("entry_" + i, Kind.ITEM, "trial_blade", .1, 1, 1, false, 0)).toList();
        var filled = new LootProfileDefinition("filled", Mode.ADDITIVE, entries);
        assertThrows(IllegalArgumentException.class, () -> LootStudioPolicy.entry(filled, sword, false));
    }
}
