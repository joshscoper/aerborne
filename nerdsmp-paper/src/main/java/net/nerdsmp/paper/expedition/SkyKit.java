package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.island;
import static net.nerdsmp.paper.expedition.SampleKit.noise;
import static net.nerdsmp.paper.expedition.SampleKit.pick;

/**
 * The Wardens' Road, in pieces: sky islands with waterfalls, clouds, cherry trees, red and blue banners,
 * market tents, stone lanterns, ruined arches, rune stones and crystals, and a road on stone arches.
 * Everything is seeded by position, so a plan is the same on every server and in tests.
 */
final class SkyKit {
    private static final Material[] OLD_STONE = {Material.STONE_BRICKS, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS};
    private static final Material RAIL = Material.SPRUCE_FENCE;

    private SkyKit() { }

    /** A floating island with moss on its stone, hung with a waterfall if {@code falls} is set. */
    static void isle(BuildCanvas b, int cx, int y, int cz, int radius, int depth, boolean falls) {
        island(b, cx, y, cz, radius, depth, SampleKit.MOSSY);
        SkyRuins.hang(b, cx, y, cz, radius, depth);
        if (falls) waterfall(b, cx + radius - 1, y, cz, 22 + depth);
    }

    /** Water pouring off an edge and down into the clouds. */
    static void waterfall(BuildCanvas b, int x, int y, int z, int length) {
        b.block(x, y, z, Material.WATER, "level=0");
        for (int down = 1; down <= length; down++) for (int side = 0; side <= (down > 6 ? 1 : 0); side++) b.block(x + 1, y - down, z + side, Material.WATER, "level=8");
    }

    /** A soft heap of cloud. */
    static void cloud(BuildCanvas b, int cx, int cy, int cz, int across, int high, int salt) {
        for (int x = cx - across; x <= cx + across; x++) for (int z = cz - across; z <= cz + across; z++) for (int y = cy - high; y <= cy + high; y++) {
            double dx = (x - cx) / (double) across, dz = (z - cz) / (double) across, dy = (y - cy) / (double) high;
            if (dx * dx + dz * dz + dy * dy + (SampleKit.smooth(x, z + y, 4, salt) - .5) * .7 > 1) continue;
            b.block(x, y, z, y < cy - high / 3 ? Material.LIGHT_GRAY_WOOL : SampleKit.smooth(x, z, 7, salt + 1) < .3 && y <= cy ? Material.PINK_WOOL : Material.WHITE_WOOL);
        }
    }

    /** A cherry tree in full flower: forked boughs, each with its own puff of blossom, and petals fallen round its foot. */
    static void blossom(BuildCanvas b, int x, int y, int z, int height, int salt) {
        for (int up = 0; up < height; up++) b.block(x, y + up, z, Material.CHERRY_LOG, "axis=y");
        int across = height / 2 + 1;
        for (int index = 0; index < 5; index++) {
            double angle = salt + index * Math.TAU / 5;
            int tipX = x + (int) Math.round(Math.cos(angle) * across * .9), tipZ = z + (int) Math.round(Math.sin(angle) * across * .9), tipY = y + height + index % 2 * 2;
            int steps = across * 2;
            for (int step = 0; step <= steps; step++) {
                double t = step / (double) steps;
                b.block((int) Math.round(x + (tipX - x) * t), (int) Math.round(y + height - 3 + (tipY - y - height + 3) * t), (int) Math.round(z + (tipZ - z) * t), Material.CHERRY_WOOD);
            }
            puff(b, tipX, tipY + 1, tipZ, across * 2 / 3 + 1, 2 + index % 2, salt + index);
        }
        puff(b, x, y + height + 3, z, across * 3 / 4 + 1, 3, salt + 9);
        int spread = across + 2;
        for (int dx = -spread; dx <= spread; dx++) for (int dz = -spread; dz <= spread; dz++)
            if (b.get(x + dx, y - 1, z + dz) == Material.MOSS_BLOCK && CanopyKit.empty(b, x + dx, y, z + dz) && noise(x + dx, z + dz, salt + 11) < .4)
                b.block(x + dx, y, z + dz, Material.PINK_PETALS, "facing=north,flower_amount=" + (1 + (int) (noise(z + dz, x + dx, salt) * 4)));
    }

