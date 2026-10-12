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
 @Test void starterIsIndependentGradleProjectWithPreservedWrapperAndMetadata()throws Exception{
  Path root=Files.createTempDirectory("mod-starter-");var store=store(root);Path wrapper=store.buildTools().wrapperJar();byte[] bytes=Files.readAllBytes(wrapper);var project=store.createNeoForge("friend_mod");
  assertEquals("friend_mod",project.id());assertTrue(store.importedGradle(project.id()));assertTrue(store.hasGradle(project.id()));assertEquals("build",store.gradleTask(project.id()));
  Path dir=store.projectDirectory(project.id());assertTrue(Arrays.equals(bytes,Files.readAllBytes(dir.resolve("gradle/wrapper/gradle-wrapper.jar"))));assertTrue(Arrays.equals(Files.readAllBytes(wrapper.resolveSibling("gradle-wrapper.properties")),Files.readAllBytes(dir.resolve("gradle/wrapper/gradle-wrapper.properties"))));
  assertTrue(store.files(project.id()).contains(NeoForgeProjectTemplate.entry(project.id())));assertTrue(store.files(project.id()).contains("src/main/resources/META-INF/neoforge.mods.toml"));
  String source=store.read(project.id(),NeoForgeProjectTemplate.entry(project.id()));assertTrue(source.contains("@Mod(ExampleMod.MOD_ID)"));assertTrue(source.contains("genesis.generated.mod_friend_mod"));
  String build=store.read(project.id(),"build.gradle");assertTrue(build.contains("7.1.38"));assertFalse(build.contains("genesis-core"));assertFalse(build.contains("com.fixmer.mared"));
  assertTrue(store.read(project.id(),"src/main/resources/META-INF/neoforge.mods.toml").contains("[1.21.1]"));assertTrue(store.read(project.id(),"gradle.properties").contains("mod_version=0.4.0"));
  assertThrows(IOException.class,()->store.javaSources(project.id(),Map.of()));assertThrows(IOException.class,()->store.createNeoForge("friend_mod"));assertEquals(build,store.read(project.id(),"build.gradle"));
  assertEquals(dir.resolve("gradle/wrapper/gradle-wrapper.jar"),store.buildTools(project.id()).wrapperJar());
 }
 @Test void fullExportContainsStandaloneWrapperScriptsAndUnsavedModSource()throws Exception{
  Path root=Files.createTempDirectory("mod-export-");var store=store(root);store.createNeoForge("hello_mod");String path=NeoForgeProjectTemplate.entry("hello_mod"),before=store.read("hello_mod",path),draft=before+"// draft\n";
  try(var zip=new ZipFile(store.exportJava("hello_mod",Map.of(path,new SourceProjects.Recovery(before,draft))).toFile())){
   for(String file:List.of("build.gradle","settings.gradle","gradle.properties","gradlew","gradlew.bat","gradle/wrapper/gradle-wrapper.jar","gradle/wrapper/gradle-wrapper.properties","src/main/resources/META-INF/neoforge.mods.toml","README.md",".gitignore"))assertNotNull(zip.getEntry(file));
   assertEquals(draft,new String(zip.getInputStream(zip.getEntry(path)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));assertNull(zip.getEntry(".build-tools.properties"));
   String batch=new String(zip.getInputStream(zip.getEntry("gradlew.bat")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(batch.contains("%~dp0gradle\\wrapper\\gradle-wrapper.jar"));assertTrue(batch.contains("%JAVA_HOME%\\bin\\java.exe"));
  }assertEquals(before,store.read("hello_mod",path));
 }
 @Test void invalidIdsAndMissingToolsDoNotPublishProjects()throws Exception{
  Path root=Files.createTempDirectory("mod-refuse-");var store=new SourceProjects(root.resolve("managed"));
  for(String id:List.of("mared","minecraft","neoforge","UPPER","x","../bad","a-b","x".repeat(49)))assertFalse(NeoForgeProjectTemplate.validId(id));
  assertTrue(NeoForgeProjectTemplate.validId("class"));assertTrue(NeoForgeProjectTemplate.entry("class").contains("mod_class"));
  assertThrows(IOException.class,()->store.createNeoForge("no_tools"));assertFalse(Files.exists(store.root().resolve("no_tools")));
  assertThrows(IOException.class,()->store.createNeoForge("mared"));assertThrows(IllegalArgumentException.class,()->NeoForgeProjectTemplate.files("bad-id"));
  assertThrows(IllegalArgumentException.class,()->NeoForgeProjectTemplate.entry("../bad"));
  var files=NeoForgeProjectTemplate.files("valid_mod");assertThrows(UnsupportedOperationException.class,()->files.put("x","x"));
  try(var entries=Files.list(store.root())){assertTrue(entries.noneMatch(p->p.getFileName().toString().startsWith(".create-")));}
 }
}
