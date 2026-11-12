package net.nerdsmp.paper.item;

import net.nerdsmp.paper.engine.ItemStats;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStatRulesTest {
    private final PlayerStatRules.HealthRules rules = new PlayerStatRules.HealthRules(20, 2, 512);
    @Test void healthGrowsFromProgressionArmorAndExplicitBonusesWhileHeartsStayCompact() {
        assertEquals(20, rules.health(1, 0, 0).maximum());
        var health = rules.health(25, 24, 10);
        assertEquals(48, health.progression());
        assertEquals(24, health.equipment());
        assertEquals(101.2, health.maximum(), 1E-9);
        assertEquals(20, PlayerStatRules.HEART_DISPLAY);
        assertEquals(512, rules.health(100, 1000, 100).maximum());
    }
    @Test void onlyArmorHasDefaultItemLevelVitalityAndQualityScalesOnlyTheFlatStat() {
        assertEquals(6, PlayerStatRules.armorHealth(PaperItemService.GearSlot.CHEST, 24));
        assertEquals(0, PlayerStatRules.armorHealth(PaperItemService.GearSlot.HEAD, 0));
        assertEquals(0, PlayerStatRules.armorHealth(PaperItemService.GearSlot.MAIN_HAND, 100));
        assertEquals(0, PlayerStatRules.armorHealth(PaperItemService.GearSlot.NONE, 100));
        assertTrue(GearQuality.REFINED.scales("max-health"));
        assertFalse(GearQuality.REFINED.scales("max-health-percent"));
        assertEquals(Map.of("max-health", 30.0, "max-health-percent", 5.0), ItemStats.validate(Map.of("max-health",30.0,"max-health-percent",5.0),false));
    }
    @Test void gearChangesCannotHealAndHealthIsClampedWhenMaximumFalls() {
        assertEquals(15, PlayerStatRules.adjustedHealth(15, 20, 100, false));
        assertEquals(40, PlayerStatRules.adjustedHealth(90, 100, 40, false));
        assertEquals(75, PlayerStatRules.adjustedHealth(15, 20, 100, true));
        assertEquals(15, PlayerStatRules.adjustedHealth(75, 100, 20, true));
        assertEquals(0, PlayerStatRules.adjustedHealth(0, 100, 20, true));
    }
    @Test void defenseAndEffectiveHealthMatchTheActualCombatFormula() {
        assertEquals(0, PlayerStatRules.defenseReduction(0));
        assertEquals(50, PlayerStatRules.defenseReduction(100));
        assertEquals(200, PlayerStatRules.effectiveHealth(100, 100));
        assertEquals(100 * (1 - PlayerStatRules.defenseReduction(35) / 100), GearMath.mitigateDamage(100,35),1E-9);
    }
    @Test void invalidConfigurationAndNonFiniteEquipmentCannotPoisonAttributes() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerStatRules.HealthRules(0,2,512));
        assertThrows(IllegalArgumentException.class, () -> new PlayerStatRules.HealthRules(20,Double.NaN,512));
        assertThrows(IllegalArgumentException.class, () -> new PlayerStatRules.HealthRules(20,2,2048));
        assertEquals(20, rules.health(1, Double.NaN, Double.POSITIVE_INFINITY).maximum());
    }
}
