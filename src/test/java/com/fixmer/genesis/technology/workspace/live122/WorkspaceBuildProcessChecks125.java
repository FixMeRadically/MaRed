package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.genesis.technology.workspace.collaboration121.*;
import java.nio.file.*;
import java.util.*;

/** Actual offline Gradle/JDK build of transferred source, using the already installed distribution. */
public final class WorkspaceBuildProcessChecks125 {
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("Workspace real build125 ");
        try {
            Path tools=root.resolve("tools"),wrapper=tools.resolve("gradle/wrapper/gradle-wrapper.jar");Files.createDirectories(wrapper.getParent());if(args.length>0&&args[0].equals("bundled"))WorkspaceProject126.wrapper(wrapper.getParent());else Files.copy(Path.of("gradle/wrapper/gradle-wrapper.jar"),wrapper);
            var props=new Properties();try(var in=Files.newInputStream(args.length>0&&args[0].equals("bundled")?wrapper.resolveSibling("gradle-wrapper.properties"):Path.of("gradle/wrapper/gradle-wrapper.properties"))){props.load(in);}
            Path cache=Path.of(System.getProperty("user.home"),".gradle/wrapper/dists");String url=props.getProperty("distributionUrl"),distribution=url.substring(url.lastIndexOf('/')+1).replace(".zip","");
            if(!Files.isDirectory(cache.resolve(distribution)))throw new IllegalStateException("Existing offline Gradle distribution required");
            String relative=tools.relativize(cache).toString().replace('\\','/');props.setProperty("distributionBase","PROJECT");props.setProperty("zipStoreBase","PROJECT");props.setProperty("distributionPath",relative);props.setProperty("zipStorePath",relative);props.setProperty("networkTimeout","1000");
            try(var out=Files.newOutputStream(wrapper.resolveSibling("gradle-wrapper.properties"))){props.store(out,"Offline fixture reuses the installed distribution");}
            var source=new TreeMap<String,WorkspaceRelease121.File>();
            var texts=Map.of("settings.gradle","rootProject.name='workspace125'\n","build.gradle","plugins { id 'java' }\nversion='1.0.0'\n","src/main/java/demo/Example.java","package demo; public final class Example { public static int value(){return 125;} }\n","src/main/resources/META-INF/neoforge.mods.toml","modLoader=\"javafml\"\nloaderVersion=\"[4,)\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\"workspace125\"\nversion=\"1.0.0\"\n");
            for(var e:texts.entrySet())source.put(e.getKey(),new WorkspaceRelease121.File(e.getKey(),1,e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var download=new WorkspaceSources125.Download(WorkspaceRelease121.digest(source),WorkspaceSources125.manifest(source));
            while(download.next()!=null){var f=source.get(download.next().path());download.accept(f.path(),0,Base64.getEncoder().encodeToString(f.bytes()));}
            Path project=download.materialize(root.resolve("copies"));
            try(var runner=new GradleBuildRunner(120000)) {
                runner.start(project,new GradleBuildRunner.Settings(Path.of(System.getProperty("java.home")),wrapper),"build");
                var result=new java.util.concurrent.atomic.AtomicReference<GradleBuildRunner.Result>();long deadline=System.nanoTime()+130_000_000_000L;
                while(runner.busy()&&System.nanoTime()<deadline){runner.poll(result::set);Thread.sleep(20);}
                var r=result.get();if(r==null||r.status()!=GradleBuildRunner.Status.SUCCESS)throw new AssertionError(r==null?"Build timed out":r.status()+"\n"+r.output());
                var artifact=r.inventory().artifacts().stream().filter(a->a.kind().equals("mod")).findFirst().orElseThrow();
                var snapshot=BuildSnapshot84.create(project,"workspace125","build",r,artifact,root.resolve("exports"),()->false);
                if(!BuildSnapshot84.verify(snapshot.directory(),()->false).seal().equals(snapshot.seal()))throw new AssertionError("Snapshot seal");
                Files.writeString(project.resolve("src/main/java/demo/Example.java"),"changed after build");
                boolean rejected=false;try{BuildSnapshot84.verify(snapshot.directory(),()->false);}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("Changed source was accepted");
                System.out.println("Wrapper distribution: "+distribution);System.out.println("Workspace125 actual offline Gradle build -> compiled class/resource mod archive -> BuildSnapshot84 verify -> changed-source rejection passed; no Minecraft launched.");
            }
        }finally{try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
