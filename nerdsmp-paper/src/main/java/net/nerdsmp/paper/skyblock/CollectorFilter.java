package net.nerdsmp.paper.skyblock;

import org.bukkit.Material;

/** A material-only drop filter. An invalid saved filter fails closed rather than collecting everything. */
final class CollectorFilter {
    private CollectorFilter() { }
    static boolean accepts(String filter, Material material) {
        return material != Material.AIR && material != Material.CAVE_AIR && material != Material.VOID_AIR
            && (filter == null || material.name().equals(filter));
    }
}
