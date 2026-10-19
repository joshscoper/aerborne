package net.nerdsmp.paper.studio.qa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class WorkboardStoreTest {
    @TempDir Path directory;
    private static final UUID AUTHOR=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant NOW=Instant.parse("2026-10-12T12:00:00Z");
    private static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
    private WorkboardStore store(Path path) throws IOException { return new WorkboardStore(path.toFile(),CLOCK,(file,text)->{
        Files.createDirectories(file.getParent()); Files.writeString(file,text);
    }); }

    @Test void readsDoNotCreateFilesAndCommittedRecordsRoundTrip() throws Exception {
        Path file=directory.resolve("nested/workboard.yml");
        var store=new WorkboardStore(file.toFile()); assertTrue(store.all().isEmpty()); assertFalse(Files.exists(file));
        var item=store.create("crypt_playtest","Playtest the Crypt",WorkItem.Kind.REVIEW,AUTHOR);
        item=store.assign(item.id(),item.revision(),OWNER);
        item=store.reference(item.id(),item.revision(),new ContentRef(ContentRef.Type.ZONE,"crypt"));
        item=store.note(item.id(),item.revision(),AUTHOR,"Check the west stairs.");
        item=store.transition(item.id(),item.revision(),WorkItem.State.IN_PROGRESS);
        var restored=new WorkboardStore(file.toFile()); assertEquals(List.of(item),restored.all());
        assertThrows(UnsupportedOperationException.class,()->restored.all().clear());
        assertThrows(UnsupportedOperationException.class,()->restored.find("crypt_playtest").orElseThrow().references().clear());
        try(var paths=Files.list(file.getParent())) { assertEquals(1,paths.count()); }
    }
    @Test void staleEditorsCannotOverwriteWorkOwnerStateNotesOrReferences() throws Exception {
        var store=store(directory.resolve("board.yml")); var first=store.create("bug_1","Missing loot",WorkItem.Kind.BUG,AUTHOR);
        var current=store.assign(first.id(),first.revision(),OWNER);
        assertThrows(IllegalStateException.class,()->store.assign(first.id(),first.revision(),AUTHOR));
        assertThrows(IllegalStateException.class,()->store.transition(first.id(),first.revision(),WorkItem.State.DONE));
        assertThrows(IllegalStateException.class,()->store.note(first.id(),first.revision(),AUTHOR,"Stale note"));
        assertThrows(IllegalStateException.class,()->store.reference(first.id(),first.revision(),new ContentRef(ContentRef.Type.MOB,"stalker")));
        assertEquals(current,store.find(first.id()).orElseThrow());
    }
    @Test void diskFailureNeverPublishesOrErasesAnExistingSnapshot() throws Exception {
        Path path=directory.resolve("board.yml"); var fail=new AtomicBoolean();
        var store=new WorkboardStore(path.toFile(),CLOCK,(file,text)->{ if(fail.get()) throw new IOException("disk full"); Files.writeString(file,text); });
        var item=store.create("bug_1","Missing loot",WorkItem.Kind.BUG,AUTHOR); String original=Files.readString(path); fail.set(true);
        assertThrows(IOException.class,()->store.assign(item.id(),item.revision(),OWNER));
        assertThrows(IOException.class,()->store.note(item.id(),item.revision(),AUTHOR,"New note"));
        assertThrows(IOException.class,()->store.transition(item.id(),item.revision(),WorkItem.State.DONE));
        assertThrows(IOException.class,()->store.reference(item.id(),item.revision(),new ContentRef(ContentRef.Type.LOOT,"crypt")));
        assertThrows(IOException.class,()->store.create("other","Other task",WorkItem.Kind.TASK,AUTHOR));
        assertEquals(List.of(item),store.all()); assertEquals(original,Files.readString(path));
        assertEquals(store.all(),store(path).all());
    }
    @Test void invalidReloadPreservesThePreviousSnapshotAndDoesNotOverwriteBadFile() throws Exception {
        Path path=directory.resolve("board.yml"); var store=store(path); var item=store.create("task","Test",WorkItem.Kind.TASK,AUTHOR);
        String invalid="schema-version: 99\nitems: {}\n"; Files.writeString(path,invalid);
        assertThrows(IOException.class,store::reload); assertEquals(List.of(item),store.all()); assertEquals(invalid,Files.readString(path));
    }
    @Test void strictSchemaRejectsDuplicatesWrongTypesUnknownFieldsAndOversizedFiles() throws Exception {
        Path path=directory.resolve("board.yml");
        for(String invalid:List.of("schema-version: 1\nschema-version: 1\nitems: {}\n",
            "schema-version: '1'\nitems: {}\n", "schema-version: 1\nitems: []\n", "schema-version: 1\nitems: {}\nextra: true\n",
            "schema-version: 1\nitems: &items {}\nextra: *items\n")) {
            Files.writeString(path,invalid); assertThrows(IOException.class,()->store(path),invalid);
        }
        Files.writeString(path," ".repeat(WorkboardStore.MAX_FILE_BYTES+1)); assertThrows(IOException.class,()->store(path));
    }
    @Test void malformedTimestampsAndRevisionsFailClosed() throws Exception {
        Path path=directory.resolve("board.yml"); var store=store(path); store.create("task","Test",WorkItem.Kind.TASK,AUTHOR);
        String original=Files.readString(path);
        for(String invalid:List.of(original.replace("revision: 1","revision: 0"),original.replace("revision: 1","revision: 1.5"),
            original.replace(NOW.toString(),"not-an-instant"),original.replace(AUTHOR.toString(),"1-2-3-4-5"))) {
            Files.writeString(path,invalid); assertThrows(IOException.class,()->store(path));
        }
    }
    @Test void noteHistoryIsBoundedAndPersistsTheLatestNotes() throws Exception {
        var store=store(directory.resolve("board.yml")); var item=store.create("task","Test",WorkItem.Kind.TASK,AUTHOR);
        for(int i=0;i<40;i++) item=store.note(item.id(),item.revision(),AUTHOR,"Note "+i);
        assertEquals(32,item.notes().size()); assertEquals("Note 8",item.notes().getFirst().text()); assertEquals("Note 39",item.notes().getLast().text());
        assertEquals(item,store(directory.resolve("board.yml")).find(item.id()).orElseThrow());
    }
    @Test void clockRegressionDoesNotInvalidatePersistedHistory() throws Exception {
        Path path=directory.resolve("board.yml"); var store=store(path); var item=store.create("task","Test",WorkItem.Kind.TASK,AUTHOR);
        var older=new WorkboardStore(path.toFile(),Clock.fixed(NOW.minusSeconds(60),ZoneOffset.UTC),(file,text)->Files.writeString(file,text));
        var updated=older.note(item.id(),item.revision(),AUTHOR,"Late clock"); assertEquals(NOW,updated.updatedAt()); assertEquals(NOW,updated.notes().getFirst().at());
    }
    @Test void itemAndReferenceLimitsAreEnforcedWithoutSilentEviction() throws Exception {
        var store=new WorkboardStore(directory.resolve("board.yml").toFile(),CLOCK,(file,text)->{});
        for(int i=0;i<256;i++) store.create("task_"+i,"Task "+i,WorkItem.Kind.TASK,AUTHOR);
        assertThrows(IllegalArgumentException.class,()->store.create("overflow","Overflow",WorkItem.Kind.TASK,AUTHOR)); assertEquals(256,store.all().size());
        var item=store.find("task_0").orElseThrow();
        for(int i=0;i<12;i++) item=store.reference(item.id(),item.revision(),new ContentRef(ContentRef.Type.ITEM,"item_"+i));
        var full=item;
        assertThrows(IllegalArgumentException.class,()->store.reference(full.id(),full.revision(),new ContentRef(ContentRef.Type.ITEM,"another")));
        assertThrows(IllegalArgumentException.class,()->store.reference(full.id(),full.revision(),full.references().getFirst()));
        assertEquals(full,store.find(full.id()).orElseThrow());
    }
    @Test void completedItemsCanBeReopenedAndReferencesRemovedWithoutAReviewGate() throws Exception {
        var store=store(directory.resolve("board.yml")); var item=store.create("review","Optional review",WorkItem.Kind.REVIEW,AUTHOR);
        var ref=new ContentRef(ContentRef.Type.ITEM,"blade"); item=store.reference(item.id(),item.revision(),ref);
        item=store.transition(item.id(),item.revision(),WorkItem.State.DONE); item=store.transition(item.id(),item.revision(),WorkItem.State.OPEN);
        item=store.unreference(item.id(),item.revision(),ref); assertTrue(item.references().isEmpty()); assertEquals(WorkItem.State.OPEN,item.state());
        var current=item; assertThrows(IllegalArgumentException.class,()->store.transition(current.id(),current.revision(),current.state()));
    }
    @Test void workTextAndTypedIdentifiersRejectFormattingAndControlCharacters() {
        assertEquals(new ContentRef(ContentRef.Type.MOB,"vanilla:zombie"),ContentRef.parse("mob:vanilla:zombie"));
        assertEquals(new ContentRef(ContentRef.Type.QUEST,"daily_miner"),ContentRef.parse("QUEST:daily_miner"));
        assertEquals(new ContentRef(ContentRef.Type.FACTION,"wardens"),ContentRef.parse("FACTION:wardens"));
        assertEquals(new ContentRef(ContentRef.Type.ITEM,"sky/blade.v2"),ContentRef.parse("ITEM:sky/blade.v2"));
        for(String value:List.of("TASK:x","ITEM:","item:../secrets","ITEM:<red>blade","ITEM:Upper"))
            assertThrows(IllegalArgumentException.class,()->ContentRef.parse(value));
        assertThrows(IllegalArgumentException.class,()->WorkItem.text("<not control>\nhello",400,"Note"));
        assertThrows(IllegalArgumentException.class,()->WorkItem.text("§4Danger",400,"Note"));
        assertThrows(IllegalArgumentException.class,()->WorkItem.text("a".repeat(401),400,"Note"));
    }
}
