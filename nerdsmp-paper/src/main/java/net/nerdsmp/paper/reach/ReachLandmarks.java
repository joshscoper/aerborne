package net.nerdsmp.paper.reach;

import net.nerdsmp.paper.reach.ReachTerrain.Column;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Each zone's hero landmark, the thing a player sees from the road and walks toward (vault, 30 Zones): the
 * Huntsman's Barrow, the Stone Rings and Stonewatch on the Barrowdowns; the Heart Tree over Thornwood; the Great
 * Aqueduct across Mistmere; the Deep Foreman's Headframe on the Ironspine; the Sunscar Arch; the Ember Gate on the
 * Rift's lip; the Seal Spire on the Archive Isle. Each is a named place with a world chest, so the zones read as
 * places with purpose before their towns and quests arrive in their phases. Sites are found in the terrain near a
 * planned point: the flattest dry ground that is clear of roads, rivers and the lip.
 */
final class ReachLandmarks {
    private ReachLandmarks() { }

    /** A landmark: its id, the planned point it is sited near, how far it reaches from its site, and its builder. */
    private record Plan(String id, int x, int z, int reach, int reachZ, int search, int roadClear, Builder builder) { }

    private interface Builder { void build(ReachLandmarks.Site s); }

    static List<ReachFeatures.Feature> features(ReachTerrain terrain) {
        List<Plan> plans = List.of(
            new Plan("huntsmans_barrow", -520, 395, 30, 30, 50, 28, ReachLandmarks::barrow),
            new Plan("stone_rings", -700, 540, 16, 16, 50, 16, ReachLandmarks::stoneRing),
            new Plan("stonewatch", -850, 175, 14, 14, 40, 14, ReachLandmarks::watchtower),
            new Plan("heart_tree", -720, -330, 30, 30, 60, 24, ReachLandmarks::heartTree),
            new Plan("great_aqueduct", 150, -745, 100, 18, 30, 0, ReachLandmarks::aqueduct),
            new Plan("headframe", 600, -700, 22, 22, 70, 26, ReachLandmarks::headframe),
            new Plan("sunscar_arch", 830, 300, 34, 14, 60, 38, ReachLandmarks::sunArch),
            new Plan("ember_gate", -10, -70, 26, 26, 30, 0, ReachLandmarks::emberGate),
            new Plan("seal_spire", 1520, -80, 22, 22, 30, 16, ReachLandmarks::sealSpire));
        List<ReachFeatures.Feature> out = new ArrayList<>();
        for (Plan plan : plans) {
            int[] at = plan.id().equals("ember_gate") ? riftLip(terrain, plan.x(), plan.z(), plan.search())
                : plan.id().equals("great_aqueduct") ? new int[] {plan.x(), plan.z()}
                : site(terrain, plan.x(), plan.z(), plan.search(), plan.reach() / 2, plan.roadClear());
            // The barrow's avenue runs out to the road, well past the mound.
            int pad = plan.id().equals("huntsmans_barrow") ? 70 : 0;
            int r = plan.reach() + 4 + pad, rz = plan.reachZ() + 4 + pad;
            out.add(new ReachFeatures.Feature(plan.id(), new ReachFeatures.Footprint(at[0] - r, at[1] - rz, at[0] + r, at[1] + rz), t -> {
                Site s = new Site(t, plan.id(), at[0], at[1]);
                plan.builder().build(s);
                return new ReachFeatures.Built(s.canvas, List.copyOf(s.spots));
            }));
        }
        return out;
    }

    // ------------------------------------------------------------------ siting

    /** The flattest dry land within {@code search} of a point, clear of roads, rivers, lakes and the lip. */
    static int[] site(ReachTerrain terrain, int x0, int z0, int search, int half, int roadClear) {
        int[] best = {x0, z0};
        double bestScore = Double.MAX_VALUE;
        for (int dx = -search; dx <= search; dx += 4) for (int dz = -search; dz <= search; dz += 4) {
            int x = x0 + dx, z = z0 + dz;
            Column c = terrain.column(x, z);
            if (!c.land() || c.wet() || c.edge() < half + 14 || c.river() < half + 8 || c.lake() < 1.3 || c.across() < half + 10) continue;
            if (roadClear > 0 && terrain.roadDistance(x, z, roadClear + 2) < roadClear) continue;
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            boolean ok = true;
            for (int sx = -half; sx <= half && ok; sx += Math.max(2, half / 3)) for (int sz = -half; sz <= half; sz += Math.max(2, half / 3)) {
                Column s = terrain.column(x + sx, z + sz);
                if (!s.land() || s.wet()) { ok = false; break; }
                lo = Math.min(lo, s.top());
                hi = Math.max(hi, s.top());
            }
            if (!ok) continue;
            double score = (hi - lo) + Math.hypot(dx, dz) * 0.04;
            if (score < bestScore) { bestScore = score; best = new int[] {x, z}; }
        }
        return best;
    }

    /** A point on solid ground a few blocks back from the Rift's edge, nearest the planned point. */
    static int[] riftLip(ReachTerrain terrain, int x0, int z0, int search) {
        int[] best = {x0, z0};
        double bestScore = Double.MAX_VALUE;
        for (int dx = -search; dx <= search; dx += 2) for (int dz = -search; dz <= search; dz += 2) {
            int x = x0 + dx, z = z0 + dz;
            double rift = terrain.riftEdge(x, z);
            Column c = terrain.column(x, z);
            if (!c.land() || c.wet() || rift < 9 || rift > 14) continue;
            double score = Math.hypot(dx, dz);
            if (score < bestScore) { bestScore = score; best = new int[] {x, z}; }
        }
        return best;
    }

    /** One landmark's canvas, spots and terrain, centred on its site. */
    static final class Site {
        final ReachTerrain terrain;
        final ReachCanvas canvas = new ReachCanvas();
        final List<ReachFeatures.Spot> spots = new ArrayList<>();
        final String id;
        final int x, z, ground;
        final SplittableRandom random;

        Site(ReachTerrain terrain, String id, int x, int z) {
            this.terrain = terrain;
            this.id = id;
            this.x = x;
            this.z = z;
            this.ground = terrain.column(x, z).top() + 1;
            this.random = new SplittableRandom(id.hashCode() * 31L + x * 7L + z);
        }

        int top(int bx, int bz) { return terrain.column(bx, bz).top(); }

        void set(int bx, int by, int bz, Material m) { canvas.set(bx, by, bz, m); }

        void set(int bx, int by, int bz, Material m, String state) { canvas.set(bx, by, bz, m, state); }

        /** Stone down from just under {@code y} to the terrain, so nothing built here floats. */
        void footing(int bx, int y, int bz, Material m) {
            for (int yy = top(bx, bz) + 1; yy < y; yy++) set(bx, yy, bz, m);
        }

        void spot(String spot, int bx, int by, int bz, float yaw) { spots.add(new ReachFeatures.Spot(spot, bx + 0.5, by, bz + 0.5, yaw)); }

        /** A spot standing on whatever is really there at a column: the terrain, or the build's floor over it. */
        void groundSpot(String spot, int bx, int bz, float yaw) {
            int y = top(bx, bz) + 1;
            while (y > top(bx, bz) - 6 && air(bx, y - 1, bz)) y--;
            while (!air(bx, y, bz) || !air(bx, y + 1, bz)) y++;
            spot(spot, bx, y, bz, yaw);
        }

        /** Air as the generator will leave it: the build's own air, or open sky above the terrain. */
        boolean air(int bx, int by, int bz) {
            ReachCanvas.Spec spec = canvas.get(bx, by, bz);
            if (spec != null) return spec.state().equals("minecraft:air");
            return by > top(bx, bz);
        }

        /** A world chest of a tier, standing on whatever is below. */
        void chest(int tier, String name, int bx, int by, int bz, String facing) {
            set(bx, by, bz, Material.CHEST, "facing=" + facing + ",type=single");
            spot("chest_tier" + tier + "_" + name, bx, by, bz, 0F);
        }
    }

    /** How an expedition's door is dressed: its frame and trim, the sill and approach, the braziers' fire, banners and signs. */
    record DoorStyle(Material frame, Material accent, Material floor, Material fire, Material banner, Material sign) { }

