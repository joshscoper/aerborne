package net.nerdsmp.paper.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DamageDisplayTypeTest {
    @Test
    void paperDamageKeysMapToDistinctRpgStyles() {
        assertEquals(DamageDisplayType.PHYSICAL, classify("player_attack", false));
        assertEquals(DamageDisplayType.PROJECTILE, classify("arrow", true));
        assertEquals(DamageDisplayType.FIRE, classify("fireball", true));
        assertEquals(DamageDisplayType.FROST, classify("freeze", false));
        assertEquals(DamageDisplayType.ARCANE, classify("indirect_magic", true));
        assertEquals(DamageDisplayType.POISON, classify("wither_skull", true));
        assertEquals(DamageDisplayType.BLAST, classify("player_explosion", false));
    }

    @Test
    void damageCauseProvidesAFallbackForCustomSources() {
        assertEquals(DamageDisplayType.ARCANE, DamageDisplayType.classify(
            "custom", "MAGIC", false
        ));
        assertEquals(DamageDisplayType.PROJECTILE, DamageDisplayType.classify(
            "custom", "CUSTOM", true
        ));
    }

    private DamageDisplayType classify(String key, boolean indirect) {
        return DamageDisplayType.classify(key, "CUSTOM", indirect);
    }
}
