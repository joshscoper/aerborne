package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.noise;
import static net.nerdsmp.paper.expedition.SampleKit.pick;
import static net.nerdsmp.paper.expedition.SampleKit.stone;
import static net.nerdsmp.paper.expedition.SkyKit.*;

/**
 * First Flight, second edition: a training run along the Wardens' Road at sunset. The party steps off
 * Captain Wren's airship at the Landing, crosses the Trial Path, holds the Guardian Yard, wakes the rune
 * stones that open its gate, and walks the road on its arches to the Final Test at the ruined gate.
 *
 * <p>Runs south (+Z); ground at Y63, the airship's deck at Y66. Anyone who falls is caught
 * ({@code falls:} in {@code expeditions/first_flight.yml}), which marks the same coordinates.
 */
final class FirstFlightTemplate {
    static final int Y = 63, DECK = 66;
    private static final int LANDING_Z = 22, YARD_Z = 103, GATE_Z = 170;
    private static final Material[] PAVING = {Material.STONE_BRICKS, Material.STONE_BRICKS, Material.ANDESITE, Material.POLISHED_ANDESITE, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS};
    private static final int[][] LEVERS = {{-12, 110}, {12, 110}, {0, 118}};
    private static final int[][] RITE_FIRES = {{-13, 170}, {13, 170}, {-9, 178}, {9, 178}, {0, 181}};

