package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.mared.technology.scripts.JavaProjectRuns;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class JavaResourcesExportIntegrationTest {
    private static final String MAIN="public class Main{public static void main(String[]a)throws Exception{try(var in=Main.class.getClassLoader().getResourceAsStream(\"data/message.txt\")){System.out.println(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}}";
    @Test void binaryAndUnicodeResourcesWorkThroughRealEditorRunAdapter()throws Exception{
        Path root=Files.createTempDirectory("java-resources-");var store=new SourceProjects(root);store.create("demo",SourceProjects.Language.JAVA);Path resources=root.resolve("demo/resources/data");Files.createDirectory(resources);Files.writeString(resources.resolve("message.txt"),"Привет из resources");Files.write(resources.resolve("image.bin"),new byte[]{0,1,(byte)255});
        var snapshot=store.javaResources("demo");assertTrue(Arrays.equals(new byte[]{0,1,(byte)255},snapshot.get("data/image.bin")));
        String baseline=store.read("demo","src/Main.java");try(var runs=new JavaProjectRuns(store)){
            assertTrue(runs.start("demo","Main",Map.of("src/Main.java",new SourceProjects.Recovery(baseline,MAIN))));JavaSourceRunner.Result[] result={null};long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
            while(runs.busy()&&System.nanoTime()<end){runs.poll(r->result[0]=r);Thread.sleep(5);}assertFalse(runs.busy());assertEquals(JavaSourceRunner.Status.SUCCESS,result[0].status());assertTrue(result[0].output().contains("Привет из resources"));assertEquals(baseline,store.read("demo","src/Main.java"));
        }
    }
    @Test void invalidResourcesCannotReplaceClassesOrEscapeScratch()throws Exception{
        var runner=new JavaSourceRunner();var source=Map.of("src/Main.java","public class Main{public static void main(String[]a){System.out.println(1);}}");
        for(String path:List.of("../outside","Main.class","demo/../file.txt","/absolute"))assertEquals(JavaSourceRunner.Status.INPUT_ERROR,runner.run(source,Map.of(),Map.of(path,new byte[]{1}),"Main",JavaSourceRunner.Limits.defaults(),new JavaSourceRunner.Cancellation()).status());
        for(var resources:List.of(Map.of("a",new byte[]{1},"a/b",new byte[]{2}),Map.of("Dir/a",new byte[]{1},"dir/b",new byte[]{2})))assertEquals(JavaSourceRunner.Status.INPUT_ERROR,runner.run(source,Map.of(),resources,"Main",JavaSourceRunner.Limits.defaults(),new JavaSourceRunner.Cancellation()).status());
        var result=runner.run(Map.of("src/demo/Main.java","package demo;public class Main{public static void main(String[]a){}}"),Map.of(),Map.of("demo",new byte[]{1}),"demo.Main",JavaSourceRunner.Limits.defaults(),new JavaSourceRunner.Cancellation());assertEquals(JavaSourceRunner.Status.INPUT_ERROR,result.status());
    }
    @Test void exportContainsDraftsMetadataLibrariesAndBinaryResourcesWithoutSaving()throws Exception{
        Path root=Files.createTempDirectory("java-export-");var store=new SourceProjects(root);store.create("demo",SourceProjects.Language.JAVA);store.createFile("demo","src/data/settings.json");String baseline=store.read("demo","src/Main.java");String draft=baseline+"// export draft\n";store.checkpoint("demo","src/Main.java",baseline,draft);
        Files.write(root.resolve("demo/libraries/example.jar"),new byte[]{1,2,3});Files.write(root.resolve("demo/resources/image.bin"),new byte[]{0,(byte)255});Files.writeString(root.resolve("demo/secret.txt"),"included in shared project export");
        Path archive=store.exportJava("demo",Map.of("src/Main.java",new SourceProjects.Recovery(baseline,draft)));assertTrue(archive.startsWith(root.resolve(".exports")));assertEquals(baseline,store.read("demo","src/Main.java"));assertEquals(draft,store.recovery("demo","src/Main.java").text());
        try(var zip=new ZipFile(archive.toFile())){
            assertEquals(draft,new String(zip.getInputStream(zip.getEntry("src/Main.java")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));assertTrue(Arrays.equals(new byte[]{0,(byte)255},zip.getInputStream(zip.getEntry("resources/image.bin")).readAllBytes()));assertNotNull(zip.getEntry("genesis-project.properties"));assertNotNull(zip.getEntry("libraries/example.jar"));assertNotNull(zip.getEntry("src/data/settings.json"));assertNotNull(zip.getEntry("secret.txt"));
            for(var e:Collections.list(zip.entries()))assertFalse(e.getName().contains("recovery"));
        }
        assertEquals(1,store.list().projects().size());assertFalse(store.exportJava("demo",Map.of()).equals(archive));
    }
    @Test void exportIsRepeatableAndRefusesStaleEditsBeforeCreatingOutput()throws Exception{
        Path root=Files.createTempDirectory("java-export-conflict-");var store=new SourceProjects(root);store.create("demo",SourceProjects.Language.JAVA);
        Path a=store.exportJava("demo",Map.of()),b=store.exportJava("demo",Map.of());assertTrue(Arrays.equals(Files.readAllBytes(a),Files.readAllBytes(b)));
        String baseline=store.read("demo","src/Main.java");Files.writeString(root.resolve("demo/src/Main.java"),"external");assertThrows(IOException.class,()->store.exportJava("demo",Map.of("src/Main.java",new SourceProjects.Recovery(baseline,"draft"))));assertThrows(IOException.class,()->store.exportJava("demo",Map.of("genesis-project.properties",new SourceProjects.Recovery("","wrong"))));
        try(var files=Files.list(root.resolve(".exports"))){assertEquals(2L,files.count());}
        store.create("python",SourceProjects.Language.PYTHON);assertThrows(IOException.class,()->store.exportJava("python",Map.of()));
    }
    @Test void resourceStorageRefusesLinksAndOversizeAndPreservesIndependentSnapshot()throws Exception{
        Path root=Files.createTempDirectory("java-resource-limits-");var store=new SourceProjects(root);store.create("demo",SourceProjects.Language.JAVA);Path file=root.resolve("demo/resources/values.bin");Files.write(file,new byte[]{1});var snapshot=store.javaResources("demo");Files.write(file,new byte[]{2});assertEquals(1,(int)snapshot.get("values.bin")[0]);assertEquals(2,(int)store.javaResources("demo").get("values.bin")[0]);
        Path large=root.resolve("demo/resources/large.bin");try(var ch=java.nio.channels.FileChannel.open(large,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){ch.position(JavaSourceRunner.MAX_RESOURCE_BYTES);ch.write(java.nio.ByteBuffer.wrap(new byte[]{0}));}assertThrows(IOException.class,()->store.javaResources("demo"));Files.delete(large);
        if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")){Files.createSymbolicLink(root.resolve("demo/resources/linked.bin"),file);assertThrows(IOException.class,()->store.javaResources("demo"));}
    }
}
