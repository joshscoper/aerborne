package net.nerdsmp.paper.reach;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Writes into a {@link ReachCanvas} in a building's own frame: local x runs along the front, local z runs back from
 * the street, y is up from the floor. A turn of 0 puts the front facing north (-z); each turn rotates the building a
 * quarter clockwise seen from above, and block states (stairs, logs, doors, trapdoors) turn with it.
 */
final class ReachBrush {
    private static final String[] FACES = {"north", "east", "south", "west"};
    final ReachCanvas canvas;
    final int ox, oy, oz, turn;

    ReachBrush(ReachCanvas canvas, int ox, int oy, int oz, int turn) {
        this.canvas = canvas;
        this.ox = ox; this.oy = oy; this.oz = oz;
        this.turn = Math.floorMod(turn, 4);
    }

    /** A brush for a sub-part, offset in this brush's local frame and optionally turned further. */
    ReachBrush at(int lx, int ly, int lz, int extraTurn) {
        return new ReachBrush(canvas, wx(lx, lz), oy + ly, wz(lx, lz), turn + extraTurn);
    }

    int wx(int lx, int lz) {
        return ox + switch (turn) { case 0 -> lx; case 1 -> -lz; case 2 -> -lx; default -> lz; };
    }

    int wz(int lx, int lz) {
        return oz + switch (turn) { case 0 -> lz; case 1 -> lx; case 2 -> -lz; default -> -lx; };
    }

    void set(int lx, int ly, int lz, Material material) { canvas.set(wx(lx, lz), oy + ly, wz(lx, lz), material); }

    void set(int lx, int ly, int lz, Material material, String properties) {
        canvas.set(wx(lx, lz), oy + ly, wz(lx, lz), material, rotate(properties));
    }

    boolean has(int lx, int ly, int lz) { return canvas.has(wx(lx, lz), oy + ly, wz(lx, lz)); }

    void box(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, material);
    }

    void box(int x1, int y1, int z1, int x2, int y2, int z2, Material material, String properties) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, material, properties);
    }

    void air(int x1, int y1, int z1, int x2, int y2, int z2) { box(x1, y1, z1, x2, y2, z2, Material.AIR); }

    /** Fills from {@code ly} down to the terrain so nothing floats on a slope. */
    void footing(ReachTerrain terrain, int lx, int ly, int lz, Material material) {
        int x = wx(lx, lz), z = wz(lx, lz);
        ReachTerrain.Column column = terrain.column(x, z);
        int ground = column.land() ? column.top() : oy + ly - 12;
        for (int y = oy + ly; y > ground; y--) canvas.set(x, y, z, material);
    }

    /** Rotates facing and axis properties to this brush's turn. */
    String rotate(String properties) {
        if (properties == null || properties.isEmpty() || turn == 0) return properties;
        StringBuilder out = new StringBuilder();
        for (String part : properties.split(",")) {
            String[] kv = part.split("=", 2);
            String key = kv[0], value = kv.length > 1 ? kv[1] : "";
            if (key.equals("facing") || key.equals("horizontal_facing")) value = turnFace(value, turn);
            else if (key.equals("axis") && (turn & 1) == 1 && !value.equals("y")) value = value.equals("x") ? "z" : "x";
            else if (key.equals("rotation")) value = String.valueOf((Integer.parseInt(value) + turn * 4) & 15);
            else if ((turn & 1) == 1 || turn == 2) {
                // Wall and fence connections are named by side: north=true becomes east=true after one turn.
                int side = indexOf(key);
                if (side >= 0) key = FACES[(side + turn) & 3];
            }
            if (out.length() > 0) out.append(',');
            out.append(key).append(kv.length > 1 ? "=" + value : "");
        }
        return out.toString();
    }

    private static int indexOf(String face) {
        for (int i = 0; i < 4; i++) if (FACES[i].equals(face)) return i;
        return -1;
    }

    static String turnFace(String face, int turn) {
        int i = indexOf(face.toLowerCase(Locale.ROOT));
        return i < 0 ? face : FACES[(i + turn) & 3];
    }

    /** A stable pseudo-random value in [0, 1) for a local position, so weathering repeats exactly. */
    double noise(int lx, int ly, int lz, long salt) {
        long h = ReachFlora.hash(wx(lx, lz) * 31 + ly * 7919 + (int) salt, wz(lx, lz) * 17 - ly * 104729);
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
