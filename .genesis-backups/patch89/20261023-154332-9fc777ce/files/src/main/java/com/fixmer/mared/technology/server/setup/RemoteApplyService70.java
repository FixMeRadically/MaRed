package com.fixmer.mared.technology.server.setup;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Explicit local handoff for one remote client. The worker owns only its NEW child. */
@EventBusSubscriber(modid=com.fixmer.mared.Mared.MOD_ID,value=Dist.CLIENT)
public final class RemoteApplyService70 {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();private static final AtomicBoolean busy=new AtomicBoolean();
    private static volatile String phase="IDLE",request="";private static volatile JsonObject state=new JsonObject();private static volatile Path folder;
    private static volatile boolean armIntent,shutdown,cancelRequested;private static final Set<String> PHASES=Set.of("WAITING_SERVER","VALIDATING","PREPARING","READY","ARMING","ARMED","STARTING","CONNECTING","ACTIVE","COMPLETE","CANCELLED","FAILED");
    private RemoteApplyService70(){}
    public static boolean busy(){return ManagedRemote71.available()?ManagedRemote71.busy():busy.get();}public static String phase(){return ManagedRemote71.available()?ManagedRemote71.phase():phase;}public static JsonObject state(){return ManagedRemote71.available()?ManagedRemote71.state():state.deepCopy();}
    public static Path reports(){if(ManagedRemote71.available())return ManagedRemote71.reports();return folder==null?null:Files.isDirectory(folder.resolve("run"))?folder.resolve("run"):folder;}
    private static void write(Path target,JsonObject data)throws Exception{com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles.write(target,JSON.toJson(data).getBytes(StandardCharsets.UTF_8),true);}
    public static synchronized void start(String mode){start0(mode,RemoteService69.download(),null);}
    public static synchronized void startControlled73(String mode,com.fixmer.genesis.technology.server.remote.RemoteClient69.Download d,byte[] control){start0(mode,d,control);}
    public static synchronized boolean startBootstrap76(String mode,com.fixmer.genesis.technology.server.remote.RemoteClient69.Download d,byte[] control){if(busy())return false;start0(mode,d,control);return busy();}
    private static void start0(String mode,com.fixmer.genesis.technology.server.remote.RemoteClient69.Download candidate,byte[] control){
        if(ManagedRemote71.available()){ManagedRemote71.start(candidate,control);return;}
        if(busy())return;if(ServerPanelService66.busy()||com.fixmer.mared.dev.client.DevActivationService.isActivating()||com.fixmer.mared.dev.client.DevRestartService.isRestartInProgress()){phase="OWNER_BUSY";return;}if(candidate==null){phase="NO_CANDIDATE";return;}
        if(System.getenv("GENESIS_FIRST58_ROOT")!=null||System.getenv("GENESIS_REMOTE70_ROOT")!=null||System.getenv("GENESIS_SUPERVISOR_TOKEN")!=null){phase="MANAGED";return;}
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows")||System.getenv("LOCALAPPDATA")==null){phase="WINDOWS";return;}
        if(!Set.of("ONLINE","LOCAL_OFFLINE").contains(mode)){phase="FAILED";return;}
        if(!busy.compareAndSet(false,true))return;phase="VALIDATING";state=new JsonObject();request=UUID.randomUUID().toString();folder=null;armIntent=false;shutdown=false;cancelRequested=false;
        try{
            Minecraft mc=Minecraft.getInstance();JsonObject r=new JsonObject();r.addProperty("schema",70);r.addProperty("request",request);r.addProperty("pid",ProcessHandle.current().pid());r.addProperty("birth",ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli());r.addProperty("profile",mc.gameDirectory.toPath().toAbsolutePath().normalize().toString());r.addProperty("account",mc.getUser().getProfileId().toString());r.addProperty("neoforge",ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString());r.addProperty("cache",RemoteService69.root().toAbsolutePath().normalize().toString());r.addProperty("candidate",candidate.directory().toAbsolutePath().normalize().toString());r.addProperty("authMode",mode);
            JsonObject s=new JsonObject();s.addProperty("schema",62);for(String key:List.of("pid","profile","account"))s.add(key,r.get(key));s.addProperty("name",mc.getUser().getName());s.addProperty("accessToken",mc.getUser().getAccessToken());s.addProperty("userType",mc.getUser().getType().getName());s.addProperty("issuedAt",System.currentTimeMillis());s.addProperty("devMods",Objects.toString(System.getenv("MOD_CLASSES"),""));s.addProperty("cwd",Path.of("").toAbsolutePath().normalize().toString());
            Thread worker=new Thread(()->work(r,s,control),"genesis-remote-handoff70");worker.setDaemon(true);worker.start();
        }catch(RuntimeException bad){phase="FAILED";busy.set(false);}
    }
    private static void work(JsonObject r,JsonObject session,byte[] control){
        try{
            Path parent=Path.of(System.getenv("LOCALAPPDATA"),"Genesis","remote-installs70");ServerSetupBundle59.ensureDirectory(parent);folder=Files.createTempDirectory(parent,"update-");ServerSetupBundle59.privateDirectory(folder);
            ServerSetupBundle59.unpack(folder,RemoteApplyService70.class.getResourceAsStream("/genesis/server59/controller.zip"));write(folder.resolve("request70.json"),r);if(control!=null)com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles.write(folder.resolve("control73.cfg"),control,false);write(folder.resolve("client-session61.json"),session);if(cancelRequested){phase="CANCELLED";return;}Process process;
            try{process=launch("pyw.exe");}catch(java.io.IOException absent){process=launch("py.exe");}
            while(process.isAlive()){observe();Thread.sleep(150);}observe();if(!Set.of("CANCELLED","COMPLETE","FAILED").contains(phase))phase="FAILED";
        }catch(Exception failure){phase="FAILED";}
        finally{if(folder!=null)try{Files.deleteIfExists(folder.resolve("client-session61.json"));}catch(java.io.IOException ignored){}busy.set(false);}
    }
    private static Process launch(String launcher)throws java.io.IOException{return new ProcessBuilder(launcher,"-3",folder.resolve("tools/remote_client70.py").toString(),folder.toString()).directory(folder.toFile()).redirectErrorStream(true).redirectOutput(folder.resolve("worker70.log").toFile()).start();}
    private static void observe()throws Exception{
        Path file=folder.resolve("state70.json");if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>65536)return;
        JsonObject next=JsonParser.parseString(Files.readString(file)).getAsJsonObject();if(next.get("schema").getAsInt()!=70||!request.equals(next.get("request").getAsString())||!PHASES.contains(next.get("phase").getAsString()))return;state=next;phase=next.get("phase").getAsString();
    }
    public static void command(String action){
        if(ManagedRemote71.available()){ManagedRemote71.command(action);return;}
        if(!busy()||!Set.of("ARM","CANCEL","COMMIT").contains(action)||action.equals("ARM")&&!phase.equals("READY"))return;
        if(action.equals("CANCEL")){cancelRequested=true;armIntent=false;}if(folder==null)return;
        if(action.equals("ARM"))armIntent=true;if(action.equals("CANCEL"))armIntent=false;
        JsonObject c=new JsonObject();c.addProperty("schema",70);c.addProperty("request",request);c.addProperty("action",action);
        try{write(folder.resolve("command70.json"),c);}catch(Exception failure){armIntent=false;phase="FAILED";}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(ManagedRemote71.available()||!armIntent||shutdown||!phase.equals("ARMED")||!busy())return;command("COMMIT");if(!armIntent)return;shutdown=true;Minecraft.getInstance().stop();}
}
