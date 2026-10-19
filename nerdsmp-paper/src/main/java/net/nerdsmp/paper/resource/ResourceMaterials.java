package net.nerdsmp.paper.resource;

import java.util.Set;

/** Shared material classifications for skill XP, digging rewards and daily contracts. */
public final class ResourceMaterials {
    private static final Set<String> SOIL = Set.of("DIRT", "COARSE_DIRT", "ROOTED_DIRT", "GRASS_BLOCK",
        "SAND", "RED_SAND", "GRAVEL", "CLAY", "MUD", "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL", "SOUL_SAND");
    private ResourceMaterials() { }
    public static boolean ore(String name) { return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS"); }
    public static boolean log(String name) {
        return name.endsWith("_LOG") || name.endsWith("_HYPHAE") || name.equals("CRIMSON_STEM")
            || name.equals("STRIPPED_CRIMSON_STEM") || name.equals("WARPED_STEM") || name.equals("STRIPPED_WARPED_STEM");
    }
    public static boolean excavation(String name) { return SOIL.contains(name); }
}
