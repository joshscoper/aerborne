package net.nerdsmp.paper.reach;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes every block that hangs, leans or stands on something have that something, the way a builder would by hand.
 * A lantern hangs on a chain from the ceiling over it, or from a bracket out of the wall beside it, or else stands
 * on what is below it. A chain runs up to the beam it hangs from. A ladder leans on a wall; a pot sits on a window
 * box. What cannot be held up any way is taken away rather than left in the air. Runs on every plan before it is
 * settled, so brackets join the walls they come out of.
 */
final class ReachSupport {
    private ReachSupport() { }

    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final String[] FACING = {"north", "east", "south", "west"};

    static void secure(ReachCanvas canvas, ReachTerrain terrain) {
        List<int[]> lanterns = new ArrayList<>(), chains = new ArrayList<>(), ladders = new ArrayList<>(), props = new ArrayList<>(), links = new ArrayList<>();
        canvas.forEachBlock((x, y, z, spec) -> {
            String n = name(spec.state());
            if (n.endsWith("lantern") && !n.equals("sea_lantern")) lanterns.add(new int[] {x, y, z});
            else if ((n.equals("iron_chain") || n.equals("chain")) && spec.state().contains("axis=y")) chains.add(new int[] {x, y, z});
            else if (n.equals("iron_chain") || n.equals("chain")) links.add(new int[] {x, y, z});
            else if (n.equals("ladder")) ladders.add(new int[] {x, y, z});
            else if (floorProp(n)) props.add(new int[] {x, y, z});
        });
        // Chains first, top down, so a lantern under a chain finds it already hung.
        chains.sort((a, b) -> Integer.compare(b[1], a[1]));
        for (int[] p : chains) chain(canvas, terrain, p[0], p[1], p[2]);
        // Until nothing moves: turning or taking one link can leave its neighbour holding on to nothing.
        for (int pass = 0; pass < 12; pass++) {
            boolean changed = false;
            for (int[] p : links) {
                ReachCanvas.Spec before = canvas.get(p[0], p[1], p[2]);
                if (before == null || !name(before.state()).contains("chain")) continue;
                link(canvas, terrain, p[0], p[1], p[2]);
                changed |= !before.equals(canvas.get(p[0], p[1], p[2]));
            }
            if (!changed) break;
        }
        for (int[] p : lanterns) lantern(canvas, terrain, p[0], p[1], p[2]);
        for (int[] p : ladders) ladder(canvas, terrain, p[0], p[1], p[2]);
        for (int[] p : props) prop(canvas, terrain, p[0], p[1], p[2]);
    }

    /** A chain laid along a span holds on at its ends; one that holds on to nothing that way is turned to what it does touch. */
    private static void link(ReachCanvas canvas, ReachTerrain terrain, int x, int y, int z) {
        String state = canvas.get(x, y, z).state();
        if (state.contains("axis=y")) return;
        boolean alongX = state.contains("axis=x");
        if (alongX ? !empty(at(canvas, terrain, x - 1, y, z)) || !empty(at(canvas, terrain, x + 1, y, z))
                   : !empty(at(canvas, terrain, x, y, z - 1)) || !empty(at(canvas, terrain, x, y, z + 1))) return;
        String to = !alongX && (!empty(at(canvas, terrain, x - 1, y, z)) || !empty(at(canvas, terrain, x + 1, y, z))) ? "x"
            : alongX && (!empty(at(canvas, terrain, x, y, z - 1)) || !empty(at(canvas, terrain, x, y, z + 1))) ? "z"
            : !empty(at(canvas, terrain, x, y - 1, z)) || !empty(at(canvas, terrain, x, y + 1, z)) ? "y" : null;
        canvas.set(x, y, z, new ReachCanvas.Spec(to == null ? "minecraft:air" : state.replaceAll("axis=[xyz]", "axis=" + to)));
    }

