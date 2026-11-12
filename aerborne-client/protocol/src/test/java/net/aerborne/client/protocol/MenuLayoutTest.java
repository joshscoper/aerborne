package net.aerborne.client.protocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuLayoutTest {
    @Test void talentGraphKeepsEveryBranchAndConnection() {
        assertEquals(8, new java.util.HashSet<>(TalentGraph.NODES).size());
        for (int width : new int[]{540, 620}) for (int i = 0; i < 8; i++) {
            var box = TalentGraph.node(i, width);
            assertTrue(box.x() >= 14 && box.x() + box.width() <= width - 14);
            assertTrue(box.y() + box.height() <= 300);
        }
        for (var edge : TalentGraph.EDGES) {
            assertTrue(edge.from() < edge.to());
            assertTrue(edge.from() >= 0 && edge.to() < 8);
        }
        assertTrue(TalentGraph.matches("net.nerdsmp.paper.gui.MenuManager$MenuHolder", "Mining Talent Tree", 54));
        assertFalse(TalentGraph.matches("AuctionController$Page", "Mining Talent Tree", 54));
    }
    @Test void everyEntryIsReachableWithoutCrossingControls() {
        for (int width : new int[]{360, 427, 620}) for (int height : new int[]{280, 352, 420})
            for (int count : new int[]{0, 1, 18, 19, 21, 54}) for (int tools : new int[]{0, 5}) for (boolean table : new boolean[]{false, true}) {
                var layout = MenuLayout.of(width, height, tools, 9, count, table);
                int visited = 0;
                for (int page = 0; page < layout.pages(); page++)
                    for (int i = 0; i < layout.perPage() && page * layout.perPage() + i < count; i++) {
                        var b = layout.card(i);
                        assertTrue(b.x() >= 14 && b.x() + b.width() <= width - 14);
                        assertTrue(b.y() >= layout.bodyTop() && b.y() + b.height() <= layout.bodyBottom());
                        visited++;
                    }
                assertEquals(count, visited);
                assertEquals(0, layout.clampPage(-1));
                assertEquals(layout.pages() - 1, layout.clampPage(1000));
                for (int i = 0; i < 9; i++) {
                    var nav = layout.navigation(i);
                    assertTrue(nav.x() + nav.width() <= width - 14);
                    assertTrue(nav.y() + nav.height() < height - 28);
                }
            }
    }
    @Test void auctionSpecializationRequiresTheKnownServerMenu() {
        assertTrue(MenuLayout.auctionBrowse("net.nerdsmp.paper.market.AuctionController$Page", "Auction House", 54));
        assertTrue(MenuLayout.auctionBrowse("net.nerdsmp.paper.market.AuctionController$Page", "My Auctions", 54));
        assertFalse(MenuLayout.auctionBrowse("Unknown$Page", "Auction House", 54));
        assertFalse(MenuLayout.auctionBrowse("AuctionController$Page", "Auction 12345678", 54));
        assertFalse(MenuLayout.auctionBrowse("AuctionController$Page", "Auction House", 27));
    }
}
