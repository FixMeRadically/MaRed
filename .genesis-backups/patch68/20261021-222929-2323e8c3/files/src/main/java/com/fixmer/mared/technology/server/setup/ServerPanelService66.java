package com.fixmer.mared.technology.server.setup;

import com.fixmer.mared.Mared;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** The game owns its UI and shutdown decision; external worker owns only its NEW game pair. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class ServerPanelService66 {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static volatile JsonObject state=new JsonObject();
    private static volatile Path runtime;private static volatile String request="",phase=System.getenv("GENESIS_FIRST58_ROOT")==null?"IDLE":"MANAGED_SESSION";
    private static volatile boolean armIntent,shutdownIssued;
    private static final Set<String> PHASES=Set.of("VALIDATING","SNAPSHOT","PREPARING","READY","CHECKED","ARMING","ARMED","STARTING","CANCELLED","COMPLETE","FAILED");
    private ServerPanelService66(){}
    public static boolean busy(){return ManagedApplyPanel67.available()?ManagedApplyPanel67.busy():BUSY.get();}
    public static String phase(){return ManagedApplyPanel67.available()?ManagedApplyPanel67.phase():phase;}
    public static JsonObject state(){return ManagedApplyPanel67.available()?ManagedApplyPanel67.state():state.deepCopy();}
    public static Path reportDirectory(){
        try{var s=state();if(s.has("reportDirectory"))return Path.of(s.get("reportDirectory").getAsString());if(runtime!=null)return runtime;String managed=System.getenv("GENESIS_FIRST58_ROOT");return managed==null?null:Path.of(managed);}catch(RuntimeException bad){return runtime;}
    }
    private static JsonObject settings(JsonObject source){
        JsonObject out=new JsonObject();for(String key:List.of("server","jar","side","authMode","port"))if(source.has(key)&&source.get(key).isJsonPrimitive())out.add(key,source.get(key));
        if(!out.has("server"))out.addProperty("server","");if(!out.has("jar"))out.addProperty("jar","");if(!out.has("side"))out.addProperty("side","BOTH");if(!out.has("authMode"))out.addProperty("authMode","LOCAL_OFFLINE");if(!out.has("port"))out.addProperty("port",27690);return out;
    }
    private static Path preferences(){return Minecraft.getInstance().gameDirectory.toPath().resolve("config/mared/server-panel66.json").toAbsolutePath().normalize();}
    public static JsonObject preferencesRead(){
        try{Path p=preferences();if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&Files.size(p)<=16384)return settings(JsonParser.parseString(Files.readString(p)).getAsJsonObject());}catch(IOException|RuntimeException ignored){}
        JsonObject p=new JsonObject();Path candidate=Path.of(System.getProperty("user.home"),"Desktop","server");p.addProperty("server",Files.isDirectory(candidate)?candidate.toString():"");p.addProperty("jar","");p.addProperty("side","BOTH");p.addProperty("authMode","LOCAL_OFFLINE");p.addProperty("port",27690);return p;
    }
    private static void write(Path file,JsonObject object)throws IOException{
        ServerSetupBundle59.ensureDirectory(file.getParent());
        if(Files.isSymbolicLink(file))throw new IOException("Linked panel file");
        Path temp=Files.createTempFile(file.getParent(),"panel66-",".tmp");
        try{Files.writeString(temp,JSON.toJson(object),StandardCharsets.UTF_8);try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
    }
    public static void save(JsonObject prefs){try{write(preferences(),settings(prefs));}catch(IOException|RuntimeException ignored){phase="PREFS_FAILED";}}
    public static void start(JsonObject prefs,boolean checkOnly){
        Minecraft mc=Minecraft.getInstance();
        if(ManagedApplyPanel67.available()){save(prefs);ManagedApplyPanel67.start(settings(prefs),checkOnly);return;}
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows")||System.getenv("LOCALAPPDATA")==null){phase="WINDOWS_REQUIRED";return;}
        if(System.getenv("GENESIS_FIRST58_ROOT")!=null||System.getenv("GENESIS_SUPERVISOR_TOKEN")!=null){phase="MANAGED_SESSION";return;}
        try{if(prefs.get("server").getAsString().isBlank()||prefs.get("port").getAsInt()<1024||prefs.get("port").getAsInt()>65535){phase="INVALID_INPUT";return;}}catch(RuntimeException bad){phase="INVALID_INPUT";return;}
        if(!BUSY.compareAndSet(false,true))return;
        save(prefs);armIntent=false;shutdownIssued=false;request=UUID.randomUUID().toString();phase="VALIDATING";state=new JsonObject();runtime=null;
        final String id=request;
        JsonObject r=settings(prefs);r.addProperty("schema",66);r.addProperty("request",id);r.addProperty("operation",checkOnly?"CHECK":"PREPARE");r.addProperty("pid",ProcessHandle.current().pid());r.addProperty("profile",mc.gameDirectory.toPath().toAbsolutePath().normalize().toString());r.addProperty("account",mc.getUser().getProfileId().toString());
        r.addProperty("neoforge",ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString());
        JsonObject session=new JsonObject();session.addProperty("schema",62);session.addProperty("pid",ProcessHandle.current().pid());session.addProperty("profile",r.get("profile").getAsString());session.addProperty("account",r.get("account").getAsString());session.addProperty("name",mc.getUser().getName());session.addProperty("accessToken",mc.getUser().getAccessToken());session.addProperty("userType",mc.getUser().getType().getName());session.addProperty("issuedAt",System.currentTimeMillis());session.addProperty("devMods",Objects.toString(System.getenv("MOD_CLASSES"),""));session.addProperty("cwd",Path.of("").toAbsolutePath().normalize().toString());
        Thread t=new Thread(()->work(id,r,session),"genesis-server-panel66");t.setDaemon(true);t.start();
    }
    private static void work(String id,JsonObject r,JsonObject session){
        Path folder=null;
        try{
            Path base=Path.of(System.getenv("LOCALAPPDATA"),"Genesis","setup-runtime").toAbsolutePath().normalize();ServerSetupBundle59.ensureDirectory(base);
            folder=Files.createTempDirectory(base,"panel66-");ServerSetupBundle59.privateDirectory(folder);runtime=folder;
            ServerSetupBundle59.unpack(folder,ServerPanelService66.class.getResourceAsStream("/genesis/server59/controller.zip"));
            write(folder.resolve("client-session61.json"),session);write(folder.resolve("request66.json"),r);
            Process process;
            try{process=launch("pyw.exe",folder);}catch(IOException unavailable){process=launch("py.exe",folder);}
            while(process.isAlive()){
                observe(folder,id);Thread.sleep(150);
            }
            observe(folder,id);
            if(!Set.of("COMPLETE","CHECKED","CANCELLED","FAILED").contains(phase))phase="FAILED";
        }catch(InterruptedException stop){Thread.currentThread().interrupt();phase="FAILED";}
        catch(IOException|RuntimeException bad){phase="FAILED";Mared.LOGGER.warn("[Genesis panel66] Worker unavailable ({})",bad.getClass().getSimpleName());}
        finally{
            if(folder!=null)try{Files.deleteIfExists(folder.resolve("client-session61.json"));}catch(IOException ignored){}
            BUSY.set(false);
        }
    }
    private static Process launch(String launcher,Path folder)throws IOException{
        return new ProcessBuilder(launcher,"-3",folder.resolve("tools/server_panel66.py").toString(),folder.toString()).directory(folder.toFile()).redirectErrorStream(true).redirectOutput(folder.resolve("panel-worker66.log").toFile()).start();
    }
    private static void observe(Path folder,String id)throws IOException{
        Path p=folder.resolve("state66.json");if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>65536)return;
        try{JsonObject v=JsonParser.parseString(Files.readString(p)).getAsJsonObject();String next=v.get("phase").getAsString();
            if(v.get("schema").getAsInt()!=66||!id.equals(v.get("request").getAsString())||!PHASES.contains(next))return;
            state=v;phase=next;
        }catch(RuntimeException incomplete){/* Atomic writer; reject an unexpected state instead of guessing. */}
    }
    public static void command(String action){
        if(ManagedApplyPanel67.available()){ManagedApplyPanel67.command(action);return;}
        if(!busy()||runtime==null||!Set.of("ARM","CANCEL","COMMIT").contains(action))return;
        if(action.equals("ARM")&&!phase.equals("READY"))return;
        if(action.equals("ARM"))armIntent=true;
        if(action.equals("CANCEL"))armIntent=false;
        JsonObject c=new JsonObject();c.addProperty("schema",66);c.addProperty("request",request);c.addProperty("action",action);
        try{write(runtime.resolve("command66.json"),c);}catch(IOException bad){armIntent=false;phase="FAILED";}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!armIntent||shutdownIssued||!phase.equals("ARMED")||!busy())return;
        // User explicitly pressed Restart. Doctor and cover acknowledgement already succeeded.
        command("COMMIT");if(!armIntent)return;
        shutdownIssued=true;Minecraft.getInstance().stop();
    }
}
