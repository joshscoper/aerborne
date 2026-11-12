package net.aerborne.client.protocol;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HudLayoutTest {
    @TempDir Path directory;

    @Test void anchoredElementsKeepTheirCornerAsTheScreenResizes() {
        HudLayout corner = new HudLayout(HudLayout.Anchor.TOP_RIGHT, -4, 4, 1, true);
        assertEquals(400 - 4 - 80, corner.left(400, 80));
        assertEquals(900 - 4 - 80, corner.left(900, 80));
        HudLayout bottom = new HudLayout(HudLayout.Anchor.BOTTOM, 0, -40, 1, true);
        assertEquals(200 - 30, bottom.left(400, 60));
        assertEquals(300 - 40 - 20, bottom.top(300, 20));
    }

    @Test void placingAnElementPicksTheAnchorOfItsThirdAndLandsWhereDropped() {
        int width = 480, height = 270;
        for (int[] spot : new int[][] {{4, 4}, {200, 10}, {400, 120}, {10, 240}, {220, 130}}) {
            HudLayout placed = HudLayout.at(spot[0], spot[1], 60, 20, width, height, 1, true);
            assertEquals(spot[0], placed.left(width, 60));
            assertEquals(spot[1], placed.top(height, 20));
        }
        assertEquals(HudLayout.Anchor.TOP_RIGHT, HudLayout.at(400, 4, 60, 20, width, height, 1, true).anchor());
        assertEquals(HudLayout.Anchor.CENTER, HudLayout.at(220, 130, 40, 20, width, height, 1, true).anchor());
    }

    @Test void roundTripsAndRejectsMalformedLayouts() throws Exception {
        Path file = directory.resolve("client.properties");
        ClientPreferences p = new ClientPreferences();
        p.layout.put("minimap", new HudLayout(HudLayout.Anchor.BOTTOM_RIGHT, -8, -30, 0.75F, false));
        p.save(file);
        assertEquals(p.layout, ClientPreferences.load(file).layout);

        Files.writeString(file, "layout.player=TOP_LEFT;1;2;99;true\nlayout.bad=NOWHERE;0;0;1;true\nlayout.Evil..=TOP;0;0;1;true\nlayout.x=TOP;a;0;1;true\n");
        ClientPreferences loaded = ClientPreferences.load(file);
        assertEquals(1, loaded.layout.size());
        assertEquals(HudLayout.MAX_SCALE, loaded.layout.get("player").scale());
    }
}
