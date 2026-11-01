package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static net.nerdsmp.paper.expedition.SampleKit.smooth;

/**
 * Digs a mine out of nothing: every passage is carved as open air first, then {@link #close} wraps the whole
 * of it in rock, so drifts and caverns can run into each other freely.
 *
 * <p>The rock is worked the way terrain builders work it. Carving is only the block-out. {@link #weather} then
 * pushes the surface in and out along bedding planes, and melts it, as erosion brushes do, so it gets ledges and
 * soft lumps. {@link #tone} shades it last: a ramp of blocks in order of value, so only neighbouring tones ever touch,
 * laid out in large clusters and drips that follow where a real wall weathers. Cracks and hollows take the darkest block
 * and ridges the lightest, so the shape reads even in flat light. Random speckle is what makes rock look generated, and
 * nothing here is random block to block. Every step in the rock then gets a stair or a slab, because a form that's only
 * half smoothed looks worse than one that isn't.
 */
final class MineKit {
    // Each ramp runs dark to light, in steps close enough to touch.
    private static final Material[] UPPER = {Material.TUFF, Material.COBBLESTONE, Material.ANDESITE, Material.LIGHT_GRAY_CONCRETE_POWDER};
    private static final Material[] MIDDLE = {Material.COBBLED_DEEPSLATE, Material.TUFF, Material.COBBLESTONE, Material.ANDESITE};
    private static final Material[] LOWER = {Material.DEEPSLATE_TILES, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE, Material.TUFF, Material.COBBLESTONE};
    private static final Material[] FORGE = {Material.BLACKSTONE, Material.DEEPSLATE_TILES, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE, Material.TUFF};
    /** Rock the fire is shining on, baked into the block because the game's own light has no colour. */
    private static final Material[] EMBER = {Material.BLACKSTONE, Material.BROWN_TERRACOTTA, Material.RED_TERRACOTTA, Material.TERRACOTTA, Material.ORANGE_TERRACOTTA};
    private static final Material[] DROWNED = {Material.DEEPSLATE_TILES, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE, Material.CYAN_TERRACOTTA};
    /** The wet band along the waterline. */
    private static final Material[] TIDE = {Material.DEEPSLATE_TILES, Material.DARK_PRISMARINE, Material.CYAN_TERRACOTTA, Material.PRISMARINE_BRICKS};
    private static final Material[] ORES = {Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_COPPER_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_REDSTONE_ORE};
    private static final Material TIMBER = Material.STRIPPED_SPRUCE_LOG;
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    /** Fourteen ways out from a block, to feel the shape round it at a distance. */
    private static final int[][] FAR = {{3, 0, 0}, {-3, 0, 0}, {0, 3, 0}, {0, -3, 0}, {0, 0, 3}, {0, 0, -3},
        {2, 2, 2}, {2, 2, -2}, {2, -2, 2}, {2, -2, -2}, {-2, 2, 2}, {-2, 2, -2}, {-2, -2, 2}, {-2, -2, -2}};
    /** A block of rock has to be part of something at least this big to stay. */
    private static final int CRUMB = 400;
    private static final double DRIFT_CLEAR = 2.5;
    private static final int SET_EVERY = 11, REACH = 4;

    private record Door(int x, int floor, int z, boolean alongX, int length) { }


    private final Set<Long> air = new HashSet<>();
    private final Set<Long> rock = new HashSet<>();
    /** Square-cut work, which weather leaves alone. */
    private final Set<Long> dressed = new HashSet<>();
    private final List<List<int[]>> drifts = new ArrayList<>();
    private final List<Door> doors = new ArrayList<>();
    private IntBinaryOperator ground = (x, z) -> Integer.MAX_VALUE;

    private static long key(int x, int y, int z) { return ((x + 2048L) << 40) | ((y + 512L) << 20) | (z + 2048L); }

    private static int xOf(long key) { return (int) (key >> 40) - 2048; }

    private static int yOf(long key) { return (int) ((key >> 20) & 0xFFFFF) - 512; }

    private static int zOf(long key) { return (int) (key & 0xFFFFF) - 2048; }

    boolean open(int x, int y, int z) { return air.contains(key(x, y, z)); }

