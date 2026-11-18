package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** OS-owner controls do not redeploy the mod, need Python, or require a fresh build. */
public final class ServerLocal105 {
    private ServerLocal105() {}
    public static Map<String,String> observe(Path server) throws Exception {
        server = FirstLaunchFiles.safe(server);
        if(!Files.isDirectory(server)) return Map.of("phase","INVALID_FOLDER","error","SERVER105_FOLDER_REQUIRED");
        var state=ServerApp104.state(server);
        Path review=FirstLaunchFiles.safe(ServerApp104.root(server).resolve("review104"));
        if(Files.exists(review)&&Set.of("FAILED","STOPPED","BOOTSTRAP_FAILED_RESTORED").contains(state.getOrDefault("phase",""))){byte[] key=FirstLaunchFiles.read(ServerApp104.root(server).resolve("owner74.key"),32);var record=LocalApply67.verify(key,FirstLaunchFiles.read(review,16384));if(!record.getOrDefault("schema","").equals("104"))throw new IOException("SERVER105_REVIEW_RECORD");var next=new HashMap<>(state);next.put("phase","RECOVERY_REQUIRED");state=Map.copyOf(next);}
        if(state.getOrDefault("phase","").equals("RECOVERY_REQUIRED")){var copy=new HashMap<>(state);copy.put("repairPending",Boolean.toString(Files.exists(ServerApp104.root(server).resolve("change104"),LinkOption.NOFOLLOW_LINKS)));return Map.copyOf(copy);}
        return state;
    }
    public static UUID command(Path server,String operation) throws Exception {
        if(!Set.of("restart","stop").contains(operation)) throw new IOException("SERVER104_ACTION");
        var state = observe(server);
        var view = ServerView105.of(state,true,false);
        if(operation.equals("restart") ? !view.canStart() || !view.operation().equals("restart") : !view.canStop()) throw new IOException("SERVER104_BUSY_OR_STOPPED");
        var admin = DedicatedAdmin75.open(ServerApp104.root(server));
        if(!admin.isInPlace104()) throw new IOException("SERVER105_NOT_MANAGED");
        UUID request=UUID.randomUUID();admin.submit(request, operation.equals("restart")?"RESTART":"SHUTDOWN", "r1");return request;
    }
    private static FileChannel lockFile(Path path) throws IOException {
        FirstLaunchFiles.safe(path);
        FileChannel channel = FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        try{FirstLaunchFiles.privateMode(path);return channel;}catch(IOException failed){channel.close();throw failed;}
    }
    private static FileLock lock(FileChannel c) throws IOException {
        try { FileLock held=c.tryLock(0,1,false); if(held==null) throw new IOException("SERVER105_DIRECTORY_BUSY"); return held; }
        catch(OverlappingFileLockException busy) { throw new IOException("SERVER105_DIRECTORY_BUSY",busy); }
    }
    public static void acknowledge(Path server) throws Exception {
        server=FirstLaunchFiles.safe(server);Path root=ServerApp104.root(server);
        if(!Files.isDirectory(root)) throw new IOException("SERVER105_NOT_MANAGED");
        try(var owner=lockFile(root.resolve("owner104.lock"));var ownerLock=lock(owner);
            var game=lockFile(root.resolve("game104.lock"));var gameLock=lock(game)) {
            var state=observe(server);
            if(!state.getOrDefault("phase","").equals("RECOVERY_REQUIRED")) throw new IOException("SERVER105_NO_REVIEW");
            byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);
            var review=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("review104"),16384));
            if(!review.getOrDefault("schema","").equals("104")) throw new IOException("SERVER105_REVIEW_RECORD");
            if(Files.exists(root.resolve("change104"),LinkOption.NOFOLLOW_LINKS)) throw new IOException("SERVER105_RECOVERY_PENDING");
            var properties=new Properties();Path settings=FirstLaunchFiles.safe(server.resolve("server.properties"));
            if(Files.isRegularFile(settings)) try(var reader=Files.newBufferedReader(settings)){properties.load(reader);}
            String level=properties.getProperty("level-name","world");
            if(!level.matches("[A-Za-z0-9_-]{1,80}") || Set.of("mods","config","libraries").contains(level.toLowerCase(Locale.ROOT))) throw new IOException("SERVER104_WORLD_NAME");
            Path world=FirstLaunchFiles.safe(server.resolve(level));
            if(Files.exists(world)) {
                Path session=FirstLaunchFiles.safe(world.resolve("session.lock"));
                try(var worldChannel=FileChannel.open(session,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var worldLock=lock(worldChannel)) { complete(root,key,state); }
            } else complete(root,key,state);
        }
    }
    private static void complete(Path root,byte[] key,Map<String,String> old) throws Exception {
        // Publish STOPPED before removing the review marker; a crash still leaves the marker blocking startup.
        var state=new HashMap<>(old);state.remove("repairPending");state.put("phase","STOPPED");state.put("error","");state.put("busy","false");state.put("updated",Long.toString(System.currentTimeMillis()));
        FirstLaunchFiles.write(root.resolve("state104"),LocalApply67.sign(key,state),true);
        Files.delete(root.resolve("review104"));
    }
}
