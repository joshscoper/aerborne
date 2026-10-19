package net.nerdsmp.api.model;

import java.util.Locale;

public enum Skill {
    MINING,
    FORESTRY,
    FARMING,
    FISHING,
    HUNTING,
    EXCAVATION,
    SMITHING,
    ALCHEMY,
    BUILDING,
    COOKING,
    ENCHANTING;

    public String displayName() {
        String value = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
