package com.fixmer.mared.technology.server.remote;
import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.genesis.technology.permissions.MaopPolicy113;import com.fixmer.mared.technology.permissions.MaopServer113;import com.fixmer.mared.Mared;
import net.minecraft.server.MinecraftServer;import net.minecraft.server.level.ServerPlayer;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.event.server.*;import net.neoforged.neoforge.event.entity.player.PlayerEvent;import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
/** Real session -> explicit Maop policy -> signed owner queue -> fresh controller policy check. OP is not authority. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class ServerAdmin75 {
 private static final Map<MinecraftServer,Actor> actors=new IdentityHashMap<>();
 private static final class Actor{final MaopPolicy113 policy;final ExecutorService io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"genesis-admin113");t.setDaemon(true);return t;});final Map<UUID,Long> limits=new HashMap<>();boolean busy;Actor(MaopPolicy113 policy){this.policy=policy;}}
 private ServerAdmin75(){}
 @SubscribeEvent public static void started(ServerStartedEvent e){if("75".equals(System.getenv("GENESIS_DEDICATED75_VERSION")))actors.put(e.getServer(),new Actor(MaopServer113.initialize(e.getServer())));}
 private static boolean current(ServerPlayer p,MinecraftServer s){return p.getServer()==s&&s.getPlayerList().getPlayer(p.getUUID())==p;}
 private static void reply(ServerPlayer p,UUID id,Map<String,String> d){try{PacketDistributor.sendToPlayer(p,new ServerAdminPayloads75.View(id,ControlWire73.encode(d)));}catch(Exception bad){Mared.LOGGER.warn("[Maop113] Admin reply refused",bad);}}
 private static void reply88(ServerPlayer p,UUID id,Map<String,String> d){try{PacketDistributor.sendToPlayer(p,new ServerAccessPayloads88.View(id,ControlWire73.encode(d)));}catch(Exception bad){Mared.LOGGER.warn("[Maop113] Access reply refused",bad);}}
 private static String code(Exception e){String s=e.getMessage();return s!=null&&s.matches("ACCESS88_[A-Z_]{1,40}")?s:"REFUSED";}
 private static Actor begin(ServerPlayer p,MinecraftServer s){Actor a=actors.get(s);if(a==null||!current(p,s))return null;long now=System.nanoTime(),last=a.limits.getOrDefault(p.getUUID(),0L);if(now-last<500_000_000L||a.limits.size()>=256&&!a.limits.containsKey(p.getUUID()))return null;a.limits.put(p.getUUID(),now);return a;}
 public static void receive(ServerPlayer p,ServerAdminPayloads75.Request q){var s=p.getServer();if(s!=null)s.execute(()->receive0(p,s,q,null,false));}
 public static void receive153(ServerPlayer player,Map<String,String> request,java.util.function.Consumer<Map<String,String>> done){try{MaopServer113.verifyRequest153(player,request);receive0(player,player.getServer(),new ServerAdminPayloads75.Request(UUID.fromString(request.get("request")),request.get("action"),request.get("label")),done,true);}catch(Exception invalid){done.accept(Map.of("result","DENIED"));}}
 private static void answer153(ServerPlayer player,UUID id,Map<String,String> result,java.util.function.Consumer<Map<String,String>> done){if(done==null)reply(player,id,result);else done.accept(result);}
 private static void receive0(ServerPlayer p,MinecraftServer s,ServerAdminPayloads75.Request q,java.util.function.Consumer<Map<String,String>> done,boolean signed153){
  if(!current(p,s)||!p.connection.hasChannel(ServerAdminPayloads75.View.TYPE.id()))return;
  if(!actors.containsKey(s)){answer153(p,q.request(),Map.of("result","NOT_MANAGED"),done);return;}Actor a=begin(p,s);if(a==null||a.busy){answer153(p,q.request(),Map.of("result","BUSY"),done);return;}
  if(q.request().equals(new UUID(0,0))||!ServerAccess88.ROOT_ACTIONS.contains(q.action())||!q.label().matches("[A-Za-z0-9_-]{1,40}")){answer153(p,q.request(),Map.of("result","INVALID"),done);return;}
  if((!s.usesAuthentication()&&!signed153)||!MaopServer113.authenticated153(p)||!MaopServer113.remoteReady(s)||!MaopServer113.allowed(p,q.action())||a.policy==null){answer153(p,q.request(),Map.of("result","DENIED"),done);return;}
  String deviceProof153=MaopServer113.fingerprint153(p);UUID actor=p.getUUID();long accepted=System.currentTimeMillis();a.busy=true;
  a.io.execute(()->{Map<String,String> answer;try{
   if(!a.policy.state().allows(actor,q.action()))throw new java.io.IOException("ACCESS88_DENIED");
   Path path=Path.of(System.getenv("GENESIS_DEDICATED74_ROOT"));var admin=DedicatedAdmin75.open(path);var d=new TreeMap<>(admin.view());d.put("restart103",Boolean.toString(admin.supportsRestart103()));
   Path testReport=path.resolve("logs/update115.json");if(Files.isRegularFile(testReport)&&Files.size(testReport)<=32768){try{var report=com.google.gson.JsonParser.parseString(Files.readString(testReport)).getAsJsonObject();for(String field:List.of("status","request","target","sha256","error"))if(report.has(field))d.put("test152_"+field,report.get(field).getAsString());}catch(Exception ignored){}}
d.put("inplace104",Boolean.toString(admin.isInPlace104()));d.put("test152",Boolean.toString(admin.isInPlace104()&&Files.isRegularFile(path.resolve("owner152"),LinkOption.NOFOLLOW_LINKS)));var session=DedicatedSession74.open(System.getenv());if(session==null||!d.get("generation").equals(Integer.toString(session.generation)))throw new IllegalStateException();
   // root=true here is a private bridge capability, not vanilla OP. The113 controller rechecks the bound Maop file.
   var access=ServerAccess88.open(path);var rights=access.access(actor,true,true);d.put("access88",String.join(",",new TreeSet<>(MaopPolicy113.RIGHTS.get(a.policy.state().subjects().get(actor)))));d.put("role88",a.policy.state().subjects().get(actor).name());
   if(!q.action().equals("PING"))access.queue153(actor,true,true,accepted,q.request(),q.action(),q.label(),deviceProof153);d.put("result",q.action().equals("PING")?"STATE":"QUEUED");answer=d;
  }catch(Exception bad){answer=Map.of("result",code(bad));}Map<String,String> result=answer;s.execute(()->{if(actors.get(s)!=a)return;a.busy=false;if(current(p,s)&&MaopServer113.authenticated153(p)&&MaopServer113.allowed(p,q.action()))answer153(p,q.request(),result,done);else if(current(p,s))answer153(p,q.request(),Map.of("result","DENIED"),done);});});
 }
 public static void access88(ServerPlayer p,ServerAccessPayloads88.Request q){var s=p.getServer();if(s!=null)s.execute(()->access0(p,s,q));}
 private static void access0(ServerPlayer p,MinecraftServer s,ServerAccessPayloads88.Request q){
  if(!current(p,s)||!p.connection.hasChannel(ServerAccessPayloads88.View.TYPE.id()))return;
  if(!actors.containsKey(s)){reply88(p,q.request(),Map.of("result","NOT_MANAGED"));return;}Actor a=begin(p,s);if(a==null||a.busy){reply88(p,q.request(),Map.of("result","BUSY"));return;}
  if(!q.operation().equals("VIEW")||!MaopServer113.remoteReady(s)||!MaopServer113.authenticated153(p)||!MaopServer113.allowed(p,"PING")||a.policy==null){reply88(p,q.request(),Map.of("result","ACCESS88_DENIED"));return;}
  UUID actor=p.getUUID();a.busy=true;a.io.execute(()->{Map<String,String> answer;try{if(!a.policy.state().allows(actor,"PING"))throw new java.io.IOException("ACCESS88_DENIED");Path path=Path.of(System.getenv("GENESIS_DEDICATED74_ROOT"));var d=new TreeMap<>(ServerAccess88.open(path).view(actor,true,true));var policy=a.policy.state();var role=policy.subjects().get(actor);if(role==null)throw new java.io.IOException("ACCESS88_DENIED");d.put("role",role.name());d.put("manage","false");d.put("subjects","");d.put("actions",String.join(",",new TreeSet<>(MaopPolicy113.RIGHTS.get(role))));d.put("result","STATE");answer=d;}catch(Exception bad){answer=Map.of("result",code(bad));}Map<String,String> result=answer;s.execute(()->{if(actors.get(s)!=a)return;a.busy=false;if(current(p,s)&&MaopServer113.authenticated153(p)&&MaopServer113.allowed(p,"PING"))reply88(p,q.request(),result);else if(current(p,s))reply88(p,q.request(),Map.of("result","ACCESS88_DENIED"));});});
 }
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){Actor a=actors.get(p.getServer());if(a!=null)a.limits.remove(p.getUUID());}}
 @SubscribeEvent public static void stopped(ServerStoppedEvent e){Actor a=actors.remove(e.getServer());if(a!=null)a.io.shutdownNow();}
}
