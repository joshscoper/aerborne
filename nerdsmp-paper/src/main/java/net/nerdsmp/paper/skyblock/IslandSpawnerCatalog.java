package net.nerdsmp.paper.skyblock;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Stable island recipes. Existing spawners remain usable without new progression receipts. */
public final class IslandSpawnerCatalog {
    public static final int ITEM_SCHEMA = 1;
    public static final List<String> RECIPE_SHAPE = List.of("IFI", "FDF", "IFI");
    private static final List<Definition> DEFINITIONS = List.of(
        new Definition(EntityType.ZOMBIE, "Zombie Spawner", Material.ROTTEN_FLESH, 0, true),
        new Definition(EntityType.SKELETON, "Skeleton Spawner", Material.BONE, 0, true),
        new Definition(EntityType.SPIDER, "Spider Spawner", Material.STRING, 0, true),
        new Definition(EntityType.BLAZE, "Blaze Spawner", Material.BLAZE_ROD, 0, true),
        new Definition(EntityType.CREEPER, "Creeper Spawner", Material.GUNPOWDER, 1, false),
        new Definition(EntityType.DROWNED, "Drowned Spawner", Material.COPPER_INGOT, 1, false),
        new Definition(EntityType.HUSK, "Husk Spawner", Material.SAND, 1, false),
        new Definition(EntityType.STRAY, "Stray Spawner", Material.ICE, 2, false),
        new Definition(EntityType.CAVE_SPIDER, "Cave Spider Spawner", Material.FERMENTED_SPIDER_EYE, 2, false),
        new Definition(EntityType.ENDERMAN, "Enderman Spawner", Material.ENDER_PEARL, 3, false),
        new Definition(EntityType.ZOMBIFIED_PIGLIN, "Zombified Piglin Spawner", Material.GOLD_NUGGET, 3, false),
        new Definition(EntityType.WITCH, "Witch Spawner", Material.GLOWSTONE_DUST, 3, false),
        new Definition(EntityType.BREEZE, "Breeze Spawner", Material.BREEZE_ROD, 4, false)
    );

    private IslandSpawnerCatalog() { }
    public static List<Definition> all() { return DEFINITIONS; }
    public static Optional<Definition> find(EntityType type) {
        return DEFINITIONS.stream().filter(definition -> definition.type() == type).findFirst();
    }
    public static boolean supports(EntityType type) { return find(type).isPresent(); }
    public static boolean unlocked(EntityType type, int resourceStage) {
        return resourceStage >= 0 && resourceStage < IslandResourceStage.values().length
            && find(type).filter(definition -> definition.minimumResourceStage() <= resourceStage).isPresent();
    }

    /** Untagged historic items are valid only for the four original species. Partial tags fail closed. */
    static boolean authentic(EntityType expected, EntityType embedded, String markedType, Integer schema) {
        var definition = find(expected);
        if (definition.isEmpty() || expected != embedded) return false;
        if (markedType == null && schema == null) return definition.get().legacy();
        return expected.name().equals(markedType) && Integer.valueOf(ITEM_SCHEMA).equals(schema);
    }

    public record Definition(EntityType type, String title, Material ingredient, int minimumResourceStage, boolean legacy) {
        public Definition {
            java.util.Objects.requireNonNull(type);
            java.util.Objects.requireNonNull(title);
            java.util.Objects.requireNonNull(ingredient);
            if (minimumResourceStage < 0 || minimumResourceStage >= IslandResourceStage.values().length
                || legacy && minimumResourceStage != 0) throw new IllegalArgumentException("Invalid spawner resource stage");
        }
        public String recipeKey() { return "island_spawner_" + type.name().toLowerCase(Locale.ROOT); }
        public IslandResourceStage unlockStage() { return IslandResourceStage.at(minimumResourceStage); }
    }
}
