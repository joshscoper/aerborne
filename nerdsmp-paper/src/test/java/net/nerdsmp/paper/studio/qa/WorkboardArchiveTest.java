package net.nerdsmp.paper.studio.qa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class WorkboardArchiveTest {
    @TempDir Path directory;
    private static final UUID AUTHOR=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"),ZoneOffset.UTC);
    private WorkboardStore store(Path file) throws IOException { return new WorkboardStore(file.toFile()); }
    private WorkItem completed(WorkboardStore store) throws IOException {
        var item=store.create("playtest","Playtest the Crypt",WorkItem.Kind.REVIEW,AUTHOR);
        item=store.note(item.id(),item.revision(),AUTHOR,"Party encounter passed.");
        return store.transition(item.id(),item.revision(),WorkItem.State.DONE);
    }
    @Test void completedSnapshotsSurviveRestartAndTheirIdsCannotBeReused() throws Exception {
        Path path=directory.resolve("studio/workboard.yml"); var store=store(path); var item=completed(store);
        assertEquals(item,store.archive(item.id(),item.revision())); assertTrue(store.all().isEmpty()); assertEquals(1,store.archivedCount());
        Path record=path.getParent().resolve("workboard-archive/playtest-r3.yml"); assertTrue(Files.exists(record));
        var archive=store(record); assertEquals(item,archive.find(item.id()).orElseThrow());
        var restored=store(path); assertEquals(1,restored.archivedCount()); assertTrue(restored.all().isEmpty());
        assertThrows(IllegalArgumentException.class,()->restored.create(item.id(),"Different task",WorkItem.Kind.TASK,AUTHOR));
        assertDoesNotThrow(()->restored.create("new_playtest","New task",WorkItem.Kind.TASK,AUTHOR));
    }
    @Test void openOrInProgressAndStaleRevisionsCannotBeArchived() throws Exception {
        Path path=directory.resolve("board.yml"); var store=store(path);
        var open=store.create("playtest","Test",WorkItem.Kind.TASK,AUTHOR);
        assertThrows(IllegalArgumentException.class,()->store.archive(open.id(),open.revision()));
        var working=store.transition(open.id(),open.revision(),WorkItem.State.IN_PROGRESS);
        assertThrows(IllegalArgumentException.class,()->store.archive(working.id(),working.revision()));
        var done=store.transition(working.id(),working.revision(),WorkItem.State.DONE);
        assertThrows(IllegalStateException.class,()->store.archive(done.id(),working.revision()));
        assertEquals(done,store.find(done.id()).orElseThrow()); assertFalse(Files.exists(directory.resolve("workboard-archive")));
    }
    @Test void failedActiveCommitKeepsTheActiveItemAndArchiveAndRetryDoesNotDuplicateIt() throws Exception {
        Path path=directory.resolve("board.yml"); var failActive=new AtomicBoolean();
        var store=new WorkboardStore(path.toFile(),CLOCK,(file,text)->{
            if(failActive.get()&&file.equals(path)) throw new IOException("active board full");
            Files.createDirectories(file.getParent());Files.writeString(file,text);
        });
        var item=completed(store); String activeBefore=Files.readString(path); failActive.set(true);
        assertThrows(IOException.class,()->store.archive(item.id(),item.revision()));
        assertEquals(item,store.find(item.id()).orElseThrow()); assertEquals(activeBefore,Files.readString(path));
        Path record=directory.resolve("workboard-archive/playtest-r3.yml"); String savedArchive=Files.readString(record);
        assertEquals(1,store.archivedCount()); assertEquals(item,store(path).find(item.id()).orElseThrow());
        failActive.set(false); store.archive(item.id(),item.revision()); assertTrue(store.all().isEmpty());
        assertEquals(savedArchive,Files.readString(record)); assertEquals(1,store.archivedCount());
    }
    @Test void failedArchiveSaveNeverRemovesActiveRecords() throws Exception {
        Path path=directory.resolve("board.yml");
        var store=new WorkboardStore(path.toFile(),CLOCK,(file,text)->{
            if(file.getParent().getFileName().toString().equals("workboard-archive")) throw new IOException("archive full");
            Files.writeString(file,text);
        });
        var item=completed(store); String saved=Files.readString(path);
        assertThrows(IOException.class,()->store.archive(item.id(),item.revision()));
        assertEquals(item,store.find(item.id()).orElseThrow()); assertEquals(saved,Files.readString(path)); assertEquals(0,store.archivedCount());
    }
    @Test void conflictingArchiveIsPreservedInsteadOfOverwritten() throws Exception {
        Path path=directory.resolve("board.yml"); var store=store(path); var item=completed(store);
        Path record=directory.resolve("workboard-archive/playtest-r3.yml"); Files.createDirectories(record.getParent());
        String invalid="schema-version: 99\nitems: {}\n"; Files.writeString(record,invalid);
        assertThrows(IOException.class,()->store.archive(item.id(),item.revision()));
        assertEquals(invalid,Files.readString(record)); assertEquals(item,store.find(item.id()).orElseThrow());
    }
    @Test void archiveLimitIsEnforcedWithoutDeletingHistoryOrActiveItems() throws Exception {
        Path path=directory.resolve("board.yml"),archive=directory.resolve("workboard-archive"); Files.createDirectories(archive);
        // Indexing never expands historical snapshots into active records.
        for(int i=0;i<WorkboardStore.MAX_ARCHIVES;i++) Files.writeString(archive.resolve("old_"+i+"-r1.yml"),"schema-version: 1\nitems: {}\n");
        var store=store(path); assertEquals(WorkboardStore.MAX_ARCHIVES,store.archivedCount()); var item=completed(store);
        assertThrows(IllegalArgumentException.class,()->store.archive(item.id(),item.revision()));
        assertEquals(item,store.find(item.id()).orElseThrow());
        assertThrows(IllegalArgumentException.class,()->store.create("old_0","Reused",WorkItem.Kind.TASK,AUTHOR));
    }
}
