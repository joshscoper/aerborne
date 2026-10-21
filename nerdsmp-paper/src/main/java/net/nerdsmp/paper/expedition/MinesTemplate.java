package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.noise;
import static net.nerdsmp.paper.expedition.SampleKit.pick;
import static net.nerdsmp.paper.expedition.SampleTemplates.doorway;
import static net.nerdsmp.paper.expedition.SampleTemplates.hall;

/**
 * Gloomforge Mines: the Lift Station (spawn), the Ore Galleries, Grell's Shaft, the Blasting Hall,
 * the Slag Pits, the Miners' Rest and the Foreman's Delve. Timber-framed galleries cut through
 * deepslate. Runs south (+Z); floors at Y63, doorways three wide at X -1..1.
 */
final class MinesTemplate {
    static final SampleTemplates.Palette MINE = new SampleTemplates.Palette(
        new Material[]{Material.STONE, Material.ANDESITE, Material.GRAVEL, Material.TUFF, Material.COBBLED_DEEPSLATE},
        new Material[]{Material.DEEPSLATE, Material.TUFF, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE, Material.STONE},
        Material.STRIPPED_SPRUCE_LOG, "axis=y", Material.DEEPSLATE, Material.LANTERN, "hanging=true");
    private static final Material[] ORES = {Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_COPPER_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_REDSTONE_ORE};

