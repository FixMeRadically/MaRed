package com.fixmer.mared.technology.server.setup;

import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.mared.Mared;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Folder-scoped results. Control and report actions never depend on finding or rebuilding a JAR. */
public final class InPlaceServerService104 {
    private static final AtomicBoolean working=new AtomicBoolean(), reading=new AtomicBoolean();
    private static volatile Map<String,String> state=Map.of("phase","UNSELECTED");
    private static volatile String selected="",result="",report="",pendingRun="",pendingGeneration="";
    private static volatile UUID pending;
    private static volatile long next,lastPollLog;private static volatile String pollError="";
    private InPlaceServerService104() {}
    public static boolean busy(){return working.get() || pending!=null;}
    public static boolean working(){return working.get();}
    public static String result(){return result;}
    public static String report(){return report;}
    public static Map<String,String> state(){return Map.copyOf(state);}
    public static String folder(){try{var p=DedicatedPanelService75.preferences();if(p.has("folder104"))return p.get("folder104").getAsString();if(p.has("source"))return p.get("source").getAsString();return ServerPanelService66.preferencesRead().get("server").getAsString();}catch(Exception ignored){return "";}}
    public static void folder(String value){var p=DedicatedPanelService75.preferences();if(p.has("folder104")&&p.get("folder104").getAsString().equals(value))return;p.addProperty("folder104",value);DedicatedPanelService75.save(p);}
    private static synchronized void select(String scope){
        if(!scope.equals(selected)){selected=scope;state=Map.of("phase",scope.isBlank()?"UNSELECTED":"READING");result="";report="";pending=null;next=0;pollError="";}
    }
    public static void poll(String folder){
        folder=folder.trim();select(folder);if(folder.isBlank()||System.nanoTime()<next||!reading.compareAndSet(false,true))return;
        next=System.nanoTime()+1_000_000_000L;final String scope=folder;
        Thread t=new Thread(()->{try{
            var latest=ServerLocal105.observe(Path.of(scope));
            synchronized(InPlaceServerService104.class){if(scope.equals(selected)){
                state=latest;
                if(pending!=null&&(!latest.getOrDefault("run","").equals(pendingRun)||!latest.getOrDefault("generation","").equals(pendingGeneration)||!latest.getOrDefault("phase","").equals("RUNNING")||pending.toString().equals(latest.get("request"))&&Set.of("COMPLETED","REJECTED","FAILED").contains(latest.getOrDefault("requestResult",""))))pending=null;
            }}
        }catch(Exception failed){String signature=failed.getClass().getName()+":"+failed.getMessage();if(scope.equals(selected)&&(!signature.equals(pollError)||System.nanoTime()-lastPollLog>30_000_000_000L)){pollError=signature;lastPollLog=System.nanoTime();try{ServerApp104.diagnostic(Path.of(scope),failed);}catch(Exception ignored){}}synchronized(InPlaceServerService104.class){if(scope.equals(selected)){state=Map.of("phase","OWNER_UNAVAILABLE","error",code(failed));pending=null;}}}
        finally{reading.set(false);}},"genesis-server105-view");t.setDaemon(true);t.start();
    }
    private static String code(Exception failed){String code=failed.getMessage();return code!=null&&code.matches("[A-Z][A-Z0-9_]{1,100}")?code:"SERVER104_OPERATION_FAILED";}
    private static ServerArtifact105.Artifact modJar(Path game)throws Exception{
        var candidates=new LinkedHashSet<Path>();var bases=new LinkedHashSet<Path>();Path preferred=null;
        try{Path source=Path.of(Mared.class.getProtectionDomain().getCodeSource().getLocation().toURI());if(Files.isRegularFile(source)){candidates.add(source);preferred=source;}bases.add(source);}catch(Exception ignored){}
        bases.add(Path.of(System.getProperty("user.dir")));bases.add(game);
        for(Path origin:bases){Path base=origin;for(int n=0;base!=null&&n<7;n++,base=base.getParent())if(Files.isRegularFile(base.resolve("build.gradle"))&&Files.isDirectory(base.resolve("build/libs"))){try(var paths=Files.list(base.resolve("build/libs"))){for(Path p:paths.limit(64).toList())if(p.getFileName().toString().endsWith(".jar"))candidates.add(p);}break;}}
        Path mods=game.resolve("mods");if(Files.isDirectory(mods))try(var paths=Files.list(mods)){for(Path p:paths.limit(256).toList())if(p.getFileName().toString().endsWith(".jar"))candidates.add(p);}
        byte[] marker,bundle;
        try(var in=InPlaceServerService104.class.getResourceAsStream("/com/fixmer/mared/technology/server/firstlaunch/InPlaceWorld104.class")){if(in==null)throw new IOException("SERVER104_CLIENT_BUILD");marker=in.readNBytes(1024*1024);}
        try(var in=InPlaceServerService104.class.getResourceAsStream("/genesis/server59/controller.zip")){if(in==null)throw new IOException("SERVER104_CLIENT_BUILD");bundle=in.readNBytes(100*1024*1024);}
        return ServerArtifact105.select(candidates,preferred,marker,bundle);
    }
    public static void action(String folder,String action){
        final String scope=folder.trim();if(scope.isBlank()){result="SERVER105_FOLDER_REQUIRED";return;}
        if(!working.compareAndSet(false,true))return;
        select(scope);result="";Path game;com.google.gson.JsonObject prefs;Path java;
        try{game=(action.equals("start")||action.equals("repair"))?net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath():null;prefs=(action.equals("start")||action.equals("repair"))?DedicatedPanelService75.preferences():null;java=(action.equals("start")||action.equals("repair"))?DedicatedPanelService75.java():null;folder(scope);}
        catch(Exception failed){result=code(failed);working.set(false);return;}
        Thread t=new Thread(()->{String outcome="DONE",savedReport="";UUID queued=null;Map<String,String> before=Map.of();try{
            Path server=Path.of(scope).toAbsolutePath();if(!Files.isDirectory(server))throw new IOException("SERVER105_FOLDER_REQUIRED");
            if(action.equals("report")){savedReport=ServerDiagnostics105.export(server).toString();outcome="REPORT_SAVED";}
            else if(action.equals("restart")||action.equals("stop")){before=ServerLocal105.observe(server);queued=ServerLocal105.command(server,action);outcome="QUEUED";}
            else if(action.equals("acknowledge")){ServerLocal105.acknowledge(server);}
            else if(action.equals("start")||action.equals("repair")){
                if(action.equals("start")&&prefs.has("root")&&prefs.has("source")&&Path.of(prefs.get("source").getAsString()).toAbsolutePath().normalize().equals(server.normalize())){String old=prefs.get("root").getAsString();if(!old.isBlank()&&Files.exists(Path.of(old).resolve("started74")))throw new IOException("SERVER104_LEGACY_WORLD_REVIEW");}
                var artifact=modJar(game);Path app=ServerApp104.deploy(server,InPlaceServerService104.class.getResourceAsStream("/genesis/server59/controller.zip"),new ByteArrayInputStream(artifact.bytes()));
                ServerAgent106.prepareStopped(server,app,ServerBootstrap106.key(server));
                Path logs=ServerApp104.root(server).resolve("logs");Files.createDirectories(logs);Process process=ServerApp104.helper(app,"start",server,java,logs.resolve("launch.log"));
                if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroy();throw new IOException("SERVER104_HELPER_TIMEOUT");}
                if(process.exitValue()!=0){String error="SERVER104_START_FAILED";Path log=logs.resolve("launch.log");if(Files.size(log)<32768)for(String line:Files.readString(log).split("\\R"))if(line.matches("[A-Z][A-Z0-9_]{1,100}"))error=line;throw new IOException(error);}
            }else throw new IOException("SERVER104_ACTION");
        }catch(Exception failed){try{ServerApp104.diagnostic(Path.of(scope).toAbsolutePath(),failed);}catch(Exception ignored){}outcome=code(failed);}
        finally{synchronized(InPlaceServerService104.class){if(scope.equals(selected)){result=outcome;if(!savedReport.isEmpty())report=savedReport;if(queued!=null){pending=queued;pendingRun=before.getOrDefault("run","");pendingGeneration=before.getOrDefault("generation","");}next=0;}working.set(false);}}
        },"genesis-server105-action");t.setDaemon(true);t.start();
    }
}
