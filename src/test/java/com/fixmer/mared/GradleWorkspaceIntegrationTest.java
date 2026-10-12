package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import static org.junit.jupiter.api.Assertions.*;

/** Process fixtures verify orchestration; they are explicitly NOT a Gradle distribution. */
class GradleWorkspaceIntegrationTest {
    private static Path wrapper()throws Exception{
        Path root=Files.createTempDirectory("wrapper tools with spaces ");Path src=root.resolve("src/org/gradle/wrapper/GradleWrapperMain.java");Files.createDirectories(src.getParent());
        Files.writeString(src,"""
            package org.gradle.wrapper;
            import java.nio.file.*;
            public class GradleWrapperMain{
                public static void main(String[]args)throws Exception{
                    String task=args[args.length-1];Path project=Path.of(args[1]);
                    if(!args[0].equals("--project-dir"))throw new AssertionError("Missing project directory");
                    Files.writeString(project.resolve(".genesis-build/argv.txt"),String.join("\\n",args));
                    System.out.println("Привет из process fixture");System.out.flush();
                    if(task.equals("wait")||task.equals("live"))Thread.sleep(task.equals("wait")?5000:1500);
                    if(task.equals("fail")){System.err.println("fixture failed");System.exit(23);}
                    if(task.equals("flood")){for(int i=0;i<100;i++)System.out.print("x".repeat(4096));System.out.println("TAIL_MARKER");}
                    if(task.equals("stdin"))System.out.println("EOF="+System.in.read());
                    if(task.equals("build")){Path out=project.resolve("build/libs/example.jar");Files.createDirectories(out.getParent());try(var j=new java.util.jar.JarOutputStream(Files.newOutputStream(out))){j.putNextEntry(new java.util.jar.JarEntry("fixture.txt"));j.write("fixture artifact".getBytes(java.nio.charset.StandardCharsets.UTF_8));j.closeEntry();}}
                    System.out.println("FIXTURE_FINISHED");
                }
            }
            """);Path classes=Files.createDirectory(root.resolve("classes"));assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"-encoding","UTF-8","-d",classes.toString(),src.toString()));
        Path jar=root.resolve("gradle-wrapper.jar");try(var out=new JarOutputStream(Files.newOutputStream(jar));var paths=Files.walk(classes)){for(Path p:paths.filter(Files::isRegularFile).toList()){out.putNextEntry(new JarEntry(classes.relativize(p).toString().replace('\\','/')));out.write(Files.readAllBytes(p));out.closeEntry();}}
        Files.writeString(root.resolve("gradle-wrapper.properties"),"distributionUrl=https://example.com/fixture.zip\n");return jar;
    }
    private static Path project()throws Exception{Path p=Files.createTempDirectory("project with spaces ; ");Files.writeString(p.resolve("build.gradle"),"// process fixture only\n");return p;}
    private static GradleBuildRunner.Settings settings(Path wrapper){return new GradleBuildRunner.Settings(Path.of(System.getProperty("java.home")),wrapper);}
    private static GradleBuildRunner.Result finish(GradleBuildRunner runner)throws Exception{GradleBuildRunner.Result[] result={null};long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);while(runner.busy()&&System.nanoTime()<end){runner.poll(x->result[0]=x);Thread.sleep(5);}assertFalse(runner.busy());assertNotNull(result[0]);return result[0];}
    @Test void workspaceSetupPreservesSourcesSupportsBuildFilesTestsAndPortableExport()throws Exception{
        Path root=Files.createTempDirectory("gradle-setup-");var store=new SourceProjects(root);store.create("demo",SourceProjects.Language.JAVA);String before=store.read("demo","src/Main.java");store.initializeGradle("demo");assertTrue(store.hasGradle("demo"));assertEquals(before,store.read("demo","src/Main.java"));assertTrue(store.files("demo").contains("build.gradle"));assertTrue(store.read("demo","build.gradle").contains("JavaLanguageVersion.of(21)"));assertThrows(IOException.class,()->store.initializeGradle("demo"));
        store.createFile("demo","tests/demo/Test.java");assertTrue(store.files("demo").contains("tests/demo/Test.java"));assertEquals(1,store.javaSources("demo",Map.of()).size());store.setGradleTask("demo",":module:build");assertEquals(":module:build",store.gradleTask("demo"));
        Path configuredJdk=BuildToolsIntegrationTest.jdk(root);store.setBuildTool("java_home",configuredJdk.toString());store.setBuildTool("wrapper_jar",wrapper().toString());assertEquals(configuredJdk,store.buildTools().javaHome());assertThrows(IOException.class,()->store.setBuildTool("java_home","relative/path"));
        Path projectWrapper=root.resolve("demo/gradle/wrapper");Files.createDirectories(projectWrapper);Path fixtureWrapper=wrapper();Files.copy(fixtureWrapper,projectWrapper.resolve("gradle-wrapper.jar"));Files.writeString(projectWrapper.resolve("gradle-wrapper.properties"),"distributionUrl=example\n");Files.writeString(root.resolve("demo/gradlew.bat"),"@echo off\n");
        try(var zip=new java.util.zip.ZipFile(store.exportJava("demo",Map.of()).toFile())){assertNotNull(zip.getEntry("build.gradle"));assertNotNull(zip.getEntry("settings.gradle"));assertNotNull(zip.getEntry("tests/demo/Test.java"));assertNull(zip.getEntry(".build-tools.properties"));assertNotNull(zip.getEntry("gradlew.bat"));assertNotNull(zip.getEntry("gradle/wrapper/gradle-wrapper.properties"));assertTrue(Arrays.equals(Files.readAllBytes(fixtureWrapper),zip.getInputStream(zip.getEntry("gradle/wrapper/gradle-wrapper.jar")).readAllBytes()));}
    }
    @Test void realChildProcessReceivesExactArgumentsStreamsOutputAndListsArtifacts()throws Exception{
        Path p=project(),wrapper=wrapper();try(var runner=new GradleBuildRunner(5000)){assertTrue(runner.start(p,settings(wrapper),"build"));assertFalse(runner.start(p,settings(wrapper),"build"));var result=finish(runner);assertEquals(GradleBuildRunner.Status.SUCCESS,result.status());assertTrue(result.output().contains("Привет"));assertEquals(1,result.artifacts().size());String arguments=Files.readString(p.resolve(".genesis-build/argv.txt"));assertTrue(arguments.contains(p.toString()));assertTrue(arguments.contains("--no-daemon"));assertTrue(arguments.contains("--console=plain"));}
        try(var runner=new GradleBuildRunner(5000)){runner.start(p,settings(wrapper),"live");String live="";long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);while(!live.contains("Привет")&&System.nanoTime()<end){live+=runner.drainOutput();Thread.sleep(5);}assertTrue(live.contains("Привет"));assertTrue(runner.busy());assertEquals(GradleBuildRunner.Status.SUCCESS,finish(runner).status());}
    }
    @Test void failedTasksTimeoutCancellationCloseAndRestartAreHandled()throws Exception{
        Path p=project(),wrapper=wrapper();try(var runner=new GradleBuildRunner(5000)){runner.start(p,settings(wrapper),"fail");var failed=finish(runner);assertEquals(GradleBuildRunner.Status.FAILED,failed.status());assertEquals(23,failed.exitCode());runner.start(p,settings(wrapper),"wait");Thread.sleep(150);runner.stop();assertEquals(GradleBuildRunner.Status.CANCELLED,finish(runner).status());runner.start(p,settings(wrapper),"stdin");assertTrue(finish(runner).output().contains("EOF=-1"));runner.start(p,settings(wrapper),"wait");runner.close();assertEquals(GradleBuildRunner.Status.CANCELLED,finish(runner).status());assertFalse(runner.start(p,settings(wrapper),"build"));}
        try(var runner=new GradleBuildRunner(100)){runner.start(p,settings(wrapper),"wait");assertEquals(GradleBuildRunner.Status.TIMEOUT,finish(runner).status());}
    }
    @Test void verboseOutputIsBoundedWithoutFailingBuildAndKeepsItsTail()throws Exception{
        try(var runner=new GradleBuildRunner(5000)){runner.start(project(),settings(wrapper()),"flood");var result=finish(runner);assertEquals(GradleBuildRunner.Status.SUCCESS,result.status());assertTrue(result.truncated());assertTrue(result.output().contains("TAIL_MARKER"));assertTrue(result.output().length()<=GradleBuildRunner.MAX_OUTPUT_CHARS+64);}
    }
    @Test void invalidTasksAndToolsDoNotStartProcesses()throws Exception{
        Path p=project();try(var runner=new GradleBuildRunner(5000)){
            for(String task:List.of("--init-script","build;anything","build test","../build")){runner.start(p,settings(wrapper()),task);assertEquals(GradleBuildRunner.Status.INPUT_ERROR,finish(runner).status());assertFalse(Files.exists(p.resolve(".genesis-build/argv.txt")));}
            runner.start(p,settings(p.resolve("missing.jar")),"build");assertEquals(GradleBuildRunner.Status.INPUT_ERROR,finish(runner).status());
        }
        assertTrue(GradleBuildRunner.validTask(":module:build"));assertFalse(GradleBuildRunner.validAbsolutePath("relative"));assertThrows(IllegalArgumentException.class,()->new GradleBuildRunner(0));
    }
    @Test void delayedReaderOutputCannotContaminateAnotherBuild()throws Exception{
        Path p=project();try(var runner=new GradleBuildRunner(5000)){
            runner.start(p,settings(wrapper()),"wait");
            var tokenField=GradleBuildRunner.class.getDeclaredField("cancellation");tokenField.setAccessible(true);
            Object oldToken=tokenField.get(runner);runner.stop();assertEquals(GradleBuildRunner.Status.CANCELLED,finish(runner).status());
            runner.start(p,settings(wrapper()),"live");
            // Model delivery by the previous reader after cancellation and after the new capture was reset.
            var delivery=GradleBuildRunner.class.getDeclaredMethod("append",String.class,java.util.concurrent.atomic.AtomicBoolean.class);delivery.setAccessible(true);
            delivery.invoke(runner,"STALE_PREVIOUS_BUILD",oldToken);
            var result=finish(runner);assertEquals(GradleBuildRunner.Status.SUCCESS,result.status());
            assertFalse(result.output().contains("STALE_PREVIOUS_BUILD"));assertFalse(runner.drainOutput().contains("STALE_PREVIOUS_BUILD"));
        }
    }

}
