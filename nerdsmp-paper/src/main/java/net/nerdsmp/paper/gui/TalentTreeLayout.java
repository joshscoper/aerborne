package net.nerdsmp.paper.gui;

import java.util.List;

/** Six-row inventory graph with ranked roots, shared crossroads, and a capstone. */
public final class TalentTreeLayout {
    private TalentTreeLayout() { }
    public static int root(int branch) { if (branch<0||branch>3) throw new IllegalArgumentException("Invalid branch"); return 10+branch*2; }
    public static int junction(int index) { if(index<0||index>3) throw new IllegalArgumentException("Invalid junction"); return index==3?40:29+index*2; }
    public record Edge(int from, int to, List<Integer> slots) { public Edge { slots=List.copyOf(slots); } }
    public static List<Edge> edges() { return List.of(
        new Edge(0,4,List.of(19,20)),new Edge(1,4,List.of(21)),
        new Edge(1,5,List.of(22)),new Edge(2,5,List.of(23)),
        new Edge(2,6,List.of(24)),new Edge(3,6,List.of(25)),
        new Edge(4,7,List.of(38,39)),new Edge(5,7,List.of()),new Edge(6,7,List.of(41,42)));
    }
    public static int islandNode(int column, int node) {
        if(column<0||column>1||node<0||node>3) throw new IllegalArgumentException("Invalid island graph position");
        return switch(node) {case 0->11+column*4;case 1->28+column*4;case 2->30+column*4;default->38+column*4;};
    }
    public static List<Integer> islandLinks(int column) { return List.of(19+column*4,20+column*4,21+column*4,37+column*4,39+column*4); }
}
