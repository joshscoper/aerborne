package net.aerborne.client.protocol;

import java.util.List;

/**
 * Mirrors the server's island talent tree pages (TalentTreeLayout.islandNode/islandLinks): two branches a page, each a
 * root, two paths that fork from it, and a capstone either path opens. Never decides what can be learned.
 */
public final class IslandTalentGraph {
    private IslandTalentGraph() { }
    /** Node order: branch one root, left path, right path, capstone; then the same for branch two. */
    public static final List<Integer> NODES = List.of(11, 28, 30, 38, 15, 32, 34, 42);
    public static final List<Integer> CONNECTORS = List.of(19, 20, 21, 37, 39, 23, 24, 25, 41, 43);
    public static final List<TalentGraph.Edge> EDGES = List.of(
            new TalentGraph.Edge(0, 1), new TalentGraph.Edge(0, 2), new TalentGraph.Edge(1, 3), new TalentGraph.Edge(2, 3),
            new TalentGraph.Edge(4, 5), new TalentGraph.Edge(4, 6), new TalentGraph.Edge(5, 7), new TalentGraph.Edge(6, 7));

    public static boolean matches(String kind, String title, int slots) {
        return kind.contains("SkyblockController") && title.startsWith("Island Talent Tree") && slots == 54;
    }

    /** Each branch is a diamond in its half of the window: root on top, the two paths, the capstone below. */
    public static MenuLayout.Box node(int i, int width) {
        if (i < 0 || i > 7) throw new IndexOutOfBoundsException(i);
        int branch = i / 4, step = i % 4;
        int half = (width - 36) / 2, originX = 14 + branch * (half + 8);
        int w = Math.min(150, (half - 12) / 2);
        int row = step == 0 ? 0 : step == 3 ? 2 : 1;
        int x = step == 1 ? originX + half / 2 - w - 6 : step == 2 ? originX + half / 2 + 6 : originX + (half - w) / 2;
        return new MenuLayout.Box(x, 96 + row * 76, w, 52);
    }

    /** A path lights once the talent it leaves is at rank II: that's what opens the next one. */
    public static int lightsAt(int from) { return 2; }
}
