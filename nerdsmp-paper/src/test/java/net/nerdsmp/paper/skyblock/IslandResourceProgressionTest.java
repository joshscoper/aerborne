package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

final class IslandResourceProgressionTest {
    @TempDir Path folder;
    private static final String SEASON = "test";
    private static final String PATH = "seasons.test.island-resources";
    private final AccountId owner = AccountId.random(), member = AccountId.random(), guest = AccountId.random();
    private final LocalDate day = LocalDate.of(2026, 10, 12);
    private PlayerDataStore data;
    private IslandService islands;
    private IslandDevelopment development;

    private IslandResourceProgression setup(int legacyTier, int islandLevel) {
        data = new PlayerDataStore(folder.toFile(), Logger.getAnonymousLogger());
        for (AccountId account : List.of(owner, member, guest))
            data.bind(account.value(), account, new PlatformIdentity(PlatformIdentity.Platform.JAVA, account.toString()), "Player");
        islands = new IslandService(data, SEASON);
        islands.create(owner);
        islands.rename(owner, "Cloud Orchard");
        islands.invite(owner, member, Instant.now());
        islands.accept(member, owner, Instant.now());
        for (int tier = 0; tier < legacyTier; tier++) assertTrue(islands.advance(member, owner, tier, skills(Map.of(
            Skill.MINING, 50, Skill.FARMING, 50, Skill.BUILDING, 50,
            Skill.SMITHING, 50, Skill.FORESTRY, 50, Skill.HUNTING, 50,
            Skill.FISHING, 50, Skill.EXCAVATION, 50, Skill.ALCHEMY, 50))));
        seedLevel(islandLevel);
        development = new IslandDevelopment(data, islands, SEASON, () -> day);
        return service();
    }

    private void seedLevel(int level) {
        data.update(owner, yaml -> {
            String path = "seasons.test.island-development";
            yaml.set(path + ".schema", 1);
            yaml.set(path + ".xp", IslandDevelopment.experienceForLevel(level));
            yaml.set(path + ".day", day.toString());
            yaml.set(path + ".earned-today", 0);
        });
    }

    private IslandResourceProgression service() {
        return new IslandResourceProgression(data, islands, development, SEASON);
    }

    private ProgressionService.ProgressionSnapshot skills(Map<Skill, Integer> levels) {
        EnumMap<Skill, ProgressionService.SkillProgress> values = new EnumMap<>(Skill.class);
        levels.forEach((skill, level) -> values.put(skill,
            new ProgressionService.SkillProgress(ProgressionService.experienceForLevel(level), level)));
        return new ProgressionService.ProgressionSnapshot(values, Set.of(), Set.of(), Instant.EPOCH);
    }

    private ProgressionService.ProgressionSnapshot foundry() {
        return skills(Map.of(Skill.MINING, 25, Skill.SMITHING, 20, Skill.EXCAVATION, 20));
    }

    private ProgressionService.ProgressionSnapshot aetherworks() {
        return skills(Map.of(Skill.MINING, 35, Skill.ALCHEMY, 30, Skill.FARMING, 30));
    }

    private Path file() { return data.directory().toPath().resolve(owner + ".yml"); }

    @Test void legacyIslandsAreGrandfatheredWithoutWritingOrResizing() throws Exception {
        for (int tier = 0; tier <= 3; tier++) {
            var service = setup(tier, 10);
            byte[] before = Files.readAllBytes(file());
            var island = islands.owned(owner).orElseThrow();
            assertEquals(tier, service.effectiveStage(owner));
            assertEquals(tier, service.snapshot(owner).stage());
            assertFalse(data.<Boolean>read(owner, yaml -> yaml.contains(PATH)));
            var reloaded = new PlayerDataStore(folder.toFile(), Logger.getAnonymousLogger());
            var reloadedIslands = new IslandService(reloaded, SEASON);
            var reloadedDevelopment = new IslandDevelopment(reloaded, reloadedIslands, SEASON, () -> day);
            assertEquals(tier, new IslandResourceProgression(reloaded, reloadedIslands, reloadedDevelopment, SEASON).effectiveStage(owner));
            assertEquals(island, reloadedIslands.owned(owner).orElseThrow());
            assertArrayEquals(before, Files.readAllBytes(file()));
            // Reset only this unit-test fixture before the next legacy tier.
            if (tier < 3) {
                data.update(owner, yaml -> {
                    yaml.set("seasons.test.skyblock", null);
                    yaml.set("seasons.test.island-development", null);
                });
            }
        }
    }

