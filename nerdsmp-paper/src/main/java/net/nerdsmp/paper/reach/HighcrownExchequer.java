package net.nerdsmp.paper.reach;

import org.bukkit.Material;

/**
 * The Highcrown Exchequer, the city's bank, on the east side of the market square facing the avenue. It is built to
 * look like what it is, the safest building in the Reach:
 * - white marble on a raised podium, reached by a flight of steps the full width of its front;
 * - a portico of six great columns carrying an entablature lettered with its name and a pediment with a gilded coin;
 * - a roof of green copper;
 * - copper doors into a long counting hall under a coffered ceiling and chandeliers, its tellers at high desks down
 *   both sides of the aisle;
 * - the head banker's desk on the axis of the door, and behind it the great round door of the vault.
 */
final class HighcrownExchequer {
    /** Where the bank's spots and claimed columns go. */
    interface Site {
        void mark(String id, int x, int y, int z, float yaw);
        void reserve(int x, int z);
    }

    /** The square's walking level, and the bank's, three steps up. */
    static final int F = HighcrownCity.MARKET, B = F + 3;
    /** Along x: the foot of the steps, the portico's front, the façade wall, the back wall. */
    static final int STEPS = 13, FRONT = 16, WALL = 21, BACK = 33;
    /** Along z: the side walls; the axis lies between {@code AXIS} and {@code AXIS + 1}. */
    static final int Z0 = 929, Z1 = 964, AXIS = 946;
    /** The columns' centres along z, and the heights of the capitals, the cornice and the roof's foot. */
    static final int[] COLUMNS = {931, 937, 943, 950, 956, 962};
    static final int CAPITAL = 11, CORNICE = 14, ROOF = 15;
    /** The counting hall's teller rows (the counters) and the cross aisle from the door to the vault. */
    static final int WEST_DESK = 24, EAST_DESK = 30, CROSS0 = 944, CROSS1 = 949;

    private final ReachCanvas canvas;
    private final Site site;

    HighcrownExchequer(ReachCanvas canvas, Site site) {
        this.canvas = canvas;
        this.site = site;
    }

    void build() {
        for (int x = STEPS - 1; x <= BACK; x++) for (int z = Z0 - 1; z <= Z1 + 1; z++) site.reserve(x, z);
        podium();
        walls();
        portico();
        roof();
        hall();
        vaultDoor();
        furnish();
        site.mark("exchequer", STEPS - 3, F, AXIS, 270F);
        site.mark("npc_orin_ledger", EAST_DESK + 1, B, AXIS, 90F);
        site.mark("npc_highcrown_folk_clerk_a", WEST_DESK - 1, B + 1, 936, 270F);
        site.mark("npc_highcrown_folk_clerk_b", EAST_DESK + 1, B + 1, 955, 90F);
    }

    private void set(int x, int y, int z, Material m) { canvas.set(x, y, z, m); }

    private void set(int x, int y, int z, Material m, String state) { canvas.set(x, y, z, m, state); }

    /** Distance from the axis in half blocks, so a wall at Z0 or Z1 is 35 out and the axis pair is 1. */
    private static int half(int z) { return Math.abs(2 * z - (2 * AXIS + 1)); }

    // ------------------------------------------------------------------ podium and steps