    void carve(int x, int y, int z) { air.add(key(x, y, z)); }

    /** The lie of the land: no rock goes above it, so a shaft or a valley opens to the sky at the same line the hills end. */
    void ground(IntBinaryOperator height) { ground = height; }

    /** A round-backed section at a spot along a drift. */
    private void disk(double cx, int floor, double cz, double radius, int high) {
        int reach = (int) Math.ceil(radius);
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
            int x = (int) Math.round(cx) + dx, z = (int) Math.round(cz) + dz;
            double from = Math.hypot(x - cx, z - cz) / radius;
            if (from > 1) continue;
            int top = floor + 3 + (int) Math.round((high - 3) * Math.sqrt(1 - from * from));
            for (int y = floor + 1; y <= top; y++) carve(x, y, z);
        }
    }

    /** A winding drift through {x, floor, z} points, roughly {@code wide} across and {@code high} at its crown. */
    void tunnel(int wide, int high, int salt, int[]... points) {
        List<int[]> line = new ArrayList<>();
        for (int index = 0; index + 1 < points.length; index++) {
            int[] from = points[index], to = points[index + 1];
            double length = Math.hypot(to[0] - from[0], to[2] - from[2]);
            int steps = Math.max(2, (int) Math.ceil(length * 2));
            double acrossX = -(to[2] - from[2]) / length, acrossZ = (to[0] - from[0]) / length;
            for (int step = 0; step <= steps; step++) {
                double t = step / (double) steps;
                double wobble = Math.sin(t * Math.PI) * (smooth((int) (t * length) + from[0], from[2], 6, salt) - .5) * 7;
                double x = from[0] + (to[0] - from[0]) * t + acrossX * wobble, z = from[2] + (to[2] - from[2]) * t + acrossZ * wobble;
                int floor = (int) Math.round(from[1] + (to[1] - from[1]) * t);
                double radius = wide / 2D + (smooth((int) x, (int) z, 5, salt + 1) - .5) * 2;
                disk(x, floor, z, radius, high);
                int[] cell = {(int) Math.round(x), floor, (int) Math.round(z)};
                if (line.isEmpty() || line.getLast()[0] != cell[0] || line.getLast()[2] != cell[2]) line.add(cell);
            }
        }
        drifts.add(line);
    }

    /** A rough cave. Its walls stand {@code walls} high before the roof starts to close. */
    void cavern(int cx, int floor, int cz, int rx, int rz, int high, int walls, int salt) {
        for (int x = cx - rx * 2; x <= cx + rx * 2; x++) for (int z = cz - rz * 2; z <= cz + rz * 2; z++) {
            double edge = 1 + (smooth(x, z, 7, salt) - .5) * .4, from = Math.hypot((x - cx) / (double) rx, (z - cz) / (double) rz) / edge;
            if (from > 1) continue;
            int top = floor + walls + (int) Math.round((high - walls) * Math.sqrt(1 - from * from));
            for (int y = floor + 1; y <= top; y++) carve(x, y, z);
        }
    }

    /** A square-cut room. */
    void hall(int x1, int z1, int x2, int z2, int floor, int high) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) for (int y = floor + 1; y <= floor + high; y++) cut(x, y, z);
    }

    /** The rock takes back everything past this line, so a cave ends in a cliff. */
    void stopAt(int x) {
        air.removeIf(cell -> xOf(cell) > x);
        dressed.removeIf(cell -> xOf(cell) > x);
    }

    /** Clears a cell for good: the rock isn't allowed to weather back across it. */
    void cut(int x, int y, int z) {
        carve(x, y, z);
        dressed.add(key(x, y, z));
    }

    /** A round room, or a shaft if it's tall. */
    void round(int cx, int floor, int cz, int radius, int high) {
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++)
            if (Math.hypot(x - cx, z - cz) <= radius + .3) for (int y = floor + 1; y <= floor + high; y++) cut(x, y, z);
    }

    /** A timbered doorway three wide and four high, cut through {@code length} blocks of rock. */
    void door(int x, int floor, int z, boolean alongX, int length) {
        for (int along = 0; along < length; along++) for (int across = -1; across <= 1; across++) for (int y = floor + 1; y <= floor + 4; y++)
            cut(alongX ? x + along : x + across, y, alongX ? z + across : z + along);
        doors.add(new Door(x, floor, z, alongX, length));
    }

    /** The Drowned Deep, and the bay south of it past the Slag Pits. */
    private static boolean drowned(int x, int z) { return x > 168 && z < 92 || x > 200 && z < 140; }

    private static Material[] ramp(int x, int y, int z) {
        double depth = y + (smooth(x, z, 11, 501) - .5) * 8;
        if (drowned(x, z)) return depth < 17 ? TIDE : DROWNED;
        if (x > 72 && x < 172 && z > 28 && z < 132 && depth < 46) return EMBER;
        return depth >= 84 ? UPPER : depth >= 62 ? MIDDLE : depth >= 42 ? LOWER : depth >= 22 ? FORGE : DROWNED;
    }

    /** The big soft shapes in a rock face: lumps lying along the bedding, and flutes where water ran down it. */
    private static double lump(int x, int y, int z) {
        double bedding = (smooth(x, y * 3, 11, 509) + smooth(z, y * 3, 11, 510)) / 2 - .5;
        return bedding * 14 + (smooth(x, z, 6, 511) - .5) * 4;
    }

    private boolean nearFloor(int x, int y, int z) {
        return !open(x, y - 1, z) || !open(x, y - 2, z) || !open(x, y - 3, z);
    }

    private boolean floorOf(int x, int y, int z) {
        return open(x, y + 1, z) || open(x, y + 2, z) || open(x, y + 3, z);
    }

    private boolean held(long cell) {
        int x = xOf(cell), y = yOf(cell), z = zOf(cell);
        return dressed.contains(cell) || (air.contains(cell) ? nearFloor(x, y, z) : floorOf(x, y, z));
    }

    /** How far each cell near a worked surface lies from it: air counts up, rock counts down. */
    private Map<Long, Integer> band() {
        Map<Long, Integer> depth = new HashMap<>();
        List<Long> rim = new ArrayList<>();
        for (long cell : air) {
            if (dressed.contains(cell)) continue;
            int x = xOf(cell), y = yOf(cell), z = zOf(cell);
            for (int[] face : FACES) {
                long side = key(x + face[0], y + face[1], z + face[2]);
                if (air.contains(side)) continue;
                depth.put(cell, 1);
                if (depth.putIfAbsent(side, -1) == null) rim.add(side);
            }
            if (depth.containsKey(cell)) rim.add(cell);
        }
        for (int step = 2; step <= REACH; step++) {
            List<Long> next = new ArrayList<>();
            for (long cell : rim) {
                int x = xOf(cell), y = yOf(cell), z = zOf(cell), sign = Integer.signum(depth.get(cell));
                for (int[] face : FACES) {
                    long side = key(x + face[0], y + face[1], z + face[2]);
                    if (air.contains(side) != sign > 0 || depth.containsKey(side)) continue;
                    depth.put(side, sign * step);
                    next.add(side);
                }
            }
            rim = next;
        }
        return depth;
    }

    private void change(long cell, boolean open) {
        if (open) air.add(cell);
        else air.remove(cell);
    }

    /** The block-out becomes rock: the surface moves in and out along the bedding, then melts, then settles. */
    private void weather() {
        Map<Long, Integer> band = band();
        Map<Long, Boolean> moved = new HashMap<>();
        band.forEach((cell, depth) -> {
            if (held(cell)) return;
            boolean open = depth + lump(xOf(cell), yOf(cell), zOf(cell)) > 0;
            if (open != air.contains(cell)) moved.put(cell, open);
        });
        moved.forEach(this::change);
        melt(band.keySet(), 4, 5);
        melt(band.keySet(), 4, 4);
    }

    /** One pass of an erosion brush: rock with {@code wear} faces bare is worn away, and hollows with {@code silt} faces of rock fill in. */
    private void melt(Set<Long> cells, int wear, int silt) {
        Map<Long, Boolean> moved = new HashMap<>();
        for (long cell : cells) {
            if (held(cell)) continue;
            int x = xOf(cell), y = yOf(cell), z = zOf(cell), bare = 0;
            for (int[] face : FACES) if (open(x + face[0], y + face[1], z + face[2])) bare++;
            if (air.contains(cell) && 6 - bare >= silt) moved.put(cell, false);
            else if (!air.contains(cell) && bare >= wear) moved.put(cell, true);
        }
        moved.forEach(this::change);
    }

    /** A thin winding seam through the rock. */
    private static boolean seam(int x, int y, int z, int salt, double width) {
        return Math.abs((smooth(x + y, z, 9, salt) + smooth(x, z + y, 9, salt + 1)) / 2 - .5) < width;
    }

    /** How far down the wall a cell sits from whatever it stands on, for the grime at the foot of a face. */
    private int footing(int x, int y, int z) {
        for (int[] face : FACES) {
            if (face[1] != 0 || !open(x + face[0], y, z + face[2])) continue;
            int rise = 0;
            while (rise < 6 && open(x + face[0], y - rise - 1, z + face[2])) rise++;
            return rise;
        }
        return 6;
    }

    /** How a block of rock sits in the shape round it: below zero down a crack or in a hollow, above zero out on a ridge. */
    private double relief(int x, int y, int z) {
        int near = 0, far = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) if (open(x + dx, y + dy, z + dz)) near++;
        for (int[] out : FAR) if (open(x + out[0], y + out[1], z + out[2])) far++;
        return (near - 9) / 9D * .6 + (far - 5) / 5D * .4;
    }

    /** The value of a block of rock, from what's happened to it: clustered weathering, drips, dark cracks, light ridges, grime at the foot. */
    private Material tone(int x, int y, int z) {
        Material[] ramp = ramp(x, y, z);
        double cluster = (smooth(x + y, z, 14, 520) + smooth(x, z + y, 14, 521)) / 2 - .5;
        double value = .42 + cluster * 2 - Math.max(0, smooth(x, z, 4, 522) - .56) * 1.6 + relief(x, y, z) * .46;
        if (open(x, y + 1, z)) value += .16;
        if (open(x, y - 1, z)) value -= .28;
        else value -= .24 * (1 - footing(x, y, z) / 6D);
        Material block = ramp[Math.clamp((int) Math.round(value * (ramp.length - 1)), 0, ramp.length - 1)];
        // Powder falls, so it only goes on a face with rock under it.
        boolean loose = block == Material.LIGHT_GRAY_CONCRETE_POWDER && (open(x, y + 1, z) || !rock.contains(key(x, y - 1, z)));
        return loose ? Material.ANDESITE : block;
    }

    /** Wraps everything carved in rock, then sets the floors, timbers and rails. */
    void close(BuildCanvas b) {
        weather();
        // The drifts are the way through: however the rock settled, a party walks them two abreast with room to swing.
        for (List<int[]> drift : drifts) for (int[] at : drift) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            if (Math.hypot(dx, dz) <= DRIFT_CLEAR) for (int y = at[1] + 1; y <= at[1] + 5; y++) carve(at[0] + dx, y, at[2] + dz);
        Set<Long> solid = new HashSet<>();
        for (long cell : air) {
            int x = xOf(cell), y = yOf(cell), z = zOf(cell);
            boolean exposed = false;
            for (int[] face : FACES) exposed |= !open(x + face[0], y + face[1], z + face[2]);
            if (!exposed) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
                if (!open(x + dx, y + dy, z + dz)) solid.add(key(x + dx, y + dy, z + dz));
        }
        solid.removeIf(cell -> yOf(cell) > ground.applyAsInt(xOf(cell), zOf(cell)));
        dropCrumbs(solid);
        rock.addAll(solid);
        for (long cell : solid) {
            int x = xOf(cell), y = yOf(cell), z = zOf(cell);
            boolean drowned = drowned(x, z);
            if (open(x, y + 1, z)) b.block(x, y, z, smooth(x, z, 5, 503) > .66 ? Material.GRAVEL : tone(x, y, z));
            else if (drowned && seam(x, y, z, 505, .006)) b.block(x, y, z, Material.SEA_LANTERN);
            else if (!drowned && seam(x, y, z, 505, .008)) b.block(x, y, z, ORES[Math.floorMod((x + y / 2) / 16 + z / 16, ORES.length)]);
            else b.block(x, y, z, tone(x, y, z));
        }
        for (List<int[]> drift : drifts) rails(b, drift);
        ease(b);
        for (List<int[]> drift : drifts) sets(b, drift);
        for (Door door : doors) frame(b, door);
    }

    /** Weathering can leave a crumb of rock in mid air, or a shell round a pocket nobody can reach. Neither stays. */
    private static void dropCrumbs(Set<Long> solid) {
        Set<Long> seen = new HashSet<>();
        for (long start : Set.copyOf(solid)) {
            if (!seen.add(start)) continue;
            List<Long> group = new ArrayList<>(List.of(start));
            for (int index = 0; index < group.size(); index++) {
                long cell = group.get(index);
                int x = xOf(cell), y = yOf(cell), z = zOf(cell);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    long near = key(x + dx, y + dy, z + dz);
                    if (solid.contains(near) && seen.add(near)) group.add(near);
                }
            }
            if (group.size() < CRUMB) group.forEach(solid::remove);
        }
    }

    /** The lowest open block at a spot, working down from {@code y}, or {@code MIN_VALUE} if it's solid there. */
    private int standing(int x, int y, int z) {
        if (!open(x, y, z)) return Integer.MIN_VALUE;
        while (open(x, y - 1, z)) y--;
        return y;
    }

    /** The half and the stair of the same stone, or the nearest there is: {slab, stairs}. */
    private static Material[] trim(Material block) {
        return switch (block) {
            case TUFF -> new Material[]{Material.TUFF_SLAB, Material.TUFF_STAIRS};
            case COBBLESTONE -> new Material[]{Material.COBBLESTONE_SLAB, Material.COBBLESTONE_STAIRS};
            case ANDESITE, LIGHT_GRAY_CONCRETE_POWDER -> new Material[]{Material.ANDESITE_SLAB, Material.ANDESITE_STAIRS};
            case DEEPSLATE_TILES -> new Material[]{Material.DEEPSLATE_TILE_SLAB, Material.DEEPSLATE_TILE_STAIRS};
            case BLACKSTONE, BROWN_TERRACOTTA, RED_TERRACOTTA -> new Material[]{Material.BLACKSTONE_SLAB, Material.BLACKSTONE_STAIRS};
            case TERRACOTTA, ORANGE_TERRACOTTA -> new Material[]{Material.GRANITE_SLAB, Material.GRANITE_STAIRS};
            case DARK_PRISMARINE, PRISMARINE_BRICKS -> new Material[]{Material.DARK_PRISMARINE_SLAB, Material.DARK_PRISMARINE_STAIRS};
            default -> new Material[]{Material.COBBLED_DEEPSLATE_SLAB, Material.COBBLED_DEEPSLATE_STAIRS};
        };
    }

    /**
     * Stairs and slabs on every step in worked rock, floor and roof alike, in the stone it's cut from. Floors can then be
     * walked up as well as down. Square-cut rooms stay square.
     */
    private void ease(BuildCanvas b) {
        for (long cell : air) {
            int x = xOf(cell), y = yOf(cell), z = zOf(cell);
            if (dressed.contains(cell) || !CanopyKit.empty(b, x, y, z)) continue;
            boolean floor = !open(x, y - 1, z) && !CanopyKit.empty(b, x, y - 1, z);
            boolean roof = !open(x, y + 1, z) && !CanopyKit.empty(b, x, y + 1, z) && open(x, y - 1, z) && open(x, y - 2, z) && open(x, y - 3, z) && open(x, y - 4, z);
            if (!floor && !roof) continue;
            int rises = 0, riseX = 0, riseZ = 0;
            for (int[] face : FACES) {
                if (face[1] != 0) continue;
                int nx = x + face[0], nz = z + face[2];
                if (open(nx, y, nz) || !open(nx, floor ? y + 1 : y - 1, nz) || (floor && !open(nx, y + 2, nz))) continue;
                rises++;
                riseX = face[0];
                riseZ = face[2];
            }
            if (rises == 0) continue;
            Material[] trim = trim(tone(x + riseX, y, z + riseZ));
            if (rises == 1) b.block(x, y, z, trim[1], "facing=" + side(riseX, riseZ) + ",half=" + (floor ? "bottom" : "top") + ",shape=straight");
            else b.block(x, y, z, trim[0], floor ? "type=bottom" : "type=top");
        }
    }

    /** A line of rails down the middle of a drift, with its curves and slopes. */
    private void rails(BuildCanvas b, List<int[]> drift) {
        List<int[]> cells = new ArrayList<>();
        for (int[] at : drift) {
            if (!cells.isEmpty()) {
                int[] last = cells.getLast();
                if (last[0] != at[0] && last[2] != at[2]) cells.add(new int[]{at[0], at[1] + 1, last[2]});
            }
            cells.add(new int[]{at[0], at[1] + 1, at[2]});
        }
        for (int[] cell : cells) cell[1] = standing(cell[0], cell[1], cell[2]);
        for (int index = 1; index + 1 < cells.size(); index++) {
            int[] before = cells.get(index - 1), at = cells.get(index), after = cells.get(index + 1);
            if (at[1] == Integer.MIN_VALUE || before[1] == Integer.MIN_VALUE || after[1] == Integer.MIN_VALUE) continue;
            String shape = shape(before, at, after);
            if (shape != null) b.block(at[0], at[1], at[2], Material.RAIL, "shape=" + shape);
        }
    }

    private static String side(int dx, int dz) { return dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north"; }

    private static String shape(int[] before, int[] at, int[] after) {
        int fromX = before[0] - at[0], fromZ = before[2] - at[2], toX = after[0] - at[0], toZ = after[2] - at[2];
        if (Math.abs(fromX) + Math.abs(fromZ) != 1 || Math.abs(toX) + Math.abs(toZ) != 1) return null;
        if (after[1] > at[1]) return "ascending_" + side(toX, toZ);
        if (before[1] > at[1]) return "ascending_" + side(fromX, fromZ);
        if (fromX != 0 && toX != 0) return "east_west";
        if (fromZ != 0 && toZ != 0) return "north_south";
        String north = fromZ != 0 ? side(fromX, fromZ) : side(toX, toZ), east = fromX != 0 ? side(fromX, fromZ) : side(toX, toZ);
        return north + "_" + east;
    }

    /** A timber set every few yards: a post at each wall and a cap across, with a lamp under every other one. */
    private void sets(BuildCanvas b, List<int[]> drift) {
        for (int index = SET_EVERY / 2; index + 3 < drift.size(); index += SET_EVERY) {
            int[] at = drift.get(index), ahead = drift.get(index + 3), behind = drift.get(index - 3);
            double dx = ahead[0] - behind[0], dz = ahead[2] - behind[2], length = Math.hypot(dx, dz);
            if (length == 0) continue;
            boolean acrossX = Math.abs(dz) >= Math.abs(dx);
            int stepX = acrossX ? 1 : 0, stepZ = acrossX ? 0 : 1;
            int floor = standing(at[0], at[1] + 1, at[2]);
            if (floor == Integer.MIN_VALUE) continue;
            // A set wants a level floor. On a steep drop its cap ends up hanging across the hole.
            if (Math.abs(ahead[1] - behind[1]) > 2) continue;
            int left = 0, right = 0;
            while (left < 8 && open(at[0] - stepX * (left + 1), floor, at[2] - stepZ * (left + 1))) left++;
            while (right < 8 && open(at[0] + stepX * (right + 1), floor, at[2] + stepZ * (right + 1))) right++;
            int cap = floor + 3;
            // Both legs have to reach a wall and the ground, or it's not holding anything up.
            if (left >= 8 || right >= 8 || !grounded(at[0] - stepX * left, floor, at[2] - stepZ * left) || !grounded(at[0] + stepX * right, floor, at[2] + stepZ * right)) continue;
            for (int offset = -left; offset <= right; offset++) {
                int x = at[0] + stepX * offset, z = at[2] + stepZ * offset;
                if (open(x, cap, z)) b.block(x, cap, z, TIMBER, acrossX ? "axis=x" : "axis=z");
                if (offset == -left || offset == right) for (int y = cap - 1; y >= floor; y--) if (open(x, y, z)) b.block(x, y, z, TIMBER, "axis=y");
            }
            // A bracket under each end of the cap.
            for (int sign : new int[]{-1, 1}) {
                int reach = sign < 0 ? left - 1 : right - 1;
                if (reach < 2) continue;
                int x = at[0] + stepX * sign * reach, z = at[2] + stepZ * sign * reach;
                if (open(x, cap - 1, z) && open(x, cap - 2, z) && open(x, cap - 3, z) && CanopyKit.empty(b, x, cap - 1, z) && CanopyKit.empty(b, x, cap - 3, z)) b.block(x, cap - 1, z, Material.SPRUCE_STAIRS, "facing=" + side(stepX * sign, stepZ * sign) + ",half=top,shape=straight");
            }
            if (index % (SET_EVERY * 2) == SET_EVERY / 2 && open(at[0], cap - 1, at[2])) b.block(at[0], cap - 1, at[2], Material.LANTERN, "hanging=true");
        }
    }

    private boolean grounded(int x, int floor, int z) { return !open(x, floor - 1, z); }

    private void frame(BuildCanvas b, Door door) {
        for (int along = 0; along < door.length(); along += door.length() - 1) {
            for (int across = -2; across <= 2; across++) {
                int x = door.alongX() ? door.x() + along : door.x() + across, z = door.alongX() ? door.z() + across : door.z() + along;
                if (Math.abs(across) == 2) for (int y = door.floor() + 1; y <= door.floor() + 5; y++) b.block(x, y, z, TIMBER, "axis=y");
                b.block(x, door.floor() + 5, z, TIMBER, door.alongX() ? "axis=z" : "axis=x");
            }
        }
    }

    /** Run last: whatever grew across a doorway while the rooms were dressed comes out of it again. */
    void clearDoors(BuildCanvas b) {
        for (Door door : doors) for (int along = -4; along < door.length() + 4; along++) for (int across = -1; across <= 1; across++) for (int y = door.floor() + 1; y <= door.floor() + 4; y++) {
            int x = door.alongX() ? door.x() + along : door.x() + across, z = door.alongX() ? door.z() + across : door.z() + along;
            if (b.get(x, y, z) != null && b.get(x, y, z) != Material.IRON_BARS) b.block(x, y, z, Material.AIR);
        }
    }

    /** A dusting of the crystal's own colour in the rock face round a burst, thinning out with distance, so it grows out of the seam rather than sitting on it. */
    void dust(BuildCanvas b, int cx, int cy, int cz, int radius, Material glass, int salt) {
        for (int x = cx - radius; x <= cx + radius; x++) for (int y = cy - radius; y <= cy + radius; y++) for (int z = cz - radius; z <= cz + radius; z++) {
            double from = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz)) / radius;
            if (from > 1 || !rock.contains(key(x, y, z)) || CanopyKit.empty(b, x, y, z)) continue;
            if (SampleKit.noise(x + y, z, salt) < .2 * (1 - from)) b.block(x, y, z, glass);
        }
    }

    /** Fills what's open in a box with water or lava, up to {@code top}, leaving anything built there. */
    void flood(BuildCanvas b, Material liquid, int x1, int z1, int x2, int z2, int bottom, int top) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) for (int y = bottom; y <= top; y++)
            if (open(x, y, z) && CanopyKit.empty(b, x, y, z)) b.block(x, y, z, liquid, "level=0");
    }

    /** A rock standing out of the floor of a cave, from {@code bottom} to {@code top}. */
    void shore(BuildCanvas b, int cx, int cz, int rx, int rz, int bottom, int top, int salt) {
        for (int x = cx - rx - 3; x <= cx + rx + 3; x++) for (int z = cz - rz - 3; z <= cz + rz + 3; z++) {
            double from = Math.hypot((x - cx) / (double) rx, (z - cz) / (double) rz) / (1 + (smooth(x, z, 5, salt) - .5) * .5);
            if (from > 1) continue;
            int surface = top - (int) Math.round(Math.pow(from, 5) * 5);
            for (int y = bottom; y <= surface; y++) if (open(x, y, z)) b.block(x, y, z, y == surface ? (smooth(x, z, 4, salt) > .55 ? Material.GRAVEL : Material.TUFF) : tone(x, y, z));
        }
    }
}
