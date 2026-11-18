package com.fixmer.mared.technology.server.remote;
import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.genesis.technology.server.remote.RemoteConsent72.*;
import com.fixmer.mared.Mared;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Signed control publisher. No server STOP, world copying, port switching or claim of automatic server readiness. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class RemoteControlServer73 {
 private static final Map<MinecraftServer,Actor> actors=new IdentityHashMap<>();
 private static final class Actor {ControlRoom73 room;boolean busy,failed;final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"genesis-control73-publisher");t.setDaemon(true);return t;});final Map<UUID,UUID> announced=new HashMap<>();long ticks;}
 private RemoteControlServer73(){}private static Path base(MinecraftServer s){return s.getServerDirectory().resolve("config/mared/control73");}private static Path identity(MinecraftServer s){return s.getServerDirectory().resolve("config/mared/publisher73");}
 public static void sync(MinecraftServer s,RemoteConsent72 round){Actor a=actors.get(s);if(a!=null&&a.room!=null&&a.room.round.equals(round.round()))round.decisions().forEach((id,d)->a.room.decision(id,d));}
 public static boolean enabled(MinecraftServer s){return Files.isRegularFile(identity(s).resolve("private.der"));}
 public static boolean blocksNew(MinecraftServer s){Actor a=actors.get(s);return a!=null&&(a.failed||a.room!=null&&!Set.of("CANCELLED","CLOSED").contains(a.room.phase())||Files.exists(base(s).resolve("active73"))&&a.room==null);}
 public static boolean canCancel(MinecraftServer s){Actor a=actors.get(s);return a==null||a.room==null||Set.of("PREPARING","CANCELLED","CLOSED").contains(a.room.phase());}
 public static void cancel(MinecraftServer s)throws Exception{Actor a=actors.get(s);if(a!=null&&a.room!=null&&!Set.of("CANCELLED","CLOSED").contains(a.room.phase())){a.room.cancel();}}
 public static void create(MinecraftServer s,RemoteConsent72 round,Collection<Session> cohort)throws Exception{
  if(!enabled(s))return;if(cohort.isEmpty())throw new IllegalStateException("CONTROL73_EMPTY");Actor a=actors.get(s);if(a==null||a.failed||a.busy)throw new IllegalStateException("CONTROL73_BUSY");if(a.room!=null&&a.room.phase().equals("CANCELLED")){a.room.closeRound();a.room=null;}
  if(Files.exists(base(s).resolve("active73")))throw new IllegalStateException("CONTROL73_RECOVERY_REQUIRED");a.room=ControlRoom73.create(base(s),identity(s),round.offer(),round.frame(cohort.iterator().next(),System.nanoTime()/1000000).instance(),round.round(),cohort.stream().map(Session::player).toList());a.announced.clear();
 }
 public static void announce(ServerPlayer p,Frame f){
  Actor a=actors.get(p.getServer());if(a==null||a.failed||a.room==null||!a.room.round.equals(f.round())||!a.room.instance.equals(f.instance()))return;
  try{var m=a.room.member(p.getUUID(),false);a.room.decision(p.getUUID(),f.decision());{Map<String,String> config=new HashMap<>(a.room.config(m,f.ticket()));config.put("stage",m.state);config.put("authMode",p.getServer().usesAuthentication()?"ONLINE":"LOCAL_OFFLINE");PacketDistributor.sendToPlayer(p,new RemoteControlPayloads73.Config(ControlWire73.encode(config)));a.announced.put(p.getUUID(),f.ticket());}}catch(Exception bad){a.failed=true;}
 }
 @SubscribeEvent public static void started(ServerStartedEvent e){actors.put(e.getServer(),new Actor());}
 private static boolean allowed(CommandSourceStack s){return s.hasPermission(4)&&(s.getServer().usesAuthentication()||s.getEntity()==null);}
 private static void tell(CommandSourceStack s,String key,Object...args){s.sendSuccess(()->Component.translatable("mared.remote73."+key,args),false);}
 @SubscribeEvent public static void commands(RegisterCommandsEvent e){
  e.getDispatcher().register(Commands.literal("genesiscontrol").requires(RemoteControlServer73::allowed)
   .then(Commands.literal("status").executes(c->{Actor a=actors.get(c.getSource().getServer());if(a==null||a.room==null){tell(c.getSource(),"no_round");return 0;}tell(c.getSource(),"stats",a.room.phase(),a.room.denominator(),a.room.installed(),a.room.needed(),a.room.admission());for(var m:a.room.members())tell(c.getSource(),"member",m.account,m.decision,m.state,m.counted);return 1;}))
   .then(Commands.literal("commit").executes(c->action(c.getSource(),"commit")))
   .then(Commands.literal("admit-current-server").executes(c->action(c.getSource(),"admit")))
   .then(Commands.literal("resume").executes(c->action(c.getSource(),"resume")))
   .then(Commands.literal("close-round").then(Commands.literal("confirm").executes(c->action(c.getSource(),"close")))));
 }
 private static int action(CommandSourceStack source,String op){MinecraftServer server=source.getServer();Actor actor=actors.get(server);if(actor==null||actor.busy){tell(source,"busy");return 0;}
  if(!op.equals("resume")&&(actor.room==null||actor.failed)||op.equals("resume")&&actor.room!=null){tell(source,"refused",op);return 0;}
  actor.busy=true;tell(source,"queued",op);ControlRoom73 current=actor.room;
  actor.io.execute(()->{ControlRoom73 result=current;boolean success=false;try{
   if(op.equals("resume"))result=ControlRoom73.restore(base(server),identity(server));
   else{current.drain();switch(op){case "commit"->current.commit();case "admit"->current.admit();case "close"->current.closeRound();default->throw new IllegalArgumentException();}}
   success=true;
  }catch(Exception rejected){}ControlRoom73 ready=result;boolean okay=success;
  server.execute(()->{if(actors.get(server)!=actor)return;actor.busy=false;if(okay){actor.room=ready;if(op.equals("resume"))actor.failed=false;if(op.equals("close"))RemoteConsentServer72.closeControl(server);tell(source,"done",op);}else tell(source,"refused",op);});});return 1;
 }
 @SubscribeEvent public static void tick(ServerTickEvent.Post e){Actor a=actors.get(e.getServer());if(a==null||a.room==null||a.failed||a.busy||++a.ticks%20!=0)return;a.busy=true;ControlRoom73 room=a.room;
  a.io.execute(()->{boolean okay=true;try{room.drain();room.publish(System.currentTimeMillis());}catch(Exception bad){okay=false;}final boolean valid=okay;e.getServer().execute(()->{if(actors.get(e.getServer())==a){a.busy=false;if(!valid){a.failed=true;Mared.LOGGER.warn("[Genesis control73] Publisher suspended; no new restart/transfer authority");}}});});
 }
 @SubscribeEvent public static void stopping(ServerStoppingEvent e){dispose(e.getServer());}@SubscribeEvent public static void stopped(ServerStoppedEvent e){dispose(e.getServer());}
 private static void dispose(MinecraftServer s){Actor a=actors.remove(s);if(a!=null){a.io.shutdownNow();/* Signed journal survives; fresh-state heartbeat stops. No automatic rollback. */}}
}