    /** The podium the building stands on, faced in marble, and the flight of steps across its whole front. */
    private void podium() {
        for (int x = FRONT; x <= BACK; x++) for (int z = Z0 - 1; z <= Z1 + 1; z++) {
            boolean edge = z == Z0 - 1 || z == Z1 + 1 || x == BACK;
            for (int y = F - 1; y < B - 1; y++) set(x, y, z, edge ? Material.QUARTZ_BRICKS : Material.STONE);
            set(x, B - 1, z, edge ? Material.SMOOTH_QUARTZ : Material.POLISHED_DIORITE);
            for (int y = B; y <= B + 3; y++) if (z == Z0 - 1 || z == Z1 + 1) set(x, y, z, Material.AIR);
        }
        // Three steps, each a stair on the one below, with marble cheeks at both ends carrying lamps.
        for (int k = 0; k < 3; k++) for (int z = Z0; z <= Z1; z++) {
            int x = STEPS + k;
            for (int y = F; y < F + k; y++) set(x, y, z, Material.SMOOTH_QUARTZ);
            set(x, F + k, z, Material.QUARTZ_STAIRS, "facing=east,half=bottom");
            set(x, F - 1, z, Material.SMOOTH_QUARTZ);
        }
        for (int z : new int[] {Z0 - 1, Z1 + 1}) {
            for (int x = STEPS; x < FRONT; x++) {
                set(x, F - 1, z, Material.SMOOTH_QUARTZ);
                for (int y = F; y <= F + 2; y++) set(x, y, z, Material.QUARTZ_BRICKS);
                set(x, F + 3, z, Material.SMOOTH_QUARTZ_SLAB, "type=bottom");
            }
            set(STEPS, F + 3, z, Material.SMOOTH_QUARTZ);
            set(STEPS, F + 4, z, Material.CHISELED_QUARTZ_BLOCK);
            set(STEPS, F + 5, z, Material.LANTERN);
        }
    }

    // ------------------------------------------------------------------ walls

    /** The façade behind the portico, the side walls and the back: marble between pilasters, tall arched windows. */
    private void walls() {
        for (int x = WALL; x <= BACK; x++) for (int z = Z0; z <= Z1; z++) {
            boolean wall = x == WALL || x == BACK || z == Z0 || z == Z1;
            if (!wall) continue;
            boolean corner = (x == WALL || x == BACK) && (z == Z0 || z == Z1);
            boolean pilaster = corner || (z == Z0 || z == Z1) && Math.floorMod(x - WALL, 4) == 0
                || x == BACK && Math.floorMod(z - Z0, 5) == 0;
            for (int y = B; y <= B + CORNICE; y++) {
                Material m = y >= B + CAPITAL + 1 ? (y == B + CORNICE - 1 ? Material.QUARTZ_BRICKS : Material.SMOOTH_QUARTZ)
                    : pilaster ? (y == B + CAPITAL ? Material.CHISELED_QUARTZ_BLOCK : Material.QUARTZ_PILLAR) : Material.SMOOTH_QUARTZ;
                set(x, y, z, m, m == Material.QUARTZ_PILLAR ? "axis=y" : "");
            }
        }
        // Windows: tall and round-headed, one in each bay of the sides and back, and between the columns in front.
        for (int x = WALL + 2; x < BACK; x += 4) for (int z : new int[] {Z0, Z1}) window(x, z, true);
        for (int z : new int[] {934, 940, 953, 959}) window(WALL, z, false);
        // The cornice running all round: a corbelled lip of marble stairs.
        for (int x = WALL; x <= BACK; x++) for (int z : new int[] {Z0 - 1, Z1 + 1})
            set(x, B + CORNICE, z, Material.QUARTZ_STAIRS, "facing=" + (z < AXIS ? "south" : "north") + ",half=top");
    }

    /** A tall window three high under a round head, with a marble sill and keystone. */
    private void window(int x, int z, boolean alongX) {
        for (int y = B + 2; y <= B + 8; y++) set(x, y, z, y == B + 8 ? Material.CHISELED_QUARTZ_BLOCK : Material.GLASS_PANE);
        int ox = alongX ? 0 : (x == WALL ? -1 : 1), oz = alongX ? (z == Z0 ? -1 : 1) : 0;
        String face = ox < 0 ? "east" : ox > 0 ? "west" : oz < 0 ? "south" : "north";
        set(x + ox, B + 1, z + oz, Material.QUARTZ_STAIRS, "facing=" + face + ",half=top");
        set(x + ox, B + 9, z + oz, Material.QUARTZ_SLAB, "type=bottom");
        int ax = alongX ? 1 : 0, az = alongX ? 0 : 1;
        for (int s : new int[] {-1, 1}) set(x + ax * s, B + 7, z + az * s, Material.QUARTZ_STAIRS,
            "facing=" + (alongX ? (s < 0 ? "east" : "west") : (s < 0 ? "south" : "north")) + ",half=top");
    }

