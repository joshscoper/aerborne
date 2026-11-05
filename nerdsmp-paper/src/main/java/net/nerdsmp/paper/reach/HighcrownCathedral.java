package net.nerdsmp.paper.reach;

import org.bukkit.Material;

/**
 * The Lantern Fane, Highcrown's cathedral, where the lantern for the Wardens who left is never let go out. It is laid
 * out the way a real Gothic cathedral is:
 * - a west front of twin towers with open belfries and slate spires, a recessed portal of three orders with a lantern
 *   in its tympanum, a rose window and a gable with a gallery;
 * - a nave of five bays with side aisles, a pointed arcade, a clerestory and a ribbed vault;
 * - flying buttresses with pinnacles carrying the high walls over the aisle roofs;
 * - a transept with a rose window in each end and a door to the citadel street and the churchyard;
 * - a crossing tower with lancets and the tallest spire in the city, the great lantern hanging under it;
 * - a choir and a round apse with tall lancets round the altar.
 * The walls are pale limestone (calcite and diorite) over a grey stone skeleton, the roofs dark slate, the finials
 * gold, so it reads from anywhere in the city as white stone under blue-grey roofs.
 */
final class HighcrownCathedral {
    /** Where the cathedral's spots and claimed columns go. */
    interface Site {
        void mark(String id, int x, int y, int z, float yaw);
        void reserve(int x, int z);
    }

    /** The terrace's walking level, and the cathedral floor's, one step up. */
    static final int GROUND = HighcrownCity.CROWN, G = GROUND + 1;
    /** The axis (z), the arcade lines and the aisle walls. */
    static final int AX = 892, NAVE_N = 886, NAVE_S = 898, AISLE_N = 881, AISLE_S = 903;
    /** Along x: the west front, the towers' east faces, the crossing, the apse's centre. */
    static final int WEST = 13, TOWER_E = 21, CROSS_W = 43, CROSS_E = 55, APSE_X = 59, MID_X = 49;
    /** The transept's end walls. */
    static final int TRANS_N = 876, TRANS_S = 908;
    /** Heights above the floor: the aisle walls, the high walls, the ridge, the towers' and the crossing tower's tops. */
    static final int AISLE_TOP = 11, WALL_TOP = 24, RIDGE = 32, TOWER_TOP = 43, CROSS_TOP = 42;
    /** The nave's piers along x. */
    static final int[] PIERS = {22, 26, 30, 34, 38, 42};
    /** The footprint the cathedral and its close claim. */
    static final int MIN_X = 8, MAX_X = 70, MIN_Z = 873, MAX_Z = 912;

    private static final Material[] GLASS = {Material.BLUE_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
        Material.BLUE_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE};

    private final ReachTerrain terrain;
    private final ReachCanvas canvas;
    private final Site site;

    HighcrownCathedral(ReachTerrain terrain, ReachCanvas canvas, Site site) {
        this.terrain = terrain;
        this.canvas = canvas;
        this.site = site;
    }

    void build() {
        for (int x = MIN_X; x <= MAX_X; x++) for (int z = MIN_Z; z <= MAX_Z; z++) site.reserve(x, z);
        ground();
        aisles();
        highWalls();
        westFront();
        transept();
        choirAndApse();
        roofs();
        interior();
        openings();
        crossingTower();
        towers();
        furnish();
        site.mark("fane", WEST - 4, GROUND, AX, 270F);
    }

    // ------------------------------------------------------------------ materials

    private void set(int x, int y, int z, Material m) { canvas.set(x, y, z, m); }

    private void set(int x, int y, int z, Material m, String state) { canvas.set(x, y, z, m, state); }

    /** The wall field: limestone, calcite with drifts of polished diorite, over a plinth of grey stone. */
    private Material ashlar(int x, int y, int z) {
        if (y <= G) return y == G - 1 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS;
        double c = terrain.patch(x * 0.9 + y * 0.6, z - y * 0.4, 0.18);
        return c > 0.45 ? Material.POLISHED_DIORITE : Material.CALCITE;
    }

    /** The skeleton: buttresses, piers, quoins; stone bricks, mossy where the damp gathers low down, cracked here and there. */
    private Material frame(int x, int y, int z) {
        double c = terrain.patch(x + y * 0.7, z - y * 0.5, 0.22);
        if (c - (y - G) * 0.08 > 0.45) return Material.MOSSY_STONE_BRICKS;
        if (c < -0.6) return Material.CRACKED_STONE_BRICKS;
        return Material.STONE_BRICKS;
    }

    /** Glass for a window light: a gold eye at the head, bands of white leading, blues and the odd red between. */
    private Material glass(int x, int y, int z, boolean head) {
        if (head) return Material.YELLOW_STAINED_GLASS_PANE;
        if (Math.floorMod(y, 4) == 0) return Material.WHITE_STAINED_GLASS_PANE;
        return GLASS[Math.floorMod(x * 7 + z * 3 + y * 5, GLASS.length)];
    }

    /**
     * Inside a pointed arch of the given width whose springing is {@code up = 0}: the two arcs of an equilateral arch,
     * each struck from the opposite spring. {@code a} is the cell's distance from the opening's centre line.
     */
    static boolean arch(double a, double up, double width) {
        if (a > (width - 1) / 2.0 + 0.01) return false;
        double hw = width / 2.0;
        return up <= 0 || (a + hw) * (a + hw) + up * up <= 4 * hw * hw + 0.01;
    }

    /** The top of an arch's opening above its springing, for the cell at {@code a} from the centre. */
    static int archTop(double a, double width) {
        int up = 0;
        while (arch(a, up + 1, width)) up++;
        return up;
    }

    /**
     * A pointed window in a wall. {@code alongX} means the wall runs along x at z = {@code fixed}. The lights run from
     * {@code y0} to the springing at {@code spring}, then rise into the arch; five-wide windows get two mullions.
     */
    private void lancet(boolean alongX, int fixed, int centre, int width, int y0, int spring) {
        for (int i = 0; i < width; i++) {
            double a = Math.abs(i - (width - 1) / 2.0);
            int along = centre - width / 2 + i;
            int top = spring + archTop(a, width);
            for (int y = y0; y <= top; y++) {
                int x = alongX ? along : fixed, z = alongX ? fixed : along;
                boolean mullion = width >= 5 && a == 1 && y <= spring + 1;
                set(x, y, z, mullion ? Material.POLISHED_DIORITE : glass(x, y, z, y == top && a == 0 && top > spring));
            }
        }
    }

