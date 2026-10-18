package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NeoForgeProjectTemplateIntegrationTest {
 static SourceProjects store(Path root)throws Exception{
  var store=new SourceProjects(root.resolve("managed"));store.setBuildTool("java_home",BuildToolsIntegrationTest.jdk(root).toString());store.setBuildTool("wrapper_jar",BuildToolsIntegrationTest.wrapper(root).toString());return store;
 }
 @Test void starterIsIndependentGradleProjectWithPinnedWrapperAndMetadata()throws Exception{
  Path root=Files.createTempDirectory("mod-starter-");var store=store(root);Path wrapper=store.buildTools().wrapperJar();byte[] bytes=Files.readAllBytes(wrapper);var project=store.createNeoForge("friend_mod");
  assertEquals("friend_mod",project.id());assertTrue(store.importedGradle(project.id()));assertTrue(store.hasGradle(project.id()));assertEquals("build",store.gradleTask(project.id()));
  Path dir=store.projectDirectory(project.id());assertTrue(Arrays.equals(NeoForgeProjectTemplate.wrapperJar(),Files.readAllBytes(dir.resolve("gradle/wrapper/gradle-wrapper.jar"))));assertEquals(NeoForgeProjectTemplate.wrapperProperties(),Files.readString(dir.resolve("gradle/wrapper/gradle-wrapper.properties")));assertTrue(Files.readString(wrapper.resolveSibling("gradle-wrapper.properties")).contains("9.2.1"));
  assertTrue(store.files(project.id()).contains(NeoForgeProjectTemplate.entry(project.id())));assertTrue(store.files(project.id()).contains("src/main/templates/META-INF/neoforge.mods.toml"));
  String source=store.read(project.id(),NeoForgeProjectTemplate.entry(project.id()));assertTrue(source.contains("@Mod(ExampleMod.MODID)"));assertTrue(source.contains("com.example.mod_friend_mod"));
  String build=store.read(project.id(),"build.gradle");assertTrue(build.contains("2.0.148"));assertFalse(build.contains("genesis-core"));assertFalse(build.contains("com.fixmer.mared"));
  assertTrue(store.read(project.id(),"src/main/templates/META-INF/neoforge.mods.toml").contains("${minecraft_version_range}"));assertTrue(store.read(project.id(),"gradle.properties").contains("mod_version=1.0.0"));
  assertThrows(IOException.class,()->store.javaSources(project.id(),Map.of()));assertThrows(IOException.class,()->store.createNeoForge("friend_mod"));assertEquals(build,store.read(project.id(),"build.gradle"));
  assertEquals(dir.resolve("gradle/wrapper/gradle-wrapper.jar"),store.buildTools(project.id()).wrapperJar());
 }
 @Test void fullExportContainsStandaloneWrapperScriptsAndUnsavedModSource()throws Exception{
  Path root=Files.createTempDirectory("mod-export-");var store=store(root);store.createNeoForge("hello_mod");String path=NeoForgeProjectTemplate.entry("hello_mod"),before=store.read("hello_mod",path),draft=before+"// draft\n";
  try(var zip=new ZipFile(store.exportJava("hello_mod",Map.of(path,new SourceProjects.Recovery(before,draft))).toFile())){
   for(String file:List.of("build.gradle","settings.gradle","gradle.properties","gradlew","gradlew.bat","gradle/wrapper/gradle-wrapper.jar","gradle/wrapper/gradle-wrapper.properties","src/main/templates/META-INF/neoforge.mods.toml","README.md",".gitignore"))assertNotNull(zip.getEntry(file));
   assertEquals(draft,new String(zip.getInputStream(zip.getEntry(path)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));assertNull(zip.getEntry(".build-tools.properties"));
   String batch=new String(zip.getInputStream(zip.getEntry("gradlew.bat")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(batch.contains("%APP_HOME%")&&batch.contains("gradle-wrapper.jar"));assertTrue(batch.contains("JAVA_HOME")&&batch.contains("JAVA_EXE"));
  }assertEquals(before,store.read("hello_mod",path));
 }
 @Test void invalidIdsAndMissingToolsDoNotPublishProjects()throws Exception{
  Path root=Files.createTempDirectory("mod-refuse-");var store=new SourceProjects(root.resolve("managed"));
  for(String id:List.of("mared","minecraft","neoforge","UPPER","x","../bad","a-b","x".repeat(49)))assertFalse(NeoForgeProjectTemplate.validId(id));
  assertTrue(NeoForgeProjectTemplate.validId("class"));assertTrue(NeoForgeProjectTemplate.entry("class").contains("mod_class"));
  assertNotNull(store.createNeoForge("no_tools"));assertTrue(Files.exists(store.root().resolve("no_tools")));
  assertThrows(IOException.class,()->store.createNeoForge("mared"));assertThrows(IllegalArgumentException.class,()->NeoForgeProjectTemplate.files("bad-id"));
  assertThrows(IllegalArgumentException.class,()->NeoForgeProjectTemplate.entry("../bad"));
  var files=NeoForgeProjectTemplate.files("valid_mod");assertThrows(UnsupportedOperationException.class,()->files.put("x","x"));
  try(var entries=Files.list(store.root())){assertTrue(entries.noneMatch(p->p.getFileName().toString().startsWith(".create-")));}
 }
}
