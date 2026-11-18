package com.fixmer.mared.technology.server.setup;
import com.fixmer.genesis.technology.server.remote.ControlWire73;import com.fixmer.mared.technology.server.remote.ServerAccessPayloads88;import net.minecraft.client.Minecraft;import net.neoforged.neoforge.network.PacketDistributor;import java.util.*;
/** Main-thread, connection-bound UI adapter. The server is authoritative. */
public final class ServerAccessService88 {
 private static Object connection;private static UUID pending;private static long sent,last;private static Map<String,String> view=Map.of();private static String result="IDLE";
 static{ServerAccessPayloads88.clientSink=p->Minecraft.getInstance().execute(()->receive(p));}private ServerAccessService88(){}
 public static Map<String,String> view(){return view;}public static String result(){return result;}public static boolean busy(){return pending!=null;}
 private static boolean connected(){var c=Minecraft.getInstance().getConnection();if(connection!=c){connection=c;pending=null;view=Map.of();last=System.nanoTime();result="IDLE";}return c!=null&&c.hasChannel(ServerAccessPayloads88.Request.TYPE.id());}
 public static void poll(){if(!connected()){view=Map.of();result="NOT_MANAGED";return;}long now=System.nanoTime();if(pending!=null&&now-sent>8_000_000_000L){pending=null;result="TIMEOUT";}if(pending==null&&now-last>2_000_000_000L)send("VIEW",new UUID(0,0),"NONE",0);}
 public static boolean manage(){return "true".equals(view.get("manage"));}
 public static void change(String target,String role){if(!manage())return;try{String value=target.trim().toLowerCase(Locale.ROOT);UUID id=UUID.fromString(value);if(!id.toString().equals(value))throw new IllegalArgumentException();send("SET",id,role,Long.parseLong(view.get("revision")));}catch(RuntimeException invalid){result="INVALID";}}
 private static void send(String operation,UUID target,String role,long revision){if(!connected()){result="NOT_MANAGED";return;}long now=System.nanoTime();if(pending!=null||now-last<600_000_000L){result="BUSY";return;}pending=UUID.randomUUID();sent=last=now;PacketDistributor.sendToServer(new ServerAccessPayloads88.Request(pending,operation,target,role,revision));}
 private static void receive(ServerAccessPayloads88.View packet){if(!connected()||pending==null||!pending.equals(packet.request()))return;pending=null;try{var d=ControlWire73.decode(packet.bytes());result=d.getOrDefault("result","REFUSED");if(d.containsKey("revision")){Long.parseLong(d.get("revision"));view=Map.copyOf(d);}else if(Set.of("ACCESS88_DENIED","NOT_MANAGED","ACCESS88_NEW_OWNER_REQUIRED").contains(result))view=Map.of();}catch(Exception bad){result="REFUSED";}}
}