    // ------------------------------------------------------------------ portico

    /** Six columns, the entablature with the bank's name, the pediment, and the copper doors. */
    private void portico() {
        for (int x = FRONT; x < WALL; x++) for (int z = Z0; z <= Z1; z++) {
            set(x, B - 1, z, Math.floorMod(x + z, 2) == 0 ? Material.SMOOTH_QUARTZ : Material.POLISHED_DIORITE);
            for (int y = B; y < B + CAPITAL + 1; y++) set(x, y, z, Material.AIR);
        }
        for (int c : COLUMNS) column(FRONT + 1, c);
        // Entablature: architrave, a frieze with a carved block over every column, the cornice.
        for (int x = FRONT; x < WALL; x++) for (int z = Z0; z <= Z1; z++) {
            set(x, B + CAPITAL + 1, z, x == FRONT ? Material.SMOOTH_QUARTZ : Math.floorMod(x + z, 3) == 0 ? Material.QUARTZ_BRICKS : Material.SMOOTH_QUARTZ);
            boolean overColumn = false;
            for (int c : COLUMNS) if (c == z) overColumn = true;
            set(x, B + CAPITAL + 2, z, x == FRONT && overColumn ? Material.CHISELED_QUARTZ_BLOCK : Material.QUARTZ_BRICKS);
            set(x, B + CORNICE, z, Material.SMOOTH_QUARTZ);
        }
        for (int z = Z0 - 1; z <= Z1 + 1; z++) set(FRONT - 1, B + CORNICE, z, Material.QUARTZ_STAIRS, "facing=east,half=top");
        for (int x = FRONT - 1; x < WALL; x++) for (int z : new int[] {Z0 - 1, Z1 + 1})
            set(x, B + CORNICE, z, Material.QUARTZ_STAIRS, "facing=" + (z < AXIS ? "south" : "north") + ",half=top");
        // The name on the frieze over the middle bay.
        set(FRONT - 1, B + CAPITAL + 2, AXIS, Material.DARK_OAK_WALL_SIGN, "facing=west");
        canvas.text(FRONT - 1, B + CAPITAL + 2, AXIS, "", "THE", "HIGHCROWN", "");
        set(FRONT - 1, B + CAPITAL + 2, AXIS + 1, Material.DARK_OAK_WALL_SIGN, "facing=west");
        canvas.text(FRONT - 1, B + CAPITAL + 2, AXIS + 1, "", "EXCHEQUER", "coin & vaults", "");
        // The doors: a double leaf of copper between grilles, under a round-headed marble surround.
        for (int z = CROSS0 + 1; z <= CROSS1 - 1; z++) for (int y = B; y <= B + 5; y++) {
            boolean head = y == B + 5 && (z == CROSS0 + 1 || z == CROSS1 - 1);
            set(WALL, y, z, head ? Material.CHISELED_QUARTZ_BLOCK : y >= B + 2 ? Material.WAXED_COPPER_GRATE : Material.AIR);
        }
        for (int z : new int[] {CROSS0 + 1, CROSS1 - 1}) for (int y = B; y <= B + 1; y++) set(WALL, y, z, Material.WAXED_COPPER_GRATE);
        set(WALL, B, AXIS, Material.WAXED_COPPER_DOOR, "facing=west,half=lower,hinge=left,open=false");
        set(WALL, B + 1, AXIS, Material.WAXED_COPPER_DOOR, "facing=west,half=upper,hinge=left,open=false");
        set(WALL, B, AXIS + 1, Material.WAXED_COPPER_DOOR, "facing=west,half=lower,hinge=right,open=false");
        set(WALL, B + 1, AXIS + 1, Material.WAXED_COPPER_DOOR, "facing=west,half=upper,hinge=right,open=false");
        for (int z = CROSS0; z <= CROSS1; z++) set(WALL, B + 6, z, z == CROSS0 || z == CROSS1 ? Material.QUARTZ_PILLAR : Material.CHISELED_QUARTZ_BLOCK, z == CROSS0 || z == CROSS1 ? "axis=y" : "");
        for (int y = B; y <= B + 5; y++) for (int z : new int[] {CROSS0, CROSS1}) set(WALL, y, z, Material.QUARTZ_PILLAR, "axis=y");
        // Hanging signs either side of the door, at a height a visitor reads.
        for (int z : new int[] {CROSS0 - 2, CROSS1 + 2}) {
            set(WALL - 1, B + 3, z, Material.DARK_OAK_WALL_HANGING_SIGN, "facing=north");
            canvas.text(WALL - 1, B + 3, z, "The Highcrown", "Exchequer", "Banking, coin", "& safekeeping");
        }
        // Lamps on the portico's back wall.
        for (int z : new int[] {934, 940, 953, 959}) {
            set(WALL - 1, B + 9, z, Material.LANTERN, "hanging=true");
            for (int y = B + 10; y <= B + CAPITAL; y++) set(WALL - 1, y, z, Material.IRON_CHAIN, "axis=y");
        }
    }

