package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.paper.forge.GemType;
import org.bukkit.Material;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The nine magical disciplines. An Aspect is a classification for research and the Codex: it groups
 * existing enchantments and socket gems by what they already do. It is never stored on an item and
 * never changes a stat.
 */
public enum ArcaneAspect {
    MIGHT("Might", "<red>", Material.REDSTONE, "Offensive power."),
    PRECISION("Precision", "<light_purple>", Material.AMETHYST_SHARD, "Aim, finishing blows and ranged strikes."),
    AEGIS("Aegis", "<blue>", Material.LAPIS_LAZULI, "Physical protection."),
    WARD("Ward", "<white>", Material.QUARTZ, "Resistance to magic and affliction."),
    CELERITY("Celerity", "<aqua>", Material.PRISMARINE_CRYSTALS, "Speed of action and motion."),
    VITALITY("Vitality", "<dark_green>", Material.SLIME_BALL, "Health, growth and recovery."),
    RESOLVE("Resolve", "<dark_gray>", Material.ECHO_SHARD, "Endurance and lasting work."),
    AFFINITY("Affinity", "<green>", Material.EMERALD, "Elemental and arcane attunement."),
    FORTUNE("Fortune", "<gold>", Material.RAW_GOLD, "Rewards, finds and discovery.");

    private final String displayName;
    private final String color;
    private final Material icon;
    private final String description;

    ArcaneAspect(String displayName, String color, Material icon, String description) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.description = description;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    /** MiniMessage opening tag. */
    public String color() {
        return color;
    }

    public String colored() {
        return color + displayName + "</" + color.substring(1);
    }

    public Material icon() {
        return icon;
    }

    public String description() {
        return description;
    }

    /**
     * The research reagent for each gem. Prismatic gems have no single Aspect: they are a catalyst.
     * Every Aspect has at least one gem, so every discipline can be researched.
     */
    public static Optional<ArcaneAspect> ofGem(GemType gem) {
        if (gem == null) return Optional.empty();
        return Optional.ofNullable(switch (gem) {
            case RUBY -> MIGHT;
            case AMETHYST -> PRECISION;
            case SAPPHIRE -> AEGIS;
            case MOONSTONE -> WARD;
            case AQUAMARINE -> CELERITY;
            case JADE -> VITALITY;
            case ONYX -> RESOLVE;
            case EMERALD -> AFFINITY;
            case TOPAZ, CITRINE -> FORTUNE;
            case PRISMATIC -> null;
        });
    }

    public static Set<GemType> gemsFor(ArcaneAspect aspect) {
        return java.util.Arrays.stream(GemType.values())
            .filter(gem -> ofGem(gem).filter(aspect::equals).isPresent())
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(GemType.class)));
    }

    /** A stable, order-independent key for a set of Aspects, such as {@code celerity+might}. */
    public static String key(Collection<ArcaneAspect> aspects) {
        return aspects.stream().map(ArcaneAspect::id).sorted().collect(Collectors.joining("+"));
    }

    public static Optional<ArcaneAspect> fromId(String id) {
        if (id == null) return Optional.empty();
        try {
            return Optional.of(valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    public static String names(Collection<ArcaneAspect> aspects) {
        return aspects.stream().sorted().map(ArcaneAspect::colored).collect(Collectors.joining("<gray> + </gray>"));
    }
}
