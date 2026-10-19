package net.nerdsmp.paper.alchemy;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReagentSavingsTest {
    @Test void savesOneActualReagentAtTheTalentChance() {
        var ingredients=List.of(Material.GLASS_BOTTLE,Material.WATER_BUCKET,Material.NETHER_WART,Material.SUGAR);
        assertEquals(Material.NETHER_WART,ReagentSavings.choose(ingredients,15,0.149).orElseThrow());
        assertTrue(ReagentSavings.choose(ingredients,15,0.15).isEmpty());
        assertTrue(ReagentSavings.choose(ingredients,0,0).isEmpty());
    }
    @Test void carrierContainersArrowsAndMalformedRollsAreNeverFreeOutputs() {
        var carriers=List.of(Material.HONEY_BOTTLE,Material.DRAGON_BREATH,Material.MILK_BUCKET,Material.POTION,Material.ARROW);
        assertTrue(ReagentSavings.choose(carriers,85,0).isEmpty());
        assertTrue(ReagentSavings.choose(List.of(Material.SUGAR),85,Double.NaN).isEmpty());
        assertTrue(ReagentSavings.choose(List.of(Material.SUGAR),1000,0.86).isEmpty());
    }
}
