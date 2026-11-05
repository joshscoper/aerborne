package net.nerdsmp.paper.reach;

import org.bukkit.Material;

/**
 * The Highcrown Exchequer, the city's bank, on the east side of the market square facing the avenue. It is built in
 * the city's own stone and slate, the grandest of its guild houses rather than a stranger from another country:
 * - a podium with steps the full width of its front;
 * - a ground-floor arcade of round arches on stone piers, the middle arch wider and taller, with the bank's doors
 *   set back in the loggia behind it;
 * - a floor of tall round-headed windows between pilasters, banners of the city between them, a cornice on corbels;
 * - a steep slate roof between crow-stepped gables, with dormers;
 * - over the middle a stepped gable carrying the city's clock, the bank's name and gilded finials;
 * - a bell lantern on the ridge.
 * Inside, the long counting hall is marble under a coffered ceiling and chandeliers, its tellers at high desks down
 * both sides of the aisle, the head banker's desk on the axis of the door and behind it the vault's round door.
 */
final class HighcrownExchequer {
    /** Where the bank's spots and claimed columns go. */
    interface Site {
        void mark(String id, int x, int y, int z, float yaw);
        void reserve(int x, int z);
    }

    /** The square's walking level, and the bank's, three steps up. */
    static final int F = HighcrownCity.MARKET, B = F + 3;
    /** Along x: the foot of the steps, the arcade's face, the doors' wall, the back wall. */
    static final int STEPS = 13, FRONT = 16, WALL = 21, BACK = 33;
    /** Along z: the side walls; the axis lies between {@code AXIS} and {@code AXIS + 1}. */
    static final int Z0 = 929, Z1 = 964, AXIS = 946;
    /** The arcade's openings along z, each {from, to}; the piers stand between them. The middle one is the door's. */
    static final int[][] BAYS = {{931, 934}, {937, 940}, {943, 950}, {953, 956}, {959, 962}};
    /** Heights above the floor: the hall's coffers, the string course, the cornice, the roof's foot. */
    static final int CAPITAL = 11, STRING = 9, CORNICE = 16, ROOF = 17;
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
        front();
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

    /** Ashlar of the city's stone, mossy low down and cracked here and there, in patches. */
    private static Material stone(int x, int y, int z) {
        long h = ReachFlora.hash((x >> 1) * 7 + y, (z >> 1) * 13 - y);
        int k = (int) Math.floorMod(h, 23L);
        if (y < B + 2 && k < 4) return Material.MOSSY_STONE_BRICKS;
        if (k == 5) return Material.CRACKED_STONE_BRICKS;
        return Material.STONE_BRICKS;
    }

    private static boolean inBay(int z) {
        for (int[] b : BAYS) if (z >= b[0] && z <= b[1]) return true;
        return false;
    }

    /** Height above the floor of a bay's arch at z: straight jambs, then a round head. Zero outside the bays. */
    private static int archTop(int z) {
        for (int[] b : BAYS) {
            if (z < b[0] || z > b[1]) continue;
            double width = b[1] - b[0] + 1, centre = (b[0] + b[1]) / 2.0, a = Math.abs(z - centre);
            int spring = width > 4 ? 4 : 5;
            double rr = width / 2.0 + 0.4;
            return spring + (int) Math.floor(Math.sqrt(Math.max(0, rr * rr - a * a)));
        }
        return 0;
    }

    // ------------------------------------------------------------------ podium and steps

