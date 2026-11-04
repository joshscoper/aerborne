package net.nerdsmp.paper.reach;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs of fences, walls and parapets that join up the way a builder's would. Generated blocks don't update their
 * neighbours, so each post is given its connections here: a fence joins the fences and gates beside it, a wall joins
 * the walls beside it and only shows a post where the run turns or ends, and where the next post stands a block
 * higher an extra post is added so the run steps up instead of breaking.
 */
final class ReachRuns {
    private record Post(int x, int y, int z, Material material, Kind kind, String facing) { }

    enum Kind { FENCE, WALL, GATE }

    private final Map<Long, Post> posts = new LinkedHashMap<>();

    void fence(int x, int y, int z, Material material) { posts.put(key(x, y, z), new Post(x, y, z, material, Kind.FENCE, null)); }

    void wall(int x, int y, int z, Material material) { posts.put(key(x, y, z), new Post(x, y, z, material, Kind.WALL, null)); }

    /** A gate across a fence run; {@code facing} is the way it opens (across the run). */
    void gate(int x, int y, int z, Material material, String facing) { posts.put(key(x, y, z), new Post(x, y, z, material, Kind.GATE, facing)); }

    boolean has(int x, int y, int z) { return posts.containsKey(key(x, y, z)); }

    void remove(int x, int y, int z) { posts.remove(key(x, y, z)); }

    /** Writes every post into the canvas with its joins. Blocks the canvas already holds are left alone. */
    void write(ReachCanvas canvas) {
        List<Post> extra = new ArrayList<>();
        for (Post p : posts.values()) {
            if (p.kind() == Kind.GATE) continue;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                if (posts.containsKey(key(p.x() + d[0], p.y() + 1, p.z() + d[1])) && !posts.containsKey(key(p.x(), p.y() + 1, p.z())))
                    extra.add(new Post(p.x(), p.y() + 1, p.z(), p.material(), p.kind(), null));
        }
        for (Post p : extra) posts.putIfAbsent(key(p.x(), p.y(), p.z()), p);
        String[] faces = {"north", "east", "south", "west"};
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        Map<Long, Boolean> written = new HashMap<>();
        for (Post p : posts.values()) {
            if (canvas.has(p.x(), p.y(), p.z()) && !written.containsKey(key(p.x(), p.y(), p.z()))) {
                String state = canvas.get(p.x(), p.y(), p.z()).state();
                if (!state.equals("minecraft:air")) continue;
            }
            written.put(key(p.x(), p.y(), p.z()), true);
            if (p.kind() == Kind.GATE) {
                canvas.set(p.x(), p.y(), p.z(), p.material(), "facing=" + p.facing() + ",open=false,in_wall=false");
                continue;
            }
            boolean[] joined = new boolean[4];
            StringBuilder props = new StringBuilder();
            for (int f = 0; f < 4; f++) {
                Post n = posts.get(key(p.x() + dirs[f][0], p.y(), p.z() + dirs[f][1]));
                joined[f] = n != null && (n.kind() == p.kind() || n.kind() == Kind.GATE && p.kind() == Kind.FENCE);
                if (props.length() > 0) props.append(',');
                props.append(faces[f]).append('=').append(p.kind() == Kind.WALL ? (joined[f] ? "low" : "none") : String.valueOf(joined[f]));
            }
            if (p.kind() == Kind.WALL) {
                boolean straight = joined[0] && joined[2] && !joined[1] && !joined[3] || joined[1] && joined[3] && !joined[0] && !joined[2];
                boolean capped = posts.containsKey(key(p.x(), p.y() + 1, p.z()));
                props.append(",up=").append(!straight || capped);
            }
            canvas.set(p.x(), p.y(), p.z(), p.material(), props.toString());
        }
    }

    private static long key(int x, int y, int z) { return ((long) x & 0x3FFFFF) << 42 | ((long) z & 0x3FFFFF) << 20 | (y & 0xFFFFF); }
}
