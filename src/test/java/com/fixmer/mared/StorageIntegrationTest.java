package com.fixmer.mared;

import com.fixmer.genesis.technology.storage.TextRepository;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import com.fixmer.mared.technology.storage.WorkspaceDrafts;
import com.fixmer.mared.commands.events.MaredEventRegistry;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real production storage/adapter/journal classes on the Minecraft classpath; no window. */
public class StorageIntegrationTest {
    @TempDir Path directory;
    TextRepository repository;
    @BeforeEach void setup(){
        net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(directory);
        repository=new TextRepository(directory.resolve("repository"));
    }
    @Test void newerIntentInvalidatesOlderSave() throws Exception {
        repository.create("file","base");
        var first=repository.capture("file","base");
        var second=repository.capture("file","base");
        assertFalse(repository.write(first,"old"));assertTrue(repository.write(second,"new"));
        assertFalse(repository.write(first,"old"));assertEquals("new",repository.read("file"));
    }
    @Test void deleteAndRecreationCannotResurrectOldSnapshot() throws Exception {
        repository.create("file","base");var ticket=repository.capture("file");
        repository.delete("file");assertFalse(repository.write(ticket,"ghost"));
        repository.create("file","replacement");assertFalse(repository.write(ticket,"ghost"));
        assertEquals("replacement",repository.read("file"));
    }
    @Test void renameInvalidatesQueuedSaveAndKeepsDestination() throws Exception {
        repository.create("file","base");var ticket=repository.capture("file");
        repository.rename("file","new");assertFalse(repository.write(ticket,"ghost"));
        assertFalse(repository.exists("file"));assertEquals("base",repository.read("new"));
        repository.create("occupied","protected");
        assertThrows(IOException.class,()->repository.rename("new","occupied"));
        assertEquals("protected",repository.read("occupied"));assertEquals("base",repository.read("new"));
    }
    @Test void externalEditsCauseConflict() throws Exception {
        repository.create("file","base");var ticket=repository.capture("file");
        Files.writeString(directory.resolve("repository/file.txt"),"external");
        assertFalse(repository.write(ticket,"editor"));
        assertThrows(IOException.class,()->repository.capture("file","base"));
        assertEquals("external",repository.read("file"));
    }
    @Test void ownCommittedSaveCanBeFollowedBeforeCallback() throws Exception {
        repository.create("file","base");var first=repository.capture("file","base");
        assertTrue(repository.write(first,"first"));
        var second=repository.capture("file","base","first");assertTrue(repository.write(second,"second"));
        assertThrows(IOException.class,()->repository.capture("file","base","first"));
        assertEquals("second",repository.read("file"));
    }
    @Test void invalidateCancelsQueuedSave() throws Exception {
        repository.create("file","base");var ticket=repository.capture("file");repository.invalidate("file");
        assertFalse(repository.write(ticket,"discarded"));assertEquals("base",repository.read("file"));
    }
    @Test void invalidUtf8AndOversizeAreRejected() throws Exception {
        repository.create("file","base");Path file=directory.resolve("repository/file.txt");
        Files.write(file,new byte[]{(byte)0xc3,(byte)0x28});assertThrows(IOException.class,()->repository.read("file"));
        Files.write(file,new byte[TextRepository.MAX_BYTES+1]);assertThrows(IOException.class,()->repository.read("file"));
        assertThrows(IOException.class,()->repository.create("huge","x".repeat(TextRepository.MAX_BYTES+1)));
        assertFalse(repository.exists("huge"));
    }
    @Test void malformedUnicodeCannotBeSavedAsReplacementCharacters() throws Exception {
        repository.create("file","base");var ticket=repository.capture("file");
        assertThrows(IOException.class,()->repository.write(ticket,"\ud800"));
        assertEquals("base",repository.read("file"));
        assertThrows(IOException.class,()->repository.create("invalid","\ud800"));assertFalse(repository.exists("invalid"));
    }
    @Test void traversalAndSymlinksAreRejected() throws Exception {
        repository.create("file","base");
        assertThrows(IOException.class,()->repository.read("../outside"));
        Path outside=directory.resolve("outside");Files.writeString(outside,"protected");
        try{Files.createSymbolicLink(directory.resolve("repository/link.txt"),outside);}
        catch(UnsupportedOperationException | FileSystemException error){Assumptions.assumeTrue(false,"Symlink creation unavailable: "+error.getMessage());}
        assertThrows(IOException.class,()->repository.read("link"));
        assertThrows(IOException.class,()->repository.create("link","replacement"));assertEquals("protected",Files.readString(outside));
        Files.createSymbolicLink(directory.resolve("linked"),directory.resolve("repository"));
        assertThrows(IOException.class,()->new TextRepository(directory.resolve("linked")).read("file"));
    }
    @Test void exclusiveCreateAndUtf8RoundTrip() throws Exception {
        repository.create("file","Привет 🌍\n");assertThrows(IOException.class,()->repository.create("file","overwrite"));
        assertEquals("Привет 🌍\n",repository.read("file"));
        assertEquals(List.of("file"),repository.list());
    }
    static final String ID="0123456789abcdef0123456789abcdef";
    @Test void recoveryCopiesDraftWithoutOverwritingOriginal() throws Exception {
        assertTrue(MaredCommandStorage.createCommand("original","on disk"));
        new WorkspaceDrafts().flush(List.of(new WorkspaceDrafts.Draft(ID,"original","unsaved changes")));
        var recovered=new WorkspaceDrafts().recover();assertEquals(1,recovered.size());
        assertEquals("on disk",MaredCommandStorage.readCommand("original"));
        assertEquals("unsaved changes",MaredCommandStorage.readCommand(recovered.getFirst().name()));
        assertTrue(new WorkspaceDrafts().recover().isEmpty());
    }
    @Test void recoveryNeverOverwritesEarlierRecoveryCopy() throws Exception {
        assertTrue(MaredCommandStorage.createCommand("recovered."+ID,"previous recovery"));
        new WorkspaceDrafts().flush(List.of(new WorkspaceDrafts.Draft(ID,null,"new draft")));
        var recovered=new WorkspaceDrafts().recover();assertNotEquals("recovered."+ID,recovered.getFirst().name());
        assertEquals("previous recovery",MaredCommandStorage.readCommand("recovered."+ID));
        assertEquals("new draft",MaredCommandStorage.readCommand(recovered.getFirst().name()));
    }
    @Test void corruptJournalIsPreservedAndCannotBeCleared() throws Exception {
        var journal=new TextRepository(directory.resolve("config/mared/drafts"));journal.create("workspace","{broken");
        var drafts=new WorkspaceDrafts();assertThrows(IOException.class,drafts::recover);
        assertThrows(IOException.class,()->drafts.flush(List.of()));assertEquals("{broken",journal.read("workspace"));
    }
    @Test void invalidEntriesFailBeforeCreatingAnyCopies() throws Exception {
        var journal=new TextRepository(directory.resolve("config/mared/drafts"));
        String text="{\"version\":1,\"documents\":[{\"id\":\""+ID+"\",\"text\":\"first\"},{\"id\":\"bad\",\"text\":\"second\"}]}";
        journal.create("workspace",text);assertThrows(IOException.class,()->new WorkspaceDrafts().recover());
        assertFalse(MaredCommandStorage.exists("recovered."+ID));assertEquals(text,journal.read("workspace"));
    }
    @Test void oversizeJournalRetainsLastCheckpoint() throws Exception {
        var drafts=new WorkspaceDrafts();drafts.flush(List.of(new WorkspaceDrafts.Draft(ID,null,"good")));
        assertThrows(IOException.class,()->drafts.flush(List.of(new WorkspaceDrafts.Draft(ID,null,"x".repeat(TextRepository.MAX_BYTES)))));
        assertEquals("good",new WorkspaceDrafts().recover().getFirst().text());
    }
    @Test void persistentReadFailureCannotEraseConfiguration() throws Exception {
        var store=new TextRepository(directory.resolve("config/mared"));store.create("persistent","saved\n");
        Path file=directory.resolve("config/mared/persistent.txt");
        Files.write(file,new byte[]{(byte)0xc3,(byte)0x28});byte[] before=Files.readAllBytes(file);
        com.fixmer.mared.commands.storage.MaredPersistentStorage.invalidateCache();
        assertThrows(java.io.UncheckedIOException.class,()->com.fixmer.mared.commands.storage.MaredPersistentStorage.add("new"));
        assertArrayEquals(before,Files.readAllBytes(file));
    }
    @Test void persistentExternalEditCannotBeOverwrittenByCachedList() throws Exception {
        com.fixmer.mared.commands.storage.MaredPersistentStorage.add("first");
        Path file=directory.resolve("config/mared/persistent.txt");Files.writeString(file,"external\n");
        assertThrows(java.io.UncheckedIOException.class,()->com.fixmer.mared.commands.storage.MaredPersistentStorage.add("second"));
        assertEquals("external\n",Files.readString(file));
        assertEquals(List.of("external"),com.fixmer.mared.commands.storage.MaredPersistentStorage.load());
    }
    @Test void deletedScriptHandlersStopEvenWhenPersistentListUpdateConflicts() throws Exception {
        MaredEventRegistry.removeAllEvents();
        try {
            assertTrue(MaredCommandStorage.createCommand("script","#persistent\n"));
            com.fixmer.mared.commands.storage.MaredPersistentStorage.add("script");
            var ordinary=new MaredScriptContext(null,null,s->{});
            var persistent=new MaredScriptContext(null,null,s->{});persistent.setPersistent(true);
            MaredEventRegistry.register("test",List.of(),ordinary,false,false);
            MaredEventRegistry.register("test",List.of(),persistent,false,true);
            Path list=directory.resolve("config/mared/persistent.txt");Files.writeString(list,"external\n");
            var result=new com.fixmer.mared.services.command.CommandFileService().delete("script");
            assertTrue(result.ok());assertFalse(MaredCommandStorage.exists("script"));
            assertEquals("external\n",Files.readString(list));assertEquals(1,MaredEventRegistry.totalCount());
        }finally{MaredEventRegistry.removeAllEvents();com.fixmer.mared.commands.events.MaredPersistentLoader.reset();}
    }
    @Test void persistentReplacementDoesNotDeleteOrdinaryListener() {
        MaredEventRegistry.removeAllEvents();
        try {
            var ordinary=new MaredScriptContext(null,null,s->{});
            var persistent=new MaredScriptContext(null,null,s->{});persistent.setPersistent(true);
            MaredEventRegistry.register("test",List.of(),ordinary,false,false);
            MaredEventRegistry.register("test",List.of(),persistent,false,true);
            MaredEventRegistry.register("test",List.of(),persistent,true,true);
            assertEquals(2,MaredEventRegistry.totalCount());
            MaredEventRegistry.clearAllPersistent();assertEquals(1,MaredEventRegistry.totalCount());
        }finally{MaredEventRegistry.removeAllEvents();}
    }
    @Test void selectiveCleanupAlsoPreservesDelayedAndRepeatingListeners() {
        MaredEventRegistry.removeAllEvents();
        try {
            var ordinary=new MaredScriptContext(null,null,s->{});
            var persistent=new MaredScriptContext(null,null,s->{});persistent.setPersistent(true);
            MaredEventRegistry.registerEvery(10,List.of(),ordinary,false);
            MaredEventRegistry.registerEvery(10,List.of(),persistent,true);
            MaredEventRegistry.registerAfter(10,List.of(),ordinary);
            MaredEventRegistry.registerAfter(10,List.of(),persistent);
            assertEquals(4,MaredEventRegistry.totalCount());MaredEventRegistry.clearAll();
            assertEquals(2,MaredEventRegistry.totalCount());MaredEventRegistry.clearAllPersistent();
            assertEquals(0,MaredEventRegistry.totalCount());
        }finally{MaredEventRegistry.removeAllEvents();}
    }
    @Test void deletedDocumentRemainsRecoverableAfterUndoToSavedText() {
        var session=new com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession();
        session.storageId="file";session.setInitialText("saved");session.exists=false;session.recomputeDirty();
        assertTrue(session.dirty);assertFalse(session.canSave());
    }
    @Test void persistentCleanupKeepsOrdinaryListeners() {
        MaredEventRegistry.removeAllEvents();
        try {
            var ordinary=new MaredScriptContext(null,null,s->{});
            var persistent=new MaredScriptContext(null,null,s->{});persistent.setPersistent(true);
            MaredEventRegistry.register("test",List.of(),ordinary,false,false);
            MaredEventRegistry.register("test",List.of(),persistent,false,true);
            assertEquals(2,MaredEventRegistry.totalCount());MaredEventRegistry.clearAllPersistent();
            assertEquals(1,MaredEventRegistry.totalCount());MaredEventRegistry.clearAll();assertEquals(0,MaredEventRegistry.totalCount());
        }finally{MaredEventRegistry.removeAllEvents();}
    }
}