    static final DoorStyle BARROW_DOOR = new DoorStyle(Material.MOSSY_STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.STONE_BRICKS,
        Material.SOUL_CAMPFIRE, Material.BLACK_WALL_BANNER, Material.SPRUCE_WALL_SIGN);
    static final DoorStyle THORN_DOOR = new DoorStyle(Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_WOOD, Material.ROOTED_DIRT,
        Material.SOUL_CAMPFIRE, Material.GREEN_WALL_BANNER, Material.DARK_OAK_WALL_SIGN);
    static final DoorStyle CISTERN_DOOR = new DoorStyle(Material.CALCITE, Material.CHISELED_QUARTZ_BLOCK, Material.POLISHED_DIORITE,
        Material.CAMPFIRE, Material.CYAN_WALL_BANNER, Material.BIRCH_WALL_SIGN);
    static final DoorStyle MINE_DOOR = new DoorStyle(Material.DEEPSLATE_TILES, Material.STRIPPED_DARK_OAK_WOOD, Material.DARK_OAK_PLANKS,
        Material.CAMPFIRE, Material.ORANGE_WALL_BANNER, Material.DARK_OAK_WALL_SIGN);
    static final DoorStyle EMBER_DOOR = new DoorStyle(Material.POLISHED_BLACKSTONE_BRICKS, Material.GILDED_BLACKSTONE, Material.POLISHED_BLACKSTONE,
        Material.CAMPFIRE, Material.RED_WALL_BANNER, Material.CRIMSON_WALL_SIGN);
    static final DoorStyle SEAL_DOOR = new DoorStyle(Material.CHISELED_QUARTZ_BLOCK, Material.GOLD_BLOCK, Material.QUARTZ_BRICKS,
        Material.SOUL_CAMPFIRE, Material.YELLOW_WALL_BANNER, Material.BIRCH_WALL_SIGN);

    /**
     * An expedition's door: an arched opening three wide and four tall, filled with the starlit dark of a way between
     * places, in a frame two blocks deep with a keystone and a cornice, banners hung either side, and braziers burning
     * on pedestals before it, each pedestal lettered with the expedition's name and level. It opens toward
     * {@code (fx, fz)}; stepping into it opens the expedition's board.
     */
    static void expeditionDoor(Site s, int cx, int y, int cz, int fx, int fz, DoorStyle st, String expedition, String[] name, String[] level) {
        int px = -fz, pz = fx, halfW = 1, height = 4;
        for (int k = -halfW - 1; k <= halfW + 1; k++) for (int dy = -1; dy <= height + 1; dy++) {
            int x = cx + px * k, z = cz + pz * k;
            boolean inside = Math.abs(k) <= halfW && dy >= 0 && dy < height - (Math.abs(k) == halfW ? 1 : 0);
            boolean sill = dy == -1 && Math.abs(k) <= halfW;
            s.set(x, y + dy, z, inside ? Material.END_GATEWAY : sill ? st.floor() : dy == height + 1 && k == 0 ? st.accent() : st.frame());
            // The frame stands one proud, its pillars and cornice in the trim, so the door reads from the side too.
            s.set(x + fx, y + dy, z + fz, inside ? Material.AIR : sill ? st.floor()
                : Math.abs(k) == halfW + 1 || dy == height + 1 ? st.accent() : st.frame());
        }
        for (int k = -halfW; k <= halfW; k++) s.set(cx + px * k + fx * 2, y - 1, cz + pz * k + fz * 2, st.floor());
        String facing = facing(fx, fz);
        if (st.banner() != null) for (int side : new int[] {-1, 1})
            s.set(cx + px * (halfW + 1) * side + fx * 2, y + height - 1, cz + pz * (halfW + 1) * side + fz * 2, st.banner(), "facing=" + facing);
        doorBraziers(s, cx, y, cz, fx, fz, halfW + 2, 3, st, name, level);
        s.spot("door_" + expedition, cx + fx * 3, y, cz + fz * 3, yawToward(-fx, -fz));
    }

    /**
     * Two braziers on pedestals before a door, {@code lateral} either side of its centre and {@code forward} out from
     * it, each pedestal standing on whatever is below and carrying a glowing sign on its face: the name on the left as
     * you walk up, the level on the right.
     */
    static void doorBraziers(Site s, int cx, int y, int cz, int fx, int fz, int lateral, int forward, DoorStyle st, String[] name, String[] level) {
        int px = -fz, pz = fx;
        String facing = facing(fx, fz);
        for (int side : new int[] {-1, 1}) {
            int bx = cx + px * lateral * side + fx * forward, bz = cz + pz * lateral * side + fz * forward;
            int base = y;
            while (base > y - 8 && s.air(bx, base - 1, bz)) base--;
            for (int yy = base; yy <= y; yy++) s.set(bx, yy, bz, st.frame());
            s.set(bx, y + 1, bz, st.accent());
            s.set(bx, y + 2, bz, st.fire(), "facing=" + facing + ",lit=true,signal_fire=false");
            s.set(bx + fx, y + 1, bz + fz, st.sign(), "facing=" + facing);
            // Facing the door, the side at minus the perpendicular is on the right hand.
            s.canvas.text(bx + fx, y + 1, bz + fz, side > 0 ? name : level);
        }
    }

    static String facing(int fx, int fz) { return fx > 0 ? "east" : fx < 0 ? "west" : fz > 0 ? "south" : "north"; }

    /** The yaw that looks along {@code (dx, dz)}: south is 0, west 90, north 180, east -90. */
    static float yawToward(int dx, int dz) { return dz > 0 ? 0F : dz < 0 ? 180F : dx < 0 ? 90F : -90F; }

    private static Material weathered(Site s, int x, int y, int z, Material stone, Material mossy, Material cracked) {
        double c = s.terrain.patch(x + y * 0.7, z - y * 0.5, 0.25);
        return c > 0.4 ? mossy : c < -0.5 ? cracked : stone;
    }

    // ------------------------------------------------------------------ the Barrowdowns

