package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.paper.item.EquipmentMaterials;
import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Build-defining enchantments bound one per item. Not to be confused with the catalog-bound
 * {@link net.nerdsmp.paper.item.SignatureAffix}, which belongs to specific relic items.
 */
public enum ArcaneSignature {
    STORMWAKE("stormwake", "Stormwake", Material.LIGHTNING_ROD,
        EnumSet.of(ArcaneAspect.MIGHT, ArcaneAspect.CELERITY, ArcaneAspect.AFFINITY), "Melee weapons",
        "Full-strength hits build Static Charge. At full charge, your next hit arcs lightning to nearby enemies."),
    HEART_OF_THE_MOUNTAIN("heart_of_the_mountain", "Heart of the Mountain", Material.DEEPSLATE_DIAMOND_ORE,
        EnumSet.of(ArcaneAspect.FORTUNE, ArcaneAspect.RESOLVE, ArcaneAspect.AFFINITY), "Pickaxes",
        "Mining natural ore builds Resonance. At full Resonance, the next natural ore drops its loot twice."),
    LAST_BASTION("last_bastion", "Last Bastion", Material.TOTEM_OF_UNDYING,
        EnumSet.of(ArcaneAspect.AEGIS, ArcaneAspect.VITALITY, ArcaneAspect.RESOLVE), "Armor",
        "A blow that would kill you leaves you at one health with brief protection instead. Long cooldown.");

    private final String id;
    private final String displayName;
    private final Material icon;
    private final Set<ArcaneAspect> aspects;
    private final String equipment;
    private final String summary;

    ArcaneSignature(String id, String displayName, Material icon, Set<ArcaneAspect> aspects, String equipment, String summary) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.aspects = Set.copyOf(aspects);
        this.equipment = equipment;
        this.summary = summary;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public Set<ArcaneAspect> aspects() {
        return aspects;
    }

    public String equipment() {
        return equipment;
    }

    public String summary() {
        return summary;
    }

    /** The config section under {@code arcane-forge.signatures}. */
    public String configKey() {
        return id.replace('_', '-');
    }

    public boolean supports(Material material) {
        if (material == null || material.isAir()) return false;
        return switch (this) {
            case STORMWAKE -> EquipmentMaterials.isMeleeWeapon(material);
            case HEART_OF_THE_MOUNTAIN -> material.name().endsWith("_PICKAXE");
            case LAST_BASTION -> EquipmentMaterials.isArmor(material);
        };
    }

    public static Optional<ArcaneSignature> fromId(String id) {
        if (id == null) return Optional.empty();
        String wanted = id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (ArcaneSignature signature : values()) if (signature.id.equals(wanted)) return Optional.of(signature);
        return Optional.empty();
    }
}
