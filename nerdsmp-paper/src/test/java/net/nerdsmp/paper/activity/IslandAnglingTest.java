package net.nerdsmp.paper.activity;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class IslandAnglingTest {
    @TempDir Path directory;
    @Test void tiersRequireCatchWorkAndHabitatBreadth() {
        assertEquals(0, new IslandAngling.Progress(99, Set.of("INLAND"), List.of()).tier());
        assertEquals(2, new IslandAngling.Progress(400, Set.of("INLAND","RIVER","SWAMP"), List.of()).tier());
        assertEquals(2, new IslandAngling.Progress(1000, Set.of("INLAND","RIVER","SWAMP"), List.of()).tier());
        assertEquals(8, new IslandAngling.Progress(1000, Set.of("INLAND","RIVER","SWAMP","OCEAN","COLD"), List.of()).luck());
    }
    @Test void receiptReplayAndRestartPreserveProgressAndOtherPlayerData() {
        var data = new PlayerDataStore(directory.toFile(), java.util.logging.Logger.getAnonymousLogger()); var owner = AccountId.random();
        data.update(owner, yaml -> yaml.set("unrelated.skill", 42));
        var fishery = new IslandAngling(data,"season"); UUID hook=UUID.randomUUID(); fishery.record(owner,hook,"INLAND"); fishery.record(owner,hook,"RIVER");
        assertEquals(1,fishery.progress(owner).catches()); assertEquals(Set.of("INLAND"),fishery.progress(owner).habitats());
        assertEquals(42,data.read(owner,yaml->yaml.getInt("unrelated.skill")).intValue());
        var reload = new IslandAngling(new PlayerDataStore(directory.toFile(),java.util.logging.Logger.getAnonymousLogger()),"season");
        assertEquals(fishery.progress(owner),reload.progress(owner));
    }
    @Test void malformedProgressNeverResetsAndHistoryIsBounded() {
        var p=new IslandAngling.Progress(0,Set.of(),List.of()); for(int i=0;i<300;i++) p=p.caught(UUID.randomUUID(),"INLAND");
        assertEquals(300,p.catches()); assertEquals(256,p.receipts().size());
        assertThrows(IllegalArgumentException.class,()->new IslandAngling.Progress(-1,Set.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new IslandAngling.Progress(1,Set.of("FAKE"),List.of()));
    }
    @Test void malformedPersistedHabitatListsStayUntouched() throws Exception {
        var data = new PlayerDataStore(directory.toFile(), java.util.logging.Logger.getAnonymousLogger()); var owner=AccountId.random();
        var fishery=new IslandAngling(data,"season"); fishery.record(owner,UUID.randomUUID(),"INLAND");
        data.update(owner,yaml->yaml.set("seasons.season.island-angling.habitats",List.of("INLAND",42)));
        byte[] before=java.nio.file.Files.readAllBytes(data.directory().toPath().resolve(owner+".yml"));
        assertThrows(IllegalStateException.class,()->fishery.record(owner,UUID.randomUUID(),"RIVER"));
        assertArrayEquals(before,java.nio.file.Files.readAllBytes(data.directory().toPath().resolve(owner+".yml")));
    }
}
