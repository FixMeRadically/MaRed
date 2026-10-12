package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Real local child-JVM orchestration fixtures; no Gradle distribution is simulated as a real build. */
class BuildStabilityIntegrationTest {
 static Path wrapper()throws Exception{var m=GradleWorkspaceIntegrationTest.class.getDeclaredMethod("wrapper");m.setAccessible(true);return (Path)m.invoke(null);}
 static GradleBuildRunner.Result finish(GradleBuildRunner runner)throws Exception{return GradleTasksIntegrationTest.finish(runner);}
 @Test void settingsOnlyRootsAndNonAlphabeticTasksAreSupported()throws Exception{
  Path root=Files.createTempDirectory("settings-only-");Files.writeString(root.resolve("settings.gradle.kts"),"include(\":1-core\")");Files.createDirectories(root.resolve("1-core"));Files.writeString(root.resolve("1-core/build.gradle.kts"),"plugins { java }");
  var store=new SourceProjects(Files.createTempDirectory("settings-managed-"));var p=store.importGradle("multi",root,()->false);assertTrue(store.hasGradle(p.project().id()));assertTrue(Files.exists(root.resolve("settings.gradle.kts")));
  for(String task:List.of(":1-core:_prepare","_compile","core.api:2test",":основа:сборка","generated$task"))assertTrue(GradleBuildRunner.validTask(task));
  for(String task:List.of("--all",":--all","../build","build test"))assertFalse(GradleBuildRunner.validTask(task));
  assertEquals(2,GradleTasks.parse("Other tasks\n---\n:1-core:_prepare\ncore.api:2test\n",false).tasks().size());
  try(var runner=new GradleBuildRunner(5000)){runner.start(root,new GradleBuildRunner.Settings(Path.of(System.getProperty("java.home")),wrapper()),":1-core:_prepare");assertEquals(GradleBuildRunner.Status.SUCCESS,finish(runner).status());}
 }
 @Test void buildLimitsPersistWithoutReplacingProjectConfiguration()throws Exception{
  var store=new SourceProjects(Files.createTempDirectory("build-options-"));store.createEmpty("demo");Path metadata=store.projectDirectory("demo").resolve("genesis-project.properties");Files.writeString(metadata,Files.readString(metadata)+"# keep comment\ncustom_key=keep\n");
  assertEquals(600_000L,store.buildOptions("demo").timeoutMillis());store.setBuildOption("demo","build_timeout_minutes",1440);store.setBuildOption("demo","build_max_workers",0);store.setGradleTask("demo",":1-core:_prepare");
  var reopened=new SourceProjects(store.root());assertEquals(86_400_000L,reopened.buildOptions("demo").timeoutMillis());assertEquals(0,reopened.buildOptions("demo").maxWorkers());assertTrue(Files.readString(metadata).contains("# keep comment\ncustom_key=keep"));assertEquals(":1-core:_prepare",store.gradleTask("demo"));
  String before=Files.readString(metadata);assertThrows(java.io.IOException.class,()->store.setBuildOption("demo","build_timeout_minutes",0));assertThrows(java.io.IOException.class,()->store.setBuildOption("demo","build_max_workers",65));assertThrows(java.io.IOException.class,()->store.setBuildOption("demo","unknown",1));assertEquals(before,Files.readString(metadata));
  Files.writeString(metadata,before.replace("build_timeout_minutes=1440","build_timeout_minutes=999999999999999999"));assertThrows(java.io.IOException.class,()->store.buildOptions("demo"));
 }
 @Test void launchUsesExplicitJdkProjectToolchainsAndPerJobLimits()throws Exception{
  Path root=Files.createTempDirectory("explicit-jdk-");Files.writeString(root.resolve("build.gradle"),"// fixture");Files.writeString(root.resolve("gradle.properties"),"org.gradle.java.home=/missing/path\norg.gradle.java.installations.auto-download=true\n");Files.createDirectories(root.resolve("gradle"));Files.writeString(root.resolve("gradle/gradle-daemon-jvm.properties"),"toolchainVersion=21\n");Path jdk=Path.of(System.getProperty("java.home"));var settings=new GradleBuildRunner.Settings(jdk,wrapper());
  try(var runner=new GradleBuildRunner(1)){
   runner.start(root,settings,"stdin",new GradleBuildRunner.Options(5000,0));var result=finish(runner);assertEquals(GradleBuildRunner.Status.SUCCESS,result.status());String argv=Files.readString(root.resolve(".genesis-build/argv.txt"));assertTrue(argv.contains("-Dorg.gradle.java.home="+jdk));assertFalse(argv.contains("--max-workers"));assertFalse(argv.contains("auto-download=false"));assertTrue(result.output().contains("JVM criteria"));assertTrue(result.output().contains("EOF=-1"));
   runner.start(root,settings,"wait",new GradleBuildRunner.Options(50,3));assertEquals(GradleBuildRunner.Status.TIMEOUT,finish(runner).status());
   runner.start(root,settings,"build",new GradleBuildRunner.Options(5000,4));assertEquals(GradleBuildRunner.Status.SUCCESS,finish(runner).status());assertTrue(Files.readString(root.resolve(".genesis-build/argv.txt")).contains("--max-workers=4"));
  }
 }
 @Test void malformedWrapperIsRejectedBeforeExecutingItsMain()throws Exception{
  Path root=Files.createTempDirectory("wrapper-preflight-");Files.writeString(root.resolve("build.gradle"),"// fixture");Path jar=wrapper();Files.writeString(jar.resolveSibling("gradle-wrapper.properties"),"distributionUrl=missing\n");
  try(var runner=new GradleBuildRunner(5000)){runner.start(root,new GradleBuildRunner.Settings(Path.of(System.getProperty("java.home")),jar),"build");assertEquals(GradleBuildRunner.Status.INPUT_ERROR,finish(runner).status());assertFalse(Files.exists(root.resolve(".genesis-build/argv.txt")));}
 }
 public static void main(String[]args)throws Exception{var t=new BuildStabilityIntegrationTest();t.settingsOnlyRootsAndNonAlphabeticTasksAreSupported();t.buildLimitsPersistWithoutReplacingProjectConfiguration();t.launchUsesExplicitJdkProjectToolchainsAndPerJobLimits();t.malformedWrapperIsRejectedBeforeExecutingItsMain();System.out.println("Build stability integration scenarios passed: 4; real child JVM, settings-only import, task identifiers, persisted limits, JDK arguments, Wrapper preflight, timeout and restart; not an actual Gradle distribution");}
}
