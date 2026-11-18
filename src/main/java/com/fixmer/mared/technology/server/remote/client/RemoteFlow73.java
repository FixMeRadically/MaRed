package com.fixmer.mared.technology.server.remote.client;
import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.genesis.technology.server.remote.RemoteConsent72.Frame;
import com.fixmer.mared.Mared;
import com.fixmer.mared.technology.server.remote.RemoteControlPayloads73;
import com.fixmer.mared.technology.server.setup.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native-game coordinator. A positive click authorizes preparation, but only signed COMMIT permits exit. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class RemoteFlow73 {
 private static com.fixmer.genesis.technology.server.remote.RemoteClient69.Download pending;private static byte[] pendingControl;private static boolean completed;private static byte[] raw;private static Map<String,String> cfg;private static Object connection;private static boolean authorized,launched,armed,handedOff;private static volatile String phase="IDLE";private static String localFailure="";private static final AtomicBoolean downloading=new AtomicBoolean();private static AtomicBoolean cancelled=new AtomicBoolean();
 static{RemoteControlPayloads73.clientSink=RemoteFlow73::receive;}
 private RemoteFlow73(){}
 private static void receive(byte[] bytes){try{Map<String,String> next=new HashMap<>(ControlWire73.decode(bytes));String stage=next.remove("stage");Minecraft mc=Minecraft.getInstance();if(!"73".equals(next.get("schema"))||!next.get("account").equals((mc.player==null?mc.getUser().getProfileId():mc.player.getUUID()).toString()))return;
  if(cfg!=null&&connection==mc.getConnection()&&cfg.get("round").equals(next.get("round"))&&cfg.get("ticket").equals(next.get("ticket")))return;
  if(downloading.get()||launched&&RemoteApplyService70.busy())return;raw=ControlWire73.encode(next);cfg=Map.copyOf(next);completed=Set.of("INSTALLED","JOINED").contains(stage==null?"":stage);connection=mc.getConnection();authorized=launched=armed=handedOff=false;phase="IDLE";localFailure="";cancelled=new AtomicBoolean();if(matches(RemoteConsentClient72.current73())&&RemoteConsentClient72.authorized125())authorized=true;
 }catch(Exception ignored){}}
 public static boolean matches(Frame f){return f!=null&&cfg!=null&&connection==Minecraft.getInstance().getConnection()&&cfg.get("round").equals(f.round().toString())&&cfg.get("ticket").equals(f.ticket().toString())&&cfg.get("release").equals(f.release().toString())&&cfg.get("archive").equals(f.sha())&&cfg.get("game").equals(f.game())&&cfg.get("endpoint").equals(f.endpoint())&&cfg.get("signer").equals(f.signer());}
 public static String failureReason(){
  if(!phase.equals("FAILED"))return "";
  if(!localFailure.isBlank())return localFailure;
  if(!handedOff)return "REMOTE73_PREPARATION_FAILED";
  var report=RemoteApplyService70.state();
  var reason=report.get("failureReason");
  return reason!=null&&reason.isJsonPrimitive()&&!reason.getAsString().isBlank()?reason.getAsString():"REMOTE73_PREPARATION_FAILED";
 }
 private static String downloadFailure166(Exception failure){
  // Only fixed categories enter the UI; exception messages can contain credentials or URLs.
  if(failure instanceof java.net.ConnectException)return "REMOTE73_DOWNLOAD_CONNECTION_REFUSED";
  if(failure instanceof java.net.SocketTimeoutException)return "REMOTE73_DOWNLOAD_TIMEOUT";
  if(failure instanceof javax.net.ssl.SSLException)return "REMOTE73_DOWNLOAD_TLS_FAILED";
  return "REMOTE73_DOWNLOAD_FAILED";
 }
 public static String phase(){return phase;}public static boolean canPrepare(){return !downloading.get()&&!RemoteApplyService70.busy()&&Set.of("IDLE","FAILED","CANCELLED").contains(phase);}
 public static boolean completed(Frame f){if(!completed||!matches(f)||!com.fixmer.mared.technology.server.firstlaunch.RemoteClient70.ready73())return false;try{String root=System.getenv("GENESIS_REMOTE70_ROOT");if(root==null)return false;var local=ControlWire73.decode(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(root).resolve("control73.cfg")));return local.get("round").equals(f.round().toString())&&local.get("archive").equals(f.sha());}catch(Exception bad){return false;}}
 public static void authorize(Frame f){if(matches(f)&&!completed(f)){authorized=true;if(Set.of("FAILED","CANCELLED").contains(phase)){launched=handedOff=armed=false;phase="IDLE";localFailure="";cancelled.set(false);}}}
 public static void cancel(){RemoteConsentClient72.clearIntent125();if(!cancelled.getAndSet(true)&&cfg!=null){Map<String,String> cap=cfg;Thread abort=new Thread(()->{try{ControlHttp73.post(cap,"ABORT","0".repeat(64),UUID.randomUUID());}catch(Exception ignored){}},"genesis-flow73-abort");abort.setDaemon(true);abort.start();}pending=null;authorized=false;if(launched&&!armed)RemoteApplyService70.command("CANCEL");phase="CANCELLED";}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  Minecraft mc=Minecraft.getInstance();Frame frame=RemoteConsentClient72.current73();if(connection!=null&&connection!=mc.getConnection()){if(!armed)cancel();connection=null;return;}if(!matches(frame))return;
  if(frame.phase().equals("CANCELLED")){if(!armed)cancel();return;}
  if(phase.equals("IDLE")&&RemoteConsentClient72.authorized125())authorized=true;
  if(authorized&&frame.decision().equals("ACCEPTED")&&!launched&&!phase.equals("FAILED")&&!cancelled.get()&&downloading.compareAndSet(false,true)){
   phase="DOWNLOADING";localFailure="";launched=true;byte[] config=raw.clone();Map<String,String> data=cfg;Object expected=connection;AtomicBoolean token=cancelled;String neo=ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString();
   Thread task=new Thread(()->{try{RemoteClient69 client=new RemoteClient69(RemoteService69.root(),"1.21.1",neo);var p=client.inspect(data.get("game"),data.get("endpoint"),token::get);if(!p.trusted()||!p.offer().archiveSha().equals(data.get("archive"))||!p.offer().release().toString().equals(data.get("release")))throw new IllegalStateException();Recovery76.save(RemoteService69.root(),config,p,UUID.fromString(data.get("account")));ServerDirectory76.save(RemoteService69.root(),data.get("game"),data.get("endpoint"),data.get("authMode"));var d=client.download(p,token::get,n->{});
    mc.execute(()->{if(connection!=expected||cfg!=data||token.get())return;phase="PREPARING";pending=d;pendingControl=config;});
   }catch(Exception bad){
    Mared.LOGGER.error("MaRed: client update download or preparation failed at {}",data.get("endpoint"),bad);
    mc.execute(()->{if(connection==expected&&cfg==data){phase=token.get()?"CANCELLED":"FAILED";localFailure=downloadFailure166(bad);}});
   }finally{downloading.set(false);}},"genesis-flow73-download");task.setDaemon(true);task.start();
  }
  if(pending!=null){
   if(ManagedRemote71.available()&&ManagedRemote71.phase().equals("OWNER_UNAVAILABLE")){
    phase="FAILED";localFailure="REMOTE73_OWNER_UNAVAILABLE";pending=null;
   }else if(!RemoteApplyService70.busy()){
    String mode=cfg.get("authMode");
    RemoteApplyService70.startControlled73(mode,pending,pendingControl);
    handedOff=true;pending=null;
   }
  }
  if(handedOff&&Set.of("PREPARING","VALIDATING","WAITING_SERVER","READY","ARMING","ARMED").contains(phase)){
   String current=RemoteApplyService70.phase();
   if(current.equals("READY")&&RemoteApplyService70.busy()){
    phase="ARMING";
    if(!armed){armed=true;RemoteApplyService70.command("ARM");}
   }
   else if(current.equals("CANCELLED"))phase=current;
   else if(Set.of("FAILED","OWNER_UNAVAILABLE","OWNER_BUSY","MANAGED","WINDOWS","NO_CANDIDATE").contains(current)){phase="FAILED";}
   else if(RemoteApplyService70.busy())phase=current;else if(pending==null)phase="FAILED";
  }
 }
}
