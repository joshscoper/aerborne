package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

final class IslandServiceTest {
    @TempDir Path directory;
    private final AccountId owner = AccountId.random(), friend = AccountId.random(), stranger = AccountId.random();
    private final Instant now = Instant.parse("2026-09-28T12:00:00Z");
    private PlayerDataStore data() {
        var data = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
        for (AccountId account : List.of(owner, friend, stranger)) bind(data, account);
        return data;
    }
    private void bind(PlayerDataStore data, AccountId account) {
        data.bind(account.value(), account, new PlatformIdentity(PlatformIdentity.Platform.JAVA, account.toString()), "Player");
    }
    private IslandService service() { return new IslandService(data(), "sky-test"); }
    private ProgressionService.ProgressionSnapshot skills(Skill... skills) {
        Map<Skill, ProgressionService.SkillProgress> values = new EnumMap<>(Skill.class);
        for (Skill skill : skills) values.put(skill, new ProgressionService.SkillProgress(40_000, 20));
        return new ProgressionService.ProgressionSnapshot(values, Set.of(), Set.of(), Instant.EPOCH);
    }
    @Test void createIsIdempotentAndSlotsAreUnique() {
        var s = service(); var first = s.create(owner);
        assertEquals(first, s.create(owner)); assertEquals(0, first.slot());
        assertEquals(1, s.create(friend).slot()); assertEquals(2, s.create(stranger).slot());
        assertEquals(48, first.width()); assertFalse(first.visitors());
    }
    @Test void ownedIslandAndJoinedHomesRemainSeparate() {
        var s = service(); var own = s.create(friend); s.create(owner);
        s.invite(owner, friend, now); s.accept(friend, owner, now);
        assertEquals(own, s.owned(friend).orElseThrow()); assertEquals(2, s.homes(friend).size());
        s.remove(friend, owner, friend);
        assertEquals(List.of(own), s.homes(friend));
    }
    @Test void invitationsDoNotGrantBuildOrEntryAndReplayDoesNotExtendExpiry() {
        var s = service(); s.create(owner); s.invite(owner, friend, now);
        var invited = s.owned(owner).orElseThrow();
        assertFalse(invited.member(friend)); assertFalse(invited.canEnter(friend));
        s.invite(owner, friend, now.plusSeconds(100)); assertEquals(invited, s.owned(owner).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> s.accept(friend, owner, now.plusSeconds(300)));
        assertTrue(s.invitations(friend, now.plusSeconds(300)).isEmpty());
    }
    @Test void acceptedInviteIsIdempotentAndOnlyOwnerCanKickAnotherPlayer() {
        var s = service(); s.create(owner); s.invite(owner, friend, now); s.accept(friend, owner, now);
        var joined = s.owned(owner).orElseThrow(); s.accept(friend, owner, now);
        assertEquals(joined, s.owned(owner).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> s.remove(stranger, owner, friend));
        assertThrows(IllegalArgumentException.class, () -> s.remove(owner, owner, owner));
        s.remove(owner, owner, friend); assertFalse(s.owned(owner).orElseThrow().member(friend));
    }
    @Test void reservedInvitationsEnforceSixPersonCap() {
        var s = service(); s.create(owner);
        List<AccountId> invited = new ArrayList<>();
        for (int n = 0; n < 5; n++) { AccountId id = AccountId.random(); invited.add(id); s.invite(owner, id, now); }
        assertThrows(IllegalArgumentException.class, () -> s.invite(owner, stranger, now));
        s.accept(invited.getFirst(), owner, now);
        assertThrows(IllegalArgumentException.class, () -> s.invite(owner, stranger, now));
        s.remove(owner, owner, invited.get(1)); s.invite(owner, stranger, now);
        assertEquals(4, s.owned(owner).orElseThrow().invites().size());
    }
    @Test void expiredReservationsFreeSeats() {
        var s = service(); s.create(owner);
        for (int n = 0; n < 5; n++) s.invite(owner, AccountId.random(), now);
        s.invite(owner, friend, now.plusSeconds(300));
        assertEquals(Set.of(friend), s.owned(owner).orElseThrow().invites().keySet());
    }
    @Test void visitorOptInDoesNotGrantMembership() {
        var s = service(); s.create(owner); assertTrue(s.directory().isEmpty());
        s.visitors(owner, true); assertEquals(1, s.directory().size());
        assertTrue(s.owned(owner).orElseThrow().canEnter(stranger)); assertFalse(s.owned(owner).orElseThrow().member(stranger));
        s.visitors(owner, false); assertFalse(s.owned(owner).orElseThrow().canEnter(stranger));
    }
    @Test void accountStillLoadingDoesNotThrowOrGrantMembership() {
        var island = service().create(owner);
        assertFalse(island.member(null));
        assertFalse(island.canEnter(null));
    }
    @Test void chapterContributionsCombineDifferentTeammatesAndCannotReplayExpansion() {
        var s = service(); s.create(owner); s.invite(owner, friend, now); s.accept(friend, owner, now);
        assertFalse(s.advance(owner, owner, 0, skills(Skill.MINING)));
        assertEquals(0, s.owned(owner).orElseThrow().tier());
        assertTrue(s.advance(friend, owner, 0, skills(Skill.FARMING, Skill.BUILDING)));
        var expanded = s.owned(owner).orElseThrow(); assertEquals(1, expanded.tier()); assertEquals(80, expanded.width());
        assertEquals(friend, expanded.proofs().get("0:FARMING"));
        assertThrows(IllegalArgumentException.class, () -> s.advance(owner, owner, 0, skills(Skill.values())));
        assertEquals(expanded, s.owned(owner).orElseThrow());
    }
    @Test void missingSkillsUnauthorizedAndKickedMembersCannotAdvance() {
        var s = service(); s.create(owner); assertFalse(s.advance(owner, owner, 0, skills()));
        assertThrows(IllegalArgumentException.class, () -> s.advance(stranger, owner, 0, skills(Skill.values())));
        s.invite(owner, friend, now); s.accept(friend, owner, now); s.remove(owner, owner, friend);
        assertThrows(IllegalArgumentException.class, () -> s.advance(friend, owner, 0, skills(Skill.values())));
    }
    @Test void finalChapterIsBoundedAndProofsStayAfterMemberLeaves() {
        var s = service(); s.create(owner); s.invite(owner, friend, now); s.accept(friend, owner, now);
        for (int n = 0; n < 3; n++) assertTrue(s.advance(friend, owner, n, skills(Skill.values())));
        s.remove(friend, owner, friend);
        assertFalse(s.advance(owner, owner, 3, skills(Skill.values())));
        assertEquals(144, s.owned(owner).orElseThrow().width()); assertEquals(9, s.owned(owner).orElseThrow().proofs().size());
    }
    @Test void growthChangesProtectedAreaButNeverTouchesNeighborIsland() {
        var s = service(); s.create(owner); s.create(friend);
        assertTrue(s.at(2, 0).isEmpty());
        s.advance(owner, owner, 0, skills(Skill.values())); assertEquals(owner, s.at(2, 0).orElseThrow().owner());
        assertTrue(s.at(16, 0).isEmpty()); assertEquals(friend, s.at(32, 0).orElseThrow().owner());
    }
    @Test void restartKeepsOwnershipTeamInvitesProofsAndUnrelatedPlayerData() {
        var d = data(); d.update(owner, yaml -> yaml.set("economy.balance", 9123));
        var s = new IslandService(d, "sky-test"); s.create(owner); s.rename(owner, "Cloud Orchard"); s.visitors(owner, true);
        s.invite(owner, friend, now); s.accept(friend, owner, now); s.invite(owner, stranger, now);
        s.advance(friend, owner, 0, skills(Skill.MINING));
        var reloaded = new IslandService(new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger()), "sky-test");
        assertEquals(s.owned(owner), reloaded.owned(owner)); assertEquals(9123L, d.<Long>read(owner, y -> y.getLong("economy.balance")).longValue());
        assertTrue(new IslandService(d, "another-season").owned(owner).isEmpty());
    }
    @Test void invalidNamesAndUnsafeSeasonIdsAreRejected() {
        var s = service(); s.create(owner);
        for (String name : List.of("  ", "xx", "<red>Injected", "bad\nname", "a".repeat(33))) assertThrows(IllegalArgumentException.class, () -> s.rename(owner, name));
        assertThrows(IllegalArgumentException.class, () -> new IslandService(data(), "../season"));
        assertThrows(IllegalArgumentException.class, () -> new IslandService(data(), "season.bad"));
    }
    @Test void malformedSchemaMembersAndRawSectionsFailClosed() {
        var d = data(); var s = new IslandService(d, "sky-test"); s.create(owner);
        String path = "seasons.sky-test.skyblock";
        for (var bad : Map.of("schema", 99, "tier", -1, "slot", -1, "members", List.of("not-a-uuid"), "visitors", "maybe").entrySet()) {
            Object old = d.read(owner, y -> y.get(path + "." + bad.getKey()));
            d.update(owner, y -> y.set(path + "." + bad.getKey(), bad.getValue()));
            assertThrows(IllegalStateException.class, () -> new IslandService(d, "sky-test"));
            d.update(owner, y -> y.set(path + "." + bad.getKey(), old));
        }
        d.update(owner, y -> y.set(path, "broken"));
        assertThrows(IllegalStateException.class, () -> new IslandService(d, "sky-test"));
    }
    @Test void duplicateSlotsFailClosed() {
        var d = data(); var s = new IslandService(d, "sky-test"); s.create(owner); s.create(friend);
        d.update(friend, y -> y.set("seasons.sky-test.skyblock.slot", 0));
        assertThrows(IllegalStateException.class, () -> new IslandService(d, "sky-test"));
    }
    @Test void failedSavePublishesNeitherMembershipNorChapter() throws Exception {
        var s = service(); s.create(owner); s.invite(owner, friend, now);
        var before = s.owned(owner).orElseThrow();
        Files.createDirectory(directory.resolve("player_data").resolve(owner + ".yml.tmp"));
        assertThrows(IllegalStateException.class, () -> s.accept(friend, owner, now));
        assertEquals(before, s.owned(owner).orElseThrow());
        assertThrows(IllegalStateException.class, () -> s.advance(owner, owner, 0, skills(Skill.values())));
        assertEquals(before, s.owned(owner).orElseThrow());
    }
    @Test void snapshotsCannotMutateCoopPermissions() {
        var s = service(); var island = s.create(owner);
        assertThrows(UnsupportedOperationException.class, () -> island.members().add(friend));
        assertThrows(UnsupportedOperationException.class, () -> island.invites().put(friend, now));
        assertThrows(UnsupportedOperationException.class, () -> island.proofs().put("0:MINING", friend));
    }
    @Test void aPlayerCannotJoinMoreThanThreeOtherCoops() {
        var d = data(); var s = new IslandService(d, "sky-test"); s.create(friend);
        for (int n = 0; n < 3; n++) { var leader = AccountId.random(); bind(d, leader); s.create(leader); s.invite(leader, friend, now); s.accept(friend, leader, now); }
        s.create(owner); s.invite(owner, friend, now);
        assertThrows(IllegalArgumentException.class, () -> s.accept(friend, owner, now));
        assertEquals(4, s.homes(friend).size());
    }
    @Test void directoryIsCachedImmutableAndInvalidatedOnlyAfterSuccessfulSave() throws Exception {
        var s = service(); s.create(owner); s.visitors(owner, true);
        var directory = s.directory();
        for (int n = 0; n < 1000; n++) assertSame(directory, s.directory());
        assertThrows(UnsupportedOperationException.class, () -> directory.clear());
        s.rename(owner, "Cloud Garden");
        assertNotSame(directory, s.directory()); assertEquals("Cloud Garden", s.directory().getFirst().name());
        var unchanged = s.directory();
        Files.createDirectory(this.directory.resolve("player_data/" + owner + ".yml.tmp"));
        assertThrows(IllegalStateException.class, () -> s.visitors(owner, false));
        assertSame(unchanged, s.directory());
    }
    @Test void indexesRebuildAndTrackExpiryCancellationAndMembershipRevocation() {
        var d = data(); var s = new IslandService(d, "sky-test");
        s.create(owner); s.create(friend); s.invite(owner, friend, now); s.invite(owner, stranger, now);
        s.accept(friend, owner, now);
        var restored = new IslandService(new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger()), "sky-test");
        assertEquals(2, restored.homes(friend).size()); assertEquals(1, restored.invitations(stranger, now).size());
        assertTrue(restored.invitations(stranger, now.plusSeconds(300)).isEmpty());
        restored.remove(owner, owner, stranger); assertTrue(restored.invitations(stranger, now).isEmpty());
        restored.remove(friend, owner, friend); assertEquals(1, restored.homes(friend).size());
        assertEquals(friend, restored.homes(friend).getFirst().owner());
    }
    @Test void failedMembershipSavePublishesNeitherIndex() throws Exception {
        var s = service(); s.create(owner); s.invite(owner, friend, now);
        Files.createDirectory(directory.resolve("player_data/" + owner + ".yml.tmp"));
        assertThrows(IllegalStateException.class, () -> s.accept(friend, owner, now));
        assertTrue(s.homes(friend).isEmpty()); assertEquals(1, s.invitations(friend, now).size());
    }
    @Test void repeatedCertificationKeepsOriginalContributorAndDoesNotWrite() throws Exception {
        var s = service(); s.create(owner); s.invite(owner, friend, now); s.accept(friend, owner, now);
        s.advance(owner, owner, 0, skills(Skill.MINING)); var before = s.owned(owner).orElseThrow();
        Files.createDirectory(directory.resolve("player_data/" + owner + ".yml.tmp"));
        assertFalse(s.advance(friend, owner, 0, skills(Skill.MINING)));
        assertSame(before, s.owned(owner).orElseThrow()); assertEquals(owner, before.proofs().get("0:MINING"));
    }
    @Test void showcaseRequiresItsChapterAndSurvivesRenameMembershipAndRestart() {
        var d = data(); var s = new IslandService(d, "sky-test"); s.create(owner);
        assertThrows(IllegalArgumentException.class, () -> s.showcase(owner, IslandShowcase.CELESTIAL));
        s.advance(owner, owner, 0, skills(Skill.values())); s.showcase(owner, IslandShowcase.VERDANT);
        s.rename(owner, "Verdant Garden"); s.visitors(owner, true); s.invite(owner, friend, now); s.accept(friend, owner, now);
        var restored = new IslandService(new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger()), "sky-test");
        assertSame(IslandShowcase.VERDANT, restored.owned(owner).orElseThrow().showcase());
        assertEquals(80, restored.owned(owner).orElseThrow().width());
    }
    @Test void sameShowcaseDoesNotSaveAndFailedSelectionDoesNotPublish() throws Exception {
        var s = service(); s.create(owner); s.advance(owner, owner, 0, skills(Skill.values()));
        var before = s.owned(owner).orElseThrow();
        Files.createDirectory(directory.resolve("player_data/" + owner + ".yml.tmp"));
        assertDoesNotThrow(() -> s.showcase(owner, IslandShowcase.HEARTH));
        assertThrows(IllegalStateException.class, () -> s.showcase(owner, IslandShowcase.VERDANT));
        assertSame(before, s.owned(owner).orElseThrow());
    }
    @Test void legacyShowcaseDefaultsWithoutGrantingLockedOptionsAndMalformedValuesFailClosed() {
        var d = data(); var s = new IslandService(d, "sky-test"); s.create(owner);
        String path = "seasons.sky-test.skyblock.showcase"; d.update(owner, y -> y.set(path, null));
        assertSame(IslandShowcase.HEARTH, new IslandService(d, "sky-test").owned(owner).orElseThrow().showcase());
        for (Object bad : List.of("celestial", "unknown", 7, List.of("hearth"))) {
            d.update(owner, y -> y.set(path, bad));
            assertThrows(IllegalStateException.class, () -> new IslandService(d, "sky-test"));
        }
    }
    @Test void allocationCursorUsesFirstFreeSlotAfterRestartAndDoesNotSkipOnFailure() throws Exception {
        var d = data(); var s = new IslandService(d, "sky-test"); s.create(owner);
        d.update(owner, y -> y.set("seasons.sky-test.skyblock.slot", 5));
        var restored = new IslandService(d, "sky-test"); assertEquals(0, restored.create(friend).slot());
        Path blocker = Files.createDirectory(directory.resolve("player_data/" + stranger + ".yml.tmp"));
        assertThrows(IllegalStateException.class, () -> restored.create(stranger));
        Files.delete(blocker); assertEquals(1, restored.create(stranger).slot());
    }
    @Test void showcaseProgressUsesCurrentChapterAndCompletesAtTheCap() {
        var s = service(); s.create(owner); assertEquals(0, IslandShowcase.certified(s.owned(owner).orElseThrow()));
        s.advance(owner, owner, 0, skills(Skill.MINING)); assertEquals(1, IslandShowcase.certified(s.owned(owner).orElseThrow()));
        for (int tier = 0; tier < 3; tier++) s.advance(owner, owner, tier, skills(Skill.values()));
        assertEquals("All chapters complete", IslandShowcase.progress(s.owned(owner).orElseThrow()));
    }
}
