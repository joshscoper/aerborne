package net.nerdsmp.paper.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The gemstone ladder and its sockets. Gems climb five grades (Rough, Flawed, Fine, Flawless, Perfect)
 * by merging two of a kind. Gear sockets are typed: weapons take combat gems, armour and shields take
 * defensive gems, tools take gathering gems, and legendary and better gear ends with one universal
 * socket. Utility gems fit any socket; Prismatic gems only a universal one.
 */
public final class SocketRules {
    private SocketRules() {}

    /** The highest grade: Perfect. Gems merge up to it. */
    public static final int MAXIMUM_TIER = 5;
    private static final String[] GRADES = {"Rough", "Flawed", "Fine", "Flawless", "Perfect"};

    public static boolean validTier(int tier) { return tier >= 1 && tier <= MAXIMUM_TIER; }

    /** Materials, currencies and consumables never inherit the catalog's default gear sockets. */
    public static int capacity(PaperItemService.GearSlot slot, int configured) {
        return slot == PaperItemService.GearSlot.NONE ? 0 : Math.clamp(configured, 0, 4);
    }

    public static int dustCost(int tier) {
        if (!validTier(tier)) throw new IllegalArgumentException("Gem tier must be between 1 and " + MAXIMUM_TIER);
        return 4 + tier * 4;
    }

    /** A gem tier's grade name: Rough (1) to Perfect (5). */
    public static String grade(int tier) {
        return GRADES[Math.max(1, Math.min(MAXIMUM_TIER, tier)) - 1];
    }

    /** What a socket accepts. */
    public enum SlotType {
        COMBAT("C", "Combat", "<red>"),
        DEFENSIVE("D", "Defensive", "<blue>"),
        GATHERING("G", "Gathering", "<gold>"),
        UNIVERSAL("U", "Universal", "<light_purple>");

        private final String symbol;
        private final String title;
        private final String color;

        SlotType(String symbol, String title, String color) {
            this.symbol = symbol;
            this.title = title;
            this.color = color;
        }

        public String symbol() { return symbol; }
        public String title() { return title; }
        public String color() { return color; }
    }

    private static final java.util.Set<String> GATHERING_STATS = java.util.Set.of("mining-power", "gathering-power", "fishing-power", "excavation-power");
    private static final java.util.Set<String> UNIVERSAL_RARITIES = java.util.Set.of("LEGENDARY", "MYTHIC", "RELIC");

    /**
     * The type of the socket at {@code index}: gathering gear (any gathering power) takes gathering
     * gems, armour and shields defensive gems, and other gear combat gems. Legendary, mythic and relic
     * gear with two or more sockets ends with a universal one.
     */
    public static SlotType slot(java.util.Map<String, Double> stats, boolean armour, String rarity, int index, int capacity) {
        if (capacity >= 2 && index == capacity - 1 && rarity != null && UNIVERSAL_RARITIES.contains(rarity.toUpperCase(java.util.Locale.ROOT)))
            return SlotType.UNIVERSAL;
        if (stats.keySet().stream().anyMatch(GATHERING_STATS::contains)) return SlotType.GATHERING;
        return armour ? SlotType.DEFENSIVE : SlotType.COMBAT;
    }

    /** Whether a gem of this category fits a socket of this type. */
    public static boolean fits(net.nerdsmp.paper.forge.GemType.Category category, SlotType slot) {
        return switch (category) {
            case PRISMATIC -> slot == SlotType.UNIVERSAL;
            case UTILITY -> true;
            case COMBAT -> slot == SlotType.COMBAT || slot == SlotType.UNIVERSAL;
            case DEFENSIVE -> slot == SlotType.DEFENSIVE || slot == SlotType.UNIVERSAL;
            case GATHERING -> slot == SlotType.GATHERING || slot == SlotType.UNIVERSAL;
        };
    }

    public static Optional<Removal> remove(List<PaperItemService.SocketedGem> sockets, int index,
                                           PaperItemService.SocketedGem expected) {
        if (sockets == null || expected == null || index < 0 || index >= sockets.size()
            || !expected.equals(sockets.get(index)) || expected.type() == null
            || !validTier(expected.tier())) return Optional.empty();
        List<PaperItemService.SocketedGem> remaining = new ArrayList<>(sockets);
        PaperItemService.SocketedGem recovered = remaining.remove(index);
        return Optional.of(new Removal(recovered, remaining));
    }

    public record Removal(PaperItemService.SocketedGem recovered, List<PaperItemService.SocketedGem> remaining) {
        public Removal { remaining = List.copyOf(remaining); }
    }
}