    /** A rose window in a wall plane: a carved ring, eight spokes of white, petals of colour and a gold eye. */
    private void rose(boolean alongX, int fixed, int cAlong, int cy, double r) {
        int span = (int) Math.ceil(r);
        for (int da = -span; da <= span; da++) for (int dy = -span; dy <= span; dy++) {
            double d = Math.hypot(da, dy);
            if (d > r) continue;
            int x = alongX ? cAlong + da : fixed, z = alongX ? fixed : cAlong + da, y = cy + dy;
            Material m;
            if (d > r - 0.9) m = Material.CHISELED_STONE_BRICKS;
            else if (d < 1) m = Material.YELLOW_STAINED_GLASS_PANE;
            else if (da == 0 || dy == 0 || Math.abs(da) == Math.abs(dy)) m = d < 2.2 ? Material.ORANGE_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE;
            else m = d < 2.6 ? Material.RED_STAINED_GLASS_PANE : (Math.floorMod(da * dy, 3) == 0 ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : Material.BLUE_STAINED_GLASS_PANE);
            set(x, y, z, m);
        }
    }

    /** A pinnacle: a carved base, a shaft, a crocketed tip of rods and a gold finial. */
    private void pinnacle(int x, int y, int z, int height) {
        set(x, y, z, Material.CHISELED_STONE_BRICKS);
        for (int k = 1; k < height - 1; k++) set(x, y + k, z, Material.STONE_BRICK_WALL, "up=true");
        set(x, y + height - 1, z, Material.LIGHTNING_ROD, "facing=up");
    }

    /** A vault's height above the floor at {@code d} blocks from its ridge line: it springs at 23 and peaks at 28. */
    static int vault(int d) {
        return switch (Math.min(d, 5)) {
            case 5 -> 23;
            case 4 -> 25;
            case 3 -> 26;
            case 2 -> 27;
            default -> 28;
        };
    }

    /** The roof line over a span whose ridge is {@code d} away: eaves at 25 seven out, the ridge at 32. */
    static int roofLine(int d) { return 25 + 7 - d; }

    // ------------------------------------------------------------------ ground