    @Test void legacyChapterAdvanceIsImmediatelyVisibleWithoutAnAdditionalSave() throws Exception {
        var service = setup(2, 15);
        assertEquals(2, service.effectiveStage(owner));
        assertThrows(IllegalArgumentException.class, () -> service.contribute(owner, owner, 2, foundry()));
        assertTrue(islands.advance(owner, owner, 2, skills(Map.of(Skill.FISHING, 15, Skill.EXCAVATION, 15, Skill.ALCHEMY, 15))));
        byte[] afterChapter = Files.readAllBytes(file());
        assertEquals(3, service.effectiveStage(owner));
        assertArrayEquals(afterChapter, Files.readAllBytes(file()));
    }

    @Test void coOpProofsCombineAndOriginalContributionSurvivesAPlayerLeaving() {
        var service = setup(3, 15);
        assertFalse(service.contribute(member, owner, 3, skills(Map.of(Skill.MINING, 25))));
        assertTrue(service.snapshot(owner).certified(Skill.MINING));
        islands.remove(owner, owner, member);
        assertTrue(service.contribute(owner, owner, 3, skills(Map.of(Skill.SMITHING, 20, Skill.EXCAVATION, 20))));
        assertEquals(4, service.effectiveStage(owner));
        assertEquals(member, service.snapshot(owner).proofs().get(IslandResourceProgression.Proof.FOUNDRY_MINING));
        assertEquals(service.snapshot(owner), service().snapshot(owner));
        assertThrows(IllegalArgumentException.class, () -> service.contribute(member, owner, 4, aetherworks()));
    }

    @Test void certificationsMayBeSavedBeforeIslandLevelAndReusedOnceLevelIsEarned() {
        var service = setup(3, 14);
        assertFalse(service.contribute(owner, owner, 3, foundry()));
        assertEquals(3, service.effectiveStage(owner));
        assertEquals(3, service.snapshot(owner).proofs().size());
        assertFalse(service.contribute(owner, owner, 3, ProgressionService.ProgressionSnapshot.empty()));
        seedLevel(15);
        development = new IslandDevelopment(data, islands, SEASON, () -> day);
        var afterLevel = service();
        assertTrue(afterLevel.contribute(owner, owner, 3, ProgressionService.ProgressionSnapshot.empty()));
        assertEquals(4, afterLevel.effectiveStage(owner));
    }

    @Test void stagesAdvanceOneAtATimeAndFinalStageIsBounded() {
        var service = setup(3, 25);
        var all = skills(Map.of(Skill.MINING, 50, Skill.SMITHING, 50, Skill.EXCAVATION, 50, Skill.ALCHEMY, 50, Skill.FARMING, 50));
        assertTrue(service.contribute(owner, owner, 3, all));
        assertEquals(4, service.effectiveStage(owner));
        assertEquals(3, service.snapshot(owner).proofs().size());
        assertTrue(service.contribute(member, owner, 4, all));
        assertEquals(5, service.effectiveStage(owner));
        assertEquals(6, service.snapshot(owner).proofs().size());
        assertTrue(service.snapshot(owner).nextStage().isEmpty());
        assertFalse(service.contribute(owner, owner, 5, all));
        assertEquals(service.snapshot(owner), service().snapshot(owner));
    }

