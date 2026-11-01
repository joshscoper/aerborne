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
    private static final int YARD_X1 = 99, YARD_X2 = 149, YARD_Z1 = 155, YARD_Z2 = 185;
    private static final int[] TRACKS_ALONG = {160, 165, 170, 175, 180}, TRACKS_ACROSS = {112, 136};
    private static final int FOUNDRY_X = 122, FOUNDRY_Z = 88, FOUNDRY_RADIUS = 18, LAVA = 27;
    private static final Material RAIL = CanopyKit.RAIL, TIMBER = Material.STRIPPED_SPRUCE_LOG;
    private static final Material[] CRATES = {Material.SPRUCE_PLANKS, Material.STRIPPED_SPRUCE_WOOD, Material.BARREL};
    private static final Material[] DARK_ROCK = {Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF, Material.DEEPSLATE};
    private static final Material[] HOT_ROCK = {Material.BLACKSTONE, Material.BASALT, Material.SMOOTH_BASALT, Material.BLACKSTONE};
    private static final Material[] WET_ROCK = {Material.DEEPSLATE, Material.TUFF, Material.DARK_PRISMARINE, Material.DEEPSLATE};
    private static final Material[] FORT = {Material.POLISHED_BLACKSTONE_BRICKS, Material.DEEPSLATE_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS};

    private MinesTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        MineKit kit = new MineKit();
        dig(kit);
        kit.close(b);
        surface(b, kit);
        pithead(b);
        gullet(b, kit);
        crystalHollow(b);
        mainline(b);
        oreLine(b, kit);
        foundry(b, kit);
        slagPits(b);
        drownedDeep(b, kit);
        SampleChests.place(b, "gloomforge_mines");
        CanopyKit.joinRails(b);
        return b.freeze();
    }

    /** Every passage, level by level. */
    private static void dig(MineKit kit) {
        // The valley and the mine's mouth.
        kit.cavern(14, PIT, 0, 26, 16, 20, 20, 21);
        kit.openToSky(14, 0, 62, PIT + 4);
        kit.hall(10, 15, 18, 19, PIT, 5);
        kit.hall(12, 19, 16, 23, PIT, 5);
        kit.tunnel(7, 6, 1, new int[]{14, 96, 24}, new int[]{12, 96, 30}, new int[]{22, 95, 38});
        kit.cavern(24, 95, 47, 13, 10, 8, 3, 2);
        kit.tunnel(6, 6, 3, new int[]{33, 95, 44}, new int[]{48, 94, 40}, new int[]{60, 93, 33});
        kit.cavern(65, 93, 31, 7, 6, 5, 3, 4);
        kit.tunnel(7, 6, 5, new int[]{23, 95, 55}, new int[]{26, 93, 60}, new int[]{29, 92, 64});
        // The Gullet: three terraces, and nothing above them but sky.
        for (int x = GULLET_X - 48; x <= GULLET_X + 48; x++) for (int z = GULLET_Z - 48; z <= GULLET_Z + 48; z++) {
            double from = Math.hypot(x - GULLET_X, z - GULLET_Z), angle = Math.atan2(z - GULLET_Z, x - GULLET_X);
            for (int y = DEEP + 1; y <= 122; y++) {
                double wall = y <= RING_LOW ? 17 : y <= RING_HIGH ? 23 : y <= 108 ? 31 : 31 + (y - 108) * .9;
                wall += (smooth((int) Math.round(angle * 40), y / 3, 3, 601) - .5) * 5 + Math.sin(angle * 11) * 1.2;
                if (from < wall) kit.carve(x, y, z);
            }
        }
        kit.openToSky(GULLET_X, GULLET_Z, 50, 116);
        // Crystal Hollow, where Grell stands.
        kit.tunnel(6, 6, 6, new int[]{36, 70, 104}, new int[]{42, 70, 112}, new int[]{49, 70, 116});
        kit.door(49, DEEP, 117, false, 5);
        kit.cavern(50, DEEP, 134, 19, 12, 20, 7, 7);
        kit.door(50, DEEP, 146, false, 7);
        kit.tunnel(6, 6, 8, new int[]{50, 70, 153}, new int[]{42, 66, 160}, new int[]{38, 61, 167}, new int[]{48, 56, 175}, new int[]{60, 52, 170});
        // The Mainline, with refuges in its walls, out onto the trestles of the Ore Line.
        kit.hall(60, 169, 88, 171, LINE, 5);
        for (int x = 65; x <= 83; x += 7) {
            kit.hall(x, 166, x + 2, 168, LINE, 4);
            kit.hall(x + 3, 172, x + 5, 174, LINE, 4);
        }
        kit.cavern(124, 30, 170, 36, 25, 46, 30, 14);
        for (int z : TRACKS_ALONG) kit.hall(162, z - 1, 176, z + 1, LINE, 4);
        for (int x : TRACKS_ACROSS) {
            kit.hall(x - 1, 134, x + 1, 150, LINE, 4);
            kit.hall(x - 1, 190, x + 1, 206, LINE, 4);
        }
        kit.tunnel(6, 6, 9, new int[]{124, 52, 146}, new int[]{122, 47, 138}, new int[]{114, 42, 134}, new int[]{108, 38, 128}, new int[]{114, 35, 124}, new int[]{122, 32, 124});
        // The sea of fire, and the Slag Pits off its east causeway.
        kit.cavern(FOUNDRY_X, 18, 84, 42, 36, 46, 26, 15);
        kit.tunnel(6, 6, 10, new int[]{164, 32, 88}, new int[]{172, 31, 98}, new int[]{176, 30, 104});
        kit.cavern(182, 30, 112, 16, 13, 11, 4, 11);
        kit.tunnel(7, 7, 12, new int[]{122, 32, 47}, new int[]{126, 28, 40}, new int[]{136, 23, 38}, new int[]{150, 18, 36}, new int[]{166, 14, 34});
        // The Drowned Deep.
        kit.door(167, DOCK, 34, true, 5);
        kit.cavern(234, 1, 40, 62, 46, 58, 30, 13);
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
        double valley = Math.hypot((x - 14) / 26D, z / 16D);
        if (valley < 1) return PIT;
        double climb = Math.min(24, (valley - 1) * 34 + (smooth(x, z, 13, 602) - .3) * 14 + smooth(x, z, 5, 603) * 3);
        return PIT + Math.max(1, (int) Math.round(climb));
    }

    /** The hills: grass over dirt in cut terraces, ringing the valley and running back over the mine to the rim of the pit. */
    private static void surface(BuildCanvas b, MineKit kit) {
        for (int x = -46; x <= 92; x++) for (int z = -44; z <= 146; z++) {
            boolean nearValley = Math.hypot((x - 14) / 26D, z / 16D) < 2, nearPit = Math.hypot(x - GULLET_X, z - GULLET_Z) < 58;
            if (!nearValley && !nearPit) continue;
            int top = hillHeight(x, z);
            // Where the pit or the valley has eaten the hill, the turf runs down to the lip.
            int lip = nearValley && !nearPit ? PIT : 104;
            while (top > lip && kit.open(x, top, z)) top--;
            if (kit.open(x, top, z) || top <= PIT) continue;
            int lowest = top;
            for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) lowest = Math.min(lowest, hillHeight(x + side[0], z + side[1]));
            b.block(x, top, z, Material.GRASS_BLOCK, "snowy=false");
            for (int y = top - 1; y >= Math.min(lowest, top - 4); y--) if (!kit.open(x, y, z)) b.block(x, y, z, top - y <= 2 ? Material.DIRT : smooth(x + y, z, 4, 615) < .5 ? Material.STONE : Material.ANDESITE);
            double roll = noise(x, z, 604);
            if (roll < .1) b.block(x, top + 1, z, Material.SHORT_GRASS);
            else if (roll < .13) b.block(x, top + 1, z, Material.FERN);
            else if (roll > .994) tree(b, x, top + 1, z);
        }
    }

    private static void tree(BuildCanvas b, int x, int y, int z) {
        for (int up = 0; up < 8; up++) b.block(x, y + up, z, Material.SPRUCE_LOG, "axis=y");
        for (int layer = 0; layer < 7; layer++) {
            int reach = Math.max(1, 4 - layer / 2);
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++)
                if (Math.abs(dx) + Math.abs(dz) <= reach + 1 && (dx != 0 || dz != 0 || layer > 4)) b.block(x + dx, y + 3 + layer, z + dz, Material.SPRUCE_LEAVES, "persistent=true");
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
            b.block(x, PIT, z, noise(x * 3, z, 608) < .3 ? Material.ANDESITE : noise(x, z * 3, 609) < .4 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
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
    }

    /** A timber fence along the inner edge of a terrace, wherever the floor drops away. */
    private static void terraceRail(BuildCanvas b, MineKit kit, int y, double from, double to, double gapAt) {
        for (double angle = 0; angle < Math.TAU; angle += .012) {
            double off = Math.abs(Math.IEEEremainder(angle - gapAt, Math.TAU));
            if (off < .28) continue;
            for (double r = from; r <= to; r += .4) {
                int x = GULLET_X + (int) Math.round(Math.cos(angle) * r), z = GULLET_Z + (int) Math.round(Math.sin(angle) * r);
                if (kit.open(x, y, z) || !kit.open(x, y + 1, z)) continue;
                if (CanopyKit.empty(b, x, y + 1, z)) b.block(x, y + 1, z, RAIL);
                break;
            }
        }
    }

    /** A timber stair sweeping down round the wall from one terrace to the next. */
    private static void ramp(BuildCanvas b, double start, double span, double radius, int top, int bottom) {
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
            if (step % 110 == 0) for (int under = 1; under <= height - bottom + 1; under++) b.block(edgeX, whole - under, edgeZ, TIMBER, "axis=y");
        }
    }

    /** The great open pit: terraces and working camps, stairs of timber, gold lamps on the rock, and a tree grown up from the floor to find the light. */
    private static void gullet(BuildCanvas b, MineKit kit) {
        double firstRamp = Math.PI * .95, secondRamp = -Math.PI * .1;
        ramp(b, firstRamp, 1.5, 21, RING_HIGH, RING_LOW);
        ramp(b, secondRamp, 1.9, 14.5, RING_LOW, DEEP);
        terraceRail(b, kit, RING_HIGH, 18, 30, firstRamp);
        terraceRail(b, kit, RING_LOW, 12, 23, secondRamp);
        // The tree. Nothing should grow down here. It grew anyway.
        CanopyKit.trunk(b, GULLET_X, GULLET_Z, DEEP + 1, DEEP + 22, 3.6, 610);
        CanopyKit.crown(b, GULLET_X, DEEP + 22, GULLET_Z, 10, 611);
        // Camps on the upper terrace, each on its own stretch of rock.
        for (double angle : new double[]{-.9, .1, .9, 1.75, 4.3}) {
            int x = GULLET_X + (int) Math.round(Math.cos(angle) * 31), z = GULLET_Z + (int) Math.round(Math.sin(angle) * 31);
            MineProps.shack(b, x, RING_HIGH, z, 2, 2);
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
        for (int out = 0; out <= 9; out++) b.block(GULLET_X - 26 + out, RING_HIGH + 12, GULLET_Z - 14, TIMBER, "axis=x");
        for (int y = RING_HIGH + 1; y <= RING_HIGH + 11; y++) b.block(GULLET_X - 26, y, GULLET_Z - 14, TIMBER, "axis=y");
        for (int y = RING_LOW + 4; y <= RING_HIGH + 11; y++) b.block(GULLET_X - 17, y, GULLET_Z - 14, Material.IRON_CHAIN, "axis=y");
        MineProps.cart(b, GULLET_X - 18, RING_LOW + 2, GULLET_Z - 15, Material.RAW_GOLD_BLOCK);
        b.block(24, DEEP + 1, 98, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        spoil(b, GULLET_X + 8, DEEP + 1, GULLET_Z - 6, Material.RAW_IRON_BLOCK);
        spoil(b, GULLET_X - 8, DEEP + 1, GULLET_Z + 7, Material.COAL_BLOCK);
    }

    /** Grell's cave: a seam of blue crystal, ice underfoot, hanging rock, and timber loads swung from the roof that can be cut down on him. */
    private static void crystalHollow(BuildCanvas b) {
        for (int x = 31; x <= 69; x++) for (int z = 122; z <= 146; z++) {
            if (b.get(x, DEEP, z) == null) continue;
            double seam = smooth(x, z, 5, 620);
            b.block(x, DEEP, z, seam > .58 ? Material.PACKED_ICE : seam > .52 ? Material.BLUE_ICE : seam > .44 ? Material.CALCITE : noise(x, z, 622) < .5 ? Material.DEEPSLATE_TILES : Material.COBBLED_DEEPSLATE);
        }
        for (int[] burst : new int[][]{{37, 128, 12}, {63, 128, 14}, {35, 140, 9}, {65, 140, 11}, {42, 123, 7}, {58, 144, 8}}) MineProps.crystals(b, burst[0], DEEP + 1, burst[1], burst[2], MineProps.BLUE, burst[0] + burst[1]);
        for (int[] drip : new int[][]{{44, 126, 8}, {56, 126, 7}, {40, 136, 9}, {60, 136, 8}, {50, 142, 7}, {50, 124, 6}}) MineProps.stalactite(b, drip[0], DEEP + 19, drip[1], drip[2], 2.4, DARK_ROCK, Material.LIGHT_BLUE_STAINED_GLASS, drip[0]);
        for (int[] load : new int[][]{{44, 130}, {56, 130}, {44, 139}, {56, 139}}) {
            for (int y = DEEP + 8; y <= DEEP + 18; y++) b.block(load[0], y, load[1], Material.IRON_CHAIN, "axis=y");
            b.block(load[0], DEEP + 7, load[1], Material.STRIPPED_OAK_WOOD);
            for (int[] arm : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) b.block(load[0] + arm[0], DEEP + 7, load[1] + arm[1], TIMBER, arm[0] != 0 ? "axis=x" : "axis=z");
        }
        // The south door, barred till Grell is down.
        for (int x = 49; x <= 51; x++) for (int y = DEEP + 1; y <= DEEP + 4; y++) b.block(x, y, 150, Material.IRON_BARS, "east=true,west=true");
    }

    private static void straightRail(BuildCanvas b, int x1, int z1, int x2, int z2, int y) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
            b.block(x, y, z, Material.RAIL, x1 == x2 ? "shape=north_south" : "shape=east_west");
    }

    /** One straight run of rails, and the refuges men jumped into when a cart came. */
    private static void mainline(BuildCanvas b) {
        straightRail(b, 60, 170, 96, 170, LINE + 1);
        for (int x = 60; x <= 88; x += 6) b.block(x, LINE + 4, 170, Material.LANTERN, "hanging=true");
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
            for (int y = LINE + 2; y <= LINE + 6; y++) b.block(x, y, z, TIMBER, "axis=y");
            b.block(x, LINE + 7, z, Material.LANTERN, "hanging=false");
        }
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
            if (x % 10 == 4) {
                MineProps.trestle(b, x, high, z, false, 31);
                CanopyKit.lantern(b, x, high - 1, z, 2);
            }
        }
        // Down in the dark, the crystal seam that paid for all of it.
        for (int[] burst : new int[][]{{100, 150, 12}, {150, 150, 10}, {98, 190, 14}, {152, 192, 11}, {124, 196, 13}, {92, 172, 9}, {156, 172, 12}, {116, 148, 9}}) {
            int y = 31;
            while (y < 44 && !kit.open(burst[0], y, burst[1])) y++;
            MineProps.crystals(b, burst[0], y, burst[1], burst[2], MineProps.PINK, burst[0] * 3 + burst[1]);
        }
        for (int x = 92; x <= 156; x += 3) for (int z = 146; z <= 194; z += 3) {
            if (b.get(x, 30, z) != null && noise(x, z, 621) < .3) b.block(x, 30, z, Material.MAGMA_BLOCK);
        }
    }

    /** A wall of blackstone with arches, fire running in its channels and lit windows, standing out of the lava. */
    private static void fortress(BuildCanvas b, MineKit kit) {
        int[][] tiers = {{26, 46, 52}, {46, 62, 49}, {62, 76, 46}};
        for (int index = 0; index < tiers.length; index++) {
            int bottom = tiers[index][0], top = tiers[index][1], face = tiers[index][2], half = 34 - index * 8;
            for (int x = FOUNDRY_X - half; x <= FOUNDRY_X + half; x++) for (int y = bottom; y <= top; y++) {
                int bay = Math.floorMod(x - FOUNDRY_X + 4, 8) - 4;
                double arch = Math.sqrt(Math.max(0, 9 - bay * bay));
                int spring = top - 9;
                boolean opening = y > bottom + 2 && Math.abs(bay) <= 2 && y <= spring + arch && !(index == 0 && Math.abs(x - FOUNDRY_X) <= 4);
                boolean cornice = y == top || y == top - 1;
                if (opening) {
                    b.block(x, y, face, Material.AIR);
                    b.block(x, y, face - 1, y % 4 == 0 ? Material.SHROOMLIGHT : Material.ORANGE_STAINED_GLASS);
                    b.block(x, y, face - 2, Material.POLISHED_BLACKSTONE_BRICKS);
                } else {
                    b.block(x, y, face, cornice ? Material.CHISELED_POLISHED_BLACKSTONE : pick(FORT, x + y, face, 622));
                    b.block(x, y, face - 1, pick(FORT, x, y, 623));
                }
                if (y == top + 0 && Math.floorMod(x, 3) == 0) b.block(x, y + 1, face + 1, Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        // The great gate at the head of the causeway.
        for (int x = FOUNDRY_X - 5; x <= FOUNDRY_X + 5; x++) for (int y = FORGE + 1; y <= FORGE + 11; y++) {
            int off = Math.abs(x - FOUNDRY_X);
            boolean way = off <= 2 && y <= FORGE + 5 + (off < 2 ? 2 : 1);
            for (int z = 47; z <= 52; z++) if (way) b.block(x, y, z, Material.AIR);
            if (!way) b.block(x, y, 52, off == 5 || y == FORGE + 11 ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
        }
        // Fire pours down the channels from the top tier into the sea.
        for (int offset : new int[]{-22, 22, -10, 10}) {
            int x = FOUNDRY_X + offset, top = Math.abs(offset) > 12 ? 62 : 76;
            for (int y = top; y >= LAVA; y--) {
                int z = y > 62 ? 47 : y > 46 ? 50 : 53;
                b.block(x, y, z, Material.LAVA, y == top ? "level=0" : "level=8");
                b.block(x + 1, y, z, Material.LAVA, "level=8");
                if (y == 62 || y == 46) for (int run = z; run <= z + 3; run++) { b.block(x, y, run, Material.LAVA, "level=8"); b.block(x + 1, y, run, Material.LAVA, "level=8"); }
            }
        }
        for (int side : new int[]{-1, 1}) {
            int x = FOUNDRY_X + side * 38;
            for (int y = 24; y <= 72; y++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                double from = Math.hypot(dx, dz);
                if (from > 4.3 || from < 3 && y < 70) continue;
                b.block(x + dx, y, 54 + dz, y % 7 == 0 ? Material.CHISELED_POLISHED_BLACKSTONE : (dx == 0 || dz == 0) && y % 7 == 3 ? Material.SHROOMLIGHT : pick(FORT, x + dx + y, 54 + dz, 624));
            }
        }
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
        for (int run = alongZ ? Math.min(z1, z2) + 2 : Math.min(x1, x2) + 2; run < (alongZ ? Math.max(z1, z2) : Math.max(x1, x2)); run += 9) {
            for (int side : new int[]{-1, 1}) {
                int x = alongZ ? x1 + side * (half + 4) : run, z = alongZ ? run : z1 + side * (half + 4);
                for (int y = 19; y < FORGE; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.block(x + dx, y, z + dz, pick(FORT, x + dx + y, z + dz, 626));
                MineProps.spire(b, x, FORGE + 1, z, 10, Material.POLISHED_BLACKSTONE_BRICKS, Material.CHISELED_POLISHED_BLACKSTONE, Material.SHROOMLIGHT);
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
        for (int[] corner : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) for (int y = FORGE + 15; y <= 62; y++) if (kit.open(FOUNDRY_X + corner[0], y, FOUNDRY_Z + corner[1])) b.block(FOUNDRY_X + corner[0], y, FOUNDRY_Z + corner[1], Material.IRON_CHAIN, "axis=y");
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
        for (int[] spire : new int[][]{{88, 74, 24}, {94, 104, 30}, {158, 76, 28}, {152, 108, 22}, {100, 62, 18}, {146, 60, 20}, {104, 116, 16}, {142, 118, 14}}) {
            for (int up = 0; up < spire[2]; up++) {
                double across = 4.5 * Math.pow(1 - up / (double) spire[2], .7) + .4;
                int reach = (int) Math.ceil(across);
                for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++)
                    if (Math.hypot(dx, dz) <= across + (noise(spire[0] + dx, spire[1] + dz + up / 3, 629) - .5) * 1.2) b.block(spire[0] + dx, 20 + up, spire[1] + dz, up % 5 == 2 && noise(dx, dz, up) < .4 ? Material.MAGMA_BLOCK : pick(HOT_ROCK, spire[0] + dx + up, spire[1] + dz, 630));
            }
        }
        for (int index = 0; index < 34; index++) {
            int x = 84 + (int) (noise(index, 11, 631) * 78), z = 54 + (int) (noise(11, index, 632) * 62), y = 66;
            while (y < 66 + 20 && kit.open(x, y, z)) y++;
            if (y >= 86 || Math.hypot(x - FOUNDRY_X, z - FOUNDRY_Z) < 9) continue;
            MineProps.stalactite(b, x, y, z, 7 + (int) (noise(index, index, 633) * 14), 2.4 + noise(z, x, 634) * 2.6, HOT_ROCK, null, 635 + index);
        }
        for (int[] fall : new int[][]{{96, 84, 2}, {150, 72, 2}, {148, 106, 1}, {100, 108, 1}, {156, 92, 1}}) {
            int y = 60;
            while (y < 80 && kit.open(fall[0], y, fall[1])) y++;
            MineProps.lavafall(b, fall[0], y - 1, fall[1], LAVA + 1, fall[2]);
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

    /** A drowned temple across the far wall of the cavern, older than the mine: columns, arches, and blue light behind them. */
    private static void temple(BuildCanvas b) {
        int face = 283;
        for (int z = 6; z <= 74; z++) for (int y = 2; y <= 48; y++) {
            int bay = Math.floorMod(z - 40 + 7, 14) - 7;
            double arch = Math.sqrt(Math.max(0, 25 - bay * bay));
            int tier = y < 20 ? 0 : y < 36 ? 1 : 2, spring = tier == 0 ? 12 : tier == 1 ? 28 : 41;
            int foot = tier == 0 ? WATER + 1 : tier == 1 ? 21 : 37;
            boolean opening = Math.abs(bay) <= 4 && y >= foot && y <= spring + arch * (tier == 2 ? .6 : 1);
            int roofline = 48 - Math.abs(z - 40) / 2;
            if (y > roofline) continue;
            if (opening) {
                b.block(face, y, z, Material.AIR);
                b.block(face + 2, y, z, y % 3 == 0 ? Material.SEA_LANTERN : Material.CYAN_STAINED_GLASS);
                b.block(face + 3, y, z, Material.DARK_PRISMARINE);
            } else {
                b.block(face, y, z, Math.abs(bay) >= 6 ? Material.PRISMARINE_BRICKS : Material.DARK_PRISMARINE);
                b.block(face + 1, y, z, Material.DARK_PRISMARINE);
                if (Math.abs(bay) >= 6 && y % 5 == 0) b.block(face - 1, y, z, Material.SEA_LANTERN);
            }
        }
    }

    /** The bottom of everything: black water, a trestle out across it, and the Gloomforge at anchor in a blue cathedral. */
    private static void drownedDeep(BuildCanvas b, MineKit kit) {
        kit.shore(b, 176, 34, 11, 17, 2, DOCK, 519);
        kit.shore(b, 206, 6, 9, 7, 2, 11, 520);
        kit.shore(b, 203, 74, 8, 8, 2, 11, 521);
        kit.shore(b, 280, 18, 10, 13, 2, 12, 522);
        kit.shore(b, 272, 72, 11, 8, 2, 12, 523);
        temple(b);
        // The dock and the trestle out to the ship.
        for (int z = 26; z <= 42; z++) for (int x = 172; x <= 187; x++) plank(b, x, DOCK, z);
        for (int z = 26; z <= 42; z += 16) for (int x = 172; x <= 187; x++) b.block(x, DOCK + 1, z, RAIL);
        for (int z = 26; z <= 42; z++) if (z < 33 || z > 35) b.block(187, DOCK + 1, z, RAIL);
        b.block(178, DOCK + 1, 38, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        crates(b, 174, DOCK + 1, 28);
        crates(b, 183, DOCK + 1, 40);
        for (int x = 188; x <= 215; x++) {
            for (int z = 33; z <= 35; z++) plank(b, x, DOCK, z);
            b.block(x, DOCK + 1, 32, RAIL);
            b.block(x, DOCK + 1, 36, RAIL);
            if (x % 6 == 0) {
                for (int y = 2; y <= DOCK - 1; y++) for (int z : new int[]{32, 36}) b.block(x, y, z, Material.STRIPPED_DARK_OAK_LOG, "axis=y");
                for (int z : new int[]{32, 36}) {
                    for (int y = DOCK + 2; y <= DOCK + 3; y++) b.block(x, y, z, RAIL);
                    b.block(x, DOCK + 4, z, Material.SOUL_LANTERN, "hanging=false");
                }
            }
        }
        for (int step = 0; step < 4; step++) for (int z = 33; z <= 35; z++) {
            int x = 216 + step, half = step + 1;
            b.block(x, DOCK + half / 2, z, Material.SPRUCE_PLANKS);
            if (half % 2 == 1) b.block(x, DOCK + half / 2 + 1, z, Material.SPRUCE_SLAB, "type=bottom");
        }
        GloomforgeShip.build(b);
        // Blue crystal on every shore, and in the water itself.
        for (int[] burst : new int[][]{{206, 6, 11, 18}, {203, 74, 11, 16}, {280, 18, 12, 22}, {272, 72, 12, 18}, {196, 10, 8, 10}, {214, 70, 6, 12}, {286, 44, 8, 14}, {262, 8, 6, 15}, {258, 78, 6, 14}}) MineProps.crystals(b, burst[0], burst[2], burst[1], burst[3], MineProps.BLUE, burst[0] + burst[1]);
        kit.flood(b, Material.WATER, 170, -12, 300, 92, 2, WATER);
        for (int x = 182; x <= 292; x += 2) for (int z = -2; z <= 84; z += 2) {
            if (noise(x, z, 636) < .06 && b.get(x, 1, z) != null) b.block(x, 1, z, Material.SEA_LANTERN);
            if (noise(z, x, 637) < .05 && b.get(x, 2, z) == Material.WATER) for (int y = 2; y < 6 + noise(x, z, 638) * 5; y++) b.block(x, y, z, Material.KELP_PLANT);
        }
        // The roof comes down in great blue-tipped curtains, and water falls in threads.
        for (int index = 0; index < 46; index++) {
            int x = 180 + (int) (noise(index, 7, 524) * 110), z = -2 + (int) (noise(7, index, 525) * 84), y = 40;
            if (Math.abs(x - GloomforgeShip.X) < 20 && Math.abs(z - GloomforgeShip.Z) < 30) continue;
            while (y < 62 && kit.open(x, y, z)) y++;
            if (y < 62) MineProps.stalactite(b, x, y, z, 8 + (int) (noise(index, index, 526) * 16), 3 + noise(z, x, 527) * 3, WET_ROCK, Material.SEA_LANTERN, 640 + index);
        }
        for (int[] fall : new int[][]{{190, 12, 3}, {214, 78, 2}, {262, 4, 3}, {288, 66, 2}}) {
            int y = 34;
            while (y < 62 && kit.open(fall[0], y, fall[1])) y++;
            MineProps.waterfall(b, fall[0], y - 1, fall[1], WATER + 1, fall[2]);
        }
    }
}
