package net.nerdsmp.paper.skyblock;

import net.nerdsmp.paper.naming.Nicknames;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

final class IslandFarmsteadTest {
    @Test void composterTurnsMatterIntoCompostOverTime() {
        long t = 1_000_000L;
        var state = IslandComposter.add(new IslandComposter.State(0, 0, t), 100);
        var later = IslandComposter.work(state, t + 3 * IslandComposter.MILLIS_PER_COMPOST);
        assertEquals(2, later.compost(), "100 organic makes two, though time allowed three");
        assertEquals(20, later.organic(), 0.001);
        // Time with nothing to compost isn't banked for later.
        var refilled = IslandComposter.add(later, 400);
        assertEquals(2, IslandComposter.work(refilled, later.at()).compost());
        assertEquals(IslandComposter.MAX_ORGANIC, IslandComposter.add(state, 1e9).organic(), 0.001);
        var full = IslandComposter.work(new IslandComposter.State(4000, IslandComposter.MAX_COMPOST - 1, t), t + 100 * IslandComposter.MILLIS_PER_COMPOST);
        assertEquals(IslandComposter.MAX_COMPOST, full.compost());
    }

    @Test void cropMilestonesClimb() {
        assertEquals(0, IslandFarmstead.tier(99));
        assertEquals(1, IslandFarmstead.tier(100));
        assertEquals(6, IslandFarmstead.tier(1_000_000));
        assertEquals(IslandFarmstead.Crop.CARROT, IslandFarmstead.Crop.of(org.bukkit.Material.CARROTS));
        assertNull(IslandFarmstead.Crop.of(org.bukkit.Material.STONE));
    }

    @Test void contestMedalsScaleByCropAndLiftTheTopFarmer() {
        var wheat = IslandFarmstead.Crop.WHEAT;
        assertNull(FarmingContests.medal(wheat, 149, true));
        assertEquals(FarmingContests.Medal.BRONZE, FarmingContests.medal(wheat, 150, false));
        assertEquals(FarmingContests.Medal.SILVER, FarmingContests.medal(wheat, 150, true));
        assertEquals(FarmingContests.Medal.GOLD, FarmingContests.medal(wheat, 900, true));
        assertEquals(FarmingContests.Medal.BRONZE, FarmingContests.medal(IslandFarmstead.Crop.PUMPKIN, 75, false));
        assertEquals(3, FarmingContests.crops(123_456_789L).stream().distinct().count());
        assertEquals(FarmingContests.crops(123_456_789L), FarmingContests.crops(123_456_789L));
    }

    @Test void contestsOpenOnTheEvenHoursForTwentyMinutes() {
        ZoneId utc = ZoneId.of("UTC");
        assertEquals(Instant.parse("2026-11-14T10:00:00Z"), FarmingContests.slot(Instant.parse("2026-11-14T10:05:00Z"), utc, 2, 20));
        assertNull(FarmingContests.slot(Instant.parse("2026-11-14T10:25:00Z"), utc, 2, 20));
        assertNull(FarmingContests.slot(Instant.parse("2026-11-14T11:05:00Z"), utc, 2, 20));
    }

    @Test void nicknamesAreCleanedOrRefused() {
        assertEquals("Old Faithful", Nicknames.clean("  Old   <red>Faithful</red> "));
        assertEquals("", Nicknames.clean("   "));
        assertNull(Nicknames.clean("x".repeat(25)));
        assertNull(Nicknames.clean("bad{name}"));
        assertEquals("Ember", Nicknames.clean("&cEmber"));
        assertTrue(Nicknames.Style.EMBER.paint("Hi", null).startsWith("<gradient:"));
        assertEquals(Nicknames.Style.RARITY, Nicknames.Style.of("nonsense"));
    }
}
