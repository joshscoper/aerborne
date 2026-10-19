package net.nerdsmp.paper.activity;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ArchaeologyValuesTest {
    @Test
    void acceptsEveryPotterySherdAndExceptionalVanillaFinds() {
        for (Material material : Material.values()) {
            if (material.name().endsWith("_POTTERY_SHERD")) {
                assertEquals(2, ArchaeologyValues.reputation(material), material.name());
            }
        }
        assertEquals(10, ArchaeologyValues.reputation(Material.MUSIC_DISC_RELIC));
        assertEquals(12, ArchaeologyValues.reputation(Material.SNIFFER_EGG));
        assertEquals(0, ArchaeologyValues.reputation(Material.DIRT));
        assertEquals(0, ArchaeologyValues.reputation(Material.AMETHYST_SHARD));
    }
}
