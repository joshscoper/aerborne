package net.nerdsmp.paper.alchemy;

import org.bukkit.Material;
import java.util.*;

/** Save one actual non-container reagent, never a carrier bottle, bucket, potion or arrow. */
public final class ReagentSavings {
    private ReagentSavings() { }
    public static Optional<Material> choose(List<Material> ingredients,double chancePercent,double roll) {
        if(!Double.isFinite(chancePercent)||!Double.isFinite(roll)||roll<0||roll>=1) return Optional.empty();
        if(roll>=Math.clamp(chancePercent,0,85)/100.0) return Optional.empty();
        return ingredients.stream().filter(material->!carrier(material)).findFirst();
    }
    private static boolean carrier(Material material) {
        String name=material.name();
        return name.endsWith("BUCKET")||name.endsWith("POTION")||name.endsWith("ARROW")
            ||Set.of("GLASS_BOTTLE","HONEY_BOTTLE","DRAGON_BREATH","AIR").contains(name);
    }
}