    @Test void aetherworksRequiresItsExactSkillsAndIslandLevel() {
        var service = setup(3, 24);
        assertTrue(service.contribute(owner, owner, 3, foundry()));
        assertFalse(service.contribute(owner, owner, 4, skills(Map.of(Skill.MINING, 34, Skill.ALCHEMY, 29, Skill.FARMING, 29))));
        assertEquals(3, service.snapshot(owner).proofs().size());
        assertFalse(service.contribute(owner, owner, 4, aetherworks()));
        assertEquals(4, service.effectiveStage(owner));
        assertEquals(6, service.snapshot(owner).proofs().size());
        seedLevel(25);
        development = new IslandDevelopment(data, islands, SEASON, () -> day);
        assertTrue(service().contribute(owner, owner, 4, ProgressionService.ProgressionSnapshot.empty()));
    }

    @Test void unauthorizedAndStaleRequestsDoNotWrite() throws Exception {
        var service = setup(3, 25);
        byte[] before = Files.readAllBytes(file());
        assertThrows(IllegalArgumentException.class, () -> service.contribute(guest, owner, 3, foundry()));
        assertThrows(IllegalArgumentException.class, () -> service.contribute(owner, owner, 2, foundry()));
        assertArrayEquals(before, Files.readAllBytes(file()));
        assertTrue(service.contribute(owner, owner, 3, foundry()));
        byte[] upgraded = Files.readAllBytes(file());
        assertThrows(IllegalArgumentException.class, () -> service.contribute(owner, owner, 3, foundry()));
        assertArrayEquals(upgraded, Files.readAllBytes(file()));
    }

    @Test void repeatedContributionDoesNotRewriteOrChangeOriginalActor() throws Exception {
        var service = setup(3, 14);
        assertFalse(service.contribute(member, owner, 3, skills(Map.of(Skill.MINING, 25))));
        byte[] before = Files.readAllBytes(file());
        Files.createDirectory(file().resolveSibling(owner + ".yml.tmp"));
        assertFalse(service.contribute(owner, owner, 3, skills(Map.of(Skill.MINING, 25))));
        assertEquals(member, service.snapshot(owner).proofs().get(IslandResourceProgression.Proof.FOUNDRY_MINING));
        assertArrayEquals(before, Files.readAllBytes(file()));
    }

    @Test void anotherWriterCannotBeOverwrittenByACachedStage() throws Exception {
        var first = setup(3, 25);
        var stale = service();
        assertTrue(first.contribute(owner, owner, 3, foundry()));
        byte[] afterFirst = Files.readAllBytes(file());
        assertThrows(IllegalArgumentException.class, () -> stale.contribute(member, owner, 3, foundry()));
        assertArrayEquals(afterFirst, Files.readAllBytes(file()));
        assertEquals(4, stale.snapshot(owner).stage());
    }

    @Test void cachedValidStateCannotReplaceALaterMalformedRecord() throws Exception {
        var service = setup(3, 25);
        data.update(owner, yaml -> { yaml.set(PATH + ".schema", 99); yaml.set(PATH + ".stage", 3); });
        byte[] before = Files.readAllBytes(file());
        assertThrows(IllegalStateException.class, () -> service.contribute(owner, owner, 3, foundry()));
        assertArrayEquals(before, Files.readAllBytes(file()));
    }

    @Test void failedWritePublishesNeitherProofNorStageAndCanBeRetried() throws Exception {
        var service = setup(3, 15);
        var before = service.snapshot(owner);
        Path blocker = Files.createDirectory(file().resolveSibling(owner + ".yml.tmp"));
        assertThrows(IllegalStateException.class, () -> service.contribute(owner, owner, 3, foundry()));
        assertEquals(before, service.snapshot(owner));
        assertFalse(data.<Boolean>read(owner, yaml -> yaml.contains(PATH)));
        Files.delete(blocker);
        assertTrue(service.contribute(owner, owner, 3, foundry()));
    }