    /** A chain hangs from something: run it up to the beam within six blocks over it, or take the loose run away. */
    private static void chain(ReachCanvas canvas, ReachTerrain terrain, int x, int y, int z) {
        ReachCanvas.Spec self = canvas.get(x, y, z);
        if (self == null || !name(self.state()).contains("chain")) return;
        if (!empty(at(canvas, terrain, x, y + 1, z))) return;
        for (int up = 2; up <= 6; up++) {
            String s = at(canvas, terrain, x, y + up, z);
            if (empty(s)) continue;
            if (!holds(s)) break;
            for (int k = 1; k < up; k++) canvas.set(x, y + k, z, self);
            return;
        }
        // Nothing to hang from: the run and whatever hangs on it go.
        int k = y;
        while (true) {
            ReachCanvas.Spec s = canvas.get(x, k, z);
            if (s == null) break;
            String n = name(s.state());
            if (!n.contains("chain") && !(n.endsWith("lantern") && s.state().contains("hanging=true"))) break;
            canvas.set(x, k, z, new ReachCanvas.Spec("minecraft:air"));
            k--;
        }
    }

    private static void lantern(ReachCanvas canvas, ReachTerrain terrain, int x, int y, int z) {
        ReachCanvas.Spec self = canvas.get(x, y, z);
        if (self == null) return;
        String state = self.state(), n = name(state);
        if (!n.endsWith("lantern")) return;
        boolean hanging = state.contains("hanging=true");
        String above = at(canvas, terrain, x, y + 1, z), below = at(canvas, terrain, x, y - 1, z);
        if (hanging && holds(above) || !hanging && stands(below)) return;
        // A ceiling within three over it: hang it on a chain.
        for (int up = 2; up <= 4; up++) {
            String s = at(canvas, terrain, x, y + up, z);
            if (empty(s)) continue;
            if (!holds(s)) break;
            for (int k = 1; k < up; k++) canvas.set(x, y + k, z, new ReachCanvas.Spec("minecraft:iron_chain[axis=y]"));
            canvas.set(x, y, z, new ReachCanvas.Spec(withHanging(state, true)));
            return;
        }
        // A wall beside it at the height over it: a bracket out of the wall to hang it from.
        for (int[] d : SIDES) {
            if (sturdy(at(canvas, terrain, x + d[0], y + 1, z + d[1]))) {
                canvas.set(x, y + 1, z, new ReachCanvas.Spec("minecraft:dark_oak_fence"));
                canvas.set(x, y, z, new ReachCanvas.Spec(withHanging(state, true)));
                return;
            }
        }
        if (holds(above)) { canvas.set(x, y, z, new ReachCanvas.Spec(withHanging(state, true))); return; }
        if (stands(below)) { canvas.set(x, y, z, new ReachCanvas.Spec(withHanging(state, false))); return; }
        // A floor within three under it: a post to stand it on.
        for (int down = 2; down <= 4; down++) {
            String s = at(canvas, terrain, x, y - down, z);
            if (empty(s)) continue;
            if (!stands(s)) break;
            for (int k = 1; k < down; k++) canvas.set(x, y - k, z, new ReachCanvas.Spec("minecraft:dark_oak_fence"));
            canvas.set(x, y, z, new ReachCanvas.Spec(withHanging(state, false)));
            return;
        }
        canvas.set(x, y, z, new ReachCanvas.Spec("minecraft:air"));
    }

    /** A ladder leans on the wall behind it; turned to face away from a wall beside it, or given a board to lean on. */
    private static void ladder(ReachCanvas canvas, ReachTerrain terrain, int x, int y, int z) {
        String state = canvas.get(x, y, z).state();
        int f = face(prop(state, "facing"));
        if (f < 0) return;
        int[] back = SIDES[(f + 2) % 4];
        if (sturdy(at(canvas, terrain, x + back[0], y, z + back[1]))) return;
        for (int i = 0; i < 4; i++) {
            int[] d = SIDES[i];
            if (sturdy(at(canvas, terrain, x + d[0], y, z + d[1]))) {
                canvas.set(x, y, z, new ReachCanvas.Spec("minecraft:ladder[facing=" + FACING[(i + 2) % 4] + ",waterlogged=false]"));
                return;
            }
        }
        canvas.set(x + back[0], y, z + back[1], new ReachCanvas.Spec("minecraft:spruce_planks"));
    }

