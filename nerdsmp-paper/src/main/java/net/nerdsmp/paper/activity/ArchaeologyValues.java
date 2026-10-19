package net.nerdsmp.paper.activity;

import org.bukkit.Material;

final class ArchaeologyValues {
    private ArchaeologyValues() {
    }

    static int reputation(Material material) {
        if (material.name().endsWith("_POTTERY_SHERD")) {
            return 2;
        }
        return switch (material) {
            case MUSIC_DISC_RELIC -> 10;
            case SNIFFER_EGG -> 12;
            default -> 0;
        };
    }
}
