package net.nerdsmp.paper.reach;

import org.bukkit.Material;

/**
 * Highcrown Keep: the citadel on the crag that makes the capital's power plain. An octagonal curtain wall with eight
 * round towers and a twin-towered gatehouse closes the city's north-east corner. Inside the bailey:
 * - the Crown Keep, whose throne hall holds the empty crown and the Steward;
 * - the Hall of Accord, where envoys of the Reach meet under their banners;
 * - barracks and stables for the Watch, an armory with the quartermaster, a chapel;
 * - the treasury where the city's banker keeps its gold;
 * - a training yard with targets, where the duel marshal drills recruits.
 */
final class HighcrownCastle {
    /** Where the keep's spots and claimed columns go. */
    interface Site {
        void mark(String id, int x, int y, int z, float yaw);
        void reserve(int x, int z);
    }

    static final int CX = 112, CZ = 868;
    /** The courtyard floor level (the crag's top block is one below) and the wall-walk level. */
    static final int K = ReachTerrain.CROWN_TOP + 13, WALK = K + 14;
    private static final double OUTER = 39, INNER = 36;

    private final ReachTerrain terrain;
    private final ReachCanvas canvas;
    private final ReachKit kit;
    private final Site site;

    HighcrownCastle(ReachTerrain terrain, ReachCanvas canvas, ReachKit kit, Site site) {
        this.terrain = terrain;
        this.canvas = canvas;
        this.kit = kit;
        this.site = site;
    }

    /** Inside an octagon with flat faces on the axes, {@code a} from the centre to each face. */
    static boolean inOctagon(double dx, double dz, double a) {
        return Math.abs(dx) <= a && Math.abs(dz) <= a && Math.abs(dx) + Math.abs(dz) <= a * 1.4142;
    }

    private Material stone(int x, int y, int z) {
        double c = terrain.patch(x + y * 0.7, z - y * 0.5, 0.2);
        if (c > 0.35 && y < K + 3) return Material.MOSSY_STONE_BRICKS;
        if (c < -0.55) return Material.CRACKED_STONE_BRICKS;
        if (c > 0.6) return Material.TUFF_BRICKS;
        return Material.STONE_BRICKS;
    }

