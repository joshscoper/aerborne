package net.nerdsmp.paper.reach;

import org.bukkit.Material;

/**
 * Building pieces shared by every town in the Reach: timber-framed houses on stone plinths, tall narrow towers,
 * market stalls, lamps and street furniture. Rules from the vault's Build Style Guide are built in: stone footings
 * down to the ground, posts one proud of the infill, jettied upper floors on corbels, windows with shutters and
 * sills, steep roofs with an overhang and eaves, weathering in clusters that darken toward the ground.
 */
final class ReachKit {
    /** Materials for one look of building. */
    record Style(Material base, Material baseAccent, Material post, Material beam, Material plaster, Material plasterAccent,
                 Material roofStair, Material roofSlab, Material roofBlock, Material plank, Material door, Material trapdoor,
                 Material fence) {
        /** The same style with another plaster for the infill, so a street of houses is not one colour. */
        Style withPlaster(Material plaster, Material accent) {
            return new Style(base, baseAccent, post, beam, plaster, accent, roofStair, roofSlab, roofBlock, plank, door, trapdoor, fence);
        }

        static final Style HIGHCROWN = new Style(Material.STONE_BRICKS, Material.ANDESITE, Material.DARK_OAK_LOG,
            Material.STRIPPED_DARK_OAK_LOG, Material.CALCITE, Material.WHITE_CONCRETE_POWDER, Material.SPRUCE_STAIRS,
            Material.SPRUCE_SLAB, Material.SPRUCE_PLANKS, Material.SPRUCE_PLANKS, Material.SPRUCE_DOOR,
            Material.SPRUCE_TRAPDOOR, Material.DARK_OAK_FENCE);
        static final Style SLATE = new Style(Material.STONE_BRICKS, Material.TUFF_BRICKS, Material.SPRUCE_LOG,
            Material.STRIPPED_SPRUCE_LOG, Material.CALCITE, Material.DIORITE, Material.DEEPSLATE_TILE_STAIRS,
            Material.DEEPSLATE_TILE_SLAB, Material.DEEPSLATE_TILES, Material.SPRUCE_PLANKS, Material.SPRUCE_DOOR,
            Material.SPRUCE_TRAPDOOR, Material.SPRUCE_FENCE);
        static final Style HARBOUR = new Style(Material.DEEPSLATE_BRICKS, Material.COBBLED_DEEPSLATE, Material.SPRUCE_LOG,
            Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_PLANKS, Material.STRIPPED_SPRUCE_WOOD, Material.DARK_OAK_STAIRS,
            Material.DARK_OAK_SLAB, Material.DARK_OAK_PLANKS, Material.SPRUCE_PLANKS, Material.SPRUCE_DOOR,
            Material.SPRUCE_TRAPDOOR, Material.SPRUCE_FENCE);
        static final Style VALE = new Style(Material.COBBLESTONE, Material.STONE_BRICKS, Material.OAK_LOG,
            Material.STRIPPED_OAK_LOG, Material.SMOOTH_SANDSTONE, Material.STRIPPED_BIRCH_WOOD, Material.SPRUCE_STAIRS,
            Material.SPRUCE_SLAB, Material.SPRUCE_PLANKS, Material.OAK_PLANKS, Material.OAK_DOOR,
            Material.OAK_TRAPDOOR, Material.OAK_FENCE);
        /** The Lantern Fane: all stone, deep slate roofs, deepslate pillars where a frame would be. */
        static final Style FANE = new Style(Material.STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.DEEPSLATE,
            Material.DEEPSLATE, Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.DEEPSLATE_TILE_STAIRS,
            Material.DEEPSLATE_TILE_SLAB, Material.DEEPSLATE_TILES, Material.POLISHED_ANDESITE, Material.DARK_OAK_DOOR,
            Material.DARK_OAK_TRAPDOOR, Material.DARK_OAK_FENCE);
        /** Warm red roofs for the Dock Quarter and the odd house elsewhere, so districts read by colour from above. */
        static final Style EMBER = new Style(Material.STONE_BRICKS, Material.ANDESITE, Material.DARK_OAK_LOG,
            Material.STRIPPED_DARK_OAK_LOG, Material.CALCITE, Material.WHITE_CONCRETE_POWDER, Material.MANGROVE_STAIRS,
            Material.MANGROVE_SLAB, Material.MANGROVE_PLANKS, Material.SPRUCE_PLANKS, Material.DARK_OAK_DOOR,
            Material.DARK_OAK_TRAPDOOR, Material.DARK_OAK_FENCE);
        static final Style VALE_SPRUCE = new Style(Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.SPRUCE_LOG,
            Material.STRIPPED_SPRUCE_LOG, Material.SMOOTH_SANDSTONE, Material.STRIPPED_BIRCH_WOOD, Material.DARK_OAK_STAIRS,
            Material.DARK_OAK_SLAB, Material.DARK_OAK_PLANKS, Material.SPRUCE_PLANKS, Material.SPRUCE_DOOR,
            Material.SPRUCE_TRAPDOOR, Material.SPRUCE_FENCE);
    }

    private final ReachTerrain terrain;

    ReachKit(ReachTerrain terrain) { this.terrain = terrain; }

    // ------------------------------------------------------------------ weathering

    /** Stone with mossy and cracked patches that gather toward the ground, never single speckles. */
    Material weathered(ReachBrush b, int lx, int ly, int lz, Material stone) {
        double cluster = terrain.patch(b.wx(lx, lz) + ly * 0.7, b.wz(lx, lz) - ly * 0.5, 0.22);
        double damp = cluster - ly * 0.12;
        if (stone == Material.STONE_BRICKS) {
            if (damp > 0.45) return Material.MOSSY_STONE_BRICKS;
            if (cluster < -0.55) return Material.CRACKED_STONE_BRICKS;
            return stone;
        }
        if (stone == Material.COBBLESTONE) return damp > 0.35 ? Material.MOSSY_COBBLESTONE : stone;
        if (stone == Material.DEEPSLATE_BRICKS) return cluster < -0.5 ? Material.CRACKED_DEEPSLATE_BRICKS : stone;
        return stone;
    }

    // ------------------------------------------------------------------ sites

