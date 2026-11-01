package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.noise;
import static net.nerdsmp.paper.expedition.SampleKit.pick;
import static net.nerdsmp.paper.expedition.SampleKit.smooth;

/**
 * Gloomforge Mines: a descent from a green valley to the bottom of the world, and every level has its own look.
 * A timbered mine portal in the hillside; the Gullet, a terraced pit open to the sky with a tree grown up from
 * its floor; a cavern of blue crystal; a rail yard on trestles over a dark drop; a platform in a sea of lava
 * before a fortress with fire running down its face; and a drowned blue cathedral with the Gloomforge at anchor.
 *
 * <p>The markers in {@code expeditions/gloomforge_mines.yml} use the same coordinates.
 */
final class MinesTemplate {
    static final int PIT = 96, DEEP = 70, LINE = 52, FORGE = 32, DOCK = 14, WATER = 9, DECK = 16;
    private static final int GULLET_X = 32, GULLET_Z = 92, RING_HIGH = 92, RING_LOW = 81;
    private static final double FIRST_RAMP = Math.PI * .95, FIRST_SWEEP = 1.5, FIRST_RADIUS = 21, SECOND_RAMP = -Math.PI * .1, SECOND_SWEEP = 1.9, SECOND_RADIUS = 14.5;
    private static final int YARD_X1 = 99, YARD_X2 = 149, YARD_Z1 = 155, YARD_Z2 = 185;
    private static final int[] TRACKS_ALONG = {160, 165, 170, 175, 180}, TRACKS_ACROSS = {112, 136};
    private static final int FOUNDRY_X = 122, FOUNDRY_Z = 88, FOUNDRY_RADIUS = 18, LAVA = 27, BOIL_X = 134, BOIL_Z = 113;
    private static final Material RAIL = CanopyKit.RAIL, TIMBER = Material.STRIPPED_SPRUCE_LOG;
    private static final Material[] CRATES = {Material.SPRUCE_PLANKS, Material.STRIPPED_SPRUCE_WOOD, Material.BARREL};
    private static final Material[] DARK_ROCK = {Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF, Material.DEEPSLATE};
    private static final Material[] ICE_ROCK = {Material.DEEPSLATE_TILES, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE, Material.CYAN_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA};
    private static final Material[] SLAG_ROCK = {Material.BLACKSTONE, Material.BLACKSTONE, Material.BROWN_TERRACOTTA, Material.RED_TERRACOTTA, Material.TERRACOTTA};
    private static final Material[] HOT_ROCK = {Material.BLACKSTONE, Material.BASALT, Material.SMOOTH_BASALT, Material.BLACKSTONE};
    private static final Material[] WET_ROCK = {Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.CYAN_TERRACOTTA, Material.DEEPSLATE};
    private static final Material[] FORT = {Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.DEEPSLATE_TILES, Material.DEEPSLATE_BRICKS};

