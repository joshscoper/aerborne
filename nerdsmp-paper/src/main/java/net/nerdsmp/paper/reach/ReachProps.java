package net.nerdsmp.paper.reach;

import org.bukkit.Material;

/**
 * Street life: the small pieces a street is lived in with, set in groups by the towns. Each is built in a brush's
 * frame facing local -z (the street), standing on the brush's floor, and never wider than three blocks so it fits
 * against any wall. Shovel's library of parts is the model (see the vault's Shovel Study).
 */
final class ReachProps {
    private ReachProps() { }

    private static final Material[] GOODS = {Material.HAY_BLOCK, Material.PUMPKIN, Material.MELON, Material.DRIED_KELP_BLOCK,
        Material.BARREL, Material.HONEYCOMB_BLOCK, Material.BROWN_MUSHROOM_BLOCK};

    /** A hand cart: a raised plank bed on two wheels, shafts to the front, loaded with something. */
    static void cart(ReachBrush b, long seed) {
        for (int z = 0; z <= 1; z++) b.set(0, 0, z, Material.SPRUCE_SLAB, "type=top");
        // Wheels: open trapdoors lie on the side opposite their facing, so these sit flat against the bed.
        b.set(-1, 0, 1, Material.DARK_OAK_TRAPDOOR, "facing=west,half=bottom,open=true");
        b.set(1, 0, 1, Material.DARK_OAK_TRAPDOOR, "facing=east,half=bottom,open=true");
        b.set(0, 0, -1, Material.SPRUCE_TRAPDOOR, "facing=south,half=bottom,open=false");
        Material load = GOODS[(int) Math.floorMod(seed, (long) GOODS.length)];
        b.set(0, 1, 1, load, load == Material.BARREL ? "facing=up" : load == Material.HAY_BLOCK ? "axis=z" : "");
        if ((seed & 8) == 0) b.set(0, 1, 0, Material.COMPOSTER, "level=6");
    }

    /** A pile of barrels and crates with a sack or two, the clutter of a working doorway. */
    static void crates(ReachBrush b, long seed) {
        b.set(0, 0, 0, Material.BARREL, "facing=up");
        b.set(1, 0, 0, Material.SPRUCE_PLANKS);
        b.set(1, 1, 0, Material.SPRUCE_TRAPDOOR, "facing=north,half=bottom,open=false");
        b.set(0, 0, 1, Material.BARREL, "facing=north");
        if ((seed & 1) == 0) b.set(0, 1, 0, Material.BARREL, "facing=up");
        if ((seed & 2) == 0) b.set(1, 0, 1, Material.BROWN_WOOL);
        if ((seed & 4) == 0) b.set(-1, 0, 0, Material.WHITE_CARPET);
        if ((seed & 16) == 0) b.set(1, 1, 1, Material.BROWN_CARPET);
    }

    /** A drinking trough for horses and dogs: two cauldrons of water in a plank frame. */
    static void trough(ReachBrush b) {
        b.set(0, 0, 0, Material.WATER_CAULDRON, "level=3");
        b.set(1, 0, 0, Material.WATER_CAULDRON, "level=3");
        b.set(-1, 0, 0, Material.SPRUCE_TRAPDOOR, "facing=west,half=bottom,open=true");
        b.set(2, 0, 0, Material.SPRUCE_TRAPDOOR, "facing=east,half=bottom,open=true");
    }

    /** A raised stone planter of flowers or a little tree, {@code w} by 2. */
    static void planter(ReachBrush b, int w, long seed) {
        Material[] flowers = {Material.ROSE_BUSH, Material.PEONY, Material.LILAC, Material.AZALEA, Material.FLOWERING_AZALEA};
        for (int x = 0; x < w; x++) for (int z = 0; z < 2; z++) {
            b.set(x, 0, z, Material.STONE_BRICKS);
            b.set(x, 1, z, Material.STONE_BRICK_SLAB, "type=bottom");
        }
        for (int x = 0; x < w; x++) {
            b.set(x, 1, 0, Material.ROOTED_DIRT);
            Material f = flowers[(int) Math.floorMod(seed + x * 7L, (long) flowers.length)];
            if (f == Material.ROSE_BUSH || f == Material.PEONY || f == Material.LILAC) {
                b.set(x, 2, 0, f, "half=lower");
                b.set(x, 3, 0, f, "half=upper");
            } else b.set(x, 2, 0, f);
        }
    }

