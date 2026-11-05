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
    public static final int VERSION = 8;
    /** Zones are capped at 16.7 million blocks, so they span only the heights that matter. */
    private static final int SKY_Y = 230, LOW_Y = 70;
    /** Separates fallback spots in an anchor: "a|b" stands at a, or at b when a wasn't built. */
    private static final String OR = "|";

    private ReachPack() { }

    /** Which NPC stands at which built spot. Missing spots are skipped. */
    static final Map<String, String> ANCHORS = anchors();

    private static Map<String, String> anchors() {
        Map<String, String> a = new LinkedHashMap<>();
        // Highcrown: every service NPC stands where their work is, behind a counter, at a desk or at their post
        // (HighcrownCity marks each as npc_<id>): the guide at the pier, the masters in their towers, keepers in their
        // shops, mentors in the Hall of Callings, wayfarers in their hall, the Steward on the throne-hall floor.
        String[] highcrown = {"cassia_dawn", "aeron_vale", "cato_flint", "captain_wren", "dockmaster_ebb", "gull_innkeeper",
            "tamsin_wake", "kestrel_way", "elian_quill", "gilda_penn", "hessa_clay", "ivo_reed", "bram_tallow", "garrick_mott",
            "pell_wren", "fen_calder", "nima_hart", "torren_pike", "sella_mire", "veyra_loom", "nyra_vell", "rook_salen",
            "orris_bell", "mara_venn", "vera_lune", "alba_meadow", "rowan_ashwood", "dain_slate", "petra_mason", "hob_copper",
            "highcrown_folk_baker", "highcrown_folk_porter", "highcrown_folk_widow", "highcrown_folk_urchin",
            "reach_steward_aldric", "orin_ledger", "arlen_voss", "dorian_ash", "lyra_senn", "highcrown_folk_clerk_a",
            "highcrown_folk_clerk_b"};
        for (String npc : highcrown) a.put("highcrown.npc_" + npc, npc);
        a.put("highcrown.kingsgate", "highcrown_watch");
        a.put("highcrown.castle_gate", "highcrown_watch");
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
        // Watch guards walking the avenue: one from the plaza down through the market, one from the market to the docks.
        route(npcs, spots, "highcrown_watch_avenue_w", "highcrown_watch", 0, 3);
        route(npcs, spots, "highcrown_watch_avenue_e", "highcrown_watch", 2, 5);
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

    /** A patrol along the Watch marks of Highcrown from {@code from} to {@code to}, which follow the avenue's terraces and stairs. */
    private static void route(Map<String, WorldPack.Anchor> npcs, Map<String, ReachFeatures.Spot> spots, String key, String npc, int from, int to) {
        List<WorldPack.Point> route = new ArrayList<>();
        for (int k = from; k <= to; k++) {
            ReachFeatures.Spot s = spots.get("highcrown.watch_" + k);
            if (s != null) route.add(point(s));
        }
        if (route.isEmpty()) return;
        npcs.put(key, new WorldPack.Anchor(key, npc, route.get(0), route.subList(1, route.size())));
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
