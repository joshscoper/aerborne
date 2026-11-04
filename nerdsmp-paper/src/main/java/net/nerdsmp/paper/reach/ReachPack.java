package net.nerdsmp.paper.reach;

import net.nerdsmp.paper.worldpack.WorldPack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The world pack for the Wardens' Reach, built in code from the same terrain and features the generator uses, so
 * an NPC can never stand somewhere its building isn't. Zones, spawn points, NPC anchors with patrols, warps and
 * the arrival spawn are all derived here; the world-pack service then places and maintains them exactly as it does
 * for the old map's YAML pack.
 */
public final class ReachPack {
    public static final int VERSION = 1;
    /** Zones are capped at 16.7 million blocks, so they span only the heights that matter. */
    private static final int SKY_Y = 230, LOW_Y = 70;
    /** Separates fallback spots in an anchor: "a|b" stands at a, or at b when a wasn't built. */
    private static final String OR = "|";

    private ReachPack() { }

    /** Which NPC stands at which built spot. Missing spots are skipped. */
    static final Map<String, String> ANCHORS = anchors();

    private static Map<String, String> anchors() {
        Map<String, String> a = new LinkedHashMap<>();
        // Highcrown: the arrival line, the docks, the Gate Ring, the Hall of Callings, the exchange, the keep.
        a.put("highcrown.arrival_0", "cassia_dawn");
        a.put("highcrown.arrival_1", "aeron_vale");
        a.put("highcrown.arrival_2", "cato_flint");
        a.put("highcrown.guildhall", "captain_wren");
        a.put("highcrown.dockmaster", "dockmaster_ebb");
        a.put("highcrown.tethered_gull", "gull_innkeeper");
        a.put("highcrown.memorial", "tamsin_wake");
        String[] ring = {"kestrel_way", "elian_quill", "gilda_penn", "hessa_clay", "ivo_reed", "bram_tallow", "garrick_mott"};
        for (int i = 0; i < ring.length; i++) a.put("highcrown.gate_ring_" + i, ring[i]);
        String[] hall = {"pell_wren", "fen_calder", "nima_hart"};
        for (int i = 0; i < hall.length; i++) a.put("highcrown.hall_" + i, hall[i]);
        String[] crafters = {"torren_pike", "sella_mire", "veyra_loom"};
        for (int i = 0; i < crafters.length; i++) a.put("highcrown.crafter_" + i, crafters[i]);
        String[] exchange = {"nyra_vell", "rook_salen", "orris_bell", "mara_venn", "vera_lune", "alba_meadow", "rowan_ashwood",
            "dain_slate", "petra_mason", "hob_copper"};
        for (int i = 0; i < exchange.length; i++) a.put("highcrown.stall_" + (i % 2 == 0 ? "w_" : "e_") + (i / 2 + 1), exchange[i]);
        String[] folk = {"highcrown_folk_baker", "highcrown_folk_porter", "highcrown_folk_widow", "highcrown_folk_urchin"};
        for (int i = 0; i < folk.length; i++) a.put("highcrown.stall_" + (i % 2 == 0 ? "w_" : "e_") + (i / 2 + 7), folk[i]);
        a.put("highcrown.keep_hall", "reach_steward_aldric");
        a.put("highcrown.keep_court", "orin_ledger");
        a.put("highcrown.citadel_steps", "arlen_voss");
        a.put("highcrown.plaza", "dorian_ash");
        a.put("highcrown.plaza_guide", "lyra_senn");
        a.put("highcrown.kingsgate", "highcrown_watch");
        // Goldenreach Vale.
        a.put("goldenreach.reeve_hall", "reeve_hollis");
        a.put("goldenreach.inn", "wenna_marr");
        a.put("goldenreach.smithy", "garth_ploughwright");
        a.put("goldenreach.mill" + OR + "goldenreach.ada_cottage" + OR + "goldenreach.house_d", "ada_miller");
        a.put("goldenreach.chapel", "brother_tobin");
        a.put("goldenreach.skiff", "jory_fenn");
        a.put("goldenreach.thistle_house", "bess_thistle");
        a.put("goldenreach.burned_mill", "tam_thistle");
        a.put("goldenreach.cider_press", "pip_brindle");
        a.put("goldenreach.warden_post", "warden_hale");
        a.put("goldenreach.maren_camp", "maren_ashdown");
        a.put("goldenreach.house_a", "millbrook_folk_carter");
        a.put("goldenreach.house_b", "millbrook_folk_gleaner");
        a.put("goldenreach.house_c", "millbrook_folk_elder");
        return a;
    }

    /** Zones: id, title, levels, box, safe, and the creatures. */
    private record ZoneSpec(String id, String title, String levels, int x0, int z0, int x1, int z1, int y0, int y1, boolean safe,
                            List<WorldPack.MobEntry> mobs, int cap) { }

    private static WorldPack.MobEntry mob(String id, int weight, int level) { return new WorldPack.MobEntry("custom:" + id, weight, level); }