    /** A rough ball of blossom. */
    private static void puff(BuildCanvas b, int cx, int cy, int cz, int across, int high, int salt) {
        for (int dx = -across; dx <= across; dx++) for (int dz = -across; dz <= across; dz++) for (int dy = -high; dy <= high; dy++) {
            double inside = (dx * dx + dz * dz) / (double) (across * across) + dy * dy / (double) (high * high) + (noise(cx + dx + dy, cz + dz, salt) - .5) * .5;
            if (inside <= 1 && CanopyKit.empty(b, cx + dx, cy + dy, cz + dz)) b.block(cx + dx, cy + dy, cz + dz, Material.CHERRY_LEAVES, "persistent=true");
        }
    }

    /** A long cloth hanging from a bar: three wide, gold edged, a gold compass at its heart, a swallowtail foot. {@code y} is the top. */
    static void banner(BuildCanvas b, int x, int y, int z, boolean alongX, Material cloth) {
        int dx = alongX ? 1 : 0, dz = alongX ? 0 : 1;
        for (int side = 0; side <= 2; side++) {
            for (int down = 0; down <= 6; down++) {
                boolean compass = side == 1 ? down >= 1 && down <= 3 : down == 2;
                b.block(x + dx * side, y - down, z + dz * side, compass ? Material.YELLOW_WOOL : cloth);
            }
            if (side != 1) b.block(x + dx * side, y - 7, z + dz * side, cloth);
        }
        for (int along = -1; along <= 3; along++) b.block(x + dx * along, y + 1, z + dz * along, Material.STRIPPED_DARK_OAK_LOG, alongX ? "axis=x" : "axis=z");
    }

    /** A freestanding banner on a tall post. */
    static void bannerPost(BuildCanvas b, int x, int y, int z, boolean alongX, Material cloth) {
        for (int up = 0; up <= 8; up++) b.block(x, y + up, z, Material.STRIPPED_DARK_OAK_LOG, "axis=y");
        banner(b, x + (alongX ? 1 : 0), y + 7, z + (alongX ? 0 : 1), alongX, cloth);
    }

    /** A stone post with a lantern in a little roofed head. */
    static void stoneLantern(BuildCanvas b, int x, int y, int z) {
        b.block(x, y, z, Material.CHISELED_STONE_BRICKS);
        b.block(x, y + 1, z, Material.STONE_BRICK_WALL);
        b.block(x, y + 2, z, Material.LANTERN);
        b.block(x, y + 3, z, Material.STONE_BRICK_SLAB, "type=bottom");
    }

    /** A market stall: four posts and a striped roof. */
    static void tent(BuildCanvas b, int x, int y, int z, Material stripe) {
        for (int[] post : new int[][]{{-3, -2}, {3, -2}, {-3, 2}, {3, 2}}) for (int up = 0; up < 4; up++) b.block(x + post[0], y + up, z + post[1], Material.STRIPPED_DARK_OAK_LOG, "axis=y");
        for (int dx = -4; dx <= 4; dx++) for (int dz = -3; dz <= 3; dz++) {
            int rise = 3 - Math.abs(dz);
            b.block(x + dx, y + 3 + rise, z + dz, Math.floorMod(dx, 2) == 0 ? stripe : Material.WHITE_WOOL);
        }
        b.block(x - 1, y, z, Material.BARREL, "facing=up");
        crate(b, x + 1, y, z);
    }

    /** A stack of crates and a barrel. */
    static void crates(BuildCanvas b, int x, int y, int z) {
        crate(b, x, y, z);
        crate(b, x + 1, y, z);
        crate(b, x, y + 1, z);
        b.block(x, y, z + 1, Material.BARREL, "facing=up");
    }

    private static void crate(BuildCanvas b, int x, int y, int z) {
        b.block(x, y, z, Material.STRIPPED_SPRUCE_WOOD);
    }

