package net.nerdsmp.paper.reach;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every block that hangs, leans or stands on something has that something: lanterns, chains, wall signs, banners,
 * ladders, torches, candles and plants, doors in whole pairs, and no piece of a build adrift in the air.
 */
class ReachSupportTest {
    private static final ReachTerrain TERRAIN = new ReachTerrain();
    private static final ReachFeatures FEATURES = new ReachFeatures(TERRAIN, ReachBuilds.all(TERRAIN));
    /** Builds that float by design: the Seal Spire's rings and the moored skyships ride on nothing. */
    private static final Set<String> FLOATING_BY_DESIGN = Set.of("seal_spire");

    /** Face and edge neighbours: blocks that meet along an edge read as joined in game, as a diagonal brace does. */
    static final int[][] EDGES = edges();

    static int[][] edges() {
        List<int[]> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            int n = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
            if (n == 1 || n == 2) out.add(new int[] {dx, dy, dz});
        }
        return out.toArray(new int[0][]);
    }

    static long key(int x, int y, int z) { return ((long) x & 0x3FFFFF) << 42 | ((long) z & 0x3FFFFF) << 20 | (y & 0xFFFFF); }

    /** A feature's plan as one map, so neighbours can be read quickly. */
    static Map<Long, String> materialize(ReachCanvas.Frozen plan) {
        Map<Long, String> out = new HashMap<>();
        for (int cx = plan.minX >> 4; cx <= plan.maxX >> 4; cx++) for (int cz = plan.minZ >> 4; cz <= plan.maxZ >> 4; cz++) {
            int bx = cx << 4, bz = cz << 4;
            plan.forEach(cx, cz, cell -> out.put(key(bx + cell.lx(), cell.y(), bz + cell.lz()), cell.spec().state()));
        }
        return out;
    }

    static String at(Map<Long, String> plan, int x, int y, int z) {
        String s = plan.get(key(x, y, z));
        if (s != null) return s;
        ReachTerrain.Column c = TERRAIN.column(x, z);
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

    static String prop(String state, String key) {
        int i = state.indexOf(key + "=");
        if (i < 0) return "";
        int end = state.indexOf(',', i);
        if (end < 0) end = state.indexOf(']', i);
        return state.substring(i + key.length() + 1, end < 0 ? state.length() : end);
    }

    static boolean empty(String state) {
        String n = name(state);
        return n.equals("air") || n.equals("cave_air") || n.equals("water") || n.equals("lava");
    }

    /** What a problem at a block is, or null: the generic kind, so the report groups by cause. */
    static String fault(Map<Long, String> plan, int x, int y, int z, String state) {
        String n = name(state);
        if (n.equals("end_gateway") || n.equals("air") || n.equals("cave_air") || n.equals("water") || n.equals("light")) return null;
        String above = at(plan, x, y + 1, z), below = at(plan, x, y - 1, z);
        if (n.endsWith("lantern") && !n.contains("sea_lantern") && !n.contains("jack_o")) {
            if (prop(state, "hanging").equals("true")) return empty(above) || name(above).endsWith("leaves") ? "lantern hanging from nothing" : null;
            return empty(below) ? "lantern standing on nothing" : null;
        }
        if (n.equals("iron_chain") || n.equals("chain")) {
            if (!prop(state, "axis").equals("y")) {
                boolean x1 = prop(state, "axis").equals("x");
                String a = x1 ? at(plan, x - 1, y, z) : at(plan, x, y, z - 1), b = x1 ? at(plan, x + 1, y, z) : at(plan, x, y, z + 1);
                return empty(a) && empty(b) ? "chain tied to nothing" : null;
            }
            return empty(above) && empty(below) ? "chain hanging in the air" : empty(above) ? "chain not hung from anything" : null;
        }
        if (n.endsWith("_wall_sign") || n.endsWith("_wall_banner") || n.endsWith("wall_torch") || n.equals("ladder")
            || n.endsWith("_wall_hanging_sign") || n.equals("tripwire_hook") || n.endsWith("_wall_head") || n.endsWith("_wall_skull")
            || (n.endsWith("_button") || n.equals("lever")) && prop(state, "face").equals("wall")) {
            String f = prop(state, "facing");
            int dx = f.equals("east") ? -1 : f.equals("west") ? 1 : 0, dz = f.equals("south") ? -1 : f.equals("north") ? 1 : 0;
            if (n.endsWith("_wall_hanging_sign")) return null;
            return empty(at(plan, x + dx, y, z + dz)) ? n.replace("dark_oak_", "").replace("spruce_", "").replace("birch_", "")
                .replace("oak_", "").replace("crimson_", "").replace("soul_", "") + " on no wall" : null;
        }
        if (n.endsWith("_hanging_sign")) return empty(above) ? "hanging sign hung from nothing" : null;
        if (n.endsWith("_door")) {
            boolean lower = prop(state, "half").equals("lower");
            String other = lower ? above : below;
            if (!name(other).equals(n)) return "door missing its other half";
            return lower && empty(below) ? "door over a drop" : null;
        }
        boolean floorProp = n.equals("torch") || n.equals("soul_torch") || n.equals("candle") || n.endsWith("_candle") || n.endsWith("campfire")
            || n.startsWith("potted_") || n.equals("flower_pot") || n.endsWith("_carpet") || n.endsWith("_pressure_plate") || n.endsWith("rail")
            || n.endsWith("_banner") || n.endsWith("_sign") || n.equals("lectern") || n.equals("brewing_stand") || n.endsWith("anvil")
            || n.equals("short_grass") || n.equals("tall_grass") || n.equals("fern") || n.equals("large_fern") || n.equals("dead_bush")
            || n.endsWith("_tulip") || n.equals("poppy") || n.equals("dandelion") || n.equals("allium") || n.equals("azure_bluet")
            || n.equals("oxeye_daisy") || n.equals("cornflower") || n.equals("lily_of_the_valley") || n.equals("blue_orchid")
            || n.endsWith("_sapling") || n.equals("sweet_berry_bush") || n.equals("wheat") || n.equals("carrots") || n.equals("potatoes")
            || n.equals("beetroots") || n.equals("snow") || n.equals("sea_pickle") || n.equals("lily_pad") || n.equals("pointed_dripstone")
            && prop(state, "vertical_direction").equals("up") || n.equals("bell") && prop(state, "attachment").equals("floor")
            || n.endsWith("concrete_powder") || n.equals("sand") || n.equals("red_sand") || n.equals("gravel") || n.startsWith("suspicious_")
            || n.equals("rose_bush") || n.equals("lilac") || n.equals("peony") || n.equals("sunflower");
        if (floorProp) {
            if (n.endsWith("tall_grass") || n.equals("large_fern") || n.equals("rose_bush") || n.equals("lilac") || n.equals("peony") || n.equals("sunflower")) {
                if (prop(state, "half").equals("upper")) return name(below).equals(n) ? null : "tall plant missing its lower half";
            }
            if (n.equals("lily_pad")) return below.contains("water") ? null : "lily pad off the water";
            return empty(below) ? (n.endsWith("concrete_powder") || n.equals("sand") || n.equals("red_sand") || n.equals("gravel") ? "loose block that will fall"
                : n.replace("soul_", "") + " on nothing") : null;
        }
        if (n.equals("vine") || n.endsWith("cave_vines") || n.endsWith("cave_vines_plant") || n.equals("weeping_vines") || n.equals("hanging_roots")
            || n.equals("pointed_dripstone") || n.equals("spore_blossom")) {
            if (n.equals("vine")) return null;
            return empty(above) && !name(above).contains("vines") ? n + " hanging from nothing" : null;
        }
        return null;
    }

    @Test
    void everythingHangsLeansOrStandsOnSomething() {
        Map<String, Integer> counts = new TreeMap<>();
        Map<String, List<String>> samples = new TreeMap<>();
        for (ReachFeatures.Feature feature : FEATURES.features()) {
            Map<Long, String> plan = materialize(FEATURES.plan(feature));
            for (Map.Entry<Long, String> e : plan.entrySet()) {
                long k = e.getKey();
                int x = (int) (k >> 42), z = (int) (k << 22 >> 42), y = (int) (k << 44 >> 44);
                x = x << 10 >> 10;
                String f = fault(plan, x, y, z, e.getValue());
                if (f == null) continue;
                String group = feature.id() + ": " + f;
                counts.merge(group, 1, Integer::sum);
                List<String> s = samples.computeIfAbsent(group, g -> new ArrayList<>());
                if (s.size() < 3) s.add(x + "," + y + "," + z + " " + e.getValue());
            }
        }
        List<String> report = new ArrayList<>();
        counts.forEach((g, n) -> report.add(g + " x" + n + "  e.g. " + samples.get(g)));
        String out = System.getenv("REACH_FAULT_REPORT");
        if (out != null) try { java.nio.file.Files.write(java.nio.file.Path.of(out), report); } catch (java.io.IOException ignored) { }
        report.forEach(System.out::println);
        assertEquals(List.of(), report);
    }

    /**
     * No part of a build is adrift: every solid piece of a plan reaches the ground through the plan's own blocks.
     * Pieces of fewer than 400 blocks that touch nothing are reported, with where they are.
     */
    @Test
    void noPieceOfABuildFloats() {
        List<String> report = new ArrayList<>();
        for (ReachFeatures.Feature feature : FEATURES.features()) {
            if (FLOATING_BY_DESIGN.contains(feature.id())) continue;
            Map<Long, String> plan = materialize(FEATURES.plan(feature));
            Set<Long> solid = new HashSet<>();
            plan.forEach((k, s) -> { if (!empty(s) && !name(s).equals("end_gateway")) solid.add(k); });
            Set<Long> grounded = new HashSet<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            for (long k : solid) {
                int x = ((int) (k >> 42)) << 10 >> 10, z = (int) (k << 22 >> 42), y = (int) (k << 44 >> 44);
                boolean onGround = false;
                for (int[] d : new int[][] {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}}) {
                    long n = key(x + d[0], y + d[1], z + d[2]);
                    if (plan.containsKey(n)) continue;
                    String t = at(plan, x + d[0], y + d[1], z + d[2]);
                    if (t.equals("minecraft:stone")) { onGround = true; break; }
                }
                if (onGround && grounded.add(k)) queue.add(k);
            }
            while (!queue.isEmpty()) {
                long k = queue.poll();
                int x = ((int) (k >> 42)) << 10 >> 10, z = (int) (k << 22 >> 42), y = (int) (k << 44 >> 44);
                for (int[] d : EDGES) {
                    long n = key(x + d[0], y + d[1], z + d[2]);
                    if (solid.contains(n) && grounded.add(n)) queue.add(n);
                }
            }
            Set<Long> seen = new HashSet<>(grounded);
            for (long k : solid) {
                if (seen.contains(k)) continue;
                List<Long> piece = new ArrayList<>();
                queue.add(k);
                seen.add(k);
                while (!queue.isEmpty()) {
                    long c = queue.poll();
                    piece.add(c);
                    int x = ((int) (c >> 42)) << 10 >> 10, z = (int) (c << 22 >> 42), y = (int) (c << 44 >> 44);
                    for (int[] d : EDGES) {
                        long n = key(x + d[0], y + d[1], z + d[2]);
                        if (solid.contains(n) && seen.add(n)) queue.add(n);
                    }
                }
                if (piece.size() >= 400) continue;
                long first = piece.get(0);
                int x = ((int) (first >> 42)) << 10 >> 10, z = (int) (first << 22 >> 42), y = (int) (first << 44 >> 44);
                report.add(feature.id() + ": " + piece.size() + " blocks adrift at " + x + "," + y + "," + z + " (" + plan.get(first) + ")");
            }
        }
        String out = System.getenv("REACH_SUPPORT_REPORT");
        if (out != null) try { java.nio.file.Files.write(java.nio.file.Path.of(out), report); } catch (java.io.IOException ignored) { }
        report.forEach(System.out::println);
        assertEquals(List.of(), report);
    }

    /** Set REACH_PROBE_PLAN to feature:x,y,z;feature:x,y,z to print the plan round each point, layer by layer from the top. */
    @Test
    void probePlanWhenAsked() {
        String ask = System.getenv("REACH_PROBE_PLAN");
        if (ask == null || ask.isBlank()) return;
        StringBuilder out = new StringBuilder();
        for (String one : ask.split(";")) {
            String[] fp = one.split(":");
            Map<Long, String> plan = materialize(FEATURES.plan(FEATURES.feature(fp[0]).orElseThrow()));
            String[] c = fp[1].split(",");
            int x0 = Integer.parseInt(c[0]), y0 = Integer.parseInt(c[1]), z0 = Integer.parseInt(c[2]);
            int r = c.length > 3 ? Integer.parseInt(c[3]) : 1;
            out.append("== ").append(one).append(System.lineSeparator());
            for (int y = y0 + r + 1; y >= y0 - r - 1; y--) {
                out.append("y").append(y).append(System.lineSeparator());
                for (int z = z0 - r; z <= z0 + r; z++) {
                    StringBuilder row = new StringBuilder("  z").append(z).append(": ");
                    for (int x = x0 - r; x <= x0 + r; x++) {
                        String s = at(plan, x, y, z);
                        boolean own = plan.containsKey(key(x, y, z));
                        String n = name(s);
                        row.append(String.format("%-22s", (own ? "" : "~") + n + (s.contains("hanging=true") ? "^" : s.contains("hanging=false") ? "_" : ""))
                            .substring(0, 22)).append(' ');
                    }
                    out.append(row).append(System.lineSeparator());
                }
            }
        }
        try { java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv("REACH_PROBE_OUT")), out); } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    static boolean clear(String state) {
        String n = name(state);
        return n.equals("air") || n.equals("cave_air") || n.endsWith("_carpet") || n.equals("short_grass") || n.endsWith("pressure_plate")
            || n.equals("light") || n.endsWith("_sign") && !n.contains("wall") || n.equals("snow");
    }

    /**
     * Every door opens unimpeded: on both sides the two blocks a player walks through are clear, over a floor or a
     * single step down. A lantern, fence, chest or wall in a doorway fails it.
     */
    @Test
    void everyDoorwayIsClear() {
        List<String> report = new ArrayList<>();
        for (ReachFeatures.Feature feature : FEATURES.features()) {
            Map<Long, String> plan = materialize(FEATURES.plan(feature));
            plan.forEach((k, state) -> {
                if (!name(state).endsWith("_door") || !prop(state, "half").equals("lower")) return;
                int x = (int) (k >> 42), z = (int) (k << 22 >> 42), y = (int) (k << 44 >> 44);
                String f = prop(state, "facing");
                int dx = f.equals("east") ? 1 : f.equals("west") ? -1 : 0, dz = f.equals("south") ? 1 : f.equals("north") ? -1 : 0;
                for (int side : new int[] {1, -1}) {
                    int sx = x + dx * side, sz = z + dz * side;
                    String feet = at(plan, sx, y, sz), head = at(plan, sx, y + 1, sz);
                    String floor = at(plan, sx, y - 1, sz), under = at(plan, sx, y - 2, sz);
                    boolean stand = !empty(floor) && !clear(floor) || clear(floor) && !empty(under) && !clear(under);
                    if (!clear(feet) || !clear(head) || !stand)
                        report.add(feature.id() + " door " + x + "," + y + "," + z + " " + (side > 0 ? "front" : "back") + ": feet " + name(feet)
                            + ", head " + name(head) + ", floor " + name(floor));
                }
            });
        }
        // Another build's blocks in a doorway: a roadside lamp, a wall or a stall from the plan next door.
        Map<Long, String> doorways = new HashMap<>();
        Map<Long, String> owner = new HashMap<>();
        for (ReachFeatures.Feature feature : FEATURES.features()) {
            Map<Long, String> plan = materialize(FEATURES.plan(feature));
            plan.forEach((k, state) -> {
                if (!name(state).endsWith("_door") || !prop(state, "half").equals("lower")) return;
                int x = (int) (k >> 42), z = (int) (k << 22 >> 42), y = (int) (k << 44 >> 44);
                String f = prop(state, "facing");
                int dx = f.equals("east") ? 1 : f.equals("west") ? -1 : 0, dz = f.equals("south") ? 1 : f.equals("north") ? -1 : 0;
                for (int side : new int[] {1, -1}) for (int h = 0; h <= 1; h++) {
                    long cell = key(x + dx * side, y + h, z + dz * side);
                    doorways.put(cell, feature.id() + " door " + x + "," + y + "," + z);
                    owner.put(cell, feature.id());
                }
            });
        }
        for (ReachFeatures.Feature feature : FEATURES.features()) {
            ReachCanvas.Frozen plan = FEATURES.plan(feature);
            for (long cell : doorways.keySet()) {
                if (owner.get(cell).equals(feature.id())) continue;
                int x = (int) (cell >> 42), z = (int) (cell << 22 >> 42), y = (int) (cell << 44 >> 44);
                if (x < plan.minX || x > plan.maxX || z < plan.minZ || z > plan.maxZ || !plan.occupies(x, z)) continue;
                ReachCanvas.Spec spec = plan.get(x, y, z);
                if (spec != null && !clear(spec.state()))
                    report.add(doorways.get(cell) + " blocked by " + feature.id() + "'s " + name(spec.state()) + " at " + x + "," + y + "," + z);
            }
        }
        String out = System.getenv("REACH_DOOR_REPORT");
        if (out != null) try { java.nio.file.Files.write(java.nio.file.Path.of(out), report); } catch (java.io.IOException ignored) { }
        assertEquals(List.of(), report);
    }
}
