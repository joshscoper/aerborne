package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleTemplates.BARROW;
import static net.nerdsmp.paper.expedition.SampleTemplates.candles;

/**
 * The Hollow Barrow, third edition: a dungeon to read and route through, not a chain of arenas. The
 * mouth opens on the Processional, a tall pillared nave with side aisles. That leads to the Crossing,
 * the hub. East is the Lever Vault, whose levers lift the west portcullis; west is the Ossuary and the
 * Warden's Hall, with the Widow's Cell off to one side as an optional fight. Killing the Warden lifts
 * the south gate and breaks open a tunnel to the antechamber. The King's Rest lies beyond.
 *
 * <p>Runs south (+Z) from the mouth; floors at Y63, so players stand at Y64. Enemies stand in packs,
 * listed in {@code expeditions/hollow_barrow.yml}; boss doors are barred at run time by room seals.
 */
final class HollowBarrowTemplate {
    private static final Material WALL = Material.DEEPSLATE_BRICKS;

    private HollowBarrowTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        vault(b, -6, 6, 0, 12, 69);            // mouth
        vault(b, -15, 15, 14, 54, 74);         // processional
        vault(b, -12, 12, 56, 80, 72);         // crossing
        vault(b, -24, -14, 66, 70, 68);        // west passage
        vault(b, 14, 24, 66, 70, 68);          // east passage
        vault(b, 26, 52, 54, 82, 72);          // lever vault
        vault(b, -51, -26, 52, 84, 73);        // ossuary
        vault(b, -76, -53, 56, 80, 74);        // warden's hall
        vault(b, -46, -30, 30, 50, 70);        // widow's cell
        vault(b, -6, 6, 82, 98, 69);           // antechamber
        vault(b, -18, 18, 100, 136, 78);       // king's rest
        tunnel(b);
        doors(b);
        mouth(b);
        processional(b);
        crossing(b);
        leverVault(b);
        ossuary(b);
        wardensHall(b);
        widowsCell(b);
        antechamber(b);
        kingsRest(b);
        SampleChests.place(b, "hollow_barrow");
        return b.freeze();
    }

    /** A plain enclosed space: interior X/Z inclusive, walking room from Y64 up to {@code top}. */
    private static void vault(BuildCanvas b, int x1, int x2, int z1, int z2, int top) {
        for (int x = x1 - 1; x <= x2 + 1; x++) for (int z = z1 - 1; z <= z2 + 1; z++) {
            boolean wall = x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1;
            b.block(x, 62, z, WALL);
            b.block(x, 63, z, BARROW.floor()[Math.floorMod(x * 7 + z * 13 + x * z, BARROW.floor().length)]);
            if (wall) for (int y = 64; y <= top; y++) b.block(x, y, z, BARROW.wall()[Math.floorMod(x * 31 + y * 17 + z * 13, BARROW.wall().length)]);
            b.block(x, top + 1, z, BARROW.ceiling());
        }
    }

    /** The door frames and gates between spaces. Sealed boss doors stay open air here: the run bars them. */
    private static void doors(BuildCanvas b) {
        northSouthDoor(b, 13, -2, 2, null);
        northSouthDoor(b, 55, -2, 2, null);
        northSouthDoor(b, 81, -2, 2, Material.IRON_BARS);     // lifts when the Warden falls
        northSouthDoor(b, 99, -2, 2, null);                   // sealed: the King's Rest
        northSouthDoor(b, 51, -40, -36, null);                // sealed: the Widow's Cell
        eastWestDoor(b, -13, 66, 70, Material.IRON_BARS);     // lifts when the levers are solved
        eastWestDoor(b, 13, 66, 70, null);
        eastWestDoor(b, -25, 66, 70, null);
        eastWestDoor(b, 25, 66, 70, null);
        eastWestDoor(b, -52, 66, 70, null);                   // sealed: the Warden's Hall
    }

    private static void northSouthDoor(BuildCanvas b, int z, int x1, int x2, Material gate) {
        for (int x = x1; x <= x2; x++) for (int y = 64; y <= 67; y++) {
            if (gate == null) b.block(x, y, z, Material.AIR);
            else b.block(x, y, z, gate, "east=true,west=true");
        }
        for (int x = x1 - 1; x <= x2 + 1; x++) b.block(x, 68, z, Material.CHISELED_STONE_BRICKS);
        for (int y = 64; y <= 67; y++) for (int x : new int[]{x1 - 1, x2 + 1}) b.block(x, y, z, Material.POLISHED_DEEPSLATE);
    }

    private static void eastWestDoor(BuildCanvas b, int x, int z1, int z2, Material gate) {
        for (int z = z1; z <= z2; z++) for (int y = 64; y <= 67; y++) {
            if (gate == null) b.block(x, y, z, Material.AIR);
            else b.block(x, y, z, gate, "north=true,south=true");
        }
        for (int z = z1 - 1; z <= z2 + 1; z++) b.block(x, 68, z, Material.CHISELED_STONE_BRICKS);
        for (int y = 64; y <= 67; y++) for (int z : new int[]{z1 - 1, z2 + 1}) b.block(x, y, z, Material.POLISHED_DEEPSLATE);
    }

    /** A three-wide column with a flared base and capital, the backbone of every large room. */
    private static void pillar(BuildCanvas b, int x, int z, int top) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            for (int y = 64; y <= top; y++) b.block(x + dx, y, z + dz, y % 5 == 0 ? Material.CHISELED_DEEPSLATE : Material.POLISHED_DEEPSLATE);
            b.block(x + dx, 64, z + dz, Material.DEEPSLATE_TILES);
        }
        for (int[] flare : new int[][]{{-2, 0}, {2, 0}, {0, -2}, {0, 2}}) {
            b.block(x + flare[0], 64, z + flare[1], Material.DEEPSLATE_TILE_SLAB, "type=bottom");
            b.block(x + flare[0], top, z + flare[1], Material.DEEPSLATE_BRICKS);
        }
    }

    private static void chandelier(BuildCanvas b, int x, int z, int top, int drop) {
        for (int y = top; y > top - drop; y--) b.block(x, y, z, Material.IRON_CHAIN, "axis=y");
        int y = top - drop;
        b.block(x, y, z, Material.POLISHED_DEEPSLATE);
        for (int[] arm : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) b.block(x + arm[0], y, z + arm[1], Material.SOUL_LANTERN, "hanging=false");
    }

    private static void sarcophagus(BuildCanvas b, int x, int z, boolean alongZ) {
        int length = 3;
        for (int step = 0; step < length; step++) {
            int bx = alongZ ? x : x + step, bz = alongZ ? z + step : z;
            b.block(bx, 64, bz, Material.POLISHED_BLACKSTONE_BRICKS);
            b.block(bx, 65, bz, step == 0 ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_SLAB, step == 0 ? "" : "type=bottom");
        }
    }

    private static void banner(BuildCanvas b, int x, int z, int top, Material cloth) {
        for (int y = top; y >= top - 3; y--) b.block(x, y, z, y == top - 3 ? Material.LIGHT_GRAY_WOOL : cloth);
    }

    private static void bones(BuildCanvas b, int x, int z) {
        b.block(x, 64, z, Material.BONE_BLOCK, "axis=x");
        b.block(x + 1, 64, z, Material.BONE_BLOCK, "axis=z");
        b.block(x, 65, z, Material.SKELETON_SKULL, "rotation=" + Math.floorMod(x * 3 + z, 16));
    }

    private static void brazier(BuildCanvas b, int x, int z) {
        b.block(x, 64, z, Material.CHISELED_DEEPSLATE);
        b.block(x, 65, z, Material.SOUL_CAMPFIRE, "lit=true,signal_fire=false,facing=north");
    }

    private static void mouth(BuildCanvas b) {
        // A worn stair down from the surface, roots through the ceiling and the sexton's lamp.
        for (int x = -2; x <= 2; x++) b.block(x, 64, 0, Material.STONE_BRICK_STAIRS, "facing=north,half=bottom,shape=straight");
        for (int[] root : new int[][]{{-4, 3}, {3, 5}, {-2, 10}, {5, 11}})
            for (int y = 67; y <= 69; y++) b.block(root[0], y, root[1], y == 67 ? Material.HANGING_ROOTS : Material.ROOTED_DIRT);
        b.block(4, 64, 8, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        b.block(5, 64, 8, Material.STRIPPED_SPRUCE_LOG, "axis=z");
        b.block(4, 64, 10, Material.STRIPPED_SPRUCE_LOG, "axis=x");
        b.block(-5, 64, 4, Material.BARREL, "facing=up");
        b.block(-5, 65, 4, Material.LANTERN);
        b.block(-5, 64, 8, Material.SKELETON_SKULL, "rotation=4");
        candles(b, new int[][]{{-3, 3}, {3, 3}});
    }

    /** The long nave: two rows of columns split it into a central walk and two darker aisles. */
    private static void processional(BuildCanvas b) {
        for (int z = 19; z <= 49; z += 10) for (int x : new int[]{-7, 7}) pillar(b, x, z, 74);
        for (int z = 24; z <= 44; z += 10) chandelier(b, 0, z, 74, 5);
        // The central walk's runner of old stone.
        for (int z = 14; z <= 54; z++) for (int x = -1; x <= 1; x++) b.block(x, 63, z, x == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES);
        // Burial niches along the outer walls, with their dead still in them.
        for (int z = 17; z <= 51; z += 6) for (int x : new int[]{-16, 16}) {
            b.block(x, 65, z, Material.AIR);
            b.block(x, 66, z, Material.AIR);
            b.block(x, 65, z, Material.SKELETON_SKULL, x < 0 ? "rotation=12" : "rotation=4");
            b.block(x, 67, z, Material.SOUL_LANTERN, "hanging=true");
            b.box(x < 0 ? x - 1 : x + 1, 64, z, x < 0 ? x - 1 : x + 1, 67, z, WALL);
        }
        // The hidden offering: a niche that goes further back than the others.
        for (int y = 64; y <= 66; y++) b.block(-16, y, 30, Material.AIR);
        for (int y = 64; y <= 67; y++) for (int z = 29; z <= 31; z++) b.block(-17, y, z, WALL);
        sarcophagus(b, -13, 21, true);
        sarcophagus(b, 12, 26, true);
        sarcophagus(b, -13, 41, true);
        sarcophagus(b, 12, 45, true);
        for (int z = 22; z <= 46; z += 12) for (int x : new int[]{-16, 16}) banner(b, x, z, 72, Material.GRAY_WOOL);
        bones(b, -10, 16);
        bones(b, 9, 52);
        bones(b, 13, 16);
        for (int[] web : new int[][]{{-14, 15}, {14, 15}, {-14, 53}, {14, 53}, {-10, 34}, {10, 30}}) b.block(web[0], 73, web[1], Material.COBWEB);
        candles(b, new int[][]{{-4, 16}, {4, 16}, {-4, 52}, {4, 52}});
    }

    /** The hub: a round of columns around the brazier party rests at. */
    private static void crossing(BuildCanvas b) {
        for (int[] column : new int[][]{{-8, 60}, {8, 60}, {-8, 76}, {8, 76}}) pillar(b, column[0], column[1], 72);
        // The sunken ring around the brazier.
        for (int x = -5; x <= 5; x++) for (int z = 63; z <= 73; z++) {
            int distance = x * x + (z - 68) * (z - 68);
            if (distance <= 25 && distance >= 16) b.block(x, 63, z, Material.POLISHED_DEEPSLATE);
        }
        b.block(0, 64, 68, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        chandelier(b, 0, 68, 72, 3);
        for (int[] statue : new int[][]{{-11, 57}, {11, 57}, {-11, 79}, {11, 79}}) {
            for (int y = 64; y <= 67; y++) b.block(statue[0], y, statue[1], y == 67 ? Material.CHISELED_STONE_BRICKS : Material.DEEPSLATE_BRICK_WALL);
            b.block(statue[0], 68, statue[1], Material.SKELETON_SKULL, "rotation=0");
        }
        candles(b, new int[][]{{-3, 58}, {3, 58}, {-3, 78}, {3, 78}});
        banner(b, -13, 60, 70, Material.GRAY_WOOL);
        banner(b, 13, 76, 70, Material.GRAY_WOOL);
    }

    /** Four levers on short columns; candles flicker in the order they must be pulled. */
    private static void leverVault(BuildCanvas b) {
        for (int[] lever : new int[][]{{30, 60}, {48, 60}, {30, 76}, {48, 76}}) {
            int post = lever[0] == 30 ? 29 : 49;
            for (int y = 64; y <= 66; y++) b.block(post, y, lever[1], Material.CHISELED_STONE_BRICKS);
            b.block(post, 67, lever[1], Material.SOUL_CAMPFIRE, "lit=false,signal_fire=false,facing=north");
            b.block(lever[0], 65, lever[1], Material.LEVER, "face=wall,facing=" + (lever[0] == 30 ? "east" : "west") + ",powered=false");
            b.block(lever[0] + (lever[0] == 30 ? 1 : -1), 64, lever[1], Material.CANDLE, "candles=4,lit=true");
        }
        for (int[] column : new int[][]{{34, 68}, {44, 68}}) pillar(b, column[0], column[1], 72);
        for (int z = 57; z <= 79; z += 7) for (int x : new int[]{34, 44}) if (z != 71 && z != 64) chandelier(b, x, z, 72, 2);
        // Grave markers across the floor; the party weaves between them.
        for (int[] grave : new int[][]{{38, 58}, {40, 62}, {36, 74}, {42, 78}, {39, 70}, {51, 56}, {51, 80}}) {
            b.block(grave[0], 64, grave[1], Material.COBBLED_DEEPSLATE_WALL);
            b.block(grave[0], 65, grave[1], Material.STONE_BUTTON, "face=floor,facing=north,powered=false");
        }
        sarcophagus(b, 47, 67, true);
        banner(b, 53, 62, 70, Material.GRAY_WOOL);
        banner(b, 53, 74, 70, Material.GRAY_WOOL);
    }

    /** A forest of bone columns, heaped with what the barrow has run out of room for. */
    private static void ossuary(BuildCanvas b) {
        for (int z : new int[]{58, 68, 78}) for (int x : new int[]{-46, -38, -30}) {
            if (z == 68 && x != -38) continue; // Keeps the way through to the Warden open.
            for (int y = 64; y <= 73; y++) b.block(x, y, z, Material.BONE_BLOCK, "axis=y");
            b.block(x + 1, 64, z, Material.BONE_BLOCK, "axis=x");
            b.block(x, 64, z + 1, Material.BONE_BLOCK, "axis=z");
        }
        for (int x = -50; x <= -28; x += 4) for (int z : new int[]{53, 83}) bones(b, x, z);
        for (int x = -48; x <= -28; x += 6) for (int z : new int[]{55, 81}) b.block(x, 73, z, Material.IRON_CHAIN, "axis=y");
        chandelier(b, -42, 63, 73, 4);
        chandelier(b, -34, 73, 73, 4);
    }

    /** The Warden's dais and the cracked south wall the tunnel breaks through. */
    private static void wardensHall(BuildCanvas b) {
        for (int z : new int[]{60, 76}) for (int x : new int[]{-72, -60}) pillar(b, x, z, 74);
        b.box(-71, 64, 64, -63, 64, 72, Material.POLISHED_DEEPSLATE);
        b.box(-69, 65, 66, -65, 65, 70, Material.CHISELED_DEEPSLATE);
        chandelier(b, -67, 68, 74, 4);
        for (int z = 58; z <= 78; z += 5) brazier(b, -76, z);
        banner(b, -77, 62, 72, Material.BLACK_WOOL);
        banner(b, -77, 74, 72, Material.BLACK_WOOL);
        // The wall that falls: cracked stone that reads as weak before it goes.
        for (int x = -64; x <= -62; x++) for (int y = 64; y <= 67; y++) b.block(x, y, 81, Material.CRACKED_DEEPSLATE_BRICKS);
    }

    private static void widowsCell(BuildCanvas b) {
        for (int x = -44; x <= -32; x += 4) for (int z : new int[]{31, 49}) b.block(x, 70, z, Material.COBWEB);
        sarcophagus(b, -39, 33, false);
        for (int[] column : new int[][]{{-42, 42}, {-34, 42}}) for (int y = 64; y <= 70; y++) b.block(column[0], y, column[1], Material.POLISHED_DEEPSLATE);
        candles(b, new int[][]{{-40, 36}, {-36, 36}, {-44, 34}, {-32, 34}, {-44, 46}, {-32, 46}});
        for (int z = 34; z <= 46; z += 6) for (int x : new int[]{-47, -29}) {
            b.block(x, 66, z, Material.SOUL_LANTERN);
            b.block(x < 0 ? x - 1 : x + 1, 66, z, WALL);
        }
    }

    /** The shortcut: dug from the Warden's hall to the antechamber, sealed by the cracked wall. */
    private static void tunnel(BuildCanvas b) {
        vault(b, -64, -62, 82, 86, 68);
        vault(b, -64, -8, 87, 89, 68);
        b.box(-64, 64, 86, -62, 68, 87, Material.AIR);
        for (int x = -60; x <= -9; x += 7) {
            b.block(x, 68, 88, Material.SOUL_LANTERN, "hanging=true");
            b.block(x, 67, 87, Material.DEEPSLATE_TILE_SLAB, "type=top");
            b.block(x, 67, 89, Material.DEEPSLATE_TILE_SLAB, "type=top");
        }
        for (int z = 87; z <= 89; z++) for (int y = 64; y <= 67; y++) b.block(-7, y, z, Material.AIR);
    }

    private static void antechamber(BuildCanvas b) {
        b.block(3, 64, 85, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        b.block(-4, 64, 84, Material.BARREL, "facing=east");
        for (int z = 84; z <= 96; z += 4) for (int x : new int[]{-6, 6}) b.block(x, 69, z, Material.SOUL_LANTERN, "hanging=true");
        candles(b, new int[][]{{-3, 96}, {3, 96}});
        banner(b, -7, 92, 68, Material.GRAY_WOOL);
        banner(b, 7, 92, 68, Material.GRAY_WOOL);
    }

    private static void kingsRest(BuildCanvas b) {
        // Great columns around a raised dais, where the sarcophagus the Huntsman wakes from lies.
        for (int z : new int[]{106, 118, 130}) for (int x : new int[]{-14, 14}) pillar(b, x, z, 78);
        b.box(-6, 64, 112, 6, 64, 124, Material.POLISHED_DEEPSLATE);
        for (int x = -6; x <= 6; x++) b.block(x, 64, 111, Material.POLISHED_DEEPSLATE_STAIRS, "facing=south,half=bottom,shape=straight");
        b.box(-1, 65, 118, 1, 65, 122, Material.CHISELED_DEEPSLATE);
        chandelier(b, 0, 106, 78, 6);
        chandelier(b, -8, 118, 78, 5);
        chandelier(b, 8, 118, 78, 5);
        candles(b, new int[][]{{-5, 113}, {5, 113}, {-5, 123}, {5, 123}, {-2, 134}, {2, 134}});
        for (int[] web : new int[][]{{-17, 101}, {17, 101}, {-17, 135}, {17, 135}}) b.block(web[0], 77, web[1], Material.COBWEB);
        // Banners of the old king, faded to grey.
        for (int z = 104; z <= 132; z += 7) for (int x : new int[]{-19, 19}) banner(b, x, z, 76, Material.GRAY_WOOL);
        // The cracked tomb wall at the far end, ringed by soul fire once the King falls.
        b.box(-1, 63, 133, 1, 63, 135, Material.SOUL_SOIL);
    }
}
