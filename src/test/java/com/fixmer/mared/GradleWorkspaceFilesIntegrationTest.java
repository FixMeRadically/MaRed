package com.fixmer.mared;

import com.fixmer.genesis.technology.scripts.SourceProjects;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GradleWorkspaceFilesIntegrationTest {
    @Test void setupPreservesSourcesAndKeepsTestsOutsideDirectRun() throws Exception {
        Path root=Files.createTempDirectory("gradle-files-");var store=new SourceProjects(root);
        store.create("demo",SourceProjects.Language.JAVA);String source=store.read("demo","src/Main.java");
        store.initializeGradle("demo");assertTrue(store.hasGradle("demo"));
        assertEquals(source,store.read("demo","src/Main.java"));
        assertTrue(store.read("demo","build.gradle").contains("JavaLanguageVersion.of(21)"));
        assertTrue(store.read("demo","build.gradle").contains("version = '0.4.0'"));
        store.createFile("demo","tests/demo/Test.java");
        store.save("demo","tests/demo/Test.java","","this is deliberately invalid Java");
        assertTrue(store.files("demo").contains("tests/demo/Test.java"));
        assertEquals(Set.of("src/Main.java"),store.javaSources("demo",Map.of()).keySet());
        Files.delete(root.resolve("demo/src/Main.java"));Files.delete(root.resolve("demo/src"));
        assertTrue(store.files("demo").containsAll(List.of("build.gradle","settings.gradle","tests/demo/Test.java")));
    }
    @Test void existingConfigurationsAndOtherLanguagesAreNeverOverwritten() throws Exception {
        Path root=Files.createTempDirectory("gradle-conflicts-");var store=new SourceProjects(root);
        for(String name:List.of("build.gradle","settings.gradle","build.gradle.kts","settings.gradle.kts")){
            String id="p"+name.replace('.','_');store.create(id,SourceProjects.Language.JAVA);
            store.createFile(id,name);store.save(id,name,"","// user configuration\n");
            assertThrows(IOException.class,()->store.initializeGradle(id));
            assertEquals("// user configuration\n",store.read(id,name));
            assertEquals(2,store.files(id).size());
        }
        store.create("py",SourceProjects.Language.PYTHON);
        assertThrows(IOException.class,()->store.initializeGradle("py"));
        assertFalse(Files.exists(root.resolve("py/build.gradle")));
        assertFalse(SourceProjects.validFile("../build.gradle"));
        assertFalse(SourceProjects.validFile("gradle/wrapper/gradle-wrapper.jar"));
    }
    @Test void exportIncludesBuildDraftsTestsAndWrapperWithoutSavingOrLeakingEditorState() throws Exception {
        Path root=Files.createTempDirectory("gradle-export-");var store=new SourceProjects(root);
        store.create("demo",SourceProjects.Language.JAVA);store.initializeGradle("demo");
        store.createFile("demo","tests/demo/Test.java");String before=store.read("demo","build.gradle"),draft=before+"// draft\n";
        store.checkpoint("demo","build.gradle",before,draft);
        Path wrapper=root.resolve("demo/gradle/wrapper");Files.createDirectories(wrapper);
        byte[] binary={0,1,2,(byte)255};Files.write(wrapper.resolve("gradle-wrapper.jar"),binary);
        Files.writeString(wrapper.resolve("gradle-wrapper.properties"),"distributionUrl=example\n");
        Files.writeString(root.resolve("demo/gradlew.bat"),"@echo off\n");
        Files.writeString(root.resolve(".build-tools.properties"),"java_home=private-machine-path\n");
        var edits=Map.of("build.gradle",new SourceProjects.Recovery(before,draft));
        try(var zip=new ZipFile(store.exportJava("demo",edits).toFile())){
            assertEquals(draft,new String(zip.getInputStream(zip.getEntry("build.gradle")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            for(String name:List.of("settings.gradle","tests/demo/Test.java","gradlew.bat","gradle/wrapper/gradle-wrapper.properties"))assertNotNull(zip.getEntry(name));
            assertTrue(Arrays.equals(binary,zip.getInputStream(zip.getEntry("gradle/wrapper/gradle-wrapper.jar")).readAllBytes()));
            assertNull(zip.getEntry(".build-tools.properties"));
            assertTrue(zip.stream().noneMatch(e->e.getName().contains(".genesis-recovery")));
        }
        assertEquals(before,store.read("demo","build.gradle"));
        store.save("demo","build.gradle",before,"external\n");
        assertThrows(IOException.class,()->store.exportJava("demo",edits));
    }
}