    /** A bench facing the street, with trapdoor arms. */
    static void bench(ReachBrush b, Material stair, Material trapdoor) {
        b.set(0, 0, 0, stair, "facing=south,half=bottom");
        b.set(1, 0, 0, stair, "facing=south,half=bottom");
        b.set(-1, 0, 0, trapdoor, "facing=west,half=bottom,open=true");
        b.set(2, 0, 0, trapdoor, "facing=east,half=bottom,open=true");
    }

    /** Split logs stacked against a wall under a little lean-to. */
    static void woodpile(ReachBrush b) {
        for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) b.set(x, y, 0, x == 1 && y == 1 ? Material.SPRUCE_LOG : Material.STRIPPED_SPRUCE_LOG, "axis=z");
        for (int x = -1; x <= 3; x++) b.set(x, 2, 0, Material.SPRUCE_SLAB, "type=bottom");
    }

    /** A notice board on two posts with a little roof, where the Watch pins its notices. */
    static void noticeBoard(ReachBrush b) {
        for (int y = 0; y < 3; y++) { b.set(0, y, 0, Material.DARK_OAK_FENCE); b.set(2, y, 0, Material.DARK_OAK_FENCE); }
        b.set(1, 1, 0, Material.DARK_OAK_PLANKS);
        b.set(1, 2, 0, Material.DARK_OAK_PLANKS);
        b.set(1, 1, -1, Material.DARK_OAK_WALL_SIGN, "facing=north");
        b.set(1, 2, -1, Material.DARK_OAK_WALL_SIGN, "facing=north");
        for (int x = -1; x <= 3; x++) b.set(x, 3, 0, Material.SPRUCE_SLAB, "type=bottom");
    }

    /** A smith's yard: anvil, grindstone, a quench cauldron and a rack of tools. */
    static void smithYard(ReachBrush b) {
        b.set(0, 0, 0, Material.ANVIL, "facing=east");
        b.set(2, 0, 0, Material.GRINDSTONE, "face=floor,facing=north");
        b.set(0, 0, 2, Material.WATER_CAULDRON, "level=2");
        b.set(2, 0, 2, Material.SMITHING_TABLE);
        b.set(1, 0, 2, Material.BARREL, "facing=up");
    }

    /** Market crates of goods by a stall or a shop door. */
    static void marketCrates(ReachBrush b, long seed) {
        for (int x = 0; x < 3; x++) {
            Material m = GOODS[(int) Math.floorMod(seed + x * 3L, (long) GOODS.length)];
            b.set(x, 0, 0, m, m == Material.BARREL ? "facing=up" : m == Material.HAY_BLOCK ? "axis=x" : "");
        }
        if ((seed & 1) == 0) b.set(1, 1, 0, Material.COMPOSTER, "level=7");
        else b.set(1, 1, 0, Material.SPRUCE_TRAPDOOR, "facing=north,half=bottom,open=false");
    }

    /**
     * A hero in weathered copper on a stepped stone pedestal, facing local -z: a Warden in plate with a cloak, a kite
     * shield on the left arm and a gilded sword raised high in the right. About eighteen blocks from foot to point.
     */
    static void statue(ReachBrush b) {
        Material body = Material.OXIDIZED_COPPER, cut = Material.OXIDIZED_CUT_COPPER;
        Material stair = Material.OXIDIZED_CUT_COPPER_STAIRS, slab = Material.OXIDIZED_CUT_COPPER_SLAB;
        // The pedestal: a broad step, a carved block with a cornice, and a plinth.
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            boolean rim = Math.abs(x) == 3 || Math.abs(z) == 3;
            b.set(x, 0, z, rim ? Material.STONE_BRICK_SLAB : Material.STONE_BRICKS, rim ? "type=bottom" : "");
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            boolean rim = Math.abs(x) == 2 || Math.abs(z) == 2;
            b.set(x, 1, z, rim ? Material.STONE_BRICK_STAIRS : Material.STONE_BRICKS,
                rim ? "facing=" + (x == -2 ? "east" : x == 2 ? "west" : z == -2 ? "south" : "north") + ",half=bottom" : "");
        }
        for (int y = 2; y <= 4; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            b.set(x, y, z, y == 3 && (x == 0 || z == 0) ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_ANDESITE);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            boolean rim = Math.abs(x) == 2 || Math.abs(z) == 2;
            b.set(x, 5, z, rim ? Material.STONE_BRICK_STAIRS : Material.STONE_BRICKS,
                rim ? "facing=" + (x == -2 ? "west" : x == 2 ? "east" : z == -2 ? "north" : "south") + ",half=top" : "");
        }
        // Boots, legs and a belted waist.
        for (int x : new int[] {-1, 1}) {
            b.set(x, 6, 0, cut);
            b.set(x, 6, -1, stair, "facing=south,half=bottom");
            for (int y = 7; y <= 9; y++) { b.set(x, y, 0, body); b.set(x, y, 1, body); }
        }
        for (int x = -1; x <= 1; x++) { b.set(x, 10, 0, cut); b.set(x, 10, 1, cut); }
        // Torso in plate, a breastplate standing proud, pauldrons at the shoulders.
        for (int y = 11; y <= 13; y++) for (int x = -1; x <= 1; x++) { b.set(x, y, 0, body); b.set(x, y, 1, body); }
        b.set(0, 12, -1, stair, "facing=south,half=top");
        b.set(0, 11, -1, slab, "type=top");
        for (int x : new int[] {-2, 2}) {
            b.set(x, 13, 0, cut);
            b.set(x, 13, 1, cut);
            b.set(x, 14, 0, slab, "type=bottom");
            b.set(x, 14, 1, slab, "type=bottom");
        }
        // Head, helm and a crest running front to back.
        b.set(0, 14, 0, body);
        b.set(0, 14, 1, body);
        b.set(0, 15, 0, cut);
        b.set(0, 15, 1, cut);
        b.set(0, 14, -1, stair, "facing=north,half=top");
        b.set(0, 16, 0, slab, "type=bottom");
        b.set(0, 16, 1, stair, "facing=south,half=bottom");
        // The sword arm, raised: forearm, fist, a guard and a gilded blade pointing at the sky.
        for (int y = 12; y <= 15; y++) b.set(2, y, 0, y == 12 ? body : cut);
        b.set(2, 16, 0, body);
        b.set(1, 17, 0, Material.LIGHTNING_ROD, "facing=west");
        b.set(3, 17, 0, Material.LIGHTNING_ROD, "facing=east");
        for (int y = 17; y <= 21; y++) b.set(2, y, 0, Material.LIGHTNING_ROD, "facing=up");
        // The shield arm, low, with a kite shield and a gilded boss.
        for (int y = 10; y <= 12; y++) b.set(-2, y, 0, body);
        for (int y = 8; y <= 12; y++) for (int z = -1; z <= 1; z++) {
            if ((y == 8 || y == 12) && z != 0) continue;
            b.set(-3, y, z, cut);
        }
        b.set(-4, 10, 0, Material.GOLD_BLOCK);
        // The cloak falling from the shoulders and flaring at the hem.
        for (int y = 7; y <= 13; y++) for (int x = -1; x <= 1; x++) b.set(x, y, 2, y <= 8 ? cut : body);
        for (int x : new int[] {-2, 2}) for (int y = 7; y <= 8; y++) b.set(x, y, 2, stair, "facing=" + (x < 0 ? "east" : "west") + ",half=bottom");
        for (int x = -1; x <= 1; x++) b.set(x, 6, 3, slab, "type=top");
    }

    /** A street lamp on a stone foot; the bracket points along local +x. */
    static void lamp(ReachBrush b, Material post, Material trapdoor) {
        b.set(0, 0, 0, Material.STONE_BRICK_WALL, "up=true");
        for (int y = 1; y <= 3; y++) b.set(0, y, 0, Material.DARK_OAK_FENCE);
        b.set(0, 4, 0, post, "axis=y");
        b.set(1, 4, 0, trapdoor, "facing=east,half=top,open=false");
        b.set(1, 3, 0, Material.LANTERN, "hanging=true");
    }
}
