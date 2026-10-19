package net.nerdsmp.paper.forge.arcane;

import java.util.Locale;

/** How an enchantment is classified in the Codex and in research. The tier fixes how many Aspects it has. */
public enum ArcaneTier {
    FUNDAMENTAL("Fundamental", "<aqua>", 1, "Reliable, always-on bonuses."),
    SPECIALIZED("Specialized", "<light_purple>", 2, "Bonuses that shine in a particular situation."),
    SIGNATURE("Arcane Signature", "<gradient:#ff70f6:#67e8f9>", 3, "Rare, build-defining effects. One per item.");

    private final String displayName;
    private final String color;
    private final int aspectCount;
    private final String description;

    ArcaneTier(String displayName, String color, int aspectCount, String description) {
        this.displayName = displayName;
        this.color = color;
        this.aspectCount = aspectCount;
        this.description = description;
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

    public int aspectCount() {
        return aspectCount;
    }

    public String description() {
        return description;
    }

    public static ArcaneTier forAspectCount(int count) {
        for (ArcaneTier tier : values()) if (tier.aspectCount == count) return tier;
        throw new IllegalArgumentException("No tier has " + count + " Aspects");
    }
}
