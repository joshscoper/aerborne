package net.nerdsmp.paper.skyblock;

import java.util.Arrays;
import java.util.List;

/** The Farmstead's crop milestone tiers, for tests in other packages. */
public final class IslandFarmsteadTiersProbe {
    private IslandFarmsteadTiersProbe() { }
    public static List<Integer> tiers() { return Arrays.stream(IslandFarmstead.TIERS).boxed().toList(); }
}
