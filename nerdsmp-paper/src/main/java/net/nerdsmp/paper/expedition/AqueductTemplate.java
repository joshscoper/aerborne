package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.noise;
import static net.nerdsmp.paper.expedition.SampleKit.pick;
import static net.nerdsmp.paper.expedition.SampleTemplates.doorway;
import static net.nerdsmp.paper.expedition.SampleTemplates.hall;

/**
 * The Sunken Aqueduct: the Intake Steps (spawn), the Sluice Hall with its valves and channel, the
 * Cistern, Floodgate Control with its counterweight plates, the Pumphouse, and the Tidewarden's
 * Basin. Runs south (+Z); floors at Y63, doorways three wide at X -1..1.
 */
final class AqueductTemplate {
    static final SampleTemplates.Palette WATERWORKS = new SampleTemplates.Palette(
        new Material[]{Material.PRISMARINE_BRICKS, Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE},
        new Material[]{Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.PRISMARINE_BRICKS, Material.CRACKED_STONE_BRICKS},
        Material.DARK_PRISMARINE, "", Material.STONE_BRICKS, Material.SEA_LANTERN, "");

    private AqueductTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        hall(b, -6, 6, 0, 12, 70, WATERWORKS);       // intake steps
        hall(b, -8, 8, 14, 40, 70, WATERWORKS);      // sluice hall
        hall(b, -10, 10, 43, 65, 72, WATERWORKS);    // cistern
        hall(b, -9, 9, 68, 88, 70, WATERWORKS);      // floodgate control
        hall(b, -4, 4, 90, 95, 68, WATERWORKS);      // pumphouse
        hall(b, -12, 12, 97, 122, 74, WATERWORKS);   // tidewarden's basin
        doorway(b, 13, null, Material.CHISELED_STONE_BRICKS);
        doorway(b, 41, Material.IRON_BARS, Material.CHISELED_STONE_BRICKS);
        corridor(b, 42);
        doorway(b, 66, Material.IRON_BARS, Material.CHISELED_STONE_BRICKS);
        corridor(b, 67);
        doorway(b, 89, Material.IRON_BARS, Material.CHISELED_STONE_BRICKS);
        doorway(b, 96, null, Material.CHISELED_STONE_BRICKS);
        intake(b);
        sluiceHall(b);
        cistern(b);
        floodgate(b);
        pumphouse(b);
        basin(b);
        SampleChests.place(b, "sunken_aqueduct");
        return b.freeze();
    }

    /** A one-block passage between two halls whose walls don't touch. */
    private static void corridor(BuildCanvas b, int z) {
        for (int x = -2; x <= 2; x++) {
            b.block(x, 63, z, Material.PRISMARINE_BRICKS);
            for (int y = 64; y <= 67; y++) b.block(x, y, z, Math.abs(x) == 2 || y == 67 ? Material.STONE_BRICKS : Material.AIR);
            b.block(x, 68, z, Material.STONE_BRICKS);
        }
    }

    private static void intake(BuildCanvas b) {
        // Steps down from the intake, a waterfall pouring from a grate in the back wall.
        for (int x = -3; x <= 3; x++) b.block(x, 64, 0, Material.STONE_BRICK_STAIRS, "facing=north,half=bottom,shape=straight");
        for (int y = 64; y <= 69; y++) b.block(-6, y, 6, Material.WATER, "level=0");
        b.box(-6, 63, 5, -5, 63, 7, Material.DARK_PRISMARINE);
        b.block(-6, 70, 6, Material.IRON_BARS, "east=true,west=true");
        b.block(4, 64, 8, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        b.block(5, 64, 8, Material.STRIPPED_SPRUCE_LOG, "axis=z");
        for (int[] crate : new int[][]{{4, 2}, {5, 2}, {5, 3}}) b.block(crate[0], 64, crate[1], Material.BARREL, "facing=up");
        b.block(-4, 64, 10, Material.ANVIL, "facing=north");
    }

    /** The channel runs along the east side; three valves on the west wall drain it. */
    private static void sluiceHall(BuildCanvas b) {
        for (int z = 14; z <= 40; z++) {
            for (int x = 4; x <= 7; x++) {
                b.block(x, 62, z, Material.DARK_PRISMARINE);
                b.block(x, 63, z, Material.WATER, "level=0");
            }
            b.block(3, 64, z, z % 4 == 0 ? Material.PRISMARINE_WALL : Material.AIR);
        }
        for (int z : new int[]{19, 25, 31}) {
            b.block(-9, 65, z, Material.CHISELED_STONE_BRICKS);
            b.block(-8, 65, z, Material.STONE_BUTTON, "face=wall,facing=east,powered=false");
            b.block(-8, 66, z, Material.LIGHTNING_ROD, "facing=up");
        }
        // Pipes along the ceiling.
        for (int z = 15; z <= 39; z++) b.block(-6, 69, z, Material.WAXED_COPPER_GRATE);
    }

    private static void cistern(BuildCanvas b) {
        // Round pillars and shallow pools between them.
        for (int[] pillar : new int[][]{{-6, 48}, {6, 48}, {-6, 60}, {6, 60}})
            for (int y = 64; y <= 72; y++) b.block(pillar[0], y, pillar[1], Material.DARK_PRISMARINE);
        for (int x = -9; x <= 9; x++) for (int z = 44; z <= 64; z++) {
            if (Math.abs(x) <= 2) continue;
            if (noise(x, z, 151) < .22) {
                b.block(x, 62, z, Material.PRISMARINE);
                b.block(x, 63, z, Material.WATER, "level=0");
            }
        }
        for (int[] grate : new int[][]{{-3, 50}, {3, 58}, {0, 54}}) b.block(grate[0], 63, grate[1], Material.WAXED_COPPER_GRATE);
    }

    /** Two heavy plates on raised plinths: weigh them down with anything thrown. */
    private static void floodgate(BuildCanvas b) {
        b.block(3, 64, 71, Material.BARREL, "facing=up"); // Supply crate: counterweights.
        for (int[] plate : new int[][]{{-6, 75}, {6, 75}}) {
            b.box(plate[0] - 1, 64, plate[1] - 1, plate[0] + 1, 64, plate[1] + 1, Material.POLISHED_DIORITE);
            b.block(plate[0], 65, plate[1], Material.HEAVY_WEIGHTED_PRESSURE_PLATE, "power=0");
        }
        // The floodgate machinery: a great wheel on the west wall, chains to the ceiling.
        for (int dz = -2; dz <= 2; dz++) for (int y = 64; y <= 68; y++)
            if (Math.abs(dz) + Math.abs(y - 66) == 2) b.block(-9, y, 80 + dz, Material.OXIDIZED_COPPER);
        b.block(-9, 66, 80, Material.OXIDIZED_CUT_COPPER);
        for (int y = 65; y <= 70; y++) b.block(7, y, 80, Material.IRON_CHAIN, "axis=y");
        for (int z = 70; z <= 86; z += 8) for (int x : new int[]{-8, 8}) b.block(x, 64, z, Material.BARREL, "facing=up");
    }

    private static void pumphouse(BuildCanvas b) {
        b.block(3, 64, 92, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        b.block(-3, 64, 93, Material.BLAST_FURNACE, "facing=east");
        b.block(-3, 65, 93, Material.LIGHTNING_ROD, "facing=up");
    }

    /** A round basin: a moat of water around the fighting floor, fountains at the corners. */
    private static void basin(BuildCanvas b) {
        for (int x = -12; x <= 12; x++) for (int z = 97; z <= 122; z++) {
            double dx = x, dz = z - 110;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance >= 10.5 && distance <= 12.5 && !(Math.abs(x) <= 1 && z < 105)) {
                b.block(x, 62, z, Material.DARK_PRISMARINE);
                b.block(x, 63, z, Material.WATER, "level=0");
            } else if (distance < 10.5) b.block(x, 63, z, pick(new Material[]{Material.PRISMARINE_BRICKS, Material.PRISMARINE, Material.PRISMARINE_BRICKS}, x, z, 161));
        }
        for (int[] fountain : new int[][]{{-11, 98}, {11, 98}, {-11, 121}, {11, 121}}) {
            for (int y = 64; y <= 66; y++) b.block(fountain[0], y, fountain[1], Material.DARK_PRISMARINE);
            b.block(fountain[0], 67, fountain[1], Material.SEA_LANTERN);
        }
        b.box(-2, 64, 117, 2, 64, 119, Material.DARK_PRISMARINE);
        b.block(0, 65, 118, Material.CONDUIT);
        b.box(-1, 63, 120, 1, 63, 121, Material.SEA_LANTERN);
    }
}
