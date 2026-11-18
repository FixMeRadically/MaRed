package com.fixmer.mared.technology.server;
import com.fixmer.genesis.supervisor.GameSessionClient;
import net.minecraft.server.MinecraftServer;
import java.util.*;
/** Common-side events only: no client linkage and no world copy or external halt. */
public final class IntegratedWorld90 {
 private static MinecraftServer current;
 private static final Map<MinecraftServer,World> worlds=new IdentityHashMap<>();
 private static final class World {final String epoch=UUID.randomUUID().toString();String phase="ACTIVE";boolean armed;}
 private IntegratedWorld90(){}
 public static synchronized void started(MinecraftServer s){if(s.isDedicatedServer()||worlds.containsKey(s))return;World w=new World();worlds.put(s,w);current=s;GameSessionClient.integrated90(w.epoch,w.phase);}
 public static synchronized void arm(MinecraftServer s){World w=worlds.get(s);if(w==null||!w.phase.equals("ACTIVE"))throw new IllegalStateException("INTEGRATED90_CHANGED");w.armed=true;}
 public static synchronized void stopping(MinecraftServer s){World w=worlds.get(s);if(w==null)return;
  if(!w.armed&&!GameSessionClient.supervised90())return;
  try{w.phase=s.saveEverything(true,true,true)?"SAVED":"FAILED";}catch(RuntimeException e){w.phase="FAILED";}
  if(current==s)GameSessionClient.integrated90(w.epoch,w.phase);
 }
 public static synchronized void stopped(MinecraftServer s){World w=worlds.remove(s);if(w==null)return;w.phase=w.phase.equals("SAVED")?"STOPPED":"FAILED";if(current==s){GameSessionClient.integrated90(w.epoch,w.phase);current=null;}}
}
