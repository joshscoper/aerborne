package net.nerdsmp.paper.reach;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settles a finished plan the way the game would if every block had been placed by hand. Generated blocks never get
 * neighbour updates, so without this a roof's corner stairs stay straight and notch, fences and walls stand as lone
 * posts, and panes show as thin sticks. Each stair gets its corner shape, and each fence, wall, pane and bar run
 * joins whatever it touches: its own kind, gates in line, and the solid faces of full blocks (the terrain's included).
 */
final class ReachSettle {
    private ReachSettle() { }

    private static final String[] FACES = {"north", "east", "south", "west"};
    private static final int[][] STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /** A parsed state: the block's name without its namespace, and its properties in order. */
    record State(String name, Map<String, String> props) {
        static State parse(String state) {
            int open = state.indexOf('[');
            String id = open < 0 ? state : state.substring(0, open);
            String name = id.startsWith("minecraft:") ? id.substring(10) : id;
            Map<String, String> props = new LinkedHashMap<>();
            if (open >= 0) for (String part : state.substring(open + 1, state.length() - 1).split(",")) {
                if (part.isEmpty()) continue;
                String[] kv = part.split("=", 2);
                props.put(kv[0], kv.length > 1 ? kv[1] : "");
            }
            return new State(name, props);
        }

        String write() {
            if (props.isEmpty()) return "minecraft:" + name;
            StringBuilder b = new StringBuilder("minecraft:").append(name).append('[');
            int i = 0;
            for (Map.Entry<String, String> e : props.entrySet()) b.append(i++ > 0 ? "," : "").append(e.getKey()).append('=').append(e.getValue());
            return b.append(']').toString();
        }
    }

    static boolean stairs(String n) { return n.endsWith("_stairs"); }
    static boolean fence(String n) { return n.endsWith("_fence") && !n.equals("nether_brick_fence"); }
    static boolean gate(String n) { return n.endsWith("_fence_gate"); }
    static boolean wall(String n) { return n.endsWith("_wall"); }
    static boolean pane(String n) { return n.endsWith("glass_pane") || n.equals("iron_bars"); }

    private static final String[] PARTIAL = {"_slab", "_stairs", "_fence", "_wall", "pane", "bars", "door", "sign", "banner",
        "lantern", "chain", "carpet", "leaves", "candle", "pot", "torch", "ladder", "vine", "button", "plate", "rail", "campfire",
        "_bed", "bell", "anvil", "grindstone", "cauldron", "lectern", "brewing", "rod", "sapling", "azalea", "rose", "peony",
        "lilac", "grass", "fern", "dandelion", "poppy", "tulip", "bluet", "orchid", "allium", "daisy", "cornflower", "lily",
        "wheat", "carrots", "potatoes", "beetroots", "air", "water", "lava", "chest", "head", "skull", "scaffolding", "gate",
        "composter", "hopper", "lever", "tripwire", "string", "kelp", "seagrass", "cake", "snow", "frame", "mushroom", "berry",
        "dripleaf", "moss_carpet", "lichen", "roots", "cobweb", "hay"};

    /** True for a block whose sides are full, sturdy faces that fences, walls and panes join to. */
    static boolean sturdy(String n) {
        if (n.equals("grass_block") || n.equals("hay_block") || n.equals("snow_block") || n.equals("mushroom_stem")
            || n.endsWith("_mushroom_block") || n.equals("glowstone") || n.equals("sea_lantern") || n.equals("redstone_lamp")) return true;
        for (String p : PARTIAL) if (n.contains(p)) return false;
        return true;
    }

    /** Settles every stair, fence, wall, pane and bar in the canvas against its neighbours. */
    static void settle(ReachCanvas canvas, ReachTerrain terrain) {
        Map<String, State> parsed = new HashMap<>();
        List<int[]> targets = new ArrayList<>();
        canvas.forEachBlock((x, y, z, spec) -> {
            String s = spec.state();
            String n = name(s);
            if (stairs(n) || fence(n) || wall(n) || pane(n)) targets.add(new int[] {x, y, z});
        });
        Map<Long, int[]> where = new HashMap<>();
        Map<Long, String> updates = new HashMap<>();
        for (int[] p : targets) {
            String now = canvas.get(p[0], p[1], p[2]).state();
            State st = parsed.computeIfAbsent(now, State::parse);
            String n = st.name();
            State out = new State(n, new LinkedHashMap<>(st.props()));
            if (stairs(n)) stairShape(canvas, terrain, p, out);
            else if (fence(n)) join(canvas, terrain, p, out, Kind.FENCE);
            else if (wall(n)) join(canvas, terrain, p, out, Kind.WALL);
            else join(canvas, terrain, p, out, Kind.PANE);
            String next = out.write();
            if (!next.equals(now)) {
                long k = pos(p[0], p[1], p[2]);
                updates.put(k, next);
                where.put(k, p);
            }
        }
        updates.forEach((k, state) -> {
            int[] p = where.get(k);
            canvas.set(p[0], p[1], p[2], new ReachCanvas.Spec(state));
        });
    }

    private enum Kind { FENCE, WALL, PANE }

    private static long pos(int x, int y, int z) { return ((long) x & 0x3FFFFF) << 42 | ((long) z & 0x3FFFFF) << 20 | (y & 0xFFFFF); }

    private static String name(String state) {
        int open = state.indexOf('[');
        String id = open < 0 ? state : state.substring(0, open);
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    /** The name of the block at a point: the plan's own, else the terrain's (solid rock, water or air). */
    private static String at(ReachCanvas canvas, ReachTerrain terrain, int x, int y, int z) {
        ReachCanvas.Spec s = canvas.get(x, y, z);
        if (s != null) return s.state();
        ReachTerrain.Column c = terrain.column(x, z);
        if (!c.land()) return "minecraft:air";
        if (y <= c.top() && y >= c.bottom()) return "minecraft:stone";
        if (c.wet() && y <= c.water()) return "minecraft:water";
        return "minecraft:air";
    }

    private static String prop(String state, String key) {
        int i = state.indexOf(key + "=");
        if (i < 0) return "";
        int end = state.indexOf(',', i);
        if (end < 0) end = state.indexOf(']', i);
        return state.substring(i + key.length() + 1, end < 0 ? state.length() : end);
    }

    private static int face(String f) {
        for (int i = 0; i < 4; i++) if (FACES[i].equals(f)) return i;
        return -1;
    }

    /** The vanilla stair rule: an outer corner where the stair behind turns, an inner corner where the one in front does. */
    private static void stairShape(ReachCanvas canvas, ReachTerrain terrain, int[] p, State st) {
        int f = face(st.props().getOrDefault("facing", "north"));
        if (f < 0) return;
        String half = st.props().getOrDefault("half", "bottom");
        String shape = "straight";
        String behind = at(canvas, terrain, p[0] + STEP[f][0], p[1], p[2] + STEP[f][1]);
        if (stairs(name(behind)) && half.equals(prop(behind, "half"))) {
            int bf = face(prop(behind, "facing"));
            if (bf >= 0 && (bf & 1) != (f & 1) && canTake(canvas, terrain, p, f, half, (bf + 2) & 3))
                shape = bf == ((f + 3) & 3) ? "outer_left" : "outer_right";
        }
        if (shape.equals("straight")) {
            String front = at(canvas, terrain, p[0] - STEP[f][0], p[1], p[2] - STEP[f][1]);
            if (stairs(name(front)) && half.equals(prop(front, "half"))) {
                int ff = face(prop(front, "facing"));
                if (ff >= 0 && (ff & 1) != (f & 1) && canTake(canvas, terrain, p, f, half, ff))
                    shape = ff == ((f + 3) & 3) ? "inner_left" : "inner_right";
            }
        }
        st.props().put("shape", shape);
    }

    private static boolean canTake(ReachCanvas canvas, ReachTerrain terrain, int[] p, int f, String half, int dir) {
        String n = at(canvas, terrain, p[0] + STEP[dir][0], p[1], p[2] + STEP[dir][1]);
        return !stairs(name(n)) || face(prop(n, "facing")) != f || !half.equals(prop(n, "half"));
    }

    private static void join(ReachCanvas canvas, ReachTerrain terrain, int[] p, State st, Kind kind) {
        boolean[] joined = new boolean[4];
        String above = at(canvas, terrain, p[0], p[1] + 1, p[2]);
        String aboveName = name(above);
        for (int d = 0; d < 4; d++) {
            String n = at(canvas, terrain, p[0] + STEP[d][0], p[1], p[2] + STEP[d][1]);
            String nn = name(n);
            boolean gateInLine = gate(nn) && (face(prop(n, "facing")) & 1) != (d & 1);
            joined[d] = switch (kind) {
                case FENCE -> fence(nn) || gateInLine || sturdy(nn);
                case WALL -> wall(nn) || pane(nn) || gateInLine || sturdy(nn);
                case PANE -> pane(nn) || wall(nn) || sturdy(nn);
            };
        }
        for (int d = 0; d < 4; d++) {
            if (kind == Kind.WALL) st.props().put(FACES[d], joined[d] ? (sturdy(aboveName) ? "tall" : "low") : "none");
            else st.props().put(FACES[d], String.valueOf(joined[d]));
        }
        if (kind == Kind.WALL) {
            boolean straight = joined[0] && joined[2] && !joined[1] && !joined[3] || joined[1] && joined[3] && !joined[0] && !joined[2];
            boolean post = aboveName.contains("lantern") || aboveName.contains("torch") || aboveName.contains("sign") || aboveName.contains("banner")
                || aboveName.contains("campfire") || aboveName.contains("chain") || wall(aboveName) && prop(above, "up").equals("true");
            st.props().put("up", String.valueOf(!straight || post));
        }
    }
}
