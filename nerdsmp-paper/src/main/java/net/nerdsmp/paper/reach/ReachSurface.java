package net.nerdsmp.paper.reach;

import net.nerdsmp.paper.reach.ReachTerrain.Column;
import net.nerdsmp.paper.reach.ReachTerrain.Zone;
import org.bukkit.Material;

/**
 * Chooses the block at each height of a column: zone ground, value-ramped cliffs, river and lake beds, roads, and
 * the banded rock of the underside. Picks come from smooth patch noise so texture changes in clusters, never as
 * speckle (the Conquest and ArdaCraft gradient rule; see the vault's Build Style Guide).
 */
final class ReachSurface {
    private static final Material[] BADLANDS = {
        Material.ORANGE_TERRACOTTA, Material.TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.ORANGE_TERRACOTTA,
        Material.WHITE_TERRACOTTA, Material.RED_TERRACOTTA, Material.TERRACOTTA, Material.BROWN_TERRACOTTA,
        Material.LIGHT_GRAY_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.RED_TERRACOTTA
    };
    private final ReachTerrain terrain;

    ReachSurface(ReachTerrain terrain) { this.terrain = terrain; }

    /** The top block. {@code slope} is the steepest drop to a neighbour, in blocks. */
    Material top(int x, int z, Column c, int slope) {
        double p = terrain.patch(x, z, 0.045), g = terrain.grain(x, z, 0.11);
        if (c.wet()) return bed(c, p, g);
        // The road keeps its surface right down to the water, where the bridge takes it on.
        if (c.carriageway()) return road(c, x, z, p, g);
        if (c.river() >= 0 && c.river() < 2.5) return bank(c.zone(), p, g);
        if (c.lake() >= 1.0 && c.lake() < 1.04) return p > 0.2 ? Material.GRAVEL : Material.SAND;
        if (slope >= 3) return cliff(c.zone(), x, c.top(), z, p);
        return switch (c.zone()) {
            case HIGHCROWN -> p > 0.78 ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
            case GOLDENREACH -> {
                // Terrace risers are dry-stone walls; plots are tilled, fallow plots are bare earth.
                if (c.terrace() > 0.3 && slope >= 2) yield g > 0.2 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE;
                int field = terrain.field(x, z);
                // A plot resting this year is pasture: grass, with hay stacked on it. A grass headland runs along the road.
                if (field == 6 || c.across() < 7) yield Material.GRASS_BLOCK;
                if (field >= 1) yield Material.FARMLAND;
                yield p > 0.72 && g > 0 ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
            }
            case BARROWDOWNS -> p > 0.45 ? Material.MOSS_BLOCK : p < -0.55 ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
            case THORNWOOD -> p > 0.5 ? Material.PODZOL : p < -0.4 ? Material.MOSS_BLOCK : g > 0.7 ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
            case MISTMERE -> {
                if (c.lake() < 1.3 && p > 0.35) yield Material.MUD;
                yield p < -0.6 ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
            }
            case IRONSPINE -> {
                if (c.top() > 186 + 4 * p) yield Material.SNOW_BLOCK;
                if (c.top() > 166 + 6 * p) yield g > 0.3 ? Material.GRAVEL : p > 0 ? Material.ANDESITE : Material.STONE;
                yield p > 0.5 ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
            }
            case SUNSCAR -> slope >= 2 ? BADLANDS[Math.floorMod(c.top() + (int) (3 * p), BADLANDS.length)]
                : p > 0.45 ? Material.COARSE_DIRT : Material.RED_SAND;
            case SCAR -> ash(x, z, p, g);
            case ARCHIVE -> p > 0.45 ? Material.CALCITE : Material.GRASS_BLOCK;
            case SKY -> Material.STONE;
        };
    }

    /** The few blocks under the top. */
    Material sub(Column c, int depth, int slope) {
        if (c.wet() || (c.river() >= 0 && c.river() < 2.5)) return depth < 2 ? Material.DIRT : Material.STONE;
        if (slope >= 3) return Material.STONE;
        // A two-block step shows its face: earth just under the turf, then rock, as a real bank does.
        if (slope == 2 && c.zone() != Zone.SUNSCAR && c.zone() != Zone.SCAR)
            return depth == 1 ? Material.COARSE_DIRT : (c.top() + depth) % 3 == 0 ? Material.ANDESITE : Material.STONE;
        return switch (c.zone()) {
            case SUNSCAR -> depth < 2 ? Material.RED_SANDSTONE : BADLANDS[Math.floorMod(c.top() - depth, BADLANDS.length)];
            case SCAR -> depth < 2 ? Material.TUFF : Material.BASALT;
            case IRONSPINE -> c.top() > 166 ? Material.STONE : Material.DIRT;
            case ARCHIVE -> depth < 2 ? Material.DIRT : Material.CALCITE;
            default -> Material.DIRT;
        };
    }

