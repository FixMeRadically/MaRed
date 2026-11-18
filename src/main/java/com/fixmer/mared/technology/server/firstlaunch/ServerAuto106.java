package com.fixmer.mared.technology.server.firstlaunch;

import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.mared.Mared;
import net.minecraft.server.MinecraftServer;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Runs only at the head of dedicated initServer, before Minecraft loads its levels. */
public final class ServerAuto106 {
    private static boolean attempted;
    private ServerAuto106() {}
    public static synchronized boolean transfer(MinecraftServer server){
        if(attempted||!ServerBootstrap106.eligible(System.getenv(),server.isDedicatedServer(),System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"),Boolean.getBoolean("genesis.server.auto.disabled")))return false;
        Path profile=server.getServerDirectory();if(!Files.isDirectory(profile.resolve("libraries/net/neoforged/neoforge"))||!Files.isRegularFile(profile.resolve("user_jvm_args.txt")))return false;attempted=true;UUID ticket=null;boolean released=false;
        try{
            var candidates=new LinkedHashSet<Path>(FirstLaunchServer58.loaded());Path preferred=null;
            try{Path path=Path.of(Mared.class.getProtectionDomain().getCodeSource().getLocation().toURI());if(Files.isRegularFile(path))preferred=path;}catch(Exception ignored){}
            try(var files=Files.list(profile.resolve("mods"))){for(Path p:files.limit(256).toList())if(p.getFileName().toString().endsWith(".jar"))candidates.add(p);}
            byte[] marker,bundle;try(var in=ServerAuto106.class.getResourceAsStream("/com/fixmer/mared/technology/server/firstlaunch/InPlaceWorld104.class")){marker=in.readAllBytes();}try(var in=ServerAuto106.class.getResourceAsStream("/genesis/server59/controller.zip")){bundle=in.readAllBytes();}
            var artifact=ServerArtifact105.select(candidates,preferred,marker,bundle);Path app=ServerApp104.deploy(profile,new ByteArrayInputStream(bundle),new ByteArrayInputStream(artifact.bytes()));
            ProcessHandle handle=ProcessHandle.current();var info=handle.info();Path java=Path.of(info.command().orElseThrow());if(java.getFileName().toString().equalsIgnoreCase("javaw.exe"))java=java.resolveSibling("java.exe");
            var source=new ServerBootstrap106.Source(handle.pid(),info.startInstant().orElseThrow().toEpochMilli(),java.toString(),List.of(info.arguments().orElseThrow()));
            ticket=ServerBootstrap106.register(profile,app,source,System.currentTimeMillis());
            Path log=ServerApp104.root(profile).resolve("logs/auto-launch106.log");Files.createDirectories(log.getParent());
            Process helper=ServerApp104.helper106(app,profile,java,ticket,log);long deadline=System.nanoTime()+30_000_000_000L;
            while(System.nanoTime()<deadline){
                if(ServerBootstrap106.waiting(profile,ticket)){
                    ServerAgent106.install(profile,app,ServerBootstrap106.key(profile));ServerBootstrap106.release(profile,ticket);released=true;
                    Mared.LOGGER.info("[Genesis] Initial startup handoff prepared. The controller will start this server after this bootstrap JVM exits.");return true;
                }
                if(!helper.isAlive()&&helper.exitValue()!=0)throw new IOException("SERVER106_HELPER_FAILED");Thread.sleep(100);
            }
            throw new IOException("SERVER106_HANDOFF_TIMEOUT");
        }catch(Exception failed){if(released)return true;try{if(ticket!=null)ServerBootstrap106.cancel(profile,ticket);}catch(Exception ignored){}ServerApp104.diagnostic(profile,failed);Mared.LOGGER.warn("[Genesis] Automatic controller preparation failed; continuing the ordinary server startup. Details are in .genesis/server104/logs.",failed);return false;}
    }
}
