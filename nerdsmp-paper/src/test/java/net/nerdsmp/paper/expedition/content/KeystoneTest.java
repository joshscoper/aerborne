package net.nerdsmp.paper.expedition.content;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeystoneTest {
    private static final Tier MYTHIC = Tier.DEFAULTS.get("mythic");
    private static final LocalDate MONDAY = Keystone.EPOCH;

    @Test void levelsScaleOverMythicAndStayInRange() {
        assertEquals(1.0, Keystone.scale(2), 1e-9);
        assertEquals(1.08, Keystone.scale(3), 1e-9);
        assertTrue(Keystone.scale(10) > 1.8 && Keystone.scale(10) < 1.9);
        assertEquals(Keystone.MIN_LEVEL, new Keystone.Key("x", 0).level());
        assertEquals(Keystone.MAX_LEVEL, new Keystone.Key("x", 99).level());
    }

    @Test void weeksStartOnMondayAndCountFromTheEpoch() {
        assertEquals(0, Keystone.week(MONDAY));
        assertEquals(0, Keystone.week(MONDAY.plusDays(6)));
        assertEquals(1, Keystone.week(MONDAY.plusDays(7)));
        assertEquals(-1, Keystone.week(MONDAY.minusDays(1)));
    }

    @Test void affixesJoinAtFourSevenAndTen() {
        assertEquals(List.of(), Keystone.affixes(3, MONDAY));
        assertEquals(List.of(Modifier.FORTIFIED), Keystone.affixes(4, MONDAY));
        assertEquals(List.of(Modifier.TYRANNICAL), Keystone.affixes(4, MONDAY.plusWeeks(1)));
        assertEquals(2, Keystone.affixes(7, MONDAY).size());
        assertEquals(List.of(Modifier.FORTIFIED, Modifier.TYRANNICAL, Keystone.ROTATION.get(0)), Keystone.affixes(10, MONDAY));
    }

    @Test void theRotatingAffixCyclesThroughThePool() {
        Set<Modifier> seen = new HashSet<>();
        for (int week = 0; week < Keystone.ROTATION.size(); week++) seen.add(Keystone.affixes(7, MONDAY.plusWeeks(week)).get(1));
        assertEquals(Set.copyOf(Keystone.ROTATION), seen);
        assertNotEquals(Keystone.affixes(7, MONDAY).get(1), Keystone.affixes(7, MONDAY.plusWeeks(1)).get(1));
        assertTrue(Keystone.pool().containsAll(seen));
    }

    @Test void fasterClearsUpgradeFurtherAndLateOnesDeplete() {
        long timer = 1000;
        assertEquals(3, Keystone.change(600, timer));
        assertEquals(2, Keystone.change(601, timer));
        assertEquals(2, Keystone.change(800, timer));
        assertEquals(1, Keystone.change(1000, timer));
        assertEquals(-1, Keystone.change(1001, timer));
        assertEquals(new Keystone.Key("b", 5), Keystone.after(new Keystone.Key("a", 3), 2, "b"));
        assertEquals(Keystone.MIN_LEVEL, Keystone.after(new Keystone.Key("a", 2), -1, "a").level());
    }

    @Test void theKeyTierScalesMythicAndSwapsTimedForTheWeeksAffixes() {
        Tier tier = Keystone.tier(MYTHIC, 7, Keystone.affixes(7, MONDAY));
        assertEquals("keystone", tier.id());
        assertEquals("Mythic +7", tier.title());
        assertEquals(MYTHIC.health() * Keystone.scale(7), tier.health(), 1e-9);
        assertEquals(MYTHIC.damage() * Keystone.scale(7), tier.damage(), 1e-9);
        assertEquals(Keystone.KEY_LIVES, tier.lives());
        assertEquals(MYTHIC.lootRolls() + 1, tier.lootRolls());
        assertTrue(tier.marks() > MYTHIC.marks());
        assertFalse(tier.modifiers().contains("timed"));
        assertTrue(tier.modifiers().contains("fortified"));
        assertTrue(tier.modifiers().contains(Keystone.ROTATION.get(0).key()));
        assertEquals(null, tier.requires());
        assertEquals(MYTHIC.lootRolls() + 2, Keystone.tier(MYTHIC, 12, List.of()).lootRolls());
    }

    @Test void theTimerIsTheTimedPar() {
        assertEquals(Modifier.parSeconds(25), Keystone.timerSeconds(25));
        assertEquals("Fortified, Raging", Keystone.describe(List.of(Modifier.FORTIFIED, Modifier.RAGING)));
        assertEquals("none", Keystone.describe(List.of()));
    }
}
