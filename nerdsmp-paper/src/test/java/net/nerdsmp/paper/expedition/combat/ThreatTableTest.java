package net.nerdsmp.paper.expedition.combat;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ThreatTableTest {
    private final UUID ann = UUID.randomUUID(), bo = UUID.randomUUID(), cy = UUID.randomUUID();
    private final ThreatTable table = new ThreatTable();

    private UUID choose(long now, UUID... melee) {
        Set<UUID> reach = Set.of(melee);
        return table.choose(who -> true, reach::contains, now);
    }

    @Test void theFirstAttackerHoldsTheMob() {
        table.add(ann, 5);
        assertEquals(ann, choose(0));
    }

    @Test void aMeleeAttackerTakesOverAbove110Percent() {
        table.add(ann, 100);
        assertEquals(ann, choose(0));
        table.add(bo, 105);
        assertEquals(ann, choose(0), "Not enough to pull it yet");
        assertEquals(ann, choose(0, bo));
        table.add(bo, 3);
        assertEquals(ann, choose(0, bo), "108 is under 110");
        table.add(bo, 3);
        assertEquals(bo, choose(0, bo), "111 passes 110");
    }

    @Test void aRangedAttackerNeeds130Percent() {
        table.add(ann, 100);
        assertEquals(ann, choose(0));
        table.add(bo, 125);
        assertEquals(ann, choose(0, ann));
        table.add(bo, 6);
        assertEquals(bo, choose(0, ann));
    }

    @Test void aTauntLeadsAndForcesTheMobUntilItEnds() {
        table.add(ann, 100);
        table.add(bo, 500);
        assertEquals(bo, choose(0));
        table.taunt(ann, 3000);
        assertEquals(ann, choose(1000));
        assertTrue(table.threat(ann) > table.threat(bo));
        table.add(bo, 1000);
        assertEquals(ann, choose(2999), "Nothing beats a taunt while it lasts");
        assertEquals(bo, choose(3000), "Then threat decides again");
    }

    @Test void aTauntOnAFreshMobStillGivesThreat() {
        table.taunt(ann, 1000);
        assertEquals(ann, choose(0));
        assertFalse(table.empty());
    }

    @Test void whoeverCanNoLongerBeTargetedDropsOff() {
        table.add(ann, 100);
        table.add(bo, 50);
        assertEquals(ann, choose(0));
        assertEquals(bo, table.choose(bo::equals, who -> false, 0), "Ann died");
        assertEquals(0, table.threat(ann));
        assertNull(table.choose(who -> false, who -> false, 0), "Nobody left");
        assertTrue(table.empty());
    }

    @Test void aForcedTargetThatDiesReleasesTheMob() {
        table.add(bo, 10);
        table.taunt(ann, 5000);
        assertEquals(ann, choose(0));
        assertEquals(bo, table.choose(bo::equals, who -> false, 1));
    }

    @Test void removingAndClearingForget() {
        table.add(ann, 10);
        table.add(bo, 5);
        choose(0);
        table.remove(ann);
        assertEquals(bo, choose(0));
        table.clear();
        assertTrue(table.empty());
        assertNull(table.current());
        table.add(cy, 0);
        assertTrue(table.empty(), "Zero threat isn't an entry");
    }
}
