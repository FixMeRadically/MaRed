package com.fixmer.mared;

import com.fixmer.genesis.technology.server.*;
import com.fixmer.genesis.technology.scripts.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real compiler and class loaders; world host is a recording implementation, not Minecraft. */
class ServerModuleRuntimeIntegrationTest {
    static final class Host implements ServerModuleHost {
        final Thread thread=Thread.currentThread();final UUID id=UUID.randomUUID();
        final List<String> broadcasts=new ArrayList<>();boolean allowed=true;long time;
        public UUID owner(){return id;}public String worldSession(){return "test";}
        public boolean isServerThread(){return Thread.currentThread()==thread;}public boolean allowed(){return allowed;}
        public void log(String text){}public void broadcast(String text){broadcasts.add(text);}
        public void setDayTime(String dimension,long time){this.time=time;}
        public void setWeather(int duration,boolean rain,boolean thunder){}
        public void validateBlock(String dimension,int x,int y,int z,String block){}
        public void setBlock(String dimension,int x,int y,int z,String block){}
        public Object nativeServer(){return this;}
    }
    static RuntimeBundle compile(String body)throws Exception {
        String source="import com.fixmer.genesis.technology.server.*;public class WorldModule implements ServerModule{public void onEnable(ServerContext c)throws Exception{"+body+"}}";
        return RuntimeModuleCompiler.compile(Map.of("src/WorldModule.java",source),Map.of(),Map.of(),"WorldModule",new JavaSourceRunner.Cancellation()).bundle();
    }
    @Test void failedAndCancelledReplacementsPreserveActiveGenerationAndStagedWorldActions()throws Exception {
        var host=new Host();try(var engine=new ServerModuleEngine(host)) {
            assertTrue(engine.apply("world",compile("c.setDayTime(\"minecraft:overworld\",6000);")).success());long generation=engine.snapshot().get("world").generation();
            var bad=compile("c.broadcast(\"must not publish\");throw new IllegalStateException(\"failed\");");Path badRoot=bad.root();
            assertFalse(engine.apply("world",bad).success());assertFalse(Files.exists(badRoot));assertTrue(host.broadcasts.isEmpty());
            System.clearProperty("genesis.runtime.cancel.test");var cancelled=compile("c.setDayTime(\"minecraft:overworld\",18000);System.setProperty(\"genesis.runtime.cancel.test\",\"yes\");");Path cancelledRoot=cancelled.root();
            try {assertFalse(engine.apply("world",cancelled,()->System.getProperty("genesis.runtime.cancel.test")!=null).success());}
            finally {System.clearProperty("genesis.runtime.cancel.test");}
            assertEquals(6000,host.time);assertEquals(generation,engine.snapshot().get("world").generation());assertFalse(Files.exists(cancelledRoot));
        }
    }
    @Test void rollbackRecreatesPreviousCodeWithCurrentStateAndStopDeletesBothBundles()throws Exception {
        var host=new Host();try(var engine=new ServerModuleEngine(host)) {
            var first=compile("String n=c.state(\"count\");c.broadcast(\"v1:\"+n);c.state(\"count\",\"one\");");var second=compile("c.broadcast(\"v2:\"+c.state(\"count\"));c.state(\"count\",\"two\");");
            assertTrue(engine.apply("world",first).success());assertTrue(engine.apply("world",second).success());assertTrue(engine.rollback("world").success());
            assertEquals(List.of("v1:null","v2:one","v1:two"),host.broadcasts);assertTrue(engine.snapshot().get("world").canRollback());
            assertTrue(engine.stop("world").success());assertTrue(engine.snapshot().isEmpty());assertFalse(Files.exists(first.root()));assertFalse(Files.exists(second.root()));
        }
    }
    @Test void callbackFailureCannotLeaveTickSubscriptionsAlive()throws Exception {
        var host=new Host();try(var engine=new ServerModuleEngine(host)) {
            assertTrue(engine.apply("world",compile("c.onTick(t->{throw new IllegalStateException(\"tick fault\");});c.onTick(t->c.broadcast(\"late\"));")).success());
            engine.tick();engine.tick();var status=engine.snapshot().get("world");assertFalse(status.running());assertEquals(0,status.callbacks());assertTrue(status.error().contains("tick fault"));assertTrue(host.broadcasts.isEmpty());
        }
    }
    @Test void ordinaryMainAndMalformedJavaCannotBecomeServerModules()throws Exception {
        var host=new Host();try(var engine=new ServerModuleEngine(host)) {
            var plain=RuntimeModuleCompiler.compile(Map.of("Main.java","public class Main{public static void main(String[]a){}}"),Map.of(),Map.of(),"Main",new JavaSourceRunner.Cancellation()).bundle();
            assertFalse(engine.apply("main",plain).success());assertFalse(Files.exists(plain.root()));
            assertThrows(IOException.class,()->RuntimeModuleCompiler.compile(Map.of("WorldModule.java","not valid Java"),Map.of(),Map.of(),"WorldModule",new JavaSourceRunner.Cancellation()));
        }
    }
    @Test void immutableJarDeploymentIncludesResourcesAndCannotReplaceHostApi()throws Exception {
        byte[] bytes;try(var compiled=compile("try(var in=getClass().getResourceAsStream(\"/message.txt\")){c.broadcast(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}")) {
            var output=new ByteArrayOutputStream();try(var jar=new JarOutputStream(output)) {
                try(var walk=Files.walk(compiled.root().resolve("classes"))) {for(Path file:walk.filter(Files::isRegularFile).toList()) {jar.putNextEntry(new JarEntry(compiled.root().resolve("classes").relativize(file).toString().replace('\\','/')));jar.write(Files.readAllBytes(file));jar.closeEntry();}}
                jar.putNextEntry(new JarEntry("message.txt"));jar.write("jar resource".getBytes(java.nio.charset.StandardCharsets.UTF_8));jar.closeEntry();
            }bytes=output.toByteArray();
        }
        var bundle=RuntimeModuleCompiler.fromJar(bytes,Map.of(),"WorldModule",new JavaSourceRunner.Cancellation()).bundle();Arrays.fill(bytes,(byte)0);
        var host=new Host();try(var engine=new ServerModuleEngine(host)) {assertTrue(engine.apply("jar",bundle).success());assertEquals(List.of("jar resource"),host.broadcasts);}
        assertFalse(Files.exists(bundle.root()));assertThrows(IOException.class,()->RuntimeModuleCompiler.fromJar(new byte[]{0},Map.of(),"WorldModule",new JavaSourceRunner.Cancellation()));
        assertThrows(IOException.class,()->compile("com.fixmer.mared.Mared.class.getName();"));
    }
    @Test void apiExportPreservesUserEditsAndPermissionClosureReleasesRuntime()throws Exception {
        var store=new SourceProjects(Files.createTempDirectory("server-project-test-"));var project=store.createServerModule("demo");assertEquals(ServerModuleTemplate.source(),store.read(project.id(),"src/WorldModule.java"));
        Path api=store.exportServerApi("demo");assertTrue(Arrays.equals(RuntimeModuleCompiler.apiJar(),Files.readAllBytes(api)));Files.writeString(api,"local edit");assertThrows(IOException.class,()->store.exportServerApi("demo"));assertEquals("local edit",Files.readString(api));
        var host=new Host();var bundle=compile("c.onTick(t->{});");try(var engine=new ServerModuleEngine(host)) {assertTrue(engine.apply("world",bundle).success());host.allowed=false;engine.tick();assertTrue(engine.snapshot().isEmpty());assertFalse(Files.exists(bundle.root()));}
    }
}
