package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** A CLIENT-only local channel bound to one new profile and one external owner. */
public final class RemoteSession70 {
    private final Path root,profile;private final byte[] key;private final Map<String,String> config;
    private RemoteSession70(Path root,Path profile,byte[] key,Map<String,String> config){this.root=root;this.profile=profile;this.key=key;this.config=config;}
    public static RemoteSession70 open(Map<String,String> env)throws IOException{
        String raw=env.get("GENESIS_REMOTE70_ROOT");if(raw==null)return null;Path root=FirstLaunchFiles.safe(Path.of(raw)),profile=FirstLaunchFiles.safe(Path.of(""));
        if(!profile.equals(root.resolve("generations/client-1")))throw new IOException("REMOTE70_PROFILE");byte[] key=FirstLaunchFiles.read(root.resolve("channel.key"),32);var config=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("session70"),131072));
        var fields89=new HashSet<>(config.keySet());fields89.remove("authMode");if(config.containsKey("authMode")&&!Set.of("ONLINE","LOCAL_OFFLINE").contains(config.get("authMode")))throw new IOException("AUTH89_SESSION");
        if(!fields89.equals(Set.of("run","account","game","inventory","minecraft","neoforge","devHash"))||!config.get("run").equals(env.get("GENESIS_REMOTE70_RUN"))||!config.get("inventory").matches("[a-f0-9]{64}")||!config.get("devHash").equals("none")&&!config.get("devHash").matches("[a-f0-9]{64}"))throw new IOException("REMOTE70_SESSION");
        UUID.fromString(config.get("run"));UUID.fromString(config.get("account"));if(!config.get("game").equals(RemoteOffer69.gameAddress(config.get("game"))))throw new IOException("REMOTE70_ADDRESS");
        if(!LocalApply67.verify(key,FirstLaunchFiles.read(profile.resolve("remote70.marker"),131072)).equals(config))throw new IOException("REMOTE70_SEAL");return new RemoteSession70(root,profile,key,config);
    }
    public Path authRoot89(){return root;}
    public Map<String,String> authContext89(){if(!config.containsKey("authMode"))return Map.of();return Map.of("sourceRun",config.get("run"),"profile",profile.toString(),"account",config.get("account"),"mode",config.get("authMode"),"scope","MANAGED","pid","owned","birth","owned");}
    public String address(){return config.get("game");}public UUID account(){return UUID.fromString(config.get("account"));}
    public void verifyRuntime(Collection<Path> loaded,String minecraft,String neoforge)throws IOException{
        if(!minecraft.equals(config.get("minecraft"))||!neoforge.equals(config.get("neoforge"))||!FirstLaunchFiles.inventory(profile).equals(config.get("inventory")))throw new IOException("REMOTE70_RUNTIME");
        if(!config.get("devHash").equals("none"))FirstLaunchDev62.verify(profile,loaded,config.get("devHash"));
        try(var files=Files.list(profile.resolve("mods"))){for(Path jar:files.filter(p->p.getFileName().toString().endsWith(".jar")).toList()){
            boolean found=false;for(Path item:loaded)try{if(Files.isSameFile(jar,item)){found=true;break;}}catch(IOException ignored){}if(!found)throw new IOException("REMOTE70_JAR_NOT_LOADED");
        }}
    }
    public synchronized Map<String,String> command()throws IOException{
        Path path=root.resolve("client.command");if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return Map.of();var m=LocalApply67.verify(key,FirstLaunchFiles.read(path,131072));
        if(!m.keySet().equals(Set.of("run","account","inventory","phase","sequence","request"))||!m.get("run").equals(config.get("run"))||!m.get("account").equals(config.get("account"))||!m.get("inventory").equals(config.get("inventory"))||!m.get("sequence").matches("[1-9][0-9]{0,17}"))throw new IOException("REMOTE70_COMMAND");UUID.fromString(m.get("request"));return m;
    }
    public synchronized void status(String phase,long sequence,UUID request)throws IOException{
        FirstLaunchFiles.write(root.resolve("client.status"),LocalApply67.sign(key,Map.of("run",config.get("run"),"account",config.get("account"),"inventory",config.get("inventory"),"phase",phase,"sequence",Long.toString(sequence),"request",request.toString())),true);
    }
}
