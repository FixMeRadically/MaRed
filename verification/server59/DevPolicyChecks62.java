package com.fixmer.genesis.technology.server.firstlaunch;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure JDK policy/session verification. It does not start or simulate Minecraft. */
public final class DevPolicyChecks62 {
    private static int checks;
    private static void refused(String mode,boolean auth,String ip)throws Exception {
        try{FirstLaunchDev62.serverMode(mode,auth,ip);throw new AssertionError("Invalid policy accepted");}
        catch(java.io.IOException expected){checks++;}
    }
    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("echo")){for(int i=1;i<args.length;i++)System.out.println(Base64.getEncoder().encodeToString(args[i].getBytes(StandardCharsets.UTF_8)));return;}
        if(args.length>0&&args[0].equals("session")) {
            Path root=Path.of(args[1]);String gen=args[2];
            var session=FirstLaunchSession.open(Map.of("GENESIS_FIRST58_ROOT",root.toString(),"GENESIS_FIRST58_ROLE","client","GENESIS_FIRST58_GENERATION",gen,"GENESIS_FIRST58_RUN",args[3]),"client");
            Path profile=Path.of("").toAbsolutePath();List<Path> loaded=new ArrayList<>();
            try(var files=Files.list(profile.resolve("mods"))){loaded.addAll(files.toList());}
            loaded.add(profile.resolve(".genesis-dev62/mods/MaRed"));
            session.verifyRuntime(loaded,"1.21.1","21.1.264");session.verifyServerMode(false,"127.0.0.1");
            System.out.println("Real core verified signed offline/dev generation "+gen);return;
        }
        FirstLaunchDev62.serverMode("LOCAL_OFFLINE",false,"127.0.0.1");checks++;
        FirstLaunchDev62.serverMode("ONLINE",true,"127.0.0.1");checks++;
        refused("LOCAL_OFFLINE",false,"0.0.0.0");refused("LOCAL_OFFLINE",false,"::1");
        refused("LOCAL_OFFLINE",true,"127.0.0.1");refused("ONLINE",false,"127.0.0.1");
        refused("localOffline",false,"127.0.0.1");
        String expected=UUID.nameUUIDFromBytes("OfflinePlayer:fix".getBytes(StandardCharsets.UTF_8)).toString();
        if(args.length>0&&!expected.equals(args[0]))throw new AssertionError("Offline UUID differs between Python and Java");checks++;
        Path root=Files.createTempDirectory("genesis62-hash-");
        try{
            Path dev=root.resolve(".genesis-dev62");Files.createDirectories(dev.resolve("mods/MaRed"));
            Files.writeString(dev.resolve("roots.v62"),"MaRed=mods/MaRed\n");Files.writeString(dev.resolve("mods/MaRed/fixture.txt"),"stable");
            String hash=FirstLaunchDev62.hash(dev);FirstLaunchDev62.verify(root,List.of(dev.resolve("mods/MaRed")),hash);checks++;
            try{FirstLaunchDev62.verify(root,List.of(root),hash);throw new AssertionError("Unloaded snapshot accepted");}catch(java.io.IOException expectedFailure){checks++;}
            Files.writeString(dev.resolve("mods/MaRed/fixture.txt"),"changed");
            try{FirstLaunchDev62.verify(root,List.of(dev.resolve("mods/MaRed")),hash);throw new AssertionError("Changed snapshot accepted");}catch(java.io.IOException expectedFailure){checks++;}
        }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("Pure-JDK offline/dev policy checks passed: "+checks);
    }
}
