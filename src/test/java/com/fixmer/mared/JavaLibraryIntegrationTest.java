package com.fixmer.mared;

import com.fixmer.genesis.technology.scripts.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import static org.junit.jupiter.api.Assertions.*;

class JavaLibraryIntegrationTest {
    private static final JavaSourceRunner.Limits LIMITS=JavaSourceRunner.Limits.defaults();
    private static Map<String,String> source(String body){return Map.of("src/Main.java","public class Main{public static void main(String[]a)throws Exception{"+body+"}}");}
    private static byte[] jar(Map<String,byte[]> entries,String classPath)throws Exception{
        var manifest=new Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");
        if(classPath!=null)manifest.getMainAttributes().putValue("Class-Path",classPath);
        var bytes=new ByteArrayOutputStream();try(var out=new JarOutputStream(bytes,manifest)){for(var e:entries.entrySet()){out.putNextEntry(new JarEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}}return bytes.toByteArray();
    }
    private static byte[] library(String name,String text,byte[] dependency,Map<String,byte[]> resources)throws Exception{
        Path root=Files.createTempDirectory("java-library-");
        try{
            Path src=root.resolve(name+".java"),classes=Files.createDirectory(root.resolve("classes"));Files.writeString(src,text);
            var args=new ArrayList<String>(List.of("-proc:none","-encoding","UTF-8","-d",classes.toString()));
            if(dependency!=null){Path dep=root.resolve("dependency.jar");Files.write(dep,dependency);args.addAll(List.of("-cp",dep.toString()));}args.add(src.toString());
            assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
            var entries=new TreeMap<String,byte[]>(resources);try(var walk=Files.walk(classes)){for(Path p:walk.filter(Files::isRegularFile).toList())entries.put(classes.relativize(p).toString().replace('\\','/'),Files.readAllBytes(p));}
            return jar(entries,null);
        }finally{try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
    private static JavaSourceRunner.Result run(Map<String,byte[]> libraries,String body){return new JavaSourceRunner().run(source(body),libraries,"Main",LIMITS,new JavaSourceRunner.Cancellation());}
    @Test void realLibrariesWorkAtCompileAndRuntimeWithTransitiveClassesAndResources()throws Exception{
        byte[] b=library("B","package example;public class B{public static String value(){return \"library-B\";}}",null,Map.of());
        byte[] a=library("A","package example;public class A{public static String value(){return B.value();}}",b,Map.of("message.txt","Привет из JAR".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var result=run(Map.of("a.jar",a,"b.jar",b),"System.out.println(example.A.value());try(var in=Main.class.getClassLoader().getResourceAsStream(\"message.txt\")){System.out.println(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}");
        assertEquals(JavaSourceRunner.Status.SUCCESS,result.status());assertTrue(result.output().contains("library-B"));assertTrue(result.output().contains("Привет из JAR"));
        assertEquals(JavaSourceRunner.Status.COMPILE_ERROR,run(Map.of(),"System.out.println(example.A.value());").status());
        var missing=run(Map.of("a.jar",a),"System.out.println(example.A.value());");assertEquals(JavaSourceRunner.Status.RUNTIME_ERROR,missing.status());assertTrue(missing.output().contains("example/B"));
    }
    @Test void changedLibraryIsUsedOnNextRunAndPreviousSnapshotIsIndependent()throws Exception{
        var root=Files.createTempDirectory("java-library-store-");var store=new SourceProjects(root);store.create("demo",SourceProjects.Language.JAVA);
        byte[] old=library("Value","package example;public class Value{public static int number(){return 1;}}",null,Map.of());
        byte[] next=library("Value","package example;public class Value{public static int number(){return 2;}}",null,Map.of());
        Path path=root.resolve("demo/libraries/value.jar");Files.write(path,old);var snapshot=store.javaLibraries("demo");Files.write(path,next);
        assertTrue(run(snapshot,"System.out.println(example.Value.number());").output().contains("1"));
        assertTrue(run(store.javaLibraries("demo"),"System.out.println(example.Value.number());").output().contains("2"));
        assertTrue(Arrays.equals(next,Files.readAllBytes(path)));assertThrows(UnsupportedOperationException.class,()->snapshot.put("new.jar",old));
    }
    @Test void malformedArchivesManifestEscapesAndZipBombsAreRejectedBeforeCompile()throws Exception{
        for(var data:List.of(new byte[]{1,2,3},jar(Map.of("ok.txt",new byte[]{1}),"../outside.jar"),jar(Map.of("../outside.class",new byte[]{1}),null),jar(Map.of("META-INF/INDEX.LIST",new byte[]{1}),null),jar(Map.of("huge.bin",new byte[JavaSourceRunner.MAX_LIBRARY_BYTES+1]),null))){
            var result=run(Map.of("bad.jar",data),"System.out.println(\"must not execute\");");assertEquals(JavaSourceRunner.Status.INPUT_ERROR,result.status());assertEquals("prepare",result.phase());assertFalse(result.output().contains("must not execute"));
        }
        assertEquals(JavaSourceRunner.Status.INPUT_ERROR,run(Map.of("../escape.jar",new byte[]{1}),"").status());
        assertEquals(JavaSourceRunner.Status.INPUT_ERROR,run(Map.of("x.jar",new byte[]{1},"X.JAR",new byte[]{1}),"").status());
        var many=new HashMap<String,byte[]>();for(int i=0;i<33;i++)many.put("j"+i+".jar",new byte[]{1});assertEquals(JavaSourceRunner.Status.INPUT_ERROR,run(many,"").status());
    }
    @Test void libraryDirectoryRefusesLinksFoldersAndOversizeButIgnoresPlainNotes()throws Exception{
        var root=Files.createTempDirectory("java-library-path-");var store=new SourceProjects(root);store.create("demo",SourceProjects.Language.JAVA);Path folder=root.resolve("demo/libraries");
        Files.writeString(folder.resolve("README.md"),"ignored");assertTrue(store.javaLibraries("demo").isEmpty());
        Files.createDirectory(folder.resolve("nested"));assertThrows(IOException.class,()->store.javaLibraries("demo"));Files.delete(folder.resolve("nested"));
        Files.write(folder.resolve("empty.jar"),new byte[0]);assertThrows(IOException.class,()->store.javaLibraries("demo"));Files.delete(folder.resolve("empty.jar"));
        try(var channel=java.nio.channels.FileChannel.open(folder.resolve("large.jar"),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){channel.position(JavaSourceRunner.MAX_LIBRARY_BYTES);channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));}
        assertThrows(IOException.class,()->store.javaLibraries("demo"));Files.delete(folder.resolve("large.jar"));
        if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")){Path outside=Files.createTempFile("external-library-",".jar");Files.createSymbolicLink(folder.resolve("linked.jar"),outside);assertThrows(IOException.class,()->store.javaLibraries("demo"));}
        store.create("py",SourceProjects.Language.PYTHON);assertThrows(IOException.class,()->store.javaLibraries("py"));
    }
    @Test void annotationProcessorServicesAreNotExecutedDuringCompilation()throws Exception{
        byte[] dependency=jar(Map.of("META-INF/services/javax.annotation.processing.Processor","missing.BadProcessor\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)),null);
        var result=run(Map.of("processor.jar",dependency),"System.out.println(\"no processor\");");assertEquals(JavaSourceRunner.Status.SUCCESS,result.status());assertTrue(result.output().contains("no processor"));
    }
    @Test void editorRunAdapterActuallyIncludesProjectLibraries()throws Exception{
        var root=Files.createTempDirectory("java-library-adapter-");var store=new SourceProjects(root);store.create("demo",SourceProjects.Language.JAVA);
        Files.write(root.resolve("demo/libraries/value.jar"),library("Value","package example;public class Value{public static String value(){return \"adapter-library\";}}",null,Map.of()));
        String baseline=store.read("demo","src/Main.java");
        try(var runs=new com.fixmer.mared.technology.scripts.JavaProjectRuns(store)){
            assertTrue(runs.start("demo","Main",Map.of("src/Main.java",new SourceProjects.Recovery(baseline,source("System.out.println(example.Value.value());").get("src/Main.java")))));
            JavaSourceRunner.Result[] result={null};long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
            while(runs.busy()&&System.nanoTime()<deadline){runs.poll(value->result[0]=value);Thread.sleep(5);}
            assertFalse(runs.busy());assertEquals(JavaSourceRunner.Status.SUCCESS,result[0].status());assertTrue(result[0].output().contains("adapter-library"));assertEquals(baseline,store.read("demo","src/Main.java"));
        }
    }

    @Test void automaticCompilerPluginsCannotRunFromUserLibraries()throws Exception{
        byte[] dependency=library("AutoPlugin","public class AutoPlugin implements com.sun.source.util.Plugin{public String getName(){return \"GenesisTest\";}public boolean autoStart(){return true;}public void init(com.sun.source.util.JavacTask task,String...args){throw new IllegalStateException(\"must not start during compilation\");}}",null,Map.of("META-INF/services/com.sun.source.util.Plugin","AutoPlugin\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var result=run(Map.of("plugin.jar",dependency),"System.out.println(\"plain source\");");assertEquals(JavaSourceRunner.Status.SUCCESS,result.status());assertTrue(result.output().contains("plain source"));
    }

}
