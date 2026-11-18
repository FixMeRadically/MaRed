package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.genesis.technology.server.firstlaunch.LocalApply67;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Local OS-owner bridge from an inspected Script build to the running folder owner.
 * This grants no remote/player authority and does not distribute a client update. */
public final class ServerUpdate115 {
    private ServerUpdate115() {}
    public record Review(Path server,Path snapshot,String run,String generation,String inventory,
                         String target,String sha256,String seal,String previous) {}
    private static IOException fail(String code){return new IOException("SERVER115_"+code);}
    private static byte[] key(Path root)throws IOException {
        byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);
        if(key.length!=32)throw fail("OWNER_REQUIRED");return key;
    }
    private static void owner(Path server,String run,byte[] key)throws Exception {
        Path root=ServerApp104.root(server);
        if(!Files.isRegularFile(root.resolve("owner115"),LinkOption.NOFOLLOW_LINKS))throw fail("NEW_OWNER_REQUIRED");
        var admin=DedicatedAdmin75.open(root);
        if(!admin.run.toString().equals(run)||!admin.isInPlace104()||!LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner115"),16384)).equals(Map.of("schema","115","run",run)))throw fail("NEW_OWNER_REQUIRED");
    }
    private static void idle(Path server,String run,String generation)throws Exception {
        var state=ServerLocal105.observe(server);
        if(!state.getOrDefault("phase","").equals("RUNNING")||!state.getOrDefault("busy","true").equals("false")||!run.equals(state.get("run"))||!generation.equals(state.get("generation")))throw fail("OWNER_BUSY_OR_CHANGED");
    }
    public static Review review(Path server,Path snapshot)throws Exception {
        server=FirstLaunchFiles.safe(server);snapshot=FirstLaunchFiles.safe(snapshot);
        var state=ServerLocal105.observe(server);String run=state.getOrDefault("run",""),generation=state.getOrDefault("generation","");
        idle(server,run,generation);return inspect(server,snapshot,run,generation);
    }
    private static Review inspect(Path server,Path folder,String run,String generation)throws Exception {
        server=FirstLaunchFiles.safe(server);folder=FirstLaunchFiles.safe(folder);Path root=ServerApp104.root(server);
        if(!UUID.fromString(run).toString().equals(run)||!generation.matches("[1-9][0-9]{0,8}"))throw fail("BINDING");
        owner(server,run,key(root));
        if(Files.exists(root.resolve("change104"),LinkOption.NOFOLLOW_LINKS)||Files.exists(root.resolve("review104"),LinkOption.NOFOLLOW_LINKS))throw fail("CHANGE_PENDING");
        var snapshot=BuildSnapshot84.verify(folder,()->Thread.currentThread().isInterrupted());
        var ids=new TreeSet<>(snapshot.modIds());
        if(!Collections.disjoint(ids,Set.of("mared","genesis","genesis_core","minecraft","neoforge")))throw fail("PROTECTED_MOD");
        String before=FirstLaunchFiles.inventory(server),target="",previous="";var owners=new HashMap<String,String>();var names=new HashSet<String>();
        try(var paths=Files.list(FirstLaunchFiles.safe(server.resolve("mods")))){
            for(Path file:paths.toList()){
                String name=file.getFileName().toString();if(!name.toLowerCase(Locale.ROOT).endsWith(".jar"))continue;
                FirstLaunchFiles.safe(file);
                if(!name.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,119}\\.jar")||name.contains("..")||!names.add(name.toLowerCase(Locale.ROOT))||names.size()>256)throw fail("MOD_FILENAME");
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw fail("MOD_FILENAME");
                var installed=JarInspection.modVersions(file).keySet();
                for(String id:installed)if(owners.putIfAbsent(id,name)!=null)throw fail("DUPLICATE_MOD_ID");
                if(Collections.disjoint(installed,ids))continue;
                if(!new TreeSet<>(installed).equals(ids))throw fail("PARTIAL_REPLACEMENT");
                if(!target.isEmpty())throw fail("AMBIGUOUS_REPLACEMENT");
                try(var zip=new java.util.zip.ZipFile(file.toFile())){if(zip.getEntry("com/fixmer/mared/Mared.class")!=null)throw fail("PROTECTED_MOD");}
                target=name;previous=FirstLaunchFiles.hash(file,64L*1024*1024);
            }
        }
        if(target.isEmpty()){
            target=ids.first()+"-script-"+snapshot.artifact().sha256().substring(0,12)+".jar";
            if(names.contains(target.toLowerCase(Locale.ROOT)))throw fail("NAME_COLLISION");
        }
        if(previous.equals(snapshot.artifact().sha256()))throw fail("UNCHANGED");
        if(!before.equals(FirstLaunchFiles.inventory(server)))throw fail("INPUT_CHANGED");
        return new Review(server,folder,run,generation,before,target,snapshot.artifact().sha256(),snapshot.seal(),previous);
    }
    public static void verify(Review reviewed)throws Exception {
        var state=ServerApp104.state(reviewed.server());
        if(!reviewed.run().equals(state.get("run"))||!reviewed.generation().equals(state.get("generation")))throw fail("REVIEW_CHANGED");
        if(!reviewed.equals(inspect(reviewed.server(),reviewed.snapshot(),reviewed.run(),reviewed.generation())))throw fail("REVIEW_CHANGED");
    }
    private static Map<String,String> packet(Review r,UUID request){
        var d=new TreeMap<String,String>();d.putAll(Map.of("schema","115","run",r.run(),"generation",r.generation(),"request",request.toString(),"action","APPLY115","label","r1","snapshot",r.snapshot().toString(),"seal",r.seal(),"inventory",r.inventory(),"target",r.target()));
        d.put("sha256",r.sha256());d.put("previous",r.previous());return d;
    }
    public static UUID submit(Review reviewed)throws Exception {
        UUID request=UUID.randomUUID();Path root=ServerApp104.root(reviewed.server());byte[] key=key(root);
        Path lock=FirstLaunchFiles.safe(root.resolve("submit75.lock"));
        try(var channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            FirstLaunchFiles.privateMode(lock);
            try(var held=channel.tryLock(0,1,false)){
                if(held==null)throw fail("QUEUE_BUSY");verify(reviewed);idle(reviewed.server(),reviewed.run(),reviewed.generation());
                Path path=root.resolve("request74");
                if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)){
                    var prior=LocalApply67.verify(key,FirstLaunchFiles.read(path,16384));
                    var ack=Files.exists(root.resolve("request74.ack"),LinkOption.NOFOLLOW_LINKS)?LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("request74.ack"),16384)):Map.<String,String>of();
                    if(!reviewed.run().equals(prior.get("run"))||!reviewed.run().equals(ack.get("run"))||!Objects.equals(prior.get("request"),ack.get("request")))throw fail("QUEUE_BUSY");
                }
                FirstLaunchFiles.write(path,LocalApply67.sign(key,packet(reviewed,request)),true);
            }
        }catch(OverlappingFileLockException busy){throw fail("QUEUE_BUSY");}
        return request;
    }
    /** Invoked by the owner's bundled JDK, with its own core.jar; no project code is loaded. */
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw fail("ARGUMENTS");Path server=FirstLaunchFiles.safe(Path.of(args[0]));Path root=ServerApp104.root(server);
        var d=LocalApply67.verify(key(root),FirstLaunchFiles.read(root.resolve("request74"),16384));
        if(!d.keySet().equals(Set.of("schema","run","generation","request","action","label","snapshot","seal","inventory","target","sha256","previous"))||!d.get("schema").equals("115")||!d.get("action").equals("APPLY115")||!d.get("label").equals("r1")||!args[1].equals(d.get("request")))throw fail("REQUEST_BINDING");
        Review r=new Review(server,Path.of(d.get("snapshot")),d.get("run"),d.get("generation"),d.get("inventory"),d.get("target"),d.get("sha256"),d.get("seal"),d.get("previous"));verify(r);System.out.println("SERVER115_VERIFIED");
    }
}
