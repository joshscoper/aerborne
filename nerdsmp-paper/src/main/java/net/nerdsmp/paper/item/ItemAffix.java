package net.nerdsmp.paper.item;

import java.util.Locale;
import java.util.Map;

public enum ItemAffix {
    BRUTAL("Brutal", "damage-percent", 2.0D, 6.0D, "Damage", Requirement.DAMAGE),
    KEEN("Keen", "damage-flat", 0.5D, 2.5D, "Damage", Requirement.DAMAGE),
    STALWART("Stalwart", "defense", 1.0D, 5.0D, "Defense", Requirement.DEFENSE),
    SCHOLARLY("Scholarly", "skill-xp-percent", 2.0D, 7.0D, "Skill XP", Requirement.ANY),
    PROSPECTING("Prospecting", "mining-power", 1.0D, 4.0D, "Mining Power", Requirement.MINING),
    BOUNTIFUL("Bountiful", "gathering-power", 1.0D, 4.0D, "Gathering Power", Requirement.GATHERING),
    TIDECALLED("Tidecalled", "fishing-power", 1.0D, 4.0D, "Fishing Power", Requirement.FISHING),
    FORTUNATE("Fortunate", "treasure-find-percent", 3.0D, 12.0D, "Treasure Find", Requirement.ANY),
    GEODEBOUND("Geodebound", "geode-find-percent", 4.0D, 15.0D, "Geode Find", Requirement.ANY),
    ENDURING("Enduring", "durability-save-percent", 5.0D, 18.0D, "Durability Save", Requirement.ANY);

    private final String displayName;
    private final String stat;
    private final double minimum;
    private final double maximum;
    private final String statName;
    private final Requirement requirement;

    ItemAffix(String displayName, String stat, double minimum, double maximum, String statName, Requirement requirement) {
        this.displayName = displayName;
        this.stat = stat;
        this.minimum = minimum;
        this.maximum = maximum;
        this.statName = statName;
        this.requirement = requirement;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public String stat() {
        return stat;
    }

    public boolean supports(Map<String, Double> baseStats) {
        return switch (requirement) {
            case ANY -> true;
            case DAMAGE -> baseStats.getOrDefault("damage", 0.0D) > 0.0D;
            case DEFENSE -> baseStats.getOrDefault("defense", 0.0D) > 0.0D;
            case MINING -> baseStats.containsKey("mining-power");
            case GATHERING -> baseStats.containsKey("gathering-power");
            case FISHING -> baseStats.containsKey("fishing-power");
        };
    }

    public double roll(double rawRoll) {
        double roll = Math.max(0.0D, Math.min(1.0D, rawRoll));
        return Math.round((minimum + (maximum - minimum) * roll) * 10.0D) / 10.0D;
    }

    public String effect(double value) {
        String shown = Math.rint(value) == value ? Long.toString((long) value)
            : String.format(Locale.ROOT, "%.1f", value);
        return "+" + shown + (stat.endsWith("-percent") ? "%" : "") + " " + statName;
    }

    public static ItemAffix fromId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private enum Requirement {
        ANY,
        DAMAGE,
        DEFENSE,
        MINING,
        GATHERING,
        FISHING
    }
}
