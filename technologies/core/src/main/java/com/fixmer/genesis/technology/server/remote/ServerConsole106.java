package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.io.*;
import java.util.*;

/** Local terminal -> Minecraft command mailbox. No shell evaluation or remote-player entry point. */
public final class ServerConsole106 {
    private ServerConsole106() {}
    public static UUID submit(Path server,String text)throws Exception{
        text=text.trim();if(text.isEmpty()||text.length()>2048||text.chars().anyMatch(c->c<32||c==127))throw new IOException("SERVER106_CONSOLE_TEXT");
        Path root=ServerApp104.root(server);var admin=DedicatedAdmin75.open(root);if(!admin.isInPlace104())throw new IOException("SERVER105_NOT_MANAGED");
        var live=ServerApp104.state(server);if(!live.getOrDefault("generation","").matches("[1-9][0-9]{0,8}"))throw new IOException("SERVER106_STARTING_WAIT");
        String control=text.startsWith("/")?text.substring(1):text;
        if(control.equalsIgnoreCase("stop")){UUID id=UUID.randomUUID();admin.submit(id,"SHUTDOWN","r1");return id;}
        if(control.equalsIgnoreCase("genesisrestart")){UUID id=UUID.randomUUID();admin.submit(id,"RESTART","r1");return id;}
        var state=ServerApp104.state(server);if(!state.getOrDefault("phase","").equals("RUNNING")||state.getOrDefault("busy","false").equals("true"))throw new IOException("SERVER104_BUSY_OR_STOPPED");
        byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);String run=state.get("run"),launch=state.get("launch"),generation=state.get("generation");UUID.fromString(launch);Path channel=FirstLaunchFiles.safe(root.resolve("runs").resolve(launch));
        Path lock=root.resolve("console106.lock");try(var c=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var held=c.tryLock(0,1,false)){
            if(held==null)throw new IOException("SERVER106_CONSOLE_BUSY");FirstLaunchFiles.privateMode(lock);long sequence=1;Path request=channel.resolve("console106.request"),ack=channel.resolve("console106.ack");
            if(Files.exists(request)){
                var previous=LocalApply67.verify(key,FirstLaunchFiles.read(request,16384));var done=Files.exists(ack)?LocalApply67.verify(key,FirstLaunchFiles.read(ack,16384)):Map.<String,String>of();
                if(!previous.get("request").equals(done.get("request"))||!run.equals(done.get("run"))||!launch.equals(done.get("launch")))throw new IOException("SERVER106_CONSOLE_PENDING");
                sequence=Long.parseLong(previous.get("sequence"))+1;
            }
            if(sequence>999999999999999L)throw new IOException("SERVER106_CONSOLE_LIMIT");UUID id=UUID.randomUUID();
            FirstLaunchFiles.write(request,LocalApply67.sign(key,Map.of("schema","106","run",run,"launch",launch,"generation",generation,"sequence",Long.toString(sequence),"request",id.toString(),"text",text)),true);return id;
        }
    }
    public static Map<String,String> command(Path root,Path profile,Map<String,String> env,long previous)throws Exception{
        var session=DedicatedSession74.open(env);if(session==null||!session.root.equals(FirstLaunchFiles.safe(root))||!session.profile.equals(FirstLaunchFiles.safe(profile)))throw new IOException("SERVER106_CONSOLE_PROFILE");
        String launch=env.getOrDefault("GENESIS_INPLACE_LAUNCH","");UUID.fromString(launch);Path path=root.resolve("runs").resolve(launch).resolve("console106.request");if(!Files.exists(path))return Map.of();byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);
        var d=LocalApply67.verify(key,FirstLaunchFiles.read(path,16384));
        if(!d.keySet().equals(Set.of("schema","run","launch","generation","sequence","request","text"))||!d.get("schema").equals("106")||!d.get("run").equals(env.get("GENESIS_DEDICATED74_RUN"))||!d.get("launch").equals(launch)||!d.get("generation").equals(env.get("GENESIS_DEDICATED74_GENERATION"))||!d.get("sequence").matches("[1-9][0-9]{0,14}")||d.get("text").isBlank()||d.get("text").length()>2048||d.get("text").chars().anyMatch(c->c<32||c==127)||UUID.fromString(d.get("request")).equals(new UUID(0,0)))throw new IOException("SERVER106_CONSOLE_BINDING");
        return Long.parseLong(d.get("sequence"))<=previous?Map.of():d;
    }
    public static void ack(Path root,Map<String,String> command)throws Exception{
        byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);Path channel=FirstLaunchFiles.safe(root.resolve("runs").resolve(command.get("launch")));
        FirstLaunchFiles.write(channel.resolve("console106.ack"),LocalApply67.sign(key,Map.of("schema","106","run",command.get("run"),"launch",command.get("launch"),"request",command.get("request"))),true);
    }
}