    /** A ruined arch across {@code half} blocks either side, with ivy and a broken head. */
    static void arch(BuildCanvas b, int cx, int y, int cz, int half, int height, boolean alongX, int salt) {
        for (int offset = -half; offset <= half; offset++) {
            double t = Math.abs(offset) / (double) half;
            // A true round head: the opening climbs from its springing to the crown.
            int spring = height - 2 - half * 2 / 3;
            int under = y + spring + (int) Math.round(Math.sqrt(Math.max(0, 1 - t * t)) * half * 2 / 3);
            int top = y + height - (int) (noise(offset, cz, salt) * 2);
            int x = alongX ? cx + offset : cx, z = alongX ? cz : cz + offset;
            for (int level = Math.abs(offset) >= half - 1 ? y : under; level <= top; level++) {
                b.block(x, level, z, pick(OLD_STONE, x + level, z, salt));
                b.block(alongX ? x : x + 1, level, alongX ? z + 1 : z, pick(OLD_STONE, x, z + level, salt + 1));
            }
            if (noise(offset, cz + salt, 7) < .2) b.block(x, under - 1, z, Material.HANGING_ROOTS);
        }
    }

    /** A standing stone with a blue rune glowing in its face. */
    static void runeStone(BuildCanvas b, int x, int y, int z) {
        b.block(x, y, z, Material.POLISHED_DEEPSLATE);
        b.block(x, y + 1, z, Material.CHISELED_DEEPSLATE);
        b.block(x - 1, y, z, Material.SEA_LANTERN);
        b.block(x + 1, y, z, Material.SEA_LANTERN);
        b.block(x, y, z - 1, Material.POLISHED_DEEPSLATE_SLAB, "type=bottom");
        b.block(x, y, z + 1, Material.POLISHED_DEEPSLATE_SLAB, "type=bottom");
    }

    /** Blue crystals breaking out of a rock. */
    static void crystals(BuildCanvas b, int x, int y, int z, int salt) {
        for (int[] shard : new int[][]{{0, 0, 3}, {1, 0, 2}, {0, 1, 2}, {-1, 0, 1}, {0, -1, 1}}) for (int up = 0; up < shard[2]; up++)
            b.block(x + shard[0], y + up, z + shard[1], noise(x + up, z + shard[0], salt) < .4 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_STAINED_GLASS);
        b.block(x + 1, y, z + 1, Material.MOSSY_COBBLESTONE);
    }

    /** The Wardens' Road: paved, walled, on stone arches that stand on nothing at all. */
    static void road(BuildCanvas b, int z1, int z2, int y) {
        for (int z = z1; z <= z2; z++) {
            for (int x = -4; x <= 4; x++) {
                b.block(x, y, z, Math.abs(x) <= 1 ? pick(new Material[]{Material.CHISELED_STONE_BRICKS, Material.STONE_BRICKS, Material.STONE_BRICKS}, x, z, 3) : pick(OLD_STONE, x, z, 5));
                b.block(x, y - 1, z, Material.STONE_BRICKS);
            }
            for (int x : new int[]{-4, 4}) {
                b.block(x, y + 1, z, pick(OLD_STONE, x, z, 9));
                b.block(x, y + 2, z, Math.floorMod(z, 6) == 0 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICK_SLAB, Math.floorMod(z, 6) == 0 ? "" : "type=bottom");
            }
            if (Math.floorMod(z, 6) == 0) for (int x : new int[]{-4, 4}) b.block(x, y + 3, z, Material.LANTERN);
            // Piers every eight blocks, and an arch springing between them.
            int along = Math.floorMod(z - z1, 8);
            int spring = (int) Math.round(Math.sqrt(16 - (along - 4) * (along - 4)) * 1.5);
            int foot = along == 0 ? 26 : 2 + spring;
            for (int x = -4; x <= 4; x++) for (int down = 2; down <= foot; down++) b.block(x, y - down, z, pick(OLD_STONE, x, z + down, 11));
        }
    }
}
