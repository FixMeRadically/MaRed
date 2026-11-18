"""Compile and drive the actual RemoteFlow73 tick handler with explicit Minecraft/worker stubs."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess, os
import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--project',type=Path,default=Path(__file__).resolve().parents[2])
parser.add_argument('--classes',type=Path,required=True)
options=parser.parse_args();project=options.project;classes=options.classes
stubs={
 'net.minecraft.client.Minecraft':'''public class Minecraft {
 public static final Minecraft game=new Minecraft();
 public final Object connection=new Object();
 public User player=new User();
 public static Minecraft getInstance(){return game;}
 public Object getConnection(){return connection;}
 public User getUser(){return player;}
 public void execute(Runnable task){task.run();}
 public static class User {
  public java.util.UUID getUUID(){return new java.util.UUID(0,1);}
  public java.util.UUID getProfileId(){return getUUID();}
 }
}''',
 'net.neoforged.api.distmarker.Dist':'public enum Dist {CLIENT}',
 'net.neoforged.bus.api.SubscribeEvent':'public @interface SubscribeEvent {}',
 'net.neoforged.fml.common.EventBusSubscriber':'public @interface EventBusSubscriber {String modid(); net.neoforged.api.distmarker.Dist value();}',
 'net.neoforged.neoforge.client.event.ClientTickEvent':'public class ClientTickEvent {public static class Post {}}',
 'net.neoforged.fml.ModList':'''public class ModList {
 public static ModList get(){return new ModList();}
 public java.util.Optional<ModList> getModContainerById(String id){return java.util.Optional.of(this);}
 public ModList getModInfo(){return this;}
 public String getVersion(){return "21.1.264";}
}''',
 'com.fixmer.mared.Mared':'''public class Mared {
 public static final String MOD_ID="mared";
 public static final Logger LOGGER=new Logger();
 public static class Logger {public void error(String message,Object... args){}}
}''',
 'com.fixmer.mared.technology.server.remote.RemoteControlPayloads73':'public class RemoteControlPayloads73 {public static java.util.function.Consumer<byte[]> clientSink;}',
 'com.fixmer.mared.technology.server.firstlaunch.RemoteClient70':'public class RemoteClient70 {public static boolean ready73(){return false;}}',
 'com.fixmer.mared.technology.server.setup.RemoteService69':'public class RemoteService69 {public static java.nio.file.Path root(){return java.nio.file.Path.of(".");}}',
 'com.fixmer.mared.technology.server.setup.ManagedRemote71':'public class ManagedRemote71 {public static boolean active; public static boolean available(){return active;} public static String phase(){return "OWNER_UNAVAILABLE";}}',
 'com.fixmer.mared.technology.server.setup.RemoteApplyService70':'''public class RemoteApplyService70 {
 public static String phase="VALIDATING";
 public static boolean busy=true;
 public static int arms;
 public static boolean busy(){return busy;}
 public static String phase(){return phase;}
 public static void command(String action){if(action.equals("ARM"))arms++;}
 public static void startControlled73(String mode,com.fixmer.genesis.technology.server.remote.RemoteClient69.Download download,byte[] bytes){}
 public static Report state(){return new Report();}
 public static class Report {public Value get(String key){return new Value();}}
 public static class Value {public boolean isJsonPrimitive(){return true;} public String getAsString(){return "fixture failure";}}
}''',
 'com.fixmer.mared.technology.server.remote.client.RemoteConsentClient72':'''public class RemoteConsentClient72 {
 public static com.fixmer.genesis.technology.server.remote.RemoteConsent72.Frame frame;
 public static com.fixmer.genesis.technology.server.remote.RemoteConsent72.Frame current73(){return frame;}
 public static boolean authorized125(){return false;}
 public static void clearIntent125(){}
}'''
}
driver='''import java.util.*;
import com.fixmer.genesis.technology.server.remote.RemoteConsent72.Frame;
import com.fixmer.mared.technology.server.remote.client.*;
import com.fixmer.mared.technology.server.setup.RemoteApplyService70;
public class FlowCheck {
 static void set(String name,Object value)throws Exception{
  var field=RemoteFlow73.class.getDeclaredField(name);field.setAccessible(true);field.set(null,value);
 }
 static void tick(String worker,String phase){
  RemoteApplyService70.phase=worker;
  RemoteFlow73.tick(null);
  if(!RemoteFlow73.phase().equals(phase))throw new AssertionError(worker+" -> "+RemoteFlow73.phase());
 }
 public static void main(String[] args)throws Exception{
  String hash="a".repeat(64),game="localhost:25565",endpoint="https://localhost:8443/";
  var f=new Frame(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
   hash,game,endpoint,hash,1,1,0,"ACTIVE","ACCEPTED");
  RemoteConsentClient72.frame=f;
  set("connection",net.minecraft.client.Minecraft.game.connection);
  set("cfg",Map.of("round",f.round().toString(),"ticket",f.ticket().toString(),"release",f.release().toString(),
   "archive",hash,"game",game,"endpoint",endpoint,"signer",hash));
  set("launched",true);set("handedOff",true);set("phase","PREPARING");
  tick("VALIDATING","VALIDATING");tick("PREPARING","PREPARING");tick("WAITING_SERVER","WAITING_SERVER");
  tick("READY","ARMING");tick("READY","ARMING");
  if(RemoteApplyService70.arms!=1)throw new AssertionError("Duplicate ARM");
  tick("ARMED","ARMED");
  set("phase","VALIDATING");RemoteApplyService70.busy=false;tick("FAILED","FAILED");
  if(!RemoteFlow73.failureReason().equals("fixture failure"))throw new AssertionError("Hidden failure");
  RemoteFlow73.authorize(f);set("handedOff",true);set("launched",true);set("phase","VALIDATING");
  RemoteApplyService70.busy=true;tick("READY","ARMING");
  if(RemoteApplyService70.arms!=2)throw new AssertionError("Retry did not arm");
  set("phase","PREPARING");set("handedOff",false);
  set("pending",new com.fixmer.genesis.technology.server.remote.RemoteClient69.Download(null,null,null));
  com.fixmer.mared.technology.server.setup.ManagedRemote71.active=true;
  tick("OWNER_UNAVAILABLE","FAILED");
  if(!RemoteFlow73.failureReason().equals("REMOTE73_OWNER_UNAVAILABLE"))throw new AssertionError("Owner failure hidden");
  var diagnostic=RemoteFlow73.class.getDeclaredMethod("downloadFailure166",Exception.class);diagnostic.setAccessible(true);
  String secret="private-token-DO-NOT-PRINT";
  for(Exception error:List.of(new java.net.ConnectException(secret),new java.net.SocketTimeoutException(secret),new javax.net.ssl.SSLException(secret),new IllegalStateException(secret))){
   String message=(String)diagnostic.invoke(null,error);
   if(!message.startsWith("REMOTE73_DOWNLOAD_")||message.contains(secret))throw new AssertionError("Unsafe or absent diagnostic");
  }
  RemoteFlow73.authorize(f);
  if(!RemoteFlow73.failureReason().isEmpty())throw new AssertionError("Stale failure survived retry");
  System.out.println("Flow166: diagnostic redaction and retry reset; Flow164: validating, preparation, signed wait, ARM once, failure visibility, retry and unavailable owner");
 }
}'''
with TemporaryDirectory() as path:
 p=Path(path);sources=[]
 for name,body in stubs.items():
  target=p/(name.replace('.','/')+'.java');target.parent.mkdir(parents=True,exist_ok=True)
  target.write_text('package '+name.rsplit('.',1)[0]+';\n'+body);sources.append(target)
 d=p/'FlowCheck.java';d.write_text(driver);sources.append(d)
 sources.append(project/'src/main/java/com/fixmer/mared/technology/server/remote/client/RemoteFlow73.java')
 subprocess.run(['java','com.sun.tools.javac.Main','-cp',str(classes),'-d',str(p),*map(str,sources)],check=True)
 subprocess.run(['java','-cp',str(p)+os.pathsep+str(classes),'FlowCheck'],check=True)
