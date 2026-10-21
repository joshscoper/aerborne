package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

/**
 * Building blocks for code-built expedition maps: floating islands, decks, bridges, ruins, posts
 * and lights. Everything is deterministic (seeded by position), so a plan is identical on every
 * server and in tests.
 */
final class SampleKit {
    private SampleKit() { }

    /** Stable pseudo-random value in [0, 1) for a position and salt. */
    static double noise(int x, int z, int salt) {
        int h = x * 374_761_393 + z * 668_265_263 + salt * 1_274_126_177;
        h = (h ^ (h >>> 13)) * 1_274_126_177;
        h ^= h >>> 16;
        return (h & 0x7fffffff) / (double) Integer.MAX_VALUE;
    }

    static Material pick(Material[] options, int x, int z, int salt) {
        return options[(int) Math.floor(noise(x, z, salt) * options.length) % options.length];
    }

    /** Island palettes: top, the soil under it, and the rock that makes up the hanging underside. */
    record Isle(Material top, Material soil, Material[] rock) { }

    static final Isle MEADOW = new Isle(Material.GRASS_BLOCK, Material.DIRT,
        new Material[]{Material.STONE, Material.ANDESITE, Material.TUFF, Material.STONE, Material.COBBLESTONE});
    static final Isle MOSSY = new Isle(Material.MOSS_BLOCK, Material.DIRT,
        new Material[]{Material.STONE, Material.MOSSY_COBBLESTONE, Material.TUFF, Material.ANDESITE});

    /**
     * A floating island: a rounded top at {@code topY} with a ragged edge, hanging down to a point
     * {@code depth} blocks below the middle.
     */
    static void island(BuildCanvas b, int cx, int topY, int cz, int radius, int depth, Isle isle) {
        for (int x = cx - radius - 2; x <= cx + radius + 2; x++) for (int z = cz - radius - 2; z <= cz + radius + 2; z++) {
            double dx = x - cx, dz = z - cz;
            double edge = radius + (noise(x, z, 7) - .5) * 2.2;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > edge) continue;
            double fall = 1 - distance / Math.max(1, edge);
            int bottom = topY - (int) Math.round(1 + depth * Math.pow(fall, .75) + (noise(x, z, 3) - .5) * 2);
            b.block(x, topY, z, isle.top());
            for (int y = topY - 1; y >= bottom; y--) {
                if (y >= topY - 2) b.block(x, y, z, isle.soil());
                else b.block(x, y, z, pick(isle.rock(), x * 3 + y, z, 11));
            }
        }
    }

    /** Scatters grass and flowers on an island top, keeping a clear lane along X -2..2. */
    static void meadow(BuildCanvas b, int cx, int topY, int cz, int radius, int salt) {
        Material[] plants = {Material.SHORT_GRASS, Material.SHORT_GRASS, Material.SHORT_GRASS, Material.DANDELION, Material.POPPY,
            Material.CORNFLOWER, Material.OXEYE_DAISY, Material.FERN};
        for (int x = cx - radius + 1; x <= cx + radius - 1; x++) for (int z = cz - radius + 1; z <= cz + radius - 1; z++) {
            if (Math.abs(x) <= 2) continue;
            double dx = x - cx, dz = z - cz;
            if (dx * dx + dz * dz > (radius - 1.5) * (radius - 1.5) || noise(x, z, salt) > .28) continue;
            b.block(x, topY + 1, z, pick(plants, x, z, salt + 1));
        }
    }

    /** A worn footpath down the island along X -1..1. */
    static void path(BuildCanvas b, int topY, int z1, int z2) {
        for (int z = z1; z <= z2; z++) for (int x = -1; x <= 1; x++)
            b.block(x, topY, z, noise(x, z, 21) < .7 ? Material.DIRT_PATH : Material.COARSE_DIRT);
    }

    /** A plank bridge three wide along X -1..1 with fence rails, from {@code z1} to {@code z2}. */
    static void bridge(BuildCanvas b, int y, int z1, int z2, Material deck, Material rail) {
        for (int z = z1; z <= z2; z++) {
            for (int x = -1; x <= 1; x++) b.block(x, y, z, deck);
            for (int x : new int[]{-2, 2}) {
                b.block(x, y, z, deck);
                b.block(x, y + 1, z, rail, "");
            }
        }
        for (int z = z1; z <= z2; z += 4) for (int x : new int[]{-2, 2}) b.block(x, y + 2, z, Material.LANTERN);
    }

    /** A 3x3 stepping stone for jump courses, with a little rock hanging under it. */
    static void stone(BuildCanvas b, int cx, int y, int cz, Material top) {
        for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) {
            b.block(x, y, z, top);
            b.block(x, y - 1, z, Material.STONE);
        }
        b.block(cx, y - 2, cz, Material.COBBLESTONE);
        b.block(cx, y - 3, cz, Material.COBBLESTONE_WALL, "");
    }

    /** A post of {@code height} blocks with a lantern on top. */
    static void lamp(BuildCanvas b, int x, int y, int z, int height, Material post) {
        for (int h = 0; h < height; h++) b.block(x, y + h, z, post, "");
        b.block(x, y + height, z, Material.LANTERN);
    }

    /** A broken wall segment: height falls away irregularly and some blocks are cracked or mossy. */
    static void ruinWall(BuildCanvas b, int x1, int z1, int x2, int z2, int y, int height, int salt) {
        Material[] stone = {Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE};
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        for (int step = 0; step <= steps; step++) {
            int x = steps == 0 ? x1 : x1 + (x2 - x1) * step / steps;
            int z = steps == 0 ? z1 : z1 + (z2 - z1) * step / steps;
            int top = Math.max(1, height - (int) Math.floor(noise(x, z, salt) * height * .8));
            for (int h = 0; h < top; h++) b.block(x, y + h, z, pick(stone, x + h, z, salt + h));
        }
    }

    /** A ring of standing pillars, skipping any that would sit in the walking lane X -2..2 near the front. */
    static void pillarRing(BuildCanvas b, int cx, int y, int cz, int radius, int count, int height, int salt) {
        for (int index = 0; index < count; index++) {
            double angle = 2 * Math.PI * index / count;
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int z = cz + (int) Math.round(Math.sin(angle) * radius);
            if (Math.abs(x) <= 2) continue;
            int top = height - (int) Math.floor(noise(x, z, salt) * 3);
            for (int h = 0; h < top; h++)
                b.block(x, y + h, z, h == top - 1 && noise(x, z, salt + 1) < .5 ? Material.CHISELED_STONE_BRICKS
                    : pick(new Material[]{Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS}, x, z + h, salt));
        }
    }
}