    /** Grading and paving: a raised platform under the building, the parvis before the west door, paths round it. */
    private void ground() {
        for (int x = MIN_X; x <= MAX_X; x++) for (int z = MIN_Z; z <= MAX_Z; z++) {
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land()) continue;
            for (int y = c.top() + 1; y < GROUND - 1; y++) set(x, y, z, Material.STONE);
            for (int y = GROUND; y <= GROUND + 6; y++) if (!canvas.has(x, y, z)) set(x, y, z, Material.AIR);
            if (!canvas.has(x, GROUND - 1, z)) set(x, GROUND - 1, z, Material.GRASS_BLOCK);
        }
        // The parvis: a paved forecourt from the avenue to the portal, a compass of polished stone at its middle.
        for (int x = MIN_X + 1; x <= WEST - 1; x++) for (int z = 877; z <= 907; z++) {
            double r = Math.hypot(x - 9.5, z - AX);
            set(x, GROUND - 1, z, r < 2.5 ? Material.POLISHED_ANDESITE : Math.floorMod(x + z, 2) == 0 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
        }
        // A walk all the way round, one block out from the plinth.
        for (int x = WEST - 1; x <= APSE_X + 10; x++) for (int z = TRANS_N - 2; z <= TRANS_S + 2; z++) {
            if (inside(x, z) || !terrain.column(x, z).land()) continue;
            boolean nearWall = false;
            for (int dx = -2; dx <= 2 && !nearWall; dx++) for (int dz = -2; dz <= 2 && !nearWall; dz++) nearWall = inside(x + dx, z + dz);
            if (nearWall) set(x, GROUND - 1, z, terrain.grain(x, z, 0.4) > 0.55 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
        }
    }

    /** True inside the building's outline (walls included), not counting buttresses. */
    private boolean inside(int x, int z) {
        if (x >= WEST && x <= TOWER_E && z >= 878 && z <= 906) return true;
        if (x > TOWER_E && x < CROSS_W && z >= AISLE_N && z <= AISLE_S) return true;
        if (x >= CROSS_W && x <= CROSS_E && z >= TRANS_N && z <= TRANS_S) return true;
        if (x > CROSS_E && x <= APSE_X && z >= NAVE_N && z <= NAVE_S) return true;
        return x > APSE_X && Math.hypot(x - APSE_X, z - AX) < 6.5;
    }

    // ------------------------------------------------------------------ aisles and buttresses

    /** The aisle walls with a tall window in each bay, the buttresses between, and the flyers over the aisle roofs. */
    private void aisles() {
        for (int side : new int[] {-1, 1}) {
            int wz = side < 0 ? AISLE_N : AISLE_S;
            for (int x = TOWER_E + 1; x < CROSS_W; x++) for (int y = G - 1; y <= G + AISLE_TOP; y++) set(x, y, wz, ashlar(x, y, wz));
            // Floors and the plinth for the aisle interiors and the nave between them.
            for (int x = TOWER_E + 1; x < CROSS_W; x++) for (int z = AISLE_N; z <= AISLE_S; z++) set(x, G - 1, z, Material.STONE_BRICKS);
            for (int p = 1; p < PIERS.length - 1; p++) {
                int x = PIERS[p];
                int z1 = wz + side, z2 = wz + 2 * side;
                for (int y = G - 1; y <= G + 8; y++) set(x, y, z2, frame(x, y, z2));
                set(x, G + 9, z2, Material.STONE_BRICK_STAIRS, "facing=" + (side < 0 ? "south" : "north") + ",half=bottom");
                for (int y = G - 1; y <= G + AISLE_TOP + 7; y++) set(x, y, z1, frame(x, y, z1));
                pinnacle(x, G + AISLE_TOP + 8, z1, 5);
                // The flyer: a sloping beam from the buttress over the aisle roof to the high wall, arched beneath.
                for (int k = 1; k <= 5; k++) {
                    int y = G + AISLE_TOP + 7 + k, fz = wz - side * (k - 1);
                    set(x, y, fz, Material.STONE_BRICKS);
                    set(x, y + 1, fz, Material.STONE_BRICK_STAIRS, "facing=" + (side < 0 ? "south" : "north") + ",half=bottom");
                    set(x, y - 1, fz, Material.STONE_BRICK_STAIRS, "facing=" + (side < 0 ? "north" : "south") + ",half=top");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the high walls

    /** The arcade walls, carried up as the clerestory; their piers with shafts on both faces. */
    private void highWalls() {
        for (int wz : new int[] {NAVE_N, NAVE_S}) for (int x = TOWER_E + 1; x < CROSS_W; x++) for (int y = G - 1; y <= G + WALL_TOP; y++)
            set(x, y, wz, ashlar(x, y, wz));
    }

    // ------------------------------------------------------------------ west front

    /** The west front between the towers: the portal, the rose, the gable and its gallery. */
    private void westFront() {
        for (int z = NAVE_N + 1; z < NAVE_S; z++) {
            int top = G + roofLine(Math.abs(z - AX));
            for (int y = G - 1; y <= top; y++) set(WEST, y, z, ashlar(WEST, y, z));
            set(WEST, top + 1, z, Material.STONE_BRICK_STAIRS, "facing=" + (z < AX ? "south" : "north") + ",half=bottom");
        }
        set(WEST, G + RIDGE + 1, AX, Material.CHISELED_STONE_BRICKS);
        pinnacle(WEST, G + RIDGE + 2, AX, 4);
        // The portal: three orders stepping out from the wall, the doors in the innermost under a carved tympanum.
        int spring = G + 3;
        for (int z = AX - 6; z <= AX + 6; z++) {
            double a = Math.abs(z - AX);
            for (int y = G - 1; y <= G + 14; y++) {
                int up = y - spring;
                boolean door = arch(a, up, 5), order2 = arch(a, up, 7), order3 = arch(a, up, 9), hood = arch(a, up, 11);
                if (y == G - 1) {
                    if (order3) { set(WEST - 1, y, z, Material.POLISHED_ANDESITE); set(WEST - 2, y, z, Material.POLISHED_ANDESITE); }
                    continue;
                }
                if (door) {
                    set(WEST - 1, y, z, Material.AIR);
                    set(WEST - 2, y, z, Material.AIR);
                    set(WEST, y, z, y <= G + 1 ? Material.AIR : Math.abs(z - AX) == 0 && y == G + 4 ? Material.GOLD_BLOCK
                        : y == G + 2 ? Material.POLISHED_DIORITE : Material.CALCITE);
                } else if (order2) {
                    set(WEST - 1, y, z, Material.STONE_BRICKS);
                    set(WEST - 2, y, z, Material.AIR);
                } else if (order3) {
                    set(WEST - 1, y, z, Material.STONE_BRICKS);
                    set(WEST - 2, y, z, Material.CHISELED_STONE_BRICKS);
                } else if (hood && y > spring) {
                    set(WEST - 2, y, z, Material.STONE_BRICK_STAIRS, "facing=east,half=bottom");
                }
            }
        }
        for (int z : new int[] {AX - 2, AX - 1}) {
            set(WEST, G, z, Material.DARK_OAK_DOOR, "facing=west,half=lower,hinge=" + (z == AX - 2 ? "left" : "right") + ",open=false");
            set(WEST, G + 1, z, Material.DARK_OAK_DOOR, "facing=west,half=upper,hinge=" + (z == AX - 2 ? "left" : "right") + ",open=false");
        }
        for (int z : new int[] {AX + 1, AX + 2}) {
            set(WEST, G, z, Material.DARK_OAK_DOOR, "facing=west,half=lower,hinge=" + (z == AX + 1 ? "left" : "right") + ",open=false");
            set(WEST, G + 1, z, Material.DARK_OAK_DOOR, "facing=west,half=upper,hinge=" + (z == AX + 1 ? "left" : "right") + ",open=false");
        }
        set(WEST, G, AX, Material.POLISHED_DIORITE);
        set(WEST, G + 1, AX, Material.POLISHED_DIORITE);
        set(WEST - 1, G + 3, AX, Material.LANTERN, "hanging=true");
        // The step up from the parvis into the porch.
        for (int z = AX - 4; z <= AX + 4; z++) set(WEST - 3, GROUND, z, Material.STONE_BRICK_STAIRS, "facing=east,half=bottom");
        // The rose above, and the gallery along the foot of the gable on a corbel table.
        rose(false, WEST, AX, G + 18, 4.7);
        for (int z = NAVE_N + 1; z < NAVE_S; z++) {
            set(WEST - 1, G + WALL_TOP - 1, z, Material.STONE_BRICK_STAIRS, "facing=east,half=top");
            set(WEST - 1, G + WALL_TOP, z, Material.STONE_BRICK_SLAB, "type=bottom");
            set(WEST - 1, G + WALL_TOP + 1, z, Material.STONE_BRICK_WALL);
        }
        // A string course across the front under the rose, and slim shafts framing it.
        for (int z = NAVE_N + 1; z < NAVE_S; z++) set(WEST - 1, G + 12, z, Material.STONE_BRICK_STAIRS, "facing=east,half=top");
        for (int z : new int[] {AX - 5, AX + 5}) for (int y = G + 13; y <= G + 22; y++) set(WEST - 1, y, z, Material.STONE_BRICK_WALL, "up=true");
    }

    // ------------------------------------------------------------------ towers

    /** The twin west towers: angle buttresses, string courses, windows by stage, open belfries, parapets and spires. */
    private void towers() {
        for (int[] t : new int[][] {{878, -1}, {898, 1}}) tower(t[0], t[1]);
    }

    private void tower(int z0, int side) {
        int x0 = WEST, x1 = TOWER_E, z1 = z0 + 8, cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        int outer = side < 0 ? z0 : z1;
        int belfry = TOWER_TOP - 9;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = G - 1; y <= G + TOWER_TOP; y++) {
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            if (wall) set(x, y, z, ashlar(x, y, z));
            else set(x, y, z, y == G - 1 || y == G + belfry || y == G + TOWER_TOP ? Material.SPRUCE_PLANKS : Material.AIR);
        }
        // Angle buttresses at the outer corners, stepping in twice on their way up.
        String[] faceOf = {"east", "west", "south", "north"};
        for (int[] c : new int[][] {{x0, outer}, {x1, outer}, {x0, side < 0 ? z1 : z0}}) {
            int sx = c[0] == x0 ? -1 : 1, sz = c[1] == outer ? side : 0;
            for (int[] o : new int[][] {{sx, 0}, {0, sz}}) {
                if (o[0] == 0 && o[1] == 0) continue;
                String face = o[0] < 0 ? faceOf[0] : o[0] > 0 ? faceOf[1] : o[1] < 0 ? faceOf[2] : faceOf[3];
                int bx = c[0] + o[0], bz = c[1] + o[1];
                for (int y = G - 1; y <= G + belfry + 2; y++) set(bx, y, bz, frame(bx, y, bz));
                set(bx, G + belfry + 3, bz, Material.STONE_BRICK_STAIRS, "facing=" + face + ",half=bottom");
                int fx = bx + o[0], fz = bz + o[1];
                for (int y = G - 1; y <= G + 19; y++) set(fx, y, fz, frame(fx, y, fz));
                set(fx, G + 20, fz, Material.STONE_BRICK_STAIRS, "facing=" + face + ",half=bottom");
            }
        }
        // String courses on the open faces between the stages.
        for (int y : new int[] {G + 10, G + 21, G + belfry - 1}) {
            for (int z = z0 + 1; z < z1; z++) set(x0 - 1, y, z, Material.STONE_BRICK_STAIRS, "facing=east,half=top");
            for (int x = x0 + 1; x < x1; x++) set(x, y, outer + side, Material.STONE_BRICK_STAIRS, "facing=" + (side < 0 ? "south" : "north") + ",half=top");
        }
        // Windows by stage: a slit, a tall lancet, a small rose, then the belfry's twin openings.
        lancet(false, x0, cz, 1, G + 3, G + 7);
        lancet(true, outer, cx, 1, G + 3, G + 7);
        lancet(false, x0, cz, 3, G + 12, G + 17);
        lancet(true, outer, cx, 3, G + 12, G + 17);
        rose(false, x0, cz, G + 26, 2.7);
        rose(true, outer, cx, G + 26, 2.7);
        for (int[] face : new int[][] {{0, x0}, {1, outer}, {0, x1}}) {
            boolean alongX = face[0] == 1;
            int centre = alongX ? cx : cz;
            for (int i = -1; i <= 1; i++) {
                double a = Math.abs(i);
                for (int y = G + belfry + 1; y <= G + belfry + 5 + archTop(a, 3); y++) {
                    int x = alongX ? centre + i : face[1], z = alongX ? face[1] : centre + i;
                    boolean mullion = i == 0 && y <= G + belfry + 5;
                    set(x, y, z, mullion ? Material.STONE_BRICK_WALL : Material.AIR, mullion ? "up=true" : "");
                }
            }
            int sx = alongX ? 0 : (face[1] == x0 ? -1 : 1), sz = alongX ? side : 0;
            for (int i = -1; i <= 1; i++) {
                int x = alongX ? centre + i : face[1] + sx, z = alongX ? face[1] + sz : centre + i;
                set(x, G + belfry, z, Material.STONE_BRICK_SLAB, "type=top");
            }
        }
        // The bell under the belfry's beam.
        for (int x = x0 + 1; x < x1; x++) set(x, G + belfry + 6, cz, Material.DARK_OAK_LOG, "axis=x");
        set(cx, G + belfry + 5, cz, Material.BELL, "attachment=ceiling,facing=west");
        // Corbel table, parapet and corner pinnacles.
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            boolean edge = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
            if (!edge) continue;
            boolean corner = (x == x0 - 1 || x == x1 + 1) && (z == z0 - 1 || z == z1 + 1);
            set(x, G + TOWER_TOP, z, Material.STONE_BRICK_STAIRS, x == x0 - 1 ? "facing=east,half=top" : x == x1 + 1 ? "facing=west,half=top"
                : z == z0 - 1 ? "facing=south,half=top" : "facing=north,half=top");
            if (corner) pinnacle(x, G + TOWER_TOP + 1, z, 6);
            else set(x, G + TOWER_TOP + 1, z, Material.STONE_BRICK_WALL);
        }
        spire(cx, cz, 3.6, G + TOWER_TOP + 1, 24);
    }

    /**
     * An octagonal spire of slate from {@code y0}, {@code r0} across at its foot, closing to a point {@code height} up:
     * full tiles on its faces, stairs wherever a course steps in, a gilded ball and rod on top.
     */
    private void spire(int cx, int cz, double r0, int y0, int height) {
        for (int k = 0; k < height; k++) {
            double r = r0 * (1 - (double) k / height), next = r0 * (1 - (double) (k + 1) / height);
            int span = (int) Math.ceil(r);
            for (int dx = -span; dx <= span; dx++) for (int dz = -span; dz <= span; dz++) {
                if (!HighcrownCastle.inOctagon(dx, dz, r)) continue;
                int x = cx + dx, z = cz + dz, y = y0 + k;
                boolean steps = !HighcrownCastle.inOctagon(dx, dz, next);
                if (!steps) { set(x, y, z, Material.DEEPSLATE_TILES); continue; }
                String face = Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? "east" : "west") : (dz < 0 ? "south" : "north");
                set(x, y, z, Material.DEEPSLATE_TILE_STAIRS, "facing=" + face + ",half=bottom");
            }
        }
        set(cx, y0 + height, cz, Material.GOLD_BLOCK);
        set(cx, y0 + height + 1, cz, Material.LIGHTNING_ROD, "facing=up");
    }

    // ------------------------------------------------------------------ transept

    /** The transept arms: tall windowed walls, the end façades with doors, lancets, a rose and a gable. */
    private void transept() {
        for (int x = CROSS_W; x <= CROSS_E; x++) for (int z = TRANS_N; z <= TRANS_S; z++) {
            boolean crossing = z > NAVE_N - 1 && z < NAVE_S + 1;
            boolean wall = x == CROSS_W || x == CROSS_E || z == TRANS_N || z == TRANS_S;
            set(x, G - 1, z, wall ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
            if (!wall || crossing && (x == CROSS_W || x == CROSS_E)) continue;
            for (int y = G; y <= G + WALL_TOP; y++) set(x, y, z, ashlar(x, y, z));
        }
        // The crossing's walls: solid above its four arches.
        for (int x = CROSS_W; x <= CROSS_E; x++) for (int z : new int[] {NAVE_N, NAVE_S}) for (int y = G; y <= G + WALL_TOP; y++) set(x, y, z, ashlar(x, y, z));
        for (int z = NAVE_N; z <= NAVE_S; z++) for (int x : new int[] {CROSS_W, CROSS_E}) for (int y = G; y <= G + WALL_TOP; y++) set(x, y, z, ashlar(x, y, z));
        for (int side : new int[] {-1, 1}) {
            int ez = side < 0 ? TRANS_N : TRANS_S;
            // The gable with its coping and a pinnacle at the apex.
            for (int x = CROSS_W - 1; x <= CROSS_E + 1; x++) {
                int d = Math.abs(x - MID_X), top = G + roofLine(d);
                if (d > 6) continue;
                for (int y = G + WALL_TOP + 1; y <= top; y++) set(x, y, ez, ashlar(x, y, ez));
                set(x, top + 1, ez, Material.STONE_BRICK_STAIRS, "facing=" + (x < MID_X ? "east" : "west") + ",half=bottom");
            }
            set(MID_X, G + RIDGE + 1, ez, Material.CHISELED_STONE_BRICKS);
            pinnacle(MID_X, G + RIDGE + 2, ez, 4);
            rose(true, ez, MID_X, G + 16, 4.7);
            lancet(true, ez, MID_X - 3, 3, G + 5, G + 9);
            lancet(true, ez, MID_X + 3, 3, G + 5, G + 9);
            // The door: a single leaf in a pointed recess, the arch carried out one block on shafts.
            for (int dx = -2; dx <= 2; dx++) for (int y = G; y <= G + 5; y++) {
                double a = Math.abs(dx);
                int up = y - (G + 2);
                if (arch(a, up, 3)) set(MID_X + dx, y, ez + side, Material.AIR);
                else if (arch(a, up, 5)) set(MID_X + dx, y, ez + side, a == 2 && y <= G + 2 ? Material.STONE_BRICK_WALL : Material.STONE_BRICKS, a == 2 && y <= G + 2 ? "up=true" : "");
                if (arch(a, up, 3)) set(MID_X + dx, y, ez, Math.abs(dx) == 0 && y <= G + 1 ? Material.AIR : Material.CALCITE);
            }
            set(MID_X, G - 1, ez + side, Material.POLISHED_ANDESITE);
            set(MID_X - 1, G - 1, ez + side, Material.POLISHED_ANDESITE);
            set(MID_X + 1, G - 1, ez + side, Material.POLISHED_ANDESITE);
            for (int dx = -1; dx <= 1; dx++) set(MID_X + dx, GROUND - 1, ez + 2 * side, Material.STONE_BRICKS);
            set(MID_X, G, ez, Material.DARK_OAK_DOOR, "facing=" + (side < 0 ? "north" : "south") + ",half=lower,hinge=left,open=false");
            set(MID_X, G + 1, ez, Material.DARK_OAK_DOOR, "facing=" + (side < 0 ? "north" : "south") + ",half=upper,hinge=left,open=false");
            set(MID_X, G + 3, ez + side, Material.LANTERN, "hanging=true");
            set(MID_X, G - 1, ez + 2 * side, Material.STONE_BRICK_STAIRS, "facing=" + (side < 0 ? "south" : "north") + ",half=bottom");
            // Clasping buttresses at the end's corners, pinnacles above the gable's foot.
            for (int x : new int[] {CROSS_W, CROSS_E}) {
                int sx = x == CROSS_W ? -1 : 1;
                for (int[] o : new int[][] {{sx, 0}, {0, side}, {sx, side}}) {
                    int bx = x + o[0], bz = ez + o[1];
                    for (int y = G - 1; y <= G + WALL_TOP; y++) set(bx, y, bz, frame(bx, y, bz));
                }
                pinnacle(x + sx, G + WALL_TOP + 1, ez + side, 7);
                set(x, G + WALL_TOP + 1, ez + side, Material.STONE_BRICK_STAIRS, "facing=" + (side < 0 ? "south" : "north") + ",half=bottom");
                set(x + sx, G + WALL_TOP + 1, ez, Material.STONE_BRICK_STAIRS, "facing=" + (sx < 0 ? "east" : "west") + ",half=bottom");
            }
            // Tall windows down the arms' sides: two to the east, one to the west above where the aisle meets it.
            int armMid = ez - side * 5;
            lancet(false, CROSS_E, armMid - 2, 3, G + 3, G + 16);
            lancet(false, CROSS_E, armMid + 2, 3, G + 3, G + 16);
            lancet(false, CROSS_W, ez - side * 2, 3, G + 3, G + 16);
            // A buttress between the two east windows.
            for (int y = G - 1; y <= G + 20; y++) set(CROSS_E + 1, y, armMid, frame(CROSS_E + 1, y, armMid));
            set(CROSS_E + 1, G + 21, armMid, Material.STONE_BRICK_STAIRS, "facing=west,half=bottom");
        }
    }

    // ------------------------------------------------------------------ choir and apse

    /** The choir's walls with tall lancets, and the round apse with its windows and radiating buttresses. */
    private void choirAndApse() {
        for (int x = CROSS_E + 1; x <= APSE_X; x++) for (int z = NAVE_N; z <= NAVE_S; z++) {
            set(x, G - 1, z, Material.STONE_BRICKS);
            if (z == NAVE_N || z == NAVE_S) for (int y = G; y <= G + WALL_TOP; y++) set(x, y, z, ashlar(x, y, z));
        }
        lancet(true, NAVE_N, 57, 3, G + 3, G + 16);
        lancet(true, NAVE_S, 57, 3, G + 3, G + 16);
        for (int x = APSE_X; x <= APSE_X + 8; x++) for (int z = AX - 8; z <= AX + 8; z++) {
            double r = Math.hypot(x - APSE_X, z - AX);
            if (r < 6.5) set(x, G - 1, z, Material.STONE_BRICKS);
            if (r < 5.5 || r >= 6.5 || x < APSE_X) continue;
            double a = Math.toDegrees(Math.atan2(z - AX, x - APSE_X));
            boolean window = false;
            for (double w : new double[] {-60, -30, 0, 30, 60}) if (Math.abs(a - w) < 6.5) window = true;
            for (int y = G - 1; y <= G + WALL_TOP; y++) {
                boolean light = window && y >= G + 3 && y <= G + 18;
                set(x, y, z, light ? glass(x, y, z, y == G + 18) : ashlar(x, y, z));
            }
        }
        // Radiating buttresses between the windows, stepping out and topped with pinnacles.
        for (double w : new double[] {-90, -45, -15, 15, 45, 90}) {
            double rad = Math.toRadians(w);
            for (double r = 6.5; r <= 8.5; r += 0.5) {
                int x = (int) Math.round(APSE_X + r * Math.cos(rad)), z = (int) Math.round(AX + r * Math.sin(rad));
                if (x < APSE_X) continue;
                int top = r > 7.6 ? G + 10 : G + 20;
                for (int y = G - 1; y <= top; y++) set(x, y, z, frame(x, y, z));
            }
            int px = (int) Math.round(APSE_X + 7 * Math.cos(rad)), pz = (int) Math.round(AX + 7 * Math.sin(rad));
            if (px >= APSE_X) pinnacle(px, G + 21, pz, 5);
        }
    }

    // ------------------------------------------------------------------ roofs

    /**
     * The roofs, all slate at the one pitch: the nave and choir along the axis, the transept across it, the aisles'
     * lean-tos against the high walls, the apse a half cone. A cresting runs along every ridge.
     */
    private void roofs() {
        // Nave (between the towers too) and choir: ridge along x.
        for (int x = WEST + 1; x <= APSE_X; x++) {
            if (x >= CROSS_W && x <= CROSS_E) continue;
            boolean narthex = x <= TOWER_E;
            for (int z = AX - 7; z <= AX + 7; z++) {
                if (narthex && (z <= NAVE_N || z >= NAVE_S)) continue;
                int d = Math.abs(z - AX), top = G + roofLine(d);
                for (int y = G + WALL_TOP + 1; y < top; y++) set(x, y, z, Material.DEEPSLATE_TILES);
                if (d == 0) {
                    set(x, top, z, Material.DEEPSLATE_TILES);
                    if (Math.floorMod(x, 2) == 0) set(x, top + 1, z, Material.DEEPSLATE_TILE_WALL);
                } else set(x, top, z, Material.DEEPSLATE_TILE_STAIRS, "facing=" + (z < AX ? "south" : "north") + ",half=bottom");
            }
        }
        // Transept arms: ridge along z.
        for (int z = TRANS_N; z <= TRANS_S; z++) {
            if (z >= NAVE_N && z <= NAVE_S) continue;
            for (int x = MID_X - 7; x <= MID_X + 7; x++) {
                int d = Math.abs(x - MID_X), top = G + roofLine(d);
                if (z == TRANS_N || z == TRANS_S) continue;
                for (int y = G + WALL_TOP + 1; y < top; y++) set(x, y, z, Material.DEEPSLATE_TILES);
                if (d == 0) {
                    set(x, top, z, Material.DEEPSLATE_TILES);
                    if (Math.floorMod(z, 2) == 0) set(x, top + 1, z, Material.DEEPSLATE_TILE_WALL);
                } else set(x, top, z, Material.DEEPSLATE_TILE_STAIRS, "facing=" + (x < MID_X ? "east" : "west") + ",half=bottom");
            }
        }
        // Aisles: lean-tos from the eaves over the aisle wall up against the clerestory.
        for (int side : new int[] {-1, 1}) for (int x = TOWER_E + 1; x < CROSS_W; x++) for (int k = 0; k <= 5; k++) {
            int z = (side < 0 ? AISLE_N - 1 : AISLE_S + 1) - side * k, top = G + AISLE_TOP + 1 + k;
            if (canvas.has(x, top, z) && !canvas.get(x, top, z).state().equals("minecraft:air") && k == 0) continue;
            for (int y = G + AISLE_TOP + 1; y < top; y++) set(x, y, z, Material.DEEPSLATE_TILES);
            set(x, top, z, Material.DEEPSLATE_TILE_STAIRS, "facing=" + (side < 0 ? "south" : "north") + ",half=bottom");
        }
        // The apse: a half cone meeting the choir's ridge.
        for (int k = 0; k <= 7; k++) for (int x = APSE_X + 1; x <= APSE_X + 8; x++) for (int z = AX - 8; z <= AX + 8; z++) {
            double r = Math.hypot(x - APSE_X, z - AX);
            if (r >= 7.5 - k) continue;
            int y = G + WALL_TOP + 1 + k;
            boolean steps = r >= 6.5 - k;
            if (!steps) { set(x, y, z, Material.DEEPSLATE_TILES); continue; }
            int dx = x - APSE_X, dz = z - AX;
            String face = Math.abs(dx) >= Math.abs(dz) ? "west" : dz < 0 ? "south" : "north";
            set(x, y, z, Material.DEEPSLATE_TILE_STAIRS, "facing=" + face + ",half=bottom");
        }
        set(APSE_X, G + RIDGE + 1, AX, Material.GOLD_BLOCK);
        set(APSE_X, G + RIDGE + 2, AX, Material.LIGHTNING_ROD, "facing=up");
    }

    // ------------------------------------------------------------------ interior volumes

    /** Carves the interior: the vaulted nave, narthex, arms and choir, the aisles under their ceilings, the apse. */
    private void interior() {
        // Nave, narthex and choir: vaulted along x.
        for (int x = WEST + 1; x <= APSE_X; x++) {
            if (x >= CROSS_W && x <= CROSS_E) continue;
            for (int z = NAVE_N + 1; z < NAVE_S; z++) vaultColumn(x, z, Math.abs(z - AX), z < AX ? "north" : "south", isPier(x));
        }
        // The arms: vaulted along z.
        for (int z = TRANS_N + 1; z < TRANS_S; z++) {
            if (z >= NAVE_N && z <= NAVE_S) continue;
            for (int x = CROSS_W + 1; x < CROSS_E; x++) vaultColumn(x, z, Math.abs(x - MID_X), x < MID_X ? "west" : "east", z == TRANS_N + 5 || z == TRANS_S - 5);
        }
        // The crossing: open up into the tower to its painted ceiling.
        for (int x = CROSS_W + 1; x < CROSS_E; x++) for (int z = NAVE_N + 1; z < NAVE_S; z++) {
            set(x, G - 1, z, Material.STONE_BRICKS);
            for (int y = G; y < G + CROSS_TOP - 1; y++) set(x, y, z, Material.AIR);
            int d = Math.max(Math.abs(x - MID_X), Math.abs(z - AX));
            set(x, G + CROSS_TOP - 1, z, d == 0 ? Material.GOLD_BLOCK : (Math.abs(x - MID_X) == Math.abs(z - AX) || d == 5) ? Material.POLISHED_ANDESITE : Material.CALCITE);
        }
        // The crossing's four great arches, cut to the shape of the vaults they open into.
        for (int i = -5; i <= 5; i++) {
            int top = G + vault(Math.abs(i));
            for (int y = G; y < top; y++) {
                set(CROSS_W, y, AX + i, Material.AIR);
                set(CROSS_E, y, AX + i, Material.AIR);
                set(MID_X + i, y, NAVE_N, Material.AIR);
                set(MID_X + i, y, NAVE_S, Material.AIR);
            }
        }
        // Aisles: a flat ceiling on corbels.
        for (int side : new int[] {-1, 1}) for (int x = TOWER_E + 1; x < CROSS_W; x++) for (int k = 1; k <= 4; k++) {
            int z = (side < 0 ? AISLE_N : AISLE_S) - side * k;
            for (int y = G; y < G + AISLE_TOP; y++) set(x, y, z, Material.AIR);
            set(x, G + AISLE_TOP, z, Material.CALCITE);
            if (k == 1) set(x, G + AISLE_TOP - 1, z, Material.STONE_BRICK_STAIRS, "facing=" + (side < 0 ? "north" : "south") + ",half=top");
            if (k == 4) set(x, G + AISLE_TOP - 1, z, Material.STONE_BRICK_STAIRS, "facing=" + (side < 0 ? "south" : "north") + ",half=top");
            if (isPier(x)) set(x, G + AISLE_TOP - 1, z, Material.POLISHED_ANDESITE_SLAB, "type=top");
        }
        // The apse: open to a ribbed flat ceiling.
        for (int x = APSE_X + 1; x <= APSE_X + 6; x++) for (int z = AX - 6; z <= AX + 6; z++) {
            double r = Math.hypot(x - APSE_X, z - AX);
            if (r >= 5.5) continue;
            for (int y = G; y <= G + WALL_TOP - 1; y++) set(x, y, z, Material.AIR);
            double a = Math.toDegrees(Math.atan2(z - AX, x - APSE_X));
            boolean rib = false;
            for (double w : new double[] {-90, -45, -15, 15, 45, 90}) if (Math.abs(a - w) < 5) rib = true;
            set(x, G + WALL_TOP, z, rib ? Material.POLISHED_ANDESITE : Material.CALCITE);
        }
        // A chancel arch closes the choir's vault where the apse's lower ceiling begins.
        for (int z = NAVE_N + 1; z < NAVE_S; z++) for (int y = G + WALL_TOP; y <= G + vault(Math.abs(z - AX)); y++) set(APSE_X, y, z, Material.POLISHED_DIORITE);
    }

    private static boolean isPier(int x) {
        for (int p : PIERS) if (p == x) return true;
        return x == 17 || x == 57;
    }

    /**
     * One column of a ribbed vault: air below, the vault's surface at its height, smoothed with stairs where it steps.
     * On a rib (a pier's line, or the ridge) the surface is grey stone, elsewhere limestone.
     */
    private void vaultColumn(int x, int z, int d, String out, boolean rib) {
        int top = G + vault(d);
        if (!canvas.has(x, G - 1, z) || canvas.get(x, G - 1, z).state().contains("air")) set(x, G - 1, z, Material.STONE_BRICKS);
        for (int y = G; y < top; y++) set(x, y, z, Material.AIR);
        boolean grey = rib || d == 0;
        set(x, top, z, grey ? Material.POLISHED_ANDESITE : Material.CALCITE);
        for (int y = top + 1; y <= G + WALL_TOP; y++) set(x, y, z, Material.CALCITE);
        if (d < 5 && vault(d + 1) < vault(d))
            set(x, top - 1, z, grey ? Material.POLISHED_ANDESITE_STAIRS : Material.STONE_BRICK_STAIRS, "facing=" + out + ",half=top");
    }

    // ------------------------------------------------------------------ openings

    /** The nave arcade, the aisle and clerestory windows, the side doors, the shafts and string courses. */
    private void openings() {
        for (int b = 0; b < PIERS.length - 1; b++) {
            int mid = PIERS[b] + 2;
            for (int side : new int[] {-1, 1}) {
                int hz = side < 0 ? NAVE_N : NAVE_S, az = side < 0 ? AISLE_N : AISLE_S, in = side < 0 ? NAVE_N + 1 : NAVE_S - 1;
                // The arcade arch into the aisle.
                for (int i = -1; i <= 1; i++) for (int y = G; y <= G + 7 + archTop(Math.abs(i), 3); y++) set(mid + i, y, hz, Material.AIR);
                // Aisle window (a door below it in the middle bay), clerestory window above the aisle roof.
                if (b == 2) {
                    lancet(true, az, mid, 3, G + 4, G + 7);
                    for (int y = G; y <= G + 2; y++) for (int i = -1; i <= 1; i++) set(mid + i, y, az, y == G + 2 ? Material.STONE_BRICKS : Material.POLISHED_DIORITE);
                    set(mid, G, az, Material.DARK_OAK_DOOR, "facing=" + (side < 0 ? "north" : "south") + ",half=lower,hinge=left,open=false");
                    set(mid, G + 1, az, Material.DARK_OAK_DOOR, "facing=" + (side < 0 ? "north" : "south") + ",half=upper,hinge=left,open=false");
                    for (int i = -1; i <= 1; i++) set(mid + i, G + 3, az + side, Material.STONE_BRICK_SLAB, "type=top");
                    set(mid, G - 1, az + side, Material.STONE_BRICK_STAIRS, "facing=" + (side < 0 ? "south" : "north") + ",half=bottom");
                } else {
                    lancet(true, az, mid, 3, G + 2, G + 7);
                    for (int i = -1; i <= 1; i++) set(mid + i, G + 1, az + side, Material.STONE_BRICK_STAIRS, "facing=" + (side < 0 ? "south" : "north") + ",half=top");
                }
                lancet(true, hz, mid, 3, G + 18, G + 20);
                // Banners of the Fane in the triforium band, a hanging lamp in the aisle bay.
                set(mid, G + 14, in, Material.BLUE_WALL_BANNER, "facing=" + (side < 0 ? "south" : "north"));
                int lz = az - side * 2;
                set(mid, G + AISLE_TOP - 1, lz, Material.IRON_CHAIN, "axis=y");
                set(mid, G + AISLE_TOP - 2, lz, Material.LANTERN, "hanging=true");
            }
        }
        // Piers: the arcade's grey columns, with shafts rising to the vault on the nave side and to the aisle ceiling.
        for (int i = 0; i < PIERS.length; i++) {
            int x = PIERS[i];
            for (int side : new int[] {-1, 1}) {
                int hz = side < 0 ? NAVE_N : NAVE_S, in = hz - side, out = hz + side;
                for (int y = G; y <= G + 8; y++) set(x, y, hz, y == G + 8 ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_ANDESITE);
                for (int y = G; y < G + vault(5); y++) set(x, y, in, y == G ? Material.POLISHED_ANDESITE : Material.STONE_BRICK_WALL, y == G ? "" : "up=true");
                for (int y = G; y < G + AISLE_TOP - 1; y++) set(x, y, out, y == G ? Material.POLISHED_ANDESITE : Material.STONE_BRICK_WALL, y == G ? "" : "up=true");
            }
        }
        // String courses along the nave: one under the triforium, one under the clerestory sills.
        for (int x = TOWER_E + 1; x < CROSS_W; x++) {
            if (isPier(x)) continue;
            for (int side : new int[] {-1, 1}) {
                int in = side < 0 ? NAVE_N + 1 : NAVE_S - 1;
                String face = side < 0 ? "north" : "south";
                set(x, G + 11, in, Material.STONE_BRICK_STAIRS, "facing=" + face + ",half=top");
                set(x, G + 17, in, Material.STONE_BRICK_STAIRS, "facing=" + face + ",half=top");
            }
        }
        // Doorways from each aisle into its transept arm.
        for (int z : new int[] {AISLE_N + 1, AISLE_N + 2, AISLE_N + 3, AISLE_N + 4, AISLE_S - 4, AISLE_S - 3, AISLE_S - 2, AISLE_S - 1})
            for (int y = G; y <= G + 6; y++) set(CROSS_W, y, z, Material.AIR);
    }

    // ------------------------------------------------------------------ crossing tower

    /** The lantern tower over the crossing: lancet pairs on every face, a parapet with pinnacles, and the high spire. */
    private void crossingTower() {
        for (int x = CROSS_W; x <= CROSS_E; x++) for (int z = NAVE_N; z <= NAVE_S; z++) {
            boolean wall = x == CROSS_W || x == CROSS_E || z == NAVE_N || z == NAVE_S;
            if (!wall) continue;
            for (int y = G + WALL_TOP + 1; y < G + CROSS_TOP; y++) set(x, y, z, ashlar(x, y, z));
        }
        for (int c : new int[] {MID_X - 3, MID_X + 3}) {
            lancet(true, NAVE_N, c, 3, G + 33, G + 37);
            lancet(true, NAVE_S, c, 3, G + 33, G + 37);
        }
        for (int c : new int[] {AX - 3, AX + 3}) {
            lancet(false, CROSS_W, c, 3, G + 33, G + 37);
            lancet(false, CROSS_E, c, 3, G + 33, G + 37);
        }
        // Corner buttresses up the tower, then the corbel table, parapet and pinnacles.
        for (int[] c : new int[][] {{CROSS_W, NAVE_N}, {CROSS_E, NAVE_N}, {CROSS_W, NAVE_S}, {CROSS_E, NAVE_S}}) {
            int sx = c[0] == CROSS_W ? -1 : 1, sz = c[1] == NAVE_N ? -1 : 1;
            for (int y = G + WALL_TOP + 1; y < G + CROSS_TOP; y++) {
                set(c[0] + sx, y, c[1], frame(c[0] + sx, y, c[1]));
                set(c[0], y, c[1] + sz, frame(c[0], y, c[1] + sz));
            }
        }
        for (int x = CROSS_W; x <= CROSS_E; x++) for (int z = NAVE_N; z <= NAVE_S; z++) set(x, G + CROSS_TOP, z, Material.STONE_BRICKS);
        for (int x = CROSS_W - 1; x <= CROSS_E + 1; x++) for (int z = NAVE_N - 1; z <= NAVE_S + 1; z++) {
            boolean edge = x == CROSS_W - 1 || x == CROSS_E + 1 || z == NAVE_N - 1 || z == NAVE_S + 1;
            if (!edge) continue;
            boolean corner = (x == CROSS_W - 1 || x == CROSS_E + 1) && (z == NAVE_N - 1 || z == NAVE_S + 1);
            if (corner) { pinnacle(x, G + CROSS_TOP, z, 8); continue; }
            set(x, G + CROSS_TOP, z, Material.STONE_BRICK_STAIRS, x == CROSS_W - 1 ? "facing=east,half=top" : x == CROSS_E + 1 ? "facing=west,half=top"
                : z == NAVE_N - 1 ? "facing=south,half=top" : "facing=north,half=top");
            set(x, G + CROSS_TOP + 1, z, Material.STONE_BRICK_WALL);
        }
        spire(MID_X, AX, 5.6, G + CROSS_TOP + 1, 30);
    }

    // ------------------------------------------------------------------ furnishing

    /** Floors, pews, fonts, the choir stalls and altar, the ever-burning lantern, and light. */
    private void furnish() {
        // The nave floor: polished stone with a pale runner up the middle to the crossing's compass.
        for (int x = WEST + 1; x < CROSS_W; x++) for (int z = NAVE_N + 1; z < NAVE_S; z++) {
            int d = Math.abs(z - AX);
            set(x, G - 1, z, d <= 1 ? Material.POLISHED_DIORITE : isPier(x) ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
            if (d <= 1 && x > WEST + 1) set(x, G, z, Material.BLUE_CARPET);
        }
        for (int x = CROSS_W + 1; x < CROSS_E; x++) for (int z = NAVE_N + 1; z < NAVE_S; z++) {
            double r = Math.hypot(x - MID_X, z - AX);
            set(x, G - 1, z, r < 1.5 ? Material.GOLD_BLOCK : r < 2.5 ? Material.POLISHED_DEEPSLATE : r < 3.5 ? Material.CALCITE
                : r < 4.5 ? Material.POLISHED_DEEPSLATE : Material.POLISHED_ANDESITE);
        }
        // Pews in rows down both sides of the runner, facing the altar.
        for (int x = TOWER_E + 2; x < CROSS_W - 1; x += 2) {
            if (isPier(x)) continue;
            for (int z = NAVE_N + 2; z < NAVE_S - 1; z++) {
                if (Math.abs(z - AX) <= 1) continue;
                set(x, G, z, Material.SPRUCE_STAIRS, "facing=west,half=bottom");
            }
        }
        // Holy-water fonts by the door.
        for (int z : new int[] {AX - 4, AX + 4}) {
            set(WEST + 2, G, z, Material.CHISELED_STONE_BRICKS);
            set(WEST + 2, G + 1, z, Material.WATER_CAULDRON, "level=3");
        }
        // Chandeliers down the nave: long chains from the vault's ridge.
        for (int b = 0; b < PIERS.length - 1; b++) {
            int x = PIERS[b] + 2;
            for (int y = G + 16; y < G + vault(0); y++) set(x, y, AX, Material.IRON_CHAIN, "axis=y");
            set(x, G + 15, AX, Material.LANTERN, "hanging=true");
        }
        // The great lantern hanging in the crossing: a gold and copper cage round a burning heart.
        int ly = G + 11;
        for (int y = ly + 5; y < G + CROSS_TOP - 1; y++) set(MID_X, y, AX, Material.IRON_CHAIN, "axis=y");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean corner = dx != 0 && dz != 0, centre = dx == 0 && dz == 0;
            set(MID_X + dx, ly + 4, AX + dz, centre ? Material.GOLD_BLOCK : Material.WAXED_CUT_COPPER_SLAB, centre ? "" : "type=bottom");
            for (int y = ly + 1; y <= ly + 3; y++)
                set(MID_X + dx, y, AX + dz, centre ? Material.SHROOMLIGHT : corner ? Material.WAXED_CUT_COPPER : Material.YELLOW_STAINED_GLASS);
            set(MID_X + dx, ly, AX + dz, centre ? Material.GOLD_BLOCK : Material.WAXED_CUT_COPPER_SLAB, centre ? "" : "type=top");
        }
        set(MID_X, ly - 1, AX, Material.LIGHTNING_ROD, "facing=down");
        // Lamps on the crossing's piers.
        for (int[] p : new int[][] {{CROSS_W + 1, NAVE_N + 1}, {CROSS_E - 1, NAVE_N + 1}, {CROSS_W + 1, NAVE_S - 1}, {CROSS_E - 1, NAVE_S - 1}}) {
            set(p[0], G, p[1], Material.POLISHED_ANDESITE);
            set(p[0], G + 1, p[1], Material.STONE_BRICK_WALL);
            set(p[0], G + 2, p[1], Material.LANTERN);
        }
        // The choir: stalls along both walls and the sanctuary raised a step, the altar and the lantern behind it.
        for (int x = CROSS_E + 1; x <= APSE_X - 1; x++) {
            set(x, G, NAVE_N + 1, Material.SPRUCE_STAIRS, "facing=north,half=bottom");
            set(x, G, NAVE_S - 1, Material.SPRUCE_STAIRS, "facing=south,half=bottom");
        }
        for (int x = APSE_X; x <= APSE_X + 5; x++) for (int z = AX - 5; z <= AX + 5; z++) {
            if (Math.hypot(x - APSE_X, z - AX) >= 5.5) continue;
            set(x, G, z, x == APSE_X ? Material.STONE_BRICK_STAIRS : Material.POLISHED_DIORITE, x == APSE_X ? "facing=east,half=bottom" : "");
        }
        for (int z = AX - 1; z <= AX + 1; z++) {
            set(APSE_X + 2, G + 1, z, z == AX ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_DIORITE);
            set(APSE_X + 2, G + 2, z, Material.CANDLE, "candles=" + (z == AX ? 4 : 3) + ",lit=true");
        }
        set(APSE_X + 4, G + 1, AX, Material.GOLD_BLOCK);
        set(APSE_X + 4, G + 2, AX, Material.STONE_BRICK_WALL);
        set(APSE_X + 4, G + 3, AX, Material.LANTERN);
        for (int z : new int[] {AX - 3, AX + 3}) set(APSE_X + 3, G + 1, z, Material.CANDLE, "candles=2,lit=true");
        // Light in the narthex and the arms.
        for (int[] p : new int[][] {{17, AX - 4}, {17, AX + 4}, {MID_X, TRANS_N + 3}, {MID_X, TRANS_S - 3}}) {
            for (int y = G + 18; y < G + vault(Math.abs(p[0] < CROSS_W ? p[1] - AX : 0)); y++) set(p[0], y, p[1], Material.IRON_CHAIN, "axis=y");
            set(p[0], G + 17, p[1], Material.LANTERN, "hanging=true");
        }
    }
}
