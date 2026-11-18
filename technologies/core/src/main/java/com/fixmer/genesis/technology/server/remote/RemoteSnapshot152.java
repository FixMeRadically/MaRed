package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;import com.fixmer.genesis.technology.scripts.*;import com.fixmer.genesis.technology.permissions.MaopPolicy113;
import java.io.*;import java.nio.file.*;import java.util.*;
/** A server-sealed remote upload. This is publisher authorization, never a claim of local build provenance. */
public final class RemoteSnapshot152 {
    private RemoteSnapshot152(){}
    public static void authorize(Path server,UUID actor)throws Exception{
        Path root=ServerApp104.root(server);var admin=DedicatedAdmin75.open(root);byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);
        if(!LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner152"),16384)).equals(Map.of("schema","152","run",admin.run.toString())))throw new IOException("REMOTE152_NEW_OWNER_REQUIRED");
        var binding=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("maop113.binding"),16384));
        if(!admin.run.toString().equals(binding.get("run"))||!FirstLaunchFiles.safe(server).toString().equals(binding.get("profile")))throw new SecurityException("DENIED");
        Path policy=FirstLaunchFiles.safe(Path.of(binding.get("policy")));if(!policy.startsWith(FirstLaunchFiles.safe(server)))throw new SecurityException("DENIED");
        var state=MaopPolicy113.decode(FirstLaunchFiles.read(policy,20000));
        for(String right:List.of("STAGE","RESTART","COMMIT"))if(!state.allows(actor,right))throw new SecurityException("DENIED");
    }
    public static void device153(Path server,UUID actor,String fingerprint)throws Exception{
        if(fingerprint.isEmpty())return;Path root=ServerApp104.root(server);byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);var admin=DedicatedAdmin75.open(root);
        if(!LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner153"),16384)).equals(Map.of("schema","153","run",admin.run.toString())))throw new java.io.IOException("AUTH153_NEW_OWNER_REQUIRED");
        var binding=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("maop153.binding"),16384));Path file=FirstLaunchFiles.safe(Path.of(binding.get("devices")));
        if(!"153".equals(binding.get("schema"))||!admin.run.toString().equals(binding.get("run"))||!FirstLaunchFiles.safe(server).toString().equals(binding.get("profile"))||!file.startsWith(FirstLaunchFiles.safe(server))||!com.fixmer.genesis.technology.permissions.DeviceTrust153.allows(file,actor,fingerprint))throw new SecurityException("DENIED");
    }
    public static void seal(Path server,Path folder,UUID actor,String hash,long size)throws Exception{seal153(server,folder,actor,hash,size,"");}
    public static void seal153(Path server,Path folder,UUID actor,String hash,long size,String fingerprint)throws Exception{device153(server,actor,fingerprint);
        authorize(server,actor);Path root=ServerApp104.root(server);folder=FirstLaunchFiles.safe(folder);
        if(!folder.startsWith(root.resolve("remote-tests152")))throw new IOException("REMOTE152_PATH");
        var data=new TreeMap<>(Map.of("schema","152","actor",actor.toString(),"run",DedicatedAdmin75.open(root).run.toString(),"hash",hash,"size",Long.toString(size),"generation",DedicatedAdmin75.open(root).view().get("generation")));if(!fingerprint.isEmpty())data.put("device153",fingerprint);
        Path marker=folder.resolve("remote152");byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);
        if(Files.exists(marker)){if(!LocalApply67.verify(key,FirstLaunchFiles.read(marker,16384)).equals(data))throw new IOException("REMOTE152_BINDING");}
        else FirstLaunchFiles.write(marker,LocalApply67.sign(key,data),false);
    }
    public static BuildSnapshot84.Snapshot verify(Path server,Path folder)throws Exception{
        Path root=ServerApp104.root(server);folder=FirstLaunchFiles.safe(folder);
        if(!folder.startsWith(root.resolve("remote-tests152")))throw new IOException("REMOTE152_PATH");
        byte[] raw=FirstLaunchFiles.read(folder.resolve("remote152"),16384);var data=LocalApply67.verify(FirstLaunchFiles.read(root.resolve("owner74.key"),32),raw);
        var keys=new HashSet<>(data.keySet());keys.remove("device153");if(!keys.equals(Set.of("schema","actor","run","hash","size","generation"))||!"152".equals(data.get("schema"))||!DedicatedAdmin75.open(root).run.toString().equals(data.get("run")))throw new IOException("REMOTE152_BINDING");
        authorize(server,UUID.fromString(data.get("actor")));device153(server,UUID.fromString(data.get("actor")),data.getOrDefault("device153",""));Path jar=FirstLaunchFiles.safe(folder.resolve("artifact.jar"));long size=Long.parseLong(data.get("size"));
        if(size<1||size>64L*1024*1024||Files.size(jar)!=size||!FirstLaunchFiles.hash(jar,64L*1024*1024).equals(data.get("hash")))throw new IOException("REMOTE152_HASH");
        var info=JarInspection.inspect(jar,()->Thread.currentThread().isInterrupted());
        if(!info.mod()||info.modIds().isEmpty()||!Collections.disjoint(info.modIds(),Set.of("mared","genesis","genesis_core","minecraft","neoforge")))throw new IOException("REMOTE152_PROTECTED_MOD");
        try(var zip=new java.util.zip.ZipFile(jar.toFile())){if(zip.getEntry("com/fixmer/mared/Mared.class")!=null)throw new IOException("REMOTE152_PROTECTED_MOD");}
        return new BuildSnapshot84.Snapshot(folder,server,"remote-test","build",new BuildArtifacts.Artifact("artifact.jar",size,data.get("hash")),List.copyOf(info.modIds()),FirstLaunchFiles.sha(raw));
    }
}
