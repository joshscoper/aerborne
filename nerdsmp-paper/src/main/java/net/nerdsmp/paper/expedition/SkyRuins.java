package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

import static net.nerdsmp.paper.expedition.SampleKit.noise;
import static net.nerdsmp.paper.expedition.SampleKit.pick;
import static net.nerdsmp.paper.expedition.SampleKit.smooth;

/**
 * The stonework of the Wardens' Road: rock hanging under the islands, rim walls, watchtowers, timber gates,
 * curved arcades, broken columns and the ivy over all of it. Seeded by position, like the rest of the kit.
 */
final class SkyRuins {
    private static final Material[] OLD_STONE = {Material.STONE_BRICKS, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS};
    private static final Material POST = Material.DARK_OAK_LOG, BEAM = Material.STRIPPED_DARK_OAK_LOG;

    private SkyRuins() { }

    /** Spikes of rock under an island, longest near its middle, and vines trailing over its rim. */
    static void hang(BuildCanvas b, int cx, int y, int cz, int radius, int depth) {
        int spikes = 3 + radius * 2 / 3;
        for (int index = 0; index < spikes; index++) {
            double angle = index * 2.4 + cx * .13 + cz * .07, out = Math.sqrt((index + .5) / spikes) * radius * .8, fall = 1 - out / radius;
            int x = cx + (int) Math.round(Math.cos(angle) * out), z = cz + (int) Math.round(Math.sin(angle) * out);
            int start = y - (int) Math.round(depth * Math.pow(fall, .75));
            int length = Math.max(2, (int) Math.round(depth * (.35 + noise(x, z, 411) * .55) * (.5 + fall * .7)));
            double thick = 1 + radius / 8.0 + noise(z, x, 412);
            for (int down = 0; down <= length; down++) {
                double across = thick * (1 - down / (double) length);
                int reach = (int) Math.ceil(across);
                for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++)
                    if (dx * dx + dz * dz <= across * across + .2) b.block(x + dx, start - down, z + dz, pick(SampleKit.MOSSY.rock(), x + dx + down, z + dz, 413));
            }
        }
        for (double angle = 0; angle < Math.TAU; angle += 4.5 / radius) {
            int x = cx + (int) Math.round(Math.cos(angle) * (radius - 1)), z = cz + (int) Math.round(Math.sin(angle) * (radius - 1));
            int under = y - 2;
            while (under > y - depth && !CanopyKit.empty(b, x, under, z)) under--;
            CanopyKit.strand(b, x, under, z, 2 + (int) (noise(x, z, 414) * 6), true);
        }
    }

    /** A little rock under a stepping stone, so it doesn't float as a plate. */
    static void pad(BuildCanvas b, int cx, int y, int cz) {
        for (int down = 2; down <= 6; down++) {
            int reach = down <= 3 ? 1 : 0;
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) b.block(cx + dx, y - down, cz + dz, pick(SampleKit.MOSSY.rock(), cx + dx + down, cz + dz, 415));
        }
    }

    /** A low crenellated wall on an arc of a circle, between two angles in degrees (east 0, south 90). */
    static void rimWall(BuildCanvas b, int cx, int cz, int radius, int from, int to, int y, int high, int salt) {
        for (double degrees = from; degrees <= to; degrees += 20D / radius) for (int thick = 0; thick <= 1; thick++) {
            double angle = Math.toRadians(degrees);
            int x = cx + (int) Math.round(Math.cos(angle) * (radius + thick)), z = cz + (int) Math.round(Math.sin(angle) * (radius + thick));
            int top = high + (int) (smooth(x, z, 6, salt) * 2);
            for (int rel = 0; rel < top; rel++) b.block(x, y + rel, z, pick(OLD_STONE, x + rel, z, salt));
            if (thick == 1 && Math.floorMod(x + z, 3) == 0) b.block(x, y + top, z, pick(OLD_STONE, x, z, salt + 1));
        }
    }

    /** A round watchtower with lit windows and a tall red cap. */
    static void tower(BuildCanvas b, int cx, int y, int cz, int radius, int height, int salt) {
        for (int up = 0; up <= height; up++) for (int dx = -radius - 1; dx <= radius + 1; dx++) for (int dz = -radius - 1; dz <= radius + 1; dz++) {
            double from = Math.hypot(dx, dz);
            if (from > radius + .3 || from < radius - .9) continue;
            boolean window = Math.floorMod(up, 6) >= 3 && Math.floorMod(up, 6) <= 4 && (dx == 0 || dz == 0);
            b.block(cx + dx, y + up, cz + dz, window ? Material.ORANGE_STAINED_GLASS : pick(OLD_STONE, cx + dx + up, cz + dz, salt));
            if (window) b.block(cx + Integer.signum(dx) * (radius - 1), y + up, cz + Integer.signum(dz) * (radius - 1), Material.SHROOMLIGHT);
        }
        // The head steps out on corbels, then the cap climbs to a gilded point.
        for (int dx = -radius - 1; dx <= radius + 1; dx++) for (int dz = -radius - 1; dz <= radius + 1; dz++) {
            double from = Math.hypot(dx, dz);
            if (from <= radius + 1.3 && from >= radius - .4) b.block(cx + dx, y + height + 1, cz + dz, Material.CHISELED_STONE_BRICKS);
        }
        int rise = radius * 2 + 4;
        for (int up = 0; up <= rise; up++) {
            double across = (radius + 2) * (1 - up / (double) (rise + 1));
            int reach = (int) Math.ceil(across);
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
                double from = Math.hypot(dx, dz);
                if (from <= across && from >= across - 1.5) b.block(cx + dx, y + height + 2 + up, cz + dz, Math.floorMod(up, 3) == 0 ? Material.BROWN_TERRACOTTA : Material.RED_TERRACOTTA);
            }
        }
        b.block(cx, y + height + 3 + rise, cz, Material.GOLD_BLOCK);
    }

    /** A timber gate across a way running along Z: posts, knees, a tiled head and two lanterns. */
    static void gateway(BuildCanvas b, int cx, int y, int z, int half, int high) {
        for (int side : new int[]{-half, half}) {
            for (int up = 0; up < high; up++) b.block(cx + side, y + up, z, POST, "axis=y");
            b.block(cx + side, y, z - 1, Material.DARK_OAK_PLANKS);
            b.block(cx + side, y, z + 1, Material.DARK_OAK_PLANKS);
            b.block(cx + side - Integer.signum(side), y + high - 2, z, BEAM, "axis=x");
            CanopyKit.lantern(b, cx + side - Integer.signum(side) * 2, y + high - 1, z, 1);
        }
        for (int dx = -half - 2; dx <= half + 2; dx++) {
            b.block(cx + dx, y + high, z, BEAM, "axis=x");
            for (int dz = -2; dz <= 2; dz++) {
                b.block(cx + dx, y + high + 1, z + dz, Math.abs(dz) == 2 ? Material.DARK_OAK_PLANKS : Material.RED_TERRACOTTA);
                if (Math.abs(dz) <= 1) b.block(cx + dx, y + high + 2, z + dz, Material.RED_TERRACOTTA);
            }
            b.block(cx + dx, y + high + 3, z, Material.BROWN_TERRACOTTA);
        }
    }

    /**
     * A wall of round-headed bays on a circle. Below {@code sill} the bays stay stone, so a court still holds people
     * in; {@code ruin} is how much of the height the years have taken. It stops short of the way in, north and south.
     */
    static void arcade(BuildCanvas b, int cx, int cz, int radius, int y, int tiers, int tierHigh, int bay, int sill, double ruin, int salt) {
        int bays = Math.max(6, (int) Math.round(Math.TAU * radius / bay));
        double width = Math.TAU * radius / bays, rise = (width - 2.4) / 2;
        int height = tiers * tierHigh;
        for (double angle = 0; angle < Math.TAU; angle += .35 / radius) for (int thick = 0; thick <= 1; thick++) {
            int x = cx + (int) Math.round(Math.cos(angle) * (radius + thick)), z = cz + (int) Math.round(Math.sin(angle) * (radius + thick));
            if (Math.abs(x - cx) <= 7) continue;
            double along = angle / Math.TAU * bays, t = Math.abs(along - Math.floor(along) - .5) * 2 * width / (width - 2.4);
            int top = height - (int) Math.round(smooth(x, z, 8, salt) * ruin * height);
            for (int rel = 0; rel <= top; rel++) {
                if (t < 1 && opening(rel, tiers, tierHigh, sill, rise, t)) continue;
                b.block(x, y + rel, z, pick(OLD_STONE, x + rel, z, salt + 1));
            }
            if (ruin == 0 && Math.floorMod(x + z, 4) < 2) b.block(x, y + top + 1, z, pick(OLD_STONE, x, z, salt + 2));
        }
    }

    private static boolean opening(int rel, int tiers, int tierHigh, int sill, double rise, double t) {
        int tier = rel / tierHigh, within = rel - tier * tierHigh;
        if (tier >= tiers) return false;
        double spring = tierHigh - 2 - rise;
        return within >= (tier == 0 ? sill : 1) && within <= spring + Math.sqrt(Math.max(0, 1 - t * t)) * rise;
    }

    /** A column on a stepped foot, fluted by its corners, broken off at the top unless {@code whole}. */
    static void column(BuildCanvas b, int x, int y, int z, int high, boolean whole, int salt) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.block(x + dx, y, z + dz, Material.CHISELED_STONE_BRICKS);
        for (int up = 1; up <= high; up++) {
            b.block(x, y + up, z, pick(OLD_STONE, x + up, z, salt));
            for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (!whole && up > high - 1 - (int) (noise(x + side[0], z + side[1], salt) * 4)) continue;
                b.block(x + side[0], y + up, z + side[1], pick(OLD_STONE, x + side[0] + up, z + side[1], salt + 1));
            }
        }
        if (whole) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.block(x + dx, y + high + 1, z + dz, Material.CHISELED_STONE_BRICKS);
    }

    private static boolean oldStone(Material block) {
        return block == Material.STONE_BRICKS || block == Material.MOSSY_STONE_BRICKS || block == Material.CRACKED_STONE_BRICKS || block == Material.CHISELED_STONE_BRICKS;
    }

    /** Moss on the tops, ivy down the faces and vines from the soffits of the old stone in a box. Ivy stops at {@code floor}. */
    static void overgrow(BuildCanvas b, int x1, int y1, int z1, int x2, int y2, int z2, double amount, int floor, int salt) {
        List<ExpeditionLayout.Cell> stone = new ArrayList<>();
        for (var entry : b.blocks().entrySet()) {
            var at = entry.getKey();
            if (oldStone(entry.getValue().material()) && at.x() >= x1 && at.x() <= x2 && at.y() >= y1 && at.y() <= y2 && at.z() >= z1 && at.z() <= z2) stone.add(at);
        }
        for (var at : stone) {
            int x = at.x(), y = at.y(), z = at.z();
            if (CanopyKit.empty(b, x, y + 1, z) && noise(x, z + y, salt) < amount) {
                b.block(x, y, z, Material.MOSSY_STONE_BRICKS);
                b.block(x, y + 1, z, Material.MOSS_CARPET);
            }
            if (y > floor && CanopyKit.empty(b, x, y - 1, z) && noise(z, x + y, salt + 1) < amount * .4) CanopyKit.strand(b, x, y - 1, z, 2 + (int) (noise(x, y, salt + 2) * 4), true);
            for (var side : new String[][]{{"1", "0", "west"}, {"-1", "0", "east"}, {"0", "1", "north"}, {"0", "-1", "south"}}) {
                int vx = x + Integer.parseInt(side[0]), vz = z + Integer.parseInt(side[1]);
                if (noise(vx + y * 3, vz, salt + 3) >= amount * .3) continue;
                String state = "east=" + side[2].equals("east") + ",north=" + side[2].equals("north") + ",south=" + side[2].equals("south") + ",up=false,west=" + side[2].equals("west");
                for (int vy = y, left = 2 + (int) (noise(vx, vz + y, salt + 4) * 7); left > 0 && vy > floor && CanopyKit.empty(b, vx, vy, vz); vy--, left--) b.block(vx, vy, vz, Material.VINE, state);
            }
        }
    }
}