    private MinesTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        MineKit kit = new MineKit();
        kit.ground(MinesTemplate::hillHeight);
        dig(kit);
        kit.close(b);
        surface(b, kit);
        pithead(b);
        gullet(b, kit);
        crystalHollow(b, kit);
        mainline(b);
        oreLine(b, kit);
        foundry(b, kit);
        slagPits(b);
        drownedDeep(b, kit);
        kit.clearDoors(b);
        SampleChests.place(b, "gloomforge_mines");
        CanopyKit.joinRails(b);
        return b.freeze();
    }

    /** Every passage, level by level. */
    private static void dig(MineKit kit) {
        // The valley and the mine's mouth.
        kit.cavern(14, PIT, 0, 26, 16, 20, 20, 21);
        kit.hall(10, 15, 18, 19, PIT, 5);
        kit.hall(12, 19, 16, 23, PIT, 5);
        kit.tunnel(7, 6, 1, new int[]{14, 96, 24}, new int[]{12, 96, 30}, new int[]{22, 95, 38});
        kit.cavern(24, 95, 47, 13, 10, 8, 3, 2);
        kit.tunnel(6, 6, 3, new int[]{33, 95, 44}, new int[]{48, 94, 40}, new int[]{60, 93, 33});
        kit.cavern(65, 93, 31, 7, 6, 5, 3, 4);
        kit.tunnel(7, 6, 5, new int[]{23, 95, 55}, new int[]{26, 93, 60}, new int[]{29, 92, 64});
        // The Gullet: three terraces, and nothing above them but sky.
        // Dug on well past the top of the hill: weathering closes up the top of whatever it's given, and that has to happen in the open sky above the ground line.
        for (int x = GULLET_X - 48; x <= GULLET_X + 48; x++) for (int z = GULLET_Z - 48; z <= GULLET_Z + 48; z++) {
            double from = Math.hypot(x - GULLET_X, z - GULLET_Z), angle = Math.atan2(z - GULLET_Z, x - GULLET_X);
            for (int y = DEEP + 1; y <= 132; y++) {
                double wall = y <= RING_LOW ? 17 : y <= RING_HIGH ? 23 : y <= 108 ? 31 : 31 + (y - 108) * .9;
                wall += (smooth((int) Math.round(angle * 40), y / 3, 3, 601) - .5) * 5 + Math.sin(angle * 11) * 1.2;
                if (from < wall) kit.carve(x, y, z);
            }
        }
        rampRoom(kit, FIRST_RAMP, FIRST_SWEEP, FIRST_RADIUS, RING_HIGH, RING_LOW);
        rampRoom(kit, SECOND_RAMP, SECOND_SWEEP, SECOND_RADIUS, RING_LOW, DEEP);
        // Crystal Hollow, where Grell stands.
        kit.tunnel(6, 6, 6, new int[]{36, 70, 104}, new int[]{42, 70, 112}, new int[]{49, 70, 116});
        kit.door(49, DEEP, 117, false, 5);
        kit.cavern(50, DEEP, 134, 19, 12, 30, 9, 7);
        kit.door(50, DEEP, 146, false, 7);
        kit.tunnel(6, 6, 8, new int[]{50, 70, 153}, new int[]{42, 66, 160}, new int[]{38, 61, 167}, new int[]{48, 56, 175}, new int[]{60, 52, 170});
        // The Mainline, with refuges in its walls, out onto the trestles of the Ore Line.
        kit.hall(60, 169, 88, 171, LINE, 5);
        for (int x = 65; x <= 83; x += 7) {
            kit.hall(x, 166, x + 2, 168, LINE, 4);
            kit.hall(x + 3, 172, x + 5, 174, LINE, 4);
        }
        kit.cavern(124, 30, 170, 36, 25, 58, 36, 14);
        for (int z : TRACKS_ALONG) kit.hall(162, z - 1, 176, z + 1, LINE, 4);
        for (int x : TRACKS_ACROSS) {
            kit.hall(x - 1, 134, x + 1, 150, LINE, 4);
            kit.hall(x - 1, 190, x + 1, 206, LINE, 4);
        }
        kit.tunnel(6, 6, 9, new int[]{124, 52, 146}, new int[]{122, 47, 138}, new int[]{114, 42, 134}, new int[]{108, 38, 128}, new int[]{114, 35, 124}, new int[]{122, 32, 124});
        // The sea of fire, and the Slag Pits off its east causeway.
        kit.cavern(FOUNDRY_X, 18, 84, 42, 36, 66, 34, 15);
        // The vault the fortress rises into, high above the road out behind it.
        kit.cavern(124, 40, 190, 20, 13, 60, 30, 43);
        kit.cavern(FOUNDRY_X, 40, 52, 44, 24, 66, 24, 41);
        kit.tunnel(6, 6, 10, new int[]{164, 32, 88}, new int[]{172, 31, 98}, new int[]{176, 30, 104});
        kit.cavern(182, 30, 112, 16, 13, 11, 4, 11);
        kit.tunnel(7, 7, 12, new int[]{122, 32, 47}, new int[]{126, 28, 40}, new int[]{136, 23, 38}, new int[]{150, 18, 36}, new int[]{166, 14, 34});
        // The Drowned Deep.
        kit.door(167, DOCK, 34, true, 5);
        kit.cavern(216, 1, 40, 44, 50, 58, 30, 13);
        GloomforgePalace.dig(kit);
        kit.hall(170, 24, 192, 44, 1, 26);
    }

    private static void plank(BuildCanvas b, int x, int y, int z) {
        b.block(x, y, z, noise(x, z, 511) < .12 ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS);
    }

    private static void crates(BuildCanvas b, int x, int y, int z) {
        for (int[] at : new int[][]{{0, 0, 0}, {1, 0, 0}, {0, 0, 1}, {0, 1, 0}}) {
            Material crate = pick(CRATES, x + at[0], z + at[2] + at[1], 512);
            if (crate == Material.BARREL) b.block(x + at[0], y + at[1], z + at[2], crate, "facing=up");
            else b.block(x + at[0], y + at[1], z + at[2], crate);
        }
    }

    /** A heap of raw ore where someone tipped a load and never came back. */
    private static void spoil(BuildCanvas b, int x, int y, int z, Material ore) {
        b.block(x, y, z, ore);
        b.block(x + 1, y, z, Material.GRAVEL);
        b.block(x, y, z + 1, Material.GRAVEL);
    }

    private static int hillHeight(int x, int z) {
        // The valley's edge wanders by a fifth, so the hill has to start inside its narrowest point.
        double valley = Math.hypot((x - 14) / 26D, z / 16D);
        if (valley < .8) return PIT;
        double climb = Math.min(24, (valley - .8) * 34 + (smooth(x, z, 13, 602) - .3) * 14 + smooth(x, z, 5, 603) * 3);
        // The hill stands up as a crag behind the mine's mouth, so the portal is cut into something.
        double crag = Math.clamp((z - 15) * 2.6, 0, 26) * Math.exp(-Math.pow((x - 14) / 24D, 2)) * (z > 44 ? Math.max(0, 1 - (z - 44) / 18D) : 1);
        int rise = Math.max(1, (int) Math.round(Math.max(climb, crag + (smooth(x, z, 4, 616) - .5) * 5)));
        // Round the mine's mouth the hill is a level bank at the head of the portal, and it climbs away behind it.
        boolean mouth = z >= 14 && z <= 24 && Math.abs(x - 14) <= 11;
        return PIT + (mouth ? Math.max(11, z > 19 ? rise : 11) : rise);
    }

    /** Bare hillside in beds of stone, dark low down in the cracks and pale on the faces. */
    private static Material bed(int x, int y, int z) {
        double value = smooth(x, y * 2, 9, 617) * .5 + smooth(z, y * 2, 9, 618) * .5 + (smooth(x + y, z, 4, 615) - .5);
        return value < .34 ? Material.TUFF : value < .47 ? Material.COBBLESTONE : value < .62 ? Material.ANDESITE : Material.STONE;
    }

    /** The hills: grass over dirt in cut terraces, ringing the valley and running back over the mine to the rim of the pit. */
    private static void surface(BuildCanvas b, MineKit kit) {
        for (int x = -46; x <= 92; x++) for (int z = -44; z <= 146; z++) {
            boolean nearValley = Math.hypot((x - 14) / 26D, z / 16D) < 2, nearPit = Math.hypot(x - GULLET_X, z - GULLET_Z) < 58;
            boolean between = z >= 0 && z <= 100 && x >= -30 && x <= 80;
            if (!nearValley && !nearPit && !between) continue;
            int top = hillHeight(x, z);
            // Where the pit or the valley has eaten the hill, the turf runs down to the lip.
            int lip = nearValley && !nearPit ? PIT : 104;
            while (top > lip && kit.open(x, top, z)) top--;
            if (kit.open(x, top, z) || top <= PIT) continue;
            int lowest = top;
            for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) lowest = Math.min(lowest, hillHeight(x + side[0], z + side[1]));
            // Steep ground sheds its turf. The lip of the pit has been trampled to bare earth.
            boolean bare = top - lowest >= 2;
            boolean trampled = nearPit && (kit.open(x + 3, top, z) || kit.open(x - 3, top, z) || kit.open(x, top, z + 3) || kit.open(x, top, z - 3));
            if (bare) b.block(x, top, z, bed(x, top, z));
            else if (trampled) b.block(x, top, z, lowest < top ? bed(x, top, z) : smooth(x, z, 6, 619) < .5 ? Material.COARSE_DIRT : Material.GRAVEL);
            else b.block(x, top, z, Material.GRASS_BLOCK, "snowy=false");
            for (int y = top - 1; y >= Math.min(lowest, top - 4); y--) if (!kit.open(x, y, z)) b.block(x, y, z, top - y <= 1 && !bare ? Material.DIRT : bed(x, y, z));
            if (bare || trampled) continue;
            double roll = noise(x, z, 604);
            if (roll < .1) b.block(x, top + 1, z, Material.SHORT_GRASS);
            else if (roll < .13) b.block(x, top + 1, z, Material.FERN);
            else if (roll < .14) b.block(x, top + 1, z, Material.SWEET_BERRY_BUSH, "age=2");
            else if (roll > .988 && lowest >= top - 1) MineProps.pine(b, x, top + 1, z, 11 + (int) (noise(z, x, 605) * 13), x + z);
            else if (roll > .982 && lowest >= top - 1) MineProps.boulder(b, x, top + 1, z, 2 + (int) (noise(z, x, 606) * 2), 2, 2, x * 3 + z);
        }
    }

    /** The valley floor, the portal, and the miners' last camp in the open air. */
    private static void pithead(BuildCanvas b) {
        for (int x = -16; x <= 44; x++) for (int z = -18; z <= 16; z++) {
            if (b.get(x, PIT, z) == null || b.get(x, PIT + 1, z) != null) continue;
            b.block(x, PIT, z, Material.GRASS_BLOCK, "snowy=false");
            double roll = noise(z, x, 606);
            if (roll < .14) b.block(x, PIT + 1, z, Material.SHORT_GRASS);
            else if (roll < .17) b.block(x, PIT + 1, z, Material.FERN);
        }
        // The worn road up to the mouth, in broken flags.
        for (int z = -6; z <= 23; z++) for (int x = 10; x <= 18; x++) {
            double wander = Math.abs(x - 14) + (noise(x, z, 607) - .5) * 3;
            if (wander > 3.2 + (z > 8 ? 1 : 0)) continue;
            b.block(x, PIT, z, wander > 2.2 ? (noise(x, z, 609) < .5 ? Material.COARSE_DIRT : Material.GRAVEL) : noise(x * 3, z, 608) < .3 ? Material.COARSE_DIRT : noise(x, z * 3, 609) < .4 ? Material.ANDESITE : Material.PACKED_MUD);
            if (b.get(x, PIT + 1, z) == Material.SHORT_GRASS || b.get(x, PIT + 1, z) == Material.FERN) b.block(x, PIT + 1, z, Material.AIR);
        }
        // The throat of the mine: dressed stone, with a stone bench and lamp let into the wall.
        for (int z = 19; z <= 23; z++) {
            for (int y = PIT + 1; y <= PIT + 5; y++) for (int x : new int[]{11, 17}) b.block(x, y, z, Math.floorMod(y + z, 3) == 0 ? Material.ANDESITE : Material.STONE_BRICKS);
            for (int x = 11; x <= 17; x++) b.block(x, PIT + 6, z, x % 2 == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
        }
        // A stone bench and a shelf let into the side of the throat, as men left them.
        for (int y = PIT + 1; y <= PIT + 2; y++) b.block(10, y, 17, Material.STONE_BRICKS);
        b.block(11, PIT + 1, 17, Material.STONE_BRICK_STAIRS, "facing=west,half=bottom,shape=straight");
        b.block(10, PIT + 3, 17, Material.SPRUCE_SLAB, "type=bottom");
        b.block(10, PIT + 4, 17, Material.LANTERN, "hanging=false");
        MineProps.portal(b, 14, PIT + 1, 16);
        for (int z = 10; z <= 24; z++) b.block(16, PIT + 1, z, Material.RAIL, "shape=north_south");
        MineProps.cart(b, 16, PIT + 1, 8, Material.RAW_IRON_BLOCK);
        crates(b, 21, PIT + 1, 4);
        crates(b, 5, PIT + 1, 12);
        b.block(9, PIT + 1, 14, Material.CHEST, "facing=north");
        b.block(18, PIT + 1, 12, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        b.block(19, PIT + 1, 12, TIMBER, "axis=z");
        b.block(18, PIT + 1, 13, TIMBER, "axis=x");
        MineProps.shack(b, 27, PIT, 8, 3, 2);
        MineProps.shack(b, 1, PIT, 2, 3, 2);
        // Big trees and big rocks to frame the mouth, and something small and bright beside them for scale.
        for (int[] tree : new int[][]{{-5, -4, 20}, {-11, 8, 17}, {33, -6, 22}, {38, 9, 16}, {0, -14, 15}, {24, -15, 18}}) MineProps.pine(b, tree[0], PIT + 1, tree[1], tree[2], tree[0] + tree[1]);
        for (int[] rock : new int[][]{{-1, 11, 3, 2, 3}, {26, 13, 2, 3, 2}, {35, 2, 3, 3, 4}, {-9, -2, 2, 2, 2}, {8, -12, 3, 2, 3}, {30, -10, 2, 2, 2}}) MineProps.boulder(b, rock[0], PIT + 1, rock[1], rock[2], rock[3], rock[4], rock[0] * 5 + rock[1]);
        for (int x = -14; x <= 42; x++) for (int z = -16; z <= 14; z++) {
            if (b.get(x, PIT, z) != Material.GRASS_BLOCK || !CanopyKit.empty(b, x, PIT + 1, z)) continue;
            double drift = smooth(x, z, 6, 623), roll = noise(x * 5, z * 3, 624);
            if (drift > .66 && roll < .3) b.block(x, PIT + 1, z, drift > .72 ? Material.BLUE_ORCHID : Material.AZURE_BLUET);
        }
    }

    /** A timber fence along the inner edge of a terrace, wherever the floor drops away. */
    private static void terraceRail(BuildCanvas b, MineKit kit, int y, double from, double to, double gapAt) {
        int lastX = 0, lastZ = 0;
        boolean first = true;
        for (double angle = 0; angle < Math.TAU; angle += .012) {
            double off = Math.abs(Math.IEEEremainder(angle - gapAt, Math.TAU));
            if (off < .28) { first = true; continue; }
            for (double r = from; r <= to; r += .4) {
                int x = GULLET_X + (int) Math.round(Math.cos(angle) * r), z = GULLET_Z + (int) Math.round(Math.sin(angle) * r);
                if (kit.open(x, y, z) || !kit.open(x, y + 1, z)) continue;
                if (CanopyKit.empty(b, x, y + 1, z)) b.block(x, y + 1, z, RAIL);
                // Fences only join square on, so a diagonal step needs a post in the corner or it's a row of sticks.
                if (!first && Math.abs(x - lastX) == 1 && Math.abs(z - lastZ) == 1 && !kit.open(lastX, y, z) && kit.open(lastX, y + 1, z) && CanopyKit.empty(b, lastX, y + 1, z)) b.block(lastX, y + 1, z, RAIL);
                lastX = x;
                lastZ = z;
                first = false;
                break;
            }
        }
    }

    /** Head room over a stair, so the rock of the wall can't lean out across it. */
    private static void rampRoom(MineKit kit, double start, double span, double radius, int top, int bottom) {
        for (int step = 0; step <= 900; step++) {
            double t = step / 900D, angle = start + span * t;
            int floor = (int) Math.floor(top - (top - bottom) * t);
            for (double r = radius - 1.7; r <= radius + 4.2; r += .45) {
                int x = GULLET_X + (int) Math.round(Math.cos(angle) * r), z = GULLET_Z + (int) Math.round(Math.sin(angle) * r);
                for (int y = floor + 1; y <= floor + 5; y++) kit.cut(x, y, z);
            }
        }
    }

    /** A timber stair sweeping down round the wall from one terrace to the next. */
    private static void ramp(BuildCanvas b, MineKit kit, double start, double span, double radius, int top, int bottom) {
        int steps = 900;
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps, angle = start + span * t, height = top - (top - bottom) * t;
            int whole = (int) Math.floor(height);
            boolean half = height - whole >= .5;
            for (double r = radius - 1.7; r <= radius + 3.2; r += .45) {
                int x = GULLET_X + (int) Math.round(Math.cos(angle) * r), z = GULLET_Z + (int) Math.round(Math.sin(angle) * r);
                plank(b, x, whole, z);
                if (half) b.block(x, whole + 1, z, Material.SPRUCE_SLAB, "type=bottom");
                else if (b.get(x, whole + 1, z) == Material.SPRUCE_SLAB) b.block(x, whole + 1, z, Material.AIR);
            }
            int edgeX = GULLET_X + (int) Math.round(Math.cos(angle) * (radius - 2.2)), edgeZ = GULLET_Z + (int) Math.round(Math.sin(angle) * (radius - 2.2));
            if (height > bottom + 1) b.block(edgeX, whole + (half ? 2 : 1), edgeZ, RAIL);
            // A leg every so often, down to whatever's under it.
            if (step % 170 == 0) for (int y = whole - 1; y > DEEP - 2 && kit.open(edgeX, y, edgeZ); y--) b.block(edgeX, y, edgeZ, TIMBER, "axis=y");
        }
    }

    /** Earth trodden over the bare rock of a terrace, thinner the deeper it lies, so a pit isn't all one grey. */
    private static void earth(BuildCanvas b, MineKit kit, int y, int from, int to, double thin) {
        for (int x = GULLET_X - to; x <= GULLET_X + to; x++) for (int z = GULLET_Z - to; z <= GULLET_Z + to; z++) {
            double r = Math.hypot(x - GULLET_X, z - GULLET_Z);
            if (r < from || r > to || !kit.open(x, y + 1, z) || kit.open(x, y, z) || !CanopyKit.empty(b, x, y + 1, z)) continue;
            Material here = b.get(x, y, z);
            if (here == null || here.name().contains("PLANK") || here.name().contains("SLAB") || here.name().contains("STAIRS")) continue;
            double patch = smooth(x, z, 5, 642), fine = smooth(x, z, 2, 643);
            if (patch < thin - .22) continue;
            b.block(x, y, z, patch > thin + .16 && y == RING_HIGH ? Material.MOSS_BLOCK : fine < .4 ? Material.COARSE_DIRT : fine < .6 ? Material.PACKED_MUD : Material.ROOTED_DIRT);
            double roll = noise(x, z, 644);
            if (y == RING_HIGH && roll < .12) b.block(x, y + 1, z, roll < .08 ? Material.SHORT_GRASS : Material.FERN);
        }
    }

    /** Spruce shoring against the wall of a terrace: posts on sills, a ledger run along their heads, and braces, as if it's all that's holding the rock back. */
    private static void shoring(BuildCanvas b, MineKit kit, int y, int high, int rise) {
        int lastX = 0, lastZ = 0;
        boolean first = true;
        for (double angle = 0; angle < Math.TAU; angle += 9D / high) {
            int foot = 0, footZ = 0;
            boolean found = false;
            for (double r = high - 4; r < high + 12 && !found; r += .5) {
                int x = GULLET_X + (int) Math.round(Math.cos(angle) * r), z = GULLET_Z + (int) Math.round(Math.sin(angle) * r);
                if (kit.open(x, y + 2, z)) { foot = x; footZ = z; }
                else if (foot != 0) found = true;
            }
            if (!found || !CanopyKit.empty(b, foot, y + 1, footZ)) { first = true; continue; }
            b.block(foot, y + 1, footZ, Material.COBBLESTONE_WALL);
            // A post only stands where it can run clear from sill to head; rock in the way means there's no set here.
            boolean clear = true;
            for (int up = 2; up <= rise + 1; up++) clear &= kit.open(foot, y + up, footZ) && CanopyKit.empty(b, foot, y + up, footZ);
            if (!clear) { first = true; continue; }
            for (int up = 2; up <= rise; up++) b.block(foot, y + up, footZ, TIMBER, "axis=y");
            if (!first && Math.hypot(foot - lastX, footZ - lastZ) < 9 && lineClear(kit, lastX, y + rise + 1, lastZ, foot, footZ)) MineProps.line(b, lastX, y + rise + 1, lastZ, foot, y + rise + 1, footZ, TIMBER, "axis=y");
            lastX = foot;
            lastZ = footZ;
            first = false;
        }
    }

    private static boolean lineClear(MineKit kit, int x1, int y, int z1, int x2, int z2) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        for (int step = 0; step <= steps; step++) if (!kit.open(x1 + (x2 - x1) * step / Math.max(1, steps), y, z1 + (z2 - z1) * step / Math.max(1, steps))) return false;
        return true;
    }

    /** The great open pit: terraces and working camps, stairs of timber, gold lamps on the rock, and a tree grown up from the floor to find the light. */
    private static void gullet(BuildCanvas b, MineKit kit) {
        ramp(b, kit, FIRST_RAMP, FIRST_SWEEP, FIRST_RADIUS, RING_HIGH, RING_LOW);
        ramp(b, kit, SECOND_RAMP, SECOND_SWEEP, SECOND_RADIUS, RING_LOW, DEEP);
        terraceRail(b, kit, RING_HIGH, 18, 30, FIRST_RAMP);
        terraceRail(b, kit, RING_LOW, 12, 23, SECOND_RAMP);
        earth(b, kit, RING_HIGH, 24, 34, .46);
        earth(b, kit, RING_LOW, 17, 24, .6);
        earth(b, kit, DEEP, 0, 18, .7);
        shoring(b, kit, RING_HIGH, 26, 9);
        shoring(b, kit, RING_LOW, 18, 7);
        // The tree. Nothing should grow down here. It grew anyway.
        CanopyKit.trunk(b, GULLET_X, GULLET_Z, DEEP + 1, DEEP + 22, 3.6, 610);
        CanopyKit.crown(b, GULLET_X, DEEP + 22, GULLET_Z, 10, 611);
        // Camps on the upper terrace, each on its own stretch of rock, stacked up the wall where the ground ran out.
        for (double angle : new double[]{-.9, .1, .9, 1.75, 4.3}) {
            int x = GULLET_X + (int) Math.round(Math.cos(angle) * 31), z = GULLET_Z + (int) Math.round(Math.sin(angle) * 31);
            MineProps.shack(b, x, RING_HIGH, z, 2, 2);
        }
        for (double angle : new double[]{-.9, .9}) {
            int x = GULLET_X + (int) Math.round(Math.cos(angle) * 36), z = GULLET_Z + (int) Math.round(Math.sin(angle) * 36);
            MineProps.shack(b, x, RING_HIGH + 7, z, 2, 2);
        }
        // Gold light on the rock, where the lamps of the old shifts were set into the floor.
        for (double angle = .3; angle < Math.TAU; angle += .75) {
            double r = 19 + noise((int) (angle * 10), 3, 612) * 3;
            int x = GULLET_X + (int) Math.round(Math.cos(angle) * r), z = GULLET_Z + (int) Math.round(Math.sin(angle) * r);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                if (Math.abs(dx) + Math.abs(dz) < 2 && b.get(x + dx, RING_LOW, z + dz) != null && !b.get(x + dx, RING_LOW, z + dz).name().contains("PLANK")) b.block(x + dx, RING_LOW, z + dz, Material.OCHRE_FROGLIGHT, "axis=y");
        }
        for (double angle = .1; angle < Math.TAU; angle += .95) {
            int x = GULLET_X + (int) Math.round(Math.cos(angle) * 13), z = GULLET_Z + (int) Math.round(Math.sin(angle) * 13);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (Math.abs(dx) + Math.abs(dz) < 2) b.block(x + dx, DEEP, z + dz, Material.OCHRE_FROGLIGHT, "axis=y");
        }
        // Green hanging down over the lip, a ribbon of fire down one wall, a hoist over the drop.
        for (double angle = 0; angle < Math.TAU; angle += .16) {
            double r = 33 + (noise((int) (angle * 9), 5, 613) * 3);
            int x = GULLET_X + (int) Math.round(Math.cos(angle) * r), z = GULLET_Z + (int) Math.round(Math.sin(angle) * r), y = 113;
            while (y > 96 && !kit.open(x, y, z)) y--;
            if (y > 96) CanopyKit.strand(b, x, y, z, 6 + (int) (noise(x, z, 614) * 14), true);
        }
        MineProps.lavafall(b, GULLET_X + 30, 104, GULLET_Z - 8, RING_HIGH + 1, 1);
        b.block(GULLET_X + 29, RING_HIGH, GULLET_Z - 8, Material.MAGMA_BLOCK);
        // The great hoist on the upper terrace, its jib out over the drop against the sky, and the headhouse that worked it.
        MineProps.crane(b, GULLET_X - 27, RING_HIGH + 1, GULLET_Z - 11, 20, 18, 1, 26, Material.RAW_GOLD_BLOCK);
        MineProps.headhouse(b, GULLET_X - 32, RING_HIGH + 1, GULLET_Z + 8, 4, 3, 641);
        b.block(24, DEEP + 1, 98, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        spoil(b, GULLET_X + 8, DEEP + 1, GULLET_Z - 6, Material.RAW_IRON_BLOCK);
        spoil(b, GULLET_X - 8, DEEP + 1, GULLET_Z + 7, Material.COAL_BLOCK);
    }

    /** Grell's cave: a seam of blue crystal, ice underfoot, crystal bursting from every wall, columns floor to roof, and timber loads swung overhead that can be cut down on him. */
    private static void crystalHollow(BuildCanvas b, MineKit kit) {
        for (int x = 31; x <= 69; x++) for (int z = 122; z <= 146; z++) {
            if (b.get(x, DEEP, z) == null) continue;
            double seam = smooth(x, z, 5, 620);
            b.block(x, DEEP, z, seam > .58 ? Material.PACKED_ICE : seam > .52 ? Material.BLUE_ICE : seam > .44 ? Material.CALCITE : noise(x, z, 622) < .5 ? Material.DEEPSLATE_TILES : Material.COBBLED_DEEPSLATE);
        }
        for (int[] burst : new int[][]{{37, 128, 12}, {63, 128, 14}, {35, 140, 9}, {65, 140, 11}, {37, 123, 6}, {58, 144, 8}}) MineProps.crystals(b, burst[0], DEEP + 1, burst[1], burst[2], MineProps.BLUE, burst[0] + burst[1]);
        for (int[] drip : new int[][]{{44, 126, 8}, {56, 126, 7}, {40, 136, 9}, {60, 136, 8}, {50, 142, 7}}) MineProps.curtain(b, drip[0], roof(kit, drip[0], DEEP + 12, drip[1], 110), drip[1], Math.min(drip[2] + 6, roof(kit, drip[0], DEEP + 12, drip[1], 110) - DEEP - 6), 3, drip[0] % 2 == 0, DARK_ROCK, Material.LIGHT_BLUE_STAINED_GLASS, drip[0]);
        // Bursts thrown out of the walls at every height, big ones low and small ones up in the dark.
        for (int index = 0; index < 20; index++) {
            double angle = index * 2.399, lift = noise(index, 3, 645);
            int y = DEEP + 3 + (int) (lift * lift * 20), x = 50, z = 134;
            for (double step = 0; step < 26 && kit.open(x, y, z); step += .5) {
                x = 50 + (int) Math.round(Math.cos(angle) * step * 1.5);
                z = 134 + (int) Math.round(Math.sin(angle) * step);
            }
            if (kit.open(x, y, z)) continue;
            MineProps.burst(b, x, y, z, 5 + (int) ((1 - lift) * 11), -Math.cos(angle) * .8, -Math.sin(angle) * .6, MineProps.BLUE, index * 7 + 3);
            kit.dust(b, x, y, z, 5, Material.CYAN_STAINED_GLASS, index + 646);
        }
        for (int[] column : new int[][]{{36, 134, 4}, {64, 133, 4}, {39, 126, 3}, {61, 142, 3}}) MineProps.column(b, column[0], DEEP, column[1], roof(kit, column[0], DEEP + 12, column[1], 110), column[2], ICE_ROCK, column[0] + column[1]);
        for (int[] load : new int[][]{{44, 130}, {56, 130}, {44, 139}, {56, 139}}) {
            for (int y = DEEP + 8; y <= roof(kit, load[0], DEEP + 12, load[1], 110); y++) b.block(load[0], y, load[1], Material.IRON_CHAIN, "axis=y");
            b.block(load[0], DEEP + 7, load[1], Material.STRIPPED_OAK_WOOD);
            for (int[] arm : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) b.block(load[0] + arm[0], DEEP + 7, load[1] + arm[1], TIMBER, arm[0] != 0 ? "axis=x" : "axis=z");
        }
        // The south door, barred till Grell is down.
        for (int x = 49; x <= 51; x++) for (int y = DEEP + 1; y <= DEEP + 4; y++) b.block(x, y, 150, Material.IRON_BARS, "east=true,west=true");
    }

    /** The first block of roof above a spot, or {@code limit}. */
    private static int roof(MineKit kit, int x, int y, int z, int limit) {
        while (y < limit && kit.open(x, y, z)) y++;
        return y;
    }

    private static void straightRail(BuildCanvas b, int x1, int z1, int x2, int z2, int y) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
            b.block(x, y, z, Material.RAIL, x1 == x2 ? "shape=north_south" : "shape=east_west");
    }

    /** One straight run of rails, and the refuges men jumped into when a cart came. */
    private static void mainline(BuildCanvas b) {
        straightRail(b, 60, 170, 96, 170, LINE + 1);
        for (int x = 66; x <= 88; x += 6) {
            b.block(x, LINE + 5, 170, Material.IRON_CHAIN, "axis=y");
            b.block(x, LINE + 4, 170, Material.LANTERN, "hanging=true");
            for (int side : new int[]{-1, 1}) b.block(x, LINE + 5, 170 + side, TIMBER, "axis=x");
        }
        for (int x = 65; x <= 83; x += 7) {
            b.block(x + 1, LINE + 4, 166, Material.LANTERN, "hanging=true");
            b.block(x + 4, LINE + 4, 174, Material.LANTERN, "hanging=true");
        }
        // Out of the rock, the Mainline runs on along a trestle to the yard.
        for (int x = 86; x <= 98; x++) {
            for (int z = 168; z <= 172; z++) plank(b, x, LINE, z);
            for (int z : new int[]{168, 172}) b.block(x, LINE + 1, z, RAIL);
            if (x % 6 == 0) MineProps.trestle(b, x, LINE, 170, true, 31);
        }
    }

    /** The great headframe over the shaft: four battered legs braced in tiers, a back stay, a platform and sheave wheels at the very top. */
    private static void headframe(BuildCanvas b, int x, int z) {
        int foot = 33, top = 84;
        for (int y = foot; y <= top; y++) {
            int spread = 3 + (top - y) / 8;
            for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) b.block(x + sx * spread, y, z + sz * spread, Material.DARK_OAK_LOG, "axis=y");
            if ((top - y) % 8 == 2) for (int run = -spread; run <= spread; run++) for (int side : new int[]{-1, 1}) {
                b.block(x + run, y, z + side * spread, TIMBER, "axis=x");
                b.block(x + side * spread, y, z + run, TIMBER, "axis=z");
            }
        }
        for (int panel = top - 2, flip = 0; panel - 8 >= foot; panel -= 8, flip++) {
            int high = 3 + (top - panel) / 8, low = 3 + (top - panel + 8) / 8, sign = flip % 2 == 0 ? 1 : -1;
            for (int side : new int[]{-1, 1}) {
                MineProps.line(b, x - high * sign, panel - 1, z + side * high, x + low * sign, panel - 7, z + side * low, RAIL, "");
                MineProps.line(b, x + side * high, panel - 1, z - high * sign, x + side * low, panel - 7, z + low * sign, RAIL, "");
            }
        }
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            b.block(x + dx, top + 1, z + dz, Math.abs(dx) == 6 || Math.abs(dz) == 6 ? TIMBER : Material.SPRUCE_PLANKS, Math.abs(dz) == 6 ? "axis=x" : Math.abs(dx) == 6 ? "axis=z" : "");
            if (Math.abs(dx) == 6 || Math.abs(dz) == 6) b.block(x + dx, top + 2, z + dz, RAIL);
        }
        // Two sheave wheels, side by side, and the ropes going down from them.
        for (int side : new int[]{-3, 3}) {
            for (double angle = 0; angle < Math.TAU; angle += .05) {
                int dx = (int) Math.round(Math.cos(angle) * 3.4), dy = (int) Math.round(Math.sin(angle) * 3.4);
                b.block(x + dx, top + 6 + dy, z + side, Material.DARK_OAK_PLANKS);
            }
            for (int spoke = -3; spoke <= 3; spoke++) {
                b.block(x + spoke, top + 6, z + side, TIMBER, "axis=x");
                b.block(x, top + 6 + spoke, z + side, TIMBER, "axis=y");
            }
            for (int y = top + 2; y <= top + 5; y++) for (int leg : new int[]{-3, 3}) b.block(x + leg, y, z + side, Material.DARK_OAK_LOG, "axis=y");
            for (int y = foot + 12; y <= top; y++) b.block(x - 3, y, z + side, Material.IRON_CHAIN, "axis=y");
            MineProps.cart(b, x - 4, foot + 9, z + side - 1, Material.RAW_COPPER_BLOCK);
        }
    }

    /** A rail yard on a timber deck, over a dark drop with crystal glowing at the bottom of it. */
    private static void oreLine(BuildCanvas b, MineKit kit) {
        for (int x = YARD_X1; x <= YARD_X2; x++) for (int z = YARD_Z1; z <= YARD_Z2; z++) {
            boolean bed = false;
            for (int track : TRACKS_ALONG) bed |= Math.abs(z - track) <= 1;
            for (int track : TRACKS_ACROSS) bed |= Math.abs(x - track) <= 1;
            b.block(x, LINE, z, bed ? (noise(x, z, 515) < .5 ? Material.GRAVEL : Material.COBBLED_DEEPSLATE) : noise(x, z, 516) < .15 ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS);
            boolean rim = x == YARD_X1 || x == YARD_X2 || z == YARD_Z1 || z == YARD_Z2;
            if (rim) b.block(x, LINE, z, TIMBER, x == YARD_X1 || x == YARD_X2 ? "axis=z" : "axis=x");
            if (rim && !bed && !(z == YARD_Z1 && x >= 123 && x <= 125)) b.block(x, LINE + 1, z, RAIL);
        }
        for (int z : TRACKS_ALONG) straightRail(b, YARD_X1 + 1, z, 172, z, LINE + 1);
        for (int x : TRACKS_ACROSS) for (int z = 140; z <= 200; z++) if (b.get(x, LINE + 1, z) != Material.RAIL || Math.floorMod(z - 160, 5) != 0) b.block(x, LINE + 1, z, Material.RAIL, "shape=north_south");
        // The tracks run off the deck on narrow trestles, into their own holes in the rock.
        for (int z : TRACKS_ALONG) for (int x = YARD_X2 + 1; x <= 164; x++) {
            if (kit.open(x, LINE + 1, z) && CanopyKit.empty(b, x, LINE, z)) for (int dz = -1; dz <= 1; dz++) plank(b, x, LINE, z + dz);
            if (x % 6 == 0 && x < 162) MineProps.trestle(b, x, LINE, z, false, 31);
            for (int dz : new int[]{-1, 1}) if (kit.open(x, LINE + 1, z + dz) && x < 162) b.block(x, LINE + 1, z + dz, RAIL);
        }
        for (int x : TRACKS_ACROSS) for (int z = 140; z <= 200; z++) {
            if (z >= YARD_Z1 && z <= YARD_Z2) continue;
            for (int dx = -1; dx <= 1; dx++) if (kit.open(x + dx, LINE + 1, z)) plank(b, x + dx, LINE, z);
            if (z % 6 == 0) MineProps.trestle(b, x, LINE, z, true, 31);
            for (int dx : new int[]{-1, 1}) if (kit.open(x + dx, LINE + 1, z)) b.block(x + dx, LINE + 1, z, RAIL);
        }
        // Legs under the deck.
        for (int x = YARD_X1 + 3; x < YARD_X2; x += 9) for (int z = YARD_Z1 + 4; z < YARD_Z2; z += 11) MineProps.trestle(b, x, LINE, z, true, 31);
        // The north walkway out, and its gate.
        for (int z = 140; z < YARD_Z1; z++) for (int x = 122; x <= 126; x++) {
            if (!kit.open(x, LINE + 1, z)) continue;
            b.block(x, LINE, z, x == 122 || x == 126 ? TIMBER : Material.SPRUCE_PLANKS, x == 122 || x == 126 ? "axis=z" : "");
            if (x == 122 || x == 126) b.block(x, LINE + 1, z, RAIL);
        }
        for (int x : new int[]{122, 126}) for (int y = LINE + 1; y <= LINE + 5; y++) b.block(x, y, 153, TIMBER, "axis=y");
        for (int x = 122; x <= 126; x++) b.block(x, LINE + 5, 153, TIMBER, "axis=x");
        for (int x = 123; x <= 125; x++) for (int y = LINE + 1; y <= LINE + 4; y++) b.block(x, y, 153, Material.IRON_BARS, "east=true,west=true");
        // Lamps on posts, ore bins, and buffers.
        for (int x = YARD_X1 + 6; x < YARD_X2; x += 12) for (int z : new int[]{YARD_Z1, YARD_Z2}) {
            if (x >= 121 && x <= 127) continue;
            MineProps.lamp(b, x, LINE + 1, z, true);
        }
        headframe(b, 124, 190);
        // The yard is a long way from any wall, so its lamps stand out on the deck, and the fight can be seen.
        for (int x : new int[]{108, 124, 142}) MineProps.lamp(b, x, LINE + 1, 167, true);
        for (int x = YARD_X1 + 5; x < YARD_X2; x += 10) for (int z = YARD_Z1 + 5; z < YARD_Z2; z += 10) b.block(x, LINE + 7, z, Material.LIGHT, "level=15");
        // And a pair either side of the north gate, the way out of the yard.
        for (int x : new int[]{120, 128}) MineProps.lamp(b, x, LINE + 1, 157, true);
        for (int[] bin : new int[][]{{103, 157}, {145, 157}, {103, 183}, {145, 183}}) crates(b, bin[0], LINE + 1, bin[1]);
        for (int x = 104; x <= 146; x += 11) {
            spoil(b, x, LINE + 1, 162, Material.RAW_IRON_BLOCK);
            spoil(b, x + 3, LINE + 1, 177, Material.COAL_BLOCK);
        }
        // An old high line overhead, on stilts, long out of use.
        int high = LINE + 11;
        for (int x = 94; x <= 154; x++) for (int z : new int[]{150, 190}) {
            if (!kit.open(x, high + 1, z)) continue;
            for (int dz = -1; dz <= 1; dz++) plank(b, x, high, z + dz);
            b.block(x, high + 1, z, Material.RAIL, "shape=east_west");
            for (int dz : new int[]{-1, 1}) b.block(x, high + 1, z + dz, RAIL);
            // Its legs stand clear of the walkways and the tracks across the yard.
            if (x == 94 || x == 104 || x == 118 || x == 130 || x == 144 || x == 154) {
                MineProps.trestle(b, x, high, z, false, 31);
                CanopyKit.lantern(b, x, high - 1, z, 2);
            }
        }
        // Down in the dark, the crystal seam that paid for all of it.
        for (int[] burst : new int[][]{{100, 150, 12}, {150, 150, 10}, {98, 190, 14}, {152, 192, 11}, {124, 196, 13}, {92, 172, 9}, {156, 172, 12}, {116, 148, 9}}) {
            int y = 31;
            while (y < 44 && !kit.open(burst[0], y, burst[1])) y++;
            if (y < 44) MineProps.crystals(b, burst[0], y, burst[1], burst[2], MineProps.PINK, burst[0] * 3 + burst[1]);
        }
        for (int x = 92; x <= 156; x += 3) for (int z = 146; z <= 194; z += 3) {
            if (b.get(x, 30, z) != null && noise(x, z, 621) < .3) b.block(x, 30, z, Material.MAGMA_BLOCK);
        }
    }

    /**
     * The fortress across the back of the cavern: the hero of the whole map, sixty blocks of it stepping back in three
     * tiers to a keep and two great stacks, with the Foreman's iron mask over the gate. Nothing here is a flat wall: pilasters
     * stand proud of the face between bays, every arch is recessed behind a lighter ring, each tier ends in a deep stepped
     * cornice, and the stone runs light to dark, red with the heat where it meets the fire.
     */
    private static void fortress(BuildCanvas b, MineKit kit) {
        int[][] tiers = {{26, 48, 53}, {48, 68, 46}, {68, 86, 39}};
        for (int index = 0; index < tiers.length; index++) {
            int bottom = tiers[index][0], top = tiers[index][1], face = tiers[index][2], half = 36 - index * 9, back = index + 1 < tiers.length ? tiers[index + 1][2] : face - 12;
            // Its roof, back to the next tier, and its flanks.
            for (int x = FOUNDRY_X - half; x <= FOUNDRY_X + half; x++) for (int z = back; z < face; z++) {
                b.block(x, top, z, Math.floorMod(x + z, 7) == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_TILES);
                for (int y = top - 1; y >= top - 2; y--) b.block(x, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
                boolean flank = Math.abs(x - FOUNDRY_X) == half;
                if (flank) for (int y = bottom; y < top; y++) b.block(x, y, z, Math.floorMod(z, 6) == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : fortStone(x, y, z, bottom, top));
            }
            for (int x = FOUNDRY_X - half; x <= FOUNDRY_X + half; x++) {
                int bay = Math.floorMod(x - FOUNDRY_X + 4, 8) - 4;
                boolean pilaster = Math.abs(bay) == 4 || Math.abs(bay) == 3 && bay == -3;
                for (int y = bottom; y <= top; y++) {
                    double arch = Math.sqrt(Math.max(0, 7.5 - bay * bay));
                    int spring = top - 7;
                    double above = y - (spring + arch);
                    boolean gate = index == 0 && Math.abs(x - FOUNDRY_X) <= 4 || index == 1 && Math.abs(x - FOUNDRY_X) <= 12;
                    boolean opening = !pilaster && !gate && y > bottom + 3 && Math.abs(bay) <= 2 && above <= 0;
                    boolean ring = !pilaster && !gate && y > bottom + 3 && Math.abs(bay) <= 3 && above > 0 && above <= 1.3;
                    if (opening) {
                        b.block(x, y, face, Material.AIR);
                        b.block(x, y, face - 1, Material.IRON_BARS, "east=true,west=true,north=false,south=false");
                        b.block(x, y, face - 2, y % 3 == 0 ? Material.SHROOMLIGHT : Material.ORANGE_STAINED_GLASS);
                        b.block(x, y, face - 3, Material.POLISHED_BLACKSTONE_BRICKS);
                        continue;
                    }
                    b.block(x, y, face, ring ? Material.POLISHED_DEEPSLATE : fortStone(x, y, face, bottom, top));
                    b.block(x, y, face - 1, fortStone(x, y, face - 1, bottom, top));
                    if (pilaster) b.block(x, y, face + 1, fortStone(x, y, face + 1, bottom, top + 2));
                }
                // A plinth at the foot and a stepped cornice at the head of each tier.
                b.block(x, bottom + 3, face + 1, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, "facing=north,half=bottom,shape=straight");
                for (int y = bottom; y < bottom + 3; y++) b.block(x, y, face + 1, Material.POLISHED_BLACKSTONE_BRICKS);
                b.block(x, top - 1, face + 1, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, "facing=north,half=top,shape=straight");
                b.block(x, top, face + 1, Material.CHISELED_POLISHED_BLACKSTONE);
                b.block(x, top, face + 2, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, "facing=north,half=top,shape=straight");
                b.block(x, top + 1, face + 2, Material.POLISHED_BLACKSTONE_BRICKS);
                b.block(x, top + 1, face + 3, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, "facing=north,half=top,shape=straight");
                // Merlons with a spike on every other one.
                if (Math.floorMod(x, 4) == 0) {
                    for (int y = top + 2; y <= top + 3; y++) b.block(x, y, face + 2, Material.POLISHED_BLACKSTONE_BRICKS);
                    if (Math.floorMod(x, 8) == 0) for (int y = top + 4; y <= top + 5; y++) b.block(x, y, face + 2, Material.IRON_BARS);
                } else {
                    b.block(x, top + 2, face + 2, Material.POLISHED_BLACKSTONE_BRICK_SLAB, "type=bottom");
                }
            }
        }
        keep(b);
        mask(b);
        // The great gate at the head of the causeway, stepped back in three orders.
        for (int order = 0; order <= 2; order++) for (int x = FOUNDRY_X - 7 + order; x <= FOUNDRY_X + 7 - order; x++) for (int y = FORGE + 1; y <= FORGE + 14 - order; y++) {
            int off = Math.abs(x - FOUNDRY_X), z = 54 - order;
            double arch = 4.6 - order * .8;
            boolean way = off < arch && y <= FORGE + 6 + Math.sqrt(Math.max(0, arch * arch - off * off));
            b.block(x, y, z, way ? Material.AIR : order == 0 ? Material.CHISELED_POLISHED_BLACKSTONE : fortStone(x, y, z, FORGE, FORGE + 14));
        }
        for (int x = FOUNDRY_X - 2; x <= FOUNDRY_X + 2; x++) for (int y = FORGE + 1; y <= FORGE + 8; y++) for (int z = 46; z <= 51; z++) b.block(x, y, z, Material.AIR);
        // Fire pours down the channels from the top tier into the sea, stepping out at each cornice.
        for (int offset : new int[]{-23, 23, -15, 15}) {
            int x = FOUNDRY_X + offset, top = Math.abs(offset) > 16 ? 68 : 86;
            for (int y = top; y >= LAVA; y--) {
                int z = y > 68 ? 43 : y > 48 ? 50 : 57;
                for (int dx = 0; dx <= 1; dx++) {
                    b.block(x + dx, y, z, Material.LAVA, y == top ? "level=0" : "level=8");
                    if (y == 68 || y == 48) for (int run = z; run <= z + 7; run++) b.block(x + dx, y, run, Material.LAVA, "level=8");
                }
                for (int side : new int[]{-1, 2}) b.block(x + side, y, z, y % 2 == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.CHISELED_POLISHED_BLACKSTONE);
            }
        }
        // Round towers at the ends of the wall, with lit slits, a corbelled head, and a tall iron cap.
        for (int side : new int[]{-1, 1}) tower(b, FOUNDRY_X + side * 40, 56, 24, 78, 6);
        for (int side : new int[]{-1, 1}) tower(b, FOUNDRY_X + side * 28, 46, 48, 84, 4);
    }

    /** A round tower: a battered foot, lit slits in rings, a head corbelled out on a stepped ring, and a spike. */
    private static void tower(BuildCanvas b, int x, int z, int foot, int top, int radius) {
        for (int y = foot; y <= top; y++) for (int dx = -radius - 3; dx <= radius + 3; dx++) for (int dz = -radius - 3; dz <= radius + 3; dz++) {
            double from = Math.hypot(dx, dz), wide = y > top - 6 ? radius + 1.3 : y > top - 8 ? radius + .7 : radius + .3 + Math.max(0, foot + 6 - y) * .3;
            if (from > wide) continue;
            boolean shell = from > wide - 1.6;
            if (!shell) continue;
            boolean slit = y % 9 == 4 && (dx == 0 || dz == 0);
            b.block(x + dx, y, z + dz, Math.floorMod(y, 9) == 0 ? Material.CHISELED_POLISHED_BLACKSTONE : slit ? Material.SHROOMLIGHT : y == top - 7 ? Material.POLISHED_BLACKSTONE_BRICKS : fortStone(x + dx, y, z + dz, foot, top));
        }
        for (int up = 0; up <= radius * 3; up++) {
            double across = (radius + 1.3) * Math.pow(1 - up / (radius * 3D + 1), 1.4);
            int reach = (int) Math.ceil(across);
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++)
                if (Math.hypot(dx, dz) <= across) b.block(x + dx, top + 1 + up, z + dz, up % 4 == 0 ? Material.WAXED_EXPOSED_CUT_COPPER : Material.POLISHED_BLACKSTONE);
        }
        b.block(x, top + 2 + radius * 3, z, Material.GILDED_BLACKSTONE);
    }

    /** The keep on the top tier and the two great stacks either side of it, smoking. */
    private static void keep(BuildCanvas b) {
        for (int x = FOUNDRY_X - 8; x <= FOUNDRY_X + 8; x++) for (int z = 26; z <= 36; z++) for (int y = 86; y <= 98; y++) {
            boolean wall = Math.abs(x - FOUNDRY_X) == 8 || z == 36 || z == 26;
            if (!wall && y < 98) continue;
            int bay = Math.floorMod(x - FOUNDRY_X + 2, 4) - 2;
            boolean window = z == 36 && Math.abs(bay) <= 1 && bay != -2 && y >= 89 && y <= 94 - Math.abs(bay) && Math.abs(x - FOUNDRY_X) < 8;
            b.block(x, y, z, window ? Material.SHROOMLIGHT : Math.abs(bay) == 2 ? Material.POLISHED_BLACKSTONE_BRICKS : y == 98 ? Material.CHISELED_POLISHED_BLACKSTONE : fortStone(x, y, z, 80, 98));
        }
        for (int x = FOUNDRY_X - 10; x <= FOUNDRY_X + 10; x++) {
            b.block(x, 98, 37, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, "facing=north,half=top,shape=straight");
            if (Math.floorMod(x, 2) == 0) for (int y = 99; y <= 100; y++) b.block(x, y, 37, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int side : new int[]{-1, 1}) {
            int x = FOUNDRY_X + side * 14, z = 32;
            for (int y = 84; y <= 100; y++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                double from = Math.hypot(dx, dz), wide = y >= 98 ? 4.3 : y == 97 ? 3.8 : 3.3;
                if (from > wide) continue;
                b.block(x + dx, y, z + dz, from > wide - 1.4 ? (y % 4 == 0 ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS) : y >= 100 ? Material.MAGMA_BLOCK : Material.AIR);
            }
            // A bale under the fire sends the smoke up three times as far.
            b.block(x, 99, z, Material.HAY_BLOCK, "axis=y");
            b.block(x, 100, z, Material.CAMPFIRE, "lit=true,signal_fire=true,facing=north");
        }
    }

    /** The Foreman's iron mask over the gate, twenty-five blocks across: domed brow, slit eyes with fire behind them, a gilded nose guard and a barred mouth. */
    private static void mask(BuildCanvas b) {
        int cx = FOUNDRY_X, cy = 58, plane = 47, wide = 12, tall = 10;
        for (int dx = -wide; dx <= wide; dx++) for (int dy = -tall; dy <= tall; dy++) {
            double e = Math.pow(dx / (double) wide, 2) + Math.pow(dy / (double) tall, 2);
            if (e > 1) continue;
            int depth = (int) Math.round(6 * Math.sqrt(1 - e));
            boolean eye = dy >= 1 && dy <= 2 && Math.abs(dx) >= 2 && Math.abs(dx) <= 8;
            boolean mouth = dy <= -4 && dy >= -8 && Math.abs(dx) <= 6;
            boolean nose = Math.abs(dx) <= 1 && dy <= 5 && dy >= -3;
            boolean brow = dy == 3 && Math.abs(dx) <= 9;
            for (int z = plane; z <= plane + depth; z++) {
                boolean face = z == plane + depth;
                double value = .45 + dy / (double) tall * .3 + (depth / 6D - .5) * .6;
                Material plate = value > .62 ? Material.POLISHED_DEEPSLATE : value > .36 ? Material.POLISHED_BLACKSTONE : Material.BLACKSTONE;
                b.block(cx + dx, cy + dy, z, face && eye ? Material.AIR : face && mouth && Math.floorMod(dx, 2) == 0 ? Material.IRON_BARS : face && brow ? Material.GILDED_BLACKSTONE : plate);
                if (face && eye) b.block(cx + dx, cy + dy, z - 1, Material.SHROOMLIGHT);
                if (face && mouth) b.block(cx + dx, cy + dy, z - 1, Material.MAGMA_BLOCK);
                if (face && nose) b.block(cx + dx, cy + dy, z + 1, Material.GILDED_BLACKSTONE);
            }
        }
        for (int side : new int[]{-1, 1}) for (int step = 0; step <= 16; step++) {
            int x = cx + side * (wide - 1 + step), y = cy + 2 + (int) Math.round(Math.pow(step / 16D, 2.2) * 12), reach = Math.max(0, 2 - step / 6);
            for (int dy = -reach; dy <= reach; dy++) for (int dz = -reach; dz <= reach; dz++)
                if (Math.abs(dy) + Math.abs(dz) <= reach + 1) b.block(x, y + dy, plane + 2 + dz, step > 13 ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE);
        }
    }

    /** Fortress stone in order of value, darkest low down, red with heat where it meets the fire, with big soft patches of wear. */
    private static Material fortStone(int x, int y, int z, int bottom, int top) {
        double wear = (smooth(x + y, z, 8, 622) - .5) * 1.6;
        if (y + wear * 6 < 33) return Material.RED_NETHER_BRICKS;
        if (y + wear * 6 < 38) return Material.NETHER_BRICKS;
        double value = (y - bottom) / (double) Math.max(1, top - bottom) * .7 + wear + .12;
        return FORT[Math.clamp((int) Math.round(value * (FORT.length - 1)), 0, FORT.length - 1)];
    }

    /** A stone causeway over the fire, with a parapet. */
    private static void causeway(BuildCanvas b, int x1, int z1, int x2, int z2, int half) {
        boolean alongZ = x1 == x2;
        for (int run = alongZ ? Math.min(z1, z2) : Math.min(x1, x2); run <= (alongZ ? Math.max(z1, z2) : Math.max(x1, x2)); run++) for (int across = -half - 1; across <= half + 1; across++) {
            int x = alongZ ? x1 + across : run, z = alongZ ? run : z1 + across;
            boolean parapet = Math.abs(across) == half + 1;
            b.block(x, FORGE, z, parapet ? Material.POLISHED_BLACKSTONE_BRICKS : noise(x, z, 625) < .3 ? Material.POLISHED_BLACKSTONE : Material.DEEPSLATE_TILES);
            if (parapet) b.block(x, FORGE + 1, z, Math.floorMod(run, 2) == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE_BRICK_SLAB, Math.floorMod(run, 2) == 0 ? "" : "type=bottom");
            for (int y = FORGE - 1; y >= FORGE - 2; y--) b.block(x, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int run = alongZ ? Math.min(z1, z2) + 4 : Math.min(x1, x2) + 4; run < (alongZ ? Math.max(z1, z2) : Math.max(x1, x2)); run += 15) {
            for (int side : new int[]{-1, 1}) {
                int x = alongZ ? x1 + side * (half + 4) : run, z = alongZ ? run : z1 + side * (half + 4);
                for (int y = 19; y < FORGE; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.block(x + dx, y, z + dz, pick(FORT, x + dx + y, z + dz, 626));
                MineProps.spire(b, x, FORGE + 1, z, 12, Material.POLISHED_BLACKSTONE_BRICKS, Material.CHISELED_POLISHED_BLACKSTONE, Material.SHROOMLIGHT);
            }
        }
    }

    /** The sea of fire: the Round Foundry on its rock in the middle, causeways to it, and the fortress across the back of the cavern. */
    private static void foundry(BuildCanvas b, MineKit kit) {
        // The rock it all stands on.
        for (int x = FOUNDRY_X - FOUNDRY_RADIUS - 2; x <= FOUNDRY_X + FOUNDRY_RADIUS + 2; x++) for (int z = FOUNDRY_Z - FOUNDRY_RADIUS - 2; z <= FOUNDRY_Z + FOUNDRY_RADIUS + 2; z++) {
            double from = Math.hypot(x - FOUNDRY_X, z - FOUNDRY_Z), edge = FOUNDRY_RADIUS + 2 + (noise(x, z, 627) - .5) * 1.5;
            if (from > edge) continue;
            for (int y = 19; y < FORGE; y++) b.block(x, y, z, pick(HOT_ROCK, x + y, z, 628));
        }
        for (int x = FOUNDRY_X - FOUNDRY_RADIUS; x <= FOUNDRY_X + FOUNDRY_RADIUS; x++) for (int z = FOUNDRY_Z - FOUNDRY_RADIUS; z <= FOUNDRY_Z + FOUNDRY_RADIUS; z++) {
            double from = Math.hypot(x - FOUNDRY_X, z - FOUNDRY_Z);
            if (from > FOUNDRY_RADIUS + .3) continue;
            boolean diagonal = Math.abs(Math.abs(x - FOUNDRY_X) - Math.abs(z - FOUNDRY_Z)) <= 1 && from > 7;
            boolean ring = Math.abs(from - 13) < .7;
            b.block(x, FORGE, z, diagonal ? Material.GILDED_BLACKSTONE : ring ? Material.POLISHED_BLACKSTONE_BRICKS : noise(x, z, 517) < .3 ? Material.POLISHED_BLACKSTONE : Material.BLACKSTONE);
            if (from <= 6.4) {
                b.block(x, FORGE + 1, z, from > 5 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.CHISELED_POLISHED_BLACKSTONE);
                if (from <= 4.2) b.block(x, FORGE + 2, z, from > 3 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.LAVA, from > 3 ? "" : "level=0");
                if (from <= 3) b.block(x, FORGE + 1, z, Material.MAGMA_BLOCK);
            }
            boolean rim = from > FOUNDRY_RADIUS - .7;
            boolean gap = Math.abs(x - FOUNDRY_X) <= 2 || Math.abs(z - FOUNDRY_Z) <= 2 && x > FOUNDRY_X;
            if (rim && !gap) b.block(x, FORGE + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
            if (rim && !gap && Math.floorMod(x + z, 2) == 0) b.block(x, FORGE + 2, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
        }
        // The crucible's hood, hung from the roof on four great chains.
        for (double angle = 0; angle < Math.TAU; angle += .02) for (int r = 3; r <= 5; r++)
            b.block(FOUNDRY_X + (int) Math.round(Math.cos(angle) * r), FORGE + 14, FOUNDRY_Z + (int) Math.round(Math.sin(angle) * r), Material.POLISHED_BLACKSTONE_BRICKS);
        for (int[] corner : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) for (int y = FORGE + 15; y <= 92; y++) if (kit.open(FOUNDRY_X + corner[0], y, FOUNDRY_Z + corner[1])) b.block(FOUNDRY_X + corner[0], y, FOUNDRY_Z + corner[1], Material.IRON_CHAIN, "axis=y");
        for (double angle = Math.PI / 8; angle < Math.TAU; angle += Math.PI / 4) {
            int x = FOUNDRY_X + (int) Math.round(Math.cos(angle) * 16), z = FOUNDRY_Z + (int) Math.round(Math.sin(angle) * 16);
            for (int y = FORGE + 1; y <= FORGE + 5; y++) b.block(x, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
            b.block(x, FORGE + 6, z, Material.MAGMA_BLOCK);
            b.block(x, FORGE + 7, z, Material.FIRE);
        }
        // The dampers, a lever on each post, and the fire the party rests at inside the south gap.
        for (int[] damper : new int[][]{{108, 82}, {136, 82}, {122, 74}}) {
            b.block(damper[0], FORGE + 1, damper[1], Material.POLISHED_BLACKSTONE_BRICKS);
            b.block(damper[0], FORGE + 2, damper[1], Material.CHISELED_POLISHED_BLACKSTONE);
            b.block(damper[0], FORGE + 3, damper[1], Material.LEVER, "face=floor,facing=south,powered=false");
        }
        b.block(116, FORGE + 1, 101, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        b.block(115, FORGE + 1, 101, TIMBER, "axis=z");
        b.block(127, FORGE + 1, 101, Material.ANVIL, "facing=north");
        causeway(b, FOUNDRY_X, 105, FOUNDRY_X, 124, 2);
        causeway(b, FOUNDRY_X, 47, FOUNDRY_X, 71, 2);
        causeway(b, 139, FOUNDRY_Z, 166, FOUNDRY_Z, 2);
        // The landing where the incline comes out onto the south causeway.
        for (int x = 112; x <= 119; x++) for (int z = 121; z <= 128; z++) {
            if (!kit.open(x, FORGE + 3, z) && !kit.open(x, FORGE + 2, z)) continue;
            int floor = x <= 116 ? FORGE + 2 : x <= 118 ? FORGE + 1 : FORGE;
            for (int y = FORGE + 3; y > floor; y--) b.block(x, y, z, Material.AIR);
            b.block(x, floor, z, Material.DEEPSLATE_TILES);
            for (int y = floor - 1; y >= floor - 2; y--) b.block(x, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int x = 120; x <= 124; x++) for (int y = FORGE + 5; y <= FORGE + 7; y++) b.block(x, y, 67, Material.POLISHED_BLACKSTONE_BRICKS);
        for (int y = FORGE + 1; y <= FORGE + 7; y++) for (int x : new int[]{120, 124}) b.block(x, y, 67, Material.CHISELED_POLISHED_BLACKSTONE);
        for (int x = 121; x <= 123; x++) for (int y = FORGE + 1; y <= FORGE + 4; y++) b.block(x, y, 67, Material.IRON_BARS, "east=true,west=true");
        fortress(b, kit);
        // The rest of the cavern: spires of black rock, hanging stone, and fire from the roof.
        for (int[] column : new int[][]{{92, 92, 8}, {152, 94, 7}, {98, 112, 6}, {146, 114, 6}}) MineProps.column(b, column[0], 20, column[1], roof(kit, column[0], 40, column[1], 110), column[2], SLAG_ROCK, column[0] + column[1]);
        for (int[] spire : new int[][]{{88, 74, 24}, {158, 76, 28}, {100, 62, 18}, {146, 60, 20}, {104, 116, 16}, {142, 118, 14}}) {
            for (int up = 0; up < spire[2]; up++) {
                double across = 4.5 * Math.pow(1 - up / (double) spire[2], .7) + .4;
                int reach = (int) Math.ceil(across);
                for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++)
                    if (Math.hypot(dx, dz) <= across + (noise(spire[0] + dx, spire[1] + dz + up / 3, 629) - .5) * 1.2) b.block(spire[0] + dx, 20 + up, spire[1] + dz, up % 5 == 2 && noise(dx, dz, up) < .4 ? Material.MAGMA_BLOCK : pick(HOT_ROCK, spire[0] + dx + up, spire[1] + dz, 630));
            }
        }
        for (int index = 0; index < 34; index++) {
            int x = 84 + (int) (noise(index, 11, 631) * 78), z = 62 + (int) (noise(11, index, 632) * 54), y = roof(kit, x, 60, z, 106);
            if (y >= 106 || Math.hypot(x - FOUNDRY_X, z - FOUNDRY_Z) < 9) continue;
            MineProps.curtain(b, x, y, z, 10 + (int) (noise(index, index, 633) * 16), 3 + (int) (noise(z, x, 634) * 4), index % 2 == 0, HOT_ROCK, null, 635 + index);
        }
        for (int[] fall : new int[][]{{96, 84, 2}, {150, 72, 2}, {148, 106, 1}, {100, 108, 1}, {156, 92, 1}}) {
            int y = roof(kit, fall[0], 50, fall[1], 106);
            MineProps.lavafall(b, fall[0], y - 1, fall[1], LAVA + 1, fall[2]);
        }
        // Off the south causeway the slag boils of its own accord, in a broken ring of black rock nobody built.
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            double from = Math.hypot(dx, dz) + (noise(dx + 40, dz, 651) - .5) * 1.4;
            if (from < 5 || from > 6.6 || noise(dx, dz + 40, 652) < .2) continue;
            for (int y = LAVA - 3; y <= LAVA + (noise(dx, dz, 653) < .45 ? 1 : 0); y++) b.block(BOIL_X + dx, y, BOIL_Z + dz, y > LAVA - 1 && noise(dz, dx, 654) < .15 ? Material.CRYING_OBSIDIAN : Material.BASALT, y > LAVA - 1 && noise(dz, dx, 654) < .15 ? "" : "axis=y");
        }
        kit.flood(b, Material.LAVA, 80, 44, 166, 124, 19, LAVA);
    }

    /** Pools of slag between the walkways, off the east causeway. */
    private static void slagPits(BuildCanvas b) {
        for (int x = 164; x <= 200; x++) for (int z = 97; z <= 127; z++) {
            double roll = smooth(x, z, 6, 518);
            if (b.get(x, FORGE - 2, z) == null) continue;
            if (roll < .3 && Math.hypot(x - 182, z - 112) > 5) b.block(x, FORGE - 2, z, Material.MAGMA_BLOCK);
            else if (roll > .78) b.block(x, FORGE - 2, z, Material.OBSIDIAN);
        }
        for (int[] pillar : new int[][]{{174, 105}, {190, 105}, {174, 119}, {190, 119}})
            for (int y = FORGE - 1; y <= FORGE + 6; y++) b.block(pillar[0], y, pillar[1], y % 3 == 0 ? TIMBER : Material.COBBLED_DEEPSLATE, y % 3 == 0 ? "axis=y" : "");
    }

    /** The bottom of everything: black water, the last dock, and the Gloomforge cut into the far cliff. */
    private static void drownedDeep(BuildCanvas b, MineKit kit) {
        kit.shore(b, 176, 34, 11, 17, 2, DOCK, 519);
        kit.shore(b, 198, 6, 9, 7, 2, 11, 520);
        kit.shore(b, 198, 74, 8, 8, 2, 11, 521);
        // The dock.
        for (int z = 26; z <= 42; z++) for (int x = 172; x <= 187; x++) plank(b, x, DOCK, z);
        for (int z = 26; z <= 42; z += 16) for (int x = 172; x <= 187; x++) b.block(x, DOCK + 1, z, RAIL);
        for (int z = 26; z <= 42; z++) if (z < 33 || z > 35) b.block(187, DOCK + 1, z, RAIL);
        b.block(178, DOCK + 1, 38, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        crates(b, 174, DOCK + 1, 28);
        crates(b, 183, DOCK + 1, 40);
        GloomforgePalace.build(b, kit);
        MineProps.hoard(b, GloomforgePalace.END - 6, DECK + 1, GloomforgePalace.AXIS - 9, 3, 650);
        MineProps.hoard(b, GloomforgePalace.END - 6, DECK + 1, GloomforgePalace.AXIS + 9, 3, 655);
        // Blue crystal on every shore, and in the water itself.
        for (int[] burst : new int[][]{{198, 6, 11, 18}, {198, 74, 11, 16}, {196, 10, 8, 10}, {238, -2, 7, 22}, {232, 74, 7, 18}, {218, 0, 5, 14}, {214, 72, 6, 12}}) MineProps.crystals(b, burst[0], burst[2], burst[1], burst[3], MineProps.BLUE, burst[0] + burst[1]);
        kit.flood(b, Material.WATER, 170, -12, 300, 136, 2, WATER);
        for (int x = 182; x <= 244; x += 2) for (int z = -8; z <= 90; z += 2) {
            if (noise(x, z, 636) < .06 && b.get(x, 1, z) != null) b.block(x, 1, z, Material.SEA_LANTERN);
            if (noise(z, x, 637) < .05 && b.get(x, 2, z) == Material.WATER) for (int y = 2; y < 6 + noise(x, z, 638) * 5; y++) b.block(x, y, z, Material.KELP_PLANT);
        }
        // The roof comes down in great blue-tipped curtains, and water falls in threads.
        for (int index = 0; index < 46; index++) {
            int x = 176 + (int) (noise(index, 7, 524) * 52), z = -6 + (int) (noise(7, index, 525) * 92), y = 40;
            // Nothing hangs on the line of the causeway: the view of the Gloomforge is the point of this cave.
            if (Math.abs(z - GloomforgePalace.AXIS) < 16 || !kit.open(x, y, z)) continue;
            while (y < 62 && kit.open(x, y, z)) y++;
            if (y < 62) MineProps.curtain(b, x, y, z, 10 + (int) (noise(index, index, 526) * 18), 3 + (int) (noise(z, x, 527) * 4), index % 2 == 0, WET_ROCK, Material.SEA_LANTERN, 640 + index);
        }
        for (int[] column : new int[][]{{198, 60, 7}, {198, 12, 6}, {226, 72, 7}, {226, -4, 7}}) MineProps.column(b, column[0], 2, column[1], roof(kit, column[0], 30, column[1], 62), column[2], ICE_ROCK, column[0] + column[1]);
        for (int[] fall : new int[][]{{190, 12, 3}, {214, 78, 2}, {232, -2, 2}}) {
            int y = 34;
            while (y < 62 && kit.open(fall[0], y, fall[1])) y++;
            MineProps.waterfall(b, fall[0], y - 1, fall[1], WATER + 1, fall[2]);
        }
    }
}