    /**
     * The Huntsman's Barrow: a long grassy mound with a kerb of grey boulders at its foot and a ring of standing
     * stones on its crown, every stone standing on the mound itself. On its south side a crescent of tall stones
     * frames the forecourt and the passage mouth under a great capstone, where the Hollow Barrow's door stands. An
     * avenue of paired stones leads to it from the road, the mourners' lights along its last stretch, and two small
     * round barrows keep it company.
     */
    private static void barrow(Site s) {
        int cx = s.x, cz = s.z, g = s.ground;
        double rx = 22, rz = 15, height = 10;
        int dz0 = cz + (int) rz;
        // The long mound, its top rolling a little, built up from the ground under it.
        for (int dx = (int) -rx - 1; dx <= rx + 1; dx++) for (int dz = (int) -rz - 1; dz <= rz + 1; dz++) {
            int x = cx + dx, z = cz + dz;
            int m = moundTop(s, cx, cz, g, rx, rz, height, x, z);
            if (m == Integer.MIN_VALUE) continue;
            int t = s.top(x, z);
            for (int y = t + 1; y <= m; y++) s.set(x, y, z, y == m ? (s.terrain.grain(x, z, 0.4) > 0.6 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK)
                : y > m - 3 ? Material.DIRT : Material.STONE);
        }
        // The kerb: boulders round the foot, seated where the mound meets the ground, leaving the forecourt open.
        for (int k = 0; k < 64; k++) {
            double a = k * Math.PI * 2 / 64;
            int x = cx + (int) Math.round(Math.cos(a) * rx), z = cz + (int) Math.round(Math.sin(a) * rz);
            if (Math.abs(x - cx) <= 9 && z > cz) continue;
            if (Math.floorMod(k * 5 + 3, 4) == 0) continue;
            int base = surface(s, cx, cz, g, rx, rz, height, x, z) + 1;
            s.set(x, base, z, weathered(s, x, base, z, Material.ANDESITE, Material.MOSSY_COBBLESTONE, Material.COBBLESTONE));
            if (k % 3 == 0) s.set(x, base + 1, z, Material.MOSSY_COBBLESTONE_WALL, "up=true");
        }
        // The crown: nine stones in a ring, each on the mound's own surface where it stands, and one fallen.
        int crownTop = surface(s, cx, cz, g, rx, rz, height, cx, cz);
        for (int k = 0; k < 9; k++) {
            double a = k * Math.PI * 2 / 9 + 0.2;
            int x = cx + (int) Math.round(Math.cos(a) * 7), z = cz + (int) Math.round(Math.sin(a) * 5);
            if (k == 4) {
                for (int i = 0; i < 3; i++) s.set(x + i, surface(s, cx, cz, g, rx, rz, height, x + i, z) + 1, z, Material.ANDESITE_SLAB, "type=bottom");
                continue;
            }
            int h = 3 + (k % 3 == 0 ? 1 : 0);
            // Each stone two wide along the ring, the second shoulder a block lower: a slab of rock, not a post.
            int tx = (int) Math.round(-Math.sin(a)), tz = (int) Math.round(Math.cos(a));
            standingStone(s, x, z, surface(s, cx, cz, g, rx, rz, height, x, z) + 1, h);
            standingStone(s, x + tx, z + tz, surface(s, cx, cz, g, rx, rz, height, x + tx, z + tz) + 1, h - 1);
        }
        s.set(cx, crownTop + 1, cz, Material.CHISELED_STONE_BRICKS);
        s.set(cx, crownTop + 2, cz, Material.CANDLE, "candles=3,lit=true");
        // The forecourt before the passage: levelled earth in a half round.
        for (int dx = -12; dx <= 12; dx++) for (int dz = 0; dz <= 12; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 12) continue;
            int x = cx + dx, z = dz0 + dz;
            if (moundTop(s, cx, cz, g, rx, rz, height, x, z) != Integer.MIN_VALUE) continue;
            Column c = s.terrain.column(x, z);
            if (!c.land() || c.wet()) continue;
            int t = c.top();
            double ease = d <= 9 ? 1 : 1 - (d - 9) / 3.0;
            int target = (int) Math.round(t + (g - 1 - t) * ease);
            for (int y = t + 1; y < target; y++) s.set(x, y, z, Material.DIRT);
            for (int y = target + 1; y <= t; y++) s.set(x, y, z, Material.AIR);
            double p = s.terrain.patch(x * 0.7, z * 0.7, 0.2);
            s.set(x, target, z, d > 9 ? Material.GRASS_BLOCK : p > 0.35 ? Material.GRAVEL : p < -0.3 ? Material.PACKED_MUD : Material.COARSE_DIRT);
        }
        // The passage cut into the mound, stone-lined and roofed.
        for (int k = -2; k <= 6; k++) for (int x = cx - 2; x <= cx + 2; x++) {
            int z = dz0 - k;
            s.set(x, g - 1, z, Material.COBBLESTONE);
            for (int y = g; y <= g + 4; y++) {
                boolean wall = Math.abs(x - cx) == 2 || y == g + 4;
                if (k <= -1 && !wall) s.set(x, y, z, Material.AIR);
                else if (k >= 0 && k <= 5) s.set(x, y, z, wall ? weathered(s, x, y, z, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS)
                    : k == 5 ? Material.MOSSY_STONE_BRICKS : Material.AIR);
            }
        }
        expeditionDoor(s, cx, g, dz0 - 1, 0, 1, BARROW_DOOR, "hollow_barrow",
            new String[] {"EXPEDITION", "The Hollow", "Barrow", ""}, new String[] {"Level 8", "", "Step through", "to set out"});
        // The capstone: one great flat stone over the passage mouth, resting on the door's frame and two side stones.
        for (int x = cx - 3; x <= cx + 3; x++) for (int z = dz0 - 1; z <= dz0 + 1; z++)
            s.set(x, g + 6, z, Math.abs(x - cx) == 3 || z == dz0 + 1 ? Material.POLISHED_ANDESITE : Material.ANDESITE);
        for (int z = dz0 - 1; z <= dz0 + 1; z++) for (int x : new int[] {cx - 3, cx + 3}) for (int y = g; y < g + 6; y++)
            if (s.air(x, y, z)) s.set(x, y, z, weathered(s, x, y, z, Material.ANDESITE, Material.MOSSY_STONE_BRICKS, Material.TUFF));
        // The crescent: tall stones either side of the forecourt, tallest beside the door, standing on its floor.
        for (int deg : new int[] {20, 38, 56, 124, 142, 160}) {
            double a = Math.toRadians(deg);
            int x = cx + (int) Math.round(Math.cos(a) * 8), z = dz0 - 2 + (int) Math.round(Math.sin(a) * 8);
            int h = deg == 56 || deg == 124 ? 6 : deg == 38 || deg == 142 ? 5 : 4;
            int base = groundAt(s, x, z);
            standingStone(s, x, z, base, h);
            int ox = x + (x > cx ? 1 : -1);
            standingStone(s, ox, z, groundAt(s, ox, z), h - 2);
        }
        // An offering stone in the forecourt: a low slab with candles and the bones of the hunt.
        int ox = cx + 5, oz = dz0 + 6;
        s.set(ox, g, oz, Material.POLISHED_ANDESITE);
        s.set(ox + 1, g, oz, Material.ANDESITE_SLAB, "type=bottom");
        s.set(ox, g + 1, oz, Material.CANDLE, "candles=2,lit=true");
        s.set(ox + 1, g, oz + 1, Material.BONE_BLOCK, "axis=x");
        avenue(s, cx, dz0 + 10, g);
        roundBarrow(s, cx - 36, cz + 6, 5, 3);
        roundBarrow(s, cx + 35, cz - 3, 4, 3);
        s.spots.add(ReachRoost.build(s.canvas, s.terrain, cx - 10, dz0 + 9, 0, 1, "Huntsman's Barrow"));
        s.chest(2, "barrow", cx + 2, crownTop + 1, cz + 1, "south");
        s.spot("barrow_door", cx, g, dz0 + 2, 0F);
        s.groundSpot("place", cx, dz0 + 5, 0F);
    }

    /** The first open block over whatever is really at a column: the build's floor where it laid one, else the ground. */
    private static int groundAt(Site s, int x, int z) {
        int y = s.top(x, z) + 1;
        while (y > s.top(x, z) - 6 && s.air(x, y - 1, z)) y--;
        while (!s.air(x, y, z)) y++;
        return y;
    }

    /** The mound's top at a column, or MIN_VALUE off the mound: a long rounded back, rolling a little. */
    private static int moundTop(Site s, int cx, int cz, int g, double rx, double rz, double height, int x, int z) {
        double d = Math.hypot((x - cx) / rx, (z - cz) / rz);
        if (d >= 1) return Integer.MIN_VALUE;
        double h = height * Math.pow(1 - d * d, 0.7) + 0.9 * s.terrain.patch(x * 0.7, z * 0.7, 0.2) * (1 - d);
        return (int) Math.round(g - 1 + Math.max(0, h));
    }

    /** What a stone stands on at a column: the mound where it is, else the ground. */
    private static int surface(Site s, int cx, int cz, int g, double rx, double rz, double height, int x, int z) {
        return Math.max(moundTop(s, cx, cz, g, rx, rz, height, x, z), s.top(x, z));
    }

    /** A standing stone {@code h} tall from {@code base}, weathered, its head a block of lichened rock. */
    private static void standingStone(Site s, int x, int z, int base, int h) {
        for (int y = base; y < base + h; y++)
            s.set(x, y, z, y == base + h - 1 ? Material.MOSSY_COBBLESTONE : weathered(s, x, y, z, Material.ANDESITE, Material.TUFF, Material.COBBLESTONE));
    }

    /**
     * The avenue to the barrow: from the forecourt to the nearest road a worn track three wide, following the ground,
     * with a pair of standing stones every eight blocks and the mourners' lights on the last stretch before the
     * door: a lantern nearest, then lit candles, then one cold candle further out.
     */
    private static void avenue(Site s, int fx, int fz, int g) {
        double bestD = Double.MAX_VALUE, rxp = fx, rzp = fz + 40;
        for (ReachPath road : s.terrain.roads()) for (int i = 0; i < road.xs.length; i++) {
            double d = Math.hypot(road.xs[i] - fx, road.zs[i] - fz);
            if (d < bestD) { bestD = d; rxp = road.xs[i]; rzp = road.zs[i]; }
        }
        double len = Math.hypot(rxp - fx, rzp - fz);
        if (len < 2 || len > 120) return;
        double ux = (rxp - fx) / len, uz = (rzp - fz) / len, px = -uz, pz = ux;
        double sway = s.terrain.patch(fx, fz, 0.1) > 0 ? 1 : -1;
        int[] bank = {g - 1};
        int lastStone = -100;
        for (double t = 0; t <= len + 1; t += 0.5) {
            double bend = 3 * Math.sin(t / len * Math.PI) * sway;
            double ax = fx + ux * t + px * bend, az = fz + uz * t + pz * bend;
            boolean onRoad = s.terrain.column((int) Math.floor(ax), (int) Math.floor(az)).carriageway();
            for (double w = -1.5; w <= 1.5; w += 0.5) {
                int x = (int) Math.floor(ax + px * w), z = (int) Math.floor(az + pz * w);
                Column c = s.terrain.column(x, z);
                // Over water, a plank footbridge level with the bank behind it, railed on both sides.
                if (c.land() && c.wet()) {
                    int deck = Math.max(c.water() + 1, bank[0]);
                    if (!s.canvas.has(x, deck, z)) s.set(x, deck, z, Math.abs(w) > 1 ? Material.STRIPPED_SPRUCE_LOG : Material.SPRUCE_PLANKS,
                        Math.abs(w) > 1 ? (Math.abs(ux) > Math.abs(uz) ? "axis=x" : "axis=z") : "");
                    if (Math.abs(w) > 1.2) s.set(x, deck + 1, z, Material.SPRUCE_FENCE);
                    if (Math.floorMod((int) Math.floor(t), 4) == 0 && Math.abs(w) > 1.2)
                        for (int y = c.top() + 1; y < deck; y++) s.set(x, y, z, Material.STRIPPED_SPRUCE_LOG, "axis=y");
                    continue;
                }
                if (!c.land() || c.carriageway() || s.canvas.has(x, c.top(), z)) continue;
                if (Math.abs(w) < 0.6) bank[0] = c.top();
                double p = s.terrain.patch(x * 0.8, z * 0.8, 0.2);
                s.set(x, c.top(), z, Math.abs(w) > 1 ? Material.COARSE_DIRT : p > 0.4 ? Material.GRAVEL : Material.DIRT_PATH);
            }
            if (onRoad) break;
            int step = (int) Math.floor(t);
            if (t == step && step - lastStone >= 8 && step >= 12) {
                for (int side : new int[] {-1, 1}) {
                    int x = (int) Math.floor(ax + px * 3.5 * side), z = (int) Math.floor(az + pz * 3.5 * side);
                    Column c = s.terrain.column(x, z);
                    if (!c.land() || c.wet() || c.across() < 4.5) continue;
                    standingStone(s, x, z, c.top() + 1, 2 + Math.floorMod(x * 3 + z, 2));
                }
                lastStone = step;
            }
            // The mourners' lane on the last stretch, narrowing toward the door.
            for (int lane = 0; lane < 3; lane++) {
                if (t != 1 + lane * 3) continue;
                for (int side : new int[] {-1, 1}) {
                    int x = (int) Math.floor(ax + px * (2.5 + lane) * side), z = (int) Math.floor(az + pz * (2.5 + lane) * side);
                    int y = groundAt(s, x, z);
                    s.set(x, y, z, Material.MOSSY_STONE_BRICKS);
                    s.set(x, y + 1, z, Material.ANDESITE_WALL, "up=true");
                    if (lane == 0) s.set(x, y + 2, z, Material.LANTERN, "hanging=false");
                    else s.set(x, y + 2, z, Material.CANDLE, lane == 1 ? "candles=3,lit=true" : "candles=1,lit=false");
                }
            }
        }
    }

    /** A small round barrow beside the long one: a low grassy dome with one stone on its top. */
    private static void roundBarrow(Site s, int bx, int bz, int r, int h) {
        Column centre = s.terrain.column(bx, bz);
        if (!centre.land() || centre.wet() || centre.across() < r + 8) return;
        int base = centre.top();
        int top = base;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            double d = Math.hypot(dx, dz) / r;
            if (d >= 1) continue;
            int x = bx + dx, z = bz + dz, t = s.top(x, z);
            int m = base + (int) Math.round(h * Math.sqrt(1 - d * d));
            for (int y = t + 1; y <= m; y++) s.set(x, y, z, y == m ? Material.GRASS_BLOCK : Material.DIRT);
            if (dx == 0 && dz == 0) top = Math.max(m, t);
        }
        standingStone(s, bx, bz, top + 1, 2);
    }

    /** The Stone Rings: an old circle of standing stones on the open down, a flat altar stone at its heart. */
    private static void stoneRing(Site s) {
        int cx = s.x, cz = s.z;
        for (int k = 0; k < 11; k++) {
            double a = k * Math.PI * 2 / 11 + s.random.nextDouble() * 0.1;
            int x = cx + (int) Math.round(Math.cos(a) * 9), z = cz + (int) Math.round(Math.sin(a) * 9);
            int base = s.top(x, z) + 1, h = k % 4 == 3 ? 1 : 3 + s.random.nextInt(2);
            for (int y = 0; y < h; y++) {
                s.set(x, base + y, z, weathered(s, x, base + y, z, Material.ANDESITE, Material.MOSSY_COBBLESTONE, Material.COBBLESTONE));
                if (h >= 4 && y < 2) s.set(x + (k % 2 == 0 ? 1 : 0), base + y, z + (k % 2 == 0 ? 0 : 1), Material.ANDESITE);
            }
            if (h == 1) s.set(x + 1, base, z, Material.ANDESITE_SLAB, "type=bottom");
        }
        // Two pairs carry lintels, like a gate on the side toward the road.
        int b = s.top(cx, cz) + 1;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) s.set(cx + dx, b, cz + dz, Material.POLISHED_ANDESITE_SLAB, "type=bottom");
        s.set(cx, b, cz, Material.POLISHED_ANDESITE);
        s.set(cx, b + 1, cz, Material.CANDLE, "candles=2,lit=true");
        for (int dx = -1; dx <= 1; dx += 2) s.set(cx + dx, b + 1, cz, Material.ALLIUM);
        s.chest(2, "rings", cx, s.top(cx, cz + 3) + 1, cz + 3, "north");
        s.groundSpot("place", cx, cz - 3, 0F);
    }

    /** Stonewatch: a ruined round watchtower over the way to Thornwood, its top broken, a ladder to the old floor. */
    private static void watchtower(Site s) {
        int cx = s.x, cz = s.z, g = s.ground;
        double r = 4.5;
        int height = 22;
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            double d = Math.hypot(dx, dz);
            int x = cx + dx, z = cz + dz;
            if (d > r + 0.5) continue;
            s.footing(x, g - 1, z, Material.COBBLESTONE);
            s.set(x, g - 1, z, Material.COBBLESTONE);
            boolean wall = d > r - 1;
            // The broken top: each column of wall ends at its own height, lower on the windward side.
            double angle = Math.atan2(dz, dx);
            int top = g + height - (int) Math.round(7 * (0.5 + 0.5 * Math.sin(angle * 2 + 0.7)) + 3 * (0.5 + 0.5 * Math.cos(angle * 5)));
            for (int y = g; y <= (wall ? top : g + height); y++) {
                if (!wall) { s.set(x, y, z, y == g + 11 && d < r - 1.5 && dx > -2 ? Material.SPRUCE_PLANKS : Material.AIR); continue; }
                boolean slit = (y - g) % 6 == 3 && (Math.abs(dx) <= 0 || Math.abs(dz) <= 0);
                s.set(x, y, z, slit ? Material.AIR : weathered(s, x, y, z, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS));
            }
        }
        // The door, rubble round the foot, the ladder up to the surviving half of the floor.
        int doorZ = cz - (int) Math.round(r);
        for (int y = g; y <= g + 2; y++) for (int z = doorZ; z <= doorZ + 2; z++) s.set(cx, y, z, Material.AIR);
        for (int y = g; y <= g + 11; y++) s.set(cx, y, cz + 3, Material.LADDER, "facing=north");
        for (int k = 0; k < 14; k++) {
            int x = cx + s.random.nextInt(15) - 7, z = cz + s.random.nextInt(15) - 7;
            if (Math.hypot(x - cx, z - cz) < r + 1) continue;
            int t = s.top(x, z) + 1;
            s.set(x, t, z, k % 3 == 0 ? Material.MOSSY_COBBLESTONE : k % 3 == 1 ? Material.COBBLESTONE_SLAB : Material.MOSSY_STONE_BRICKS, k % 3 == 1 ? "type=bottom" : "");
        }
        s.chest(3, "stonewatch", cx + 1, g + 12, cz + 1, "west");
        s.set(cx - 1, g + 12, cz + 1, Material.LANTERN);
        s.groundSpot("place", cx, doorZ - 2, 180F);
    }

    // ------------------------------------------------------------------ Thornwood

    /**
     * The Heart Tree: a colossal tree on its hill, a trunk seven across on buttress roots, great limbs carrying
     * canopies that stand over the whole wood, black thorn spiralling up the bark, and the Canopy's door, a hollow
     * between two roots at its foot.
     */
    private static void heartTree(Site s) {
        int cx = s.x, cz = s.z, g = s.ground;
        int h = 52;
        // Buttress roots running out and down into the ground.
        for (int k = 0; k < 9; k++) {
            double a = k * Math.PI * 2 / 9 + 0.2 * Math.sin(k * 3.1);
            for (int i = 0; i < 9 + k % 3 * 2; i++) {
                int x = cx + (int) Math.round(Math.cos(a) * (3 + i)), z = cz + (int) Math.round(Math.sin(a) * (3 + i));
                int up = Math.max(0, 6 - i);
                for (int y = s.top(x, z) - 1; y <= g + up - 1; y++) s.set(x, y, z, Material.DARK_OAK_WOOD, "axis=y");
            }
        }
        // The trunk: a disc that narrows as it climbs, with thorn spiralling round it.
        for (int y = 0; y < h; y++) {
            double r = 3.6 - y * 0.035;
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r) continue;
                s.set(cx + dx, g + y, cz + dz, d > r - 1.1 ? Material.DARK_OAK_WOOD : Material.DARK_OAK_LOG, "axis=y");
            }
            double a = y * 0.42;
            // The thorn grows out of the bark, so it sits on the trunk's skin, not a block off it.
            int tx = cx + (int) Math.round(Math.cos(a) * (r + 0.4)), tz = cz + (int) Math.round(Math.sin(a) * (r + 0.4));
            if (y > 2 && y < h - 6) s.set(tx, g + y, tz, Material.BLACKSTONE_WALL, "up=true");
            if (y % 7 == 3) s.set(tx, g + y + 1, tz, Material.POINTED_DRIPSTONE, "vertical_direction=up,thickness=tip");
        }
        // The door: a hollow between roots facing south, the Canopy's way in.
        for (int y = 0; y <= 4; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = 0; dz <= 3; dz++) {
            if (y == 4 && dx != 0) continue;
            s.set(cx + dx, g + y, cz + 4 - dz, Material.AIR);
        }
        for (int dx = -1; dx <= 1; dx++) s.set(cx + dx, g - 1, cz + 4, Material.PODZOL);
        expeditionDoor(s, cx, g, cz + 1, 0, 1, THORN_DOOR, "thornwood_canopy",
            new String[] {"EXPEDITION", "Thornwood", "Canopy", ""}, new String[] {"Level 12", "", "Step through", "to set out"});
        // Great limbs, each ending in a canopy; vines and glow berries hanging under them.
        SplittableRandom r = s.random;
        for (int k = 0; k < 7; k++) {
            double a = k * Math.PI * 2 / 7 + r.nextDouble() * 0.4;
            int from = g + h - 22 + r.nextInt(12);
            int len = 13 + r.nextInt(6);
            int ex = cx, ey = from, ez = cz;
            for (int i = 0; i <= len; i++) {
                ex = cx + (int) Math.round(Math.cos(a) * (2 + i)); ey = from + (int) Math.round(i * 0.55); ez = cz + (int) Math.round(Math.sin(a) * (2 + i));
                for (int t = 0; t <= (i < len / 2 ? 1 : 0); t++) {
                    s.set(ex, ey - t, ez, Material.DARK_OAK_WOOD, "axis=y");
                    s.set(ex + 1, ey - t, ez, Material.DARK_OAK_WOOD, "axis=y");
                }
            }
            canopy(s, ex, ey + 2, ez, 7 + r.nextInt(2), 4, r);
        }
        canopy(s, cx, g + h + 2, cz, 10, 6, r);
        // A chest in a hollow of the highest limb's fork, for those who climb the roots and vines.
        for (int y = g; y <= g + 6; y++) s.set(cx + 4, y, cz, Material.DARK_OAK_WOOD, "axis=y");
        s.chest(3, "heart_tree", cx + 4, g + 7, cz, "east");
        for (int y = g; y <= g + 6; y++) s.set(cx + 5, y, cz, Material.VINE, "west=true");
        s.spots.add(ReachRoost.build(s.canvas, s.terrain, cx + 7, cz + 11, 0, 1, "The Heart Tree"));
        s.groundSpot("place", cx, cz + 9, 180F);
    }

    /** A broad flat canopy: two greens in patches, thinning at the rim, vines and glow berries hanging below. */
    private static void canopy(Site s, int cx, int cy, int cz, int rad, int depth, SplittableRandom r) {
        double p = r.nextDouble() * 6;
        for (int dx = -rad - 1; dx <= rad + 1; dx++) for (int dz = -rad - 1; dz <= rad + 1; dz++) for (int dy = -depth; dy <= depth; dy++) {
            double lump = 0.12 * Math.sin(dx * 0.5 + p) * Math.cos(dz * 0.45 - p);
            double d = Math.pow(dx / (double) rad, 2) + Math.pow(dz / (double) rad, 2) + Math.pow(dy / (dy < 0 ? depth * 0.6 : depth), 2);
            if (d > 1 + lump) continue;
            int x = cx + dx, y = cy + dy, z = cz + dz;
            if (s.canvas.has(x, y, z)) continue;
            boolean dark = Math.floorMod((x >> 2) * 7 + (z >> 2) * 3 + (y >> 1), 5) < 2;
            s.set(x, y, z, dark ? Material.DARK_OAK_LEAVES : Material.OAK_LEAVES, "persistent=true");
        }
        for (int k = 0; k < rad * 2; k++) {
            int vx = cx + r.nextInt(rad * 2 + 1) - rad, vz = cz + r.nextInt(rad * 2 + 1) - rad;
            int top = cy - (int) (depth * 0.6) - 1;
            if (!s.canvas.has(vx, top + 1, vz)) continue;
            int len = 2 + r.nextInt(6);
            for (int i = 0; i < len; i++) {
                if (s.canvas.has(vx, top - i, vz)) break;
                boolean last = i == len - 1;
                s.set(vx, top - i, vz, last && k % 3 == 0 ? Material.CAVE_VINES : Material.CAVE_VINES_PLANT, last && k % 3 == 0 ? "berries=true" : "berries=" + (k % 4 == 0));
            }
        }
    }

    // ------------------------------------------------------------------ Mistmere

    /**
     * The Great Aqueduct: a line of tall round arches in pale stone carrying the water channel east from the lake
     * toward the Ironspine. The channel runs level; where the ground falls away, a second tier of arches stands
     * under the first. Copper sluice gates sit over every fifth pier, and a stair climbs a pier to the channel walk.
     * South of the arches a sunken court holds the door to the Sunken Aqueduct.
     */
    private static void aqueduct(Site s) {
        int x0 = s.x - 96, x1 = s.x + 96;
        double slope = 0.12;
        // The channel runs level at the height most of its line stands, plus the arches; where a hill rises above
        // that, the channel cuts through it in a trench, as a real aqueduct would.
        java.util.List<Integer> tops = new java.util.ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            Column c = s.terrain.column(x, s.z + (int) Math.round((x - s.x) * slope));
            if (c.land() && !c.wet()) tops.add(c.top());
        }
        java.util.Collections.sort(tops);
        int channel = (tops.isEmpty() ? s.ground : tops.get((int) (tops.size() * 0.7))) + 8;
        int pier = 0;
        for (int x = x0; x <= x1; x++) {
            int z = s.z + (int) Math.round((x - s.x) * slope);
            Column c = s.terrain.column(x, z);
            // Over open sky or water the deck runs on without piers.
            int t = c.land() && !c.wet() ? c.top() : channel - 3;
            int phase = Math.floorMod(x - x0, 9);
            boolean isPier = phase <= 1 && c.land();
            double arch = Math.sqrt(Math.max(0, 12.25 - Math.pow(phase - 5, 2)));
            boolean twoTier = channel - t > 18;
            int lowerTop = twoTier ? t + (channel - t) / 2 : t;
            for (int dz = -2; dz <= 2; dz++) {
                int zz = z + dz;
                boolean face = Math.abs(dz) == 2;
                int base = c.land() && !c.wet() ? s.top(x, zz) : t;
                // Into a hill: the channel runs on in a tunnel under the hillside, which is left whole above it.
                if (base > channel + 1 && Math.abs(dz) <= 1) for (int y = channel + 1; y <= Math.min(base, channel + 3); y++) s.set(x, y, zz, Material.AIR);
                for (int y = Math.min(base + 1, channel - 1); y <= channel + 1; y++) {
                    // Round arches between the piers: the upper tier's spring six under the channel, the lower's
                    // five under its own deck, each crown the arch's height above its spring.
                    boolean open = !isPier && (twoTier && y < lowerTop ? y < lowerTop - 5 + arch : y > (twoTier ? lowerTop : base) && y < channel - 6 + arch);
                    if (open || !c.land() && y < channel - 2) continue;
                    Material m = y == channel + 1 ? (face ? Material.SMOOTH_STONE_SLAB : Material.AIR)
                        : y == channel ? (face ? Material.CALCITE : Material.WATER)
                        : y == channel - 1 || y == lowerTop && twoTier ? Material.POLISHED_DIORITE
                        : isPier && (y - t) % 8 == 0 ? Material.SMOOTH_STONE : (Math.floorMod(x + y, 7) == 0 ? Material.DIORITE : Material.CALCITE);
                    s.set(x, y, zz, m, m == Material.SMOOTH_STONE_SLAB ? "type=bottom" : m == Material.WATER ? "level=0" : "");
                }
            }
            if (phase == 0) {
                pier++;
                if (pier % 5 == 0) {
                    // A copper sluice gate across the channel.
                    for (int dz = -1; dz <= 1; dz++) s.set(x, channel + 1, z + dz, Material.WAXED_COPPER_GRATE);
                    for (int dz : new int[] {-2, 2}) { s.set(x, channel + 2, z + dz, Material.WAXED_CUT_COPPER); s.set(x, channel + 3, z + dz, Material.LIGHTNING_ROD, "facing=up"); }
                }
            }
        }
        // The ends of the channel are closed so its water stays put.
        for (int x : new int[] {x0, x1}) {
            int z = s.z + (int) Math.round((x - s.x) * slope);
            for (int dz = -2; dz <= 2; dz++) s.set(x, channel, z + dz, Material.CALCITE);
        }
        // A ladder up the face of the pier nearest the middle, to the walk along the channel's lip.
        int sx = s.x - Math.floorMod(s.x - x0, 9), lz = s.z + (int) Math.round((sx - s.x) * slope), sz = lz + 3;
        for (int y = s.top(sx, sz) + 1; y <= channel + 1; y++) s.set(sx, y, sz, Material.LADDER, "facing=south");
        s.chest(3, "aqueduct", sx + 1, channel + 1, lz + 2, "south");
        int[] court = cisternSite(s, slope);
        cistern(s, court[0], court[1]);
        s.spots.add(ReachRoost.build(s.canvas, s.terrain, court[0] + 8, court[1] + 6, 0, 1, "Great Aqueduct"));
        s.groundSpot("place", court[0], court[1] + 7, 180F);
    }

    /**
     * Where the cistern court goes: on dry, even ground just south of the arches, as near the middle of the line as
     * that can be found, and clear of any road.
     */
    private static int[] cisternSite(Site s, double slope) {
        for (int d = 0; d <= 36; d += 3) for (int sign : new int[] {1, -1}) {
            int x = s.x + d * sign, z = s.z + (int) Math.round((x - s.x) * slope) + 10;
            if (s.terrain.roadDistance(x, z, 14) < 12) continue;
            boolean ok = true;
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int dx = -6; dx <= 6 && ok; dx++) for (int dz = -6; dz <= 8; dz++) {
                Column c = s.terrain.column(x + dx, z + dz);
                if (!c.land() || c.wet()) { ok = false; break; }
                lo = Math.min(lo, c.top());
                hi = Math.max(hi, c.top());
            }
            if (ok && hi - lo <= 3) return new int[] {x, z};
        }
        return new int[] {s.x, s.z + 10};
    }

    /**
     * The Cistern Stair: a sunken court walled in pale stone, a stair the court's full width going down into it from
     * the south, and in its north wall the door to the Sunken Aqueduct, its crest showing over the coping. Lamp posts
     * stand at the four corners so the court reads from across the marsh.
     */
    private static void cistern(Site s, int gx, int gz) {
        int gy = s.top(gx, gz) + 1, fl = gy - 4;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            int x = gx + dx, z = gz + dz, t = s.top(x, z);
            boolean wall = Math.abs(dx) == 4 || dz == -4;
            if (wall) {
                for (int y = fl - 1; y < gy; y++) s.set(x, y, z, (y - fl) % 3 == 2 ? Material.POLISHED_DIORITE : Material.CALCITE);
                s.set(x, gy, z, Material.POLISHED_DIORITE_SLAB, "type=bottom");
                for (int y = gy + 1; y <= t; y++) s.set(x, y, z, Material.AIR);
                continue;
            }
            // The stair: four steps up to the ground, the court's full width, from the floor's south edge.
            int step = dz - 1;
            int solidTo = step >= 0 ? fl + step - 1 : fl - 1;
            for (int y = fl - 1; y <= solidTo; y++) s.set(x, y, z, y == fl - 1 && step < 0 ? Material.POLISHED_DIORITE : Material.CALCITE);
            if (step >= 0) s.set(x, fl + step, z, Material.POLISHED_DIORITE_STAIRS, "facing=south,half=bottom");
            for (int y = step >= 0 ? fl + step + 1 : fl; y <= Math.max(t, gy + 1); y++) s.set(x, y, z, Material.AIR);
        }
        // The stair's landing at the top, so it meets the ground cleanly.
        for (int dx = -4; dx <= 4; dx++) {
            int x = gx + dx, z = gz + 5;
            for (int y = s.top(x, z) + 1; y < gy - 1; y++) s.set(x, y, z, Material.CALCITE);
            s.set(x, gy - 1, z, Material.POLISHED_DIORITE);
            for (int y = gy; y <= s.top(x, z); y++) s.set(x, y, z, Material.AIR);
        }
        for (int dx : new int[] {-4, 4}) for (int dz : new int[] {-4, 4}) {
            s.set(gx + dx, gy, gz + dz, Material.DIORITE_WALL, "up=true");
            s.set(gx + dx, gy + 1, gz + dz, Material.DIORITE_WALL, "up=true");
            s.set(gx + dx, gy + 2, gz + dz, Material.LANTERN, "hanging=false");
        }
        expeditionDoor(s, gx, fl, gz - 4, 0, 1, CISTERN_DOOR, "sunken_aqueduct",
            new String[] {"EXPEDITION", "The Sunken", "Aqueduct", ""}, new String[] {"Level 16", "", "Step through", "to set out"});
    }

    // ------------------------------------------------------------------ the Ironspine

    /**
     * The Deep Foreman's Headframe: a giant black timber hoist over a shaft on the highest shoulder, four raking
     * legs braced in X's, a great sheave wheel at the top, cables running down into the dark, the winding house
     * beside it and spoil heaps round the pit.
     */
    private static void headframe(Site s) {
        int cx = s.x, cz = s.z, g = s.ground;
        int h = 34;
        // The shaft: a timbered square pit going down out of sight.
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean rim = Math.abs(dx) == 3 || Math.abs(dz) == 3;
            for (int y = g - 18; y <= g + 3; y++) s.set(cx + dx, y, cz + dz, rim ? (y >= g - 1 ? Material.STRIPPED_DARK_OAK_LOG : Material.COBBLED_DEEPSLATE) : Material.AIR,
                rim && y >= g - 1 ? "axis=y" : "");
            if (rim) s.set(cx + dx, g - 1, cz + dz, Material.DARK_OAK_PLANKS);
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) s.set(cx + dx, g - 19, cz + dz, Material.DEEPSLATE);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            if (Math.abs(dx) == 3 || Math.abs(dz) == 3) for (int y = g; y <= g + 3; y++) s.set(cx + dx, y, cz + dz, Material.AIR);
        for (int dx = -3; dx <= 3; dx++) for (int dz : new int[] {-3, 3}) s.set(cx + dx, g, cz + dz, Material.DARK_OAK_FENCE);
        for (int dz = -3; dz <= 3; dz++) for (int dx : new int[] {-3, 3}) s.set(cx + dx, g, cz + dz, Material.DARK_OAK_FENCE);
        // Four raking legs from the corners up to the head, braced every six blocks.
        int[][] feet = {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}};
        for (int[] f : feet) for (int y = 0; y <= h; y++) {
            double t = y / (double) h;
            int x = cx + (int) Math.round(f[0] * (1 - t * 0.7)), z = cz + (int) Math.round(f[1] * (1 - t * 0.7));
            s.footing(x, g + y, z, Material.POLISHED_BLACKSTONE);
            s.set(x, g + y, z, Material.STRIPPED_DARK_OAK_WOOD, "axis=y");
            s.set(x + (f[0] > 0 ? -1 : 1), g + y, z, Material.DARK_OAK_WOOD, "axis=y");
        }
        for (int y = 6; y < h; y += 6) {
            double t = y / (double) h;
            int a = (int) Math.round(6 * (1 - t * 0.7));
            for (int k = -a; k <= a; k++) for (int side : new int[] {-a, a}) {
                s.set(cx + k, g + y, cz + side, Material.DARK_OAK_PLANKS);
                s.set(cx + side, g + y, cz + k, Material.DARK_OAK_PLANKS);
            }
        }
        // The head: a platform and the sheave wheel standing on it, cables down the shaft.
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) s.set(cx + dx, g + h, cz + dz, Material.DARK_OAK_PLANKS);
        for (int dx = -6; dx <= 6; dx++) for (int dy = -6; dy <= 6; dy++) {
            double d = Math.hypot(dx, dy);
            if (d > 6.4 || d < 4.9 && !(dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy))) continue;
            if (d < 1.1) { s.set(cx + dx, g + h + 7 + dy, cz, Material.IRON_BLOCK); continue; }
            s.set(cx + dx, g + h + 7 + dy, cz, d > 4.9 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DARK_OAK_FENCE);
        }
        for (int y = g + 1; y < g + h; y++) for (int dx : new int[] {-1, 1}) s.set(cx + dx, y, cz, Material.IRON_CHAIN, "axis=y");
        for (int y = g - 17; y <= g; y++) for (int dx : new int[] {-1, 1}) s.set(cx + dx, y, cz, Material.IRON_CHAIN, "axis=y");
        // The cage gate on the shaft's south rim: the way down to the Gloomforge.
        expeditionDoor(s, cx, g, cz + 3, 0, 1, MINE_DOOR, "gloomforge_mines",
            new String[] {"EXPEDITION", "Gloomforge", "Mines", ""}, new String[] {"Level 20", "", "Step through", "to set out"});
        // The winding house beside the frame: a stone shed under a green roof, the drum showing through its door.
        int wx = cx + 12, wz = cz - 3;
        for (int dx = 0; dx <= 8; dx++) for (int dz = 0; dz <= 6; dz++) {
            int x = wx + dx, z = wz + dz;
            boolean wall = dx == 0 || dx == 8 || dz == 0 || dz == 6;
            s.footing(x, g - 1, z, Material.COBBLESTONE);
            s.set(x, g - 1, z, Material.COBBLESTONE);
            for (int y = g; y <= g + 4; y++) s.set(x, y, z, wall ? weathered(s, x, y, z, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.ANDESITE) : Material.AIR);
            // A green gabled roof, its ends closed in stone.
            int k = Math.min(dz, 6 - dz);
            s.set(x, g + 5 + k, z, Material.GREEN_TERRACOTTA);
            if (dx == 0 || dx == 8) for (int y = g + 5; y < g + 5 + k; y++) s.set(x, y, z, Material.COBBLESTONE);
        }
        for (int y = g; y <= g + 2; y++) s.set(wx, y, wz + 3, Material.AIR);
        for (int dz = 2; dz <= 4; dz++) s.set(wx + 4, g + 1, wz + dz, Material.STRIPPED_SPRUCE_LOG, "axis=z");
        for (int dz : new int[] {2, 4}) s.set(wx + 4, g, wz + dz, Material.DARK_OAK_FENCE);
        s.set(wx + 2, g + 3, wz + 3, Material.LANTERN, "hanging=true");
        s.chest(3, "headframe", wx + 6, g, wz + 2, "west");
        // Spoil heaps.
        for (int[] heap : new int[][] {{-14, 6}, {-10, -12}, {8, 12}}) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 4.2) continue;
            int x = cx + heap[0] + dx, z = cz + heap[1] + dz, t = s.top(x, z);
            for (int y = t + 1; y <= t + (int) Math.round(3 * (1 - d / 4.2)); y++) s.set(x, y, z, d < 2 ? Material.TUFF : Material.GRAVEL);
        }
        s.spots.add(ReachRoost.build(s.canvas, s.terrain, cx - 8, cz - 12, 0, -1, "The Headframe"));
        s.groundSpot("place", cx, cz - 9, 0F);
    }

    // ------------------------------------------------------------------ Sunscar

    /**
     * The Sunscar Arch: a natural arch of banded rock over the canyon floor, its two feet great buttresses of strata,
     * its span thinning to a ridge you can walk, dead bushes and red sand round its feet.
     */
    private static void sunArch(Site s) {
        int cx = s.x, cz = s.z, g = s.ground;
        Material[] bands = {Material.TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.WHITE_TERRACOTTA, Material.RED_TERRACOTTA,
            Material.YELLOW_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA};
        double span = 26, rise = 26;
        for (int dx = -32; dx <= 32; dx++) for (int dz = -7; dz <= 7; dz++) for (int y = 0; y <= (int) rise + 6; y++) {
            double ax = dx / span;
            // The arch's centreline is a half ellipse; its thickness swells toward the feet.
            double cy = Math.abs(ax) <= 1 ? rise * Math.sqrt(1 - ax * ax) : 0;
            double thick = 4 + 6 * Math.pow(Math.abs(ax), 3);
            double width = 3.5 + 3 * Math.pow(Math.min(1, Math.abs(ax)), 2);
            boolean inArch = Math.abs(ax) <= 1.15 && Math.abs(y - cy) <= thick / 2 && Math.abs(dz) <= width;
            boolean inFoot = Math.abs(ax) > 0.75 && Math.abs(ax) <= 1.2 && y <= cy + thick / 2 && Math.abs(dz) <= width + 1;
            if (!inArch && !inFoot) continue;
            int x = cx + dx, z = cz + dz, by = g - 1 + y;
            if (by <= s.top(x, z)) continue;
            int band = Math.floorMod(by / 2 + (int) Math.round(1.5 * Math.sin(dx * 0.15)), bands.length);
            s.set(x, by, z, bands[band]);
        }
        for (int k = 0; k < 18; k++) {
            int x = cx + s.random.nextInt(60) - 30, z = cz + s.random.nextInt(24) - 12;
            int t = s.top(x, z);
            if (s.canvas.has(x, t + 1, z)) continue;
            s.set(x, t, z, Material.RED_SAND);
            if (k % 3 == 0) s.set(x, t + 1, z, Material.DEAD_BUSH);
        }
        s.chest(3, "sunscar_arch", cx, g + (int) rise + 2, cz, "south");
        s.spots.add(ReachRoost.build(s.canvas, s.terrain, cx + 8, cz + 15, 0, 1, "Sunscar Arch"));
        s.groundSpot("place", cx, cz + 12, 0F);
    }

    // ------------------------------------------------------------------ the Ashen Scar

    /**
     * The Ember Gate on the Rift's lip: a great square portal of black stone standing on a raised dais, its opening
     * thirteen wide and twenty tall alight with the way to Cinder Keep. Its pillars are carved with glowing runes and
     * its lintel watches with a weeping eye. A broad stair climbs to the dais between two colossal stone guardians,
     * swords planted, facing all who come. Behind it the ground the corruption has taken runs out to the abyss.
     */
    private static void emberGate(Site s) {
        int cx = s.x, cz = s.z, g = s.ground;
        // Which way the Rift lies, so the gate faces into it.
        double ax = ReachTerrain.RIFT_X - cx, az = ReachTerrain.RIFT_Z - cz;
        int fx = Math.abs(ax) >= Math.abs(az) ? (int) Math.signum(ax) : 0, fz = fx == 0 ? (int) Math.signum(az) : 0;
        int px = -fz, pz = fx;
        int floor = g + 5, open = 6, high = 20;
        // The dais: a platform of polished black stone from the stair's head to just behind the gate, footed to the
        // ground, a processional runner inlaid with magma leading to the portal, a low parapet round its edges.
        for (int f = -16; f <= 3; f++) for (int k = -15; k <= 15; k++) {
            int x = cx + fx * f + px * k, z = cz + fz * f + pz * k;
            s.footing(x, floor - 1, z, Material.BLACKSTONE);
            boolean runner = Math.abs(k) <= 2 && f < -2;
            boolean edge = Math.abs(k) == 15 || f == -16 && Math.abs(k) > 8;
            s.set(x, floor - 1, z, runner ? (Math.abs(k) == 2 ? Material.GILDED_BLACKSTONE : f % 3 == 0 ? Material.MAGMA_BLOCK : Material.CHISELED_POLISHED_BLACKSTONE)
                : Math.floorMod(f + k, 2) == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE);
            for (int y = floor; y <= floor + high + 8; y++) if (s.top(x, z) >= y || !s.canvas.has(x, y, z)) s.set(x, y, z, Material.AIR);
            if (edge) s.set(x, floor, z, Material.POLISHED_BLACKSTONE_BRICK_WALL, "up=true");
        }
        // The grand stair down to the ash, as wide as the portal.
        for (int step = 1; step <= 6; step++) for (int k = -8; k <= 8; k++) {
            int f = -16 - step;
            int x = cx + fx * f + px * k, z = cz + fz * f + pz * k;
            int y = floor - 1 - step;
            s.footing(x, y, z, Material.BLACKSTONE);
            s.set(x, y, z, Math.abs(k) == 8 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE_BRICK_STAIRS,
                Math.abs(k) == 8 ? "" : "facing=" + facing(fx, fz) + ",half=bottom");
            for (int yy = y + 1; yy <= floor + 4; yy++) if (s.top(x, z) >= yy || !s.canvas.has(x, yy, z)) s.set(x, yy, z, Material.AIR);
            if (Math.abs(k) == 8 && step % 2 == 0) {
                s.set(x, y + 1, z, Material.POLISHED_BLACKSTONE_WALL, "up=true");
                s.set(x, y + 2, z, Material.SOUL_LANTERN, "hanging=false");
            }
        }
        // The portal: pillars four thick and five deep, a lintel over the opening, a stepped crown.
        for (int k = -open - 5; k <= open + 5; k++) for (int f = -2; f <= 2; f++) for (int up = 0; up <= high + 8; up++) {
            int x = cx + fx * f + px * k, z = cz + fz * f + pz * k, y = floor + up;
            int a = Math.abs(k);
            boolean opening = a <= open && up < high;
            if (opening) {
                s.set(x, y, z, f == 0 ? Material.END_GATEWAY : Material.AIR);
                continue;
            }
            int crown = up - (high + 5);
            if (crown > 0 && a > open + 4 - crown * 2) continue;
            if (up > high + 8) continue;
            boolean face = Math.abs(f) == 2;
            boolean reveal = a == open + 1;
            Material m;
            if (reveal) m = up % 3 == 1 ? Material.MAGMA_BLOCK : Material.GILDED_BLACKSTONE;
            else if (face && a == open + 3 && up > 1 && up < high - 1) m = up % 4 == 0 ? Material.SHROOMLIGHT : Material.CHISELED_POLISHED_BLACKSTONE;
            else if (up == high || up == high + 5) m = Material.CHISELED_POLISHED_BLACKSTONE;
            else if (up % 6 == 0) m = Material.POLISHED_BLACKSTONE;
            else m = Math.floorMod(x * 3 + y + z * 5, 7) == 0 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS;
            // The front face's panels are sunk a block between the pillars' edges.
            if (face && f < 0 && a >= open + 2 && a <= open + 4 && up > 1 && up < high - 1 && a != open + 3) { s.set(x, y, z, Material.AIR); continue; }
            s.set(x, y, z, m);
            if (up == 0) s.footing(x, y, z, Material.BLACKSTONE);
        }
        // The weeping eye on the lintel: crying obsidian round a core of magma.
        for (int dk = -1; dk <= 1; dk++) for (int du = -1; du <= 1; du++) {
            int x = cx - fx * 2 + px * dk, z = cz - fz * 2 + pz * dk;
            s.set(x - fx, floor + high + 2 + du, z - fz, dk == 0 && du == 0 ? Material.MAGMA_BLOCK : Material.CRYING_OBSIDIAN);
        }
        // The guardians: two colossi on plinths at the dais's front corners, swords planted, facing the stair.
        int turn = fx == 0 ? (fz > 0 ? 0 : 2) : (fx > 0 ? 3 : 1);
        ReachStatues.Palette dark = new ReachStatues.Palette(Material.POLISHED_BLACKSTONE, Material.POLISHED_DEEPSLATE, Material.BLACKSTONE,
            Material.GILDED_BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS);
        for (int side : new int[] {-1, 1}) {
            int x = cx - fx * 11 + px * 12 * side, z = cz - fz * 11 + pz * 12 * side;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                for (int y = floor; y < floor + 3; y++) s.set(x + dx, y, z + dz, y == floor + 2 ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
            ReachStatues.hero(new ReachBrush(s.canvas, x, floor + 3, z, turn), 2.0, ReachStatues.Pose.SWORD_PLANTED, dark);
        }
        // Beams out over the drop from the portal's top, chains hanging into the Rift.
        for (int side : new int[] {-1, 1}) {
            int bx = cx + px * (open + 3) * side, bz = cz + pz * (open + 3) * side;
            int end = 3;
            while (end < 24 && s.terrain.column(bx + fx * end, bz + fz * end).land()) end++;
            end = Math.min(24, end + 3);
            for (int k = 3; k <= end; k++) s.set(bx + fx * k, floor + high + 4, bz + fz * k, Material.POLISHED_BASALT, "axis=" + (fx != 0 ? "x" : "z"));
            int ex = bx + fx * end, ez = bz + fz * end;
            for (int y = floor + high + 3; y >= floor - 40; y--) {
                if (s.terrain.column(ex, ez).land() && y <= s.top(ex, ez)) break;
                s.set(ex, y, ez, Material.IRON_CHAIN, "axis=y");
            }
            s.set(bx + fx * 9, floor + high + 3, bz + fz * 9, Material.IRON_CHAIN, "axis=y");
            s.set(bx + fx * 9, floor + high + 2, bz + fz * 9, Material.SOUL_LANTERN, "hanging=true");
        }
        // Behind the portal the corruption has the ground: netherrack and nylium, the odd shroomlight swelling up.
        for (int f = 4; f <= 20; f++) for (int k = -14; k <= 14; k++) {
            int x = cx + fx * f + px * k, z = cz + fz * f + pz * k;
            Column c = s.terrain.column(x, z);
            if (!c.land()) continue;
            int h = Math.floorMod(x * 7 + z * 13, 11);
            s.set(x, c.top(), z, h < 4 ? Material.CRIMSON_NYLIUM : h < 7 ? Material.NETHERRACK : h < 9 ? Material.MAGMA_BLOCK : Material.NETHER_WART_BLOCK);
            if (h == 10 && f > 8) s.set(x, c.top() + 1, z, Material.SHROOMLIGHT);
        }
        doorBraziers(s, cx, floor, cz, -fx, -fz, open + 3, 8, EMBER_DOOR, new String[] {"RAID", "Cinder Keep", "", ""}, new String[] {"Level 26", "", "Step through", "to set out"});
        for (int side : new int[] {-1, 1}) s.set(cx - fx * 3 + px * (open + 3) * side, floor + 12, cz - fz * 3 + pz * (open + 3) * side, EMBER_DOOR.banner(), "facing=" + facing(-fx, -fz));
        s.spot("door_cinder_keep", cx - fx * 4, floor, cz - fz * 4, yawToward(fx, fz));
        s.chest(4, "ember_gate", cx - fx * 14 + px * 13, floor, cz - fz * 14 + pz * 13, facing(px, pz));
        s.spots.add(ReachRoost.build(s.canvas, s.terrain, cx - fx * 25 + px * 11, cz - fz * 25 + pz * 11, -fx, -fz, "The Ember Gate"));
        s.groundSpot("place", cx - fx * 26, cz - fz * 26, 0F);
    }

    // ------------------------------------------------------------------ the Archive Isle

    /**
     * The Seal Spire: a tall pale tower on the isle, its shaft fluted with gold bands, and three rings of stone
     * floating round it at three heights, one for each Warden, never touching it. The Hall's door is at its foot,
     * its gilded portal open now on the starlit way in.
     */
    private static void sealSpire(Site s) {
        int cx = s.x, cz = s.z, g = s.ground;
        int h = 64;
        for (int y = -1; y < h; y++) {
            double r = y < 6 ? 6.5 : 5 - Math.max(0, y - 40) * 0.08;
            for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r) continue;
                int x = cx + dx, z = cz + dz;
                if (y == -1) { s.footing(x, g - 1, z, Material.SMOOTH_QUARTZ); s.set(x, g - 1, z, Material.SMOOTH_QUARTZ); continue; }
                boolean shell = d > r - 1;
                Material m = !shell ? Material.AIR : y % 12 == 11 ? Material.GOLD_BLOCK
                    : (Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 30) % 2 == 0 ? Material.QUARTZ_PILLAR : Material.CALCITE);
                s.set(x, g + y, z, m, m == Material.QUARTZ_PILLAR ? "axis=y" : "");
            }
        }
        // The cap: a gilded cone.
        for (int k = 0; k < 8; k++) for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++)
            if (Math.hypot(dx, dz) <= 5 - k * 0.65) s.set(cx + dx, g + h + k, cz + dz, k == 7 ? Material.GOLD_BLOCK : Material.SMOOTH_QUARTZ);
        // Three floating rings.
        int[] heights = {18, 34, 50};
        for (int i = 0; i < 3; i++) {
            double r = 11 - i * 1.5;
            for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
                double d = Math.hypot(dx, dz);
                if (Math.abs(d - r) > 0.8) continue;
                s.set(cx + dx, g + heights[i], cz + dz, Math.floorMod(dx * 3 + dz, 6) == 0 ? Material.CHISELED_QUARTZ_BLOCK : Material.CALCITE);
                if (Math.floorMod(dx + dz * 2, 9) == 0) s.set(cx + dx, g + heights[i] - 1, cz + dz, Material.IRON_CHAIN, "axis=y");
            }
        }
        // The door of the Hall at the foot, south: a tall gilded portal, sealed.
        for (int y = g; y <= g + 7; y++) for (int dx = -2; dx <= 2; dx++) {
            int up = y - (g + 5);
            boolean open = Math.abs(dx) <= 1 && (up <= 0 || Math.abs(dx) + up <= 1);
            s.set(cx + dx, y, cz + 6, open ? Material.END_GATEWAY : Material.CHISELED_QUARTZ_BLOCK);
        }
        // The approach: a paved walk cut level into the ground before the door.
        for (int dx = -3; dx <= 3; dx++) for (int dz = 7; dz <= 12; dz++) {
            int x = cx + dx, z = cz + dz;
            s.footing(x, g - 1, z, Material.QUARTZ_BRICKS);
            s.set(x, g - 1, z, dz % 2 == 0 ? Material.QUARTZ_BRICKS : Material.SMOOTH_QUARTZ);
            for (int y = g; y <= s.top(x, z) + 1; y++) s.set(x, y, z, Material.AIR);
        }
        for (int side : new int[] {-1, 1}) s.set(cx + 2 * side, g + 4, cz + 7, SEAL_DOOR.banner(), "facing=south");
        doorBraziers(s, cx, g, cz + 6, 0, 1, 4, 5, SEAL_DOOR, new String[] {"EXPEDITION", "The Hall of", "the Three", "Wardens"},
            new String[] {"Level 24", "", "Step through", "to set out"});
        s.spot("door_hall_of_wardens", cx, g, cz + 9, 180F);
        // The three keys echo the rings above, set proud of the door's head. One socket is dark and empty where the
        // archive was opened.
        for (int i = 0; i < 3; i++) {
            int dx = (i - 1) * 2;
            s.set(cx + dx, g + 7, cz + 7, switch (i) {
                case 0 -> Material.WAXED_CHISELED_COPPER;
                case 1 -> Material.SEA_LANTERN;
                default -> Material.POLISHED_BLACKSTONE;
            });
        }
        s.chest(4, "seal_spire", cx + 5, g, cz + 7, "west");
        s.groundSpot("place", cx, cz + 13, 180F);
    }
}
