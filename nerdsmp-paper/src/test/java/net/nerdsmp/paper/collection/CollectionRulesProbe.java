package net.nerdsmp.paper.collection;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import static org.junit.jupiter.api.Assertions.*;

/** The collection rules, checked from the calendar test (the class's helpers are package-private). */
public final class CollectionRulesProbe {
    private CollectionRulesProbe() { }

    public static void check() {
        assertEquals("ore_iron", Collections.BY_BLOCK.get(Material.DEEPSLATE_IRON_ORE).id());
        assertEquals("log_oak", Collections.BY_BLOCK.get(Material.OAK_LOG).id());
        assertEquals("kill_zombie", Collections.BY_KILL.get(EntityType.HUSK).id());
        assertEquals("fish_cod", Collections.BY_CATCH.get(Material.COD).id());
        var iron = Collections.entry("ore_iron");
        assertEquals(0, iron.tier(49));
        assertEquals(1, iron.tier(50));
        assertEquals(5, iron.tier(1_000_000));
        assertEquals(250, iron.next(1));
        assertEquals(-1, iron.next(5));
        var wheat = Collections.entry("crop_wheat");
        assertEquals(6, wheat.maxTier(), "farming collections are the crop milestones");
        assertEquals(net.nerdsmp.paper.skyblock.IslandFarmsteadTiersProbe.tiers(), Collections.tiersOf(wheat), "crop tiers match the Farmstead's");
        assertTrue(Collections.marks(iron, 2) > Collections.marks(iron, 1));
        assertEquals(Collections.ENTRIES.size(), Collections.ENTRIES.stream().map(Collections.Entry::id).distinct().count());
    }
}
