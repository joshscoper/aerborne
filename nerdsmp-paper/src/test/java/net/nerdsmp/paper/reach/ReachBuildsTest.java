package net.nerdsmp.paper.reach;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachBuildsTest {
    private static final ReachTerrain TERRAIN = new ReachTerrain();
    private static final ReachFeatures FEATURES = new ReachFeatures(TERRAIN, ReachBuilds.all(TERRAIN));

    /** The block at a spot as the generator would leave it: the feature's own block, else the terrain's. */
    static String blockAt(ReachCanvas.Frozen plan, int x, int y, int z) {
        ReachCanvas.Spec spec = plan.get(x, y, z);
        if (spec != null) return spec.state();
        ReachTerrain.Column c = TERRAIN.column(x, z);
        if (!c.land()) return "minecraft:air";
        if (y <= c.top() && y >= c.bottom()) return "minecraft:stone";
        if (c.wet() && y <= c.water()) return "minecraft:water";
        return "minecraft:air";
    }

    static boolean solid(String state) {
        // Path blocks and farmland are a sixteenth short: NPC footing refuses them.
        return !state.startsWith("minecraft:air") && !state.contains("water") && !state.contains("lantern")
            && !state.contains("dirt_path") && !state.contains("farmland") && !state.contains("slab[type=bottom")
            && !state.contains("carpet") && !state.contains("_sign") && !state.contains("banner");
    }

    /** Where an NPC's body may stand: the server's placement check refuses anything with collision, carpet included. */
    static boolean passable(String state) {
        return state.equals("minecraft:air") || state.contains("_sign") || state.contains("banner")
            || state.contains("lantern") && state.contains("hanging=true");
    }

    @Test
    void everyFeatureBuildsAndStaysInItsFootprint() {
        for (ReachFeatures.Feature feature : FEATURES.features()) {
            ReachCanvas.Frozen plan = FEATURES.plan(feature);
            ReachFeatures.Footprint f = feature.footprint();
            assertTrue(plan.minX >= f.minX() && plan.maxX <= f.maxX() && plan.minZ >= f.minZ() && plan.maxZ <= f.maxZ(),
                feature.id() + " spills out of its footprint: x " + plan.minX + ".." + plan.maxX + ", z " + plan.minZ + ".." + plan.maxZ);
        }
    }

    @Test
    void everySpotHasAFloorAndHeadroom() {
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, ReachFeatures.Spot> entry : FEATURES.spots().entrySet()) {
            ReachFeatures.Spot spot = entry.getValue();
            String featureId = entry.getKey().substring(0, entry.getKey().indexOf('.'));
            ReachCanvas.Frozen plan = FEATURES.plan(FEATURES.feature(featureId).orElseThrow());
            int x = (int) Math.floor(spot.x()), y = (int) Math.floor(spot.y()), z = (int) Math.floor(spot.z());
            String floor = blockAt(plan, x, y - 1, z), feet = blockAt(plan, x, y, z), head = blockAt(plan, x, y + 1, z);
            boolean chest = entry.getKey().contains(".chest_");
            if (chest ? !solid(floor) || !feet.contains("chest") : !solid(floor) || !passable(feet) || !passable(head))
                bad.add(entry.getKey() + " at " + x + "," + y + "," + z + ": floor " + floor + ", feet " + feet + ", head " + head);
        }
        assertEquals(List.of(), bad);
    }

    /** Set REACH_SPOTS to a file path to write every named spot as JSON (bot tests read it). */
    @Test
    void dumpSpotsWhenAsked() throws java.io.IOException {
        String target = System.getenv("REACH_SPOTS");
        if (target == null || target.isBlank()) return;
        StringBuilder json = new StringBuilder("{\n");
        var spots = FEATURES.spots();
        int i = 0;
        for (var e : spots.entrySet()) {
            var s = e.getValue();
            json.append("  \"").append(e.getKey()).append("\": [").append(s.x()).append(", ").append(s.y()).append(", ").append(s.z())
                .append(", ").append(s.yaw()).append("]").append(++i < spots.size() ? ",\n" : "\n");
        }
        json.append("}\n");
        java.nio.file.Files.writeString(java.nio.file.Path.of(target), json);
    }

    /** No building may stand in the ground: inside every footprint, the three blocks above the floor are the build's own. */
    @Test
    void noBuildingIsSunkIntoTheTerrain() {
        List<String> bad = new ArrayList<>();
        for (ReachFeatures.Feature feature : FEATURES.features()) {
            ReachCanvas.Frozen plan = FEATURES.plan(feature);
            for (ReachCanvas.Lot lot : plan.lots) {
                int intruding = 0;
                for (int x = lot.minX(); x <= lot.maxX(); x++) for (int z = lot.minZ(); z <= lot.maxZ(); z++) {
                    ReachTerrain.Column c = TERRAIN.column(x, z);
                    if (!c.land()) continue;
                    for (int y = lot.floor(); y < lot.floor() + 3; y++) if (plan.get(x, y, z) == null && y <= c.top()) intruding++;
                }
                if (intruding > 0) bad.add(feature.id() + " lot " + lot + ": " + intruding + " blocks of terrain inside");
            }
        }
        assertEquals(List.of(), bad);
    }

    /** A blank shop sign tells a player nothing: every hanging sign a build places must carry words. */
    @Test
    void everyHangingSignIsLettered() {
        List<String> blank = new ArrayList<>();
        for (ReachFeatures.Feature feature : FEATURES.features()) {
            ReachCanvas.Frozen plan = FEATURES.plan(feature);
            for (int cx = plan.minX >> 4; cx <= plan.maxX >> 4; cx++) for (int cz = plan.minZ >> 4; cz <= plan.maxZ >> 4; cz++) {
                if (!plan.touches(cx, cz)) continue;
                java.util.Set<String> lettered = new java.util.HashSet<>();
                for (ReachCanvas.Text t : plan.texts(cx, cz)) lettered.add(t.x() + "," + t.y() + "," + t.z());
                int baseX = cx << 4, baseZ = cz << 4;
                plan.forEach(cx, cz, cell -> {
                    if (!cell.spec().state().contains("hanging_sign")) return;
                    String at = (baseX + cell.lx()) + "," + cell.y() + "," + (baseZ + cell.lz());
                    if (!lettered.contains(at)) blank.add(feature.id() + " " + at);
                });
            }
        }
        assertEquals(List.of(), blank);
    }

    @Test
    void theArrivalIsOnThePier() {
        ReachFeatures.Spot arrival = FEATURES.arrival();
        ReachCanvas.Frozen plan = FEATURES.plan(FEATURES.feature("highcrown").orElseThrow());
        assertTrue(solid(blockAt(plan, (int) Math.floor(arrival.x()), (int) arrival.y() - 1, (int) Math.floor(arrival.z()))), "arrival floor");
    }
}