    /** A pot by a wall sits on a window box; any other small thing on nothing is taken away. */
    private static void prop(ReachCanvas canvas, ReachTerrain terrain, int x, int y, int z) {
        String state = canvas.get(x, y, z).state(), n = name(state);
        if (!empty(at(canvas, terrain, x, y - 1, z))) return;
        if (n.startsWith("potted_") || n.equals("flower_pot")) {
            for (int i = 0; i < 4; i++) {
                int[] d = SIDES[i];
                if (sturdy(at(canvas, terrain, x + d[0], y - 1, z + d[1]))) {
                    canvas.set(x, y - 1, z, new ReachCanvas.Spec("minecraft:spruce_trapdoor[facing=" + FACING[(i + 2) % 4] + ",half=top,open=false,powered=false,waterlogged=false]"));
                    return;
                }
            }
        }
        if (n.endsWith("_carpet") || n.equals("snow")) {
            canvas.set(x, y, z, new ReachCanvas.Spec("minecraft:air"));
            return;
        }
        canvas.set(x, y, z, new ReachCanvas.Spec("minecraft:air"));
    }

    static boolean floorProp(String n) {
        return n.equals("torch") || n.equals("soul_torch") || n.equals("candle") || n.endsWith("_candle") || n.endsWith("campfire")
            || n.startsWith("potted_") || n.equals("flower_pot") || n.endsWith("_carpet") || n.endsWith("_pressure_plate")
            || n.equals("short_grass") || n.equals("fern") || n.equals("dead_bush") || n.endsWith("_tulip") || n.equals("poppy")
            || n.equals("dandelion") || n.equals("allium") || n.equals("azure_bluet") || n.equals("oxeye_daisy") || n.equals("cornflower")
            || n.equals("lily_of_the_valley") || n.equals("blue_orchid") || n.endsWith("_sapling") || n.equals("sweet_berry_bush")
            || n.equals("snow") || n.equals("sea_pickle") || n.equals("pointed_dripstone");
    }

    // ------------------------------------------------------------------ reading blocks

    static String at(ReachCanvas canvas, ReachTerrain terrain, int x, int y, int z) {
        ReachCanvas.Spec s = canvas.get(x, y, z);
        if (s != null) return s.state();
        ReachTerrain.Column c = terrain.column(x, z);
        if (!c.land()) return "minecraft:air";
        if (y <= c.top() && y >= c.bottom()) return "minecraft:stone";
        if (c.wet() && y <= c.water()) return "minecraft:water";
        return "minecraft:air";
    }

    static String name(String state) {
        int open = state.indexOf('[');
        String id = open < 0 ? state : state.substring(0, open);
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    private static String prop(String state, String key) {
        int i = state.indexOf(key + "=");
        if (i < 0) return "";
        int end = state.indexOf(',', i);
        if (end < 0) end = state.indexOf(']', i);
        return state.substring(i + key.length() + 1, end < 0 ? state.length() : end);
    }

    private static int face(String f) {
        for (int i = 0; i < 4; i++) if (FACING[i].equals(f)) return i;
        return -1;
    }

    /** Nothing there to hold anything: air, water, lava or a thing that is itself held up. */
    static boolean empty(String s) {
        String n = name(s);
        return n.equals("air") || n.equals("cave_air") || n.equals("water") || n.equals("lava") || n.equals("light")
            || n.endsWith("lantern") && !n.equals("sea_lantern") || n.endsWith("torch") || n.endsWith("candle") || n.endsWith("_sign")
            || n.endsWith("_banner") || n.endsWith("_carpet") || floorProp(n) || n.equals("end_gateway") || n.equals("vine");
    }

    /** Something a lantern or chain can hang from: any block with an underside, chains and fences included. */
    static boolean holds(String s) { return !empty(s); }

    /** Something a lantern can stand on. */
    static boolean stands(String s) { return !empty(s) && !name(s).equals("iron_chain") && !name(s).equals("chain"); }

    /** A full wall face a bracket or ladder can be fixed to. */
    static boolean sturdy(String s) {
        if (empty(s)) return false;
        String n = name(s);
        return ReachSettle.sturdy(n) && !n.endsWith("leaves") && !n.contains("glass") && !n.endsWith("trapdoor");
    }

    private static String withHanging(String state, boolean hanging) {
        if (state.contains("hanging=")) return state.replaceAll("hanging=(true|false)", "hanging=" + hanging);
        int open = state.indexOf('[');
        return open < 0 ? state + "[hanging=" + hanging + "]" : state.replace("[", "[hanging=" + hanging + ",");
    }
}
