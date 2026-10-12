package com.fixmer.mared;

import com.fixmer.genesis.technology.storage.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectArchiveIntegrationTest {
    private Path temp() throws IOException {return Files.createTempDirectory("genesis-project-");}
    private static void zip(Path p,Map<String,byte[]> entries) throws IOException {
        try(var out=new ZipOutputStream(Files.newOutputStream(p))){for(var e:entries.entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}}
    }
    private static Map<String,byte[]> contents(String name,byte[] text){var map=new LinkedHashMap<String,byte[]>();map.put("genesis-project.properties","format=genesis-commands\nversion=1\n".getBytes(StandardCharsets.UTF_8));map.put(name,text);return map;}
    @Test void roundTripPreservesUnicodeCrLfEmptyFilesAndFolders() throws Exception {
        Path root=temp();var source=new TextRepository(root.resolve("source"));source.createDirectory("quests");source.createDirectory("empty");source.create("quests/start","mc say Привет 😀\r\n// data\n");source.create("blank","");
        var plan=ProjectArchive.snapshot(source);Path archive=root.resolve("project.zip");ProjectArchive.write(archive,plan);
        var parsed=ProjectArchive.read(archive);assertEquals(plan,parsed);
        var target=new TextRepository(root.resolve("target"));var nested=parsed.beneath("mods/story");assertTrue(target.previewBatch(nested.documents(),nested.directories()).isEmpty());target.createBatch(nested.documents(),nested.directories());
        assertEquals(source.read("quests/start"),target.read("mods/story/quests/start"));assertEquals("",target.read("mods/story/blank"));assertTrue(target.directoryExists("mods/story/empty"));
        byte[] before=Files.readAllBytes(archive);assertThrows(IOException.class,()->ProjectArchive.write(archive,plan));assertTrue(Arrays.equals(before,Files.readAllBytes(archive)));
    }
    @Test void conflictsNeverOverwriteAndChangedDestinationIsRechecked() throws Exception {
        Path root=temp();var repo=new TextRepository(root);repo.create("existing","keep");var files=Map.of("new","new text","existing","replacement");
        assertEquals(List.of("existing"),repo.previewBatch(files,Set.of()));assertThrows(IOException.class,()->repo.createBatch(files,Set.of()));assertFalse(repo.exists("new"));assertEquals("keep",repo.read("existing"));
        repo.create("Mixed","keep case");assertFalse(repo.previewBatch(Map.of("mixed","new"),Set.of()).isEmpty());
        var plan=new ProjectArchive.Plan(Map.of("later","data"),Set.of(),4);assertTrue(repo.previewBatch(plan.documents(),plan.directories()).isEmpty());repo.create("later","racing write");assertThrows(IOException.class,()->repo.createBatch(plan.documents(),plan.directories()));assertEquals("racing write",repo.read("later"));
    }
    @Test void invalidAndAmbiguousPathsAndManifestAreRejected() throws Exception {
        Path root=temp();Path archive=root.resolve("bad.zip");
        for(String name:List.of("commands/../escape.txt","commands/C:/escape.txt","commands/a\\escape.txt","commands/CON.txt","other/file.txt")){
            zip(archive,contents(name,"bad".getBytes(StandardCharsets.UTF_8)));assertThrows(IOException.class,()->ProjectArchive.read(archive));
        }
        var entries=contents("commands/A.txt",new byte[0]);entries.put("commands/a.txt",new byte[0]);zip(archive,entries);assertThrows(IOException.class,()->ProjectArchive.read(archive));
        entries=contents("commands/a.txt",new byte[0]);entries.put("commands/a.txt/",new byte[0]);zip(archive,entries);assertThrows(IOException.class,()->ProjectArchive.read(archive));
        zip(archive,Map.of("commands/a.txt",new byte[0]));assertThrows(IOException.class,()->ProjectArchive.read(archive));
        entries=contents("commands/a.txt",new byte[0]);entries.put("genesis-project.properties","format=genesis-commands\nversion=2\n".getBytes(StandardCharsets.UTF_8));zip(archive,entries);assertThrows(IOException.class,()->ProjectArchive.read(archive));
    }
    @Test void malformedUtf8LargeEntriesAndCancelledExportLeaveNoFinalArchive() throws Exception {
        Path root=temp(),archive=root.resolve("bad.zip");zip(archive,contents("commands/a.txt",new byte[]{(byte)0xc3,0x28}));assertThrows(IOException.class,()->ProjectArchive.read(archive));
        zip(archive,contents("commands/a.txt",new byte[TextRepository.MAX_BYTES+1]));assertThrows(IOException.class,()->ProjectArchive.read(archive));
        var huge=contents("commands/f0.txt",new byte[TextRepository.MAX_BYTES]);
        for(int i=1;i<9;i++)huge.put("commands/f"+i+".txt",new byte[TextRepository.MAX_BYTES]);
        zip(archive,huge);assertThrows(IOException.class,()->ProjectArchive.read(archive));
        Path out=root.resolve("cancelled.zip");var plan=new ProjectArchive.Plan(Map.of("a","x"),Set.of(),1);
        Thread.currentThread().interrupt();try{assertThrows(IOException.class,()->ProjectArchive.write(out,plan));}finally{Thread.interrupted();}
        assertFalse(Files.exists(out));try(var files=Files.list(root)){assertFalse(files.anyMatch(p->p.getFileName().toString().endsWith(".tmp")));}
    }
    @Test void failedBatchRollsBackCreatedFilesAndOnlyNewFolders() throws Exception {
        Path root=temp();var repo=new TextRepository(root);repo.createDirectory("keep");repo.create("original","keep text");
        assertThrows(IOException.class,()->repo.createBatch(Map.of("a","created","missing/b","failure"),Set.of()));assertFalse(repo.exists("a"));assertEquals("keep text",repo.read("original"));
        assertThrows(IOException.class,()->repo.createBatch(Map.of(),Set.of("early","missing/late","keep")));assertFalse(repo.directoryExists("early"));assertTrue(repo.directoryExists("keep"));
    }
    @Test void truncatedZipAndCorruptCrcAreRejected() throws Exception {
        Path archive=temp().resolve("broken.zip");
        zip(archive,contents("commands/a.txt","x".getBytes(StandardCharsets.UTF_8)));
        byte[] bytes=Files.readAllBytes(archive);Files.write(archive,Arrays.copyOf(bytes,bytes.length-1));
        assertThrows(IOException.class,()->ProjectArchive.read(archive));
        var entries=contents("commands/a.txt","TEXT_CRC_TEST".getBytes(StandardCharsets.UTF_8));
        try(var out=new ZipOutputStream(Files.newOutputStream(archive))){for(var item:entries.entrySet()){
            var crc=new CRC32();crc.update(item.getValue());var entry=new ZipEntry(item.getKey());entry.setMethod(ZipEntry.STORED);entry.setSize(item.getValue().length);entry.setCrc(crc.getValue());out.putNextEntry(entry);out.write(item.getValue());out.closeEntry();
        }}
        bytes=Files.readAllBytes(archive);byte[] marker="TEXT_CRC_TEST".getBytes(StandardCharsets.UTF_8);boolean corrupted=false;
        for(int i=0;i<=bytes.length-marker.length;i++){boolean found=true;for(int j=0;j<marker.length;j++)if(bytes[i+j]!=marker[j])found=false;if(found){bytes[i]^=1;corrupted=true;break;}}
        assertTrue(corrupted);Files.write(archive,bytes);assertThrows(IOException.class,()->ProjectArchive.read(archive));
    }
    @Test void implicitParentsAndLimitsAreCheckedBeforeImport() throws Exception {
        Path root=temp(),archive=root.resolve("nested.zip");zip(archive,contents("commands/a/b/c.txt","x".getBytes(StandardCharsets.UTF_8)));var plan=ProjectArchive.read(archive);assertEquals(Set.of("a","a/b"),plan.directories());
        assertThrows(IOException.class,()->plan.beneath("../escape"));assertThrows(IOException.class,()->plan.beneath("a/".repeat(15)+"end"));
        var docs=new TreeMap<String,String>();for(int i=0;i<ProjectArchive.MAX_ENTRIES+1;i++)docs.put("f"+i,"");assertThrows(IOException.class,()->ProjectArchive.write(root.resolve("oversize.zip"),new ProjectArchive.Plan(docs,Set.of(),0)));assertFalse(Files.exists(root.resolve("oversize.zip")));
    }
}
