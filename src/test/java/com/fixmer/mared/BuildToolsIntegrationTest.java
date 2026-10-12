package com.fixmer.mared;

import com.fixmer.genesis.technology.scripts.BuildTools;
import com.fixmer.genesis.technology.scripts.SourceProjects;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Static tool fixtures deliberately contain no working compiler or executable Wrapper classes. */
class BuildToolsIntegrationTest {
    @Test void copiedPathsNormalizeBeforePersistenceAndJavaDoesNotRequireWrapper() throws Exception {
        Path root=Files.createTempDirectory("tools-normalize-");Path home=jdk(root),jar=wrapper(root);
        var store=new SourceProjects(root.resolve("projects"));store.create("demo",SourceProjects.Language.JAVA);
        store.setBuildTool("java_home","\""+home.resolve("bin")+"\"");
        assertEquals(home,store.javaHome());assertEquals(home.toString(),store.buildTool("java_home"));
        assertEquals("",store.buildTool("wrapper_jar"));
        assertThrows(IOException.class,store::buildTools);
        store.setBuildTool("wrapper_jar",jar.getParent().toString());assertEquals(jar,store.buildTools().wrapperJar());
        store.setBuildTool("wrapper_jar",jar.resolveSibling("gradle-wrapper.properties").toString());assertEquals(jar.toString(),store.buildTool("wrapper_jar"));
        assertTrue(BuildTools.validAbsolutePath("\""+home+"\""));assertFalse(BuildTools.validAbsolutePath(home+"\ninvalid"));
    }
    @Test void createdProjectWrapperTakesPriorityAndIsNotSilentlyReplaced() throws Exception {
        Path root=Files.createTempDirectory("tools-project-wrapper-");Path home=jdk(root),global=wrapper(root);
        var store=new SourceProjects(root.resolve("projects"));store.create("demo",SourceProjects.Language.JAVA);store.initializeGradle("demo");
        store.setBuildTool("java_home",home.toString());store.setBuildTool("wrapper_jar",global.toString());
        Path own=store.projectDirectory("demo").resolve("gradle/wrapper/gradle-wrapper.jar");Files.createDirectories(own.getParent());Files.copy(global,own);
        Files.copy(global.resolveSibling("gradle-wrapper.properties"),own.resolveSibling("gradle-wrapper.properties"));
        assertEquals(own,store.buildTools("demo").wrapperJar());Files.delete(own.resolveSibling("gradle-wrapper.properties"));
        assertThrows(IOException.class,()->store.buildTools("demo"));assertEquals(global,store.buildTools().wrapperJar());
    }
    static Path jdk(Path root) throws Exception {
        Path home=root.resolve("jdk home with spaces ; "+"long-name-".repeat(10));Files.createDirectories(home.resolve("bin"));
        String ext=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?".exe":"";
        for(String name:List.of("java","javac")){Path p=home.resolve("bin/"+name+ext);Files.writeString(p,"static fixture; never executed");assertTrue(p.toFile().setExecutable(true));}
        Files.writeString(home.resolve("release"),"JAVA_VERSION=\"21.0.7\"\n");return home;
    }
    static Path wrapper(Path root) throws Exception {
        Path dir=root.resolve("wrapper tools with spaces");Files.createDirectories(dir);Path jar=dir.resolve("gradle-wrapper.jar");
        try(var out=new JarOutputStream(Files.newOutputStream(jar))){out.putNextEntry(new JarEntry("org/gradle/wrapper/GradleWrapperMain.class"));out.write(new byte[]{1,2,3});out.closeEntry();}
        Files.writeString(dir.resolve("gradle-wrapper.properties"),"distributionUrl=https\\://services.gradle.org/distributions/gradle-9.2.1-bin.zip\n");return jar;
    }
    @Test void settingsPersistAcrossStoresPreserveOtherKeysAndStayOutsideExport() throws Exception {
        Path root=Files.createTempDirectory("tools-settings-");Path home=jdk(root),jar=wrapper(root);var store=new SourceProjects(root.resolve("projects"));store.create("demo",SourceProjects.Language.JAVA);
        assertEquals("",store.buildTool("wrapper_jar"));store.setBuildTool("java_home",home.toString());store.setBuildTool("wrapper_jar",jar.toString());
        Path config=root.resolve("projects/.build-tools.properties");Files.writeString(config,Files.readString(config)+"custom_key=keep\n");
        var reopened=new SourceProjects(root.resolve("projects"));assertEquals(home.toString(),reopened.buildTool("java_home"));assertEquals(jar.toString(),reopened.buildTool("wrapper_jar"));
        assertEquals(home,reopened.buildTools().javaHome());assertEquals(jar,reopened.buildTools().wrapperJar());
        reopened.setBuildTool("java_home",home.toString());assertTrue(Files.readString(config).contains("custom_key=keep"));
        try(var zip=new ZipFile(reopened.exportJava("demo",Map.of()).toFile())){assertTrue(zip.stream().noneMatch(e->e.getName().contains("build-tools")));}
        assertThrows(IllegalArgumentException.class,()->new BuildTools.Settings(Path.of("relative"),jar));
        assertFalse(BuildTools.validAbsolutePath("relative"));assertFalse(BuildTools.validAbsolutePath("\0bad"));assertFalse(BuildTools.validAbsolutePath("/"+"a".repeat(2048)));
    }
    @Test void invalidJdkWrapperOrPropertiesCannotReplaceSavedConfiguration() throws Exception {
        Path root=Files.createTempDirectory("tools-invalid-");Path home=jdk(root),jar=wrapper(root);var store=new SourceProjects(root.resolve("projects"));store.create("demo",SourceProjects.Language.JAVA);
        store.setBuildTool("java_home",home.toString());store.setBuildTool("wrapper_jar",jar.toString());Path config=root.resolve("projects/.build-tools.properties");byte[] before=Files.readAllBytes(config);
        for(String version:List.of("17.0.1","garbage","99999999999999999999")){
            Files.writeString(home.resolve("release"),"JAVA_VERSION=\""+version+"\"\n");assertThrows(IOException.class,()->store.setBuildTool("java_home",home.toString()));assertTrue(Arrays.equals(before,Files.readAllBytes(config)));
        }
        Files.writeString(home.resolve("release"),"JAVA_VERSION=\"21\"\n");
        String ext=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?".exe":"";Files.delete(home.resolve("bin/javac"+ext));assertThrows(IOException.class,()->BuildTools.validateJavaHome(home));
        Path props=jar.resolveSibling("gradle-wrapper.properties");
        for(String text:List.of("", "distributionUrl=relative.zip\n","distributionUrl=ftp://example.com/gradle.zip\n","distributionUrl=https://example.com/g.zip\ndistributionSha256Sum=bad\n","distributionUrl=\\uNOPE\n","x="+"x".repeat(16384))){
            Files.writeString(props,text);assertThrows(IOException.class,()->store.setBuildTool("wrapper_jar",jar.toString()));assertTrue(Arrays.equals(before,Files.readAllBytes(config)));
        }
        Files.delete(props);assertThrows(IOException.class,()->BuildTools.validateWrapper(jar));
        Path wrong=root.resolve("wrong.jar");try(var out=new JarOutputStream(Files.newOutputStream(wrong))){out.putNextEntry(new JarEntry("unrelated.txt"));out.closeEntry();}
        assertThrows(IOException.class,()->BuildTools.validateWrapper(wrong));Files.writeString(wrong,"not a jar");assertThrows(IOException.class,()->BuildTools.validateWrapper(wrong));
        assertThrows(IOException.class,()->store.setBuildTool("unknown",jar.toString()));assertThrows(IOException.class,()->store.setBuildTool("java_home","relative"));assertTrue(Arrays.equals(before,Files.readAllBytes(config)));
    }
    @Test void recheckDetectsChangedToolsAndMalformedLocalConfiguration() throws Exception {
        Path root=Files.createTempDirectory("tools-recheck-");Path home=jdk(root),jar=wrapper(root);var store=new SourceProjects(root.resolve("projects"));store.create("demo",SourceProjects.Language.JAVA);
        store.setBuildTool("java_home",home.toString());assertThrows(IOException.class,store::buildTools);store.setBuildTool("wrapper_jar",jar.toString());assertNotNull(store.buildTools());
        Files.writeString(jar.resolveSibling("gradle-wrapper.properties"),"distributionUrl=file:///local/gradle.zip\ndistributionSha256Sum="+"0".repeat(64)+"\n");assertNotNull(store.buildTools());
        Files.delete(jar);assertThrows(IOException.class,store::buildTools);
        Path config=root.resolve("projects/.build-tools.properties");Files.writeString(config,"java_home=\\uNOPE\n");assertThrows(IOException.class,()->store.buildTool("java_home"));
        Files.writeString(config,"x="+"x".repeat(16384));assertThrows(IOException.class,()->store.buildTool("java_home"));
        assertFalse(Files.exists(root.resolve("projects/demo/build")));
    }
}
