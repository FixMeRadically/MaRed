package com.fixmer.mared.technology.server.firstlaunch;
import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.mared.Mared;
import com.fixmer.mared.technology.server.remote.RemoteControlServer73;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.util.*;import java.util.concurrent.*;
/** Only an owner-created dedicated74 process. Save happens on the server thread, final receipt after stop. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class DedicatedServer74 {
 private static volatile Actor actor;private static final UUID ZERO=new UUID(0,0);
 private static final class Actor{final MinecraftServer server;final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"genesis-dedicated74");t.setDaemon(true);return t;});volatile DedicatedSession74 session;volatile boolean failed,quiesced,stopping,saved,pending;volatile long seq;volatile UUID request=ZERO;volatile String round="none";Actor(MinecraftServer s){server=s;}}
 private DedicatedServer74(){}
 public static boolean managed(){return System.getenv("GENESIS_DEDICATED74_ROOT")!=null;}
 public static void requirePrepared(RemoteOffer69 offer)throws Exception{if(!managed())return;Actor a=actor;if(a==null||a.failed||a.session==null||a.quiesced||a.stopping)throw new IllegalStateException("DEDICATED74_UNAVAILABLE");a.session.prepared(offer);}
 @SubscribeEvent public static void started(ServerStartedEvent e){if(!managed())return;Actor a=new Actor(e.getServer());actor=a;var loaded=FirstLaunchServer58.loaded();String mc=FirstLaunchServer58.version("minecraft"),neo=FirstLaunchServer58.version("neoforge");
  if(!a.server.isDedicatedServer()||a.server.overworld()==null){fail(a);return;}a.io.execute(()->{try{a.session=DedicatedSession74.open(System.getenv());a.session.verifyRuntime(a.server.getServerDirectory(),loaded,mc,neo,a.server.usesAuthentication(),a.server.getLocalIp(),a.server.getPort());a.session.status("READY",0,ZERO,"none");a.io.scheduleWithFixedDelay(()->poll(a),200,200,TimeUnit.MILLISECONDS);}catch(Exception bad){fail(a);}});
 }
 private static void fail(Actor a){a.failed=true;Mared.LOGGER.warn("[Genesis dedicated74] Control suspended; inspect owner report");try{if(a.session!=null)a.session.status("ERROR",a.seq,a.request,a.round);}catch(Exception ignored){}}
 private static void poll(Actor a){if(actor!=a||a.failed||a.stopping||a.pending)return;try{var d=a.session.command();if(d.isEmpty())return;long seq=Long.parseLong(d.get("sequence"));if(seq<=a.seq)return;a.pending=true;a.server.execute(()->apply(a,d,seq));}catch(Exception bad){fail(a);}}
 private static void ack(Actor a,String phase){a.io.execute(()->{try{a.session.status(phase,a.seq,a.request,a.round);}catch(Exception bad){fail(a);}finally{a.pending=false;}});}
 private static void apply(Actor a,Map<String,String> d,long seq){if(actor!=a||a.failed||a.stopping){a.pending=false;return;}try{
  String action=d.get("action");if(action.equals("QUIESCE")){
   UUID round=UUID.fromString(d.get("round"));if(a.session.generation!=0)throw new IllegalStateException();if(!RemoteControlServer73.delegate74(a.server,round)){a.pending=false;return;}a.seq=seq;a.request=UUID.fromString(d.get("request"));a.round=round.toString();a.server.tickRateManager().setFrozen(true);a.quiesced=true;ack(a,"QUIESCED");return;
  }
  if(action.equals("SAVE_STOP")&&(!a.quiesced||!a.round.equals(d.get("round"))))throw new IllegalStateException("DEDICATED74_NOT_QUIESCED");
  a.seq=seq;a.request=UUID.fromString(d.get("request"));
  if(action.equals("SAVE_STOP")||action.equals("SHUTDOWN")){
   a.round=d.get("round");a.stopping=true;if(!a.server.saveEverything(true,true,true))throw new IllegalStateException("DEDICATED74_SAVE");a.saved=true;a.io.execute(()->{try{a.session.status("SAVED",a.seq,a.request,a.round);a.server.execute(()->{if(actor==a&&!a.failed)a.server.halt(false);});}catch(Exception bad){fail(a);}});return;
  }
  if(a.quiesced||a.session.generation!=0)throw new IllegalStateException("DEDICATED74_DELEGATED");String cmd=switch(action){case "LOCAL_TEST_ON"->"genesisupdate local-test on";case "LOCAL_TEST_OFF"->"genesisupdate local-test off";case "PREPARE"->"genesisupdate prepare "+d.get("label");case "OFFER"->"genesisupdate offer";case "STATUS"->"genesiscontrol status";case "COMMIT"->"genesiscontrol commit";case "CANCEL"->"genesisupdate cancel";case "CLOSE"->"genesiscontrol close-round confirm";default->throw new IllegalStateException();};
  // Closed allowlist above; neither network players nor packet contents can submit arbitrary console text.
  a.server.getCommands().performPrefixedCommand(a.server.createCommandSourceStack(),cmd);ack(a,"COMMAND_ACCEPTED");
 }catch(Exception bad){a.pending=false;fail(a);}}
 @SubscribeEvent public static void tick(ServerTickEvent.Post e){Actor a=actor;if(a!=null&&a.server==e.getServer()&&a.quiesced&&!a.stopping)a.server.tickRateManager().setFrozen(true);}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){Actor a=actor;if(a!=null&&a.quiesced&&a.session!=null&&a.session.generation==0&&e.getEntity() instanceof ServerPlayer p&&p.getServer()==a.server)p.connection.disconnect(Component.translatable("mared.dedicated74.updating"));}
 @SubscribeEvent public static void stopped(ServerStoppedEvent e){Actor a=actor;if(a==null||a.server!=e.getServer())return;actor=null;try{a.io.submit(()->{try{if(a.session!=null)a.session.status(a.saved&&!a.failed?"STOPPED_SAVED":"STOPPED",a.seq,a.request,a.round);}catch(Exception bad){Mared.LOGGER.warn("[Genesis dedicated74] Final save receipt unavailable");}}).get(3,TimeUnit.SECONDS);}catch(Exception ignored){}finally{a.io.shutdownNow();}}
}
