package com.fixmer.genesis.technology.server.firstlaunch;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Authenticated local records between the owner controller and its fresh isolated game generations. */
public final class FirstLaunchSession {
    private final Path root,profile;private final String role,generation;private final byte[] key;private final Map<String,String> config;
    private FirstLaunchSession(Path root,Path profile,String role,String generation,byte[] key,Map<String,String> config){this.root=root;this.profile=profile;this.role=role;this.generation=generation;this.key=key;this.config=config;}
    public static FirstLaunchSession open(Map<String,String> env,String expectedRole)throws IOException{
        if(!env.containsKey("GENESIS_FIRST58_ROOT"))return null;
        String role=env.get("GENESIS_FIRST58_ROLE"),generation=env.get("GENESIS_FIRST58_GENERATION");
        if(!expectedRole.equals(role)||!Set.of("0","1").contains(generation))throw new IOException("FIRST58_ROLE");
        Path root=FirstLaunchFiles.safe(Path.of(env.get("GENESIS_FIRST58_ROOT"))),profile=FirstLaunchFiles.safe(Path.of(""));
        if(!profile.equals(root.resolve("generations").resolve(role+"-"+generation)))throw new IOException("FIRST58_FRESH_GENERATION_REQUIRED");
        byte[] key=FirstLaunchFiles.read(root.resolve("channel.key"),32);if(key.length!=32)throw new IOException("FIRST58_KEY");
        var config=FirstLaunchFiles.verify(key,FirstLaunchFiles.read(root.resolve("session.v58"),8192));
        if(!Objects.equals(config.get("run"),env.get("GENESIS_FIRST58_RUN")))throw new IOException("FIRST58_RUN");
        UUID.fromString(config.get("run"));UUID.fromString(config.get("account"));UUID.fromString(config.get("update"));
        var result=new FirstLaunchSession(root,profile,role,generation,key,config);
        var marker=FirstLaunchFiles.verify(key,FirstLaunchFiles.read(profile.resolve("first58.marker"),8192));
        if(!marker.equals(result.fields("SEALED",0,new UUID(0,0),result.expectedInventory())))throw new IOException("FIRST58_GENERATION_SEAL");return result;
    }
    public Path profile(){return profile;}public UUID run(){return UUID.fromString(config.get("run"));}public UUID account(){return UUID.fromString(config.get("account"));}
    public UUID update(){return UUID.fromString(config.get("update"));}public int generation(){return Integer.parseInt(generation);}
    public String serverPackage(){return config.get("serverPackage");}public String expectedInventory(){return config.get(role+generation);}
    public String minecraft(){return config.get("minecraft");}public String neoforge(){return config.get("neoforge");}
    public void verifyServerMode(boolean authenticated,String address)throws IOException{FirstLaunchDev62.serverMode(config.getOrDefault("authMode","ONLINE"),authenticated,address);}
    public String address(){int port=Integer.parseInt(config.get("port"));if(port<1024||port>65535)throw new IllegalArgumentException("FIRST58_PORT");return "127.0.0.1:"+port;}
    private Map<String,String> fields(String phase,long sequence,UUID request,String inventory){return Map.of("run",run().toString(),"role",role,"generation",generation,"account",account().toString(),"phase",phase,"sequence",Long.toString(sequence),"request",request.toString(),"inventory",inventory);}
    public synchronized void status(String phase,long sequence,UUID request)throws IOException{write(role+"."+generation+".status",fields(phase,sequence,request,expectedInventory()));}
    public synchronized void consent(String phase,UUID offer)throws IOException{if(!role.equals("server")||generation()!=0||!Set.of("ACCEPTED","DECLINED","EXPIRED").contains(phase))throw new IOException("FIRST58_CONSENT_ROLE");write("consent.status",fields(phase,0,offer,serverPackage()));}
    private void write(String name,Map<String,String> values)throws IOException{FirstLaunchFiles.write(root.resolve(name),FirstLaunchFiles.sign(key,values),true);}
    public synchronized Map<String,String> command()throws IOException{
        Path p=root.resolve(role+"."+generation+".command");if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return Map.of();var m=FirstLaunchFiles.verify(key,FirstLaunchFiles.read(p,8192));
        if(!m.keySet().equals(Set.of("run","role","generation","account","phase","sequence","request","inventory"))||!m.get("run").equals(run().toString())||!m.get("role").equals(role)||!m.get("generation").equals(generation)||!m.get("account").equals(account().toString())||!m.get("inventory").equals(expectedInventory())||!m.get("sequence").matches("[1-9][0-9]{0,17}"))throw new IOException("FIRST58_COMMAND_BINDING");UUID.fromString(m.get("request"));return m;
    }
    public void verifyRuntime(Collection<Path> loaded,String mc,String neo)throws IOException{
        if(!minecraft().equals(mc)||!neoforge().equals(neo)||!expectedInventory().equals(FirstLaunchFiles.inventory(profile)))throw new IOException("FIRST58_RUNTIME_VERSION_OR_INVENTORY");
        if(role.equals("client")&&config.containsKey("devHash"))FirstLaunchDev62.verify(profile,loaded,config.get("devHash"));
        // Each top-level managed jar must actually occur in NeoForge's loaded mod-file list.
        try(var files=Files.list(profile.resolve("mods"))){for(Path jar:files.filter(p->p.getFileName().toString().endsWith(".jar")).toList()){
            boolean found=false;for(Path actual:loaded){try{if(Files.isSameFile(jar,actual)){found=true;break;}}catch(IOException|RuntimeException ignored){}}
            if(!found)throw new IOException("FIRST58_MANAGED_JAR_NOT_LOADED");
        }}
    }
}
