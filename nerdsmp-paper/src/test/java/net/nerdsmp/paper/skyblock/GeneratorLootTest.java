package net.nerdsmp.paper.skyblock;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class GeneratorLootTest {
    @Test void everyTierHasAnExplicitHundredTicketTable() {
        for (int tier = 0; tier <= 3; tier++) {
            Map<Material, Integer> counts = new EnumMap<>(Material.class);
            for (int n = 0; n < 100; n++) counts.merge(GeneratorLoot.roll(tier, n), 1, Integer::sum);
            assertEquals(68, counts.get(Material.COBBLESTONE)); assertEquals(10, counts.get(Material.COAL_ORE));
            assertEquals(7, counts.get(Material.COPPER_ORE));
            assertEquals(tier == 0 ? 15 : 5, counts.get(Material.IRON_ORE));
            assertEquals(tier >= 3, counts.containsKey(Material.DIAMOND_ORE));
        }
    }
    @Test void thresholdsUnlockInOrder() {
        assertEquals(Material.IRON_ORE, GeneratorLoot.roll(0, 99)); assertEquals(Material.LAPIS_ORE, GeneratorLoot.roll(1, 99));
        assertEquals(Material.REDSTONE_ORE, GeneratorLoot.roll(2, 99)); assertEquals(Material.DIAMOND_ORE, GeneratorLoot.roll(3, 99));
    }
    @Test void invalidInputsNeverSilentlyBecomeValuableLoot() {
        assertThrows(IllegalArgumentException.class, () -> GeneratorLoot.roll(-1, 99));
        assertThrows(IllegalArgumentException.class, () -> GeneratorLoot.roll(4, 99));
        assertThrows(IllegalArgumentException.class, () -> GeneratorLoot.roll(0, 100));
        assertThrows(IllegalArgumentException.class, () -> GeneratorLoot.roll(0, -1));
    }
    @Test void resourceMilestonesPreserveAllLegacyTickets() {
        for (int tier = 0; tier <= 3; tier++) for (int ticket = 0; ticket < 100; ticket++)
            assertEquals(GeneratorLoot.roll(tier, ticket), GeneratorLoot.rollResources(tier, ticket));
    }
    @Test void advancedResourcesHaveExplicitProbabilitiesAndKeepEveryLegacyOre() {
        var before = GeneratorLoot.resourceTable(3);
        for (int stage = 4; stage <= 5; stage++) {
            var table = GeneratorLoot.resourceTable(stage);
            assertEquals(100, table.values().stream().mapToInt(Integer::intValue).sum());
            assertTrue(table.keySet().containsAll(before.keySet()));
            assertTrue(table.get(Material.DIAMOND_ORE) >= before.get(Material.DIAMOND_ORE));
            assertTrue(table.containsKey(Material.NETHER_QUARTZ_ORE));
            assertThrows(UnsupportedOperationException.class, () -> table.clear());
            before = table;
        }
        assertEquals(2, GeneratorLoot.resourceTable(5).get(Material.EMERALD_ORE));
        assertEquals(1, GeneratorLoot.resourceTable(5).get(Material.OBSIDIAN));
        assertFalse(GeneratorLoot.resourceTable(4).containsKey(Material.EMERALD_ORE));
        assertThrows(IllegalArgumentException.class, () -> GeneratorLoot.resourceTable(6));
        assertThrows(IllegalArgumentException.class, () -> GeneratorLoot.rollResources(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> GeneratorLoot.rollResources(5, 100));
    }
    @Test void rerollsCompareNewResourcesWithoutDroppingThemAsUnknown() {
        assertEquals(Material.EMERALD_ORE, GeneratorLoot.rarer(Material.COBBLESTONE, Material.EMERALD_ORE));
        assertEquals(Material.NETHER_QUARTZ_ORE, GeneratorLoot.rarer(Material.NETHER_QUARTZ_ORE, Material.COPPER_ORE));
        assertEquals(Material.DIAMOND_ORE, GeneratorLoot.rarer(Material.OBSIDIAN, Material.DIAMOND_ORE));
    }
}
