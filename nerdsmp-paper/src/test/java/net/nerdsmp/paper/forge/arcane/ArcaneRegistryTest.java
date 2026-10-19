package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.paper.forge.ArcaneEnchantment;
import net.nerdsmp.paper.forge.GemType;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcaneRegistryTest {
    private final ArcaneRegistry registry = ArcaneRegistry.defaults();

    @Test
    void everyExistingEnchantmentKeepsItsIdLevelAndDescription() {
        for (ArcaneEnchantment enchantment : ArcaneEnchantment.values()) {
            ArcaneDefinition definition = registry.of(enchantment);
            assertEquals(enchantment.id(), definition.id());
            assertEquals(enchantment.maximumLevel(), definition.maximumLevel());
            assertEquals(enchantment.description(), definition.summary());
            assertEquals(enchantment, definition.enchantment());
            for (Material material : Material.values()) {
                if (!material.isLegacy()) assertEquals(enchantment.supports(material), definition.supports(material),
                    enchantment + " on " + material);
            }
        }
        assertEquals(ArcaneEnchantment.values().length + ArcaneSignature.values().length, registry.all().size());
    }

    @Test
    void everyAspectSetResolvesToAtMostOneDefinition() {
        Set<String> keys = new HashSet<>();
        for (ArcaneDefinition definition : registry.all()) assertTrue(keys.add(definition.aspectKey()), definition.id());
        assertTrue(ArcaneRegistry.validate(registry.all()).isEmpty());
    }

    @Test
    void signaturesUseTheAspectsFromTheDesign() {
        assertEquals(EnumSet.of(ArcaneAspect.MIGHT, ArcaneAspect.CELERITY, ArcaneAspect.AFFINITY),
            registry.of(ArcaneSignature.STORMWAKE).aspects());
        assertEquals(EnumSet.of(ArcaneAspect.FORTUNE, ArcaneAspect.RESOLVE, ArcaneAspect.AFFINITY),
            registry.of(ArcaneSignature.HEART_OF_THE_MOUNTAIN).aspects());
        assertEquals(EnumSet.of(ArcaneAspect.AEGIS, ArcaneAspect.VITALITY, ArcaneAspect.RESOLVE),
            registry.of(ArcaneSignature.LAST_BASTION).aspects());
        assertEquals("Rending", registry.of(ArcaneEnchantment.EXECUTIONER).discipline());
    }

    @Test
    void validationRejectsAmbiguousRecipesAndWrongAspectCounts() {
        List<ArcaneDefinition> definitions = new ArrayList<>(ArcaneRegistry.defaultDefinitions());
        ArcaneDefinition keen = definitions.getFirst();
        definitions.add(new ArcaneDefinition("copycat", "Copycat", ArcaneTier.FUNDAMENTAL, keen.aspects(), 5, "x", "x",
            null, Material.STONE, ArcaneEnchantment.KEEN_EDGE, null));
        definitions.add(new ArcaneDefinition("lopsided", "Lopsided", ArcaneTier.SPECIALIZED, EnumSet.of(ArcaneAspect.WARD),
            5, "x", "x", null, Material.STONE, ArcaneEnchantment.KEEN_EDGE, null));
        List<String> errors = ArcaneRegistry.validate(definitions);
        assertTrue(errors.stream().anyMatch(error -> error.contains("ambiguous")), errors.toString());
        assertTrue(errors.stream().anyMatch(error -> error.contains("lopsided has 1 Aspects")), errors.toString());
        assertThrows(IllegalArgumentException.class, () -> new ArcaneRegistry(definitions));
    }

    @Test
    void everyAspectCanBeResearchedWithAnOrdinaryGem() {
        for (ArcaneAspect aspect : ArcaneAspect.values()) assertFalse(ArcaneAspect.gemsFor(aspect).isEmpty(), aspect.name());
        assertTrue(ArcaneAspect.ofGem(GemType.PRISMATIC).isEmpty());
        assertEquals("celerity+might", ArcaneAspect.key(EnumSet.of(ArcaneAspect.MIGHT, ArcaneAspect.CELERITY)));
    }

    @Test
    void supersetsAndExactMatchesAreDeterministic() {
        assertEquals("keen_edge", registry.exact(EnumSet.of(ArcaneAspect.MIGHT)).orElseThrow().id());
        assertTrue(registry.exact(EnumSet.of(ArcaneAspect.WARD)).isEmpty());
        var supersets = registry.supersets(EnumSet.of(ArcaneAspect.MIGHT, ArcaneAspect.CELERITY));
        assertEquals(List.of("stormwake"), supersets.stream().map(ArcaneDefinition::id).toList());
    }
}
