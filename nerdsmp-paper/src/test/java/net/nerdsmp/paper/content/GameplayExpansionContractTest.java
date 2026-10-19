package net.nerdsmp.paper.content;

import net.nerdsmp.paper.forge.ArcaneEnchantment;
import net.nerdsmp.paper.item.EquipmentMaterials;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GameplayExpansionContractTest {
    @Test
    void activityAlchemyAndWatchdogCommandsArePublished() throws IOException {
        String plugin = resource("/plugin.yml");
        for (String command : List.of(
            "alchemy", "fishing", "excavation", "watchdog", "identify", "bank", "repairall"
        )) {
            assertTrue(plugin.contains("  " + command + ":"), "Missing /" + command);
        }
        assertTrue(plugin.contains("nerdsmp.staff.anticheat:"));
        assertTrue(plugin.contains("nerdsmp.anticheat.bypass:"));
    }

    @Test
    void customEnchantmentsAreACompleteLevelFiveCatalog() {
        // The original twelve keep their order (stored strings sort by it); three effect enchantments follow.
        assertEquals(15, ArcaneEnchantment.values().length);
        assertEquals(java.util.List.of("keen_edge", "warding", "scholar", "prospector", "preservation", "reaping", "soulsiphon",
            "executioner", "angler", "archaeologist", "marksman", "venombound", "bloodletter", "veinseeker", "chain_harvest"),
            Arrays.stream(ArcaneEnchantment.values()).map(ArcaneEnchantment::id).toList());
        assertTrue(Arrays.stream(ArcaneEnchantment.values()).allMatch(enchantment -> enchantment.maximumLevel() == 5));
    }

    @Test
    void everySupportedEquipmentMaterialHasUsefulEnchantments() {
        for (Material material : Material.values()) {
            if (!EquipmentMaterials.isEquipment(material)) {
                continue;
            }
            long compatible = Arrays.stream(ArcaneEnchantment.values())
                .filter(enchantment -> enchantment.supports(material)).count();
            assertTrue(compatible >= 2, material + " only has " + compatible + " compatible enchantments");
        }

        assertTrue(ArcaneEnchantment.KEEN_EDGE.supports(Material.MACE));
        assertTrue(ArcaneEnchantment.KEEN_EDGE.supports(Material.NETHERITE_SPEAR));
        assertTrue(ArcaneEnchantment.MARKSMAN.supports(Material.CROSSBOW));
        assertTrue(ArcaneEnchantment.EXECUTIONER.supports(Material.BOW));
        assertTrue(ArcaneEnchantment.REAPING.supports(Material.SHEARS));
        assertTrue(ArcaneEnchantment.WARDING.supports(Material.ELYTRA));
        assertEquals(25.0D, ArcaneEnchantment.MARKSMAN.stats(5).get("projectile-damage-percent"));
    }

    @Test
    void autonomousThresholdsAndPlayerTipsAreExplicit() throws IOException {
        String config = resource("/config.yml");
        assertTrue(config.contains("mode: autonomous"));
        assertTrue(config.contains("alert-score: 12"));
        assertTrue(config.contains("setback-score: 24"));
        assertTrue(config.contains("kick-score: 50"));
        assertTrue(config.contains("/alchemy"));
        assertTrue(config.contains("/fishing"));
        assertTrue(config.contains("default-slots: 9"));
        assertTrue(config.contains("maximum-slots: 54"));
        assertTrue(config.contains("slot-cost-increment-marks:"));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
