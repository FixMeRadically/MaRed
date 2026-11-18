package com.fixmer.mared.technology.server.setup;

import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.mared.Mared;
import com.fixmer.mared.mixin.FirstLaunchConnectAccessor58;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Folder-scoped results. Control and report actions never depend on finding or rebuilding a JAR. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class InPlaceServerService104 {
    private static final AtomicBoolean working=new AtomicBoolean(), reading=new AtomicBoolean();
    private static volatile Map<String,String> state=Map.of("phase","UNSELECTED");
    private static volatile String selected="",result="",report="",pendingRun="",pendingGeneration="";
    private static volatile UUID pending;
    private static volatile ServerJoin128.Target joinTarget128;private static String remembered128="";
    private static volatile long next,lastPollLog;private static volatile String pollError="";
    private static final long JOIN_TIMEOUT_NANOS=TimeUnit.SECONDS.toNanos(45);
    private record JoinAttempt(ConnectScreen screen,Screen previous,String address,long started) {}
    private static JoinAttempt joinAttempt;
    private InPlaceServerService104() {}
    public static boolean busy(){return working.get() || pending!=null;}
    public static boolean working(){return working.get();}
    public static String result(){return result;}
    private static String scope(String value){
        String folder=value.trim();
        if(folder.isBlank())return "";
        try{return Path.of(folder).toAbsolutePath().normalize().toString();}
        catch(InvalidPathException invalid){return folder;}
    }
    public static boolean matchesFolder128(String value){return selected.equals(scope(value));}
    public static java.util.List<String> lanAddresses128(){var target=joinTarget128;return target==null?java.util.List.of():target.lan();}
    public static String joinAddress128(){var target=joinTarget128;return target==null?"":target.address();}
    public static boolean canJoin128(){var mc=net.minecraft.client.Minecraft.getInstance();return !busy()&&joinTarget128!=null&&ServerView105.of(state,true,false).phase().equals("RUNNING")&&mc.level==null&&mc.getConnection()==null;}
    private static void remember128(ServerJoin128.Target target){
        var mc=net.minecraft.client.Minecraft.getInstance();var list=new net.minecraft.client.multiplayer.ServerList(mc);list.load();
        for(int n=0;n<list.size();n++)if(ServerJoin128.sameAddress(list.get(n).ip,target.address()))return;
        list.add(new net.minecraft.client.multiplayer.ServerData(target.label(),target.address(),net.minecraft.client.multiplayer.ServerData.Type.OTHER),false);list.save();
        Mared.LOGGER.info("[Genesis join128] Local server added to Multiplayer: {}",target.address());
    }
    public static synchronized void join128(Screen previous){
        if(!canJoin128())return;
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof ConnectScreen)return;
        var target=joinTarget128;
        try{remember128(target);}
        catch(Exception failure){Mared.LOGGER.warn("[Genesis join128] Could not save local Multiplayer entry",failure);}
        Mared.LOGGER.info("[Genesis join128] Connecting to the active server at {}",target.address());
        ConnectScreen.startConnecting(previous,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(target.address()),
                new net.minecraft.client.multiplayer.ServerData(target.label(),target.address(),net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
        if(mc.screen instanceof ConnectScreen screen)joinAttempt=new JoinAttempt(screen,previous,target.address(),System.nanoTime());
    }

    @SubscribeEvent
    public static void tickJoin128(ClientTickEvent.Post event){
        var attempt=joinAttempt;
        if(attempt==null)return;
        var mc=Minecraft.getInstance();
        if(mc.screen!=attempt.screen()||mc.level!=null){joinAttempt=null;return;}
        var access=(FirstLaunchConnectAccessor58)(Object)attempt.screen();
        if(access.genesisFirst58Aborted()){joinAttempt=null;return;}
        if(System.nanoTime()-attempt.started()<JOIN_TIMEOUT_NANOS)return;
        joinAttempt=null;
        var connection=access.genesisFirst58Connection();
        var future=access.genesisFirst58Future();
        String reason=connection==null?"mared.server156.join_timeout_tcp":"mared.server156.join_timeout_login";
        var message=Component.translatable(reason,attempt.address());
        Mared.LOGGER.warn("[Genesis join128] Timed out at {}. Connection created: {}",attempt.address(),connection!=null);
        access.genesisFirst58SetAborted(true);
        if(future!=null)future.cancel(true);
        if(connection!=null)connection.disconnect(message);
        mc.setScreen(new ConfirmScreen(retry->{
            mc.setScreen(attempt.previous());
            if(retry&&attempt.address().equals(joinAddress128()))join128(attempt.previous());
        },Component.translatable("mared.server156.join_timeout_title"),message));
    }
    public static String report(){return report;}
    public static Map<String,String> state(){return Map.copyOf(state);}
    public static String folder(){try{var p=DedicatedPanelService75.preferences();if(p.has("folder104"))return p.get("folder104").getAsString();if(p.has("source"))return p.get("source").getAsString();return ServerPanelService66.preferencesRead().get("server").getAsString();}catch(Exception ignored){return "";}}
    public static void folder(String value){var p=DedicatedPanelService75.preferences();if(p.has("folder104")&&p.get("folder104").getAsString().equals(value))return;p.addProperty("folder104",value);DedicatedPanelService75.save(p);}
    private static synchronized void select(String scope){
        if(!scope.equals(selected)){selected=scope;state=Map.of("phase",scope.isBlank()?"UNSELECTED":"READING");result="";report="";pending=null;next=0;pollError="";joinTarget128=null;remembered128="";}
    }
    public static void poll(String folder){
        folder=scope(folder);select(folder);if(folder.isBlank()||System.nanoTime()<next||!reading.compareAndSet(false,true))return;
        next=System.nanoTime()+1_000_000_000L;final String scope=folder;
        Thread t=new Thread(()->{try{
            var owner=ServerLocal105.observe(Path.of(scope));
            var latest=observeRollout(Path.of(scope),owner);
            ServerJoin128.Target target128=null;try{if(ServerView105.of(latest,true,false).phase().equals("RUNNING"))target128=ServerJoin128.target(Path.of(scope),latest);}catch(Exception unavailable){}
            final ServerJoin128.Target endpoint128=target128;
            synchronized(InPlaceServerService104.class){if(scope.equals(selected)){
                state=latest;joinTarget128=endpoint128;
                if(endpoint128!=null){String key128=scope+"|"+latest.get("run")+"|"+latest.get("generation");if(!key128.equals(remembered128)){net.minecraft.client.Minecraft.getInstance().execute(()->{synchronized(InPlaceServerService104.class){if(scope.equals(selected)&&endpoint128.equals(joinTarget128)&&ServerView105.of(state,true,false).phase().equals("RUNNING"))try{remember128(endpoint128);remembered128=key128;}catch(Exception failure){Mared.LOGGER.warn("[Genesis join128] Could not save local Multiplayer entry",failure);}}});}}

                if(pending!=null&&(!latest.getOrDefault("run","").equals(pendingRun)||!latest.getOrDefault("generation","").equals(pendingGeneration)||!latest.getOrDefault("phase","").equals("RUNNING")||pending.toString().equals(latest.get("request"))&&Set.of("COMPLETED","REJECTED","FAILED").contains(latest.getOrDefault("requestResult",""))))pending=null;
            }}
        }catch(Exception failed){String signature=failed.getClass().getName()+":"+failed.getMessage();if(scope.equals(selected)&&(!signature.equals(pollError)||System.nanoTime()-lastPollLog>30_000_000_000L)){pollError=signature;lastPollLog=System.nanoTime();try{ServerApp104.diagnostic(Path.of(scope),failed);}catch(Exception ignored){}}synchronized(InPlaceServerService104.class){if(scope.equals(selected)){state=Map.of("phase","OWNER_UNAVAILABLE","error",code(failed));pending=null;joinTarget128=null;}}}
        finally{reading.set(false);}},"genesis-server105-view");t.setDaemon(true);t.start();
    }
    private static Map<String,String> observeRollout(Path server,Map<String,String> owner){
        try{return ServerLocal105.withRollout(server,owner);}
        catch(Exception invalid){return owner;}
    }
    private static String code(Exception failed){String code=failed.getMessage();return code!=null&&code.matches("[A-Z][A-Z0-9_]{1,100}")?code:"SERVER104_OPERATION_FAILED";}
    static ServerArtifact105.Artifact modJar(Path game)throws Exception{
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
        final String scope=scope(folder);if(scope.isBlank()){result="SERVER105_FOLDER_REQUIRED";return;}
        if(!working.compareAndSet(false,true))return;
        final ServerReturnClient108.Ticket returnTicket=action.equals("restart")?ServerReturnClient108.begin(UUID.randomUUID()):null;
        select(scope);result="";Path game;com.google.gson.JsonObject prefs;Path java;
        try{game=(action.equals("start")||action.equals("repair")||action.equals("report"))?net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath():null;prefs=(action.equals("start")||action.equals("repair"))?DedicatedPanelService75.preferences():null;java=(action.equals("start")||action.equals("repair"))?DedicatedPanelService75.java():null;folder(scope);}
        catch(Exception failed){result=code(failed);if(returnTicket!=null)ServerReturnClient108.answer(returnTicket.id(),false);working.set(false);return;}
        Thread t=new Thread(()->{String outcome="DONE",savedReport="";boolean returnAllowed=false;UUID queued=null;Map<String,String> before=Map.of();try{
            Path server=Path.of(scope).toAbsolutePath();if(!Files.isDirectory(server))throw new IOException("SERVER105_FOLDER_REQUIRED");
            if(action.equals("report")){savedReport=ServerDiagnostics105.export(server,game.resolve("logs/latest.log")).toString();outcome="REPORT_SAVED";}
            else if(action.equals("restart")||action.equals("stop")){before=ServerLocal105.observe(server);if(returnTicket!=null)try{returnAllowed=ServerReturnClient108.matchesLocal(returnTicket,server);}catch(Exception targetUnknown){Mared.LOGGER.warn("[Genesis return108] Local target could not be verified; server restart remains available",targetUnknown);}queued=ServerLocal105.command(server,action);outcome="QUEUED";}
            else if(action.equals("acknowledge")){ServerLocal105.acknowledge(server);}
            else if(action.equals("start")||action.equals("repair")){
                if(action.equals("start")&&prefs.has("root")&&prefs.has("source")&&Path.of(prefs.get("source").getAsString()).toAbsolutePath().normalize().equals(server.normalize())){String old=prefs.get("root").getAsString();if(!old.isBlank()&&Files.exists(Path.of(old).resolve("started74")))throw new IOException("SERVER104_LEGACY_WORLD_REVIEW");}
                Path app;
                try{
                    var artifact=modJar(game);
                    app=ServerApp104.deploy(server,InPlaceServerService104.class.getResourceAsStream("/genesis/server59/controller.zip"),new ByteArrayInputStream(artifact.bytes()));
                }catch(IOException unavailable){
                    if(!action.equals("start")||!"SERVER104_BUILD_JAR_REQUIRED".equals(unavailable.getMessage()))throw unavailable;
                    app=ServerApp104.reuseLastDeployment(server);
                    Mared.LOGGER.info("[Genesis server104] Starting with the last verified server deployment");
                }
                Path recoveryLog=ServerApp104.root(server).resolve("logs/recovery161.log");
                Process recovery=ServerApp104.helper(app,"recover-prepared",server,java,recoveryLog);
                if(!recovery.waitFor(180,TimeUnit.SECONDS)){recovery.destroy();throw new IOException("SERVER161_RECOVERY_TIMEOUT");}
                if(recovery.exitValue()!=0)throw new IOException("SERVER161_RECOVERY_FAILED");
                ServerAgent106.prepareStopped(server,app,ServerBootstrap106.key(server));
                Path logs=ServerApp104.root(server).resolve("logs");Files.createDirectories(logs);Process process=ServerApp104.helper(app,"start",server,java,logs.resolve("launch.log"));
                if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroy();throw new IOException("SERVER104_HELPER_TIMEOUT");}
                if(process.exitValue()!=0){String error="SERVER104_START_FAILED";Path log=logs.resolve("launch.log");if(Files.size(log)<32768)for(String line:Files.readString(log).split("\\R"))if(line.matches("[A-Z][A-Z0-9_]{1,100}"))error=line;throw new IOException(error);}
            }else throw new IOException("SERVER104_ACTION");
        }catch(Exception failed){try{ServerApp104.diagnostic(Path.of(scope).toAbsolutePath(),failed);}catch(Exception ignored){}outcome=code(failed);}
        finally{if(returnTicket!=null){boolean accepted=returnAllowed&&queued!=null;net.minecraft.client.Minecraft.getInstance().execute(()->ServerReturnClient108.answer(returnTicket.id(),accepted));}synchronized(InPlaceServerService104.class){if(scope.equals(selected)){result=outcome;if(!savedReport.isEmpty())report=savedReport;if(queued!=null){pending=queued;pendingRun=before.getOrDefault("run","");pendingGeneration=before.getOrDefault("generation","");}next=0;}working.set(false);}}
        },"genesis-server105-action");t.setDaemon(true);t.start();
    }
}
