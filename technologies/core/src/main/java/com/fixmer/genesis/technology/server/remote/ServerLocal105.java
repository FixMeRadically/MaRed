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
        var state=ServerOffline107.observe(server,ServerApp104.state(server));
        Path review=FirstLaunchFiles.safe(ServerApp104.root(server).resolve("review104"));
        if(Files.exists(review)&&Set.of("FAILED","STOPPED","BOOTSTRAP_FAILED_RESTORED").contains(state.getOrDefault("phase",""))){byte[] key=FirstLaunchFiles.read(ServerApp104.root(server).resolve("owner74.key"),32);var record=LocalApply67.verify(key,FirstLaunchFiles.read(review,16384));if(!record.getOrDefault("schema","").equals("104"))throw new IOException("SERVER105_REVIEW_RECORD");var next=new HashMap<>(state);next.put("phase","RECOVERY_REQUIRED");state=Map.copyOf(next);}
        if(state.getOrDefault("phase","").equals("RECOVERY_REQUIRED")){var copy=new HashMap<>(state);copy.put("repairPending",Boolean.toString(Files.exists(ServerApp104.root(server).resolve("change104"),LinkOption.NOFOLLOW_LINKS)));return Map.copyOf(copy);}
        return state;
    }
    public static Map<String,String> withRollout(Path server, Map<String,String> owner) throws IOException {
        if(owner.getOrDefault("run","").isBlank())return owner;
        Path root=ServerApp104.root(server);
        Path journal=FirstLaunchFiles.safe(root.resolve("rollout118"));
        if(!Files.exists(journal))return owner;
        byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);
        var rollout=LocalApply67.verify(key,FirstLaunchFiles.read(journal,16384));
        if(!"118".equals(rollout.get("schema"))||!owner.get("run").equals(rollout.get("run")))return owner;
        var observed=new HashMap<>(owner);
        observed.put("rolloutLabel",rollout.getOrDefault("label",""));
        observed.put("rolloutPhase",rollout.getOrDefault("phase",""));
        observed.put("rolloutReason",rollout.getOrDefault("reason",""));
        return Map.copyOf(observed);
    }

    public static String deploymentStart(Map<String,String> owner,boolean working) {
        String phase=owner.getOrDefault("phase","");
        if(Set.of("RECOVERY_REQUIRED","OWNER_UNAVAILABLE","INVALID_FOLDER","WAITING_FOR_STOP").contains(phase))
            return "BLOCKED";
        if(working||"true".equals(owner.get("busy"))||
            Set.of("STARTING","STOPPING","CHECKING","RESTARTING","WAITING_ALL_INSTALLED").contains(phase))
            return "WAIT";
        if(phase.equals("RUNNING"))return "READY";
        if(Set.of("STOPPED","FAILED","BOOTSTRAP_FAILED_RESTORED").contains(phase))return "START";
        return "BLOCKED";
    }

    public record Deployment(String phase,String error) {}
    public static Deployment deployment(Map<String,String> owner,String run,String label,String request) {
        var waiting=new Deployment("COMMIT_QUEUED","");
        if(run.isBlank()||label.isBlank()||!run.equals(owner.get("run")))return waiting;
        if(label.equals(owner.get("rolloutLabel"))) {
            String phase=owner.getOrDefault("rolloutPhase","");
            if(Set.of("RESTORED","FAILED","INTERRUPTED","CANCELLED").contains(phase)) {
                String reason=owner.getOrDefault("rolloutReason","");
                return new Deployment("ERROR",reason.isBlank()?"FOLDER118_"+phase:reason);
            }
            if(Set.of("ACTIVE_UPDATED","CLOSED").contains(phase)&&"RUNNING".equals(owner.get("phase")))
                return new Deployment("COMMITTED","");
        }
        if(!request.isBlank()&&request.equals(owner.get("request"))&&Set.of("REJECTED","FAILED").contains(owner.getOrDefault("requestResult",""))){
            String error=owner.getOrDefault("error","");
            String phase="REJECTED".equals(owner.get("requestResult"))?"COMMIT_REJECTED":"ERROR";
            return new Deployment(phase,error.isBlank()?"AUTO156_COMMAND_REJECTED":error);
        }
        String phase=owner.getOrDefault("phase","");
        if(Set.of("FAILED","STOPPED","RECOVERY_REQUIRED","BOOTSTRAP_FAILED_RESTORED").contains(phase)){
            String error=owner.getOrDefault("error","");
            return new Deployment("ERROR",error.isBlank()?"AUTO156_SERVER_NOT_READY":error);
        }
        return waiting;
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
            if(Files.isRegularFile(settings)) {
                byte[] raw = FirstLaunchFiles.read(settings, 262144);
                String text = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
                if (text.startsWith("\uFEFF")) text = text.substring(1);
                try (var reader = new StringReader(text)) { properties.load(reader); }
            }
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
