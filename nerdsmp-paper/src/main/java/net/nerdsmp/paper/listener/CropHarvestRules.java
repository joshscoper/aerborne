package net.nerdsmp.paper.listener;

import java.util.Set;
import java.util.UUID;

/** Crop recognition is explicit: many non-crop blocks also expose an age property. */
public final class CropHarvestRules {
    private static final Set<String> CROPS = Set.of(
        "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA",
        "SWEET_BERRY_BUSH", "PITCHER_CROP"
    );

    private CropHarvestRules() {
    }

    public static boolean mature(String material, int age, int maximumAge) {
        return CROPS.contains(material) && maximumAge > 0 && age == maximumAge;
    }

    static String newHarvestOperation(String blockFingerprint) {
        return "crop:" + blockFingerprint + ":" + UUID.randomUUID();
    }
}
