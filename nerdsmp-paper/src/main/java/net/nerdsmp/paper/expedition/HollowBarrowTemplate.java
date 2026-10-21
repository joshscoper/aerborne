package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleTemplates.BARROW;
import static net.nerdsmp.paper.expedition.SampleTemplates.candles;
import static net.nerdsmp.paper.expedition.SampleTemplates.doorway;
import static net.nerdsmp.paper.expedition.SampleTemplates.hall;

/**
 * The Hollow Barrow, second edition: the mouth with its campfire and the old sexton, the Crypt
 * Gallery, the Ossuary, the new Lever Vault guarded by the Barrow Warden, a quiet antechamber with
 * a brazier, and the King's Rest. Runs south (+Z); floors at Y63, doorways three wide at X -1..1.
 */
final class HollowBarrowTemplate {
    private HollowBarrowTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        hall(b, -6, 6, 0, 12, 69, BARROW);        // mouth
        hall(b, -7, 7, 14, 34, 69, BARROW);       // gallery
        hall(b, -8, 8, 36, 54, 69, BARROW);       // ossuary
        hall(b, -9, 9, 56, 74, 71, BARROW);       // lever vault
        hall(b, -4, 4, 76, 82, 68, BARROW);       // antechamber
        hall(b, -11, 11, 84, 110, 74, BARROW);    // king's rest
        doorway(b, 13, null, Material.CHISELED_STONE_BRICKS);
        doorway(b, 35, Material.IRON_BARS, Material.CHISELED_STONE_BRICKS);
        doorway(b, 55, Material.IRON_BARS, Material.CHISELED_STONE_BRICKS);
        doorway(b, 75, Material.IRON_BARS, Material.CHISELED_STONE_BRICKS);
        doorway(b, 83, null, Material.CHISELED_STONE_BRICKS);
        mouth(b);
        gallery(b);
        ossuary(b);
        vault(b);
        antechamber(b);
        kingsRest(b);
        return b.freeze();
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

    private static void gallery(BuildCanvas b) {
        for (int z = 16; z <= 32; z += 4) for (int x : new int[]{-7, 7}) {
            b.block(x, 64, z, Material.BONE_BLOCK, "axis=y");
            b.block(x, 69, z, Material.COBWEB);
        }
        // Burial niches: sealed slabs with a skull peering out.
        for (int z = 18; z <= 30; z += 6) for (int x : new int[]{-8, 8}) {
            b.block(x, 65, z, Material.AIR);
            b.block(x, 65, z, Material.SKELETON_SKULL, x < 0 ? "rotation=12" : "rotation=4");
        }
        candles(b, new int[][]{{-4, 18}, {4, 24}, {-3, 29}});
    }

    private static void ossuary(BuildCanvas b) {
        for (int[] pillar : new int[][]{{-4, 41}, {4, 41}, {-4, 49}, {4, 49}})
            for (int y = 64; y <= 69; y++) b.block(pillar[0], y, pillar[1], Material.BONE_BLOCK, "axis=y");
        for (int z = 38; z <= 52; z += 7) for (int x : new int[]{-6, 0, 6}) b.block(x, 69, z, Material.IRON_CHAIN, "axis=y");
        // Heaps of bones against the walls.
        for (int z = 38; z <= 52; z += 3) for (int x : new int[]{-8, 8}) {
            b.block(x, 64, z, Material.BONE_BLOCK, "axis=x");
            if (z % 2 == 0) b.block(x, 65, z, Material.SKELETON_SKULL, x < 0 ? "rotation=12" : "rotation=4");
        }
    }

    /** Four levers in burial niches; candles flicker in the order they must be pulled. */
    private static void vault(BuildCanvas b) {
        for (int[] lever : new int[][]{{-9, 60}, {9, 60}, {-9, 67}, {9, 67}}) {
            int wall = lever[0] < 0 ? -10 : 10;
            for (int y = 64; y <= 66; y++) b.block(wall, y, lever[1], Material.CHISELED_STONE_BRICKS);
            b.block(lever[0], 65, lever[1], Material.LEVER, "face=wall,facing=" + (lever[0] < 0 ? "east" : "west") + ",powered=false");
            b.block(lever[0] + (lever[0] < 0 ? 1 : -1), 64, lever[1], Material.CANDLE, "candles=4,lit=true");
        }
        // The Warden's plinth and a ring of grave markers.
        b.box(-7, 64, 68, -4, 64, 70, Material.POLISHED_DEEPSLATE);
        for (int[] grave : new int[][]{{-5, 58}, {5, 58}, {-6, 64}, {6, 64}, {-5, 71}, {5, 71}}) {
            b.block(grave[0], 64, grave[1], Material.COBBLED_DEEPSLATE_WALL);
            b.block(grave[0], 65, grave[1], Material.STONE_BUTTON, "face=floor,facing=north,powered=false");
        }
        for (int z = 58; z <= 72; z += 7) for (int x : new int[]{-5, 5}) b.block(x, 71, z, Material.SOUL_LANTERN, "hanging=true");
    }

    private static void antechamber(BuildCanvas b) {
        b.block(3, 64, 79, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        b.block(-3, 64, 78, Material.CHEST, "facing=east");
        b.block(-3, 64, 80, Material.BARREL, "facing=east");
        candles(b, new int[][]{{-3, 82}, {3, 77}});
    }

    private static void kingsRest(BuildCanvas b) {
        // A raised dais and the sarcophagus where the Huntsman wakes.
        b.box(-4, 64, 97, 4, 64, 103, Material.POLISHED_DEEPSLATE);
        b.box(-1, 65, 101, 1, 65, 103, Material.CHISELED_DEEPSLATE);
        for (int x = -4; x <= 4; x++) b.block(x, 64, 96, Material.POLISHED_DEEPSLATE_STAIRS, "facing=south,half=bottom,shape=straight");
        candles(b, new int[][]{{-6, 92}, {6, 92}, {-6, 100}, {6, 100}, {-2, 108}, {2, 108}});
        for (int x : new int[]{-10, 10}) for (int z = 88; z <= 104; z += 8) b.block(x, 74, z, Material.COBWEB);
        // Banners of the old king, faded to grey.
        for (int z = 90; z <= 102; z += 6) for (int x : new int[]{-11, 11}) {
            b.block(x, 70, z, Material.LIGHT_GRAY_WOOL);
            b.block(x, 69, z, Material.LIGHT_GRAY_WOOL);
            b.block(x, 68, z, Material.GRAY_WOOL);
        }
        // The cracked tomb wall at the far end, ringed by soul fire once the King falls.
        b.box(-1, 63, 107, 1, 63, 109, Material.SOUL_SOIL);
    }
}
