package net.nerdsmp.paper.skyblock;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class IslandSpawnerCatalogTest {
    @Test void legacyRecipesAndStageAccessRemainUnchanged() {
        var original = Map.of(EntityType.ZOMBIE, Material.ROTTEN_FLESH, EntityType.SKELETON, Material.BONE,
            EntityType.SPIDER, Material.STRING, EntityType.BLAZE, Material.BLAZE_ROD);
        original.forEach((type, material) -> {
            var recipe = IslandSpawnerCatalog.find(type).orElseThrow();
            assertEquals(material, recipe.ingredient());
            assertTrue(recipe.legacy());
            assertEquals(0, recipe.minimumResourceStage());
            assertEquals("island_spawner_" + type.name().toLowerCase(java.util.Locale.ROOT), recipe.recipeKey());
            assertTrue(IslandSpawnerCatalog.unlocked(type, 0));
            assertTrue(IslandSpawnerCatalog.authentic(type, type, null, null));
        });
        assertEquals(java.util.List.of("IFI", "FDF", "IFI"), IslandSpawnerCatalog.RECIPE_SHAPE);
    }
    @Test void catalogRecipesAreUniqueSafeAndBounded() {
        assertEquals(13, IslandSpawnerCatalog.all().size());
        assertEquals(13, IslandSpawnerCatalog.all().stream().map(IslandSpawnerCatalog.Definition::type).distinct().count());
        assertEquals(13, IslandSpawnerCatalog.all().stream().map(IslandSpawnerCatalog.Definition::ingredient).distinct().count());
        for (var definition : IslandSpawnerCatalog.all()) {
            assertTrue(IslandMobStackPolicy.supported(definition.type().name()));
            assertTrue(IslandSpawnerCatalog.unlocked(definition.type(), definition.minimumResourceStage()));
            if (!definition.legacy()) {
                assertFalse(IslandSpawnerCatalog.unlocked(definition.type(), definition.minimumResourceStage() - 1));
                assertFalse(IslandSpawnerCatalog.authentic(definition.type(), definition.type(), null, null));
            }
        }
        for (var type : new EntityType[]{EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.WARDEN,
            EntityType.VILLAGER, EntityType.IRON_GOLEM, EntityType.SLIME, EntityType.MAGMA_CUBE}) {
            assertFalse(IslandSpawnerCatalog.supports(type));
            assertFalse(IslandSpawnerCatalog.unlocked(type, 5));
        }
        assertThrows(UnsupportedOperationException.class, () -> IslandSpawnerCatalog.all().clear());
    }
    @Test void forgedUnknownPartialAndMismatchedItemTagsFailClosed() {
        for (var definition : IslandSpawnerCatalog.all()) {
            var type = definition.type();
            assertTrue(IslandSpawnerCatalog.authentic(type, type, type.name(), 1));
            assertFalse(IslandSpawnerCatalog.authentic(type, type, type.name(), 2));
            assertFalse(IslandSpawnerCatalog.authentic(type, type, type.name(), null));
            assertFalse(IslandSpawnerCatalog.authentic(type, type, null, 1));
            assertFalse(IslandSpawnerCatalog.authentic(type, type, "PIG", 1));
            assertFalse(IslandSpawnerCatalog.authentic(type, EntityType.WITHER, type.name(), 1));
            assertFalse(IslandSpawnerCatalog.unlocked(type, -1));
            assertFalse(IslandSpawnerCatalog.unlocked(type, Integer.MAX_VALUE));
        }
        assertFalse(IslandSpawnerCatalog.authentic(EntityType.WITHER, EntityType.WITHER, "WITHER", 1));
    }
}
