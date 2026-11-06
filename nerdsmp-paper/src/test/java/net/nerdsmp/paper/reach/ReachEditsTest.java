package net.nerdsmp.paper.reach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hand edits survive a save and a reload, and every flight point has a roost to ring. */
class ReachEditsTest {
    @Test
    void editsSurviveASaveAndAReload(@TempDir File dir) {
        File file = new File(dir, "reach-edits.yml");
        ReachEdits edits = new ReachEdits(file, null);
        edits.put(new ReachEdits.Edit(-12, 101, 7, "minecraft:lantern[hanging=true,waterlogged=false]", "minecraft:air", null));
        edits.put(new ReachEdits.Edit(3, -4, -900, "minecraft:oak_wall_sign[facing=north,waterlogged=false]", "minecraft:stone",
            List.of("THE", "MILL", "", "Open")));
        // Putting back what the generator made is no edit at all.
        edits.put(new ReachEdits.Edit(5, 90, 5, "minecraft:stone", "minecraft:stone", null));
        edits.save();
        ReachEdits again = new ReachEdits(file, null);
        assertEquals(2, again.size());
        assertEquals("minecraft:lantern[hanging=true,waterlogged=false]", again.get(-12, 101, 7).after());
        assertEquals(List.of("THE", "MILL", "", "Open"), again.get(3, -4, -900).lines());
        assertEquals("minecraft:stone", again.get(3, -4, -900).before());
        assertNull(again.get(5, 90, 5));
    }

    @Test
    void everyFlightPointHasARoost() {
        ReachTerrain terrain = new ReachTerrain();
        ReachFeatures features = new ReachFeatures(terrain, ReachBuilds.all(terrain));
        var spots = features.spots();
        for (ReachFlights.Point p : ReachFlights.POINTS) {
            boolean found = false;
            for (String choice : p.spots().split("\\|")) found |= spots.containsKey(choice);
            assertTrue(found, p.id() + " has no roost spot (" + p.spots() + ")");
        }
    }
}