    void build() {
        bailey();
        curtain();
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            tower((int) Math.round(CX + 41 * Math.cos(a)), (int) Math.round(CZ + 41 * Math.sin(a)), 5.5, WALK + 7, k % 2 == 0);
        }
        gatehouse();
        crownKeep();
        hallOfAccord();
        // Barracks and stables along the west and east walls, an armory and a chapel in the north corners.
        ReachBrush barracks = new ReachBrush(canvas, CX - 21, K, CZ - 12, 1);
        int[] q = kit.house(barracks, 26, 12, 1, ReachKit.Style.SLATE, false, false, 51, ReachInteriors.Use.BARRACKS, ReachInteriors.Trade.QUARTERMASTER);
        ReachBrush stables = new ReachBrush(canvas, CX + 21, K, CZ + 13, 3);
        kit.house(stables, 26, 12, 1, ReachKit.Style.HIGHCROWN, false, false, 52, ReachInteriors.Use.STABLES, ReachInteriors.Trade.GENERAL);
        ReachBrush armory = new ReachBrush(canvas, CX - 22, K, CZ - 27, 0);
        int[] a = kit.house(armory, 9, 9, 1, ReachKit.Style.SLATE, false, true, 53, ReachInteriors.Use.WORKSHOP, ReachInteriors.Trade.QUARTERMASTER);
        if (a != null) site.mark("npc_mara_venn", armory.wx(a[0], a[2]), K, armory.wz(a[0], a[2]), 180F);
        chapel(CX + 14, CZ - 28);
        treasury();
        trainingYard();
        well(CX + 14, CZ - 2);
        site.mark("keep_court", CX, K, CZ, 180F);
    }

    // ------------------------------------------------------------------ ground and walls

    /** Levels the crag top inside the walls and paves the bailey. */
    private void bailey() {
        for (int x = CX - 44; x <= CX + 44; x++) for (int z = CZ - 44; z <= CZ + 44; z++) {
            int dx = x - CX, dz = z - CZ;
            if (!inOctagon(dx, dz, OUTER + 0.5)) continue;
            site.reserve(x, z);
            ReachTerrain.Column c = terrain.column(x, z);
            int ground = c.land() ? c.top() : K - 12;
            for (int y = Math.min(ground, K - 1); y < K - 1; y++) canvas.set(x, y, z, Material.STONE);
            if (inOctagon(dx, dz, INNER)) {
                double p = terrain.patch(x, z, 0.2), g = terrain.grain(x, z, 0.4);
                canvas.set(x, K - 1, z, p > 0.35 ? Material.POLISHED_ANDESITE : g > 0.5 ? Material.COBBLESTONE : p < -0.45 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                for (int y = K; y <= Math.max(K + 8, ground); y++) canvas.set(x, y, z, Material.AIR);
            }
        }
    }

    /** The curtain: three thick, crenellated on the outside, with machicolations, arrow slits and stairs up from the bailey. */
    private void curtain() {
        for (int x = CX - 40; x <= CX + 40; x++) for (int z = CZ - 40; z <= CZ + 40; z++) {
            int dx = x - CX, dz = z - CZ;
            if (!inOctagon(dx, dz, OUTER) || inOctagon(dx, dz, INNER)) continue;
            ReachTerrain.Column c = terrain.column(x, z);
            int ground = c.land() ? Math.min(c.top(), K - 1) : K - 10;
            boolean outer = !inOctagon(dx, dz, OUTER - 1);
            for (int y = ground - 2; y < WALK; y++) canvas.set(x, y, z, stone(x, y, z));
            canvas.set(x, WALK - 1, z, outer ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
            for (int y = WALK; y < WALK + 4; y++) canvas.set(x, y, z, Material.AIR);
            if (outer) {
                canvas.set(x, WALK, z, Material.STONE_BRICKS);
                if (Math.floorMod(x + z, 3) != 0) canvas.set(x, WALK + 1, z, Material.STONE_BRICKS);
                if (Math.floorMod(x * 3 + z, 7) == 0) for (int y = K + 3; y <= K + 5; y++) canvas.set(x, y, z, Material.AIR);
            }
            // A plinth stepping out at the foot, and a string course halfway up.
            if (outer) canvas.set(x, K + 6, z, Material.POLISHED_ANDESITE);
        }
        // Machicolations: corbels under a projecting parapet on the outer face.
        for (int x = CX - 41; x <= CX + 41; x++) for (int z = CZ - 41; z <= CZ + 41; z++) {
            int dx = x - CX, dz = z - CZ;
            if (!inOctagon(dx, dz, OUTER + 1) || inOctagon(dx, dz, OUTER)) continue;
            int[] in = inward(dx, dz);
            String face = in == null ? "north" : faceOf(in[0], in[1]);
            canvas.set(x, WALK - 2, z, Material.STONE_BRICK_STAIRS, "facing=" + face + ",half=top");
            canvas.set(x, WALK - 1, z, Material.STONE_BRICKS);
            if (Math.floorMod(x + z, 2) == 0) canvas.set(x, WALK, z, Material.STONE_BRICKS);
        }
        // Stairs up the inner face to the walk, on the north and the south.
        for (int[] st : new int[][] {{-8, (int) -INNER + 1, 1}, {-6, (int) INNER - 1, -1}}) {
            for (int i = 0; i < WALK - K; i++) {
                int x = CX + st[0] + i, z = CZ + st[1];
                canvas.set(x, K + i, z, Material.STONE_BRICK_STAIRS, "facing=east,half=bottom");
                for (int y = K; y < K + i; y++) canvas.set(x, y, z, Material.STONE_BRICKS);
                for (int y = K + i + 1; y <= K + i + 3; y++) canvas.set(x, y, z, Material.AIR);
                canvas.set(x, K + i, z + st[2], Material.STONE_BRICKS);
                canvas.set(x, K + i + 1, z + st[2], Material.STONE_BRICK_WALL, "up=true");
            }
        }
    }

    /** The direction from a point outside the curtain back toward the centre, as a unit step. */
    private static int[] inward(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz) + 4) return new int[] {-Integer.signum(dx), 0};
        if (Math.abs(dz) > Math.abs(dx) + 4) return new int[] {0, -Integer.signum(dz)};
        return Math.abs(dx) >= Math.abs(dz) ? new int[] {-Integer.signum(dx), 0} : new int[] {0, -Integer.signum(dz)};
    }

    private static String faceOf(int sx, int sz) {
        return sx > 0 ? "east" : sx < 0 ? "west" : sz > 0 ? "south" : "north";
    }

    /** A round tower: battered base, slit windows, floors, a crenellated top and, on alternate towers, a slate cone. */
    private void tower(int cx, int cz, double r, int top, boolean cone) {
        for (int x = cx - 7; x <= cx + 7; x++) for (int z = cz - 7; z <= cz + 7; z++) {
            double d = Math.hypot(x - cx, z - cz);
            if (d > r + 0.6) continue;
            site.reserve(x, z);
            ReachTerrain.Column c = terrain.column(x, z);
            int ground = c.land() ? Math.min(c.top(), K - 1) : K - 14;
            boolean wall = d > r - 1.4;
            for (int y = ground - 2; y <= top; y++) {
                if (!wall && y >= K) { canvas.set(x, y, z, (y - K) % 7 == 6 ? Material.SPRUCE_PLANKS : Material.AIR); continue; }
                boolean batter = d > r - 0.4 && y < K + 2;
                boolean slit = wall && (y - K) % 7 == 3 && Math.floorMod(x * 5 + z * 3, 5) == 0;
                canvas.set(x, y, z, slit ? Material.AIR : batter ? Material.POLISHED_ANDESITE : stone(x, y, z));
            }
            if (wall) {
                // The parapet: merlons on the outer ring, a walkable floor inside.
                canvas.set(x, top + 1, z, Material.STONE_BRICKS);
                if (Math.floorMod(x + z, 2) == 0 && d > r - 0.6) canvas.set(x, top + 2, z, Material.STONE_BRICKS);
            } else canvas.set(x, top, z, Material.SPRUCE_PLANKS);
            // Doors onto the wall-walk wherever the tower meets the curtain.
            int dx = x - CX, dz = z - CZ;
            if (wall && inOctagon(dx, dz, OUTER) && !inOctagon(dx, dz, INNER)) for (int y = WALK; y <= WALK + 1; y++) canvas.set(x, y, z, Material.AIR);
        }
        if (cone) {
            int y = top + 2;
            for (double rr = r + 0.8; rr > 0.4; rr -= 0.5, y++) for (int x = cx - 7; x <= cx + 7; x++) for (int z = cz - 7; z <= cz + 7; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > rr || d <= rr - 1.2) continue;
                int[] in = d < 0.5 ? new int[] {0, -1} : (Math.abs(x - cx) >= Math.abs(z - cz) ? new int[] {-Integer.signum(x - cx), 0} : new int[] {0, -Integer.signum(z - cz)});
                canvas.set(x, y, z, d > rr - 0.6 ? Material.DEEPSLATE_TILE_STAIRS : Material.DEEPSLATE_TILES,
                    d > rr - 0.6 ? "facing=" + faceOf(in[0], in[1]) + ",half=bottom" : "");
            }
            canvas.set(cx, y, cz, Material.DARK_OAK_FENCE);
            canvas.set(cx, y + 1, cz, Material.DARK_OAK_FENCE);
            canvas.set(cx, y + 2, cz, Material.GREEN_BANNER, "rotation=4");
        } else {
            canvas.set(cx, top + 1, cz, Material.DARK_OAK_FENCE);
            for (int y = top + 2; y <= top + 5; y++) canvas.set(cx, y, cz, Material.DARK_OAK_FENCE);
            canvas.set(cx, top + 6, cz, Material.GREEN_BANNER, "rotation=4");
            canvas.set(cx + 1, top + 1, cz, Material.LANTERN);
        }
    }

    /** The gatehouse on the west face, at the head of the citadel steps: twin towers, a portcullis and the city's arms. */
    private void gatehouse() {
        int x0 = CX - 40, x1 = CX - 33, gz = CZ;
        tower(CX - 39, gz - 7, 4.5, WALK + 5, true);
        tower(CX - 39, gz + 7, 4.5, WALK + 5, true);
        for (int x = x0; x <= x1; x++) for (int z = gz - 4; z <= gz + 4; z++) {
            site.reserve(x, z);
            for (int y = K - 1; y <= WALK + 2; y++) canvas.set(x, y, z, stone(x, y, z));
            int dz = Math.abs(z - gz);
            int arch = dz <= 1 ? K + 5 : dz == 2 ? K + 4 : K + 2;
            if (dz <= 2) {
                canvas.set(x, K - 1, z, dz == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                for (int y = K; y <= arch; y++) canvas.set(x, y, z, Material.AIR);
            }
            // The gate room above the passage, lit by windows on both faces.
            if (x > x0 && x < x1 && dz <= 3) for (int y = K + 8; y <= K + 12; y++) canvas.set(x, y, z, Material.AIR);
            if (x > x0 && x < x1 && dz <= 3) canvas.set(x, K + 7, z, Material.SPRUCE_PLANKS);
        }
        for (int z = gz - 2; z <= gz + 2; z++) {
            for (int y = K + 3; y <= K + 5; y++) if (Math.abs(z - gz) <= 1 || y <= K + 4) canvas.set(x0 + 1, y, z, Material.IRON_BARS);
            canvas.set(x0, K + 9, z, Material.GLASS_PANE);
            canvas.set(x1, K + 9, z, Material.GLASS_PANE);
        }
        // The arms of Highcrown over the arch, banners either side, lamps at the mouth.
        for (int dz = -2; dz <= 2; dz++) for (int dy = -2; dy <= 2; dy++) {
            double r = Math.hypot(dz, dy);
            if (r > 2.5) continue;
            canvas.set(x0 - 1, K + 10 + dy, gz + dz, r > 1.6 ? Material.STRIPPED_DARK_OAK_WOOD : dy >= 0 && Math.abs(dz) <= 1 ? Material.GOLD_BLOCK : Material.GREEN_TERRACOTTA);
        }
        for (int z : new int[] {gz - 3, gz + 3}) {
            canvas.set(x0 - 1, K + 6, z, Material.GREEN_WALL_BANNER, "facing=west");
            canvas.set(x0 - 1, K + 5, z, Material.GREEN_WALL_BANNER, "facing=west");
            canvas.set(x0 - 1, K + 1, z, Material.LANTERN, "hanging=false");
        }
        for (int x = x0 + 1; x <= x1; x += 2) for (int z = gz - 2; z <= gz + 2; z += 4) canvas.set(x, K + 4, z, Material.LANTERN, "hanging=true");
        site.mark("castle_gate", x0 + 3, K, gz, 270F);
    }

    // ------------------------------------------------------------------ the buildings of the bailey

    /**
     * The Crown Keep: a great square tower with corner turrets. Its ground floor is the throne hall, two storeys
     * high, where the empty crown waits on its dais and the Steward rules in the king's absence.
     */
    private void crownKeep() {
        int x0 = CX - 11, x1 = CX + 11, z0 = CZ - 30, z1 = CZ - 8, h = 34;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            site.reserve(x, z);
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            for (int y = K - 1; y <= K + h; y++) {
                boolean floor = y == K - 1 || y == K + 9 || y == K + 16 || y == K + 23 || y == K + 30;
                if (!wall) { canvas.set(x, y, z, floor ? (y == K - 1 ? Material.DEEPSLATE_TILES : Material.DARK_OAK_PLANKS) : Material.AIR); continue; }
                boolean pier = (x - x0) % 5 == 0 && (z == z0 || z == z1) || (z - z0) % 5 == 0 && (x == x0 || x == x1);
                boolean window = !pier && ((y >= K + 2 && y <= K + 7) || (y - K) % 7 == 3 && y > K + 9 && y < K + h - 2);
                boolean lancet = window && ((x - x0) % 5 == 2 || (z - z0) % 5 == 2 || (x - x0) % 5 == 3 || (z - z0) % 5 == 3);
                canvas.set(x, y, z, lancet ? Material.GLASS_PANE : pier ? Material.POLISHED_ANDESITE : stone(x, y, z));
            }
            // Battlements.
            if (wall) {
                canvas.set(x, K + h + 1, z, Material.STONE_BRICKS);
                if (Math.floorMod(x + z, 2) == 0) canvas.set(x, K + h + 2, z, Material.STONE_BRICKS);
            } else canvas.set(x, K + h, z, Material.STONE_BRICKS);
        }
        // Buttresses down the faces.
        for (int x = x0; x <= x1; x += 5) for (int z : new int[] {z0 - 1, z1 + 1}) for (int y = K - 1; y <= K + h - 6; y++) {
            if (z > z1 && Math.abs(x - CX) <= 3) continue;
            canvas.set(x, y, z, y > K + h - 8 ? Material.STONE_BRICK_STAIRS : Material.STONE_BRICKS, y > K + h - 8 ? (z < z0 ? "facing=south,half=bottom" : "facing=north,half=bottom") : "");
        }
        // Corner turrets with slate cones, and a watch tower in the middle of the roof.
        for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) tower(c[0], c[1], 3.2, K + h + 4, true);
        for (int x = CX - 3; x <= CX + 3; x++) for (int z = CZ - 22; z <= CZ - 16; z++) for (int y = K + h + 1; y <= K + h + 9; y++) {
            boolean wall = Math.abs(x - CX) == 3 || z == CZ - 22 || z == CZ - 16;
            canvas.set(x, y, z, wall ? ((y - K) % 3 == 0 && Math.abs(x - CX) != 3 && z != CZ - 22 && z != CZ - 16 ? Material.GLASS_PANE : stone(x, y, z)) : Material.AIR);
        }
        kit.spire(new ReachBrush(canvas, CX - 3, K + h + 10, CZ - 22, 0), -1, 7, 0, ReachKit.Style.SLATE);
        // The throne hall: a red runner from the door to the dais, pillars, banners, chandeliers, the empty crown.
        int door = CX;
        for (int x = door - 2; x <= door + 2; x++) for (int y = K; y <= K + 4; y++) canvas.set(x, y, z1, Material.AIR);
        canvas.set(door - 1, K, z1, Material.DARK_OAK_DOOR, "facing=south,half=lower,hinge=left,open=true");
        canvas.set(door - 1, K + 1, z1, Material.DARK_OAK_DOOR, "facing=south,half=upper,hinge=left,open=true");
        canvas.set(door + 1, K, z1, Material.DARK_OAK_DOOR, "facing=south,half=lower,hinge=right,open=true");
        canvas.set(door + 1, K + 1, z1, Material.DARK_OAK_DOOR, "facing=south,half=upper,hinge=right,open=true");
        for (int z = z0 + 5; z < z1; z++) for (int x = door - 1; x <= door + 1; x++) canvas.set(x, K, z, Material.RED_CARPET);
        for (int x = x0 + 4; x <= x1 - 4; x++) for (int z = z0 + 1; z <= z0 + 4; z++) {
            canvas.set(x, K, z, z == z0 + 4 ? Material.POLISHED_BLACKSTONE_STAIRS : Material.POLISHED_BLACKSTONE_BRICKS, z == z0 + 4 ? "facing=north,half=bottom" : "");
            if (z < z0 + 4) canvas.set(x, K + 1, z, Material.AIR);
        }
        canvas.set(door, K + 1, z0 + 2, Material.POLISHED_BLACKSTONE_STAIRS, "facing=north,half=bottom");
        canvas.set(door - 1, K + 1, z0 + 2, Material.POLISHED_BLACKSTONE_WALL, "up=true");
        canvas.set(door + 1, K + 1, z0 + 2, Material.POLISHED_BLACKSTONE_WALL, "up=true");
        canvas.set(door, K + 1, z0 + 1, Material.POLISHED_BLACKSTONE);
        canvas.set(door, K + 2, z0 + 1, Material.GOLD_BLOCK);
        canvas.set(door, K + 3, z0 + 1, Material.LIGHTNING_ROD);
        for (int x : new int[] {door - 3, door + 3}) {
            canvas.set(x, K + 1, z0 + 2, Material.GOLD_BLOCK);
            canvas.set(x, K + 2, z0 + 2, Material.LANTERN);
        }
        for (int x : new int[] {x0 + 5, x1 - 5}) for (int z = z0 + 7; z < z1 - 1; z += 5) for (int y = K; y <= K + 8; y++)
            canvas.set(x, y, z, y == K || y == K + 8 ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_DEEPSLATE);
        for (int z = z0 + 6; z < z1 - 1; z += 5) for (int x : new int[] {x0 + 1, x1 - 1}) {
            canvas.set(x, K + 6, z, Material.GREEN_WALL_BANNER, x == x0 + 1 ? "facing=east" : "facing=west");
            canvas.set(x, K + 5, z, Material.GREEN_WALL_BANNER, x == x0 + 1 ? "facing=east" : "facing=west");
        }
        // Chandeliers: a wheel of dark timber on a chain, lanterns hung beneath it.
        for (int z = z0 + 8; z < z1; z += 6) {
            for (int y = K + 6; y <= K + 8; y++) canvas.set(door, y, z, Material.IRON_CHAIN, "axis=y");
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                canvas.set(door + dx, K + 5, z + dz, dx == 0 && dz == 0 ? Material.IRON_CHAIN : Material.DARK_OAK_TRAPDOOR, dx == 0 && dz == 0 ? "axis=y" : "facing=north,half=top,open=false");
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 0}}) canvas.set(door + d[0], K + 4, z + d[1], Material.LANTERN, "hanging=true");
        }
        // A ladder up to the upper floors in the north-east corner.
        for (int y = K; y <= K + 30; y++) {
            if (y == K + 9 || y == K + 16 || y == K + 23 || y == K + 30) canvas.set(x1 - 1, y, z0 + 1, Material.AIR);
            canvas.set(x1 - 1, y, z0 + 1, Material.LADDER, "facing=south");
        }
        site.mark("keep_hall", door, K, z0 + 6, 0F);
        site.mark("npc_reach_steward_aldric", door, K, z0 + 6, 0F);
    }

    /** The Hall of Accord: a tall stone hall with buttresses and long windows, where envoys sit at the long tables. */
    private void hallOfAccord() {
        int x0 = CX - 16, x1 = CX + 16, z0 = CZ + 8, z1 = CZ + 22, h = 11;
        Material[] banners = {Material.GREEN_WALL_BANNER, Material.YELLOW_WALL_BANNER, Material.PURPLE_WALL_BANNER, Material.BLUE_WALL_BANNER,
            Material.LIGHT_GRAY_WALL_BANNER, Material.RED_WALL_BANNER, Material.ORANGE_WALL_BANNER};
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            site.reserve(x, z);
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            for (int y = K - 1; y <= K + h; y++) {
                if (!wall) { canvas.set(x, y, z, y == K - 1 ? ((x + z) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS) : Material.AIR); continue; }
                boolean bay = (z == z0 || z == z1) && Math.floorMod(x - x0, 4) == 2;
                boolean window = bay && y >= K + 3 && y <= K + 8;
                canvas.set(x, y, z, window ? Material.GLASS_PANE : stone(x, y, z));
            }
        }
        for (int x = x0; x <= x1; x += 4) for (int z : new int[] {z0 - 1, z1 + 1}) {
            for (int y = K - 1; y <= K + 8; y++) canvas.set(x, y, z, Material.STONE_BRICKS);
            canvas.set(x, K + 9, z, Material.STONE_BRICK_STAIRS, z < z0 ? "facing=south,half=bottom" : "facing=north,half=bottom");
        }
        kit.roof(new ReachBrush(canvas, x1, K + h + 1, z0, 1), -1, z1 - z0 + 1, 0, x1 - x0, 0, ReachKit.Style.SLATE, true, true);
        // The door on the bailey side, tables, the high table and banners of the Reach's peoples.
        for (int x = CX - 1; x <= CX + 1; x++) for (int y = K; y <= K + 3; y++) canvas.set(x, y, z0, Material.AIR);
        canvas.set(CX, K + 4, z0 - 1, Material.GREEN_WALL_BANNER, "facing=north");
        for (int x = x0 + 4; x <= x1 - 6; x++) for (int z : new int[] {z0 + 4, z1 - 4}) {
            canvas.set(x, K, z, Material.DARK_OAK_SLAB, "type=top");
            canvas.set(x, K, z - 1, Material.SPRUCE_STAIRS, "facing=south,half=bottom");
            canvas.set(x, K, z + 1, Material.SPRUCE_STAIRS, "facing=north,half=bottom");
            if (x % 3 == 0) canvas.set(x, K + 1, z, Material.CANDLE, "candles=3,lit=true");
        }
        for (int z = z0 + 2; z <= z1 - 2; z++) {
            canvas.set(x1 - 3, K, z, Material.DARK_OAK_SLAB, "type=top");
            canvas.set(x1 - 2, K, z, Material.SPRUCE_STAIRS, "facing=east,half=bottom");
        }
        for (int i = 0; i < 7; i++) {
            int x = x0 + 3 + i * 4;
            canvas.set(x, K + 7, z0 + 1, banners[i], "facing=south");
            canvas.set(x, K + 7, z1 - 1, banners[(i + 3) % 7], "facing=north");
        }
        for (int x = x0 + 6; x <= x1 - 6; x += 8) {
            for (int y = K + 8; y <= K + 10; y++) canvas.set(x, y, CZ + 15, Material.IRON_CHAIN, "axis=y");
            canvas.set(x, K + 7, CZ + 15, Material.LANTERN, "hanging=true");
        }
        site.mark("npc_arlen_voss", CX, K, z0 + 2, 0F);
    }

    private void chapel(int x0, int z0) {
        ReachBrush b = new ReachBrush(canvas, x0, K, z0, 0);
        for (int x = 0; x < 9; x++) for (int z = 0; z < 11; z++) {
            site.reserve(x0 + x, z0 + z);
            boolean wall = x == 0 || x == 8 || z == 0 || z == 10;
            for (int y = -1; y <= 7; y++) {
                if (!wall) { b.set(x, y, z, y == -1 ? Material.POLISHED_ANDESITE : Material.AIR); continue; }
                boolean window = (x == 0 || x == 8) && z % 3 == 2 && y >= 2 && y <= 5;
                b.set(x, y, z, window ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : stone(x0 + x, K + y, z0 + z));
            }
        }
        kit.roof(new ReachBrush(canvas, x0, K + 8, z0, 0), -1, 9, -1, 11, 0, ReachKit.Style.SLATE, true, true);
        for (int y = 0; y <= 2; y++) b.set(4, y, 10, Material.AIR);
        for (int z = 3; z <= 7; z += 2) for (int x : new int[] {2, 6}) b.set(x, 0, z, Material.SPRUCE_STAIRS, "facing=south,half=bottom");
        b.set(4, 0, 1, Material.CHISELED_STONE_BRICKS);
        b.set(4, 1, 1, Material.CANDLE, "candles=4,lit=true");
    }

    /** The treasury: a squat strongroom with a vault behind bars and the banker's counter. */
    private void treasury() {
        int x0 = CX - 10, x1 = CX + 10, z0 = CZ + 26, z1 = CZ + 33;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            site.reserve(x, z);
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            for (int y = K - 1; y <= K + 6; y++) {
                if (!wall) { canvas.set(x, y, z, y == K - 1 ? Material.POLISHED_DEEPSLATE : y == K + 6 ? Material.STONE_BRICKS : Material.AIR); continue; }
                canvas.set(x, y, z, (x - x0) % 4 == 0 ? Material.POLISHED_DEEPSLATE : stone(x, y, z));
            }
            if (wall && Math.floorMod(x + z, 2) == 0) canvas.set(x, K + 7, z, Material.STONE_BRICKS);
        }
        for (int x = CX - 1; x <= CX + 1; x++) for (int y = K; y <= K + 2; y++) canvas.set(x, y, z0, Material.AIR);
        canvas.set(CX, K, z0, Material.IRON_DOOR, "facing=north,half=lower,hinge=left,open=true");
        canvas.set(CX, K + 1, z0, Material.IRON_DOOR, "facing=north,half=upper,hinge=left,open=true");
        // The counter, then bars, then the vault of gold and strongboxes.
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            if (x != CX + 4) canvas.set(x, K, z0 + 3, Material.POLISHED_DEEPSLATE_SLAB, "type=top");
            for (int y = K; y <= K + 3; y++) canvas.set(x, y, z0 + 5, x == CX ? Material.AIR : Material.IRON_BARS);
        }
        for (int x = x0 + 1; x <= x1 - 1; x++) for (int y = K; y <= K + 1; y++) canvas.set(x, y, z1 - 1, (x + y) % 3 == 0 ? Material.GOLD_BLOCK : Material.CHEST, (x + y) % 3 == 0 ? "" : "facing=north,type=single");
        canvas.set(x0 + 2, K, z0 + 1, Material.LECTERN, "facing=south");
        canvas.set(CX, K + 4, z0 + 2, Material.LANTERN, "hanging=true");
        canvas.set(CX, K + 4, z1 - 2, Material.LANTERN, "hanging=true");
        site.mark("npc_orin_ledger", CX, K, z0 + 4, 180F);
    }

    /** The training yard: sand and gravel, archery butts, straw dummies with pumpkin heads, a weapon rack. */
    private void trainingYard() {
        int x0 = CX - 19, x1 = CX - 2, z0 = CZ - 6, z1 = CZ + 5;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            canvas.set(x, K - 1, z, terrain.grain(x, z, 0.4) > 0.2 ? Material.COARSE_DIRT : Material.GRAVEL);
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            if (edge && (x + z) % 7 != 0 && !(z == (z0 + z1) / 2)) canvas.set(x, K, z, Material.SPRUCE_FENCE);
        }
        for (int z = z0 + 2; z <= z1 - 2; z += 3) {
            canvas.set(x0 + 2, K, z, Material.HAY_BLOCK, "axis=y");
            canvas.set(x0 + 2, K + 1, z, Material.TARGET);
        }
        for (int x = x0 + 7; x <= x1 - 3; x += 4) {
            canvas.set(x, K, z0 + 3, Material.SPRUCE_FENCE);
            canvas.set(x, K + 1, z0 + 3, Material.HAY_BLOCK, "axis=y");
            canvas.set(x, K + 2, z0 + 3, Material.CARVED_PUMPKIN, "facing=south");
        }
        for (int x = x1 - 5; x <= x1 - 2; x++) {
            canvas.set(x, K, z1 - 1, Material.SPRUCE_FENCE);
            canvas.set(x, K + 1, z1 - 1, x % 2 == 0 ? Material.LIGHTNING_ROD : Material.IRON_BARS);
        }
        site.mark("npc_dorian_ash", (x0 + x1) / 2, K, (z0 + z1) / 2, 270F);
    }

    private void well(int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean centre = dx == 0 && dz == 0;
            canvas.set(cx + dx, K - 1, cz + dz, centre ? Material.WATER : Material.STONE_BRICKS);
            if (!centre) canvas.set(cx + dx, K, cz + dz, Material.STONE_BRICK_WALL, "up=true");
        }
        for (int[] c : new int[][] {{-1, -1}, {1, 1}}) for (int y = K + 1; y <= K + 2; y++) canvas.set(cx + c[0], y, cz + c[1], Material.SPRUCE_FENCE);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) canvas.set(cx + dx, K + 3, cz + dz, Material.SPRUCE_SLAB, "type=bottom");
        canvas.set(cx, K + 2, cz, Material.IRON_CHAIN, "axis=y");
        canvas.set(cx, K + 1, cz, Material.BARREL, "facing=up");
    }
}
