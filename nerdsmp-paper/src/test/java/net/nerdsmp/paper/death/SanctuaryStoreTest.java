package net.nerdsmp.paper.death;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SanctuaryStoreTest {
    @TempDir Path directory;
    SanctuaryStore store(){return new SanctuaryStore(directory.resolve("sanctuaries.yml").toFile());}
    SanctuaryStore.Sanctuary shrine(){return new SanctuaryStore.Sanctuary("harbour","Harbour",new SpiritState.Point(UUID.randomUUID(),12,64,-50,180,0));}
    @Test void sanctuarySurvivesRenameMoveAndRemoval(){var s=store();var shrine=shrine();s.put(shrine);assertEquals(shrine,store().all().getFirst());s.put(new SanctuaryStore.Sanctuary("harbour","New Harbour",shrine.location()));assertEquals(1,store().all().size());assertEquals("New Harbour",store().all().getFirst().name());assertTrue(s.remove("harbour"));assertTrue(store().all().isEmpty());}
    @Test void saveFailureDoesNotPublishNewSanctuaryOrRemoveExistingOne()throws Exception{var s=store();var original=shrine();s.put(original);Files.createDirectory(directory.resolve("sanctuaries.yml.tmp"));assertThrows(IllegalStateException.class,()->s.remove("harbour"));assertEquals(original,s.all().getFirst());assertEquals(original,store().all().getFirst());}
    @Test void invalidFileAndInvalidIdsFailClosed()throws Exception{Files.writeString(directory.resolve("sanctuaries.yml"),"version: 1\nsanctuaries:\n  broken: {name: Missing location}\n");assertThrows(IllegalStateException.class,this::store);assertThrows(IllegalArgumentException.class,()->new SanctuaryStore.Sanctuary("../bad","bad",shrine().location()));}
}
