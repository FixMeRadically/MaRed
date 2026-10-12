package com.fixmer.mared;
import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.genesis.technology.server.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Positive regressions for the audit, using the production core and real compiler/filesystem. */
public class BuildFoundationIntegrationTest  {
    static byte[] jar(Map<String,byte[]> files)throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var out=new JarOutputStream(bytes)) {
            for(var e:files.entrySet()) {
                out.putNextEntry(new JarEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    static final String TOML="[[mods]] # valid\nmodId='examplemod'\n";
    static final String BUILD="plugins { id('net.neoforged.gradle.userdev') version '7.1.38' }\n";
    static ProjectDependencies.State state(String build,String toml) {
        return new ProjectDependencies.State(Map.of("build.gradle",build,ProjectDependencies.TOML,toml));
    }
    static class Host implements ServerModuleHost {
        int actions;
        boolean fail,validation;
        long time=3;
        List<String> logs=new ArrayList<>();
        public UUID owner() {
            return new UUID(0,1);
        }
        public String worldSession() {
            return "fixture";
        }
        public boolean isServerThread() {
            return true;
        }
        public boolean allowed() {
            return true;
        }
        public void log(String s) {
            logs.add(s);
        }
        public void validateBroadcast(String s) {
            if(validation&&s.equals("fail"))throw new IllegalArgumentException("fixture validation");
        }
        public void broadcast(String s) {
            if(fail&&s.equals("fail"))throw new IllegalArgumentException("fixture commit");
            actions++;
        }
        public void setDayTime(String d,long t) {
            time=t;
        }
        public Runnable undoDayTime(String d) {
            long previous=time;
            return ()->time=previous;
        }
        public void setWeather(int d,boolean r,boolean t) {
        }
        public void validateBlock(String d,int x,int y,int z,String b) {
        }
        public void setBlock(String d,int x,int y,int z,String b) {
        }
        public Object nativeServer() {
            return null;
        }
    }
    static RuntimeBundle compile(String name,String body)throws Exception {
        return RuntimeModuleCompiler.compile(Map.of("example/"+name+".java","package example;import com.fixmer.genesis.technology.server.*;public class "+name+" implements ServerModule {"+body+"}"),Map.of(),Map.of(),"example."+name,new JavaSourceRunner.Cancellation()).bundle();
    }
    @Test public void commentsStringsRangesAndLiteralTomlAreHandled()throws Exception {
        assertFalse(ProjectDependencies.validRange("[2,1)"));
        assertFalse(ProjectDependencies.validRange("(1,1)"));
        assertFalse(ProjectDependencies.validRange("[,2]"));
        assertTrue(ProjectDependencies.validRange("(,2]"));
        assertTrue(ProjectDependencies.validRange("[1,)"));
        assertTrue(ProjectDependencies.validCoordinate("org.example:snapshot-utils:1.0"));
        assertFalse(ProjectDependencies.validCoordinate("org.example:api:1-SNAPSHOT"));
        var lib=new ProjectDependencies.Config(List.of(ProjectDependencies.Dependency.library(ProjectDependencies.Mode.LIBRARY,"org.example:api:1.0")),List.of(),false);
        assertTrue(ProjectDependencies.render(state("plugins {id 'java'}\n// org.example:api:1.0\n",TOML),lib).get(ProjectDependencies.SCRIPT).contains("implementation 'org.example:api:1.0'"));
        var mod=new ProjectDependencies.Dependency(ProjectDependencies.Mode.MOD,"org.example:api:1.0","othermod","[1,2)","required","BOTH");
        var config=new ProjectDependencies.Config(List.of(mod),List.of(),false);
        assertTrue(ProjectDependencies.render(state(BUILD,TOML+"# modId='othermod'\n"),config).get(ProjectDependencies.TOML).contains("[[dependencies.examplemod]]"));
        var embed=new ProjectDependencies.Config(List.of(ProjectDependencies.Dependency.library(ProjectDependencies.Mode.EMBED,"org.example:api:1.0")),List.of(),false);
        assertThrows(IOException.class,()->ProjectDependencies.render(state("plugins { id 'java' }\n// net.neoforged.gradle.userdev\n",TOML),embed));
        assertThrows(IOException.class,()->ProjectDependencies.render(state("plugins { id 'java' }\n/* id 'net.neoforged.gradle.userdev' */\n",TOML),embed));
    }
    @Test public void invalidJarIsRejectedAndNestedModulesAreVisible()throws Exception {
        Path root=Files.createTempDirectory("foundation-inventory-");
        Files.createDirectories(root.resolve("build/libs"));
        Files.writeString(root.resolve("build/libs/fake.jar"),"not a JAR");
        Files.createDirectories(root.resolve("libraries/module/build/libs"));
        Files.write(root.resolve("libraries/module/build/libs/real.jar"),jar(Map.of("data",new byte[] {
            1
        })));
        var inventory=BuildArtifacts.scan(root,()->false);
        assertEquals(1,inventory.artifacts().size());
        assertTrue(inventory.artifacts().get(0).path().contains("libraries/module"));
        assertFalse(inventory.issues().isEmpty());
    }
    @Test public void resourcesHaveConsistentChildFirstEnumeration()throws Exception {
        Path parent=Files.createTempDirectory("foundation-parent-"),child=Files.createTempDirectory("foundation-child-");
        Files.writeString(parent.resolve("config.txt"),"parent");
        Files.writeString(child.resolve("config.txt"),"child");
        try(var p=new java.net.URLClassLoader(new java.net.URL[] {
            parent.toUri().toURL()
        },null);var bundle=new RuntimeBundle(child,List.of(child),"example.Entry");var loader=bundle.loader(p)) {
            assertEquals("child",Files.readString(Path.of(loader.getResource("config.txt").toURI())));
            assertEquals(loader.getResource("config.txt"),loader.getResources("config.txt").nextElement());
        }
    }
    @Test public void nestedJarClassesAndResourcesReallyLoad()throws Exception {
        var prepared=RuntimeModuleCompiler.compile(Map.of("example/Module.java","package example;import com.fixmer.genesis.technology.server.*;public class Module implements ServerModule {public void onEnable(ServerContext c){c.log(Dep.value());}}","example/Dep.java","package example;public class Dep {public static String value(){return \"nested dependency loaded\";}}"),Map.of(),Map.of(),"example.Module",new JavaSourceRunner.Cancellation());
        byte[] module=Files.readAllBytes(prepared.bundle().root().resolve("classes/example/Module.class")),dep=Files.readAllBytes(prepared.bundle().root().resolve("classes/example/Dep.class"));
        prepared.bundle().close();
        String metadata="{\"jars\":[{\"identifier\":{\"group\":\"example\",\"artifact\":\"dep\"},\"version\":{\"range\":\"[1.0]\",\"artifactVersion\":\"1.0\"},\"path\":\"META-INF/jarjar/dep.jar\"}]}";
        byte[] jar=jar(Map.of("example/Module.class",module,"META-INF/jarjar/metadata.json",metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8),"META-INF/jarjar/dep.jar",jar(Map.of("example/Dep.class",dep,"dependency.txt","nested resource".getBytes()))));
        var bundle=RuntimeModuleCompiler.fromJar(jar,Map.of(),"example.Module",new JavaSourceRunner.Cancellation()).bundle();
        var host=new Host();
        try(var loader=bundle.loader(BuildFoundationIntegrationTest.class.getClassLoader())) {
            assertTrue(loader.getResource("dependency.txt")!=null);
        }
        try(var engine=new ServerModuleEngine(host)) {
            assertTrue(engine.apply("nested",bundle).success());
            assertTrue(host.logs.stream().anyMatch(s->s.contains("nested dependency loaded")));
        }
        byte[] bad=jar(Map.of("META-INF/jarjar/metadata.json",metadata.replace("META-INF/jarjar/dep.jar","../outside.jar").getBytes()));
        assertThrows(IOException.class,()->RuntimeModuleCompiler.fromJar(bad,Map.of(),"example.Module",new JavaSourceRunner.Cancellation()));
    }
    @Test public void contextLoaderIsScopedToEveryLifecycleAndCallback()throws Exception {
        ClassLoader previous=Thread.currentThread().getContextClassLoader();
        String check="if(Thread.currentThread().getContextClassLoader()!=getClass().getClassLoader())throw new IllegalStateException(\"wrong context loader\");";
        var bundle=compile("Context","public Context(){"+check+"}public void onEnable(ServerContext c){"+check+"c.onTick(n->{"+check+"c.log(\"tick ok\");});}public void onDisable(){"+check+"}");
        Host host=new Host();
        try(var engine=new ServerModuleEngine(host)) {
            assertTrue(engine.apply("context",bundle).success());
            assertEquals(previous,Thread.currentThread().getContextClassLoader());
            engine.tick();
            assertTrue(host.logs.stream().anyMatch(s->s.contains("tick ok")));
            assertTrue(engine.stop("context").success());
            assertEquals(previous,Thread.currentThread().getContextClassLoader());
        }
    }
    @Test public void slowConstructorIsRejectedAfterReturning()throws Exception {
        var bundle=compile("Slow","public Slow()throws Exception{Thread.sleep(250);}public void onEnable(ServerContext c){}");
        try(var engine=new ServerModuleEngine(new Host())) {
            assertFalse(engine.apply("slow",bundle).success());
            assertTrue(engine.snapshot().isEmpty());
        }
    }
    @Test public void worldPlanPrevalidatesAndCompensatesOrReportsPartialCommit()throws Exception {
        Host host=new Host();
        host.validation=true;
        try(var engine=new ServerModuleEngine(host)) {
            var result=engine.apply("validate",compile("Validate","public void onEnable(ServerContext c){c.broadcast(\"ok\");c.broadcast(\"fail\");}"));
            assertFalse(result.success());
            assertEquals(0,host.actions);
        }
        host=new Host();
        host.fail=true;
        try(var engine=new ServerModuleEngine(host)) {
            var result=engine.apply("partial",compile("Partial","public void onEnable(ServerContext c){c.setDayTime(\"minecraft:overworld\",99);c.broadcast(\"fail\");}"));
            assertFalse(result.success());
            assertEquals(3L,host.time);
            assertTrue(result.message().contains("PARTIAL_APPLY"));
        }
    }
    @Test public void changedSourcesPreventDeliveryEvenWhenJarBytesMatch()throws Exception {
        Path root=Files.createTempDirectory("foundation-provenance-");
        Files.writeString(root.resolve("build.gradle"),"plugins {id 'java'}");
        Files.writeString(root.resolve("Main.java"),"class Main {}");
        Files.createDirectories(root.resolve("build/libs"));
        Files.write(root.resolve("build/libs/mod.jar"),jar(Map.of("data",new byte[] {
            1
        })));
        var artifact=BuildArtifacts.scan(root,()->false).artifacts().get(0).withBuild(BuildInputs.capture(root,()->false),"");
        Files.writeString(root.resolve("Main.java"),"class Main {int newer;}");
        assertThrows(IOException.class,()->BuildArtifacts.deliver(root,artifact,root.resolve(".exports"),()->false));
    }
    @Test public void interruptedDependencyUpdateRecoversOriginalBytes()throws Exception {
        var storage=new SourceProjects(Files.createTempDirectory("foundation-journal-"));
        storage.createEmpty("demo");
        Path root=storage.projectDirectory("demo");
        String before="plugins {id 'java'}\r\n";
        Files.writeString(root.resolve("build.gradle"),before);
        String after=before+"// interrupted\n";
        try(var out=new DataOutputStream(Files.newOutputStream(root.resolve(".genesis-dependency-update")))) {
            out.writeInt(0x47445431);
            out.writeInt(2);
            out.writeUTF("build.gradle");
            byte[] b=before.getBytes(java.nio.charset.StandardCharsets.UTF_8),a=after.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            out.writeInt(b.length);
            out.write(b);
            out.writeInt(a.length);
            out.write(a);
            out.writeUTF(ProjectDependencies.CONFIG);
            out.writeInt(-1);
            a="format=1\nlocalLibraries=false\n".getBytes();
            out.writeInt(a.length);
            out.write(a);
        }
        Files.writeString(root.resolve("build.gradle"),after);
        storage.project("demo");
        assertEquals(before,Files.readString(root.resolve("build.gradle")));
        assertFalse(Files.exists(root.resolve(".genesis-dependency-update")));
    }
    @Test public void successfulNonPackagingTaskDoesNotOfferOldJar()throws Exception {
        Path root=Files.createTempDirectory("foundation-stale-");
        Files.writeString(root.resolve("build.gradle"),"plugins { id 'java' }");
        Files.createDirectories(root.resolve("build/libs"));
        Files.write(root.resolve("build/libs/old.jar"),jar(Map.of("data",new byte[] {
            1
        })));
        try(var runner=new GradleBuildRunner(5000)) {
            runner.start(root,new GradleBuildRunner.Settings(Path.of(System.getProperty("java.home")),BuildStabilityIntegrationTest.wrapper()),"help");
            assertTrue(BuildStabilityIntegrationTest.finish(runner).artifacts().isEmpty());
        }
    }
    @Test public void resolvedModIdentityAndActualVersionAreValidated()throws Exception {
        Path root=Files.createTempDirectory("foundation-resolved-mod-");
        Path jar=root.resolve("other.jar");
        Files.write(jar,jar(Map.of("META-INF/neoforge.mods.toml","[[mods]] # metadata\nmodId='othermod'\nversion='1.0'\n".getBytes())));
        var dependency=new ProjectDependencies.Dependency(ProjectDependencies.Mode.MOD,"example:artifact:2.0","othermod","[2,3)","required","BOTH");
        assertThrows(IOException.class,()->ProjectDependencies.verifyResolved(new ProjectDependencies.Config(List.of(dependency),List.of(),false),List.of(jar)));
        var compatible=new ProjectDependencies.Dependency(ProjectDependencies.Mode.MOD,"example:artifact:2.0","othermod","[1,2)","required","BOTH");
        ProjectDependencies.verifyResolved(new ProjectDependencies.Config(List.of(compatible),List.of(),false),List.of(jar));
    }
    @Test public void embeddingRangeAndOptionalTestProfileAreRendered()throws Exception {
        var embedded=ProjectDependencies.Dependency.embedded("example:library:1.5","[1,2)");
        var config=new ProjectDependencies.Config(List.of(embedded),List.of(),false);
        String script=ProjectDependencies.render(state(BUILD,TOML),config).get(ProjectDependencies.SCRIPT);
        assertTrue(script.contains("jarJar('example:library:[1,2)')"));
        assertTrue(script.contains("prefer '1.5'"));
        assertThrows(IllegalArgumentException.class,()->ProjectDependencies.Dependency.embedded("example:library:2.5","[1,2)"));
        var optional=new ProjectDependencies.Dependency(ProjectDependencies.Mode.MOD,"example:mod:1.0","othermod","[1,2)","optional","BOTH");
        script=ProjectDependencies.render(state(BUILD,TOML),new ProjectDependencies.Config(List.of(optional),List.of(),false)).get(ProjectDependencies.SCRIPT);
        assertTrue(script.contains("genesis.optionalMods"));
        assertTrue(script.contains("compileOnly 'example:mod:1.0'"));
    }
    @Test public void customOutputsAndRuntimeReportsAreValidated()throws Exception {
        Path root=Files.createTempDirectory("foundation-custom-output-");
        Files.writeString(root.resolve("build.gradle"),"plugins {id 'java'}");
        var before=BuildInputs.snapshot(root,()->false);
        Path output=root.resolve("custom-output/mod.jar");
        Files.createDirectories(output.getParent());
        Files.write(output,jar(Map.of("data",new byte[] {
            1
        })));
        String id=UUID.randomUUID().toString();
        Path folder=root.resolve(".genesis-build/"+id);
        Files.createDirectories(folder.resolve("runtime"));
        var report=new Properties();
        report.setProperty("id",id);
        report.setProperty("archives","1");
        report.setProperty("archive.0",Base64.getEncoder().encodeToString(output.toAbsolutePath().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        report.setProperty("excludes","1");
        report.setProperty("exclude.0",Base64.getEncoder().encodeToString(output.getParent().toAbsolutePath().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        byte[] library=jar(Map.of("library.txt",new byte[] {
            1
        }));
        String name=GradleOutputs.hash(library)+".jar";
        Files.write(folder.resolve("runtime/"+name),library);
        report.setProperty("runtime.0.count","1");
        report.setProperty("runtime.0.0",name);
        try(var out=Files.newBufferedWriter(folder.resolve("outputs.properties"))) {
            report.store(out,"");
        }
        var exclusions=GradleOutputs.inputExclusions(root,id);
        assertEquals(before.fingerprint(exclusions),BuildInputs.capture(root,()->false,exclusions));
        assertEquals(List.of(output.toAbsolutePath()),GradleOutputs.outputs(root,id));
        assertEquals(1,GradleOutputs.runtime(root,id,"custom-output/mod.jar",()->false).size());
        Files.writeString(folder.resolve("runtime/"+name),"modified");
        assertThrows(IOException.class,()->GradleOutputs.runtime(root,id,"custom-output/mod.jar",()->false));
        var artifact=BuildArtifacts.capture(root,List.of(output),()->false).artifacts().get(0).withBuild(before.fingerprint(exclusions),id);
        assertTrue(Files.exists(BuildArtifacts.deliver(root,artifact,root.resolve(".exports"),()->false)));
    }
    public static void main(String[] a)throws Exception {
        var t=new BuildFoundationIntegrationTest();
        t.commentsStringsRangesAndLiteralTomlAreHandled();
        t.invalidJarIsRejectedAndNestedModulesAreVisible();
        t.resourcesHaveConsistentChildFirstEnumeration();
        t.nestedJarClassesAndResourcesReallyLoad();
        t.contextLoaderIsScopedToEveryLifecycleAndCallback();
        t.slowConstructorIsRejectedAfterReturning();
        t.worldPlanPrevalidatesAndCompensatesOrReportsPartialCommit();
        t.changedSourcesPreventDeliveryEvenWhenJarBytesMatch();
        t.interruptedDependencyUpdateRecoversOriginalBytes();
        t.successfulNonPackagingTaskDoesNotOfferOldJar();
        t.resolvedModIdentityAndActualVersionAreValidated();
        t.embeddingRangeAndOptionalTestProfileAreRendered();
        t.customOutputsAndRuntimeReportsAreValidated();
        System.out.println("Build foundation regressions passed: 13 real compiler/filesystem/lifecycle scenarios");
    }
}
