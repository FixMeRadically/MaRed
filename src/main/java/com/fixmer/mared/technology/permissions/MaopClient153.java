package com.fixmer.mared.technology.permissions;
import com.fixmer.mared.Mared;import com.fixmer.genesis.technology.permissions.*;import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;import com.fixmer.mared.technology.workspace.live122.WorkspaceClient122;
import net.minecraft.client.Minecraft;import net.neoforged.api.distmarker.Dist;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;import java.util.concurrent.*;import java.nio.charset.StandardCharsets;
/** Per-server installation keys and exact request signatures; keys never leave the client. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class MaopClient153 {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(run->{var thread=new Thread(run,"MaRed-device153");thread.setDaemon(true);return thread;});
 private static Object connection;private static WorkspaceClient122 client;private static DeviceKey153 key;private static boolean required,working,controller;private static String state="CONNECTING",ticket="",challenge="",proof="",role="NONE";private static int phase;private static long next,counter;
 private MaopClient153(){}
 public static void retry(){state="CONNECTING";phase=0;next=0;}
 public static String role(){return role;}
 public static String state(){return state;}public static boolean controllerReady(){return controller;}public static String fingerprint(){return key==null?"":key.fingerprint();}public static boolean required(){return required;}
 public static String command(){var mc=Minecraft.getInstance();return "maop trust "+mc.getUser().getName()+" "+fingerprint();}
 public static Map<String,String> sign(Map<String,String> request)throws Exception{
  if(!required||!(request.get("op").startsWith("TEST_")||request.get("op").equals("ADMIN153")))return request;
  if(!state.equals("READY")||key==null||ticket.isEmpty())throw new IllegalStateException("AUTH153_REQUIRED");
  var signed=new TreeMap<>(request);signed.put("ticket153",ticket);signed.put("counter153",Long.toString(++counter));signed.put("proof153",key.sign(DeviceSessions153.requestBody(signed)));return Map.copyOf(signed);
 }
 public static void admin(UUID request,String action,String label,java.util.function.Consumer<Map<String,String>> done){if(client==null){done.accept(Map.of("result","AUTH153_REQUIRED"));return;}var fields=new TreeMap<>(WorkspaceClient122.request("ADMIN153","",Map.of("action",action,"label",label)));fields.put("request",request.toString());client.send(fields,done);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();Object nowConnection=mc.getConnection();
  if(connection!=nowConnection){if(client!=null)client.detach();connection=nowConnection;client=null;key=null;state="CONNECTING";required=working=controller=false;phase=0;next=counter=0;ticket=challenge=proof="";if(connection!=null){client=new WorkspaceClient122();client.attach();}}
  if(client==null)return;client.tick();long now=System.nanoTime();if(!client.connected()||client.busy()||working||now-next<0||state.equals("LEGACY")||state.equals("KEY_ERROR"))return;
  if(phase==0){client.send(WorkspaceClient122.request("AUTH_STATUS153","",Map.of()),answer->{if(!"OK".equals(answer.get("result"))){state="LEGACY";return;}required=Boolean.parseBoolean(answer.get("required153"));controller=Boolean.parseBoolean(answer.get("controller153"));role=answer.getOrDefault("role153","NONE");
   if(!required){state="ONLINE";next=System.nanoTime()+5_000_000_000L;return;}
   if(Boolean.parseBoolean(answer.get("authenticated153"))&&!ticket.isEmpty()){state="READY";next=System.nanoTime()+2_000_000_000L;return;}
   if(!answer.getOrDefault("fingerprint153","").isEmpty()&&!ticket.isEmpty()){state="TRUST_REQUIRED";next=System.nanoTime()+2_000_000_000L;return;}
   if(key==null)load();else phase=1;
  });return;}
  if(phase==1){client.send(WorkspaceClient122.request("AUTH_HELLO153","",Map.of("public",key.publicEncoded())),answer->{if(!"OK".equals(answer.get("result"))){state="KEY_ERROR";return;}ticket=answer.get("ticket153");challenge=answer.get("challenge153");Object pinned=connection;working=true;IO.execute(()->{try{String signature=key.sign(challenge);mc.execute(()->{if(connection==pinned){working=false;proof=signature;phase=2;}});}catch(Exception failure){mc.execute(()->{if(connection==pinned){working=false;state="KEY_ERROR";}});}});});return;}
  client.send(WorkspaceClient122.request("AUTH_PROVE153","",Map.of("ticket",ticket,"proof",proof)),answer->{phase=0;counter=0;if(!"OK".equals(answer.get("result"))){state="CONNECTING";next=System.nanoTime()+2_000_000_000L;return;}state=Boolean.parseBoolean(answer.get("authenticated153"))?"READY":"TRUST_REQUIRED";next=System.nanoTime()+1_000_000_000L;});
 }
 private static void load(){var mc=Minecraft.getInstance();if(mc.getCurrentServer()==null||mc.getCurrentServer().ip.isBlank()){state="CONNECTING";next=System.nanoTime()+1_000_000_000L;return;}String endpoint=mc.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);String account=mc.getUser().getName();String directory=FirstLaunchFiles.sha((endpoint+"\n"+account).getBytes(StandardCharsets.UTF_8));Object pinned=connection;working=true;state="CREATING_KEY";
  IO.execute(()->{try{DeviceKey153 loaded=DeviceKey153.load(mc.gameDirectory.toPath().resolve("config/mared/devices153").resolve(directory));mc.execute(()->{if(connection==pinned){key=loaded;working=false;phase=1;}});}catch(Exception failure){mc.execute(()->{if(connection==pinned){working=false;state="KEY_ERROR";}});}});
 }
}
