package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.io.*;
import java.util.*;
import java.security.SecureRandom;

/** First-start ticket comes from the current dedicated JVM; only an ACK permits it to yield startup. */
public final class ServerBootstrap106 {
    private ServerBootstrap106() {}
    public record Source(long pid,long created,String java,List<String> arguments){}
    public static boolean eligible(Map<String,String> env,boolean dedicated,boolean windows,boolean disabled){
        if(!dedicated||!windows||disabled)return false;
        for(String k:env.keySet())if(k.startsWith("GENESIS_"))return false;
        return true;
    }
    public static byte[] key(Path server)throws Exception{
        Path root=ServerApp104.root(server),path=root.resolve("owner74.key");Files.createDirectories(root);FirstLaunchFiles.privateMode(root);
        if(!Files.exists(path)){byte[] key=new byte[32];new SecureRandom().nextBytes(key);try{FirstLaunchFiles.write(path,key,false);}catch(FileAlreadyExistsException concurrent){}}
        byte[] key=FirstLaunchFiles.read(path,32);if(key.length!=32)throw new IOException("SERVER104_KEY");return key;
    }
    public static UUID register(Path server,Path app,Source source,long now)throws Exception{
        server=FirstLaunchFiles.safe(server);app=FirstLaunchFiles.safe(app);Path root=ServerApp104.root(server);
        if(!app.getParent().equals(root.resolve("app"))||!app.getFileName().toString().matches("[a-f0-9]{64}")||source.pid()<=0||source.pid()>0xffffffffL||source.created()<=0||source.created()>now||!Path.of(source.java()).isAbsolute()||source.arguments().size()>128)throw new IOException("SERVER106_SOURCE_RECORD");
        StringBuilder argv=new StringBuilder("[");boolean comma=false;
        for(String value:source.arguments()){if(value.length()>4096||value.chars().anyMatch(c->c<32||c==127))throw new IOException("SERVER106_SOURCE_ARGUMENTS");if(comma)argv.append(',');comma=true;argv.append('"');for(char c:value.toCharArray()){if(c=='"'||c=='\\')argv.append('\\');argv.append(c);}argv.append('"');}argv.append(']');
        byte[] key=key(server);UUID ticket=UUID.randomUUID();var d=Map.ofEntries(Map.entry("schema","106"),Map.entry("ticket",ticket.toString()),Map.entry("profile",server.toString()),Map.entry("app",app.toString()),Map.entry("java",source.java()),Map.entry("pid",Long.toString(source.pid())),Map.entry("created",Long.toString(source.created())),Map.entry("inventory",FirstLaunchFiles.inventory(server)),Map.entry("issued",Long.toString(now)),Map.entry("argv",argv.toString()));
        FirstLaunchFiles.write(root.resolve("auto106.request"),LocalApply67.sign(key,d),true);return ticket;
    }
    public static boolean waiting(Path server,UUID ticket)throws Exception{
        Path root=ServerApp104.root(server),file=root.resolve("auto106.answer");if(!Files.exists(file))return false;
        var d=LocalApply67.verify(key(server),FirstLaunchFiles.read(file,16384));if(!ticket.toString().equals(d.get("ticket")))return false;
        if(d.getOrDefault("result","").equals("FAILED"))throw new IOException(d.getOrDefault("error","SERVER106_HANDOFF_FAILED"));
        return d.equals(Map.of("schema","106","ticket",ticket.toString(),"result","WAITING_FOR_SOURCE_EXIT"));
    }
    public static void cancel(Path server,UUID ticket)throws Exception{
        Path root=ServerApp104.root(server);FirstLaunchFiles.write(root.resolve("auto106.cancel"),LocalApply67.sign(key(server),Map.of("schema","106","ticket",ticket.toString())),true);
    }
    public static void release(Path server,UUID ticket)throws Exception{
        Path root=ServerApp104.root(server);byte[] key=key(server);var d=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("auto106.request"),16384));
        if(!ticket.toString().equals(d.get("ticket"))||!waiting(server,ticket)||!FirstLaunchFiles.inventory(server).equals(d.get("inventory")))throw new IOException("SERVER106_RELEASE_BINDING");
        FirstLaunchFiles.write(root.resolve("auto106.release"),LocalApply67.sign(key,Map.of("schema","106","ticket",ticket.toString(),"profile",FirstLaunchFiles.safe(server).toString(),"phase","RELEASED_FOR_HANDOFF","inventory",d.get("inventory"))),true);
    }
}
