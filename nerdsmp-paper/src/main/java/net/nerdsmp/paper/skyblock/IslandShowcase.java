package net.nerdsmp.paper.skyblock;

import org.bukkit.Material;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.Locale;

/** Earned, deterministic display unlocks. Never an inventory item or a loot roll. */
public enum IslandShowcase {
    HEARTH("Skybound Ember", "FOUNDATION", 0, Material.LANTERN, NamedTextColor.GOLD),
    VERDANT("Verdant Heart", "RARE", 1, Material.FLOWERING_AZALEA, NamedTextColor.GREEN),
    ARCANE("Arcane Prism", "EPIC", 2, Material.AMETHYST_CLUSTER, NamedTextColor.LIGHT_PURPLE),
    CELESTIAL("Celestial Star", "LEGENDARY", 3, Material.NETHER_STAR, NamedTextColor.AQUA);

    private final String title, rarity;
    private final int chapter;
    private final Material material;
    private final NamedTextColor color;
    IslandShowcase(String title, String rarity, int chapter, Material material, NamedTextColor color) {
        this.title = title; this.rarity = rarity; this.chapter = chapter; this.material = material; this.color = color;
    }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String title() { return title; }
    public String rarity() { return rarity; }
    public int chapter() { return chapter; }
    public Material material() { return material; }
    public NamedTextColor color() { return color; }
    public boolean unlocked(int tier) { return tier >= chapter; }
    public static IslandShowcase parse(String id) {
        for (var choice : values()) if (choice.id().equals(id)) return choice;
        throw new IllegalArgumentException("Unknown showcase. Choose hearth, verdant, arcane or celestial.");
    }
    public static int certified(IslandService.Island island) {
        if (island.tier() >= IslandChapter.values().length) return 3;
        var chapter = IslandChapter.values()[island.tier()];
        return (int) chapter.requirements().keySet().stream()
            .filter(skill -> island.proofs().containsKey(island.tier() + ":" + skill.name())).count();
    }
    public static String progress(IslandService.Island island) {
        return island.tier() == 3 ? "All chapters complete" : certified(island) + "/3 milestones certified";
    }
}
