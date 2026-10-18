package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.genesis.technology.minecraft.*;
import java.nio.file.*;import java.util.*;import java.util.zip.*;import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class ModDevDependenciesIntegrationTest {
 private static ProjectDependencies.Config config(){return new ProjectDependencies.Config(List.of(),List.of(),false);}
 private static SourceProjects store()throws Exception{var s=new SourceProjects(Files.createTempDirectory("mdk-deps-"));s.createNeoForge("demo");return s;}
 @Test void officialMdkDependenciesUseTheModDevConfigurationsAndTemplate()throws Exception{
  var s=store();String original=s.read("demo","build.gradle");
  var c=config().add(ProjectDependencies.Dependency.library(ProjectDependencies.Mode.LIBRARY,"g:library:1")).add(ProjectDependencies.Dependency.embedded("g:embedded:2","[2,3)"))
   .add(new ProjectDependencies.Dependency(ProjectDependencies.Mode.MOD,"g:required:1","requiredmod","[1,)","required","BOTH"))
   .add(new ProjectDependencies.Dependency(ProjectDependencies.Mode.MOD,"g:optional:1","optionalmod","[1,)","optional","CLIENT"));
  s.setDependencies("demo",s.dependencyState("demo"),c);
  String script=s.read("demo",ProjectDependencies.SCRIPT),toml=s.read("demo",ProjectDependencies.TEMPLATE_TOML);
  assertTrue(script.contains("additionalRuntimeClasspath 'g:library:1'"));assertTrue(script.contains("jarJar(implementation('g:embedded')) { version { strictly '[2,3)'; prefer '2'"));
  assertTrue(script.contains("compileOnly 'g:optional:1'"));assertTrue(script.contains("runtimeOnly 'g:optional:1'"));
  assertFalse(script.contains("runs {"));assertFalse(script.contains("tasks.named('jarJar')"));
  assertTrue(toml.contains("[[dependencies.demo]]\nmodId=\"requiredmod\""));assertTrue(toml.contains("modId=\"${mod_id}\""));
  assertFalse(Files.exists(s.projectDirectory("demo").resolve(ProjectDependencies.TOML)));assertTrue(s.read("demo","build.gradle").startsWith(original));
  var repeated=s.dependencyState("demo");s.setDependencies("demo",repeated,c);assertEquals(repeated,s.dependencyState("demo"));
  s.setDependencies("demo",repeated,config());assertFalse(s.read("demo",ProjectDependencies.SCRIPT).contains("g:embedded"));s.validateDependencies("demo");
 }
 @Test void templateEditsAndModIdPropertyChangesAreConflictChecked()throws Exception{
  var s=store();var before=s.dependencyState("demo");String props=before.files().get("gradle.properties");s.save("demo","gradle.properties",props,props+"\n// external change\n");
  assertThrows(IOException.class,()->s.setDependencies("demo",before,config()));
  var c=config().add(new ProjectDependencies.Dependency(ProjectDependencies.Mode.MOD,"g:core:1","coremod","[1,)","required","BOTH"));s.setDependencies("demo",s.dependencyState("demo"),c);
  String toml=s.read("demo",ProjectDependencies.TEMPLATE_TOML);s.save("demo",ProjectDependencies.TEMPLATE_TOML,toml,toml.replace("ordering=\"NONE\"","ordering=\"AFTER\""));
  var edited=s.dependencyState("demo");assertThrows(IOException.class,()->s.setDependencies("demo",edited,c));assertEquals(edited,s.dependencyState("demo"));
 }
 @Test void selfDependenciesAreResolvedFromGradleProperties()throws Exception{
  var s=store();var c=config().add(new ProjectDependencies.Dependency(ProjectDependencies.Mode.MOD,"g:self:1","demo","[1,)","required","BOTH"));
  var before=s.dependencyState("demo");assertThrows(IOException.class,()->s.setDependencies("demo",before,c));assertEquals(before,s.dependencyState("demo"));
 }
 @Test void detectorReadsOfficialMetadataAndEntrypointWithoutRunningGradle()throws Exception{
  var s=store();var info=MinecraftProjectDetector.analyze(s.projectDirectory("demo"));assertEquals(MinecraftLoader.NEOFORGE,info.loader());assertEquals("demo",info.modId());assertEquals("[1.21.1]",info.minecraftVersion());assertEquals("[21.1.235,)",info.loaderVersion());assertEquals(ProjectType.MINECRAFT_MOD,info.projectType());assertEquals("com.example.mod_demo.ExampleMod",info.mainClass());
  assertTrue(info.structure().metadataFiles().contains(ProjectDependencies.TEMPLATE_TOML));
  assertTrue(info.dependencies().contains("mod:neoforge:[21.1.235,) (required)"));
  assertTrue(info.dependencies().contains("mod:minecraft:[1.21.1] (required)"));
  assertTrue(info.dependencies().stream().noneMatch(d->d.startsWith("mod:demo:")));
 }
 @Test void localLibrariesAndExportKeepTheOfficialBuildStandalone()throws Exception{
  var s=store();s.setDependencies("demo",s.dependencyState("demo"),new ProjectDependencies.Config(List.of(),List.of(),true));
  assertTrue(s.read("demo",ProjectDependencies.SCRIPT).contains("additionalRuntimeClasspath fileTree"));
  try(var z=new ZipFile(s.exportProject("demo",Map.of()).toFile())){assertNotNull(z.getEntry(ProjectDependencies.TEMPLATE_TOML));assertNotNull(z.getEntry("gradle/wrapper/gradle-wrapper.jar"));assertNotNull(z.getEntry(ProjectDependencies.SCRIPT));}
 }
}
