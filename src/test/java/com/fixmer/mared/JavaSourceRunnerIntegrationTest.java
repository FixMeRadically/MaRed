package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class JavaSourceRunnerIntegrationTest {
    private final JavaSourceRunner runner=new JavaSourceRunner();
    private static Map<String,String> source(String body){return Map.of("src/Main.java","public class Main {public static void main(String[] args) throws Exception {"+body+"}}");}
    private JavaSourceRunner.Result run(String body){return runner.run(source(body),"Main",JavaSourceRunner.Limits.defaults(),new JavaSourceRunner.Cancellation());}
    @Test void realCompilerRunsUnicodeAndMultiplePackagedSources() {
        var result=runner.run(Map.of("src/demo/App.java","package demo; public class App {public static void main(String[] a){System.out.println(Helper.text());}}","src/demo/Helper.java","package demo; final class Helper {static String text(){return \"Привет, Genesis\";}}"),"demo.App",JavaSourceRunner.Limits.defaults(),new JavaSourceRunner.Cancellation());
        assertEquals(JavaSourceRunner.Status.SUCCESS,result.status());assertTrue(result.output().contains("Привет, Genesis"));assertEquals(0,result.exitCode());
    }
    @Test void syntaxErrorsAndRuntimeExitAreDifferentAndDoNotExitHost() {
        var compilation=run("not valid java;");assertEquals(JavaSourceRunner.Status.COMPILE_ERROR,compilation.status());assertEquals("compile",compilation.phase());assertTrue(compilation.output().contains("Main.java"));
        assertEquals(JavaSourceRunner.Status.COMPILE_ERROR,run("System.out.println(com.fixmer.mared.Mared.class);").status());
        var exited=run("System.exit(7);");assertEquals(JavaSourceRunner.Status.RUNTIME_ERROR,exited.status());assertEquals(7,exited.exitCode());
        var noMain=runner.run(Map.of("src/Main.java","public class Main {}"),"Main",JavaSourceRunner.Limits.defaults(),new JavaSourceRunner.Cancellation());assertEquals(JavaSourceRunner.Status.RUNTIME_ERROR,noMain.status());
    }
    @Test void timeoutOutputFloodAndClosedStdinAreBounded() {
        var timeout=runner.run(source("while(true){Thread.sleep(10);}"),"Main",new JavaSourceRunner.Limits(20_000,200,1024,64),new JavaSourceRunner.Cancellation());assertEquals(JavaSourceRunner.Status.TIMEOUT,timeout.status());
        var flood=runner.run(source("while(true){System.out.print(\"abcdefghij\");}"),"Main",new JavaSourceRunner.Limits(20_000,3000,1024,64),new JavaSourceRunner.Cancellation());assertEquals(JavaSourceRunner.Status.OUTPUT_LIMIT,flood.status());assertTrue(flood.output().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=1024);
        var stdin=run("System.out.println(System.in.read());");assertEquals(JavaSourceRunner.Status.SUCCESS,stdin.status());assertTrue(stdin.output().contains("-1"));
    }
    @Test void cancellationBeforeStartAndDuringRunStopsWork() throws Exception {
        var cancelled=new JavaSourceRunner.Cancellation();cancelled.cancel();assertEquals(JavaSourceRunner.Status.CANCELLED,runner.run(source("System.out.println(1);"),"Main",JavaSourceRunner.Limits.defaults(),cancelled).status());
        var active=new JavaSourceRunner.Cancellation();var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {var future=pool.submit(()->runner.run(source("while(true){Thread.sleep(10);}"),"Main",JavaSourceRunner.Limits.defaults(),active));long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
            while(!active.phase().equals("run")&&!future.isDone()&&System.nanoTime()<end)Thread.sleep(10);assertEquals("run",active.phase());active.cancel();assertEquals(JavaSourceRunner.Status.CANCELLED,future.get(5,java.util.concurrent.TimeUnit.SECONDS).status());
        }finally{pool.shutdownNow();}
    }
    @Test void pathsEntryPointsLimitsAndUnavailableRuntimeNeverLaunch() {
        for(String path:List.of("../Main.java","src/../../Main.java","src/module-info.java","src/Main.txt"))assertEquals(JavaSourceRunner.Status.INPUT_ERROR,runner.run(Map.of(path,""),"Main",JavaSourceRunner.Limits.defaults(),new JavaSourceRunner.Cancellation()).status());
        assertEquals(JavaSourceRunner.Status.INPUT_ERROR,runner.run(source(""),"-version",JavaSourceRunner.Limits.defaults(),new JavaSourceRunner.Cancellation()).status());
        assertFalse(JavaSourceRunner.validMainClass("demo.Main;anything"));assertTrue(JavaSourceRunner.validMainClass("demo.Main$Nested"));
        assertThrows(IllegalArgumentException.class,()->new JavaSourceRunner.Limits(0,1,1,64));
        assertEquals(JavaSourceRunner.Status.UNAVAILABLE,new JavaSourceRunner(Path.of("missing-jdk-home")).run(source(""),"Main",JavaSourceRunner.Limits.defaults(),new JavaSourceRunner.Cancellation()).status());
    }
    @Test void entryPointChangesPreserveMetadataAndSupportExistingProjects() throws Exception {
        var root=Files.createTempDirectory("java-main-");var store=new SourceProjects(root);store.create("demo",SourceProjects.Language.JAVA);
        Path metadata=root.resolve("demo/genesis-project.properties");String before=Files.readString(metadata)+"# retain comment\ncustom=value\n";Files.writeString(metadata,before);
        assertEquals("Main",store.javaMainClass("demo"));store.setJavaMainClass("demo","demo.App$Nested");assertEquals("demo.App$Nested",store.javaMainClass("demo"));assertTrue(Files.readString(metadata).contains("custom=value"));assertTrue(Files.readString(metadata).contains("# retain comment"));
        store.setJavaMainClass("demo","Main");assertEquals(1,Files.readString(metadata).split("main_class=",-1).length-1);
        assertThrows(java.io.IOException.class,()->store.setJavaMainClass("demo","-version"));
        Files.writeString(metadata,before+"main_class=bad class\n");store.setJavaMainClass("demo","demo.Main");assertEquals("demo.Main",store.javaMainClass("demo"));
    }
    @Test void sourceSnapshotIncludesDraftsRefusesStaleBasesAndDoesNotSaveOriginals() throws Exception {
        var root=Files.createTempDirectory("java-input-");var store=new SourceProjects(root);store.create("demo",SourceProjects.Language.JAVA);String baseline=store.read("demo","src/Main.java");
        var snapshot=store.javaSources("demo",Map.of("src/Main.java",new SourceProjects.Recovery(baseline,source("System.out.println(42);").get("src/Main.java"))));
        assertEquals(JavaSourceRunner.Status.SUCCESS,runner.run(snapshot,"Main",JavaSourceRunner.Limits.defaults(),new JavaSourceRunner.Cancellation()).status());assertEquals(baseline,store.read("demo","src/Main.java"));
        Files.writeString(root.resolve("demo/src/Main.java"),"external");assertThrows(java.io.IOException.class,()->store.javaSources("demo",Map.of("src/Main.java",new SourceProjects.Recovery(baseline,"draft"))));
        store.create("python",SourceProjects.Language.PYTHON);assertThrows(java.io.IOException.class,()->store.javaSources("python",Map.of()));
        assertThrows(java.io.IOException.class,()->store.javaSources("demo",Map.of("src/Missing.java",new SourceProjects.Recovery("","draft"))));
    }
}
