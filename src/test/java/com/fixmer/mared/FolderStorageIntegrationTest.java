package com.fixmer.mared;

import com.fixmer.genesis.technology.storage.RepositoryPath;
import com.fixmer.genesis.technology.storage.TextRepository;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Folder semantics use the production repository; no Minecraft window is required. */
public class FolderStorageIntegrationTest {
    @Test void legacyFilesAndEmptyFoldersArePreserved() throws Exception {
        Path root = Files.createTempDirectory("genesis-folders-");
        var repo = new TextRepository(root);
        repo.create("legacy", "old");
        repo.createDirectory("empty");
        repo.createDirectory("quests");
        repo.createDirectory("quests/chapter1");
        repo.create("quests/chapter1/start", "Привет 🌍");
        assertEquals(List.of("legacy", "quests/chapter1/start"), repo.list());
        assertEquals(List.of(new TextRepository.Entry("empty", true), new TextRepository.Entry("quests", true),
            new TextRepository.Entry("legacy", false)), repo.entries(""));
        assertEquals(List.of(), repo.entries("empty"));
        assertEquals("Привет 🌍", repo.read("quests/chapter1/start"));
        assertEquals("old", repo.read("legacy"));
    }
    @Test void movingDocumentInvalidatesBothSaveDestinations() throws Exception {
        var repo = new TextRepository(Files.createTempDirectory("genesis-move-"));
        repo.createDirectory("quests");
        repo.create("source", "base");
        var source = repo.capture("source");
        repo.create("quests/target", "previous");
        var previous = repo.capture("quests/target");
        assertThrows(IOException.class, () -> repo.rename("source", "quests/target"));
        assertEquals("previous", repo.read("quests/target"));
        repo.delete("quests/target");
        repo.rename("source", "quests/target");
        assertFalse(repo.write(source, "ghost source"));
        assertFalse(repo.write(previous, "ghost target"));
        assertFalse(repo.exists("source"));
        assertEquals("base", repo.read("quests/target"));
        assertTrue(repo.write(repo.capture("quests/target", "base"), "edited"));
    }
    @Test void pathsArePortableAndBounded() {
        for (String path : List.of("../escape", "a/../b", "/root", "a//b", "a/", "a\\b", ".hidden", "CON", "a/nul.py", "LPT1.txt", "a/" + "x".repeat(65), "a/".repeat(16) + "b", "x".repeat(300)))
            assertFalse(RepositoryPath.valid(path));
        assertTrue(RepositoryPath.valid("quests/chapter_1/start.v2"));
        assertEquals("quests/chapter_1", RepositoryPath.parent("quests/chapter_1/start"));
        assertEquals("start", RepositoryPath.leaf("quests/chapter_1/start"));
    }
    @Test void missingParentsAndCollisionsDoNotModifyFiles() throws Exception {
        Path root = Files.createTempDirectory("genesis-parents-");
        var repo = new TextRepository(root);
        assertThrows(IOException.class, () -> repo.create("missing/file", "bad"));
        assertThrows(IOException.class, () -> repo.createDirectory("missing/child"));
        assertFalse(Files.exists(root.resolve("missing")));
        repo.createDirectory("folder");
        assertThrows(IOException.class, () -> repo.createDirectory("folder"));
        repo.create("folder/file", "protected");
        assertThrows(IOException.class, () -> repo.create("folder/file", "overwrite"));
        assertEquals("protected", repo.read("folder/file"));
    }
    @Test void nestedSymlinksCannotEscapeRepository() throws Exception {
        Path root = Files.createTempDirectory("genesis-links-");
        Path outside = Files.createTempDirectory("genesis-outside-");
        Files.writeString(outside.resolve("file.txt"), "protected");
        var repo = new TextRepository(root);
        repo.createDirectory("safe");
        try { Files.createSymbolicLink(root.resolve("safe/link"), outside); }
        catch (UnsupportedOperationException | FileSystemException error) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symlink creation unavailable");
            return;
        }
        assertThrows(IOException.class, () -> repo.read("safe/link/file"));
        assertThrows(IOException.class, () -> repo.create("safe/link/new", "bad"));
        assertThrows(IOException.class, () -> repo.createDirectory("safe/link/new"));
        assertThrows(IOException.class, () -> repo.entries("safe/link"));
        assertEquals(List.of(), repo.list());
        assertEquals("protected", Files.readString(outside.resolve("file.txt")));
        assertFalse(Files.exists(outside.resolve("new.txt")));
    }
    @Test void externalEditStillConflictsInsideFolder() throws Exception {
        Path root = Files.createTempDirectory("genesis-conflict-");
        var repo = new TextRepository(root);
        repo.createDirectory("folder");
        repo.create("folder/file", "base");
        var ticket = repo.capture("folder/file", "base");
        Files.writeString(root.resolve("folder/file.txt"), "external");
        assertFalse(repo.write(ticket, "editor"));
        assertThrows(IOException.class, () -> repo.capture("folder/file", "base"));
        assertEquals("external", repo.read("folder/file"));
    }
}
