package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import static org.junit.jupiter.api.Assertions.*;

class GradleTasksIntegrationTest {
    @Test void groupedTasksModulePathsDescriptionsAndRulesAreParsedWithoutInventedTasks(){
        var c=GradleTasks.parse("""
            Configuration log - not a task
            Tasks runnable from root project 'example'
            ------------------------------------------
            Build tasks
            -----------
            build - Assembles and tests this project.
            client:build - Builds the client модуль.
            :server:build - Builds the server.

            Other tasks
            -----------
            compileJava
            build - repeated description
            --bad - flag
            ../escape - unsafe
            custom task - unsupported

            Rules
            -----
            Pattern: clean<TaskName>: Cleans the output files of a task.
            phantom - not a task
            BUILD SUCCESSFUL in 2s
            """,false);
        assertEquals(4,c.tasks().size());assertFalse(c.limited());assertEquals(1,c.filter("CLIENT модуль").size());assertEquals("client:build",c.filter("client модуль").get(0).path());assertEquals(3,c.filter("build tasks").size());assertTrue(c.filter("missing").isEmpty());assertThrows(UnsupportedOperationException.class,()->c.tasks().clear());assertThrows(IllegalArgumentException.class,()->new GradleTasks.Task("--all","",""));
    }
    @Test void ansiEmptyDescriptionsDuplicatesAndOutputLimitsAreBounded(){
        var c=GradleTasks.parse("\u001b[32mOther tasks\u001b[0m\r\n---\r\nbuild\r\nbuild - Full description\r\n",false);assertEquals(1,c.tasks().size());assertEquals("Full description",c.tasks().get(0).description());
        assertTrue(GradleTasks.parse("",true).limited());var many=new StringBuilder("Other tasks\n---\n");for(int i=0;i<600;i++)many.append("task").append(i).append(" - description\n");var capped=GradleTasks.parse(many.toString(),false);assertEquals(512,capped.tasks().size());assertTrue(capped.limited());assertTrue(GradleTasks.parse("x".repeat(GradleTasks.MAX_CHARS+1),false).limited());assertTrue(GradleTasks.parse("Other tasks\n---\n"+"x".repeat(2049),false).limited());assertTrue(GradleTasks.parse("Random heading\n---\nphantom - noise",false).tasks().isEmpty());
    }
    static Path wrapper()throws Exception{
        Path root=Files.createTempDirectory("tasks-wrapper-");Path src=root.resolve("org/gradle/wrapper/GradleWrapperMain.java");Files.createDirectories(src.getParent());Files.writeString(src,"""
            package org.gradle.wrapper;import java.nio.file.*;public class GradleWrapperMain{public static void main(String[] args)throws Exception{
                Path p=Path.of(args[1]);Files.writeString(p.resolve("argv.txt"),String.join("\\n",args));
                if(Files.exists(p.resolve("fail"))){System.out.println("configuration failed");System.exit(7);}
                if(Files.exists(p.resolve("wait")))Thread.sleep(5000);
                if(!args[args.length-1].equals("--all")||!args[args.length-2].equals("tasks"))throw new AssertionError("Wrong task listing arguments");
                System.out.println("Build tasks\\n-----------\\nbuild - Builds root\\nmodule:build - Builds module\\n\\nOther tasks\\n-----------\\ncustom\\n\\nBUILD SUCCESSFUL");
            }}
            """);Path classes=Files.createDirectory(root.resolve("classes"));assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"-d",classes.toString(),src.toString()));Path jar=root.resolve("gradle-wrapper.jar");try(var out=new JarOutputStream(Files.newOutputStream(jar));var paths=Files.walk(classes)){for(Path p:paths.filter(Files::isRegularFile).toList()){out.putNextEntry(new JarEntry(classes.relativize(p).toString().replace('\\','/')));out.write(Files.readAllBytes(p));out.closeEntry();}}Files.writeString(root.resolve("gradle-wrapper.properties"),"distributionUrl=https://example.test/fixture.zip\n");return jar;
    }
    static GradleBuildRunner.Result finish(GradleBuildRunner runner)throws Exception{GradleBuildRunner.Result[] r={null};long end=System.nanoTime()+10_000_000_000L;while(runner.busy()&&System.nanoTime()<end){runner.poll(v->r[0]=v);Thread.sleep(5);}assertNotNull(r[0]);assertFalse(runner.busy());return r[0];}
    @Test void listingUsesRealChildProcessExactArgumentsAndDoesNotDiscoverOldArtifacts()throws Exception{
        Path p=Files.createTempDirectory("tasks-project-");Files.writeString(p.resolve("build.gradle"),"// fixture");Files.createDirectories(p.resolve("build/libs"));Files.writeString(p.resolve("build/libs/old.jar"),"old output");var settings=new GradleBuildRunner.Settings(Path.of(System.getProperty("java.home")),wrapper());
        try(var runner=new GradleBuildRunner(5000)){assertTrue(runner.startTaskListing(p,settings));assertFalse(runner.start(p,settings,"build"));var r=finish(runner);assertEquals(GradleBuildRunner.Status.SUCCESS,r.status());assertEquals(3,GradleTasks.parse(r.output(),r.truncated()).tasks().size());assertTrue(r.artifacts().isEmpty());assertTrue(r.inventory().artifacts().isEmpty());assertTrue(Files.readString(p.resolve("argv.txt")).endsWith("tasks\n--all"));assertEquals("old output",Files.readString(p.resolve("build/libs/old.jar")));
            Files.writeString(p.resolve("fail"),"");runner.startTaskListing(p,settings);assertEquals(GradleBuildRunner.Status.FAILED,finish(runner).status());Files.delete(p.resolve("fail"));Files.writeString(p.resolve("wait"),"");runner.startTaskListing(p,settings);Thread.sleep(100);runner.stop();assertEquals(GradleBuildRunner.Status.CANCELLED,finish(runner).status());}
        try(var runner=new GradleBuildRunner(100)){runner.startTaskListing(p,settings);assertEquals(GradleBuildRunner.Status.TIMEOUT,finish(runner).status());}
    }
}