    private MinesTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        hall(b, -6, 6, 0, 12, 69, MINE);        // lift station
        hall(b, -9, 9, 14, 38, 70, MINE);       // ore galleries
        hall(b, -8, 8, 41, 58, 70, MINE);       // grell's shaft
        hall(b, -10, 10, 61, 82, 71, MINE);     // blasting hall
        hall(b, -10, 10, 85, 104, 71, MINE);    // slag pits
        hall(b, -4, 4, 107, 112, 68, MINE);     // miners' rest
        hall(b, -13, 13, 114, 140, 76, MINE);   // foreman's delve
        doorway(b, 13, null, Material.STRIPPED_SPRUCE_LOG);
        doorway(b, 39, Material.IRON_BARS, Material.STRIPPED_SPRUCE_LOG);
        tunnel(b, 40);
        doorway(b, 59, Material.IRON_BARS, Material.STRIPPED_SPRUCE_LOG);
        tunnel(b, 60);
        doorway(b, 83, Material.IRON_BARS, Material.STRIPPED_SPRUCE_LOG);
        tunnel(b, 84);
        doorway(b, 105, Material.IRON_BARS, Material.STRIPPED_SPRUCE_LOG);
        tunnel(b, 106);
        doorway(b, 113, null, Material.STRIPPED_SPRUCE_LOG);
        for (int[] span : new int[][]{{14, 38, 9, 70}, {41, 58, 8, 70}, {61, 82, 10, 71}, {85, 104, 10, 71}, {114, 140, 13, 76}}) {
            ores(b, span[0], span[1], span[2]);
            frames(b, span[0], span[1], span[2], span[3]);
        }
        liftStation(b);
        galleries(b);
        shaft(b);
        blastingHall(b);
        slagPits(b);
        rest(b);
        delve(b);
        return b.freeze();
    }

    private static void tunnel(BuildCanvas b, int z) {
        for (int x = -2; x <= 2; x++) {
            b.block(x, 63, z, Material.GRAVEL);
            for (int y = 64; y <= 67; y++) b.block(x, y, z, Math.abs(x) == 2 || y == 67 ? Material.DEEPSLATE : Material.AIR);
            b.block(x, 68, z, Material.DEEPSLATE);
        }
        b.block(-1, 66, z, Material.STRIPPED_SPRUCE_LOG, "axis=x");
        b.block(1, 66, z, Material.STRIPPED_SPRUCE_LOG, "axis=x");
    }

    /** Ore glints in the walls, two blocks up so they read at eye level. */
    private static void ores(BuildCanvas b, int z1, int z2, int half) {
        for (int z = z1; z <= z2; z++) for (int y = 64; y <= 68; y++) for (int x : new int[]{-half - 1, half + 1})
            if (noise(x, z * 3 + y, 171) < .12) b.block(x, y, z, pick(ORES, x + y, z, 173));
    }

    /** Timber support frames just under the ceiling every few blocks, with rails underfoot. */
    private static void frames(BuildCanvas b, int z1, int z2, int half, int top) {
        for (int z = z1 + 3; z <= z2 - 2; z += 5) {
            for (int x = -half; x <= half; x++) b.block(x, top, z, Material.STRIPPED_SPRUCE_LOG, "axis=x");
            for (int y = 64; y < top; y++) {
                b.block(-half, y, z, Material.STRIPPED_SPRUCE_LOG, "axis=y");
                b.block(half, y, z, Material.STRIPPED_SPRUCE_LOG, "axis=y");
            }
        }
        for (int z = z1; z <= z2; z++) b.block(half - 2, 64, z, Material.RAIL, "shape=north_south");
    }

    private static void liftStation(BuildCanvas b) {
        // The lift cage at the back of the station.
        b.box(-3, 63, 0, 3, 63, 2, Material.SPRUCE_PLANKS);
        for (int y = 64; y <= 69; y++) for (int x : new int[]{-3, 3}) b.block(x, y, 0, Material.IRON_CHAIN, "axis=y");
        for (int x = -2; x <= 2; x++) b.block(x, 64, 0, Material.SPRUCE_FENCE);
        b.block(4, 64, 8, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        b.block(5, 64, 8, Material.STRIPPED_SPRUCE_LOG, "axis=z");
        for (int[] cart : new int[][]{{-5, 4}, {-5, 6}}) b.block(cart[0], 64, cart[1], Material.CHEST, "facing=east");
        b.block(-4, 64, 10, Material.SMITHING_TABLE);
    }

    /** Three alarm posts: a target on a deepslate post with the alarm bell hung beside it. */
    private static void galleries(BuildCanvas b) {
        for (int[] alarm : new int[][]{{-6, 19}, {5, 29}, {-6, 35}}) {
            b.block(alarm[0], 64, alarm[1], Material.POLISHED_DEEPSLATE);
            b.block(alarm[0], 65, alarm[1], Material.DEEPSLATE_BRICK_WALL);
            b.block(alarm[0], 66, alarm[1], Material.TARGET);
            b.block(alarm[0], 67, alarm[1], Material.LANTERN);
            b.block(alarm[0] + (alarm[0] < 0 ? -1 : 1), 66, alarm[1], Material.BELL, "attachment=single_wall,facing=" + (alarm[0] < 0 ? "east" : "west"));
        }
        for (int[] heap : new int[][]{{-6, 22}, {5, 31}, {-4, 36}}) {
            b.block(heap[0], 64, heap[1], Material.RAW_IRON_BLOCK);
            b.block(heap[0] + 1, 64, heap[1], Material.GRAVEL);
        }
    }

    private static void shaft(BuildCanvas b) {
        // A deep shaft down the middle of the room, fenced round.
        for (int x = 3; x <= 6; x++) for (int z = 46; z <= 52; z++) {
            for (int y = 56; y <= 63; y++) b.block(x, y, z, Material.AIR);
            b.block(x, 55, z, Material.MAGMA_BLOCK);
        }
        for (int x = 2; x <= 7; x++) for (int z = 45; z <= 53; z++)
            if (x == 2 || x == 7 || z == 45 || z == 53) b.block(x, 64, z, Material.SPRUCE_FENCE);
        for (int y = 56; y <= 69; y++) b.block(4, y, 49, Material.IRON_CHAIN, "axis=y");
    }

    /** Ore carts on two weighted plates; crates of blasting powder along the walls. */
    private static void blastingHall(BuildCanvas b) {
        for (int[] plate : new int[][]{{-6, 66}, {6, 66}}) {
            b.block(plate[0], 64, plate[1], Material.LIGHT_WEIGHTED_PRESSURE_PLATE, "power=0");
            for (int dz = 1; dz <= 4; dz++) b.block(plate[0], 64, plate[1] + dz, Material.RAIL, "shape=north_south");
            b.block(plate[0], 64, plate[1] + 5, Material.CHEST, "facing=north");
        }
        for (int z = 63; z <= 80; z += 4) for (int x : new int[]{-9, 9}) {
            b.block(x, 64, z, Material.RED_TERRACOTTA);
            b.block(x, 65, z, Material.BARREL, "facing=up");
        }
        for (int x = -3; x <= 3; x++) for (int z = 75; z <= 76; z++) b.block(x, 64, z, Material.COBBLED_DEEPSLATE_SLAB, "type=bottom");
    }

    /** Pools of glowing slag between walkways. */
    private static void slagPits(BuildCanvas b) {
        for (int x = -9; x <= 9; x++) for (int z = 86; z <= 103; z++) {
            if (Math.abs(x) <= 2) continue;
            double roll = noise(x, z, 181);
            if (roll < .16) b.block(x, 63, z, Material.MAGMA_BLOCK);
            else if (roll < .2) b.block(x, 63, z, Material.OBSIDIAN);
        }
        for (int[] pillar : new int[][]{{-6, 90}, {6, 90}, {-6, 99}, {6, 99}})
            for (int y = 64; y <= 71; y++) b.block(pillar[0], y, pillar[1], y % 3 == 0 ? Material.STRIPPED_SPRUCE_LOG : Material.COBBLED_DEEPSLATE, y % 3 == 0 ? "axis=y" : "");
    }

    private static void rest(BuildCanvas b) {
        b.block(3, 64, 109, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        b.block(-3, 64, 109, Material.BARREL, "facing=up");
        b.block(-3, 64, 111, Material.CRAFTING_TABLE);
    }

    /** The Foreman's delve: a great cavern with stone pillars that can collapse. */
    private static void delve(BuildCanvas b) {
        for (int[] pillar : new int[][]{{-7, 121}, {7, 121}, {-7, 133}, {7, 133}})
            for (int y = 64; y <= 76; y++) for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++)
                b.block(pillar[0] + dx, y, pillar[1] + dz, y % 4 == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_BRICKS);
        // The Foreman's office: a raised timber platform at the back.
        b.box(-4, 64, 134, 4, 64, 138, Material.SPRUCE_PLANKS);
        for (int x = -4; x <= 4; x++) b.block(x, 64, 133, Material.SPRUCE_STAIRS, "facing=south,half=bottom,shape=straight");
        b.block(-3, 65, 137, Material.LECTERN, "facing=south,has_book=false");
        b.block(3, 65, 137, Material.ANVIL, "facing=north");
        b.box(-1, 63, 139, 1, 63, 140, Material.RAW_GOLD_BLOCK);
    }
}