    /** Deep rock: bands of stone darkening toward the foot, as the reference cliffs do. */
    Material rock(int x, int y, int z, Zone zone) {
        double wobble = 5 * terrain.patch(x, z, 0.02);
        int band = Math.floorDiv(y + (int) wobble, 6);
        if (zone == Zone.SUNSCAR && y > 70) return BADLANDS[Math.floorMod(band, BADLANDS.length)];
        if (zone == Zone.SCAR && y > 40) return Math.floorMod(band, 3) == 0 ? Material.BLACKSTONE : Material.BASALT;
        // A value ramp: pale stone near the top, through tuff, to deepslate at the foot of the underside.
        if (y < 0) return Math.floorMod(band, 4) == 0 ? Material.TUFF : Material.DEEPSLATE;
        if (y < 25) return switch (Math.floorMod(band, 4)) { case 0, 2 -> Material.DEEPSLATE; case 1 -> Material.TUFF; default -> Material.COBBLED_DEEPSLATE; };
        if (y < 55) return switch (Math.floorMod(band, 5)) { case 0 -> Material.DEEPSLATE; case 1, 3 -> Material.TUFF; default -> Material.ANDESITE; };
        return switch (Math.floorMod(band, 7)) {
            case 1, 4 -> Material.ANDESITE;
            case 3 -> Material.TUFF;
            case 6 -> Material.CALCITE;
            default -> Material.STONE;
        };
    }

    /** The lowest block of the underside. Roots near the lip, dripstone deeper in. */
    Material underside(Column c, double p) {
        if (c.edge() < 35) return p > 0 ? Material.ROOTED_DIRT : Material.DIRT;
        return p > 0.3 ? Material.DRIPSTONE_BLOCK : Material.TUFF;
    }

    private Material bed(Column c, double p, double g) {
        if (c.lake() < 1.0) return c.water() - c.top() > 6 ? (p > 0 ? Material.CLAY : Material.MUD) : p > 0.25 ? Material.GRAVEL : Material.SAND;
        return p > 0.3 ? Material.GRAVEL : g > 0.4 ? Material.CLAY : Material.SAND;
    }

    private Material bank(Zone zone, double p, double g) {
        if (zone == Zone.MISTMERE && p > 0.2) return Material.MUD;
        return p > 0.35 ? Material.GRAVEL : g > 0.2 ? Material.ROOTED_DIRT : Material.COARSE_DIRT;
    }

    /**
     * A road's surface across its width. In the country: a grassy crown down the middle, two wheel ruts worn into
     * path, packed earth either side and a gravel edge. Near towns: setts and cobbles with a kerb of stone bricks.
     */
    private Material road(Column c, int x, int z, double p, double g) {
        double d = c.across();
        if (c.zone() == Zone.SCAR) return d > 2.6 ? Material.POLISHED_BASALT : g > 0 ? Material.GRAVEL : Material.TUFF;
        if (c.zone() == Zone.SUNSCAR) return d > 2.6 ? Material.SMOOTH_RED_SANDSTONE : g > 0.3 ? Material.COARSE_DIRT : Material.RED_SAND;
        if (terrain.townRoad(x, z)) {
            if (d > 2.7) return Material.STONE_BRICKS;
            return g > 0.45 ? Material.GRAVEL : p > 0.35 ? Material.ANDESITE : p < -0.4 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE;
        }
        // One worn way: a path the full width of the carriageway, mended here and there with gravel in broad patches,
        // packed earth along its sides, and only at the very edge a soft dither so a diagonal road doesn't alias.
        double broad = terrain.patch(x * 0.5 + 311, z * 0.5 - 173, 0.05);
        double edge = d + 0.45 * terrain.grain(x + 311, z - 173, 0.3);
        if (edge > 2.75) return broad > 0.35 ? Material.GRAVEL : Material.COARSE_DIRT;
        if (broad > 0.62) return Material.GRAVEL;
        if (broad < -0.62 && edge < 1.5) return Material.PACKED_MUD;
        return Material.DIRT_PATH;
    }

