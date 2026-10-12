package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import java.io.*;import java.nio.file.*;import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class BuildToolConsistencyIntegrationTest {
 private static Path root()throws Exception{return Files.createTempDirectory("tool-consistency-");}
 private static Path props(Path jar){return jar.resolveSibling("gradle-wrapper.properties");}
 private static SourceProjects store(Path root)throws Exception{var s=new SourceProjects(root.resolve("projects"));s.create("demo",SourceProjects.Language.JAVA);s.setBuildTool("java_home",BuildToolsIntegrationTest.jdk(root).toString());s.setBuildTool("wrapper_jar",BuildToolsIntegrationTest.wrapper(root).toString());return s;}
 @Test public void ownPropertiesWithoutJarNeverUsesGlobalWrapper()throws Exception{
  Path r=root();var s=store(r);Path own=s.projectDirectory("demo").resolve("gradle/wrapper/gradle-wrapper.properties");Files.createDirectories(own.getParent());Files.writeString(own,"distributionUrl=https://example.test/own-version.zip\n");assertThrows(IOException.class,()->s.buildTools("demo"));assertNotNull(s.buildTools());
 }
 @Test public void incompleteNestedWrapperCannotBeHiddenByRootJar()throws Exception{
  Path r=root();Path top=BuildToolsIntegrationTest.wrapper(r).getParent();Path nested=top.resolve("gradle/wrapper");Files.createDirectories(nested);Files.writeString(nested.resolve("gradle-wrapper.properties"),"distributionUrl=https://example.test/project.zip\n");assertThrows(IOException.class,()->BuildTools.wrapperJar(top.toString()));assertNotNull(BuildTools.wrapperJar(top.resolve("gradle-wrapper.jar").toString()));
 }
 @Test public void nonRegularNestedJarCannotBeHiddenByRootJar()throws Exception{
  Path r=root();Path top=BuildToolsIntegrationTest.wrapper(r).getParent();Files.createDirectories(top.resolve("gradle/wrapper/gradle-wrapper.jar"));assertThrows(IOException.class,()->BuildTools.wrapperJar(top.toString()));
 }
 @Test public void semanticDuplicateDistributionUrlsAreRejected()throws Exception{
  Path jar=BuildToolsIntegrationTest.wrapper(root());Files.writeString(props(jar),"distributionUrl=https://example.test/first.zip\ndistribution\\u0055rl https://example.test/second.zip\n");assertThrows(IOException.class,()->BuildTools.validateWrapper(jar));
 }
 @Test public void duplicateChecksumsCannotMaskAnInvalidValue()throws Exception{
  Path jar=BuildToolsIntegrationTest.wrapper(root());Files.writeString(props(jar),Files.readString(props(jar))+"distributionSha256Sum=broken\ndistributionSha256Sum="+"a".repeat(64)+"\n");assertThrows(IOException.class,()->BuildTools.validateWrapper(jar));
 }
 @Test public void duplicateReleaseVersionsCannotMaskUnsupportedJdk()throws Exception{
  Path home=BuildToolsIntegrationTest.jdk(root());Files.writeString(home.resolve("release"),"JAVA_VERSION=\"17\"\nJAVA_VERSION=\"21\"\n");assertThrows(IOException.class,()->BuildTools.validateJavaHome(home));
 }
 @Test public void duplicateLocalToolPathsAreRejectedWithoutReplacingConfiguration()throws Exception{
  Path r=root();var s=store(r);Path config=s.root().resolve(".build-tools.properties");String before=Files.readString(config)+"java\\u005fhome="+s.buildTool("java_home").replace("\\","\\\\")+"\n";Files.writeString(config,before);assertThrows(IOException.class,()->s.buildTool("java_home"));assertThrows(IOException.class,()->s.setBuildTool("wrapper_jar",BuildToolsIntegrationTest.wrapper(root()).toString()));assertEquals(before,Files.readString(config));
 }
 @Test public void fallbackAndDirectJavaRemainIndependentOfPartialWrapper()throws Exception{
  Path r=root();var s=store(r);assertEquals(s.buildTools().wrapperJar(),s.buildTools("demo").wrapperJar());Path own=s.projectDirectory("demo").resolve("gradle/wrapper/gradle-wrapper.properties");Files.createDirectories(own.getParent());Files.writeString(own,"broken=yes");assertNotNull(s.javaHome());assertThrows(IOException.class,()->s.buildTools("demo"));Files.delete(own);assertEquals(s.buildTools().wrapperJar(),s.buildTools("demo").wrapperJar());
 }
 @Test public void repeatedChildFailuresCancellationsAndRestartsDoNotPoisonRunner()throws Exception{
  Path r=root();Files.writeString(r.resolve("build.gradle"),"// process fixture");var settings=new GradleBuildRunner.Settings(Path.of(System.getProperty("java.home")),BuildStabilityIntegrationTest.wrapper());
  try(var runner=new GradleBuildRunner(5000)){
   for(int i=0;i<8;i++){
    assertTrue(runner.start(r,settings,"fail"));var failed=BuildStabilityIntegrationTest.finish(runner);assertEquals(GradleBuildRunner.Status.FAILED,failed.status());assertEquals(23,failed.exitCode());
    assertTrue(runner.start(r,settings,"wait"));assertFalse(runner.start(r,settings,"stdin"));String live="";long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);while(!live.contains("Привет")&&System.nanoTime()<end){live+=runner.drainOutput();Thread.sleep(5);}assertTrue(live.contains("Привет"));runner.stop();var cancelled=BuildStabilityIntegrationTest.finish(runner);assertEquals(GradleBuildRunner.Status.CANCELLED,cancelled.status());assertTrue(cancelled.inventory().artifacts().isEmpty());
    assertTrue(runner.start(r,settings,"stdin"));var recovered=BuildStabilityIntegrationTest.finish(runner);assertEquals(GradleBuildRunner.Status.SUCCESS,recovered.status());assertTrue(recovered.output().contains("EOF=-1"));assertFalse(recovered.output().contains("fixture failed"));assertFalse(recovered.output().contains("Cancelled;"));
   }
   for(int i=0;i<3;i++){assertTrue(runner.start(r,settings,"wait",new GradleBuildRunner.Options(50,1)));assertEquals(GradleBuildRunner.Status.TIMEOUT,BuildStabilityIntegrationTest.finish(runner).status());}
   assertTrue(runner.start(r,settings,"stdin"));assertEquals(GradleBuildRunner.Status.SUCCESS,BuildStabilityIntegrationTest.finish(runner).status());
  }
 }
 public static void main(String[]args)throws Exception{var t=new BuildToolConsistencyIntegrationTest();t.ownPropertiesWithoutJarNeverUsesGlobalWrapper();t.incompleteNestedWrapperCannotBeHiddenByRootJar();t.nonRegularNestedJarCannotBeHiddenByRootJar();t.semanticDuplicateDistributionUrlsAreRejected();t.duplicateChecksumsCannotMaskAnInvalidValue();t.duplicateReleaseVersionsCannotMaskUnsupportedJdk();t.duplicateLocalToolPathsAreRejectedWithoutReplacingConfiguration();t.fallbackAndDirectJavaRemainIndependentOfPartialWrapper();t.repeatedChildFailuresCancellationsAndRestartsDoNotPoisonRunner();System.out.println("Build tool consistency scenarios passed: 9; partial project wrappers, nested precedence, semantic duplicate properties, JDK release and local settings, refusal without writes, 28 repeated real child JVM launches; static tool fixtures, not an actual Gradle distribution");}
}