    /** The first of an anchor's spots that was built, or null. */
    static String chooseSpot(String choices, Map<String, ReachFeatures.Spot> spots) {
        for (String choice : choices.split(java.util.regex.Pattern.quote(OR))) if (spots.containsKey(choice)) return choice;
        return null;
    }

    public static WorldPack build(String worldName, ReachTerrain terrain, ReachFeatures features) {
        Map<String, ReachFeatures.Spot> spots = features.spots();
        ReachFeatures.Spot granary = spots.get("goldenreach.granary");
        ReachFeatures.Spot king = spots.get("goldenreach.scarecrow_king");
        ReachFeatures.Spot farm = spots.get("goldenreach.thistle_house");
        int cellarFloor = king == null ? 90 : (int) king.y();
        int gx = granary == null ? -190 : (int) granary.x(), gz = granary == null ? 420 : (int) granary.z();

        List<ZoneSpec> specs = new ArrayList<>();
        specs.add(new ZoneSpec("reach_highcrown", "Highcrown", "", -190, 760, 190, 1040, 80, 200, true, List.of(), 1));
        specs.add(new ZoneSpec("reach_sky_docks", "The Sky Docks", "", -190, 1041, 190, 1175, 80, 200, true, List.of(), 1));
        specs.add(new ZoneSpec("reach_millbrook", "Millbrook", "", -25, 495, 105, 625, LOW_Y, SKY_Y, true, List.of(), 1));
        if (farm != null) {
            int fx = (int) farm.x(), fz = (int) farm.z();
            specs.add(new ZoneSpec("reach_thistle_farm", "Thistle Farm", "", Math.max(fx - 20, -255), Math.max(fz - 14, 471),
                Math.min(fx + 28, -30), Math.min(fz + 30, 599), LOW_Y, SKY_Y, true, List.of(), 1));
        }
        specs.add(new ZoneSpec("reach_kingsgate_fields", "Kingsgate Fields", "1-2", -20, 626, 105, 759, LOW_Y, SKY_Y, false,
            List.of(mob("field_gnawer", 5, 1), mob("bristle_boar", 2, 2)), 10));
        specs.add(new ZoneSpec("reach_brindle_orchards", "Brindle Orchards", "2-4", -230, 600, -26, 759, LOW_Y, SKY_Y, false,
            List.of(mob("bristle_boar", 5, 3), mob("field_gnawer", 2, 2)), 10));
        specs.add(new ZoneSpec("reach_goldwater_terraces", "Goldwater Terraces", "2-4", 106, 380, 330, 759, LOW_Y, SKY_Y, false,
            List.of(mob("straw_scarecrow", 5, 3), mob("field_gnawer", 3, 2)), 12));
        specs.add(new ZoneSpec("reach_windmill_ridge", "Windmill Ridge", "3-5", -100, 330, 105, 470, LOW_Y, SKY_Y, false,
            List.of(mob("straw_scarecrow", 4, 4), mob("choir_initiate", 1, 5)), 10));
        specs.add(new ZoneSpec("reach_black_granary", "The Black Granary", "5-7", -260, 330, -101, 470, cellarFloor + 16, SKY_Y, false,
            List.of(mob("choir_initiate", 5, 6), mob("choir_cantor", 2, 6)), 10));
        specs.add(new ZoneSpec("reach_tollroad", "The Tollroad", "4-6", -340, 360, -261, 520, LOW_Y, SKY_Y, false,
            List.of(mob("tollkeeper_cutthroat", 5, 5), mob("tollkeeper_bowman", 3, 5)), 10));
        specs.add(new ZoneSpec("reach_hollow_road", "The Hollow Road", "6-7", -445, 380, -341, 520, LOW_Y, SKY_Y, false,
            List.of(mob("tollkeeper_cutthroat", 2, 6), mob("bristle_boar", 3, 6)), 8));
        specs.add(new ZoneSpec("reach_granary_cellars", "The Old Granary Cellars", "6-7", gx - 16, gz - 120, gx + 16, gz + 2,
            cellarFloor - 2, cellarFloor + 15, false,
            List.of(mob("choir_initiate", 3, 7), mob("choir_cantor", 1, 7), mob("ember_scarecrow", 4, 7)), 9));

        Map<String, WorldPack.Zone> zones = new LinkedHashMap<>();
        for (ZoneSpec s : specs) {
            WorldPack.Box box = new WorldPack.Box(s.x0, s.y0, s.z0, s.x1, s.y1, s.z1);
            List<WorldPack.Point> points = s.safe ? List.of()
                : s.id.equals("reach_granary_cellars") ? cellarPoints(spots, box) : groundPoints(terrain, features, box, 60);
            if (!s.safe && points.isEmpty()) continue;
            zones.put(s.id, new WorldPack.Zone(s.id, s.title, box, s.safe, s.levels, s.cap, 30, 48, s.mobs, points));
        }

        Map<String, WorldPack.Anchor> npcs = new LinkedHashMap<>();
        ANCHORS.forEach((choices, npc) -> {
            String spotId = chooseSpot(choices, spots);
            if (spotId == null) return;
            String key = choices.split(java.util.regex.Pattern.quote(OR))[0].replace('.', '_');
            npcs.put(key, new WorldPack.Anchor(key, npc, point(spots.get(spotId)), List.of()));
        });
        // Watch guards walking the avenue and the green.
        patrol(npcs, "highcrown_watch_avenue_w", "highcrown_watch", -3, HighcrownCity.STREET, HighcrownCity.GATE_Z + 20, 0, 150);
        patrol(npcs, "highcrown_watch_avenue_e", "highcrown_watch", 3, HighcrownCity.STREET, HighcrownCity.DOCK_SQUARE_Z - 10, 0, -150);
        ReachFeatures.Spot green = spots.get("goldenreach.green");
        if (green != null) {
            List<WorldPack.Point> route = new ArrayList<>();
            for (int a = 30; a < 360; a += 60) route.add(new WorldPack.Point(Math.floor(green.x() + Math.cos(Math.toRadians(a)) * 14),
                green.y(), Math.floor(green.z() + Math.sin(Math.toRadians(a)) * 11), 0F));
            npcs.put("goldenreach_watch", new WorldPack.Anchor("goldenreach_watch", "millbrook_watch", point(green), route));
        }

        Map<String, WorldPack.Point> warps = new LinkedHashMap<>();
        ReachFeatures.Spot arrival = features.arrival();
        warps.put("reach", point(arrival));
        if (spots.containsKey("highcrown.plaza")) warps.put("reach_plaza", point(spots.get("highcrown.plaza")));
        if (green != null) warps.put("millbrook", point(green));

        return new WorldPack("reach", VERSION, "The Wardens' Reach", ReachTerrain.SEED, "normal", false, worldName,
            List.of(), point(arrival), null, warps, zones, npcs, Map.of());
    }

