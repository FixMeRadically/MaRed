package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.SourceProjects;
import com.fixmer.genesis.technology.scripts.SourceProjects.Language;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceProjectsIntegrationTest {
    @Test void createsAllRealLanguageSourcesAndIndependentDirectories() throws Exception {
        var root=Files.createTempDirectory("source-projects-");var store=new SourceProjects(root);
        for(Language l:Language.values()){var p=store.create(l.name(),l);assertEquals(l,store.project(p.id()).language());
            assertEquals(l.starter,store.read(p.id(),"src/"+l.entry));assertTrue(Files.isDirectory(root.resolve(p.id()+"/libraries")));assertTrue(Files.isDirectory(root.resolve(p.id()+"/resources")));}
        assertEquals(4,store.list().projects().size());assertFalse(Files.exists(root.resolve("commands")));
    }
    @Test void nestedFilesSaveAtomicallyAndRefuseExternalChangesOrMissingFiles() throws Exception {
        var root=Files.createTempDirectory("source-projects-");var store=new SourceProjects(root);store.create("demo",Language.PYTHON);
        store.createFile("demo","src/utils/tool.py");store.save("demo","src/utils/tool.py","","привет\n");assertEquals("привет\n",store.read("demo","src/utils/tool.py"));
        Files.writeString(root.resolve("demo/src/utils/tool.py"),"external");assertThrows(IOException.class,()->store.save("demo","src/utils/tool.py","привет\n","overwrite"));
        assertEquals("external",store.read("demo","src/utils/tool.py"));Files.delete(root.resolve("demo/src/utils/tool.py"));
        assertThrows(IOException.class,()->store.save("demo","src/utils/tool.py","external","resurrect"));assertFalse(Files.exists(root.resolve("demo/src/utils/tool.py")));
    }
    @Test void invalidPathsAliasesAndSymlinksNeverEscapeTheProject() throws Exception {
        var root=Files.createTempDirectory("source-projects-");var store=new SourceProjects(root);store.create("demo",Language.JAVA);
        for(String id:new String[]{"../outside","A/B","CON","NUL.txt","demo"})assertThrows(IOException.class,()->store.create(id,Language.JAVA));
        assertThrows(IOException.class,()->store.create("DEMO",Language.JAVA));
        for(String p:new String[]{"../x.java","src/../../x.java","libraries/x.jar","src/CON.java","src/x.exe","src/a\\x.py"})assertThrows(IOException.class,()->store.createFile("demo",p));
        store.createFile("demo","src/Util.java");assertThrows(IOException.class,()->store.createFile("demo","src/util.java"));
        Path outside=Files.createTempDirectory("source-outside-");
        try {Files.createSymbolicLink(root.resolve("demo/src/link"),outside);}catch(IOException|UnsupportedOperationException|SecurityException unavailable){return;}
        assertThrows(IOException.class,()->store.createFile("demo","src/link/evil.java"));assertThrows(IOException.class,()->store.files("demo"));assertFalse(Files.exists(outside.resolve("evil.java")));
    }
    @Test void recoveryPreservesBaselineAndDoesNotWriteSource() throws Exception {
        var root=Files.createTempDirectory("source-projects-");var store=new SourceProjects(root);store.create("demo",Language.RUST);String original=store.read("demo","src/main.rs");
        store.checkpoint("demo","src/main.rs",original,"draft\n");var reopened=new SourceProjects(root);var recovery=reopened.recovery("demo","src/main.rs");
        assertEquals(original,recovery.baseline());assertEquals("draft\n",recovery.text());assertEquals(original,reopened.read("demo","src/main.rs"));
        reopened.checkpoint("demo","src/main.rs",original,original);assertNull(reopened.recovery("demo","src/main.rs"));
    }
    @Test void malformedMetadataAndInvalidUtf8AreReportedWithoutDroppingOtherProjects() throws Exception {
        var root=Files.createTempDirectory("source-projects-");var store=new SourceProjects(root);store.create("good",Language.CPP);store.create("bad",Language.JAVA);
        Files.writeString(root.resolve("bad/genesis-project.properties"),"format=1\nlanguage=UNKNOWN");assertEquals(1,store.list().projects().size());assertEquals(1,store.list().errors().size());
        Files.write(root.resolve("good/src/main.cpp"),new byte[]{(byte)0xC3,(byte)0x28});assertThrows(IOException.class,()->store.read("good","src/main.cpp"));
    }
    @Test void boundedWritesAndRecoveryRejectOversizeAndCorruptRecords() throws Exception {
        var root=Files.createTempDirectory("source-projects-");var store=new SourceProjects(root);store.create("demo",Language.PYTHON);String original=store.read("demo","src/main.py");
        assertThrows(IOException.class,()->store.save("demo","src/main.py",original,"я".repeat(SourceProjects.MAX_BYTES/2+1)));assertEquals(original,store.read("demo","src/main.py"));
        store.checkpoint("demo","src/main.py",original,"draft");try(var entries=Files.list(root.resolve(".genesis-recovery"))){Files.write(entries.findFirst().orElseThrow(),new byte[]{0,0,0,1,127,127,127,127});}
        assertThrows(IOException.class,()->store.recovery("demo","src/main.py"));assertEquals(original,store.read("demo","src/main.py"));
    }
}
