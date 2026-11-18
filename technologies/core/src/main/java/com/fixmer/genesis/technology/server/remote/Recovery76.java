package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Private account/server capability cache. Knowing a UUID never creates a capability. */
public final class Recovery76 {
 private Recovery76(){}
 private static Path path(Path root,String game,String endpoint,UUID account)throws IOException{Path dir=FirstLaunchFiles.safe(root.resolve("recovery76"));Files.createDirectories(dir);FirstLaunchFiles.privateMode(dir);return dir.resolve(FirstLaunchFiles.sha((RemoteOffer69.gameAddress(game)+"\n"+RemoteOffer69.base(endpoint)+"\n"+account).getBytes(StandardCharsets.UTF_8))+".cfg");}
 public static void save(Path root,byte[] raw,RemoteClient69.Preview p,UUID account)throws Exception{
  var d=ControlWire73.decode(raw);if(!d.getOrDefault("account","").equals(account.toString()))throw new IOException("RECOVERY76_ACCOUNT");for(String k:List.of("game","endpoint","release","archive","distribution","signer")){String expected=switch(k){case "game"->p.offer().game();case "endpoint"->p.offer().endpoint();case "release"->p.offer().release().toString();case "archive"->p.offer().archiveSha();case "distribution"->p.offer().distribution().toString();default->p.offer().signer();};if(!expected.equals(d.get(k)))throw new IOException("RECOVERY76_BINDING");}if(!p.trusted())throw new IOException("REMOTE_TRUST_REQUIRED");Path file=path(root,d.get("game"),d.get("endpoint"),account);Path temp=Files.createTempFile(file.getParent(),".recovery76-",".tmp");try{FirstLaunchFiles.privateMode(temp);FirstLaunchFiles.write(temp,raw,true);ControlHttp73.config(temp);Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
 }
 public static byte[] find(Path root,Bootstrap76.State state,UUID account)throws Exception{
  Path file=path(root,state.offer().game(),state.offer().endpoint(),account);if(!Files.exists(file))return null;var d=ControlHttp73.config(file);if(!d.get("account").equals(account.toString()))throw new IOException("RECOVERY76_ACCOUNT");for(String k:List.of("game","endpoint","release","archive","distribution","signer"))if(!d.get(k).equals(state.values().get(k)))return null;
  Map<String,String> live;try{live=ControlHttp73.status(d,state.key());}catch(Exception unavailable){if(state.phase().equals("READY"))return null;throw new IOException("RECOVERY76_UNAVAILABLE");}
  if(Set.of("CANCELLED","CLOSED").contains(live.get("phase")))return null;if(!live.get("phase").equals("COMMITTED"))throw new IOException("RECOVERY76_NOT_COMMITTED");String[] member=Arrays.stream(live.get("clients").split(";")).map(s->s.split(",",-1)).filter(v->v[0].equals(d.get("client"))).findFirst().orElseThrow();if(member[1].equals("ABORTED"))throw new IOException("RECOVERY76_ABORTED");return FirstLaunchFiles.read(file,16384);
 }
}
