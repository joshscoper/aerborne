package net.nerdsmp.paper.reach;

import java.util.List;

/** The list of built features in the Reach. Each zone's kits add theirs here. */
final class ReachBuilds {
    private ReachBuilds() { }

    static List<ReachFeatures.Feature> all(ReachTerrain terrain) {
        List<ReachFeatures.Feature> all = new java.util.ArrayList<>(List.of(
            new ReachFeatures.Feature("highcrown", new ReachFeatures.Footprint(-215, 735, 215, 1175), HighcrownCity::build),
            new ReachFeatures.Feature("goldenreach", new ReachFeatures.Footprint(-445, 240, 235, 775), GoldenreachVale::build)
        ));
        all.addAll(ReachBridges.features(terrain));
        all.addAll(ReachLandmarks.features(terrain));
        return all;
    }
}