    @Test void advancedWritesPreserveAllExistingIslandAndUnrelatedPlayerState() {
        var service = setup(3, 25);
        data.update(owner, yaml -> {
            yaml.set("economy.balance", 9182L);
            yaml.set("moderation.notes", List.of("Preserve"));
            yaml.set("seasons.test.skills.mining.xp", 250_000L);
            yaml.set("entitlements.supporter", "receipt-123");
            yaml.set("seasons.test.island-development.talents.stacking", 2);
        });
        String before = data.read(owner, yaml -> {
            var copy = new org.bukkit.configuration.file.YamlConfiguration();
            yaml.getKeys(true).stream().filter(key -> !key.startsWith(PATH) && !yaml.isConfigurationSection(key))
                .forEach(key -> copy.set(key, yaml.get(key)));
            return copy.saveToString();
        });
        var island = islands.owned(owner).orElseThrow();
        assertTrue(service.contribute(owner, owner, 3, foundry()));
        assertTrue(service.contribute(owner, owner, 4, aetherworks()));
        String after = data.read(owner, yaml -> {
            var copy = new org.bukkit.configuration.file.YamlConfiguration();
            yaml.getKeys(true).stream().filter(key -> !key.startsWith(PATH) && !yaml.isConfigurationSection(key))
                .forEach(key -> copy.set(key, yaml.get(key)));
            return copy.saveToString();
        });
        assertEquals(before, after);
        assertEquals(island, islands.owned(owner).orElseThrow());
        assertEquals(3, island.tier());
        assertEquals(144, island.width());
    }

    @Test void snapshotsAreImmutableAndForgedSkillLevelsCannotCertify() {
        var service = setup(3, 25);
        var forged = new ProgressionService.ProgressionSnapshot(Map.of(Skill.MINING,
            new ProgressionService.SkillProgress(0, 50)), Set.of(), Set.of(), Instant.EPOCH);
        assertFalse(service.contribute(owner, owner, 3, forged));
        assertTrue(service.snapshot(owner).proofs().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> service.snapshot(owner).proofs()
            .put(IslandResourceProgression.Proof.FOUNDRY_MINING, owner));
    }

    @Test void malformedSavedRecordsFailClosedAndStayByteForByte() throws Exception {
        setup(3, 25);
        List<Map<String, Object>> malformed = List.of(
            Map.of("stage", 3),
            Map.of("schema", 99, "stage", 3),
            Map.of("schema", "1", "stage", 3),
            Map.of("schema", 1, "stage", "3"),
            Map.of("schema", 1, "stage", -1),
            Map.of("schema", 1, "stage", 6),
            Map.of("schema", 1, "stage", 4),
            Map.of("schema", 1, "stage", 3, "unknown", true),
            Map.of("schema", 1, "stage", 3, "proofs", List.of("bad")),
            Map.of("schema", 1, "stage", 3, "proofs.unknown", owner.toString()),
            Map.of("schema", 1, "stage", 3, "proofs.foundry_mining", "bad-uuid"),
            Map.of("schema", 1, "stage", 3, "proofs.foundry_mining", "1-1-1-1-1"),
            Map.of("schema", 1, "stage", 3, "proofs.aetherworks_mining", owner.toString()));
        for (Map<String, Object> fields : malformed) {
            data.update(owner, yaml -> { yaml.set(PATH, null); fields.forEach((key, value) -> yaml.set(PATH + "." + key, value)); });
            byte[] before = Files.readAllBytes(file());
            assertThrows(IllegalStateException.class, this::service, fields.toString());
            assertArrayEquals(before, Files.readAllBytes(file()));
        }
        data.update(owner, yaml -> yaml.set(PATH, "broken"));
        byte[] before = Files.readAllBytes(file());
        assertThrows(IllegalStateException.class, this::service);
        assertArrayEquals(before, Files.readAllBytes(file()));
    }

    @Test void noIslandAndUnsafeSeasonsAreRejected() {
        var service = setup(3, 25);
        assertThrows(IllegalArgumentException.class, () -> service.snapshot(guest));
        for (String season : List.of("../season", "season.bad", "", "a".repeat(65)))
            assertThrows(IllegalArgumentException.class, () -> new IslandResourceProgression(data, islands, development, season));
        assertThrows(IllegalArgumentException.class, () -> IslandResourceStage.at(-1));
        assertThrows(IllegalArgumentException.class, () -> IslandResourceStage.at(6));
    }
}