    private static void patrol(Map<String, WorldPack.Anchor> npcs, String key, String npc, int x, int y, int z, int dx, int dz) {
        List<WorldPack.Point> route = new ArrayList<>();
        for (int k = 1; k <= 4; k++) route.add(new WorldPack.Point(x + dx * k / 4.0, y, z + dz * k / 4.0, 0F));
        npcs.put(key, new WorldPack.Anchor(key, npc, new WorldPack.Point(x + 0.5, y, z + 0.5, 0F), route));
    }

    private static WorldPack.Point point(ReachFeatures.Spot s) { return new WorldPack.Point(s.x(), s.y(), s.z(), s.yaw()); }

    /**
     * Standable ground inside a zone box: dry land, two blocks of headroom, not on a road or a building, sampled on
     * a jittered grid so creatures spread out rather than clump.
     */
    static List<WorldPack.Point> groundPoints(ReachTerrain terrain, ReachFeatures features, WorldPack.Box box, int max) {
        List<WorldPack.Point> points = new ArrayList<>();
        int step = Math.max(6, (int) Math.sqrt((box.maxX() - box.minX()) * (double) (box.maxZ() - box.minZ()) / (max * 1.6)));
        for (int x = box.minX() + step / 2; x <= box.maxX(); x += step) for (int z = box.minZ() + step / 2; z <= box.maxZ(); z += step) {
            long h = ReachFlora.hash(x, z);
            int px = x + (int) (h % 3), pz = z + (int) ((h >> 8) % 3);
            ReachTerrain.Column c = terrain.column(px, pz);
            if (!c.land() || c.wet() || c.road() > 0.3 || c.river() < 3 || c.edge() < 8) continue;
            if (c.top() + 1 < box.minY() || c.top() + 2 > box.maxY()) continue;
            if (features.occupied(px, pz)) continue;
            points.add(new WorldPack.Point(px + 0.5, c.top() + 1, pz + 0.5, (float) (h % 360)));
            if (points.size() >= max) return points;
        }
        return points;
    }

    private static List<WorldPack.Point> cellarPoints(Map<String, ReachFeatures.Spot> spots, WorldPack.Box box) {
        List<WorldPack.Point> points = new ArrayList<>();
        for (String id : List.of("long_cellar_a", "long_cellar_b", "hatchery", "threshing_floor")) {
            ReachFeatures.Spot s = spots.get("goldenreach." + id);
            if (s == null) continue;
            for (int[] d : new int[][] {{0, 0}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
                WorldPack.Point p = new WorldPack.Point(s.x() + d[0], s.y(), s.z() + d[1], 0F);
                if (p.x() >= box.minX() && p.x() < box.maxX() + 1 && p.z() >= box.minZ() && p.z() < box.maxZ() + 1) points.add(p);
            }
        }
        return points;
    }

    /** NPC ids the pack places, for tests that check every one has a definition. */
    static Set<String> npcIds() { return Set.copyOf(ANCHORS.values()); }
}
