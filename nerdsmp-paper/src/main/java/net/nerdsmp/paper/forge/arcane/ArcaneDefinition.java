package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.paper.forge.ArcaneEnchantment;
import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * One researchable enchantment. Exactly one of {@code enchantment} (an existing stat enchantment,
 * level 1-5) and {@code signature} (an Arcane Signature, which has no levels) is set.
 */
public record ArcaneDefinition(
    String id,
    String displayName,
    ArcaneTier tier,
    Set<ArcaneAspect> aspects,
    int maximumLevel,
    String summary,
    String equipment,
    String discipline,
    Material icon,
    ArcaneEnchantment enchantment,
    ArcaneSignature signature
) {
    public ArcaneDefinition {
        aspects = Set.copyOf(EnumSet.copyOf(aspects));
        if ((enchantment == null) == (signature == null)) {
            throw new IllegalArgumentException("A definition is either an enchantment or a signature: " + id);
        }
    }

    public String aspectKey() {
        return ArcaneAspect.key(aspects);
    }

    public boolean isSignature() {
        return signature != null;
    }

    public Optional<ArcaneEnchantment> enchantmentRef() {
        return Optional.ofNullable(enchantment);
    }

    public Optional<ArcaneSignature> signatureRef() {
        return Optional.ofNullable(signature);
    }

    public boolean supports(Material material) {
        return enchantment != null ? enchantment.supports(material) : signature.supports(material);
    }

    /** Tier colour and name, for lore. */
    public String title() {
        return tier.color() + displayName + "</" + closingTag(tier.color()) + ">";
    }

    private static String closingTag(String opening) {
        return opening.substring(1, opening.length() - 1).split(":", 2)[0];
    }
}
