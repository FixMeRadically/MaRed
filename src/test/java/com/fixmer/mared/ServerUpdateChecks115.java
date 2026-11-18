package com.fixmer.mared;

import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.genesis.technology.server.firstlaunch.LocalApply67;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Actual Script provenance, owner packets, inventory checks. Running state and Gradle success are fixtures. */
public final class ServerUpdateChecks115 {
    static int count;
    static final byte[] KEY=new byte[32];
    static void ok(boolean value){if(!value)throw new AssertionError("update115 check "+count);count++;}
    interface Work{void run()throws Exception;}
    static void rejects(Work work)throws Exception{try{work.run();}catch(Exception expected){count++;return;}throw new AssertionError("update115 accepted invalid input "+count);}
    static void jar(Path file,String ids,String payload)throws Exception {
        try(var zip=new ZipOutputStream(Files.newOutputStream(file))){
            zip.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));
            zip.write("modLoader=\"javafml\"\n".getBytes(StandardCharsets.UTF_8));
            for(String id:ids.split(","))zip.write(("[[mods]]\nmodId=\""+id+"\"\nversion=\"1.0.0\"\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();zip.putNextEntry(new ZipEntry("fixture"));zip.write(payload.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
    }
    static void signed(Path p,Map<String,String> fields)throws Exception{FirstLaunchFiles.write(p,LocalApply67.sign(KEY,fields),true);}
    static void state(Path server,String run,String gen,String phase,boolean busy)throws Exception {
        signed(ServerApp104.root(server).resolve("state104"),Map.of("schema","104","run",run,"generation",gen,"phase",phase,"busy",Boolean.toString(busy),"updated",Long.toString(System.currentTimeMillis()),"error",""));
    }
    public static BuildSnapshot84.Snapshot fixture(Path root)throws Exception {
        Path project=Files.createDirectories(root.resolve("project")),exports=root.resolve("snapshots");
        Files.writeString(project.resolve("source.java"),"source 1");Files.writeString(project.resolve("build.gradle"),"fixture");
        Path output=Files.createDirectories(project.resolve("build/libs")).resolve("demo-new.jar");jar(output,"demo","new");
        String invocation=UUID.randomUUID().toString();Path report=Files.createDirectories(project.resolve(".genesis-build").resolve(invocation));Files.createDirectory(report.resolve("runtime"));
        Files.writeString(report.resolve("outputs.properties"),"id="+invocation+"\narchives=1\narchive.0="+Base64.getEncoder().encodeToString(output.toString().getBytes(StandardCharsets.UTF_8))+"\nexcludes=0\nruntime.0.count=0\n");
        var artifact=BuildArtifacts.capture(project,List.of(output),()->false).artifacts().get(0).withBuild(BuildInputs.capture(project,()->false),invocation);
        var result=new GradleBuildRunner.Result(GradleBuildRunner.Status.SUCCESS,0,"fixture",false,List.of(output),new BuildArtifacts.Inventory(List.of(artifact),false,List.of()));
        return BuildSnapshot84.create(project,"demo","build",result,artifact,exports,()->false);
    }
    public static void main(String[] args)throws Exception {
        Path root=args.length==0?Files.createTempDirectory("Genesis update115 "):Path.of(args[0]);Files.createDirectories(root);
        try {
            var snapshot=fixture(root);Path server=Files.createDirectories(root.resolve("server"));Path mods=Files.createDirectories(server.resolve("mods"));Path owner=Files.createDirectories(ServerApp104.root(server));
            Files.write(owner.resolve("owner74.key"),KEY);String run=UUID.randomUUID().toString();
            signed(owner.resolve("owner104"),Map.of("schema","104","run",run,"profile",server.toString()));
            for(String version:List.of("75","115"))signed(owner.resolve("owner"+version),Map.of("schema",version,"run",run));
            state(server,run,"1","RUNNING",false);jar(mods.resolve("demo-old.jar"),"demo","old");jar(mods.resolve("other.jar"),"other","keep");
            String old=FirstLaunchFiles.hash(mods.resolve("demo-old.jar"),1000000),unchanged=FirstLaunchFiles.hash(mods.resolve("other.jar"),1000000);
            final var review=ServerUpdate115.review(server,snapshot.directory());ok(review.target().equals("demo-old.jar"));ok(review.previous().equals(old));ok(review.sha256().equals(snapshot.artifact().sha256()));ServerUpdate115.verify(review);count++;
            ok(!Files.exists(owner.resolve("change104")));ok(!Files.exists(owner.resolve("request74")));
            Files.writeString(snapshot.project().resolve("source.java"),"changed");rejects(()->ServerUpdate115.submit(review));ok(!Files.exists(owner.resolve("request74")));Files.writeString(snapshot.project().resolve("source.java"),"source 1");
            jar(mods.resolve("late.jar"),"late","outside change");rejects(()->ServerUpdate115.submit(review));Files.delete(mods.resolve("late.jar"));
            jar(mods.resolve("duplicate.jar"),"other","duplicate id");rejects(()->ServerUpdate115.review(server,snapshot.directory()));Files.delete(mods.resolve("duplicate.jar"));
            jar(mods.resolve("demo-old.jar"),"demo,companion","multi");rejects(()->ServerUpdate115.review(server,snapshot.directory()));jar(mods.resolve("demo-old.jar"),"demo","old");
            // ZIP timestamps can differ: rebuild review after restoring fixture bytes.
            final var checked=ServerUpdate115.review(server,snapshot.directory());
            state(server,run,"1","RUNNING",true);rejects(()->ServerUpdate115.submit(checked));state(server,run,"2","RUNNING",false);rejects(()->ServerUpdate115.verify(checked));state(server,run,"1","RUNNING",false);
            Files.delete(owner.resolve("owner115"));rejects(()->ServerUpdate115.submit(checked));signed(owner.resolve("owner115"),Map.of("schema","115","run",run));
            var request=ServerUpdate115.submit(checked);var packet=LocalApply67.verify(KEY,Files.readAllBytes(owner.resolve("request74")));ok(packet.get("schema").equals("115"));ok(packet.get("request").equals(request.toString()));ok(packet.get("target").equals(checked.target()));ok(packet.get("previous").equals(checked.previous()));ok(!packet.containsKey("permit88"));
            rejects(()->ServerUpdate115.submit(checked));ok(!Files.exists(owner.resolve("change104")));ok(FirstLaunchFiles.hash(mods.resolve("other.jar"),1000000).equals(unchanged));
            state(server,run,"1","CHECKING",true);ServerUpdate115.main(new String[]{server.toString(),request.toString()});count++;rejects(()->ServerUpdate115.main(new String[]{server.toString(),UUID.randomUUID().toString()}));
            state(server,run,"1","RUNNING",false);signed(owner.resolve("request74.ack"),Map.of("schema","75","run",run,"request",request.toString()));ok(!ServerUpdate115.submit(checked).equals(request));
            signed(owner.resolve("change104"),Map.of("schema","fixture"));rejects(()->ServerUpdate115.verify(checked));Files.delete(owner.resolve("change104"));
            Files.delete(mods.resolve("demo-old.jar"));var add=ServerUpdate115.review(server,snapshot.directory());ok(add.previous().isEmpty());ok(add.target().startsWith("demo-script-"));
            Files.copy(snapshot.directory().resolve("artifact.jar"),mods.resolve("installed.jar"));rejects(()->ServerUpdate115.review(server,snapshot.directory()));Files.delete(mods.resolve("installed.jar"));
            jar(mods.resolve("demo-old.jar"),"demo","old");
            // Produce a real Java-signed packet for the Python/child-JVM owner integration suite.
            Files.delete(owner.resolve("request74"));Files.delete(owner.resolve("request74.ack"));ServerUpdate115.submit(ServerUpdate115.review(server,snapshot.directory()));
            Path logs=Files.createDirectories(owner.resolve("logs"));String candidate=UUID.randomUUID().toString();Path crash=logs.resolve("server-"+candidate+".log");Files.writeString(crash,"failure115 accessToken=private-token");Files.setLastModifiedTime(crash,java.nio.file.attribute.FileTime.fromMillis(1));
            for(int n=0;n<5;n++)Files.writeString(logs.resolve("server-"+UUID.randomUUID()+".log"),"later");
            Files.writeString(logs.resolve("update115.json"),"{\"candidateLaunch\": \""+candidate+"\", \"status\": \"RESTORED\"}");
            Path report=ServerDiagnostics105.export(server);try(var zip=new ZipFile(report.toFile())){ok(zip.getEntry(crash.getFileName().toString())!=null);ok(zip.getEntry("update115.json")!=null);String text=new String(zip.getInputStream(zip.getEntry(crash.getFileName().toString())).readAllBytes(),StandardCharsets.UTF_8);ok(text.contains("failure115")&&!text.contains("private-token"));}
            System.out.println("Server115 actual build-snapshot/review/local-queue/CAS checks passed: "+count+"; owner state and Gradle success are fixtures, not Minecraft.");
        } finally {if(args.length==0)try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
