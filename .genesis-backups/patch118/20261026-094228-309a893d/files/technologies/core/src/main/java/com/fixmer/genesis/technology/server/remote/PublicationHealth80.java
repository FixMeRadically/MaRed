package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;import java.nio.file.*;import java.util.*;

/** Private local diagnostics. No value from this file grants network admission or install authority. */
public final class PublicationHealth80 {
 public static final Set<String> FIELDS=Set.of("schema","run","at","state","code","release","expires");
 public static final Set<String> STATES=Set.of("READY","TRANSITION","UNAVAILABLE","BASELINE_EXPIRED","CLOCK_AHEAD","SIGNER_ERROR","STOPPED");
 private PublicationHealth80(){}
 public static void write(Path root,byte[] key,String run,long now,String state,String code,String release,long expires)throws Exception{
  if(!STATES.contains(state)||!code.matches("NONE|BOOTSTRAP80_[A-Z_]{1,48}")||now<0||expires<0||!release.equals("none")&&!UUID.fromString(release).toString().equals(release))throw new IOException("BOOTSTRAP80_STATUS");
  FirstLaunchFiles.write(root.resolve("bootstrap80.status"),LocalApply67.sign(key,Map.of("schema","80","run",run,"at",Long.toString(now),"state",state,"code",code,"release",release,"expires",Long.toString(expires))),true);
 }
 public static Map<String,String> inspect(Path path,long now)throws Exception{
  Path root=FirstLaunchFiles.safe(path);byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);if(key.length!=32)throw new IOException("BOOTSTRAP80_OWNER");
  var marker=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner76"),16384));if(!marker.keySet().equals(Set.of("schema","run"))||!"76".equals(marker.get("schema")))throw new IOException("BOOTSTRAP80_OWNER");String run=UUID.fromString(marker.get("run")).toString();
  var out=new TreeMap<String,String>();out.put("run",run);out.put("state","NOT_REPORTED");out.put("world","NOT_REPORTED");out.put("generation","0");out.put("code","NONE");out.put("expires","0");out.put("remainingMs","0");out.put("release","none");out.put("installAuthority","false");
  if(Files.exists(root.resolve("view75"))){var view=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("view75"),131072));if(!run.equals(view.get("run"))||!"75".equals(view.get("schema"))||!view.get("generation").matches("0|[1-9][0-9]?")||Integer.parseInt(view.get("generation"))>32)throw new IOException("BOOTSTRAP80_OWNER_VIEW");long age=now-Long.parseLong(view.get("updated"));out.put("world",age>10000||age< -30000?"OWNER_UNAVAILABLE":view.get("phase"));out.put("generation",view.get("generation"));}
  if(Files.exists(root.resolve("bootstrap80.status"))){var d=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("bootstrap80.status"),16384));if(!d.keySet().equals(FIELDS)||!"80".equals(d.get("schema"))||!run.equals(d.get("run"))||!STATES.contains(d.get("state"))||!d.get("code").matches("NONE|BOOTSTRAP80_[A-Z_]{1,48}"))throw new IOException("BOOTSTRAP80_STATUS");
   long at=Long.parseLong(d.get("at")),expires=Long.parseLong(d.get("expires"));String release=d.get("release");if(at<0||expires<0||!release.equals("none")&&!UUID.fromString(release).toString().equals(release))throw new IOException("BOOTSTRAP80_STATUS");
   String state=d.get("state");if(now-at>10000||at-now>30000)state="SIGNER_UNAVAILABLE";else if(Set.of("READY","TRANSITION","UNAVAILABLE").contains(state)&&expires>0&&expires<=now)state="BASELINE_EXPIRED";
   out.put("state",state);out.put("code",d.get("code"));out.put("release",release);out.put("expires",d.get("expires"));out.put("remainingMs",Long.toString(Math.max(0,expires-now)));
  }
  // A fresh signer is not proof that its game owner is still alive.
  if(Set.of("OWNER_UNAVAILABLE","STOPPED_SAVED","STOP_NOT_CONFIRMED","FAILED").contains(out.get("world")))out.put("state","OWNER_UNAVAILABLE");
  return Map.copyOf(out);
 }
}
