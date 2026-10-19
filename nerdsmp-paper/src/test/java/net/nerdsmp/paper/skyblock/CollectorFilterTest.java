package net.nerdsmp.paper.skyblock;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CollectorFilterTest {
    @Test void emptyFilterAcceptsMaterialsButNotAir() {
        assertTrue(CollectorFilter.accepts(null, Material.WHEAT));
        assertTrue(CollectorFilter.accepts(null, Material.BONE));
        assertFalse(CollectorFilter.accepts(null, Material.AIR));
    }
    @Test void filterMatchesOnlyExactMaterialAndBadDataFailsClosed() {
        assertTrue(CollectorFilter.accepts("WHEAT", Material.WHEAT));
        assertFalse(CollectorFilter.accepts("WHEAT", Material.WHEAT_SEEDS));
        assertFalse(CollectorFilter.accepts("not_a_material", Material.WHEAT));
        assertFalse(CollectorFilter.accepts("", Material.WHEAT));
    }
}
