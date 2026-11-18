package com.fixmer.mared.technology.server.remote;
import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.mared.Mared;
import net.minecraft.server.MinecraftServer;import net.minecraft.server.level.ServerPlayer;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.event.server.*;import net.neoforged.neoforge.event.entity.player.PlayerEvent;import net.neoforged.neoforge.network.PacketDistributor;
import java.net.InetSocketAddress;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
/** Actual ServerPlayer authorization per request. Only state + fixed label actions leave the server. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class ServerAdmin75 {
 private static final Map<MinecraftServer,Actor> actors=new IdentityHashMap<>();private static final class Actor{final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"genesis-admin75");t.setDaemon(true);return t;});final Map<UUID,Long> limits=new HashMap<>();boolean busy;}
 private ServerAdmin75(){}
 @SubscribeEvent public static void started(ServerStartedEvent e){if("75".equals(System.getenv("GENESIS_DEDICATED75_VERSION")))actors.put(e.getServer(),new Actor());}
 private static boolean current(ServerPlayer p,MinecraftServer s){return p.getServer()==s&&s.getPlayerList().getPlayer(p.getUUID())==p;}
 private static boolean allowed(ServerPlayer p,MinecraftServer s){return DedicatedAdmin75.mayAdminister(p.createCommandSourceStack().hasPermission(4),s.usesAuthentication());}
 private static void reply(ServerPlayer p,UUID request,Map<String,String> data){try{PacketDistributor.sendToPlayer(p,new ServerAdminPayloads75.View(request,ControlWire73.encode(data)));}catch(Exception ignored){}}
 public static void receive(ServerPlayer p,ServerAdminPayloads75.Request request){MinecraftServer s=p.getServer();if(s==null||!current(p,s))return;Actor a=actors.get(s);if(!p.connection.hasChannel(ServerAdminPayloads75.View.TYPE.id()))return;
  if(a==null){reply(p,request.request(),Map.of("result","NOT_MANAGED"));return;}long now=System.nanoTime(),last=a.limits.getOrDefault(p.getUUID(),0L);if(now-last<500_000_000L)return;if(a.limits.size()>=256&&!a.limits.containsKey(p.getUUID()))return;a.limits.put(p.getUUID(),now);
  if(!allowed(p,s)){reply(p,request.request(),Map.of("result","DENIED"));return;}if(a.busy){reply(p,request.request(),Map.of("result","BUSY"));return;}
  if(request.request().equals(new UUID(0,0))||!Set.of("CONTINUE_WAIT","PING","STAGE","OFFER","COMMIT","CANCEL","CLOSE","LOCAL_TEST_ON","LOCAL_TEST_OFF","SHUTDOWN").contains(request.action())||!request.label().matches("[A-Za-z0-9_-]{1,40}")){reply(p,request.request(),Map.of("result","INVALID"));return;}
  a.busy=true;a.io.execute(()->{Map<String,String> response;
   try{var admin=DedicatedAdmin75.open(Path.of(System.getenv("GENESIS_DEDICATED74_ROOT")));var d=new TreeMap<>(admin.view());var session=DedicatedSession74.open(System.getenv());if(session==null||!d.get("generation").equals(Integer.toString(session.generation)))throw new IllegalStateException();if(!request.action().equals("PING"))admin.submit(request.request(),request.action(),request.label());d.put("result",request.action().equals("PING")?"STATE":"QUEUED");response=d;}catch(Exception bad){response=Map.of("result","REFUSED");}
   Map<String,String> value=response;s.execute(()->{if(actors.get(s)!=a)return;a.busy=false;if(current(p,s)&&allowed(p,s))reply(p,request.request(),value);});});
 }
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){Actor a=actors.get(p.getServer());if(a!=null)a.limits.remove(p.getUUID());}}
 @SubscribeEvent public static void stopped(ServerStoppedEvent e){Actor a=actors.remove(e.getServer());if(a!=null)a.io.shutdownNow();}
}
