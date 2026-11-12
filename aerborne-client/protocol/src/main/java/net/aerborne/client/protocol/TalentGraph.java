package net.aerborne.client.protocol;

import java.util.List;

/** Mirrors the current server TalentTreeLayout contract; never determines purchase eligibility. */
public final class TalentGraph {
    private TalentGraph() { }
    public static final List<Integer> NODES = List.of(10, 12, 14, 16, 29, 31, 33, 40);
    public static final List<Integer> CONNECTORS = List.of(19, 20, 21, 22, 23, 24, 25, 38, 39, 41, 42);
    public record Edge(int from, int to) { }
    public static final List<Edge> EDGES = List.of(new Edge(0, 4), new Edge(1, 4), new Edge(1, 5),
            new Edge(2, 5), new Edge(2, 6), new Edge(3, 6), new Edge(4, 7), new Edge(5, 7), new Edge(6, 7));
    public static boolean matches(String kind, String title, int slots) {
        return kind.endsWith("MenuManager$MenuHolder") && title.endsWith(" Talent Tree") && slots == 54;
    }
    public static MenuLayout.Box node(int i, int width) {
        if (i < 0 || i > 7) throw new IndexOutOfBoundsException(i);
        int row = i < 4 ? 0 : i < 7 ? 1 : 2;
        int columns = row == 0 ? 4 : row == 1 ? 3 : 1;
        int index = row == 0 ? i : row == 1 ? i - 4 : 0;
        int w = row == 0 ? (width - 52) / 4 : row == 1 ? (width - 44) / 3 : 200;
        int grid = columns * w + (columns - 1) * 8;
        return new MenuLayout.Box((width - grid) / 2 + index * (w + 8), 96 + row * 76, w, 52);
    }
}
