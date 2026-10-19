package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.*;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

final class IslandCollectorsTest {
    @TempDir Path folder;
    AccountId owner = AccountId.random(), member = AccountId.random(), guest = AccountId.random();
    PlayerDataStore data; IslandService islands;
    IslandCollectors setup() {
        data = new PlayerDataStore(folder.toFile(), Logger.getAnonymousLogger());
        for (var id : List.of(owner, member, guest)) data.bind(id.value(), id, new PlatformIdentity(PlatformIdentity.Platform.JAVA, id.toString()), "Test");
        islands = new IslandService(data, "test"); islands.create(owner); islands.invite(owner, member, Instant.now()); islands.accept(member, owner, Instant.now());
        return new IslandCollectors(data, islands, "test");
    }
    @Test void ownershipWholeChunkUniquenessAndTalentCaps() {
        var service = setup(); var position = new IslandCollectors.Position(8, 90, 8);
        assertThrows(IllegalArgumentException.class, () -> service.add(guest, owner, position, 2));
        assertThrows(IllegalArgumentException.class, () -> service.add(owner, owner, position, 0));
        service.add(member, owner, position, 2);
        assertThrows(IllegalArgumentException.class, () -> service.add(owner, owner, new IslandCollectors.Position(9, 100, 9), 2));
        service.add(owner, owner, new IslandCollectors.Position(-1, 90, -1), 2);
        assertThrows(IllegalArgumentException.class, () -> service.add(owner, owner, new IslandCollectors.Position(20, 90, 20), 2));
        assertThrows(IllegalArgumentException.class, () -> service.add(owner, owner, new IslandCollectors.Position(512, 90, 8), 8));
    }
    @Test void unloadedPositionsAndRemovalSurviveRestart() {
        var service = setup(); var position = new IslandCollectors.Position(8, 90, 8); service.add(owner, owner, position, 2);
        var reloaded = new IslandCollectors(data, islands, "test"); assertEquals(owner, reloaded.owner(position));
        assertThrows(UnsupportedOperationException.class, () -> reloaded.positions().clear());
        reloaded.remove(position); assertTrue(new IslandCollectors(data, islands, "test").positions().isEmpty());
    }
    @Test void failedSaveDoesNotConsumeCollectorCapacity() throws Exception {
        var service = setup(); var position = new IslandCollectors.Position(8, 90, 8);
        Path file = data.directory().toPath().resolve(owner + ".yml"); Files.delete(file); Files.createDirectory(file); Files.createFile(file.resolve("occupied"));
        assertThrows(IllegalStateException.class, () -> service.add(owner, owner, position, 2)); assertEquals(0, service.count(owner)); assertNull(service.owner(position));
    }
    @Test void outOfTerritorySavedIndexFailsClosed() {
        setup(); data.update(owner, yaml -> yaml.set("seasons.test.island-collectors", List.of("512,90,8")));
        assertThrows(IllegalStateException.class, () -> new IslandCollectors(data, islands, "test"));
    }
    @Test void freightNetworkCapacityBeyondEightCollectorsPersistsAndRemainsHardCapped() {
        var service=setup();
        var skills=new EnumMap<Skill,net.nerdsmp.api.service.ProgressionService.SkillProgress>(Skill.class);
        for(Skill skill:Skill.values()) skills.put(skill,new net.nerdsmp.api.service.ProgressionService.SkillProgress(250_000,50));
        islands.advance(owner,owner,0,new net.nerdsmp.api.service.ProgressionService.ProgressionSnapshot(skills,Set.of(),Set.of(),Instant.EPOCH));
        int placed=0;
        for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) {
            var position=new IslandCollectors.Position(x*16+1,90,z*16+1);
            if(placed<14) { service.add(owner,owner,position,14);placed++; }
            else assertThrows(IllegalArgumentException.class,()->service.add(owner,owner,position,100));
        }
        assertEquals(14,service.count(owner));assertEquals(14,new IslandCollectors(data,islands,"test").count(owner));
    }
}
