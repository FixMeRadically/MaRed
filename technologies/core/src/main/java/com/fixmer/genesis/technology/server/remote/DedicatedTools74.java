package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.nio.file.*;import java.util.*;
/** Owner tools: no process adoption, world copying or remote shell execution. Output contains no secrets. */
public final class DedicatedTools74 {
 static String json(Object value){if(value instanceof Map<?,?> m)return "{"+String.join(",",m.entrySet().stream().map(e->json(e.getKey())+":"+json(e.getValue())).toList())+"}";if(value instanceof Collection<?> c)return "["+String.join(",",c.stream().map(DedicatedTools74::json).toList())+"]";if(value instanceof Boolean||value instanceof Number)return value.toString();return "\""+value.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
 public static void main(String[] a){try{Object out;
  if(a.length==5&&a[0].equals("author")){DedicatedRelease74.author(Path.of(a[1]),Path.of(a[2]),Path.of(a[3]),Path.of(a[4]));out=Map.of("authored",true);}
  else if(a.length==3&&Set.of("verify","verify-history").contains(a[0])){byte[] trusted=FirstLaunchFiles.read(Path.of(a[2]),256);var v=a[0].equals("verify")?DedicatedRelease74.verify(Path.of(a[1]),trusted):DedicatedRelease74.verifyHistorical(Path.of(a[1]),trusted);var o=v.offer();var details=new TreeMap<String,Object>();details.putAll(Map.of("release",o.release().toString(),"client",o.archiveSha(),"server",v.archive().archiveSha256(),"minecraft",o.minecraft(),"neoforge",o.neoforge(),"game",o.game(),"endpoint",o.endpoint(),"files",v.archive().manifest().files().stream().map(e->Map.of("path",e.path(),"sha256",e.sha256(),"bytes",e.bytes())).toList()));details.put("parent",o.parent()==null?"none":o.parent().toString());details.put("sequence",o.sequence());details.put("expires",o.expires());details.put("distribution",o.distribution().toString());details.put("signer",o.signer());out=details;}
  else if(a.length==2&&Set.of("inspect","pulse","admit","close").contains(a[0])){Path root=FirstLaunchFiles.safe(Path.of(a[1]));var room=ControlRoom73.restore(root.resolve("control73"),root.resolve("publisher73"));if(!a[0].equals("inspect")){room.drain();if(a[0].equals("admit"))room.admit();if(a[0].equals("close"))room.closeRound();room.publish(System.currentTimeMillis());}var view=new TreeMap<String,Object>();view.putAll(Map.of("round",room.round.toString(),"release",room.offer.release().toString(),"client",room.offer.archiveSha(),"phase",room.phase(),"installed",room.installed(),"total",room.denominator(),"needed",room.needed(),"admission",room.admission()));view.put("client",room.offer.archiveSha());view.put("serial",room.serial());view.put("waitingInstall",room.members().stream().filter(m->m.counted&&!Set.of("INSTALLED","JOINED").contains(m.state)).limit(8).map(m->Map.of("account",m.account.toString(),"state",m.state)).toList());view.put("waitingJoin",room.members().stream().filter(m->m.counted&&!m.state.equals("JOINED")).limit(8).map(m->Map.of("account",m.account.toString(),"state",m.state)).toList());view.put("joined",room.members().stream().filter(m->m.counted&&m.state.equals("JOINED")).count());view.put("original",room.members().stream().filter(m->m.original).count());view.put("pending",room.members().stream().filter(m->m.original&&m.decision.equals("PENDING")).count());view.put("accepted",room.members().stream().filter(m->m.original&&m.decision.equals("ACCEPTED")).count());view.put("aborted",room.members().stream().filter(m->m.original&&m.decision.equals("ACCEPTED")&&m.state.equals("ABORTED")).count());view.put("prepared",room.members().stream().filter(m->m.original&&m.decision.equals("ACCEPTED")&&m.state.equals("PREPARED")).count());out=view;}
  else throw new IllegalArgumentException();System.out.println(json(out));
 }catch(Exception bad){
  boolean verifying=a.length==3&&Set.of("verify","verify-history").contains(a[0]);
  if(verifying){
   String message=bad.getMessage();
   String code=message!=null&&message.matches("(?:DEDICATED74|REMOTE|CONTROL73|FIRST58|PACKAGE)_[A-Z0-9_]{1,60}")
     ?message:bad instanceof NoSuchFileException?"DEDICATED74_PAIR_FILE_MISSING":"DEDICATED74_PAIR_CHECK_FAILED";
   System.err.println(code);
  }else System.err.println("DEDICATED74_REJECTED; inspect private owner report, no profile activation authorized.");
  System.exit(2);
 }}
}