    private FirstFlightTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        sky(b);
        landing(b);
        trialPath(b);
        yard(b);
        road(b, 123, 147, Y);
        finalTest(b);
        SkyRuins.overgrow(b, -26, Y + 1, 82, 26, Y + 34, 124, .07, Y + 5, 391);
        SkyRuins.overgrow(b, -28, Y + 1, 146, 28, Y + 32, 196, .34, Y + 4, 392);
        SkyRuins.overgrow(b, -20, Y + 1, 0, 20, Y + 18, 44, .12, Y + 3, 393);
        SampleChests.place(b, "first_flight");
        CanopyKit.joinRails(b);
        CanopyKit.joinRails(b, SkyShip.RAIL);
        return b.freeze();
    }

    /** Clouds below, and far islands with their waterfalls, so the road always has somewhere to look. */
    private static void sky(BuildCanvas b) {
        for (int x = -90; x <= 90; x += 22) for (int z = -50; z <= 230; z += 22) {
            if (noise(x, z, 301) < .25) continue;
            int nudge = (int) (noise(z, x, 302) * 11) - 5;
            cloud(b, x + nudge, 34 + (int) (noise(x, z, 303) * 8), z - nudge, 10, 4, 304 + x + z);
        }
        int[][] far = {{-46, 36, 80, 8}, {48, 20, 74, 7}, {52, 90, 84, 9}, {-52, 130, 72, 7}, {46, 150, 78, 8}, {-44, 190, 86, 9}, {-62, -36, 72, 7}, {60, -30, 86, 6}};
        for (int index = 0; index < far.length; index++) {
            int[] isle = far[index];
            isle(b, isle[0], isle[2], isle[1], isle[3], isle[3] + 6, true);
            blossom(b, isle[0] - 2, isle[2] + 1, isle[1] + 1, 5, 310 + index);
        }
        // A broken viaduct strides past the Landing, as on the old maps.
        for (int z = 8; z <= 58; z++) {
            int along = Math.floorMod(z, 12), spring = (int) Math.round(Math.sqrt(36 - (along - 6) * (along - 6)) * 1.6);
            int foot = along == 0 ? 22 : spring;
            boolean gap = z > 30 && z < 36;
            if (gap) continue;
            for (int x = 34; x <= 37; x++) {
                b.block(x, 74, z, pick(PAVING, x, z, 321));
                for (int down = 1; down <= foot; down++) b.block(x, 74 - down, z, pick(PAVING, x, z + down, 322));
            }
        }
    }

    private static void paved(BuildCanvas b, int cx, int cz, int radius) {
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++)
            if (Math.hypot(x - cx, z - cz) <= radius + (noise(x, z, 331) - .5) * 2.5) b.block(x, Y, z, pick(PAVING, x, z, 332));
    }

    /** The training outpost: a walled square, stalls, a watchtower, cherry trees, and the airship moored at its pier. */
    private static void landing(BuildCanvas b) {
        isle(b, 0, Y, LANDING_Z, 18, 30, true);
        paved(b, 0, LANDING_Z, 13);
        SkyRuins.rimWall(b, 0, LANDING_Z, 16, -62, 66, Y + 1, 2, 334);
        SkyRuins.rimWall(b, 0, LANDING_Z, 16, 114, 242, Y + 1, 2, 335);
        SkyRuins.tower(b, -13, Y + 1, 11, 3, 15, 336);
        // The road south.
        for (int z = LANDING_Z; z <= 40; z++) for (int x = -2; x <= 2; x++) b.block(x, Y, z, pick(PAVING, x, z, 333));
        SkyShip.moor(b, 0, DECK, -12);
        // The pier, and the gangway down off the ship's side.
        for (int z = 3; z <= 9; z++) for (int x = -3; x <= 3; x++) b.block(x, Y, z, Math.floorMod(z, 2) == 0 ? Material.SPRUCE_PLANKS : Material.STRIPPED_SPRUCE_WOOD);
        for (int z = 3; z <= 9; z++) for (int x : new int[]{-3, 3}) b.block(x, Y + 1, z, RAIL_POST);
        for (int z = -4; z <= 2; z++) {
            for (int x = -1; x <= 1; x++) b.block(x, DECK, z, Material.SPRUCE_PLANKS);
            for (int x : new int[]{-2, 2}) { b.block(x, DECK, z, Material.DARK_OAK_PLANKS); b.block(x, DECK + 1, z, RAIL_POST); }
        }
        for (int x = -1; x <= 1; x++) {
            b.block(x, DECK - 1, 3, Material.SPRUCE_STAIRS, "facing=north,half=bottom,shape=straight");
            b.block(x, DECK - 2, 4, Material.SPRUCE_STAIRS, "facing=north,half=bottom,shape=straight");
        }
        for (int[] rail : new int[][]{{-2, DECK, 3}, {2, DECK, 3}, {-2, DECK - 1, 4}, {2, DECK - 1, 4}}) b.block(rail[0], rail[1], rail[2], RAIL_POST);
        for (int z = 2; z <= 4; z++) for (int x : new int[]{-2, 2}) b.block(x, DECK - 2 - (z - 2) / 2, z, Material.SPRUCE_PLANKS);
        SkyRuins.gateway(b, 0, Y + 1, 10, 4, 7);
        SkyRuins.gateway(b, 0, Y + 1, 39, 3, 6);
        bannerPost(b, -7, Y + 1, 10, true, Material.RED_WOOL);
        bannerPost(b, 6, Y + 1, 10, true, Material.RED_WOOL);
        blossom(b, -11, Y + 1, 30, 9, 341);
        blossom(b, 11, Y + 1, 14, 7, 342);
        tent(b, -9, Y + 1, 17, Material.RED_WOOL);
        tent(b, 9, Y + 1, 27, Material.BLUE_WOOL);
        crates(b, 8, Y + 1, 14);
        crates(b, -8, Y + 1, 27);
        for (int[] light : new int[][]{{-4, 13}, {4, 18}, {-4, 26}, {4, 33}, {-4, 37}, {4, 38}}) stoneLantern(b, light[0], Y + 1, light[1]);
        b.block(4, Y + 1, 16, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        for (int[] seat : new int[][]{{5, 16, 0}, {3, 16, 0}, {4, 15, 1}}) b.block(seat[0], Y + 1, seat[1], Material.STRIPPED_CHERRY_LOG, seat[2] == 0 ? "axis=z" : "axis=x");
    }

    private static final Material RAIL_POST = Material.SPRUCE_FENCE;

    /** Islets and stepping stones under blue banners. A fall is only a fall: a line catches you. */
    private static void trialPath(BuildCanvas b) {
        isle(b, 0, Y, 45, 5, 13, false);
        stone(b, 4, Y, 51, Material.STONE_BRICKS);
        SkyRuins.pad(b, 4, Y, 51);
        stone(b, 7, Y, 55, Material.MOSSY_STONE_BRICKS);
        SkyRuins.pad(b, 7, Y, 55);
        isle(b, 9, Y, 61, 5, 15, true);
        SkyRuins.column(b, 6, Y + 1, 64, 7, false, 352);
        bannerPost(b, 11, Y + 1, 58, false, Material.BLUE_WOOL);
        CanopyKit.rope(b, 5, 61, -3, 61, Y, 2);
        isle(b, -7, Y, 61, 5, 15, false);
        bannerPost(b, -10, Y + 1, 63, false, Material.BLUE_WOOL);
        SkyRuins.column(b, -4, Y + 1, 58, 6, false, 353);
        stone(b, -7, Y, 67, Material.STONE_BRICKS);
        SkyRuins.pad(b, -7, Y, 67);
        stone(b, -4, Y, 71, Material.MOSSY_STONE_BRICKS);
        SkyRuins.pad(b, -4, Y, 71);
        isle(b, 0, Y, 77, 6, 16, true);
        SkyRuins.column(b, 4, Y + 1, 74, 8, false, 354);
        for (int[] light : new int[][]{{-2, 43}, {7, 60}, {-6, 59}, {-3, 77}}) stoneLantern(b, light[0], Y + 1, light[1]);
        b.block(3, Y + 1, 77, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        b.block(4, Y + 1, 77, Material.STRIPPED_SPRUCE_LOG, "axis=z");
        crystals(b, -4, Y + 1, 75, 351);
        CanopyKit.rope(b, 0, 81, 0, 84, Y, 0);
        // Little islets off to the side, too far to reach.
        for (int[] islet : new int[][]{{-16, 52, 2, 58}, {20, 68, 3, 70}, {-20, 74, 2, 66}, {18, 46, 2, 72}}) isle(b, islet[0], islet[3], islet[1], islet[2] + 1, 10, false);
    }

    /** The Guardian Yard: a walled round of old paving with a burning rune in its floor and a great gate south. */
    private static void yard(BuildCanvas b) {
        isle(b, 0, Y, YARD_Z, 24, 36, true);
        paved(b, 0, YARD_Z, 20);
        // The rune in the floor.
        for (int x = -11; x <= 11; x++) for (int z = YARD_Z - 11; z <= YARD_Z + 11; z++) {
            double distance = Math.hypot(x, z - YARD_Z), angle = Math.atan2(z - YARD_Z, x);
            boolean ring = Math.abs(distance - 10) < .6 || Math.abs(distance - 6) < .5, spoke = distance < 10 && Math.abs(Math.sin(angle * 4)) < .08;
            if (ring || spoke) b.block(x, Y, z, Material.OCHRE_FROGLIGHT, "axis=y");
        }
        // An arcade round the court, with a sill that holds a fight in, and the north arch.
        SkyRuins.arcade(b, 0, YARD_Z, 20, Y + 1, 1, 11, 9, 2, 0, 361);
        arch(b, 0, Y + 1, 84, 8, 15, true, 364);
        gatehouse(b);
        for (int[] lever : LEVERS) {
            runeStone(b, lever[0], Y + 1, lever[1]);
            b.block(lever[0], Y + 3, lever[1], Material.LEVER, "face=floor,facing=south,powered=false");
        }
        bannerPost(b, -12, Y + 1, 88, true, Material.RED_WOOL);
        bannerPost(b, 11, Y + 1, 88, true, Material.RED_WOOL);
        for (int[] light : new int[][]{{-15, 96}, {15, 96}, {-15, 112}, {15, 112}}) stoneLantern(b, light[0], Y + 1, light[1]);
        crates(b, -15, Y + 1, 106);
        crystals(b, 16, Y + 1, 104, 368);
        blossom(b, -14, Y + 1, 92, 6, 369);
    }

    /** The south side of the yard: two tiers of arches between round towers, and the gate shut until the rune stones wake. */
    private static void gatehouse(BuildCanvas b) {
        int top = 22;
        for (int x = -13; x <= 13; x++) for (int rel = 1; rel <= top; rel++) for (int z = 122; z <= 123; z++) {
            int bay = Math.floorMod(x + 12, 5) - 2, peak = 20 - Math.abs(bay);
            boolean upper = rel >= 13 && rel <= peak && Math.abs(bay) <= 1 && Math.abs(x) <= 11;
            int niche = Math.abs(Math.abs(x) - 9), head = 8 - niche;
            boolean recess = z == 122 && niche <= 2 && rel <= head;
            boolean way = z == 122 && Math.abs(x) <= 1 && rel <= 4;
            if (upper || recess || way) continue;
            boolean trim = rel == 11 || rel == 12 || rel == top || Math.abs(x) == 2 && rel <= 5 || rel == 5 && Math.abs(x) <= 2;
            b.block(x, Y + rel, z, trim ? Material.CHISELED_STONE_BRICKS : pick(PAVING, x + rel, z, 366));
        }
        for (int x = -13; x <= 13; x += 2) b.block(x, Y + top + 1, 122, pick(PAVING, x, 122, 367));
        for (int x = -1; x <= 1; x++) for (int y = Y + 1; y <= Y + 4; y++) b.block(x, y, 123, Material.IRON_BARS, "east=true,west=true");
        banner(b, -10, Y + 7, 122, true, Material.RED_WOOL);
        banner(b, 8, Y + 7, 122, true, Material.RED_WOOL);
        SkyRuins.tower(b, -15, Y + 1, 120, 3, 26, 368);
        SkyRuins.tower(b, 15, Y + 1, 120, 3, 26, 369);
    }

    /** The ruined gate of the Wardens' Road, where the Old Sentinel lies in pieces on its round. */
    private static void finalTest(BuildCanvas b) {
        isle(b, 0, Y, GATE_Z, 25, 38, true);
        paved(b, 0, GATE_Z, 21);
        for (int x = -16; x <= 16; x++) for (int z = GATE_Z - 16; z <= GATE_Z + 16; z++) {
            double distance = Math.hypot(x, z - GATE_Z);
            if (Math.abs(distance - 15) < .6) b.block(x, Y, z, Material.CHISELED_STONE_BRICKS);
            else if (distance < 3.5) b.block(x, Y, z, Material.POLISHED_DEEPSLATE);
        }
        // Rune strips across the floor, where the Sentinel's power runs.
        for (int z : new int[]{160, 170, 180}) for (int x = -15; x <= 15; x++) b.block(x, Y, z, x % 3 == 0 ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE);
        arch(b, 0, Y + 1, 148, 8, 15, true, 371);
        arch(b, 0, Y + 1, 190, 10, 22, true, 372);
        // What's left of the colonnade that ringed the round.
        SkyRuins.arcade(b, 0, GATE_Z, 23, Y + 1, 2, 11, 11, 0, .62, 373);
        for (int[] pillar : new int[][]{{-18, 162, 14}, {18, 162, 9}, {-18, 178, 7}, {18, 178, 13}, {-13, 154, 8}, {13, 186, 11}}) SkyRuins.column(b, pillar[0], Y + 1, pillar[1], pillar[2], false, 374);
        for (int x = -21; x <= 21; x++) for (int z = GATE_Z - 21; z <= GATE_Z + 21; z++) {
            double distance = Math.hypot(x, z - GATE_Z);
            if (distance > 16.5 && distance < 22 && Math.abs(x) > 3 && CanopyKit.empty(b, x, Y + 1, z) && noise(x, z, 375) < .16)
                b.block(x, Y + 1, z, noise(z, x, 376) < .5 ? Material.MOSS_CARPET : Material.COBBLESTONE_SLAB, noise(z, x, 376) < .5 ? "" : "type=bottom");
        }
        for (int[] fire : RITE_FIRES) {
            b.block(fire[0], Y + 1, fire[1], Material.CHISELED_STONE_BRICKS);
            b.block(fire[0], Y + 2, fire[1], Material.SOUL_CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        }
        for (int[] spot : new int[][]{{-15, 154}, {15, 154}, {-16, 184}, {16, 184}}) crystals(b, spot[0], Y + 1, spot[1], 375 + spot[0]);
        for (int[] light : new int[][]{{-6, 152}, {6, 152}, {-5, 186}, {5, 186}}) stoneLantern(b, light[0], Y + 1, light[1]);
        blossom(b, 15, Y + 1, 150, 6, 379);
        // The way home: the road running on through the gate.
        for (int z = 186; z <= 194; z++) for (int x = -2; x <= 2; x++) b.block(x, Y, z, Material.CHISELED_STONE_BRICKS);
    }
}
