package com.fixmer.mared;

import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Real disk/HMAC/ZIP/lease and UI decisions. No Minecraft event loop or native Windows process. */
public final class ServerUiChecks105 {
    static int checks;static final long NOW=1800000000000L;static final byte[] KEY=new byte[32];
    interface Action{void run()throws Exception;}static void ok(boolean b){checks++;if(!b)throw new AssertionError("105 check "+checks);}
    static void rejects(Action a)throws Exception{try{a.run();}catch(Exception expected){checks++;return;}throw new AssertionError("accepted invalid input");}
    static void sign(Path p,Map<String,String> data)throws Exception{Files.createDirectories(p.getParent());FirstLaunchFiles.write(p,LocalApply67.sign(KEY,data),Files.exists(p));}
    static Map<String,String> state(String phase){return Map.of("phase",phase,"updated",Long.toString(NOW));}
    static byte[] zip(Map<String,byte[]> files)throws Exception{var out=new ByteArrayOutputStream();try(var z=new ZipOutputStream(out)){for(var e:files.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue());z.closeEntry();}}return out.toByteArray();}
    static byte[] artifact(byte[] marker,byte[] bundle,int content)throws Exception{return zip(Map.of("com/fixmer/mared/Mared.class",new byte[]{(byte)content},"com/fixmer/mared/technology/server/firstlaunch/InPlaceWorld104.class",marker,"genesis/server59/controller.zip",bundle));}
    static void deployment(Path root)throws Exception{
        var owner=Map.of("run","run-a","phase","RUNNING","generation","99");
        ok(ServerLocal105.deployment(owner,"run-a","release-a","commit-a").phase().equals("COMMIT_QUEUED"));
        var observed=new HashMap<>(owner);
        observed.put("request","different");observed.put("requestResult","REJECTED");
        ok(ServerLocal105.deployment(observed,"run-a","release-a","commit-a").phase().equals("COMMIT_QUEUED"));
        observed.put("request","commit-a");observed.put("error","FOLDER118_PAIR_CHANGED");
        ok(ServerLocal105.deployment(observed,"run-a","release-a","commit-a").error().equals("FOLDER118_PAIR_CHANGED"));
        var journal=ServerApp104.root(root);
        Files.createDirectories(journal);Files.write(journal.resolve("owner74.key"),KEY);
        sign(journal.resolve("rollout118"),Map.of("schema","118","run","run-a","label","release-a","phase","RESTORED","reason","FOLDER118_CLIENT_INSTALL_TIMEOUT"));
        var restored=ServerLocal105.deployment(ServerLocal105.withRollout(root,owner),"run-a","release-a","commit-a");
        ok(restored.phase().equals("ERROR")&&restored.error().equals("FOLDER118_CLIENT_INSTALL_TIMEOUT"));
        sign(journal.resolve("rollout118"),Map.of("schema","118","run","run-b","label","release-a","phase","CLOSED"));
        ok(ServerLocal105.deployment(ServerLocal105.withRollout(root,owner),"run-a","release-a","commit-a").phase().equals("COMMIT_QUEUED"));
        sign(journal.resolve("rollout118"),Map.of("schema","118","run","run-a","label","release-a","phase","CLOSED"));
        ok(ServerLocal105.deployment(ServerLocal105.withRollout(root,owner),"run-a","release-a","commit-a").phase().equals("COMMITTED"));
        Files.writeString(journal.resolve("rollout118"),"invalid");
        rejects(()->ServerLocal105.withRollout(root,owner));
        for(String phase:List.of("STARTING","RESTARTING","STOPPING","CHECKING","WAITING_ALL_INSTALLED"))
            ok(ServerLocal105.deploymentStart(Map.of("phase",phase),false).equals("WAIT"));
        ok(ServerLocal105.deploymentStart(Map.of("phase","STOPPED"),false).equals("START"));
        ok(ServerLocal105.deploymentStart(Map.of("phase","RUNNING","busy","true"),false).equals("WAIT"));
        ok(ServerLocal105.deploymentStart(Map.of("phase","RUNNING"),false).equals("READY"));
        ok(ServerLocal105.deploymentStart(Map.of("phase","RECOVERY_REQUIRED"),false).equals("BLOCKED"));
    }

    static void models(){
        for(String phase:List.of("STOPPED","FAILED","BOOTSTRAP_FAILED_RESTORED")){var v=ServerView105.of(state(phase),true,false,NOW);ok(v.canStart());ok(v.operation().equals("start"));ok(!v.canStop());}
        var run=ServerView105.of(state("RUNNING"),true,false,NOW);ok(run.canStart()&&run.canStop()&&run.operation().equals("restart"));
        for(String phase:List.of("READING","STARTING","STOPPING","CHECKING","RESTARTING","WAITING_FOR_STOP","RECOVERY_REQUIRED","OWNER_UNAVAILABLE","INVALID_FOLDER","arbitrary"))ok(!ServerView105.of(state(phase),true,false,NOW).canStart());
        ok(ServerView105.of(state("RECOVERY_REQUIRED"),true,false,NOW).needsReview());ok(ServerView105.of(state("WAITING_FOR_STOP"),true,false,NOW).canStop());
        ok(!ServerView105.of(state("RUNNING"),true,true,NOW).canStart());ok(!ServerView105.of(state("RUNNING"),false,false,NOW).canStart());
        ok(!ServerView105.of(Map.of("phase","RUNNING"),true,false,NOW).canStart());ok(!ServerView105.of(state("RUNNING"),true,false,NOW+10001).canStart());ok(!ServerView105.of(state("RUNNING"),true,false,NOW-30001).canStart());
        ok(ServerView105.of(state("ACTIVE_BASELINE"),true,false,NOW).phase().equals("RUNNING"));ok(ServerView105.of(state("SAVING_OLD_SERVER"),true,false,NOW).phase().equals("STOPPING"));ok(ServerView105.of(state("STARTING_NEW_SERVER"),true,false,NOW).phase().equals("STARTING"));
        ok(ServerView105.hint("SERVER105_PYTHON_REQUIRED").equals("python"));ok(ServerView105.hint("ACCESS88_DENIED").equals("permission"));ok(ServerView105.hint("SERVER105_RECOVERY_PENDING").equals("pending"));ok(ServerView105.hint("untrusted text").equals("generic"));
    }
    static void replies(){
        var replies=new ServerReplies105();Object first=new Object(),second=new Object();replies.connection(first);
        UUID command=UUID.randomUUID(),ping=UUID.randomUUID();replies.register(command,"RESTART",1);replies.register(ping,"PING",2);ok(replies.receive(command,"BUSY",3));ok(replies.control().result().equals("BUSY"));ok(replies.receive(ping,"STATE",4));ok(replies.control().result().equals("BUSY"));ok(!replies.receive(command,"QUEUED",5));
        UUID next=UUID.randomUUID();replies.register(next,"SHUTDOWN",6);ok(replies.control()==null);ok(replies.submitted().equals(next));replies.connection(second);ok(replies.control()==null&&replies.submitted()==null);ok(!replies.receive(next,"QUEUED",7));
        UUID expired=UUID.randomUUID();replies.register(expired,"RESTART",8);ok(!replies.receive(expired,"QUEUED",10_000_000_009L));
        UUID newer=UUID.randomUUID(),older=UUID.randomUUID();replies.register(older,"RESTART",20);replies.register(newer,"SHUTDOWN",21);ok(replies.receive(older,"DENIED",22));ok(replies.control()==null);ok(replies.receive(newer,"QUEUED",23));ok(replies.control().request().equals(newer));
    }
    static void reports(Path server)throws Exception{
        Path root=ServerApp104.root(server),logs=root.resolve("logs");Files.createDirectories(logs);Files.writeString(root.resolve("owner74.key"),"PRIVATE-KEY-SENTINEL");
        Files.writeString(logs.resolve("controller.log"),"java.io.IOException: useful stack\n--accessToken SECRET1\nAuthorization: Bearer SECRET2\n{\"refresh_token\": \"SECRET3\"}\npassword=SECRET4\n");
        Files.writeString(logs.resolve("status.json"),"{\"phase\":\"FAILED\"}");Files.writeString(logs.resolve("launch.json"),"NEVER-SEND-ARGV");
        for(int n=0;n<8;n++)Files.writeString(logs.resolve("server-"+UUID.randomUUID()+".log"),"fixture "+n);
        Files.writeString(logs.resolve("setup-error.log"),"X".repeat(700000));Path report=ServerDiagnostics105.export(server);ok(report.getParent().equals(root.resolve("reports")));
        try(var z=new ZipFile(report.toFile())){
            var names=z.stream().map(ZipEntry::getName).toList();ok(!names.contains("owner74.key"));ok(!names.contains("launch.json"));ok(names.stream().filter(s->s.startsWith("server-")).count()==4);ok(z.getEntry("setup-error.log").getSize()<530000);
            String text=new String(z.getInputStream(z.getEntry("controller.log")).readAllBytes(),StandardCharsets.UTF_8);ok(text.contains("useful stack"));for(String secret:List.of("SECRET1","SECRET2","SECRET3","SECRET4"))ok(!text.contains(secret));ok(text.contains("[redacted]"));
        }
        rejects(()->ServerDiagnostics105.export(server.resolve("absent")));ok(!Files.exists(server.resolve("absent")));
        Path outside=Files.createTempFile("secret105-",".txt");Files.writeString(outside,"OUTSIDE-SECRET");Path link=logs.resolve("launcher.log");boolean linked=false;
        try{Files.createSymbolicLink(link,outside);linked=true;}catch(UnsupportedOperationException|IOException denied){System.out.println("SKIP105 link creation unavailable; other report checks continue.");}
        if(linked){try(var z=new ZipFile(ServerDiagnostics105.export(server).toFile())){ok(z.getEntry("launcher.log")==null);}Files.delete(link);}Files.delete(outside);
    }
    static void artifacts(Path folder)throws Exception{
        byte[] marker={1,2,3},bundle={4,5,6};Path a=folder.resolve("a.jar"),b=folder.resolve("b.jar");Files.write(a,artifact(marker,bundle,1));Files.copy(a,b);
        var selected=ServerArtifact105.select(List.of(a,b),null,marker,bundle);ok(Arrays.equals(selected.bytes(),Files.readAllBytes(a)));
        Files.write(b,artifact(marker,bundle,2));rejects(()->ServerArtifact105.select(List.of(a,b),null,marker,bundle));ok(ServerArtifact105.select(List.of(b,a),a,marker,bundle).path().equals(a));
        Files.write(a,new byte[]{0});ok(selected.bytes().length>1);rejects(()->ServerArtifact105.select(List.of(a),null,marker,bundle));
        Path wrong=folder.resolve("wrong.jar");Files.write(wrong,artifact(new byte[]{9},bundle,1));rejects(()->ServerArtifact105.select(List.of(wrong),null,marker,bundle));
        Path largeEntry=folder.resolve("large-entry.jar");Files.write(largeEntry,artifact(new byte[1024*1024],bundle,1));rejects(()->ServerArtifact105.select(List.of(largeEntry),null,marker,bundle));
    }
    static void local(Path server)throws Exception{
        Path root=ServerApp104.root(server);Files.createDirectories(root);Files.createDirectories(server.resolve("world"));Files.writeString(server.resolve("world/session.lock"),"LOCK-BYTES");Files.writeString(server.resolve("world/level.dat"),"WORLD-UNCHANGED");FirstLaunchFiles.write(root.resolve("owner74.key"),KEY,true);
        String run=UUID.randomUUID().toString();sign(root.resolve("owner104"),Map.of("schema","104","run",run,"profile",server.toString()));for(String v:List.of("75","103"))sign(root.resolve("owner"+v),Map.of("schema",v,"run",run));
        var state=new HashMap<>(Map.of("schema","104","run",run,"phase","RUNNING","updated",Long.toString(System.currentTimeMillis()),"generation","44","busy","false","error","","launch",UUID.randomUUID().toString()));sign(root.resolve("state104"),state);
        sign(root.resolve("view75"),Map.ofEntries(Map.entry("schema","75"),Map.entry("run",run),Map.entry("generation","44"),Map.entry("phase","ACTIVE_BASELINE"),Map.entry("updated",state.get("updated")),Map.entry("ready","true"),Map.entry("control","{}"),Map.entry("delta","{}"),Map.entry("history","[]"),Map.entry("failureCode",""),Map.entry("failureStage",""),Map.entry("reason",""),Map.entry("busy","false")));
        UUID id=ServerLocal105.command(server,"restart");var command=LocalApply67.verify(KEY,FirstLaunchFiles.read(root.resolve("request74"),16384));ok(command.get("request").equals(id.toString()));ok(command.get("action").equals("RESTART"));ok(!Files.exists(root.resolve("app")));rejects(()->ServerLocal105.command(server,"stop"));
        state.put("phase","RECOVERY_REQUIRED");sign(root.resolve("state104"),state);sign(root.resolve("review104"),Map.of("schema","104","reason","WORLD_MAY_HAVE_CHANGED"));ok(ServerLocal105.observe(server).get("repairPending").equals("false"));
        Files.writeString(root.resolve("change104"),"pending");rejects(()->ServerLocal105.acknowledge(server));ok(Files.exists(root.resolve("review104")));Files.delete(root.resolve("change104"));
        try(var c=FileChannel.open(root.resolve("owner104.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var l=c.lock(0,1,false)){rejects(()->ServerLocal105.acknowledge(server));}
        try(var c=FileChannel.open(server.resolve("world/session.lock"),StandardOpenOption.WRITE);var l=c.lock(0,1,false)){rejects(()->ServerLocal105.acknowledge(server));}
        ServerLocal105.acknowledge(server);ok(!Files.exists(root.resolve("review104")));ok(ServerLocal105.observe(server).get("phase").equals("STOPPED"));ok(ServerView105.of(ServerLocal105.observe(server),true,false).canStart());ok(Files.readString(server.resolve("world/level.dat")).equals("WORLD-UNCHANGED"));ok(Files.readString(server.resolve("world/session.lock")).equals("LOCK-BYTES"));
        rejects(()->ServerLocal105.acknowledge(server));ok(ServerLocal105.observe(server.resolve("absent")).get("phase").equals("INVALID_FOLDER"));ok(!Files.exists(server.resolve("absent")));
    }
    public static void main(String[]a)throws Exception{
        models();replies();Path root=Files.createTempDirectory("Genesis UI105 ").toRealPath();Path server=root.resolve("server");Files.createDirectories(server);reports(server);artifacts(root);local(server);deployment(Files.createDirectory(root.resolve("deployment")));
        System.out.println("Server105 real model/files/HMAC/ZIP/recovery/queue checks passed: "+checks+"; no Minecraft renderer or Windows native process.");
    }
}
