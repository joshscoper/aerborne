package net.nerdsmp.paper.reach;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachTerrainTest {
    private static final ReachTerrain TERRAIN = new ReachTerrain();

    @Test
    void everyZoneSitsWhereTheLayoutSays() {
        assertEquals(ReachTerrain.Zone.HIGHCROWN, TERRAIN.zoneAt(0, 905));
        assertEquals(ReachTerrain.Zone.GOLDENREACH, TERRAIN.zoneAt(0, 520));
        assertEquals(ReachTerrain.Zone.BARROWDOWNS, TERRAIN.zoneAt(-650, 420));
        assertEquals(ReachTerrain.Zone.THORNWOOD, TERRAIN.zoneAt(-770, -280));
        assertEquals(ReachTerrain.Zone.MISTMERE, TERRAIN.zoneAt(-220, -640));
        assertEquals(ReachTerrain.Zone.IRONSPINE, TERRAIN.zoneAt(560, -620));
        assertEquals(ReachTerrain.Zone.SUNSCAR, TERRAIN.zoneAt(790, 260));
        assertEquals(ReachTerrain.Zone.SCAR, TERRAIN.zoneAt(0, -300));
        assertEquals(ReachTerrain.Zone.ARCHIVE, TERRAIN.zoneAt(1520, -80));
        assertEquals(ReachTerrain.Zone.SKY, TERRAIN.zoneAt(0, -1190));
    }

    @Test
    void theRiftIsAHoleThroughTheMiddle() {
        assertFalse(TERRAIN.column((int) ReachTerrain.RIFT_X, (int) ReachTerrain.RIFT_Z).land());
        assertTrue(TERRAIN.column(0, 0).land());
    }

    @Test
    void columnsAreSolidAndWithinTheWorld() {
        for (int x = ReachTerrain.MIN_X; x <= ReachTerrain.MAX_X; x += 37) for (int z = ReachTerrain.MIN_Z; z <= ReachTerrain.MAX_Z; z += 37) {
            ReachTerrain.Column c = TERRAIN.column(x, z);
            if (!c.land()) continue;
            assertTrue(c.top() < 300 && c.top() > 40, "top " + c.top() + " at " + x + "," + z);
            assertTrue(c.bottom() >= ReachTerrain.FLOOR_Y && c.bottom() < c.top(), "bottom at " + x + "," + z);
            if (c.wet()) assertTrue(c.water() - c.top() <= 16, "water too deep at " + x + "," + z);
        }
    }

    @Test
    void waterNeverSitsNextToLowerDryLand() {
        // Any wet column's dry neighbours must hold the water in, or it pours out as soon as a block updates.
        java.util.List<String> leaks = new java.util.ArrayList<>();
        for (int x = -1000; x <= 400; x += 2) for (int z = -1100; z <= 1100; z += 2) {
            ReachTerrain.Column c = TERRAIN.column(x, z);
            if (!c.wet()) continue;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                ReachTerrain.Column n = TERRAIN.column(x + d[0], z + d[1]);
                if (n.land() && !n.wet() && n.top() < c.water()) leaks.add((x + d[0]) + "," + (z + d[1]) + " top " + n.top() + " < " + c.water());
            }
        }
        assertEquals(java.util.List.of(), leaks, "dry land below a neighbour's water level");
    }

    @Test
    void bothRiversPourOffTheLip() {
        assertEquals(2, TERRAIN.falls().size());
        for (ReachTerrain.Fall fall : TERRAIN.falls()) {
            assertFalse(TERRAIN.column((int) fall.x(), (int) fall.z()).land());
            assertNotEquals(Integer.MIN_VALUE, TERRAIN.column((int) fall.x(), (int) fall.z()).fall());
        }
    }

    @Test
    void eachZoneHasItsOwnHeightCharacter() {
        Map<ReachTerrain.Zone, int[]> range = new EnumMap<>(ReachTerrain.Zone.class);
        for (int x = ReachTerrain.MIN_X; x <= ReachTerrain.MAX_X; x += 11) for (int z = ReachTerrain.MIN_Z; z <= ReachTerrain.MAX_Z; z += 11) {
            ReachTerrain.Column c = TERRAIN.column(x, z);
            if (!c.land()) continue;
            int[] r = range.computeIfAbsent(c.zone(), k -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
            r[0] = Math.min(r[0], c.top());
            r[1] = Math.max(r[1], c.top());
        }
        assertTrue(range.get(ReachTerrain.Zone.IRONSPINE)[1] > 190, "Ironspine needs real peaks");
        assertTrue(range.get(ReachTerrain.Zone.SCAR)[1] > 150, "the Brimwall must stand high");
    }

    /**
     * A road never runs in a slot between sheer walls: wherever it cuts through high ground, the wall at its verge is
     * at most the cutting's retaining wall, and the hillside above is battered back, so the ground a few blocks out
     * stands no higher than one block per block of distance above that wall.
     */
    @Test
    void roadsRunInGradedCuttingsNotSlots() {
        java.util.List<String> bad = new java.util.ArrayList<>();
        double deepest = 0;
        String where = "";
        for (ReachPath road : TERRAIN.roads()) for (int i = 0; i < road.size(); i++) {
            double x = road.xs[i], z = road.zs[i];
            for (double off = 3.5; off <= 12; off += 0.5) for (int side : new int[] {-1, 1}) {
                int dx = (int) Math.floor(x + side * off), dz = (int) Math.floor(z);
                ReachTerrain.Column c = TERRAIN.column(dx, dz);
                if (!c.land() || c.roadLevel() == Integer.MIN_VALUE) continue;
                if (c.cut() > deepest) { deepest = c.cut(); where = dx + "," + dz; }
                if (c.cut() > ReachTerrain.CUT_WALL + 0.6) bad.add(dx + "," + dz + " wall " + String.format("%.1f", c.cut()));
            }
        }
        System.out.println("Deepest road cutting wall: " + String.format("%.1f", deepest) + " at " + where);
        assertTrue(bad.isEmpty(), "sheer cuttings: " + bad.subList(0, Math.min(10, bad.size())));
    }

    /** Set REACH_PROBE to "x,z;x,z;..." to print those columns (a design aid). */
    @Test
    void probeWhenAsked() {
        String probe = System.getenv("REACH_PROBE");
        if (probe == null || probe.isBlank()) return;
        StringBuilder out = new StringBuilder();
        for (String pair : probe.split(";")) {
            String[] xz = pair.split(",");
            int x = Integer.parseInt(xz[0].strip()), z = Integer.parseInt(xz[1].strip());
            ReachTerrain.Column c = TERRAIN.column(x, z);
            out.append(x).append(',').append(z).append(" zone=").append(c.zone()).append(" land=").append(c.land())
                .append(" top=").append(c.top()).append(" bottom=").append(c.bottom()).append(" water=")
                .append(c.wet() ? c.water() : "-").append(" edge=").append((int) c.edge()).append(" river=")
                .append(c.river() > 1e9 ? "-" : String.valueOf((int) c.river())).append(" road=").append(String.format("%.2f", c.road())).append('\n');
        }
        try { java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv().getOrDefault("REACH_PROBE_OUT", "build/reach-probe.txt")), out); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    /** Set REACH_ATLAS to a file path to draw the Reach from above. */
    @Test
    void drawAtlasWhenAsked() throws Exception {
        String target = System.getenv("REACH_ATLAS");
        if (target == null || target.isBlank()) return;
        int scale = Integer.parseInt(System.getenv().getOrDefault("REACH_ATLAS_SCALE", "2"));
        String[] box = System.getenv().getOrDefault("REACH_ATLAS_BOX", ReachTerrain.MIN_X + "," + ReachTerrain.MIN_Z + "," + ReachTerrain.MAX_X + "," + ReachTerrain.MAX_Z).split(",");
        var image = ReachAtlas.render(TERRAIN, Integer.parseInt(box[0]), Integer.parseInt(box[1]), Integer.parseInt(box[2]), Integer.parseInt(box[3]), scale);
        ImageIO.write(image, "png", new File(target));
    }
}
