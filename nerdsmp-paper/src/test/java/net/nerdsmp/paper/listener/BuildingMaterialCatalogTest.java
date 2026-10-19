package net.nerdsmp.paper.listener;

import org.junit.jupiter.api.Test;

import static net.nerdsmp.paper.listener.BuildingMaterialCatalog.Category.DETAIL;
import static net.nerdsmp.paper.listener.BuildingMaterialCatalog.Category.LANDMARK;
import static net.nerdsmp.paper.listener.BuildingMaterialCatalog.Category.NONE;
import static net.nerdsmp.paper.listener.BuildingMaterialCatalog.Category.STRUCTURE;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class BuildingMaterialCatalogTest {
    @Test
    void recognizesCommonConstructionMaterials() {
        assertEquals(STRUCTURE, BuildingMaterialCatalog.category("STONE_BRICKS"));
        assertEquals(STRUCTURE, BuildingMaterialCatalog.category("OAK_PLANKS"));
        assertEquals(DETAIL, BuildingMaterialCatalog.category("OAK_STAIRS"));
        assertEquals(DETAIL, BuildingMaterialCatalog.category("BLUE_STAINED_GLASS_PANE"));
        assertEquals(LANDMARK, BuildingMaterialCatalog.category("QUARTZ_PILLAR"));
        assertEquals(LANDMARK, BuildingMaterialCatalog.category("OXIDIZED_CUT_COPPER"));
    }

    @Test
    void rejectsResourcesAndTemporaryPlacementBlocks() {
        assertEquals(NONE, BuildingMaterialCatalog.category("DIAMOND_ORE"));
        assertEquals(NONE, BuildingMaterialCatalog.category("DIAMOND_BLOCK"));
        assertEquals(NONE, BuildingMaterialCatalog.category("DIRT"));
        assertEquals(NONE, BuildingMaterialCatalog.category("SCAFFOLDING"));
        assertEquals(NONE, BuildingMaterialCatalog.category("CHEST"));
    }
}