    /** The podium, faced in stone with a moulded top, and the flight of steps across the whole front. */
    private void podium() {
        for (int x = FRONT; x <= BACK; x++) for (int z = Z0 - 1; z <= Z1 + 1; z++) {
            boolean edge = z == Z0 - 1 || z == Z1 + 1 || x == BACK;
            for (int y = F - 1; y < B - 1; y++) set(x, y, z, edge ? stone(x, y, z) : Material.STONE);
            set(x, B - 1, z, edge ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
            for (int y = B; y <= B + 3; y++) if (z == Z0 - 1 || z == Z1 + 1) set(x, y, z, Material.AIR);
        }
        for (int k = 0; k < 3; k++) for (int z = Z0; z <= Z1; z++) {
            int x = STEPS + k;
            for (int y = F; y < F + k; y++) set(x, y, z, Material.STONE_BRICKS);
            set(x, F + k, z, Material.STONE_BRICK_STAIRS, "facing=east,half=bottom");
            set(x, F - 1, z, Material.STONE_BRICKS);
        }
        // Cheek walls at both ends of the steps, each carrying an iron lamp.
        for (int z : new int[] {Z0 - 1, Z1 + 1}) {
            for (int x = STEPS; x < FRONT; x++) {
                set(x, F - 1, z, Material.STONE_BRICKS);
                for (int y = F; y <= F + 2; y++) set(x, y, z, stone(x, y, z));
                set(x, F + 3, z, Material.STONE_BRICK_SLAB, "type=bottom");
            }
            set(STEPS, F + 3, z, Material.POLISHED_ANDESITE);
            set(STEPS, F + 4, z, Material.STONE_BRICK_WALL, "up=true");
            set(STEPS, F + 5, z, Material.LANTERN);
        }
    }

    // ------------------------------------------------------------------ walls

    /** The side walls and the back: stone between pilasters, round-headed windows in two storeys. */
    private void walls() {
        for (int x = FRONT; x <= BACK; x++) for (int z = Z0; z <= Z1; z++) {
            boolean side = z == Z0 || z == Z1, wall = side || x == BACK || x == WALL;
            if (!wall) continue;
            boolean pilaster = (x == FRONT || x == BACK) && side || side && Math.floorMod(x - WALL, 4) == 0
                || x == BACK && Math.floorMod(z - Z0, 5) == 0;
            for (int y = B; y <= B + CORNICE; y++)
                set(x, y, z, y == B + STRING ? Material.POLISHED_ANDESITE : pilaster ? Material.POLISHED_ANDESITE : stone(x, y, z));
        }
        for (int x = WALL + 2; x < BACK; x += 4) for (int z : new int[] {Z0, Z1}) {
            window(x, z, B + 2, B + 7, true);
            window(x, z, B + 11, B + 14, true);
        }
        // String course and cornice running round the sides and back.
        for (int x = WALL; x <= BACK; x++) for (int z : new int[] {Z0 - 1, Z1 + 1}) {
            String face = "facing=" + (z < AXIS ? "south" : "north");
            set(x, B + STRING, z, Material.STONE_BRICK_STAIRS, face + ",half=top");
            set(x, B + CORNICE, z, Material.STONE_BRICK_STAIRS, face + ",half=top");
            if (Math.floorMod(x, 2) == 0) set(x, B + CORNICE - 1, z, Material.STONE_BRICK_STAIRS, face + ",half=top");
        }
    }

    /** A tall window with a round head of carved stone, a sill below and its glass in lead. */
    private void window(int x, int z, int y0, int y1, boolean alongX) {
        for (int y = y0; y <= y1; y++) set(x, y, z, y == y1 ? Material.CHISELED_STONE_BRICKS : Material.GLASS_PANE);
        int ox = alongX ? 0 : -1, oz = alongX ? (z == Z0 ? -1 : 1) : 0;
        String face = ox < 0 ? "east" : oz < 0 ? "south" : "north";
        set(x + ox, y0 - 1, z + oz, Material.STONE_BRICK_STAIRS, "facing=" + face + ",half=top");
    }

    // ------------------------------------------------------------------ the front

    /**
     * The front: the arcade and its loggia, the doors set back in it, the upper floor of windows between pilasters,
     * the string course, banners, and the cornice on corbels.
     */
    private void front() {
        // The arcade face, the loggia behind it with a timber ceiling, the room above.
        for (int z = Z0; z <= Z1; z++) {
            boolean bay = inBay(z);
            int top = archTop(z);
            for (int y = B; y <= B + CORNICE; y++) {
                boolean open = bay && y < B + top;
                Material m = open ? Material.AIR
                    : y == B + STRING ? Material.POLISHED_ANDESITE
                    : bay && y == B + top ? (top == archTop(z) && isKey(z) ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS)
                    : !bay && y < B + STRING ? Material.POLISHED_ANDESITE : stone(FRONT, y, z);
                set(FRONT, y, z, m);
            }
            for (int x = FRONT + 1; x < WALL; x++) {
                set(x, B - 1, z, Math.floorMod(x + z, 2) == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                if (z > Z0 && z < Z1) for (int y = B; y < B + STRING; y++) set(x, y, z, Material.AIR);
                set(x, B + STRING, z, Math.floorMod(z, 3) == 0 ? Material.STRIPPED_DARK_OAK_LOG : Material.DARK_OAK_PLANKS,
                    Math.floorMod(z, 3) == 0 ? "axis=x" : "");
                // The counting room over the loggia: a plank floor and its own air, inside the side walls.
                if (z > Z0 && z < Z1) for (int y = B + STRING + 1; y < B + CORNICE; y++) set(x, y, z, Material.AIR);
            }
        }
        for (int[] b : BAYS) {
            int mid = (b[0] + b[1]) / 2;
            set(FRONT + 2, B + STRING - 1, mid, Material.LANTERN, "hanging=true");
        }
        // The doors: a double leaf of dark oak, iron-barred lights either side, a fanlight under a round head.
        for (int z = CROSS0 + 1; z <= CROSS1 - 1; z++) for (int y = B; y <= B + 5; y++) {
            boolean head = y == B + 5 && (z == CROSS0 + 1 || z == CROSS1 - 1);
            set(WALL, y, z, head ? Material.CHISELED_STONE_BRICKS : y >= B + 2 ? Material.YELLOW_STAINED_GLASS_PANE : Material.IRON_BARS);
        }
        set(WALL, B, AXIS, Material.DARK_OAK_DOOR, "facing=west,half=lower,hinge=left,open=false");
        set(WALL, B + 1, AXIS, Material.DARK_OAK_DOOR, "facing=west,half=upper,hinge=left,open=false");
        set(WALL, B, AXIS + 1, Material.DARK_OAK_DOOR, "facing=west,half=lower,hinge=right,open=false");
        set(WALL, B + 1, AXIS + 1, Material.DARK_OAK_DOOR, "facing=west,half=upper,hinge=right,open=false");
        for (int y = B; y <= B + 6; y++) for (int z : new int[] {CROSS0, CROSS1}) set(WALL, y, z, Material.POLISHED_ANDESITE);
        for (int z = CROSS0; z <= CROSS1; z++) set(WALL, B + 6, z, Material.CHISELED_STONE_BRICKS);
        // Hanging signs either side of the door inside the loggia, at a height a visitor reads.
        for (int z : new int[] {CROSS0 - 2, CROSS1 + 2}) {
            set(WALL - 1, B + 3, z, Material.DARK_OAK_WALL_HANGING_SIGN, "facing=north");
            canvas.text(WALL - 1, B + 3, z, "The Highcrown", "Exchequer", "Banking, coin", "& safekeeping");
        }
        // The upper floor's windows over the bays, pilasters between, and the city's banners on them.
        for (int[] b : BAYS) {
            int mid = (b[0] + b[1]) / 2;
            int[] lights = b[1] - b[0] >= 7 ? new int[] {mid - 1, mid, mid + 1, mid + 2} : new int[] {mid, mid + 1};
            for (int z : lights) {
                for (int y = B + 11; y <= B + 14; y++) set(FRONT, y, z, y == B + 14 ? Material.CHISELED_STONE_BRICKS : Material.GLASS_PANE);
                set(FRONT - 1, B + 10, z, Material.STONE_BRICK_STAIRS, "facing=east,half=top");
            }
        }
        for (int z = Z0; z <= Z1; z++) if (!inBay(z) && (z == Z0 || z == Z1 || !inBay(z - 1) || !inBay(z + 1))) {
            for (int y = B + STRING + 1; y < B + CORNICE; y++) set(FRONT, y, z, Material.POLISHED_ANDESITE);
        }
        for (int z : new int[] {935, 941, 951, 957}) set(FRONT - 1, B + 14, z, Material.GREEN_WALL_BANNER, "facing=west");
        // String course and cornice on corbels across the front.
        for (int z = Z0 - 1; z <= Z1 + 1; z++) {
            set(FRONT - 1, B + STRING, z, Material.STONE_BRICK_STAIRS, "facing=east,half=top");
            set(FRONT - 1, B + CORNICE, z, Material.STONE_BRICK_STAIRS, "facing=east,half=top");
            if (Math.floorMod(z, 2) == 0) set(FRONT - 1, B + CORNICE - 1, z, Material.STONE_BRICK_STAIRS, "facing=east,half=top");
        }
        // The counting room: shelves of ledgers and a lamp, seen through the upper windows.
        for (int z = Z0 + 1; z < Z1; z++) {
            set(WALL - 1, B + STRING + 1, z, Material.BOOKSHELF);
            if (Math.floorMod(z, 3) == 0) set(WALL - 1, B + STRING + 2, z, Material.BOOKSHELF);
        }
        for (int z : new int[] {938, 955}) set(FRONT + 2, B + CORNICE - 1, z, Material.LANTERN, "hanging=true");
    }

    private static boolean isKey(int z) {
        for (int[] b : BAYS) {
            int w = b[1] - b[0] + 1;
            if (w % 2 == 0 ? (z == b[0] + w / 2 - 1 || z == b[0] + w / 2) : z == b[0] + w / 2) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ roof

    /**
     * A steep slate roof across the front, ridge along the street, between two crow-stepped gables; dormers on its
     * front slope; the stepped middle gable with the clock, the name and gilded finials; a bell lantern on the ridge.
     */
    private void roof() {
        int x0 = FRONT - 1, x1 = BACK, half = (x1 - x0) / 2, ridgeX = x0 + half;
        for (int z = Z0 + 1; z < Z1; z++) {
            for (int i = 0; i <= half; i++) {
                int y = B + ROOF + i;
                int xf = x0 + i, xb = x1 - i;
                if (xf == xb) { set(xf, y, z, Material.DEEPSLATE_TILE_SLAB, "type=bottom"); continue; }
                set(xf, y, z, Material.DEEPSLATE_TILE_STAIRS, "facing=east,half=bottom");
                set(xb, y, z, Material.DEEPSLATE_TILE_STAIRS, "facing=west,half=bottom");
                for (int x = xf + 1; x < xb; x++) set(x, y, z, i == 0 ? Material.STONE_BRICKS : Material.AIR);
            }
            if (Math.floorMod(z, 2) == 0) set(ridgeX, B + ROOF + half + 1, z, Material.DEEPSLATE_TILE_WALL);
        }
        // Crow-stepped gables: each pair of blocks across rises two, capped with a slab.
        for (int z : new int[] {Z0, Z1}) for (int x = x0; x <= x1; x++) {
            int k = Math.min(x - x0, x1 - x);
            int top = B + ROOF + 2 * (k / 2) + 1;
            for (int y = B + ROOF - 1; y <= top; y++) set(x, y, z, y == B + ROOF - 1 ? Material.POLISHED_ANDESITE : stone(x, y, z));
            set(x, top + 1, z, Material.STONE_BRICK_SLAB, "type=bottom");
        }
        for (int z : new int[] {Z0, Z1}) {
            pinnacle(ridgeX, B + ROOF + 2 * (half / 2) + 2, z);
            // A round window high in each gable.
            for (int x = ridgeX - 1; x <= ridgeX + 1; x++) for (int y = B + ROOF + 3; y <= B + ROOF + 5; y++)
                set(x, y, z, Math.abs(x - ridgeX) + Math.abs(y - (B + ROOF + 4)) <= 1 ? Material.YELLOW_STAINED_GLASS_PANE : Material.CHISELED_STONE_BRICKS);
        }
        // Dormers on the front slope over the outer bays.
        for (int[] b : new int[][] {BAYS[0], BAYS[1], BAYS[3], BAYS[4]}) {
            int mid = (b[0] + b[1]) / 2;
            dormer(x0 + 2, B + ROOF + 2, mid);
        }
        frontispiece();
        lantern(ridgeX, B + ROOF + half + 1);
    }

    /** A gabled dormer standing on the front slope, a window in its face. */
    private void dormer(int x, int y, int z0) {
        for (int dz = 0; dz <= 1; dz++) for (int dy = 0; dy <= 2; dy++) for (int dx = 0; dx <= 3; dx++) {
            int z = z0 + dz;
            set(x + dx, y + dy, z, dx == 0 && dy < 2 ? Material.GLASS_PANE : Material.STONE_BRICKS);
        }
        for (int dx = -1; dx <= 3; dx++) {
            set(x + dx, y + 2, z0 - 1, Material.DEEPSLATE_TILE_STAIRS, "facing=south,half=bottom");
            set(x + dx, y + 2, z0 + 2, Material.DEEPSLATE_TILE_STAIRS, "facing=north,half=bottom");
            set(x + dx, y + 3, z0, Material.DEEPSLATE_TILE_STAIRS, "facing=south,half=bottom");
            set(x + dx, y + 3, z0 + 1, Material.DEEPSLATE_TILE_STAIRS, "facing=north,half=bottom");
        }
    }

    /**
     * The middle gable: the front wall carried up over the main bay in crow steps, with the city's clock, the bank's
     * name under it, pinnacles at its shoulders and a gilded finial on top.
     */
    private void frontispiece() {
        int z0 = BAYS[2][0] - 2, z1 = BAYS[2][1] + 2;
        double mid = (z0 + z1) / 2.0;
        int base = B + CORNICE;
        for (int z = z0; z <= z1; z++) {
            int k = (int) Math.floor(Math.min(z - z0, z1 - z) / 2.0);
            int top = base + 6 + 3 * k;
            for (int x = FRONT; x <= FRONT + 1; x++) for (int y = base; y <= top; y++) set(x, y, z, stone(x, y, z));
            set(FRONT, top + 1, z, Material.STONE_BRICK_SLAB, "type=bottom");
            set(FRONT + 1, top + 1, z, Material.STONE_BRICK_SLAB, "type=bottom");
        }
        // Twin round-headed lights over the name, a small round window above them.
        for (int z : new int[] {AXIS - 2, AXIS + 3}) {
            for (int y = base + 3; y <= base + 7; y++) set(FRONT, y, z, y == base + 7 ? Material.CHISELED_STONE_BRICKS : Material.GLASS_PANE);
            set(FRONT - 1, base + 2, z, Material.STONE_BRICK_STAIRS, "facing=east,half=top");
        }
        for (int z = AXIS - 1; z <= AXIS + 2; z++) for (int y = base + 8; y <= base + 11; y++) {
            boolean ring = z == AXIS - 1 || z == AXIS + 2 || y == base + 8 || y == base + 11;
            boolean corner = (z == AXIS - 1 || z == AXIS + 2) && (y == base + 8 || y == base + 11);
            if (corner) continue;
            set(FRONT, y, z, ring ? Material.CHISELED_STONE_BRICKS : Material.YELLOW_STAINED_GLASS_PANE);
        }
        // The name, lettered on the panel between the lights.
        set(FRONT - 1, base + 4, AXIS, Material.DARK_OAK_WALL_SIGN, "facing=west");
        canvas.text(FRONT - 1, base + 4, AXIS, "", "THE", "HIGHCROWN", "");
        set(FRONT - 1, base + 4, AXIS + 1, Material.DARK_OAK_WALL_SIGN, "facing=west");
        canvas.text(FRONT - 1, base + 4, AXIS + 1, "", "EXCHEQUER", "coin & vaults", "");
        // Stone pinnacles with iron spikes on the lowest steps and the top one.
        for (int z : new int[] {z0, z1}) pinnacle(FRONT, base + 7, z);
        int topStep = base + 6 + 3 * (int) Math.floor((z1 - z0) / 4.0) + 1;
        pinnacle(FRONT, topStep, AXIS);
        pinnacle(FRONT, topStep, AXIS + 1);
    }

    /** A small stone pinnacle: a carved block, a shaft, an iron spike. */
    private void pinnacle(int x, int y, int z) {
        set(x, y, z, Material.CHISELED_STONE_BRICKS);
        set(x, y + 1, z, Material.STONE_BRICK_WALL, "up=true");
        set(x, y + 2, z, Material.LIGHTNING_ROD, "facing=up");
    }

    /** A bell lantern on the ridge: a stone base, four slender posts round the bell, a cornice and a slate spire. */
    private void lantern(int cx, int y0) {
        int z0 = AXIS - 1, z1 = AXIS + 2, x0 = cx - 1, x1 = cx + 2;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0 - 4; y <= y0; y++) set(x, y, z, Material.STONE_BRICKS);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
            for (int y = y0 + 1; y <= y0 + 4; y++) set(x, y, z, corner ? Material.STONE_BRICK_WALL : Material.AIR, corner ? "up=true" : "");
            set(x, y0 + 5, z, Material.STONE_BRICKS);
        }
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            boolean ring = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
            if (!ring || (x == x0 - 1 || x == x1 + 1) && (z == z0 - 1 || z == z1 + 1)) continue;
            set(x, y0 + 5, z, Material.STONE_BRICK_STAIRS, x == x0 - 1 ? "facing=east,half=top" : x == x1 + 1 ? "facing=west,half=top"
                : z == z0 - 1 ? "facing=south,half=top" : "facing=north,half=top");
        }
        set(cx, y0 + 4, AXIS, Material.BELL, "attachment=ceiling,facing=west");
        // The spire: courses of slate stepping in two at a time to a point.
        int y = y0 + 6;
        for (int lo = 0, hi = 3; lo <= hi; lo++, hi--) for (int k = 0; k < 3; k++, y++)
            for (int x = x0 + lo; x <= x0 + hi; x++) for (int z = z0 + lo; z <= z0 + hi; z++) {
                boolean edge = x == x0 + lo || x == x0 + hi || z == z0 + lo || z == z0 + hi;
                if (!edge) { set(x, y, z, Material.DEEPSLATE_TILES); continue; }
                set(x, y, z, Material.DEEPSLATE_TILE_STAIRS, "facing=" + (x == x0 + lo ? "east" : x == x0 + hi ? "west" : z == z0 + lo ? "south" : "north") + ",half=bottom");
            }
        set(cx, y, AXIS, Material.LIGHTNING_ROD, "facing=up");
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
