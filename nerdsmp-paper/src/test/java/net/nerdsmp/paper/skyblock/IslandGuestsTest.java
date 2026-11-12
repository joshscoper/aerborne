package net.nerdsmp.paper.skyblock;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class IslandGuestsTest {
    static final long MINUTE = 60_000L;

    @Test void spotsGrowWithIslandLevel() {
        assertEquals(2, IslandGuests.slots(1));
        assertEquals(3, IslandGuests.slots(10));
        assertEquals(4, IslandGuests.slots(25));
        assertEquals(5, IslandGuests.slots(50));
    }

    @Test void visitorsArriveWhileAwayButNeverOverfill() {
        var empty = new IslandGuests.State(0, 0, List.of());
        long now = 1_000_000_000L;
        var first = IslandGuests.catchUp(empty, now, 1, 0, 0, 42);
        assertEquals(1, first.guests().size(), "the first visitor arrives at once");
        assertEquals(now + IslandGuests.ARRIVAL_MILLIS, first.nextAt());
        // A day away fills every spot and no more; the next comes a full wait after one opens.
        var later = IslandGuests.catchUp(first, now + 24 * 60 * MINUTE, 1, 0, 0, 42);
        assertEquals(2, later.guests().size());
        assertEquals(now + 24 * 60 * MINUTE + IslandGuests.ARRIVAL_MILLIS, later.nextAt());
        // Nothing changes before the next arrival is due.
        assertEquals(first, IslandGuests.catchUp(first, now + 5 * MINUTE, 1, 0, 0, 42));
    }

    @Test void requestsOnlyNameWhatTheIslandCanMake() {
        Set<Material> early = Set.copyOf(IslandGuests.pool(IslandGuests.Pool.MINE, 0));
        assertTrue(early.contains(Material.COBBLESTONE) && early.contains(Material.IRON_INGOT));
        assertFalse(early.contains(Material.DIAMOND) || early.contains(Material.GOLD_INGOT));
        assertTrue(IslandGuests.pool(IslandGuests.Pool.MINE, 5).contains(Material.EMERALD));
        for (long seed = 0; seed < 500; seed++) {
            var guest = IslandGuests.generate(seed, 1, 0, 0);
            assertFalse(guest.wants().isEmpty());
            for (var want : guest.wants()) {
                assertTrue(IslandGuests.PRICES.containsKey(want.material()));
                assertFalse(want.material() == Material.DIAMOND || want.material() == Material.EMERALD, "stage 0 islands have no diamonds");
                assertTrue(want.amount() >= 1);
            }
            assertTrue(guest.marks() >= 10 && guest.xp() >= 20 && guest.xp() <= 600);
        }
    }

    @Test void showcaseMilestonesRaiseThePay() {
        var plain = IslandGuests.generate(7, 20, 3, 0);
        var bonus = IslandGuests.generate(7, 20, 3, 0.2);
        assertEquals(plain.wants(), bonus.wants());
        assertTrue(bonus.marks() > plain.marks());
    }

    @Test void guestsSurviveSaving() {
        var guest = IslandGuests.generate(99, 30, 4, 0.08);
        assertEquals(guest, IslandGuests.decode(IslandGuests.encode(guest)));
    }

    @Test void newShowcaseKindsReadTheCoopsProgress() {
        var none = IslandShowcase.Context.basic(0, 1, 0);
        var done = new IslandShowcase.Context(0, 1, 0, 320, Set.of("quiet_fields"), Set.of("hollow_barrow"), 50, 0, 0, 0);
        assertFalse(IslandShowcase.GILDED_CLOCK.unlocked(none));
        assertTrue(IslandShowcase.GILDED_CLOCK.unlocked(done));
        assertFalse(IslandShowcase.CROWN_OF_DEEDS.unlocked(done));
        assertTrue(IslandShowcase.EMBER_CROWN.unlocked(done));
        assertFalse(IslandShowcase.CHARTER_MAP.unlocked(done));
        assertTrue(IslandShowcase.BARROW_SKULL.unlocked(done));
        assertTrue(IslandShowcase.GUESTBOOK.unlocked(done));
        assertFalse(IslandShowcase.WAYFARER_LANTERN.unlocked(done));
        assertEquals(7, done.collected(), "hearth, spyglass, clock, crown of the fields, skull, bell and book");
    }

    @Test void rarerVisitorsNeedBiggerIslandsAndSpecialOnesTheWeekend() {
        java.util.EnumSet<IslandGuests.Rarity> small = java.util.EnumSet.noneOf(IslandGuests.Rarity.class), big = java.util.EnumSet.noneOf(IslandGuests.Rarity.class),
            weekend = java.util.EnumSet.noneOf(IslandGuests.Rarity.class);
        for (long seed = 0; seed < 3000; seed++) {
            small.add(IslandGuests.VISITORS.get(IslandGuests.generate(seed, 1, 0, 0).who()).rarity());
            big.add(IslandGuests.VISITORS.get(IslandGuests.generate(seed, 40, 5, 0).who()).rarity());
            weekend.add(IslandGuests.VISITORS.get(IslandGuests.generate(seed, 40, 5, 0, true).who()).rarity());
        }
        assertEquals(Set.of(IslandGuests.Rarity.UNCOMMON), small);
        assertEquals(Set.of(IslandGuests.Rarity.UNCOMMON, IslandGuests.Rarity.RARE, IslandGuests.Rarity.LEGENDARY, IslandGuests.Rarity.MYTHIC), big);
        assertTrue(weekend.contains(IslandGuests.Rarity.SPECIAL));
    }

    @Test void rarerVisitorsPayMoreAndMythicOnesAlwaysBringABonus() {
        double uncommon = 0, mythic = 0; int u = 0, m = 0;
        for (long seed = 0; seed < 4000; seed++) {
            var guest = IslandGuests.generate(seed, 40, 5, 0);
            var rarity = IslandGuests.VISITORS.get(guest.who()).rarity();
            if (rarity == IslandGuests.Rarity.UNCOMMON) { uncommon += guest.marks(); u++; assertEquals("", guest.bonus()); }
            if (rarity == IslandGuests.Rarity.MYTHIC) { mythic += guest.marks(); m++; assertFalse(guest.bonus().isEmpty()); assertEquals(3, guest.wants().size()); }
        }
        assertTrue(u > 0 && m > 0);
        assertTrue(mythic / m > 2 * uncommon / u, "mythic visitors pay far better");
    }

    @Test void theSameVisitorNeverWaitsTwiceAtOnce() {
        long now = 1_000_000_000L;
        for (long base = 0; base < 200; base++) {
            var state = IslandGuests.catchUp(new IslandGuests.State(0, 0, List.of()), now + 10 * 24 * 60 * MINUTE, 50, 5, 0, base);
            assertEquals(state.guests().size(), state.guests().stream().map(IslandGuests.Guest::who).distinct().count());
        }
    }

    @Test void olderSavedVisitorsAndTheLogStillRead() {
        var old = IslandGuests.decode("abc|3|WHEAT:40,COD:5|120|60");
        assertEquals("", old.bonus());
        var bonus = new IslandGuests.Guest("x", 19, List.of(new IslandGuests.Want(Material.WHEAT, 2)), 900, 300, "booster");
        assertEquals(bonus, IslandGuests.decode(IslandGuests.encode(bonus)));
        assertEquals(java.util.Map.of(3, 4, 7, 1), IslandGuests.readLog(List.of("3:2", "7:1", "3:2", "junk")));
        assertEquals("Gloomsteel Ore", IslandGuests.bonusName("gloomsteel_ore:2"));
    }

    @Test void gatedVisitorsOnlyComeOnceTheirDeedIsDone() {
        int hale = -1;
        for (int i = 0; i < IslandGuests.VISITORS.size(); i++) if (IslandGuests.VISITORS.get(i).name().equals("Warden Hale")) hale = i;
        assertTrue(hale >= 0);
        var closed = IslandGuests.Params.basic(40, 5, 0, false);
        var open = new IslandGuests.Params(40, 5, 0, false, Set.of("chain:reach_prologue"), IslandGuests.ALL_SPECIALS);
        boolean seen = false;
        for (long seed = 0; seed < 4000; seed++) {
            assertNotEquals(hale, IslandGuests.generate(seed, closed).who());
            seen |= IslandGuests.generate(seed, open).who() == hale;
        }
        assertTrue(seen, "Hale visits once the prologue is done");
    }

    @Test void rarerVisitorsSometimesAskForSomethingSpecial() {
        var none = new IslandGuests.Params(40, 5, 0, false, Set.of(), Set.of());
        boolean special = false;
        for (long seed = 0; seed < 3000; seed++) {
            for (var want : IslandGuests.generate(seed, none).wants()) assertFalse(want.special(), "no specials when the server can make none");
            special |= IslandGuests.generate(seed, IslandGuests.Params.basic(40, 5, 0, false)).wants().stream().anyMatch(IslandGuests.Want::special);
        }
        assertTrue(special);
        var meal = new IslandGuests.Guest("m", 3, List.of(new IslandGuests.Want(Material.BREAD, 3, "meal:orchard_loaf")), 100, 40, "");
        assertEquals(meal, IslandGuests.decode(IslandGuests.encode(meal)));
        assertEquals("Orchard Loaf", IslandGuests.wantName(meal.wants().getFirst()));
    }

    @Test void benchesAndSwiftnessHaveCaps() {
        assertEquals(2, IslandGuests.slots(1, 0, 0));
        assertEquals(4, IslandGuests.slots(1, 1, 10));
        assertEquals(IslandGuests.MAX_SLOTS, IslandGuests.slots(99, 5, 99));
        assertEquals(IslandGuests.ARRIVAL_MILLIS, IslandGuests.interval(0, 0));
        assertEquals(Math.round(IslandGuests.ARRIVAL_MILLIS * 0.55), IslandGuests.interval(9, 25));
    }

    @Test void milestonesAndFriendshipPayOnce() {
        assertEquals(List.of("met:5", "served:25"), IslandGuests.due(5, 30, Set.of()).stream().map(IslandGuests.Milestone::key).toList());
        assertTrue(IslandGuests.due(5, 30, Set.of("met:5", "served:25")).isEmpty());
        assertEquals(0, IslandGuests.friendship(4));
        assertEquals(1, IslandGuests.friendship(5));
        assertEquals(3, IslandGuests.friendship(99));
        assertTrue(IslandGuests.favour(IslandGuests.Rarity.MYTHIC, false) > IslandGuests.favour(IslandGuests.Rarity.UNCOMMON, true));
    }

    @Test void everyGatedVisitorHasAHint() {
        for (var visitor : IslandGuests.VISITORS) if (!visitor.gate().isEmpty()) assertFalse(visitor.hint().isBlank(), visitor.name());
        assertEquals(IslandGuests.VISITORS.size(), IslandGuests.VISITORS.stream().map(IslandGuests.Visitor::name).distinct().count());
    }
}
