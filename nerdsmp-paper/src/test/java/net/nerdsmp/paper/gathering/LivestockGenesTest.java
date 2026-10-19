package net.nerdsmp.paper.gathering;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class LivestockGenesTest {
    @Test void legacyAnimalsStayOrdinaryAndCapsHaveRealBenefits() {
        var ordinary = LivestockGenes.ordinary();
        assertEquals(1, ordinary.healthMultiplier()); assertEquals(-24000, ordinary.babyTicks());
        assertEquals(6000, ordinary.breedingCooldown());
        var prize = new LivestockGenes(10, 10, 10);
        assertEquals(1.5, prize.healthMultiplier()); assertEquals(-18000, prize.babyTicks());
        assertEquals(4800, prize.breedingCooldown());
    }
    @Test void everyInheritedTraitIsAParentValueOrOneImprovementAndNeverExceedsTen() {
        var mother = new LivestockGenes(2, 5, 10); var father = new LivestockGenes(7, 9, 10);
        for (int seed = 0; seed < 2000; seed++) {
            var child = LivestockGenes.inherit(mother, father, 24, new Random(seed));
            assertTrue(java.util.Set.of(2, 3, 7, 8).contains(child.vitality()));
            assertTrue(java.util.Set.of(5, 6, 9, 10).contains(child.growth()));
            assertEquals(10, child.fertility());
        }
    }
    @Test void qualityBonusesImproveOddsWithoutBypassingTraitCaps() {
        int normal = 0, trained = 0;
        for (int seed = 0; seed < 4000; seed++) {
            normal += LivestockGenes.inherit(LivestockGenes.ordinary(), LivestockGenes.ordinary(), 0, new Random(seed)).growth();
            trained += LivestockGenes.inherit(LivestockGenes.ordinary(), LivestockGenes.ordinary(), 24, new Random(seed)).growth();
        }
        assertTrue(trained > normal);
        assertThrows(IllegalArgumentException.class, () -> new LivestockGenes(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LivestockGenes(0, 11, 0));
    }
}
