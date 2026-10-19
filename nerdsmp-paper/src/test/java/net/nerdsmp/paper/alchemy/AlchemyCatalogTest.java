package net.nerdsmp.paper.alchemy;

import org.bukkit.Material;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AlchemyCatalogTest {
    @Test
    void traditionalCatalogCoversEveryEffectBearingPaperPotionType() {
        Set<PotionType> effectBearingTypes = Arrays.stream(PotionType.values())
            .filter(type -> type != PotionType.WATER && type != PotionType.AWKWARD
                && type != PotionType.MUNDANE && type != PotionType.THICK)
            .collect(Collectors.toUnmodifiableSet());

        assertEquals(effectBearingTypes, TraditionalPotion.coveredTypes());
        assertEquals(20, TraditionalPotion.values().length);
    }

    @Test
    void everyTraditionalFamilySupportsEveryValidBottleAndStrengthCombination() {
        assertEquals(Set.of(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION),
            Arrays.stream(PotionForm.values()).map(PotionForm::material).collect(Collectors.toUnmodifiableSet()));
        for (TraditionalPotion recipe : TraditionalPotion.values()) {
            assertTrue(recipe.availableTypes().contains(recipe.baseType()));
            assertTrue(recipe.ingredients().contains(Material.GLASS_BOTTLE));
            assertFalse(recipe.ingredients().isEmpty());
            for (PotionVariant variant : PotionVariant.values()) {
                assertEquals(variant.supports(recipe.baseType()), variant.resolve(recipe.baseType()).isPresent(),
                    recipe.name() + " " + variant.name());
            }
        }
    }

    @Test
    void arcaneBrewsIncludeOpposingPersistentSizeChanges() {
        assertEquals(8, ArcaneBrew.values().length);
        assertTrue(ArcaneBrew.GROWTH.scaleModifier() > 0.0D);
        assertTrue(ArcaneBrew.DIMINUTION.scaleModifier() < 0.0D);
        assertEquals(ArcaneBrew.GROWTH.durationTicks(), ArcaneBrew.DIMINUTION.durationTicks());
        for (ArcaneBrew brew : ArcaneBrew.values()) {
            assertTrue(brew.ingredients().contains(Material.GLASS_BOTTLE));
            assertTrue(brew.effectCount() > 0);
            assertTrue(brew.durationTicks() > 0);
        }
    }

    @Test
    void poisonCatalogProvidesEightDistinctCoatingsWithRealEffects() {
        assertEquals(8, PoisonType.values().length);
        assertEquals(8L, Arrays.stream(PoisonType.values()).map(PoisonType::id).distinct().count());
        for (PoisonType poison : PoisonType.values()) {
            assertFalse(poison.ingredients().isEmpty());
            assertTrue(poison.effectCount() > 0);
        }
    }

    @Test
    void poisonsLastLongEnoughToMatterAndPotencyImprovesThem() {
        for (PoisonType poison : PoisonType.values()) {
            int tierOneDuration = poison.maximumDurationTicks(1, 0.0D);
            int tierThreeDuration = poison.maximumDurationTicks(3, 0.0D);

            assertTrue(tierOneDuration >= 280, poison.name() + " is too brief to affect a fight");
            assertTrue(tierThreeDuration > tierOneDuration, poison.name() + " does not improve with potency");
        }
        assertTrue(PoisonType.MINDFOG.effectCount() >= 3);
        assertTrue(PoisonType.SUNDERING_ACID.effectCount() >= 3);
    }
}
