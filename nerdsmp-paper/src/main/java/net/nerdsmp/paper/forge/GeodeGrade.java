package net.nerdsmp.paper.forge;

import java.util.Locale;

public enum GeodeGrade {
    ROUGH("Rough Geode", "<gray>", "Common"),
    RESONANT("Resonant Geode", "<aqua>", "Uncommon"),
    LUMINOUS("Luminous Geode", "<light_purple>", "Rare");

    private final String displayName;
    private final String color;
    private final String rarity;

    GeodeGrade(String displayName, String color, String rarity) {
        this.displayName = displayName;
        this.color = color;
        this.rarity = rarity;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public String color() {
        return color;
    }

    public String rarity() {
        return rarity;
    }

    public static GeodeGrade fromId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
