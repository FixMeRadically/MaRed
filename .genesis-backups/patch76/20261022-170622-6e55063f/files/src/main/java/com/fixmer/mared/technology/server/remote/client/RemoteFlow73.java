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
 private static com.fixmer.genesis.technology.server.remote.RemoteClient69.Download pending;private static byte[] pendingControl;private static boolean completed;private static byte[] raw;private static Map<String,String> cfg;private static Object connection;private static boolean authorized,launched,armed;private static volatile String phase="IDLE";private static final AtomicBoolean downloading=new AtomicBoolean();private static AtomicBoolean cancelled=new AtomicBoolean();
 static{RemoteControlPayloads73.clientSink=RemoteFlow73::receive;}
 private RemoteFlow73(){}
 private static void receive(byte[] bytes){try{Map<String,String> next=new HashMap<>(ControlWire73.decode(bytes));String stage=next.remove("stage");Minecraft mc=Minecraft.getInstance();if(!"73".equals(next.get("schema"))||!next.get("account").equals((mc.player==null?mc.getUser().getProfileId():mc.player.getUUID()).toString()))return;
  if(cfg!=null&&cfg.get("round").equals(next.get("round"))&&cfg.get("ticket").equals(next.get("ticket")))return;
  if(downloading.get()||launched&&RemoteApplyService70.busy())return;raw=ControlWire73.encode(next);cfg=Map.copyOf(next);completed=Set.of("INSTALLED","JOINED").contains(stage==null?"":stage);connection=mc.getConnection();authorized=launched=armed=false;phase="IDLE";cancelled=new AtomicBoolean();
 }catch(Exception ignored){}}
 public static boolean matches(Frame f){return f!=null&&cfg!=null&&connection==Minecraft.getInstance().getConnection()&&cfg.get("round").equals(f.round().toString())&&cfg.get("ticket").equals(f.ticket().toString())&&cfg.get("release").equals(f.release().toString())&&cfg.get("archive").equals(f.sha())&&cfg.get("game").equals(f.game())&&cfg.get("endpoint").equals(f.endpoint())&&cfg.get("signer").equals(f.signer());}
 public static String phase(){return phase;}public static boolean canPrepare(){return !downloading.get()&&!RemoteApplyService70.busy()&&Set.of("IDLE","FAILED","CANCELLED").contains(phase);}
 public static boolean completed(Frame f){if(!completed||!matches(f)||!com.fixmer.mared.technology.server.firstlaunch.RemoteClient70.ready73())return false;try{String root=System.getenv("GENESIS_REMOTE70_ROOT");if(root==null)return false;var local=ControlWire73.decode(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(root).resolve("control73.cfg")));return local.get("round").equals(f.round().toString())&&local.get("archive").equals(f.sha());}catch(Exception bad){return false;}}
 public static void authorize(Frame f){if(matches(f)&&!completed(f)){authorized=true;if(Set.of("FAILED","CANCELLED").contains(phase)){launched=false;phase="IDLE";cancelled.set(false);}}}
 public static void cancel(){if(!cancelled.getAndSet(true)&&cfg!=null){Map<String,String> cap=cfg;Thread abort=new Thread(()->{try{ControlHttp73.post(cap,"ABORT","0".repeat(64),UUID.randomUUID());}catch(Exception ignored){}},"genesis-flow73-abort");abort.setDaemon(true);abort.start();}pending=null;authorized=false;if(launched&&!armed)RemoteApplyService70.command("CANCEL");phase="CANCELLED";}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  Minecraft mc=Minecraft.getInstance();Frame frame=RemoteConsentClient72.current73();if(connection!=null&&connection!=mc.getConnection()){if(!armed)cancel();return;}if(!matches(frame))return;
  if(frame.phase().equals("CANCELLED")){if(!armed)cancel();return;}
  if(authorized&&frame.decision().equals("ACCEPTED")&&!launched&&!phase.equals("FAILED")&&!cancelled.get()&&downloading.compareAndSet(false,true)){
   phase="DOWNLOADING";launched=true;byte[] config=raw.clone();Map<String,String> data=cfg;Object expected=connection;AtomicBoolean token=cancelled;String neo=ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString();
   Thread task=new Thread(()->{try{RemoteClient69 client=new RemoteClient69(RemoteService69.root(),"1.21.1",neo);var p=client.inspect(data.get("game"),data.get("endpoint"),token::get);if(!p.trusted()||!p.offer().archiveSha().equals(data.get("archive"))||!p.offer().release().toString().equals(data.get("release")))throw new IllegalStateException();var d=client.download(p,token::get,n->{});
    mc.execute(()->{if(connection!=expected||cfg!=data||token.get())return;phase="PREPARING";pending=d;pendingControl=config;});
   }catch(Exception bad){phase=cancelled.get()?"CANCELLED":"FAILED";}finally{downloading.set(false);}},"genesis-flow73-download");task.setDaemon(true);task.start();
  }
  if(pending!=null&&!RemoteApplyService70.busy()){
   if(ManagedRemote71.available()&&ManagedRemote71.phase().equals("OWNER_UNAVAILABLE")){phase="FAILED";pending=null;}
   else{String mode=cfg.get("authMode");RemoteApplyService70.startControlled73(mode,pending,pendingControl);pending=null;}
  }
  if(launched&&!downloading.get()&&Set.of("PREPARING","WAITING_SERVER","READY","ARMING").contains(phase)){
   String current=RemoteApplyService70.phase();if(current.equals("READY")&&RemoteApplyService70.busy()&&!armed){armed=true;phase="ARMING";RemoteApplyService70.command("ARM");}
   else if(Set.of("FAILED","CANCELLED","OWNER_UNAVAILABLE","OWNER_BUSY","MANAGED","WINDOWS").contains(current)){phase="FAILED";}
   else if(RemoteApplyService70.busy())phase=current;
  }
 }
}
