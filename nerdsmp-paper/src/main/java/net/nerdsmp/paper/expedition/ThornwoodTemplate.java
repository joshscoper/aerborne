package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.*;

/**
 * Thornwood Canopy: decks and rope bridges among giant trees at night. Rootgate (spawn) → the Bough
 * Run (a jump course) → the Webbed Hollow → the optional Sentinel Boughs to the west → the Briar
 * Matron's Nest → the Hunter's Fire → the Thornheart Grove. Decks at Y63; runs south (+Z).
 */
final class ThornwoodTemplate {
    static final int Y = 63;
    private static final Material LOG = Material.DARK_OAK_LOG;
    private static final Material LEAVES = Material.DARK_OAK_LEAVES;
    private static final Material PLANK = Material.SPRUCE_PLANKS;
    private static final Material RAIL = Material.SPRUCE_FENCE;

    /** Grove cells that must stay clear: the boss and add spawn points and the exit (see thornwood_canopy.yml). */
    private static final java.util.Set<String> GROVE_SPOTS = java.util.Set.of("0:114", "-9:108", "9:108", "-10:116", "10:116", "10:112");

    private ThornwoodTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        rootgate(b);
        boughRun(b);
        webbedHollow(b);
        sentinelBoughs(b);
        nest(b);
        huntersFire(b);
        grove(b);
        SampleChests.place(b, "thornwood_canopy");
        return b.freeze();
    }

    private static void rootgate(BuildCanvas b) {
        giantTree(b, -11, 4, 20, 76, 3, 11, LOG, LEAVES);
        deck(b, -6, -3, 6, 10, Y, PLANK, RAIL, (x, z) -> z == 10 && Math.abs(x) <= 1);
        lamp(b, -5, Y + 1, 9, 2, RAIL);
        lamp(b, 5, Y + 1, 9, 2, RAIL);
        b.block(-3, Y + 1, 7, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        b.block(-4, Y + 1, 7, Material.STRIPPED_DARK_OAK_LOG, "axis=z");
        b.block(4, Y + 1, 1, Material.BARREL, "facing=up");
        b.block(5, Y + 1, 1, Material.TARGET);
        // A ranger's lookout above the deck.
        for (int y = Y + 1; y <= Y + 6; y++) b.block(5, y, -2, Material.LADDER, "facing=south");
        for (int y = Y + 1; y <= Y + 6; y++) b.block(5, y, -3, PLANK);
        deck(b, 3, -3, 6, -1, Y + 7, PLANK, RAIL, (x, z) -> false);
    }

    /** Branch pads and log beams over the drop; fall below Y56 and you're back at the deck. */
    private static void boughRun(BuildCanvas b) {
        leafPad(b, 1, Y, 14, LEAVES, LOG);
        beam(b, -2, Y, 17, 22, LOG);
        leafPad(b, 0, Y + 1, 25, LEAVES, LOG);
        leafPad(b, 3, Y + 1, 29, LEAVES, LOG);
        beam(b, 3, Y, 32, 36, LOG);
        leafPad(b, 0, Y, 39, LEAVES, LOG);
        // Tall trunks rise beside the course so it reads as the inside of the forest.
        giantTree(b, 10, 24, 10, 74, 2, 8, LOG, LEAVES);
        giantTree(b, -10, 32, 12, 72, 2, 8, LOG, LEAVES);
        // Lanterns on short posts set into the pads light the jumps.
        for (int[] light : new int[][]{{2, 15, 1}, {-1, 26, 2}, {4, 30, 2}, {1, 40, 1}}) b.block(light[0], Y + light[2], light[1], Material.LANTERN);
    }

    private static void webbedHollow(BuildCanvas b) {
        giantTree(b, 9, 53, 15, 78, 3, 12, LOG, LEAVES);
        deck(b, -10, 42, 5, 64, Y, PLANK, RAIL, (x, z) -> z == 42 && Math.abs(x) <= 1 || z == 64 && Math.abs(x) <= 1 || x == -10 && z >= 55 && z <= 57);
        // Webs strung between the rails and the trunk, and the spiders' egg sacs.
        for (int[] web : new int[][]{{-9, 44}, {4, 45}, {-9, 62}, {4, 61}, {-4, 50}, {2, 57}})
            b.block(web[0], Y + 2, web[1], Material.COBWEB);
        for (int[] sac : new int[][]{{-7, 47}, {3, 52}, {-6, 60}}) {
            b.block(sac[0], Y + 1, sac[1], Material.WHITE_WOOL);
            b.block(sac[0], Y + 2, sac[1], Material.COBWEB);
        }
        lamp(b, -9, Y + 1, 50, 2, RAIL);
        lamp(b, 4, Y + 1, 48, 2, RAIL);
        bridge(b, Y, 65, 70, PLANK, RAIL);
        // The side bridge west to the Sentinel Boughs.
        for (int x = -16; x <= -11; x++) for (int z = 55; z <= 57; z++) {
            b.block(x, Y, z, PLANK);
            if (z != 56) b.block(x, Y + 1, z, RAIL);
        }
    }

    /** An archers' perch round a trunk to the west: optional, but worth enemy forces. */
    private static void sentinelBoughs(BuildCanvas b) {
        giantTree(b, -28, 56, 14, 76, 3, 10, LOG, LEAVES);
        deck(b, -24, 49, -17, 63, Y, PLANK, RAIL, (x, z) -> x == -17 && z >= 55 && z <= 57);
        for (int[] crate : new int[][]{{-23, 50}, {-23, 62}}) {
            b.block(crate[0], Y + 1, crate[1], Material.BARREL, "facing=up");
            b.block(crate[0], Y + 2, crate[1], Material.TARGET);
        }
        lamp(b, -18, Y + 1, 50, 2, RAIL);
        lamp(b, -18, Y + 1, 62, 2, RAIL);
    }

    private static void nest(BuildCanvas b) {
        giantTree(b, -12, 82, 15, 80, 3, 12, LOG, LEAVES);
        deck(b, -8, 71, 11, 92, Y, PLANK, RAIL, (x, z) -> z == 71 && Math.abs(x) <= 1 || z == 92 && Math.abs(x) <= 1);
        // The Matron's web dome over the middle of the deck.
        for (int x = -5; x <= 6; x++) for (int z = 76; z <= 87; z++) for (int y = Y + 4; y <= Y + 8; y++) {
            double dx = (x - .5) / 6, dz = (z - 81.5) / 6, dy = (y - Y - 3) / 5.0;
            double shell = dx * dx + dy * dy + dz * dz;
            if (shell > .82 && shell <= 1 && noise(x, z + y, 131) < .45) b.block(x, y, z, Material.COBWEB);
        }
        for (int[] sac : new int[][]{{-6, 74}, {9, 78}, {-6, 89}, {9, 88}, {2, 90}}) {
            b.block(sac[0], Y + 1, sac[1], Material.WHITE_WOOL);
            b.block(sac[0], Y + 2, sac[1], Material.WHITE_WOOL);
        }
        lamp(b, -7, Y + 1, 72, 2, RAIL);
        lamp(b, 10, Y + 1, 72, 2, RAIL);
        bridge(b, Y, 93, 94, PLANK, RAIL);
    }

    private static void huntersFire(BuildCanvas b) {
        deck(b, -4, 95, 4, 100, Y, PLANK, RAIL, (x, z) -> (z == 95 || z == 100) && Math.abs(x) <= 1);
        b.block(3, Y + 1, 97, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        b.block(3, Y + 1, 99, Material.STRIPPED_DARK_OAK_LOG, "axis=x");
        bridge(b, Y, 101, 102, PLANK, RAIL);
    }

    /** The Heart Tree: the largest trunk, on a round deck with roots arching over it, thorns and glow berries. */
    private static void grove(BuildCanvas b) {
        giantTree(b, 0, 127, 8, 84, 5, 15, LOG, LEAVES);
        roundDeck(b, 0, 117, 14, Y, PLANK, RAIL, (x, z) -> z < 106 && Math.abs(x) <= 1);
        // Moss and thorns (sweet berry bushes sting) across the deck, away from the walkway and the spawn spots.
        for (int x = -13; x <= 13; x++) for (int z = 104; z <= 130; z++) {
            if (Math.abs(x) <= 2 && z <= 112) continue;
            if (x * x + (z - 117) * (z - 117) > 156) continue; // Off the deck.
            if (x * x + (z - 127) * (z - 127) <= 36) continue; // The trunk.
            if (GROVE_SPOTS.contains(x + ":" + z)) continue;
            double roll = noise(x, z, 141);
            if (roll < .05) b.block(x, Y + 1, z, Material.SWEET_BERRY_BUSH, "age=3");
            else if (roll < .15) b.block(x, Y + 1, z, Material.MOSS_CARPET);
        }
        // Roots radiate from the trunk and hump over the deck.
        for (int index = 0; index < 7; index++) {
            double angle = Math.PI * (1.15 + index * .12);
            for (int d = 6; d <= 12; d++) {
                int x = (int) Math.round(Math.cos(angle) * d), z = 127 + (int) Math.round(Math.sin(angle) * d);
                if (x * x + (z - 117) * (z - 117) > 169 || GROVE_SPOTS.contains(x + ":" + z)) continue;
                int hump = d >= 8 && d <= 10 ? 1 : 0;
                b.block(x, Y + 1 + hump, z, LOG, "axis=y");
                if (hump == 1) b.block(x, Y + 1, z, Material.AIR);
            }
        }
        // Lamps round the rim and shroomlights set into the deck.
        for (int index = 0; index < 8; index++) {
            double angle = 2 * Math.PI * index / 8 + Math.PI / 8;
            int x = (int) Math.round(Math.cos(angle) * 12), z = 117 + (int) Math.round(Math.sin(angle) * 12);
            if (Math.abs(x) <= 2 && z < 117) continue;
            lamp(b, x, Y + 1, z, 3, RAIL);
        }
        for (int[] glow : new int[][]{{-5, 114}, {5, 114}, {-9, 122}, {9, 122}}) b.block(glow[0], Y, glow[1], Material.SHROOMLIGHT);
    }
}
