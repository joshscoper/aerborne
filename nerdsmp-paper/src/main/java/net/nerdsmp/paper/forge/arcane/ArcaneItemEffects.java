package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.paper.forge.ArcaneEnchantment;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Which item-level Arcane effects are live, read by the shared stat pipeline in
 * {@link net.nerdsmp.paper.item.PaperItemService}. The coordinator publishes it whenever settings
 * change. Switching a feature off makes its item data dormant, never removed: turning it back on
 * restores exactly what the player had.
 */
public final class ArcaneItemEffects {
    private static final Map<ArcaneEnchantment, Set<ArcaneAspect>> ASPECTS = new EnumMap<>(ArcaneEnchantment.class);

    static {
        ArcaneRegistry registry = ArcaneRegistry.defaults();
        for (ArcaneEnchantment enchantment : ArcaneEnchantment.values()) ASPECTS.put(enchantment, registry.of(enchantment).aspects());
    }

    private static volatile boolean corruption;
    private static volatile boolean attunement;
    private static volatile int attunementBonusPercent;
    private static volatile int durabilityMultiplier = 1;

    private ArcaneItemEffects() {
    }

    static void publish(ArcaneSettings settings) {
        corruption = settings.enabled() && settings.corruption().enabled();
        attunement = settings.enabled() && settings.attunement().enabled();
        attunementBonusPercent = Math.max(0, Math.min(25, settings.attunement().bonusPercent()));
        durabilityMultiplier = Math.max(1, settings.corruption().durabilityMultiplier());
    }

    public static boolean corruptionActive() {
        return corruption;
    }

    public static boolean attunementActive() {
        return attunement;
    }

    /** The share, in percent, added to an attuned enchantment's own stat bonus. Capped at 25. */
    public static int attunementBonusPercent() {
        return attunementBonusPercent;
    }

    public static int durabilityMultiplier() {
        return durabilityMultiplier;
    }

    public static Set<ArcaneAspect> aspects(ArcaneEnchantment enchantment) {
        return ASPECTS.get(enchantment);
    }

    /**
     * An enchantment's stat value after attunement: raised by the bonus share only when the item is
     * attuned to one of that enchantment's Aspects. The base stats of the item are never scaled.
     */
    public static double attuned(ArcaneEnchantment enchantment, ArcaneAspect focus, double value) {
        if (!attunement || focus == null || !ASPECTS.get(enchantment).contains(focus)) return value;
        return value * (100 + attunementBonusPercent) / 100.0D;
    }
}
