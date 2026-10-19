package net.nerdsmp.paper.item;

import java.util.Map;
import java.util.Objects;

/** Bounds apply to operator overrides too; a bad price must never wrap into a free action. */
public record LootProcessingRules(long baseMarks, long marksPerAffix, int dustPerAffix, Map<String, Integer> dustByRarity) {
    public static final Map<String, Integer> DEFAULT_DUST = Map.of(
        "COMMON", 2, "CRAFTED", 2, "UNCOMMON", 3, "RARE", 5,
        "EPIC", 8, "RELIC", 8, "LEGENDARY", 12, "MYTHIC", 20);

    public LootProcessingRules {
        if (baseMarks < 0 || baseMarks > 1_000_000_000L || marksPerAffix < 0 || marksPerAffix > 1_000_000_000L
            || dustPerAffix < 0 || dustPerAffix > 64) throw new IllegalArgumentException("Invalid loot processing rates");
        dustByRarity = Map.copyOf(dustByRarity);
        if (!dustByRarity.keySet().equals(DEFAULT_DUST.keySet())
            || dustByRarity.values().stream().anyMatch(value -> value < 1 || value > 1024))
            throw new IllegalArgumentException("Invalid scrap rarity rates");
    }

    public long cost(int affixes) {
        validateAffixes(affixes);
        return Math.addExact(baseMarks, Math.multiplyExact(marksPerAffix, affixes));
    }

    public int dust(String rarity, int affixes) {
        validateAffixes(affixes);
        return Math.addExact(Objects.requireNonNull(dustByRarity.get(rarity), "Unsupported scrap rarity"),
            Math.multiplyExact(dustPerAffix, affixes));
    }

    private static void validateAffixes(int affixes) {
        if (affixes < 0 || affixes > ItemAffix.values().length) throw new IllegalArgumentException("Invalid affix count");
    }
}
