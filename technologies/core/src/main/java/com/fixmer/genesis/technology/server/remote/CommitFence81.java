package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;import java.nio.file.*;import java.util.*;

/** Private immutable COMMIT evidence. Does not grant a new install, change a cohort or start a process. */
public final class CommitFence81 {
 private static final Set<String> FIELDS=Set.of("schema","run","round","source","target","label","release","client","server","oldInventory","newInventory","codeSeal","journalSha","preparedSha","total");
 private CommitFence81(){}
 private static IOException fail(String code){return new IOException("COMMIT81_"+code);}
 private record Owner(byte[] key,byte[] pub,String run){}
 private static Owner owner(Path root)throws Exception{
  byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);if(key.length!=32)throw fail("OWNER");var d=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner75"),16384));if(!d.keySet().equals(Set.of("schema","run"))||!"75".equals(d.get("schema")))throw fail("OWNER");String run=UUID.fromString(d.get("run")).toString();return new Owner(key,FirstLaunchFiles.read(root.resolve("publisher73/public.der"),256),run);
 }
 private static Map<String,String> prepared(Path root,Owner o,String label,int source,int target,byte[] bytes)throws Exception{
  if(!label.matches("[A-Za-z0-9_-]{1,40}")||source<0||target!=source+1||target>(Files.exists(root.resolve("owner104"))?999999999:32))throw fail("ARGUMENTS");
  var d=LocalApply67.verify(o.key,bytes);if(!d.keySet().equals(Set.of("schema","run","generation","oldInventory","newInventory","release","client","server","label","codeSeal"))||!"74".equals(d.get("schema"))||!o.run.equals(d.get("run"))||!Integer.toString(source).equals(d.get("generation"))||!label.equals(d.get("label")))throw fail("PREPARED");
  for(String name:List.of("oldInventory","newInventory","client","server","codeSeal"))if(!d.get(name).matches("[a-f0-9]{64}"))throw fail("PREPARED");UUID.fromString(d.get("release"));var session=LocalApply67.verify(o.key,FirstLaunchFiles.read(FolderRuntime118.channel(root,Integer.toString(source)).resolve("server-"+source+".session"),16384));if(!o.run.equals(session.get("run"))||!Integer.toString(source).equals(session.get("generation"))||!d.get("oldInventory").equals(session.get("inventory")))throw fail("SOURCE_SESSION");return d;
 }
 private static ControlRoom73 room(Path root,Path snapshot,Owner o,UUID round,Map<String,String> p)throws Exception{
  if(!new String(FirstLaunchFiles.read(snapshot.resolve("active73"),64),java.nio.charset.StandardCharsets.US_ASCII).equals(round.toString()))throw fail("ROUND");
  var r=ControlRoom73.restore(snapshot,root.resolve("publisher73"));if(!r.round.equals(round)||!r.phase().equals("COMMITTED")||r.admission()||r.denominator()<1)throw fail("NOT_COMMITTED");
  var pair=DedicatedRelease74.verifyHistorical(root.resolve("releases").resolve(p.get("label")),o.pub);
  if(!Arrays.equals(pair.offer().encode(),r.offer.encode())||!pair.offer().release().toString().equals(p.get("release"))||!pair.offer().archiveSha().equals(p.get("client"))||!pair.archive().archiveSha256().equals(p.get("server")))throw fail("PAIR_BINDING");
  cohort(r);return r;
 }
 static Set<String> cohort(ControlRoom73 r)throws Exception{var result=new TreeSet<String>();for(var m:r.members())if(m.counted){if(!m.original||!m.decision.equals("ACCEPTED")||!Set.of("PREPARED","INSTALLED","JOINED").contains(m.state)||!m.inventory.matches("[a-f0-9]{64}"))throw fail("COHORT");result.add(m.account+":"+m.id+":"+m.inventory);}return result;}
 private static Path directory(Path root,UUID round)throws Exception{return FirstLaunchFiles.safe(root.resolve("commits81").resolve(round.toString()));}
 public static Map<String,String> capture(Path root,UUID round,String label,int source,int target)throws Exception{
  root=FirstLaunchFiles.safe(root);if(round.equals(new UUID(0,0)))throw fail("ARGUMENTS");Owner o=owner(root);Path finalDir=directory(root,round);if(Files.exists(finalDir))return check(root,round,label,source,target);
  byte[] raw=FirstLaunchFiles.read(root.resolve("prepared74"),16384);var p=prepared(root,o,label,source,target,raw);Path control=FirstLaunchFiles.safe(root.resolve("control73"));
  if(!new String(FirstLaunchFiles.read(control.resolve("active73"),64),java.nio.charset.StandardCharsets.US_ASCII).equals(round.toString()))throw fail("ROUND");
  byte[] journal=FirstLaunchFiles.read(control.resolve(round+".journal"),131072);Path parent=FirstLaunchFiles.safe(root.resolve("commits81"));Files.createDirectories(parent);FirstLaunchFiles.privateMode(parent);try(var children=Files.list(parent)){if(children.limit(65).count()>=64)throw fail("LIMIT");}
  Path stage=Files.createTempDirectory(parent,".capture-");FirstLaunchFiles.privateMode(stage);boolean done=false;
  try{
   FirstLaunchFiles.write(stage.resolve("active73"),round.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII),false);FirstLaunchFiles.write(stage.resolve(round+".journal"),journal,false);FirstLaunchFiles.write(stage.resolve("prepared74"),raw,false);
   var r=room(root,stage,o,round,p);var fence=new TreeMap<String,String>();fence.putAll(Map.of("schema","81","run",o.run,"round",round.toString(),"source",Integer.toString(source),"target",Integer.toString(target),"label",label,"total",Integer.toString(r.denominator())));for(String k:List.of("release","client","server","oldInventory","newInventory","codeSeal"))fence.put(k,p.get(k));fence.put("journalSha",FirstLaunchFiles.sha(journal));fence.put("preparedSha",FirstLaunchFiles.sha(raw));
   if(!Arrays.equals(raw,FirstLaunchFiles.read(root.resolve("prepared74"),16384))||!Arrays.equals(FirstLaunchFiles.read(control.resolve("active73"),64),FirstLaunchFiles.read(stage.resolve("active73"),64)))throw fail("CHANGED");
   FirstLaunchFiles.write(stage.resolve("fence"),LocalApply67.sign(o.key,fence),false);Files.move(stage,finalDir);done=true;return Map.copyOf(fence);
  }finally{if(!done)cleanup(stage);}
 }
 public static Map<String,String> check(Path root,UUID round,String label,int source,int target)throws Exception{return checked(root,round,label,source,target,true);}
 public static Map<String,String> historical120(Path root,UUID round,String label,int source,int target)throws Exception{return checked(root,round,label,source,target,false);}
 private static Map<String,String> checked(Path root,UUID round,String label,int source,int target,boolean active)throws Exception{
  root=FirstLaunchFiles.safe(root);if(round.equals(new UUID(0,0)))throw fail("ARGUMENTS");Owner o=owner(root);Path dir=directory(root,round);var f=LocalApply67.verify(o.key,FirstLaunchFiles.read(dir.resolve("fence"),16384));
  if(!f.keySet().equals(FIELDS)||!"81".equals(f.get("schema"))||!o.run.equals(f.get("run"))||!round.toString().equals(f.get("round"))||!label.equals(f.get("label"))||!Integer.toString(source).equals(f.get("source"))||!Integer.toString(target).equals(f.get("target")))throw fail("BINDING");
  if(active&&!Arrays.equals(FirstLaunchFiles.read(root.resolve("control73/active73"),64),FirstLaunchFiles.read(dir.resolve("active73"),64)))throw fail("ROUND");
  byte[] raw=FirstLaunchFiles.read(root.resolve("prepared74"),16384);if(!FirstLaunchFiles.sha(raw).equals(f.get("preparedSha"))||!Arrays.equals(raw,FirstLaunchFiles.read(dir.resolve("prepared74"),16384))||!FirstLaunchFiles.hash(dir.resolve(round+".journal"),131072).equals(f.get("journalSha")))throw fail("CHANGED");
  var p=prepared(root,o,label,source,target,raw);var r=room(root,dir,o,round,p);for(String name:List.of("release","client","server","oldInventory","newInventory","codeSeal"))if(!p.get(name).equals(f.get(name)))throw fail("BINDING");if(!Integer.toString(r.denominator()).equals(f.get("total")))throw fail("COHORT");if(active){var current=ControlRoom73.restore(root.resolve("control73"),root.resolve("publisher73"));if(!current.phase().equals("COMMITTED")||current.admission()||current.serial()<r.serial()||!Arrays.equals(current.offer.encode(),r.offer.encode())||!cohort(current).equals(cohort(r)))throw fail("CURRENT_COMMIT");}return Map.copyOf(f);
 }
 /** Exact admitted COMMIT binding, for an already installed member; not install authority. */
 public static Map<String,String> checkAdmitted(Path root,UUID round,String label,int source,int target)throws Exception{
  var f=checked(root,round,label,source,target,false);Path dir=directory(root,round);
  if(!Arrays.equals(FirstLaunchFiles.read(root.resolve("control73/active73"),64),FirstLaunchFiles.read(dir.resolve("active73"),64)))throw fail("ROUND");
  var captured=ControlRoom73.restore(dir,root.resolve("publisher73"));var current=ControlRoom73.restore(root.resolve("control73"),root.resolve("publisher73"));
  if(!current.phase().equals("COMMITTED")||!current.admission()||current.installed()!=current.denominator()||current.serial()<captured.serial()||!Arrays.equals(current.offer.encode(),captured.offer.encode())||!cohort(current).equals(cohort(captured)))throw fail("ADMISSION_BINDING");return f;
 }
 public static Map<String,String> close(Path root,UUID round,String label,int source,int target)throws Exception{
  root=FirstLaunchFiles.safe(root);var f=checked(root,round,label,source,target,false);Owner o=owner(root);
  var view=DedicatedAdmin75.open(root).view();long age=System.currentTimeMillis()-Long.parseLong(view.get("updated"));if(!Integer.toString(target).equals(view.get("generation"))||!"true".equals(view.get("ready"))||age>10000||age< -30000)throw fail("TARGET_NOT_READY");
  var session=LocalApply67.verify(o.key,FirstLaunchFiles.read(FolderRuntime118.channel(root,Integer.toString(target)).resolve("server-"+target+".session"),16384));var receipt=LocalApply67.verify(o.key,FirstLaunchFiles.read(FolderRuntime118.channel(root,Integer.toString(target)).resolve("server-"+target+".status"),16384));for(String name:List.of("run","generation","inventory"))if(!Objects.equals(session.get(name),receipt.get(name)))throw fail("TARGET_NOT_READY");if(!o.run.equals(session.get("run"))||!Integer.toString(target).equals(session.get("generation"))||!f.get("newInventory").equals(session.get("inventory"))||!Set.of("READY","COMMAND_ACCEPTED").contains(receipt.get("phase")))throw fail("TARGET_NOT_READY");
  Path control=root.resolve("control73"),dir=directory(root,round);var captured=ControlRoom73.restore(dir,root.resolve("publisher73"));
  if(Files.exists(control.resolve("active73"))){if(!Arrays.equals(FirstLaunchFiles.read(control.resolve("active73"),64),FirstLaunchFiles.read(dir.resolve("active73"),64)))throw fail("ROUND");var current=ControlRoom73.restore(control,root.resolve("publisher73"));if(!Arrays.equals(current.offer.encode(),captured.offer.encode())||!Set.of("COMMITTED","CLOSED").contains(current.phase())||!current.admission()||current.installed()!=current.denominator()||!cohort(current).equals(cohort(captured)))throw fail("CLOSE_BINDING");current.closeRound();current.publish(System.currentTimeMillis());}
  // A timed-out close is idempotent only with the exact signed CLOSED journal, never an absent PID/file alone.
  byte[] journal=FirstLaunchFiles.read(control.resolve(round+".journal"),131072);Path probe=Files.createTempDirectory(root.resolve("commits81"),".close-");FirstLaunchFiles.privateMode(probe);
  try{FirstLaunchFiles.write(probe.resolve("active73"),round.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII),false);FirstLaunchFiles.write(probe.resolve(round+".journal"),journal,false);var closed=ControlRoom73.restore(probe,root.resolve("publisher73"));if(!closed.phase().equals("CLOSED")||!closed.admission()||closed.installed()!=closed.denominator()||!cohort(closed).equals(cohort(captured))||!Arrays.equals(closed.offer.encode(),captured.offer.encode())||Files.exists(control.resolve("active73")))throw fail("CLOSE_UNCONFIRMED");return Map.of("round",round.toString(),"phase","CLOSED");}finally{cleanup(probe);}
 }
 private static void cleanup(Path dir){try(var files=Files.walk(dir)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())try{Files.deleteIfExists(p);}catch(IOException ignored){}}catch(IOException ignored){}}
 public static void main(String[] input){var finished=new java.util.concurrent.atomic.AtomicBoolean();String[] a=input;if(input.length==7&&input[6].equals("--owned")){a=Arrays.copyOf(input,6);Thread lease=new Thread(()->{try{while(System.in.read()!=-1){}}catch(IOException ignored){}finally{if(!finished.get())System.exit(3);}},"genesis-commit81-owner-pipe");lease.setDaemon(true);lease.start();}try{if(a.length!=6||!Set.of("capture","check","close").contains(a[0]))throw fail("ARGUMENTS");Path root=Path.of(a[1]);UUID round=UUID.fromString(a[2]);var result=a[0].equals("capture")?capture(root,round,a[3],Integer.parseInt(a[4]),Integer.parseInt(a[5])):a[0].equals("check")?check(root,round,a[3],Integer.parseInt(a[4]),Integer.parseInt(a[5])):close(root,round,a[3],Integer.parseInt(a[4]),Integer.parseInt(a[5]));System.out.println(DedicatedTools74.json(result));}catch(Exception error){String c=error.getMessage();System.err.println(c!=null&&c.matches("COMMIT81_[A-Z_]{1,60}")?c:"COMMIT81_REJECTED");System.exit(2);}finally{finished.set(true);}}
}
