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

    /**
     * Set REACH_PLAN to a PNG path to draw a feature's plan from above (the top block of each column, shaded by height,
     * gridded every 16 blocks): REACH_PLAN_FEATURE picks the feature (default highcrown), REACH_PLAN_BOX=x0,z0,x1,z1
     * the area and REACH_PLAN_SCALE the pixels per block. A builder's check on layout without generating a world.
     */
    @Test
    void drawPlanWhenAsked() throws java.io.IOException {
        String target = System.getenv("REACH_PLAN");
        if (target == null || target.isBlank()) return;
        ReachFeatures.Feature feature = FEATURES.feature(System.getenv().getOrDefault("REACH_PLAN_FEATURE", "highcrown")).orElseThrow();
        ReachCanvas.Frozen plan = FEATURES.plan(feature);
        String[] box = System.getenv().getOrDefault("REACH_PLAN_BOX", plan.minX + "," + plan.minZ + "," + plan.maxX + "," + plan.maxZ).split(",");
        int x0 = Integer.parseInt(box[0].trim()), z0 = Integer.parseInt(box[1].trim()), x1 = Integer.parseInt(box[2].trim()), z1 = Integer.parseInt(box[3].trim());
        int s = Integer.parseInt(System.getenv().getOrDefault("REACH_PLAN_SCALE", "4"));
        String view = System.getenv().getOrDefault("REACH_PLAN_VIEW", "top");
        if (!view.equals("top")) {
            elevation(plan, view, x0, z0, x1, z1, s, target);
            return;
        }
        var img = new java.awt.image.BufferedImage((x1 - x0 + 1) * s, (z1 - z0 + 1) * s, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            ReachTerrain.Column c = TERRAIN.column(x, z);
            String top = null;
            int topY = Integer.MIN_VALUE;
            for (int y = 260; y >= -20; y--) {
                String state = blockAt(plan, x, y, z);
                if (state.equals("minecraft:air") || state.contains("light[")) continue;
                top = plan.get(x, y, z) == null && state.equals("minecraft:stone") && y == c.top() ? "minecraft:grass_block" : state;
                topY = y;
                break;
            }
            java.awt.Color colour = top == null ? new java.awt.Color(20, 24, 40) : planColour(top, c);
            double shade = top == null ? 1 : Math.max(0.55, Math.min(1.35, 0.6 + (topY - 90) / 80.0));
            g.setColor(new java.awt.Color((int) Math.min(255, colour.getRed() * shade), (int) Math.min(255, colour.getGreen() * shade), (int) Math.min(255, colour.getBlue() * shade)));
            g.fillRect((x - x0) * s, (z - z0) * s, s, s);
        }
        g.setColor(new java.awt.Color(255, 255, 255, 60));
        for (int x = Math.floorDiv(x0, 16) * 16; x <= x1; x += 16) if (x >= x0) g.drawLine((x - x0) * s, 0, (x - x0) * s, img.getHeight());
        for (int z = Math.floorDiv(z0, 16) * 16; z <= z1; z += 16) if (z >= z0) g.drawLine(0, (z - z0) * s, img.getWidth(), (z - z0) * s);
        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 10));
        for (int x = Math.floorDiv(x0, 32) * 32; x <= x1; x += 32) if (x >= x0) g.drawString("x" + x, (x - x0) * s + 2, 11);
        for (int z = Math.floorDiv(z0, 32) * 32; z <= z1; z += 32) if (z >= z0) g.drawString("z" + z, 2, (z - z0) * s - 2);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", new java.io.File(target));
    }

    /**
     * A side view: looking {@code west}, {@code east}, {@code north} or {@code south} into the box, the first block met
     * along each line of sight, darker the deeper it lies; REACH_PLAN_Y=y0,y1 sets the heights (default 100..230).
     */
    private static void elevation(ReachCanvas.Frozen plan, String view, int x0, int z0, int x1, int z1, int s, String target) throws java.io.IOException {
        String[] ys = System.getenv().getOrDefault("REACH_PLAN_Y", "100,230").split(",");
        int y0 = Integer.parseInt(ys[0].trim()), y1 = Integer.parseInt(ys[1].trim());
        boolean alongX = view.equals("north") || view.equals("south");
        int a0 = alongX ? x0 : z0, a1 = alongX ? x1 : z1, d0 = alongX ? z0 : x0, d1 = alongX ? z1 : x1;
        // The view names the face seen: "west" stands west of the box looking east, with north on the left.
        boolean fromLow = view.equals("west") || view.equals("north");
        boolean mirror = view.equals("east") || view.equals("north");
        int w = (a1 - a0 + 1) * s, h = (y1 - y0 + 1) * s;
        var img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        for (int a = a0; a <= a1; a++) for (int y = y0; y <= y1; y++) {
            java.awt.Color colour = new java.awt.Color(120, 160, 210);
            for (int k = 0; k <= d1 - d0; k++) {
                int d = fromLow ? d0 + k : d1 - k;
                int x = alongX ? a : d, z = alongX ? d : a;
                String state = blockAt(plan, x, y, z);
                if (state.equals("minecraft:air") || state.contains("light[")) continue;
                ReachTerrain.Column c = TERRAIN.column(x, z);
                if (plan.get(x, y, z) == null && state.equals("minecraft:stone") && y == c.top()) state = "minecraft:grass_block";
                java.awt.Color base = planColour(state, c);
                double shade = Math.max(0.35, 1.0 - k / 120.0);
                if (state.contains("_stairs") || state.contains("_slab")) shade *= 0.85;
                colour = new java.awt.Color((int) (base.getRed() * shade), (int) (base.getGreen() * shade), (int) (base.getBlue() * shade));
                break;
            }
            int px = mirror ? (a1 - a) * s : (a - a0) * s;
            g.setColor(colour);
            g.fillRect(px, (y1 - y) * s, s, s);
        }
        g.setColor(new java.awt.Color(255, 255, 255, 70));
        for (int y = Math.floorDiv(y0, 10) * 10; y <= y1; y += 10) if (y >= y0) g.drawLine(0, (y1 - y) * s, w, (y1 - y) * s);
        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 10));
        for (int y = Math.floorDiv(y0, 20) * 20; y <= y1; y += 20) if (y >= y0) g.drawString("y" + y, 2, (y1 - y) * s - 2);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", new java.io.File(target));
    }

    private static java.awt.Color planColour(String state, ReachTerrain.Column c) {
        String n = state.substring(state.indexOf(':') + 1).replaceAll("\\[.*", "");
        if (n.contains("water")) return new java.awt.Color(60, 110, 200);
        if (n.contains("glass")) return new java.awt.Color(150, 200, 230);
        if (n.contains("leaves") || n.contains("azalea")) return new java.awt.Color(50, 120, 45);
        if (n.contains("grass") || n.contains("moss") || n.contains("fern")) return new java.awt.Color(95, 150, 60);
        if (n.contains("deepslate") || n.contains("blackstone")) return new java.awt.Color(70, 70, 78);
        if (n.contains("red") || n.contains("brick") && !n.contains("stone")) return new java.awt.Color(150, 60, 50);
        if (n.contains("quartz") || n.contains("calcite") || n.contains("white")) return new java.awt.Color(225, 222, 210);
        if (n.contains("copper")) return new java.awt.Color(90, 170, 140);
        if (n.contains("gold")) return new java.awt.Color(230, 190, 60);
        if (n.contains("spruce") || n.contains("dark_oak")) return new java.awt.Color(95, 70, 45);
        if (n.contains("oak") || n.contains("birch") || n.contains("plank") || n.contains("log")) return new java.awt.Color(160, 125, 80);
        if (n.contains("sand") || n.contains("path") || n.contains("gravel")) return new java.awt.Color(190, 175, 130);
        if (n.contains("dirt") || n.contains("podzol") || n.contains("farmland")) return new java.awt.Color(120, 90, 60);
        if (n.contains("lantern") || n.contains("torch") || n.contains("candle")) return new java.awt.Color(255, 220, 120);
        if (n.contains("andesite") || n.contains("tuff")) return new java.awt.Color(140, 140, 135);
        if (n.contains("stone") || n.contains("cobble")) return new java.awt.Color(125, 125, 125);
        if (n.equals("stone") && c.land()) return new java.awt.Color(110, 110, 110);
        return new java.awt.Color(170, 160, 150);
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
