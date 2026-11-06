package net.nerdsmp.paper.reach;

import org.bukkit.Material;

/**
 * A flight point's roost: a dark timber perch post with an arm out from its top, a bell hung under the arm for
 * travellers to ring, a lantern on the post, a bale of hay at its foot and a lettered board. It stands on whatever
 * is really there, the build's floor or the ground, and is footed down to it. The spot it returns stands in front of
 * the post, where riders mount and land (see {@link ReachFlights}).
 */
final class ReachRoost {
    private ReachRoost() { }

    /** Builds a roost at (x, z) facing (fx, fz), its arm reaching to the right of that; returns the "flight" spot. */
    static ReachFeatures.Spot build(ReachCanvas canvas, ReachTerrain terrain, int x, int z, int fx, int fz, String name) {
        int px = -fz, pz = fx;
        int y = floor(canvas, terrain, x, z);
        for (int[] c : new int[][] {{0, 0}, {px, pz}, {2 * px, 2 * pz}, {-px, -pz}, {fx, fz}, {2 * fx, 2 * fz}, {fx + px, fz + pz}, {fx - px, fz - pz}}) {
            int cx = x + c[0], cz = z + c[1];
            for (int yy = terrain.column(cx, cz).top() + 1; yy < y - 1; yy++) if (air(canvas, terrain, cx, yy, cz)) canvas.set(cx, yy, cz, Material.COBBLESTONE);
            if (air(canvas, terrain, cx, y - 1, cz)) canvas.set(cx, y - 1, cz, Material.COARSE_DIRT);
            for (int yy = y; yy <= y + 4; yy++) if (!air(canvas, terrain, cx, yy, cz)) canvas.set(cx, yy, cz, Material.AIR);
        }
        for (int h = 0; h < 4; h++) canvas.set(x, y + h, z, Material.STRIPPED_DARK_OAK_LOG, "axis=y");
        canvas.set(x, y + 4, z, Material.LANTERN, "hanging=false");
        canvas.set(x + px, y + 3, z + pz, Material.DARK_OAK_FENCE);
        canvas.set(x + 2 * px, y + 3, z + 2 * pz, Material.DARK_OAK_FENCE);
        canvas.set(x + 2 * px, y + 2, z + 2 * pz, Material.BELL, "attachment=ceiling,facing=" + compass(fx, fz) + ",powered=false");
        canvas.set(x - px, y, z - pz, Material.HAY_BLOCK, "axis=y");
        canvas.set(x + fx, y + 1, z + fz, Material.DARK_OAK_WALL_SIGN, "facing=" + compass(fx, fz));
        String[] words = name.split(" ", 2);
        canvas.text(x + fx, y + 1, z + fz, "FLIGHT POINT", words[0], words.length > 1 ? words[1] : "", "Ring the bell");
        float yaw = (float) Math.toDegrees(Math.atan2(fx, -fz));
        return new ReachFeatures.Spot("flight", x + 2 * fx + 0.5, y, z + 2 * fz + 0.5, yaw);
    }

    /** The first open block over what is really at a column: the build's floor where it laid one, else the ground. */
    static int floor(ReachCanvas canvas, ReachTerrain terrain, int x, int z) {
        int top = terrain.column(x, z).top();
        int y = top + 1;
        for (int k = 0; k < 40 && !air(canvas, terrain, x, y, z); k++) y++;
        while (y > top - 6 && air(canvas, terrain, x, y - 1, z)) y--;
        return y;
    }

    private static boolean air(ReachCanvas canvas, ReachTerrain terrain, int x, int y, int z) {
        ReachCanvas.Spec spec = canvas.get(x, y, z);
        if (spec != null) return spec.state().equals("minecraft:air") || ReachPaths.Ground.plant(spec.state());
        return y > terrain.column(x, z).top();
    }

    private static String compass(int dx, int dz) { return dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north"; }
}