    /** The face of a cutting or an embankment: dry-stone, darker and mossier toward the foot. */
    Material retaining(int x, int y, int z, int fromFoot) {
        double c = terrain.patch(x + y * 0.7, z - y * 0.4, 0.25);
        if (fromFoot == 0 && c > -0.2) return Material.MOSSY_COBBLESTONE;
        if (c > 0.45) return Material.MOSSY_STONE_BRICKS;
        if (c < -0.45) return Material.ANDESITE;
        return fromFoot % 4 == 3 ? Material.STONE_BRICKS : Material.COBBLESTONE;
    }

    /**
     * How strongly the Nether's corruption has spilled out of the Rift at a point, from about one at the lip to
     * nothing forty blocks out: strongest along streaks that run outward from the edge, so it reaches out in
     * tongues across the ash rather than in a ring.
     */
    double corruption(int x, int z, double rift) {
        if (rift > 60) return 0;
        double angle = Math.atan2(z - ReachTerrain.RIFT_Z, x - ReachTerrain.RIFT_X);
        double streak = terrain.patch(angle * 38, rift * 0.035, 1);
        return (1 - rift / 46) + 0.55 * streak + 0.12 * terrain.grain(x, z, 0.2);
    }

    /** The Rift's walls: netherrack and blackstone shot through with magma and crying obsidian, as if the rock bled. */
    Material riftRock(int x, int y, int z) {
        int h = Math.floorMod(x * 73856093 ^ y * 19349663 ^ z * 83492791, 97);
        if (h < 4) return Material.MAGMA_BLOCK;
        if (h < 6) return Material.CRYING_OBSIDIAN;
        if (h < 8) return Material.NETHER_GOLD_ORE;
        double band = terrain.patch(x * 0.3, y * 0.4 + z * 0.3, 0.3);
        return band > 0.3 ? Material.BLACKSTONE : band < -0.4 ? Material.NETHER_WART_BLOCK : Material.NETHERRACK;
    }

    private Material cliff(Zone zone, int x, int y, int z, double p) {
        return switch (zone) {
            case SUNSCAR -> BADLANDS[Math.floorMod(Math.floorDiv(y, 2), BADLANDS.length)];
            case SCAR -> p > 0.2 ? Material.BLACKSTONE : Material.BASALT;
            case THORNWOOD, BARROWDOWNS -> p > 0.3 ? Material.MOSSY_COBBLESTONE : p < -0.3 ? Material.ANDESITE : Material.STONE;
            case IRONSPINE -> y > 190 ? Material.SNOW_BLOCK : p > 0.25 ? Material.ANDESITE : p < -0.35 ? Material.TUFF : Material.STONE;
            case ARCHIVE -> p > 0 ? Material.CALCITE : Material.DIORITE;
            default -> p > 0.35 ? Material.ANDESITE : Material.STONE;
        };
    }

    private Material ash(int x, int z, double p, double g) {
        double rift = terrain.riftEdge(x + 0.5, z + 0.5);
        double spill = corruption(x, z, rift);
        if (spill > 0.95) return g > 0.3 ? Material.MAGMA_BLOCK : p > 0.2 ? Material.NETHER_WART_BLOCK : Material.NETHERRACK;
        if (spill > 0.7) return p > 0.1 ? Material.CRIMSON_NYLIUM : g > 0.45 ? Material.MAGMA_BLOCK : Material.NETHERRACK;
        if (spill > 0.5) return g > 0.2 ? Material.NETHERRACK : p < -0.2 ? Material.SOUL_SOIL : Material.BLACKSTONE;
        if (spill > 0.38) return g > 0.4 ? Material.NETHERRACK : Material.BLACKSTONE;
        if (rift < 22 && g > 0.5) return Material.MAGMA_BLOCK;
        if (p > 0.45) return Material.LIGHT_GRAY_CONCRETE_POWDER;
        if (p > 0.15) return Material.GRAY_CONCRETE_POWDER;
        if (p < -0.5) return Material.BASALT;
        return g > 0.25 ? Material.TUFF : Material.GRAVEL;
    }
}