    /**
     * Grades a building's site before it's built, in the brush's frame: local x {@code lx0..lx1}, z {@code lz0..lz1}
     * is the footprint, the brush's y is the floor. Ground above the floor is cut back in a ring {@code margin} wide,
     * with a stone retaining wall at the ring's edge where the cut is deeper than a step; ground below the floor is
     * filled to a plinth and terrace faced in stone. Nothing already placed is touched, so neighbours survive.
     */
    void site(ReachBrush b, int lx0, int lz0, int lx1, int lz1, int margin, Material face) {
        int ax = b.wx(lx0, lz0), az = b.wz(lx0, lz0), bx = b.wx(lx1, lz1), bz = b.wz(lx1, lz1);
        int x0 = Math.min(ax, bx), x1 = Math.max(ax, bx), z0 = Math.min(az, bz), z1 = Math.max(az, bz);
        int floor = b.oy;
        ReachCanvas c = b.canvas;
        c.lot(x0, z0, x1, z1, floor);
        for (int x = x0 - margin; x <= x1 + margin; x++) for (int z = z0 - margin; z <= z1 + margin; z++) {
            ReachTerrain.Column col = terrain.column(x, z);
            int ground = col.land() ? col.top() : floor - 12;
            int ring = Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1));
            boolean inside = ring <= 0, edge = ring == margin;
            if (ground >= floor) {
                // Cut: clear to the floor; the outer ring holds the bank back with a wall.
                if (edge && ground >= floor + 1) {
                    for (int y = floor; y <= ground; y++) if (!c.has(x, y, z)) c.set(x, y, z, weathered(b, 0, y - floor, 0, face));
                    if (!c.has(x, ground + 1, z)) c.set(x, ground + 1, z, face == Material.COBBLESTONE ? Material.MOSSY_COBBLESTONE_WALL : Material.STONE_BRICK_WALL, "up=true");
                } else {
                    for (int y = floor; y <= ground + 1; y++) if (!c.has(x, y, z)) c.set(x, y, z, Material.AIR);
                    if (!inside && !c.has(x, floor - 1, z)) c.set(x, floor - 1, z, Material.GRASS_BLOCK);
                }
            } else if (ground < floor - 1) {
                // Fill: a plinth under the building and a terrace round it, faced in stone at its edge.
                for (int y = ground + 1; y < floor; y++) if (!c.has(x, y, z)) {
                    boolean visible = edge || inside && ring == 0;
                    c.set(x, y, z, visible ? weathered(b, 0, y - ground, 0, face) : Material.DIRT);
                }
                if (!inside && !edge && !c.has(x, floor - 1, z)) c.set(x, floor - 1, z, Material.GRASS_BLOCK);
                if (edge && !c.has(x, floor, z) && (x + z) % 3 != 0) c.set(x, floor, z, face == Material.COBBLESTONE ? Material.COBBLESTONE_WALL : Material.STONE_BRICK_WALL, "up=true");
            }
        }
    }

    /**
     * The way to a door on local z = 0: a three-wide notch cut through whatever the site left in front of it, and
     * steps up or down to the ground beyond, so no door ever faces a bank or a wall.
     */
    void approach(ReachBrush b, int door, int reach, Style s) {
        int floor = b.oy;
        for (int k = 1; k <= reach; k++) {
            for (int side : new int[] {-2, 2}) faceCut(b, door + side, -k, floor - 1, s);
        }
        for (int k = 1; k <= reach; k++) for (int side = -1; side <= 1; side++) {
            int x = b.wx(door + side, -k), z = b.wz(door + side, -k);
            for (int y = floor; y < floor + 3; y++) b.canvas.set(x, y, z, Material.AIR);
            // A laid doorstep in the house's own stone, not a patch of mud on the lawn.
            boolean rustic = s.base() == Material.COBBLESTONE;
            b.canvas.set(x, floor - 1, z, side == 0 ? (rustic ? Material.COBBLESTONE : Material.POLISHED_ANDESITE)
                : (rustic ? Material.GRAVEL : Material.STONE_BRICKS));
        }
        // Beyond the notch, follow the ground with steps for up to four blocks.
        int level = floor - 1;
        for (int k = reach + 1; k <= reach + 6; k++) {
            int x = b.wx(door, -k), z = b.wz(door, -k);
            ReachTerrain.Column col = terrain.column(x, z);
            if (!col.land()) break;
            int ground = col.top();
            if (ground == level) break;
            int next = ground > level ? level + 1 : level - 1;
            for (int side : new int[] {-2, 2}) faceCut(b, door + side, -k, Math.max(level, next), s);
            for (int side = -1; side <= 1; side++) {
                int sx = b.wx(door + side, -k), sz = b.wz(door + side, -k);
                for (int y = next + 1; y < next + 4; y++) b.canvas.set(sx, y, sz, Material.AIR);
                // Climbing away from the door the step's riser faces the house; dropping away it faces the street.
                if (ground > level) b.canvas.set(sx, next, sz, s.base() == Material.COBBLESTONE ? Material.COBBLESTONE_STAIRS : Material.STONE_BRICK_STAIRS,
                    "facing=" + ReachBrush.turnFace("north", b.turn) + ",half=bottom");
                else {
                    b.canvas.set(sx, next + 1, sz, s.base() == Material.COBBLESTONE ? Material.COBBLESTONE_STAIRS : Material.STONE_BRICK_STAIRS,
                        "facing=" + ReachBrush.turnFace("south", b.turn) + ",half=bottom");
                    for (int y = ground + 1; y <= next; y++) b.canvas.set(sx, y, sz, Material.STONE_BRICKS);
                }
            }
            level = next;
        }
    }

    /** Faces the side of a cut beside a path: where the ground stands above the path, a stone wall holds it, capped. */
    private void faceCut(ReachBrush b, int lx, int lz, int pathTop, Style s) {
        int x = b.wx(lx, lz), z = b.wz(lx, lz);
        ReachTerrain.Column col = terrain.column(x, z);
        if (!col.land() || col.top() <= pathTop) return;
        for (int y = pathTop + 1; y <= col.top(); y++) if (!b.canvas.has(x, y, z)) b.canvas.set(x, y, z, weathered(b, lx, y - b.oy, lz, s.base()));
        if (!b.canvas.has(x, col.top() + 1, z))
            b.canvas.set(x, col.top() + 1, z, s.base() == Material.COBBLESTONE ? Material.MOSSY_COBBLESTONE_WALL : Material.STONE_BRICK_WALL, "up=true");
    }

    // ------------------------------------------------------------------ houses

    /**
     * A house with its front on local z = 0, {@code w} wide and {@code d} deep. The ground storey is stone; upper
     * storeys are timber framed, the first one jettied over the street when {@code jetty} is set. {@code gableFront}
     * puts the gable to the street (the narrow-lot look of ref 7); otherwise the eaves face the street.
     */
    void house(ReachBrush b, int w, int d, int floors, Style s, boolean jetty, boolean gableFront, long seed) {
        house(b, w, d, floors, s, jetty, gableFront, seed, ReachInteriors.Use.HOME, ReachInteriors.Trade.GENERAL);
    }

    /**
     * As above, furnished for {@code use}; returns where its keeper stands (local x, y, z), or null when it has
     * none, so a town can put its shopkeeper behind the counter.
     */
    int[] house(ReachBrush b, int w, int d, int floors, Style s, boolean jetty, boolean gableFront, long seed,
                ReachInteriors.Use use, ReachInteriors.Trade trade) {
        int ground = 4, storey = 4;
        s = plasterFor(s, seed);
        site(b, -1, jetty ? -2 : -1, w, d, 2, s.base());
        approach(b, w / 2, 3, s);
        // Footing: a stone lip one wider than the walls, carried down to the terrain.
        for (int x = -1; x <= w; x++) for (int z = -1; z <= d; z++) {
            b.footing(terrain, x, -2, z, s.base());
            b.set(x, -1, z, (x == -1 || x == w || z == -1 || z == d) ? weathered(b, x, -1, z, s.base()) : s.plank());
        }
        // Ground storey in stone with proud corner piers.
        for (int y = 0; y < ground; y++) for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
            boolean wall = x == 0 || x == w - 1 || z == 0 || z == d - 1;
            if (!wall) { b.set(x, y, z, Material.AIR); continue; }
            b.set(x, y, z, weathered(b, x, y, z, s.base()));
        }
        for (int[] c : new int[][] {{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
            int px = c[0] == 0 ? -1 : w, pz = c[1] == 0 ? -1 : d;
            for (int y = -1; y < ground; y++) b.set(px, y, c[1], s.baseAccent());
            for (int y = -1; y < ground; y++) b.set(c[0], y, pz, s.baseAccent());
        }
        // Door in the middle of the front, under a little hood.
        int door = w / 2;
        b.set(door, 0, 0, s.door(), "facing=north,half=lower,hinge=left,open=false");
        b.set(door, 1, 0, s.door(), "facing=north,half=upper,hinge=left,open=false");
        b.set(door, 2, -1, s.roofStair(), "facing=south,half=top");
        b.set(door - 1, 2, -1, s.roofStair(), "facing=east,half=top");
        b.set(door + 1, 2, -1, s.roofStair(), "facing=west,half=top");
        b.set(door - 1, 0, -1, Material.LANTERN);
        // Ground-floor windows with sills.
        for (int x = 2; x < w - 2; x += 3) if (Math.abs(x - door) > 1) groundWindow(b, x, 0, s);
        for (int z = 2; z < d - 2; z += 3) { groundWindowSide(b, 0, z, -1, s); groundWindowSide(b, w - 1, z, 1, s); }
        // A plinth: a chamfered course of stone round the foot of the walls, broken only by the door.
        Material plinth = s.base() == Material.COBBLESTONE ? Material.COBBLESTONE_STAIRS
            : s.base() == Material.DEEPSLATE_BRICKS ? Material.DEEPSLATE_BRICK_STAIRS : Material.STONE_BRICK_STAIRS;
        for (int x = 0; x < w; x++) if (Math.abs(x - door) > 0) setIfEmpty(b, x, 0, -1, plinth, "facing=south,half=bottom");
        for (int x = 0; x < w; x++) setIfEmpty(b, x, 0, d, plinth, "facing=north,half=bottom");
        for (int z = 0; z < d; z++) {
            setIfEmpty(b, -1, 0, z, plinth, "facing=east,half=bottom");
            setIfEmpty(b, w, 0, z, plinth, "facing=west,half=bottom");
        }

        // Upper storeys.
        int y0 = ground;
        int front = jetty ? -1 : 0;
        for (int f = 0; f < floors; f++) {
            int fy = y0 + f * storey;
            int fz = f == 0 ? front : front;
            beamRing(b, w, d, fy, fz, s);
            if (jetty && f == 0) for (int x = 0; x < w; x += 2) b.set(x, fy - 1, -1, s.roofStair(), "facing=south,half=top");
            for (int y = fy + 1; y < fy + storey; y++) {
                for (int x = 0; x < w; x++) frameCell(b, x, y, fz, x == 0 || x == w - 1 || x % 3 == 0, s, seed);
                for (int z = fz + 1; z < d; z++) {
                    frameCell(b, 0, y, z, z == d - 1 || z % 3 == 0, s, seed);
                    frameCell(b, w - 1, y, z, z == d - 1 || z % 3 == 0, s, seed);
                }
                for (int x = 0; x < w; x++) frameCell(b, x, y, d - 1, x == 0 || x == w - 1 || x % 3 == 0, s, seed);
                for (int x = 1; x < w - 1; x++) for (int z = fz + 1; z < d - 1; z++) b.set(x, y, z, Material.AIR);
            }
            for (int x = 1; x < w - 1; x++) for (int z = fz + 1; z < d - 1; z++) b.set(x, fy, z, s.plank());
            // Windows between posts with open shutters and a flower box on the front; some panels braced instead.
            for (int x = 1; x < w - 1; x++) {
                if (x % 3 == 0) continue;
                int panel = x / 3;
                boolean braced = Math.abs(x - door) > 2 && (seed >> (panel * 2 + f * 5) & 3) == 0;
                if (braced) brace(b, x, fy, fz, x < door, s);
                else upperWindow(b, x, fy + 1, fz, s, (seed >> x & 3) == 0);
            }
            b.set(w / 2, fy + 2, fz + 1, Material.LANTERN, "hanging=true");
            // Corner posts doubled on the street face, so the frame reads as heavy timber, not a drawn line.
            for (int y = fy + 1; y < fy + storey; y++) {
                setIfEmpty(b, -1, y, fz, s.post(), "axis=y");
                setIfEmpty(b, w, y, fz, s.post(), "axis=y");
            }
            if (f == floors - 1 && floors >= 1 && (seed >> 13 & 3) != 0 && w >= 7) balcony(b, door, fy, fz, s);
        }
        int top = y0 + floors * storey;
        beamRing(b, w, d, top, front, s);
        if (gableFront) roof(b, -1, w, front - 1, d, top + 1, s, true, true);
        // Eaves to the street: the same roof in a frame turned a quarter, so its ridge runs along the front.
        else roof(b.at(w - 1, 0, 0, 1), front - 1, d, -1, w, top + 1, s, true, true);
        int span = gableFront ? w + 2 : d - front + 2;
        int chimX = gableFront ? ((seed & 1) == 0 ? 1 : w - 2) : 1;
        chimney(b, chimX, d - 2, top + span / 2 + 2, s, top);
        // A dormer breaks a long roof facing the street.
        if (!gableFront && w >= 9) dormer(b, w / 2 + ((seed & 2) == 0 ? -2 : 2), top + 1, front - 1, s);
        // Life at the door: a barrel, a crate or a bench, and a pot of flowers.
        b.set(door + 2, 0, -1, (seed & 4) == 0 ? Material.BARREL : Material.COMPOSTER, (seed & 4) == 0 ? "facing=up" : "level=4");
        if ((seed & 8) == 0) b.set(door - 2, 0, -1, s.roofStair(), "facing=north,half=bottom");
        if ((seed & 16) == 0) b.set(door + 3, 0, -1, Material.POTTED_AZURE_BLUET);
        sides(b, w, d, floors, front, s, seed);
        return ReachInteriors.furnish(b, new ReachInteriors.Layout(w, d, floors, front, chimX), use, trade, seed);
    }

    /**
     * Detail on the side walls, which a street sees as much as the front: upper-floor windows with flower boxes,
     * a diagonal brace in the frame, and a lean-to or a log store against the ground storey.
     */
    private void sides(ReachBrush b, int w, int d, int floors, int front, Style s, long seed) {
        for (int side : new int[] {0, w - 1}) {
            int out = side == 0 ? -1 : 1;
            String along = "north=true,south=true";
            for (int f = 0; f < floors; f++) {
                int fy = 4 + f * 4;
                for (int z = front + 2; z < d - 2; z += 3) {
                    if (b.has(side, fy + 1, z) && b.canvas.get(b.wx(side, z), b.oy + fy + 1, b.wz(side, z)).state().contains("_log")) continue;
                    b.set(side, fy + 1, z, Material.GLASS_PANE, along);
                    b.set(side, fy + 2, z, Material.GLASS_PANE, along);
                    setIfEmpty(b, side + out, fy, z, s.trapdoor(), "facing=" + (out < 0 ? "east" : "west") + ",half=top,open=false");
                    setIfEmpty(b, side + out, fy + 1, z, (seed + z) % 2 == 0 ? Material.POTTED_POPPY : Material.POTTED_DANDELION, "");
                }
                // A diagonal brace between two posts, as a timber frame would have.
                if (d >= 8 && (seed >> f & 1) == 0) for (int k = 0; k < 3; k++) {
                    int z = front + 4 + k, y = fy + 1 + k;
                    if (z < d - 1 && y < fy + 4 && !b.canvas.get(b.wx(side, z), b.oy + y, b.wz(side, z)).state().contains("pane")) b.set(side, y, z, s.beam(), "axis=z");
                }
            }
            if ((seed >> 20 & 3) == (side == 0 ? 0 : 1) && d >= 8) {
                // A lean-to store against the ground floor: posts, a sloping roof, logs stacked inside.
                for (int z = 2; z <= 5; z++) {
                    setIfEmpty(b, side + out * 2, 0, z, z == 2 || z == 5 ? s.fence() : Material.STRIPPED_SPRUCE_LOG, z == 2 || z == 5 ? "" : "axis=z");
                    setIfEmpty(b, side + out, 0, z, Material.STRIPPED_SPRUCE_LOG, "axis=z");
                    setIfEmpty(b, side + out, 1, z, (z & 1) == 0 ? Material.SPRUCE_LOG : Material.STRIPPED_SPRUCE_LOG, "axis=z");
                    setIfEmpty(b, side + out * 2, 1, z, z == 2 || z == 5 ? s.fence() : Material.AIR, "");
                    setIfEmpty(b, side + out, 2, z, s.roofStair(), "facing=" + (out < 0 ? "east" : "west") + ",half=bottom");
                    setIfEmpty(b, side + out * 2, 2, z, s.roofSlab(), "type=bottom");
                }
            }
        }
    }

    /** Each house's plaster: white, cream, or a warm pinkish lime, chosen by its seed, for styles with lime infill. */
    static Style plasterFor(Style s, long seed) {
        if (s.plaster() != Material.CALCITE && s.plaster() != Material.SMOOTH_SANDSTONE) return s;
        Material[] lime = {Material.CALCITE, Material.SMOOTH_SANDSTONE, Material.WHITE_TERRACOTTA, Material.CALCITE, Material.MUSHROOM_STEM};
        Material p = lime[(int) Math.floorMod(seed * 31 + (seed >> 7), (long) lime.length)];
        if (p == s.plaster()) return s;
        return s.withPlaster(p, p == Material.SMOOTH_SANDSTONE ? Material.SANDSTONE : p);
    }

    /** The roof's weathered patches: an older, darker or mossier course of the same roof, never single speckles. */
    private Material[] roofAccent(Style s) {
        return switch (s.roofStair()) {
            case SPRUCE_STAIRS -> new Material[] {Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS, Material.DARK_OAK_SLAB};
            case DARK_OAK_STAIRS -> new Material[] {Material.SPRUCE_STAIRS, Material.SPRUCE_PLANKS, Material.SPRUCE_SLAB};
            case DEEPSLATE_TILE_STAIRS -> new Material[] {Material.COBBLED_DEEPSLATE_STAIRS, Material.COBBLED_DEEPSLATE, Material.COBBLED_DEEPSLATE_SLAB};
            case MANGROVE_STAIRS -> new Material[] {Material.BRICK_STAIRS, Material.BRICKS, Material.BRICK_SLAB};
            default -> null;
        };
    }

    private boolean weatheredRoof(ReachBrush b, int lx, int ly, int lz) {
        // Broad patches several blocks across, the way a roof is mended a section at a time.
        double p = terrain.patch(b.wx(lx, lz) + ly * 0.6, b.wz(lx, lz) - ly * 0.4, 0.11);
        return p > 0.55;
    }

    /** Sets a block only where nothing is built yet, so additions never cut into a neighbour. */
    static void setIfEmpty(ReachBrush b, int x, int y, int z, Material m, String props) {
        if (!b.has(x, y, z)) b.set(x, y, z, m, props);
    }

    /**
     * A balcony on the top storey: a plank floor two deep on brackets, a railing with planters at its corners, and
     * a door out to it in place of the middle window.
     */
    private void balcony(ReachBrush b, int door, int fy, int fz, Style s) {
        for (int x = door - 1; x <= door + 1; x++) for (int z = fz - 2; z <= fz - 1; z++) {
            b.set(x, fy, z, s.plank());
            for (int y = fy + 1; y <= fy + 2; y++) b.set(x, y, z, Material.AIR);
        }
        for (int x = door - 1; x <= door + 1; x++) b.set(x, fy + 1, fz - 3, s.fence(), "east=true,west=true");
        b.set(door - 2, fy + 1, fz - 3, s.fence(), "east=true,south=true");
        b.set(door + 2, fy + 1, fz - 3, s.fence(), "west=true,south=true");
        for (int z = fz - 2; z <= fz - 1; z++) {
            b.set(door - 2, fy + 1, z, s.fence(), "north=true,south=" + (z < fz - 1));
            b.set(door + 2, fy + 1, z, s.fence(), "north=true,south=" + (z < fz - 1));
        }
        for (int x : new int[] {door - 2, door + 2}) b.set(x, fy + 2, fz - 3, Material.POTTED_RED_TULIP);
        for (int x : new int[] {door - 1, door + 1}) b.set(x, fy - 1, fz - 1, s.roofStair(), "facing=south,half=top");
        b.set(door, fy - 1, fz - 2, s.roofStair(), "facing=south,half=top");
        b.set(door, fy + 1, fz, s.door(), "facing=north,half=lower,hinge=left,open=false");
        b.set(door, fy + 2, fz, s.door(), "facing=north,half=upper,hinge=left,open=false");
        b.set(door - 1, fy + 3, fz - 1, Material.LANTERN, "hanging=true");
    }

    /** A hanging trade sign on its iron bracket beside a door on local z = 0, at height {@code y}. */
    static void shopSign(ReachBrush b, int x, int y, Material sign, String... lines) {
        b.set(x, y, -1, sign, "facing=east");
        if (lines.length > 0) b.canvas.text(b.wx(x, -1), b.oy + y, b.wz(x, -1), lines);
    }

    /** A small gabled dormer with a window, sitting on the roof's front slope. */
    private void dormer(ReachBrush b, int cx, int y, int z, Style s) {
        for (int dy = 0; dy < 3; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = 0; dz <= 2; dz++) {
            boolean face = dz == 0;
            if (dy == 2 && dx != 0) continue;
            b.set(cx + dx, y + dy, z + dz, face && dx == 0 && dy < 2 ? Material.GLASS_PANE : face ? s.post() : s.plaster(),
                face && dx == 0 && dy < 2 ? "east=true,west=true" : face && dx != 0 ? "axis=y" : "");
        }
        for (int dz = -1; dz <= 3; dz++) {
            b.set(cx - 2, y + 1, z + dz, s.roofStair(), "facing=east,half=bottom");
            b.set(cx + 2, y + 1, z + dz, s.roofStair(), "facing=west,half=bottom");
            b.set(cx - 1, y + 2, z + dz, s.roofStair(), "facing=east,half=bottom");
            b.set(cx + 1, y + 2, z + dz, s.roofStair(), "facing=west,half=bottom");
            b.set(cx, y + 3, z + dz, s.roofSlab(), "type=bottom");
        }
    }

    private void groundWindow(ReachBrush b, int x, int z, Style s) {
        b.set(x, 1, z, Material.GLASS_PANE, "east=true,west=true");
        b.set(x, 2, z, Material.GLASS_PANE, "east=true,west=true");
        b.set(x, 0, z - 1, s.roofStair(), "facing=south,half=top");
        // Open shutters lie flat against the wall either side of the window.
        for (int side : new int[] {-1, 1}) for (int y = 1; y <= 2; y++)
            setIfEmpty(b, x + side, y, z - 1, s.trapdoor(), "facing=north,half=" + (y == 1 ? "bottom" : "top") + ",open=true");
    }

    private void groundWindowSide(ReachBrush b, int x, int z, int out, Style s) {
        b.set(x, 1, z, Material.GLASS_PANE, "north=true,south=true");
        b.set(x, 2, z, Material.GLASS_PANE, "north=true,south=true");
        b.set(x + out, 0, z, s.roofStair(), "facing=" + (out < 0 ? "east" : "west") + ",half=top");
    }

    private void frameCell(ReachBrush b, int x, int y, int z, boolean post, Style s, long seed) {
        if (post) { b.set(x, y, z, s.post(), "axis=y"); return; }
        b.set(x, y, z, (seed + x * 7 + y * 3) % 11 == 0 ? s.plasterAccent() : s.plaster());
    }

    /**
     * One cell of a braced panel: the panel between two posts carries a diagonal timber rising toward the house's
     * middle, the classic brace of a framed wall, with plaster round it.
     */
    private void brace(ReachBrush b, int x, int fy, int z, boolean leftOfDoor, Style s) {
        int inPanel = x % 3;
        int rise = leftOfDoor ? inPanel : 3 - inPanel;
        for (int y = fy + 1; y <= fy + 3; y++) {
            boolean timber = y - fy == rise || y - fy == rise + 1 && rise == 2;
            b.set(x, y, z, timber ? s.beam() : s.plaster(), timber ? "axis=x" : "");
        }
    }

    private void upperWindow(ReachBrush b, int x, int y, int z, Style s, boolean flowers) {
        b.set(x, y, z, Material.GLASS_PANE, "east=true,west=true");
        b.set(x, y + 1, z, Material.GLASS_PANE, "east=true,west=true");
        // Open shutters lie flat on the posts either side of the window pair.
        int side = x % 3 == 1 ? -1 : 1;
        for (int dy = 0; dy <= 1; dy++)
            setIfEmpty(b, x + side, y + dy, z - 1, s.trapdoor(), "facing=north,half=" + (dy == 0 ? "bottom" : "top") + ",open=true");
        if (flowers) {
            b.set(x, y - 1, z - 1, s.trapdoor(), "facing=south,half=top,open=false");
            b.set(x, y, z - 1, Material.POTTED_RED_TULIP);
        } else b.set(x, y + 2, z - 1, s.roofSlab(), "type=top");
    }

    /** Horizontal timber at a floor line, with beam ends poking out at the corners. */
    private void beamRing(ReachBrush b, int w, int d, int y, int front, Style s) {
        for (int x = 0; x < w; x++) { b.set(x, y, front, s.beam(), "axis=x"); b.set(x, y, d - 1, s.beam(), "axis=x"); }
        for (int z = front; z < d; z++) { b.set(0, y, z, s.beam(), "axis=z"); b.set(w - 1, y, z, s.beam(), "axis=z"); }
        b.set(-1, y, front, s.beam(), "axis=x");
        b.set(w, y, front, s.beam(), "axis=x");
        b.set(-1, y, d - 1, s.beam(), "axis=x");
        b.set(w, y, d - 1, s.beam(), "axis=x");
    }

    /**
     * A gable roof over local x {@code x0..x1} and z {@code z0..z1}, ridge running along z, eaves on both x sides
     * starting at {@code y}. The gable ends are closed with plaster and timber.
     */
    void roof(ReachBrush b, int x0, int x1, int z0, int z1, int y, Style s, boolean closeGables) {
        roof(b, x0, x1, z0, z1, y, s, closeGables, false);
    }

    /**
     * As above; {@code deep} adds one more course below the eaves on both sides, carried on struts every other block,
     * so the roof throws a real shadow down the wall (the Shovel look). The extra course never cuts into a neighbour.
     */
    void roof(ReachBrush b, int x0, int x1, int z0, int z1, int y, Style s, boolean closeGables, boolean deep) {
        if (deep) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                setIfEmpty(b, x0 - 1, y - 1, z, s.roofStair(), "facing=east,half=bottom");
                setIfEmpty(b, x1 + 1, y - 1, z, s.roofStair(), "facing=west,half=bottom");
                if (Math.floorMod(z - z0, 2) == 0 && z >= z0 && z <= z1) {
                    setIfEmpty(b, x0, y - 2, z, s.roofStair(), "facing=east,half=top");
                    setIfEmpty(b, x1, y - 2, z, s.roofStair(), "facing=west,half=top");
                }
            }
        }
        int span = x1 - x0 + 1, half = span / 2;
        Material[] aged = roofAccent(s);
        for (int i = 0; i < half; i++) {
            int ly = y + i;
            for (int z = z0; z <= z1; z++) {
                boolean oldL = aged != null && weatheredRoof(b, x0 + i, ly, z), oldR = aged != null && weatheredRoof(b, x1 - i, ly, z);
                b.set(x0 + i, ly, z, oldL ? aged[0] : s.roofStair(), "facing=east,half=bottom");
                b.set(x1 - i, ly, z, oldR ? aged[0] : s.roofStair(), "facing=west,half=bottom");
                if (i > 0) { b.set(x0 + i, ly - 1, z, s.roofBlock()); b.set(x1 - i, ly - 1, z, s.roofBlock()); }
            }
            // Eaves: upside-down stairs under the lowest course.
            if (i == 0) for (int z = z0; z <= z1; z++) {
                b.set(x0, ly - 1, z, s.roofStair(), "facing=west,half=top");
                b.set(x1, ly - 1, z, s.roofStair(), "facing=east,half=top");
            }
            if (closeGables && i > 0) for (int x = x0 + i; x <= x1 - i; x++) {
                Material fill = (x - x0) % 3 == 0 ? s.post() : s.plaster();
                String axis = fill == s.post() ? "axis=y" : "";
                if (axis.isEmpty()) { b.set(x, ly - 1, z0 + 1, fill); b.set(x, ly - 1, z1 - 1, fill); }
                else { b.set(x, ly - 1, z0 + 1, fill, axis); b.set(x, ly - 1, z1 - 1, fill, axis); }
            }
        }
        // An odd span gets a slab ridge; an even span's two top courses meet under a capping of slabs.
        Material cap = aged != null ? aged[2] : s.roofSlab();
        if (span % 2 == 1) for (int z = z0; z <= z1; z++) b.set(x0 + half, y + half - 1, z, cap, "type=bottom");
        else if (half >= 2) for (int z = z0 - 1; z <= z1 + 1; z++) {
            setIfEmpty(b, x0 + half - 1, y + half, z, cap, "type=bottom");
            setIfEmpty(b, x0 + half, y + half, z, cap, "type=bottom");
        }
        // Barge boards: the gable's edge stands a step proud of the wall, in the frame's dark timber.
        for (int i = 0; i < half; i++) for (int gz : new int[] {z0 - 1, z1 + 1}) {
            b.set(x0 + i, y + i, gz, s.roofStair(), "facing=east,half=bottom");
            b.set(x1 - i, y + i, gz, s.roofStair(), "facing=west,half=bottom");
            b.set(x0 + i, y + i - 1, gz, s.roofStair(), "facing=west,half=top");
            b.set(x1 - i, y + i - 1, gz, s.roofStair(), "facing=east,half=top");
        }
        if (span % 2 == 1) for (int gz : new int[] {z0 - 1, z1 + 1}) b.set(x0 + half, y + half - 1, gz, s.roofSlab(), "type=bottom");
        // A small window high in each gable.
        if (closeGables && half >= 4) for (int gz : new int[] {z0 + 1, z1 - 1}) {
            int cx = x0 + span / 2;
            b.set(cx, y + half - 3, gz, Material.GLASS_PANE, "east=true,west=true");
            b.set(cx, y + half - 4, gz, Material.GLASS_PANE, "east=true,west=true");
        }
    }

    /**
     * A chimney: a stone flue up through the house, then a three-by-three brick stack above the roof with a hollow
     * core. The fire sits two blocks down inside it on a bale of hay, so smoke climbs out of the top and no flame shows.
     */
    private void chimney(ReachBrush b, int x, int z, int top, Style s, int wallTop) {
        int from = Math.min(top - 5, wallTop + 1);
        for (int y = 0; y < from; y++) if (!b.has(x, y, z) || y > 3) b.set(x, y, z, s.base());
        for (int y = from; y <= top; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean core = dx == 0 && dz == 0;
            if (!core) b.set(x + dx, y, z + dz, y == from || y == top ? Material.STONE_BRICKS : Material.BRICKS);
            else b.set(x, y, z, y >= top - 1 ? Material.AIR : y == top - 2 ? Material.CAMPFIRE : y == top - 3 ? Material.HAY_BLOCK : Material.BRICKS,
                y == top - 2 ? "lit=true,signal_fire=false" : y == top - 3 ? "axis=y" : "");
        }
        for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) b.set(x + c[0], top + 1, z + c[1], Material.BRICK_SLAB, "type=bottom");
    }

    // ------------------------------------------------------------------ towers (ref 5)

    /**
     * A tall narrow tower: a dark stone base, timber storeys with long windows, an overhanging top storey and a steep
     * spire. {@code size} is odd (7 or 9).
     */
    void tower(ReachBrush b, int size, int height, Style s, long seed) {
        site(b, -1, -1, size, size, 2, s.base());
        approach(b, size / 2, 3, s);
        int baseTop = height / 3;
        int r = size / 2;
        for (int x = -1; x <= size; x++) for (int z = -1; z <= size; z++) b.footing(terrain, x, -1, z, s.base());
        for (int y = -1; y < height; y++) for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            boolean wall = x == 0 || x == size - 1 || z == 0 || z == size - 1;
            if (!wall) { b.set(x, y, z, y == -1 || y % 6 == 5 ? s.plank() : Material.AIR); continue; }
            boolean corner = (x == 0 || x == size - 1) && (z == 0 || z == size - 1);
            if (y < baseTop) b.set(x, y, z, corner ? s.baseAccent() : weathered(b, x, y, z, s.base()));
            else if (corner || x == r || z == r) b.set(x, y, z, s.post(), "axis=y");
            else if (y % 6 == 0) b.set(x, y, z, s.beam(), x == 0 || x == size - 1 ? "axis=z" : "axis=x");
            else b.set(x, y, z, s.plaster());
        }
        // Long windows on each face of the timber part.
        for (int y = baseTop + 2; y < height - 3; y += 6) for (int k = 1; k < size - 1; k++) if (k != r && Math.abs(k - r) <= 2) {
            b.set(k, y, 0, Material.GLASS_PANE, "east=true,west=true");
            b.set(k, y + 1, 0, Material.GLASS_PANE, "east=true,west=true");
            b.set(k, y + 2, 0, Material.GLASS_PANE, "east=true,west=true");
            b.set(k, y, size - 1, Material.GLASS_PANE, "east=true,west=true");
            b.set(k, y + 1, size - 1, Material.GLASS_PANE, "east=true,west=true");
            b.set(0, y, k, Material.GLASS_PANE, "north=true,south=true");
            b.set(0, y + 1, k, Material.GLASS_PANE, "north=true,south=true");
            b.set(size - 1, y, k, Material.GLASS_PANE, "north=true,south=true");
            b.set(size - 1, y + 1, k, Material.GLASS_PANE, "north=true,south=true");
        }
        // A door in the middle of the front, framed by the corner stone.
        b.set(r, 0, 0, s.door(), "facing=north,half=lower,hinge=left,open=false");
        b.set(r, 1, 0, s.door(), "facing=north,half=upper,hinge=left,open=false");
        b.set(r, 2, -1, s.roofStair(), "facing=south,half=top");
        b.set(r - 1, 2, -1, Material.LANTERN, "hanging=true");
        // Jettied top storey on corbels, then a steep spire.
        int top = height;
        for (int x = -1; x <= size; x++) for (int z = -1; z <= size; z++) {
            boolean ring = x == -1 || x == size || z == -1 || z == size;
            if (ring) b.set(x, top - 1, z, s.roofStair(), x == -1 ? "facing=east,half=top" : x == size ? "facing=west,half=top"
                : z == -1 ? "facing=south,half=top" : "facing=north,half=top");
        }
        for (int y = top; y < top + 4; y++) for (int x = -1; x <= size; x++) for (int z = -1; z <= size; z++) {
            boolean edge = x == -1 || x == size || z == -1 || z == size;
            if (!edge) { b.set(x, y, z, y == top ? s.plank() : Material.AIR); continue; }
            boolean corner = (x == -1 || x == size) && (z == -1 || z == size);
            b.set(x, y, z, corner ? s.post() : (y == top + 1 || y == top + 2) && (x + z) % 2 == 0 ? Material.GLASS : s.plaster(),
                corner ? "axis=y" : "");
        }
        spire(b, -2, size + 1, top + 4, s);
        b.set(r, top + 2, r, Material.LANTERN);
        towerDetail(b, size, height, baseTop, s, seed);
    }

    /**
     * What makes a tower read as built rather than extruded: a corbelled string course at each floor, shutters and
     * sills on the long windows, a balcony on brackets halfway up, banners on the face, and stepped piers at the foot.
     */
    private void towerDetail(ReachBrush b, int size, int height, int baseTop, Style s, long seed) {
        int r = size / 2;
        String[] outFace = {"south", "west", "north", "east"};
        // String courses: upside-down stairs round the tower at every floor line of the timber part.
        for (int y = baseTop; y < height - 2; y += 6) for (int k = 0; k < size; k++) {
            setIfEmpty(b, k, y, -1, s.roofStair(), "facing=south,half=top");
            setIfEmpty(b, k, y, size, s.roofStair(), "facing=north,half=top");
            setIfEmpty(b, -1, y, k, s.roofStair(), "facing=east,half=top");
            setIfEmpty(b, size, y, k, s.roofStair(), "facing=west,half=top");
        }
        // Sills under the long windows and shutters flat against the wall either side.
        for (int y = baseTop + 2; y < height - 3; y += 6) for (int k = 1; k < size - 1; k++) if (k != r && Math.abs(k - r) <= 2) {
            setIfEmpty(b, k, y - 1, -1, s.roofStair(), "facing=south,half=top");
            setIfEmpty(b, k, y - 1, size, s.roofStair(), "facing=north,half=top");
        }
        for (int y = baseTop + 2; y < height - 3; y += 6) for (int dy = 0; dy <= 1; dy++) for (int side : new int[] {r - 3, r + 3}) {
            if (side <= 0 || side >= size - 1) continue;
            setIfEmpty(b, side, y + dy, -1, s.trapdoor(), "facing=north,half=bottom,open=true");
            setIfEmpty(b, side, y + dy, size, s.trapdoor(), "facing=south,half=bottom,open=true");
        }
        // A balcony on brackets round the front at the floor above the stone base.
        int by = baseTop + 5;
        if (height >= 24) {
            for (int x = -1; x <= size; x++) {
                setIfEmpty(b, x, by, -1, s.plank(), "");
                setIfEmpty(b, x, by, -2, s.plank(), "");
                setIfEmpty(b, x, by + 1, -3, s.fence(), "");
                if (x % 2 == 0) setIfEmpty(b, x, by - 1, -1, s.roofStair(), "facing=south,half=top");
            }
            for (int z = -3; z <= -1; z++) { setIfEmpty(b, -2, by + 1, z, s.fence(), ""); setIfEmpty(b, size + 1, by + 1, z, s.fence(), ""); }
            b.set(r, by + 1, 0, s.door(), "facing=north,half=lower,hinge=left,open=false");
            b.set(r, by + 2, 0, s.door(), "facing=north,half=upper,hinge=left,open=false");
            setIfEmpty(b, r + 2, by + 3, -1, Material.LANTERN, "hanging=true");
        }
        // Banners down the faces, under the top storey.
        Material banner = (seed & 1) == 0 ? Material.GREEN_WALL_BANNER : Material.BLUE_WALL_BANNER;
        int bannerY = height - 4;
        setIfEmpty(b, r, bannerY, -1, banner, "facing=north");
        setIfEmpty(b, size, bannerY, r, banner, "facing=east");
        setIfEmpty(b, -1, bannerY, r, banner, "facing=west");
        // Stepped piers at the foot of each corner.
        for (int[] cc : new int[][] {{-1, -1}, {size, -1}, {-1, size}, {size, size}}) for (int y = -1; y <= 2; y++)
            b.set(cc[0], y, cc[1], y == 2 ? Material.STONE_BRICK_SLAB : s.baseAccent(), y == 2 ? "type=bottom" : "");
    }

    /**
     * A defensive tower: stone to two-thirds of its height with arrow slits, a timber gallery on corbels, and a
     * steep spire. Used for gatehouses and keeps, where the tall plaster towers would look too light.
     */
    void keepTower(ReachBrush b, int size, int height, Style s, long seed) {
        site(b, -1, -1, size, size, 1, s.base());
        int r = size / 2, stoneTop = height * 2 / 3;
        for (int x = -1; x <= size; x++) for (int z = -1; z <= size; z++) b.footing(terrain, x, -1, z, s.base());
        for (int y = -1; y < height; y++) for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            boolean wall = x == 0 || x == size - 1 || z == 0 || z == size - 1;
            boolean corner = (x == 0 || x == size - 1) && (z == 0 || z == size - 1);
            if (!wall) { b.set(x, y, z, y % 8 == 7 ? s.plank() : Material.AIR); continue; }
            if (y < stoneTop) {
                boolean slit = y % 8 == 3 && (x == r || z == r) && !corner;
                b.set(x, y, z, slit ? Material.AIR : y < 2 || corner ? s.baseAccent() : weathered(b, x, y, z, Material.STONE_BRICKS));
            } else if (corner || x == r || z == r) b.set(x, y, z, s.post(), "axis=y");
            else if ((y - stoneTop) % 4 == 0) b.set(x, y, z, s.beam(), x == 0 || x == size - 1 ? "axis=z" : "axis=x");
            else b.set(x, y, z, s.plaster());
        }
        // A string course where stone meets timber, on corbels.
        for (int x = -1; x <= size; x++) for (int z = -1; z <= size; z++) {
            boolean ring = x == -1 || x == size || z == -1 || z == size;
            if (!ring) continue;
            b.set(x, stoneTop - 1, z, s.roofStair(), x == -1 ? "facing=east,half=top" : x == size ? "facing=west,half=top"
                : z == -1 ? "facing=south,half=top" : "facing=north,half=top");
            b.set(x, stoneTop, z, Material.STONE_BRICK_SLAB, "type=bottom");
        }
        for (int y = stoneTop + 1; y < height - 1; y += 4) for (int k = 2; k < size - 2; k++) if (k != r) {
            b.set(k, y, 0, Material.GLASS_PANE, "east=true,west=true");
            b.set(k, y + 1, 0, Material.GLASS_PANE, "east=true,west=true");
            b.set(0, y, k, Material.GLASS_PANE, "north=true,south=true");
            b.set(size - 1, y, k, Material.GLASS_PANE, "north=true,south=true");
        }
        spire(b, -2, size + 1, height, s);
        b.set(r, 0, 0, s.door(), "facing=north,half=lower,hinge=left,open=false");
        b.set(r, 1, 0, s.door(), "facing=north,half=upper,hinge=left,open=false");
    }

    /** A steep pyramid roof over a square from {@code lo} to {@code hi}, rising two blocks per step. */
    void spire(ReachBrush b, int lo, int hi, int y, Style s) {
        int level = y;
        for (int a = lo, c = hi; a <= c; a++, c--) {
            for (int k = 0; k < 2; k++, level++) {
                for (int i = a; i <= c; i++) {
                    b.set(i, level, a, s.roofStair(), "facing=south,half=bottom");
                    b.set(i, level, c, s.roofStair(), "facing=north,half=bottom");
                    b.set(a, level, i, s.roofStair(), "facing=east,half=bottom");
                    b.set(c, level, i, s.roofStair(), "facing=west,half=bottom");
                }
                for (int i = a + 1; i < c; i++) for (int j = a + 1; j < c; j++) if (k == 1 || level > y) b.set(i, level - 1, j, s.roofBlock());
            }
            if (c - a <= 1) break;
        }
        int mid = (lo + hi) / 2;
        b.set(mid, level, mid, s.fence());
        b.set(mid, level + 1, mid, s.fence());
        b.set(mid, level + 2, mid, Material.LIGHTNING_ROD);
    }

    // ------------------------------------------------------------------ street furniture

    /** A market stall facing local -z: posts, a striped awning, a counter of barrels and goods. */
    void stall(ReachBrush b, Material cloth, Material stripe, Material goods, Style s) {
        for (int[] p : new int[][] {{0, 0}, {4, 0}, {0, 3}, {4, 3}}) for (int y = 0; y < 3; y++) b.set(p[0], y, p[1], s.fence());
        for (int x = -1; x <= 5; x++) for (int z = -1; z <= 4; z++) {
            int y = z < 1 ? 3 : z < 3 ? 4 : 3;
            b.set(x, y, z, (x % 2 == 0) ? cloth : stripe);
        }
        b.set(1, 0, 0, Material.BARREL, "facing=up");
        b.set(2, 0, 0, Material.SPRUCE_TRAPDOOR, "facing=north,half=top,open=false");
        b.set(3, 0, 0, Material.BARREL, "facing=north");
        b.set(2, 1, 0, goods);
        b.set(1, 1, 0, Material.COMPOSTER, "level=7");
        b.set(3, 0, 3, Material.CHEST, "facing=north");
        b.set(1, 0, 3, Material.BARREL, "facing=up");
    }

    /** A street lamp: stone footing, a dark post, a lantern hung from a bracket. */
    void lamp(ReachBrush b, Style s) {
        b.set(0, 0, 0, Material.STONE_BRICK_WALL, "up=true");
        for (int y = 1; y <= 3; y++) b.set(0, y, 0, s.fence());
        b.set(0, 4, 0, s.post(), "axis=y");
        b.set(1, 4, 0, s.fence(), "west=true");
        b.set(1, 3, 0, Material.LANTERN, "hanging=true");
        b.set(0, 5, 0, s.trapdoor(), "facing=north,half=bottom,open=false");
    }

    /** A signpost at a fork. */
    void signpost(ReachBrush b, Style s) {
        for (int y = 0; y < 3; y++) b.set(0, y, 0, s.fence());
        b.set(1, 2, 0, Material.OAK_WALL_SIGN, "facing=east");
        b.set(-1, 1, 0, Material.OAK_WALL_SIGN, "facing=west");
        b.set(0, 3, 0, Material.LANTERN);
    }

    /** Crates and barrels piled where work happens. */
    void clutter(ReachBrush b, long seed) {
        b.set(0, 0, 0, Material.BARREL, "facing=up");
        b.set(1, 0, 0, (seed & 1) == 0 ? Material.HAY_BLOCK : Material.BARREL, (seed & 1) == 0 ? "axis=y" : "facing=north");
        b.set(0, 1, 0, Material.SPRUCE_TRAPDOOR, "facing=north,half=bottom,open=false");
        b.set(0, 0, 1, Material.COMPOSTER, "level=3");
    }
}
