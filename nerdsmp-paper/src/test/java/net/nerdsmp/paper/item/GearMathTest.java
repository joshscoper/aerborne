package net.nerdsmp.paper.item;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GearMathTest {
    @Test
    void damageUsesOnlyCustomBaseBonusesAndAttackCooldown() {
        assertEquals(12.0D, GearMath.calculateDamage(9.0D, 1.0D, 20.0D, 1.0D), 0.0001D);
        assertEquals(2.4D, GearMath.calculateDamage(9.0D, 1.0D, 20.0D, 0.0D), 0.0001D);
        assertEquals(12.0D, GearMath.calculateDamage(9.0D, 1.0D, 20.0D, 2.0D), 0.0001D);
    }

    @Test
    void defenseUsesAStableDiminishingReductionCurve() {
        assertEquals(10.0D, GearMath.mitigateDamage(10.0D, 0.0D), 0.0001D);
        assertEquals(8.0D, GearMath.mitigateDamage(10.0D, 25.0D), 0.0001D);
        assertEquals(5.0D, GearMath.mitigateDamage(10.0D, 100.0D), 0.0001D);
    }

    @Test
    void pveTalentBonusAndPlayerDefenseUseTheSameCombatPipelineAsLiveHits() {
        double customWeaponHit = GearMath.calculateDamage(9.0D, 1.0D, 20.0D, 1.0D);

        assertEquals(13.8D, GearMath.applyPercentBonus(customWeaponHit, 15.0D), 0.0001D);
        assertEquals(20.7D, GearMath.applyCritical(
            GearMath.applyPercentBonus(customWeaponHit, 15.0D), true, 1.5D
        ), 0.0001D);
        assertEquals(13.8D, GearMath.applyCritical(
            GearMath.applyPercentBonus(customWeaponHit, 15.0D), false, 1.5D
        ), 0.0001D);
        assertEquals(9.6D, GearMath.mitigateDamage(customWeaponHit, 25.0D), 0.0001D);
        assertEquals(0.0D, GearMath.applyPercentBonus(customWeaponHit, -200.0D), 0.0001D);
    }

    @Test
    void tooltipDamageIncludesTheItemsFlatAndPercentUpgradesAtFullCharge() {
        assertEquals(12.0D, PaperItemService.displayedItemDamage(Map.of(
            "damage", 9.0D,
            "damage-flat", 1.0D,
            "damage-percent", 20.0D
        )), 0.0001D);
    }
}
