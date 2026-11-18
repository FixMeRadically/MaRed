package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;import java.io.*;import java.nio.file.*;import java.util.*;
/** Private current-client challenge. No caller-supplied path and no authority to change account/mode. */
public final class SessionHandoff89 {
 private SessionHandoff89(){}public static final Set<String> CONTEXT=Set.of("sourceRun","profile","account","mode","scope","pid","birth");
 private static final Set<String> REQUEST=Set.of("schema","request","nonce","sourceRun","profile","account","mode","issued","scope","pid","birth");
 private static IOException bad(String c){return new IOException("AUTH89_"+c);}
 public static Map<String,String> request(Path root,Map<String,String> expected,long now)throws Exception{
  root=FirstLaunchFiles.safe(root);Path p=root.resolve("auth89.request");if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return Map.of();byte[] key=FirstLaunchFiles.read(root.resolve("channel.key"),32);var d=LocalApply67.verify(key,FirstLaunchFiles.read(p,32768));
  if(!expected.keySet().equals(CONTEXT)||!d.keySet().equals(REQUEST)||!"89".equals(d.get("schema"))||CONTEXT.stream().anyMatch(k->!Objects.equals(d.get(k),expected.get(k))))throw bad("BINDING");
  for(String k:List.of("request","nonce","sourceRun"))if(!UUID.fromString(d.get(k)).toString().equals(d.get(k))||UUID.fromString(d.get(k)).equals(new UUID(0,0)))throw bad("BINDING");
  if(!UUID.fromString(d.get("account")).toString().equals(d.get("account"))||d.get("mode").equals("ONLINE")&&UUID.fromString(d.get("account")).equals(new UUID(0,0)))throw bad("BINDING");
  long issued=Long.parseLong(d.get("issued"));if(now<issued||now-issued>10000||!Set.of("ONLINE","LOCAL_OFFLINE").contains(d.get("mode"))||!Set.of("INITIAL","MANAGED").contains(d.get("scope")))throw bad("HANDOFF_TIMEOUT");return d;
 }
 public static void answer(Path root,Map<String,String> request,Map<String,String> user,long now)throws Exception{
  if(!user.keySet().equals(Set.of("account","name","accessToken","userType")))throw bad("SESSION");var d=new TreeMap<>(request);
  if(!user.get("account").equals(request.get("account"))){d.put("error","AUTH89_ACCOUNT_CHANGED");}else if(!user.get("name").matches("[A-Za-z0-9_]{1,16}")||user.get("accessToken").length()>8192||!user.get("accessToken").matches("[!-~]*")||!Set.of("msa","mojang","legacy").contains(user.get("userType"))){d.put("error","AUTH89_SESSION");}else{d.put("name",user.get("name"));d.put("accessToken",user.get("accessToken"));d.put("userType",user.get("userType"));}
  long issued=Long.parseLong(request.get("issued"));if(now<issued||now-issued>10000)throw bad("HANDOFF_TIMEOUT");d.put("answered",Long.toString(now));byte[] key=FirstLaunchFiles.read(root.resolve("channel.key"),32);FirstLaunchFiles.write(FirstLaunchFiles.safe(root.resolve("auth89.response")),LocalApply67.sign(key,d),true);
 }
}