    /** One column: a square base, a fluted shaft of marble pillars in a cross, a carved capital. */
    private void column(int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean corner = dx != 0 && dz != 0;
            set(cx + dx, B, cz + dz, Material.SMOOTH_QUARTZ);
            for (int y = B + 1; y < B + CAPITAL; y++) if (!corner) set(cx + dx, y, cz + dz, Material.QUARTZ_PILLAR, "axis=y");
            if (corner) set(cx + dx, B + CAPITAL, cz + dz, Material.QUARTZ_STAIRS, "facing=" + (dx < 0 ? "east" : "west") + ",half=top");
            else set(cx + dx, B + CAPITAL, cz + dz, Material.CHISELED_QUARTZ_BLOCK);
        }
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int x = cx + d[0] * 2, z = cz + d[1] * 2;
            String face = d[0] > 0 ? "west" : d[0] < 0 ? "east" : d[1] > 0 ? "north" : "south";
            if (x >= FRONT) set(x, B, z, Material.QUARTZ_STAIRS, "facing=" + face + ",half=bottom");
        }
    }

    // ------------------------------------------------------------------ roof

    /**
     * A low classical roof of green copper, a half block in every block across, with the pediment's raking cornice in
     * marble at the front and a plain gable at the back. The pediment carries a gilded coin between marble scrolls.
     */
    private void roof() {
        for (int z = Z0 - 1; z <= Z1 + 1; z++) {
            int k = Math.floorDiv(36 - half(z), 2);
            if (k < 0) continue;
            // The surface stands k + 1 half blocks above the roof's foot: whole blocks, then a slab on an odd count.
            int h = k + 1, full = h / 2;
            for (int x = FRONT - 1; x <= BACK; x++) {
                boolean marble = x <= FRONT || x == BACK;
                for (int i = 0; i < full; i++) set(x, B + ROOF + i, z, marble ? Material.SMOOTH_QUARTZ : Material.WAXED_OXIDIZED_CUT_COPPER);
                if (h % 2 == 1) set(x, B + ROOF + full, z, marble ? Material.SMOOTH_QUARTZ_SLAB : Material.WAXED_OXIDIZED_CUT_COPPER_SLAB, "type=bottom");
            }
            // The tympanum, set back a block between the level cornice and the raking one.
            for (int yy = B + ROOF + 1; yy < B + ROOF + full - 1; yy++) set(FRONT - 1, yy, z, Material.AIR);
        }
        // The coin: a gilded disc with a marble rim, scrolls of marble either side.
        // Centred between the axis pair and between two courses, so the disc is round, not a cross.
        double cy = B + ROOF + 4.5, cz = AXIS + 0.5;
        for (int z = AXIS - 4; z <= AXIS + 5; z++) for (int y = B + ROOF + 1; y <= B + ROOF + 8; y++) {
            double r = Math.hypot(z - cz, y - cy);
            if (r <= 1.0) set(FRONT, y, z, Material.RAW_GOLD_BLOCK);
            else if (r <= 3.0) set(FRONT, y, z, Material.GOLD_BLOCK);
            else if (r <= 3.8) set(FRONT, y, z, Material.CHISELED_QUARTZ_BLOCK);
        }
        for (int s : new int[] {-1, 1}) for (int k = 4; k <= 9; k++) {
            int z = s < 0 ? AXIS - k : AXIS + 1 + k;
            set(FRONT - 1, B + ROOF + 1, z, Material.QUARTZ_STAIRS, "facing=east,half=bottom");
        }
        // Acroteria: gilded finials at the pediment's apex and both ends.
        set(FRONT - 1, B + ROOF + 9, AXIS, Material.GOLD_BLOCK);
        set(FRONT - 1, B + ROOF + 9, AXIS + 1, Material.GOLD_BLOCK);
        for (int z : new int[] {Z0 - 1, Z1 + 1}) set(FRONT - 1, B + ROOF, z, Material.CHISELED_QUARTZ_BLOCK);
    }

    // ------------------------------------------------------------------ hall

    /** The counting hall: a marble floor, pilasters down the walls, a coffered ceiling. */
    private void hall() {
        for (int x = WALL + 1; x < BACK; x++) for (int z = Z0 + 1; z < Z1; z++) {
            int dx = x - (WALL + 1), dz = z - (Z0 + 1);
            boolean band = z == AXIS || z == AXIS + 1 || x == (WALL + BACK) / 2;
            set(x, B - 1, z, band ? Material.POLISHED_DEEPSLATE : Math.floorMod(dx + dz, 2) == 0 ? Material.CALCITE : Material.POLISHED_DIORITE);
            for (int y = B; y <= B + CAPITAL; y++) set(x, y, z, Material.AIR);
            boolean beam = Math.floorMod(dx, 3) == 0 || Math.floorMod(dz, 3) == 0;
            set(x, B + CAPITAL + 1, z, beam ? Material.SMOOTH_QUARTZ : Material.AIR);
            set(x, B + CAPITAL + 2, z, beam ? Material.SMOOTH_QUARTZ : Material.DARK_OAK_PLANKS);
            if (!beam) set(x, B + CAPITAL + 3, z, Material.SMOOTH_QUARTZ);
        }
        // Pilasters inside, opposite the outside ones, each with a sconce.
        for (int x = WALL + 4; x < BACK; x += 4) for (int z : new int[] {Z0 + 1, Z1 - 1}) {
            for (int y = B; y < B + CAPITAL; y++) set(x, y, z, Material.QUARTZ_PILLAR, "axis=y");
            set(x, B + CAPITAL, z, Material.CHISELED_QUARTZ_BLOCK);
            set(x, B + 5, z + (z == Z0 + 1 ? 1 : -1), Material.LANTERN, "hanging=false");
            set(x, B + 4, z + (z == Z0 + 1 ? 1 : -1), Material.QUARTZ_SLAB, "type=top");
        }
    }

    // ------------------------------------------------------------------ vault

    /** The vault's great round door set in the back wall, iron with a dark rim, gilded hub and bolts. */
    private void vaultDoor() {
        double cy = B + 3.5, cz = AXIS + 0.5;
        for (int z = AXIS - 4; z <= AXIS + 5; z++) for (int y = B - 1; y <= B + 8; y++) {
            double r = Math.hypot(z - cz, y - cy);
            if (r > 4.3) continue;
            Material m;
            if (r > 3.4) m = Material.POLISHED_DEEPSLATE;
            else if (r < 1.0) m = Material.GOLD_BLOCK;
            else if (Math.abs(z - cz) < 0.6 || Math.abs(y - cy) < 0.6) m = Material.POLISHED_BLACKSTONE;
            else if (r > 2.5 && r < 3.1) m = Material.CHISELED_POLISHED_BLACKSTONE;
            else m = Material.IRON_BLOCK;
            set(BACK - 1, y, z, m);
        }
        for (int z = AXIS - 3; z <= AXIS + 4; z++) set(BACK - 2, B - 1, z, Material.POLISHED_DEEPSLATE);
    }

    // ------------------------------------------------------------------ furnishing

    /** The tellers' high desks down both sides, the head banker's desk before the vault, chandeliers, a runner. */
    private void furnish() {
        for (int z = Z0 + 2; z <= Z1 - 2; z++) {
            if (z >= CROSS0 - 2 && z <= CROSS1 + 2) continue;
            desk(WEST_DESK, z, -1);
            desk(EAST_DESK, z, 1);
        }
        // The head banker's desk on the axis, before the vault, with a ledger and a lamp.
        for (int z = CROSS0 + 1; z <= CROSS1 - 1; z++) {
            set(EAST_DESK, B, z, Material.DARK_OAK_PLANKS);
            set(EAST_DESK, B + 1, z, Material.SMOOTH_QUARTZ_SLAB, "type=bottom");
        }
        set(EAST_DESK, B + 2, CROSS0 + 1, Material.LANTERN);
        set(EAST_DESK, B + 2, CROSS1 - 1, Material.CANDLE, "candles=3,lit=true");
        set(EAST_DESK + 1, B, CROSS0 - 1, Material.CHEST, "facing=west,type=single");
        set(EAST_DESK + 1, B, CROSS1 + 1, Material.BARREL, "facing=up");
        // A red runner from the door to the banker's desk.
        for (int x = WALL + 1; x < EAST_DESK; x++) for (int z = AXIS; z <= AXIS + 1; z++) set(x, B, z, Material.RED_CARPET);
        // Chandeliers down the aisle: a gilded hub on a chain, four iron arms, a lantern under each.
        int aisle = (WEST_DESK + EAST_DESK) / 2;
        for (int cz : new int[] {936, AXIS, 957}) {
            for (int y = B + 8; y <= B + CAPITAL; y++) set(aisle, y, cz, Material.IRON_CHAIN, "axis=y");
            set(aisle, B + 7, cz, Material.GOLD_BLOCK);
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int k = 1; k <= 2; k++) set(aisle + d[0] * k, B + 7, cz + d[1] * k, Material.IRON_BARS);
                set(aisle + d[0] * 2, B + 6, cz + d[1] * 2, Material.LANTERN, "hanging=true");
            }
            set(aisle, B + 6, cz, Material.LANTERN, "hanging=true");
        }
        // Benches for those waiting their turn, in the cross aisle by the door.
        for (int z : new int[] {CROSS0, CROSS1}) {
            set(WALL + 1, B, z, Material.SPRUCE_STAIRS, "facing=west,half=bottom");
            set(WALL + 2, B, z, Material.SPRUCE_STAIRS, "facing=west,half=bottom");
        }
    }

    /**
     * One run of a teller's high desk: a dark oak counter with a marble top on the aisle side, the teller's raised
     * floor behind it, and every few blocks a ledger, a lamp, a strongbox or a pile of coin.
     */
    private void desk(int x, int z, int back) {
        set(x, B, z, Material.DARK_OAK_PLANKS);
        set(x, B + 1, z, Material.SMOOTH_QUARTZ_SLAB, "type=bottom");
        for (int k = 1; k <= 2; k++) set(x + back * k, B, z, Material.SPRUCE_PLANKS);
        int slot = Math.floorMod(z - Z0, 5);
        if (slot == 0) set(x, B + 2, z, Material.LANTERN);
        else if (slot == 2) set(x + back * 2, B + 1, z, Material.LECTERN, "facing=" + (back < 0 ? "east" : "west"));
        else if (slot == 3) set(x, B + 2, z, Material.CANDLE, "candles=2,lit=true");
        if (slot == 4) set(x + back * 2, B + 1, z, Material.CHEST, "facing=" + (back < 0 ? "east" : "west") + ",type=single");
        if (slot == 1 && Math.floorMod(z, 3) == 0) set(x + back * 2, B + 1, z, Material.BARREL, "facing=up");
        // A brass rail along the counter's edge.
        if (slot != 0 && slot != 3) set(x, B + 2, z, Material.IRON_BARS);
    }
}
