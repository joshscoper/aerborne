package net.nerdsmp.paper.skyblock;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class IslandMobStackPolicyTest {
    private static IslandMobStackPolicy.Signature signature(String type, int level, double health, double damage) {
        return new IslandMobStackPolicy.Signature(type, level, Map.of("max_health", health, "attack_damage", damage),
            Map.of("mob_tier", "STANDARD", "mob_reward_multiplier", 1.0D), type.equals("SKELETON") ? "BOW" : "AIR");
    }

    @Test void supportsOrdinaryFarmHostilesButNeverPetsBossesOrTraitBearingAnimals() {
        for (String type : new String[]{"ZOMBIE", "HUSK", "DROWNED", "SKELETON", "STRAY", "SPIDER", "CAVE_SPIDER", "BLAZE",
            "CREEPER", "ENDERMAN", "ZOMBIFIED_PIGLIN", "WITCH", "BREEZE"})
            assertTrue(IslandMobStackPolicy.supported(type));
        for (String type : new String[]{"PLAYER", "WOLF", "COW", "SHEEP", "CHICKEN", "VILLAGER", "SLIME", "MAGMA_CUBE", "WITHER", "WARDEN"})
            assertFalse(IslandMobStackPolicy.supported(type));
    }
    @Test void ambientOriginsDoNotAdmitStaffCustomSpawnsBreedingOrSpawnEggs() {
        assertTrue(IslandMobStackPolicy.ambient("NATURAL"));
        assertTrue(IslandMobStackPolicy.ambient("CHUNK_GEN"));
        for (String reason : new String[]{"CUSTOM", "BREEDING", "SPAWNER_EGG", "SPAWNER", "COMMAND", "SLIME_SPLIT"})
            assertFalse(IslandMobStackPolicy.ambient(reason));
    }
    @Test void mergingRequiresTheSameIslandTypeLevelAndCombatScale() {
        UUID island = UUID.randomUUID();
        var ordinary = signature("ZOMBIE", 1, 20, 3);
        assertTrue(IslandMobStackPolicy.equivalent(island, island, ordinary, signature("ZOMBIE", 1, 20, 3), 36));
        assertFalse(IslandMobStackPolicy.equivalent(island, UUID.randomUUID(), ordinary, ordinary, 0));
        assertFalse(IslandMobStackPolicy.equivalent(island, island, ordinary, signature("HUSK", 1, 20, 3), 0));
        assertFalse(IslandMobStackPolicy.equivalent(island, island, ordinary, signature("ZOMBIE", 2, 20, 3), 0));
        assertFalse(IslandMobStackPolicy.equivalent(island, island, ordinary, signature("ZOMBIE", 1, 21, 3), 0));
        assertFalse(IslandMobStackPolicy.equivalent(island, island, ordinary, signature("ZOMBIE", 1, 20, 4), 0));
    }
    @Test void rewardScaleAndEquipmentAlsoBelongToTheSignature() {
        UUID island = UUID.randomUUID();
        var first = signature("SKELETON", 1, 20, 2);
        var rewards = new IslandMobStackPolicy.Signature("SKELETON", 1, first.attributes(),
            Map.of("mob_tier", "STANDARD", "mob_reward_multiplier", 2.0D), "BOW");
        var equipment = new IslandMobStackPolicy.Signature("SKELETON", 1, first.attributes(), first.scale(), "AIR");
        assertFalse(IslandMobStackPolicy.equivalent(island, island, first, rewards, 0));
        assertFalse(IslandMobStackPolicy.equivalent(island, island, first, equipment, 0));
    }
    @Test void rangeAndNumericChecksFailClosed() {
        UUID island = UUID.randomUUID(); var member = signature("ZOMBIE", 1, 20, 3);
        for (double distance : new double[]{-1, 36.01D, Double.NaN, Double.POSITIVE_INFINITY})
            assertFalse(IslandMobStackPolicy.equivalent(island, island, member, member, distance));
        assertThrows(IllegalArgumentException.class, () -> signature("COW", 1, 20, 0));
        assertThrows(IllegalArgumentException.class, () -> signature("ZOMBIE", 0, 20, 3));
        assertThrows(IllegalArgumentException.class, () -> signature("ZOMBIE", 101, 20, 3));
        assertThrows(IllegalArgumentException.class, () -> signature("ZOMBIE", 1, Double.NaN, 3));
    }
    @Test void stackAndChunkBudgetsCannotOverflowOrMultiplyRewards() {
        assertTrue(IslandMobStackPolicy.room(15, 1, 255));
        assertTrue(IslandMobStackPolicy.room(8, 8, 248));
        assertFalse(IslandMobStackPolicy.room(16, 1, 0));
        assertFalse(IslandMobStackPolicy.room(1, 16, 0));
        assertFalse(IslandMobStackPolicy.room(1, 1, 256));
        assertFalse(IslandMobStackPolicy.room(1, 1, Integer.MAX_VALUE));
        assertFalse(IslandMobStackPolicy.room(1, 1, -1));
        assertFalse(IslandMobStackPolicy.room(0, 1, 0));
        assertFalse(IslandMobStackPolicy.room(1, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(1, IslandMobStackPolicy.members(Integer.MIN_VALUE));
        assertEquals(16, IslandMobStackPolicy.members(Integer.MAX_VALUE));
    }
    @Test void signatureCopiesItsInputMaps() {
        var attributes = new java.util.HashMap<>(Map.of("max_health", 20.0D));
        var scale = new java.util.HashMap<String,Object>(Map.of("mob_tier", "STANDARD"));
        var member = new IslandMobStackPolicy.Signature("ZOMBIE", 1, attributes, scale, "AIR");
        attributes.put("max_health", 100.0D); scale.put("mob_tier", "BOSS");
        assertEquals(20.0D, member.attributes().get("max_health"));
        assertEquals("STANDARD", member.scale().get("mob_tier"));
        assertThrows(UnsupportedOperationException.class, () -> member.scale().put("mob_tier", "BOSS"));
    }
    @Test void crowdedChunksCanConsolidateWithoutImportingMoreMembers() {
        assertTrue(IslandMobStackPolicy.mergeRoom(8, 8, 256, true));
        assertTrue(IslandMobStackPolicy.mergeRoom(8, 8, 4_096, true));
        assertTrue(IslandMobStackPolicy.mergeRoom(15, 1, Integer.MAX_VALUE, true));
        assertFalse(IslandMobStackPolicy.mergeRoom(16, 1, 4_096, true));
        assertFalse(IslandMobStackPolicy.mergeRoom(1, 16, 4_096, true));
        assertFalse(IslandMobStackPolicy.mergeRoom(1, 1, -1, true));
        assertTrue(IslandMobStackPolicy.mergeRoom(8, 8, 248, false));
        assertFalse(IslandMobStackPolicy.mergeRoom(8, 8, 249, false));
        assertFalse(IslandMobStackPolicy.mergeRoom(8, 8, Integer.MAX_VALUE, false));
        assertFalse(IslandMobStackPolicy.mergeRoom(Integer.MAX_VALUE, 1, 0, true));
        assertFalse(IslandMobStackPolicy.mergeRoom(1, Integer.MAX_VALUE, 0, true));
    }
    @Test void workLimitsAreGlobalAndBounded() {
        assertEquals(32, IslandMobStackPolicy.WORK_PER_PASS);
        assertEquals(8, IslandMobStackPolicy.MAX_CHUNKS_PER_PASS);
        assertEquals(1_024, IslandMobStackPolicy.NEARBY_CHECKS_PER_PASS);
        assertEquals(64, IslandMobStackPolicy.RECONCILE_ENTITIES_PER_PASS);
        assertEquals(2_000_000L, IslandMobStackPolicy.SOFT_PASS_NANOS);
        assertTrue(IslandMobStackPolicy.NEARBY_CHECKS_PER_PASS <= 2 * IslandMobStackPolicy.ENTITIES_PER_CHUNK);
    }
}
