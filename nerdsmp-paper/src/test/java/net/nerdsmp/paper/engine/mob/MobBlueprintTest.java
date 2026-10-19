package net.nerdsmp.paper.engine.mob;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MobBlueprintTest {
    static MobBlueprint mob(String id) {
        return new MobBlueprint(id, "guardian", "Crypt Guardian", "husk", MobBlueprint.Rank.ELITE,
            12, 64, 8, 0.23, MobBlueprint.Ability.PULSE, 4, 6, 15, 20, "");
    }
    @Test void modifiersAndCopiesKeepUnchangedFieldsAndOriginalImmutable() {
        MobBlueprint original = mob("crypt_guardian");
        MobBlueprint next = original.renamed("crypt_mender").withName("Crypt Mender").withAbility(MobBlueprint.Ability.HEAL_SELF)
            .withAbilityPower(8).withLootProfile("crypt_drops");
        assertEquals("crypt_guardian", original.id()); assertEquals(MobBlueprint.Ability.PULSE, original.ability());
        assertEquals("crypt_mender", next.id()); assertEquals("Crypt Mender", next.displayName());
        assertEquals(original.health(), next.health()); assertEquals(original.experienceReward(), next.experienceReward());
        assertEquals("crypt_drops", next.lootProfile());
    }
    @Test void identifiersCannotEscapeYamlPathsOrNamespaces() {
        for (String id : List.of("bad.id", "../../bad", "custom:bad", "Bad", "", "a".repeat(49)))
            assertThrows(IllegalArgumentException.class, () -> mob(id), id);
        assertThrows(IllegalArgumentException.class, () -> mob("good").withLootProfile("bad.profile"));
        assertEquals("", mob("good").withLootProfile(null).lootProfile());
    }
    @Test void namesArePlainTextNotCodeOrImplicitRegistryIdentity() {
        assertEquals("<red>The Guardian</red>", mob("guardian").withName("<red>The Guardian</red>").name());
        for (String name : List.of("", " ", "a\nb", "a\tb", "a".repeat(65)))
            assertThrows(IllegalArgumentException.class, () -> mob("good").withName(name));
    }
    @Test void reviewedGroundTypesOnlyNoPlayersBossesUtilityOrFlyingHabitats() {
        for (String type : List.of("player", "armor_stand", "ender_dragon", "wither", "warden", "blaze", "vex", "not_real", "ZOMBIE"))
            assertThrows(IllegalArgumentException.class, () -> mob("good").withType(type), type);
        assertEquals("skeleton", mob("good").withType("skeleton").type());
    }
    @Test void boundedLevelsExperienceAndCooldownsRejectCoercion() {
        for (int level : List.of(0, 101, Integer.MAX_VALUE)) assertThrows(IllegalArgumentException.class, () -> mob("good").withLevel(level));
        for (int xp : List.of(-1, 1001)) assertThrows(IllegalArgumentException.class, () -> mob("good").withExperienceReward(xp));
        for (int seconds : List.of(0, 4, 301)) assertThrows(IllegalArgumentException.class, () -> mob("good").withAbilityCooldownSeconds(seconds));
        assertEquals(100, mob("good").withLevel(100).level());
        assertEquals(0, mob("good").withExperienceReward(0).experienceReward());
    }
    @Test void finiteDamageHealthSpeedAndAbilitiesOnly() {
        for (double bad : List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1D)) {
            assertThrows(IllegalArgumentException.class, () -> mob("good").withHealth(bad));
            assertThrows(IllegalArgumentException.class, () -> mob("good").withAttackDamage(bad));
            assertThrows(IllegalArgumentException.class, () -> mob("good").withMovementSpeed(bad));
            assertThrows(IllegalArgumentException.class, () -> mob("good").withAbilityRadius(bad));
            assertThrows(IllegalArgumentException.class, () -> mob("good").withAbilityPower(bad));
        }
        assertThrows(IllegalArgumentException.class, () -> mob("good").withHealth(1025));
        assertThrows(IllegalArgumentException.class, () -> mob("good").withAttackDamage(101));
        assertThrows(IllegalArgumentException.class, () -> mob("good").withMovementSpeed(0.81));
        assertThrows(IllegalArgumentException.class, () -> mob("good").withAbilityRadius(13));
        assertThrows(IllegalArgumentException.class, () -> mob("good").withAbilityPower(101));
        assertThrows(IllegalArgumentException.class, () -> mob("good").withAbility(null));
        assertThrows(IllegalArgumentException.class, () -> mob("good").withRank(null));
    }
}
