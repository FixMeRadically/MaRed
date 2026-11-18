package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.security.*;import java.security.spec.PKCS8EncodedKeySpec;import java.util.*;

/** JOIN-only proof for a private committed member whose exact runtime inventory was installed.
 * This protocol neither downloads an archive nor extends RemoteOffer69 install authority. */
public final class Admission82 {
 public static final Set<String> FIELDS=Set.of("schema","purpose","owner","instance","round","source","target","label","release","archive","distribution","game","endpoint","signer","minecraft","neoforge","serverInventory","total","clients","serial","controlSequence","issued","expires");
 private static final Set<String> LOCAL=Set.of("schema","run","round","source","target","label","release","archive","serverInventory","serial","controlSequence","at");
 private Admission82(){}private static IOException bad(){return new IOException("ADMISSION82_REJECTED");}
 private static String uuid(String s)throws Exception{if(s==null||!UUID.fromString(s).toString().equals(s)||UUID.fromString(s).equals(new UUID(0,0)))throw bad();return s;}
 private static long number(String s)throws Exception{if(s==null||!s.matches("0|[1-9][0-9]{0,17}"))throw bad();return Long.parseLong(s);}
 private static void fresh(long at,long now,long age)throws Exception{if(at>now+1000||at<now-age)throw bad();}
 private static String accountHash(String s){return FirstLaunchFiles.sha(s.getBytes(StandardCharsets.US_ASCII));}
 public static Map<String,String> verify(byte[] data,byte[] publicKey,Map<String,String> cfg,String inventory,String minecraft,String neoforge,long now)throws Exception{
  if(!inventory.matches("[a-f0-9]{64}")||!FirstLaunchFiles.sha(publicKey).equals(cfg.get("signer")))throw bad();
  var d=ControlWire73.verify(RemoteOffer69.publicKey(publicKey),data);if(!d.keySet().equals(FIELDS)||!"82".equals(d.get("schema"))||!"JOIN_ONLY".equals(d.get("purpose")))throw bad();
  for(String k:List.of("instance","round","release","archive","distribution","game","endpoint","signer"))if(!Objects.equals(cfg.get(k),d.get(k)))throw bad();
  for(String k:List.of("owner","instance","round","release","distribution"))uuid(d.get(k));uuid(cfg.get("account"));uuid(cfg.get("client"));
  if(!minecraft.equals(d.get("minecraft"))||!neoforge.equals(d.get("neoforge"))||!RemoteOffer69.gameAddress(d.get("game")).equals(d.get("game"))||!RemoteOffer69.base(d.get("endpoint")).equals(d.get("endpoint"))||!d.get("label").matches("[A-Za-z0-9_-]{1,40}")||!d.get("serverInventory").matches("[a-f0-9]{64}"))throw bad();
  long source=number(d.get("source")),target=number(d.get("target")),issued=number(d.get("issued")),expires=number(d.get("expires"));if(target!=source+1||target>999999999||expires-issued!=8000||now>=expires||number(d.get("serial"))<1||number(d.get("controlSequence"))<1)throw bad();fresh(issued,now,8000);
  int total=Math.toIntExact(number(d.get("total")));if(total<1||total>64)throw bad();Set<String> ids=new HashSet<>();boolean found=false;
  for(String row:d.get("clients").split(";",-1)){String[] v=row.split(",",-1);if(v.length!=3||!ids.add(uuid(v[0]))||!v[1].matches("[a-f0-9]{64}")||!v[2].matches("[a-f0-9]{64}"))throw bad();if(v[0].equals(cfg.get("client"))){if(!v[1].equals(inventory)||!v[2].equals(accountHash(cfg.get("account"))))throw bad();found=true;}}
  if(!found||ids.size()!=total)throw bad();return Map.copyOf(d);
 }
 public static Map<String,String> poll(Path cfgFile,Path publicFile,String inventory,String minecraft,String neoforge)throws Exception{
  var cfg=ControlHttp73.config(cfgFile);byte[] pub=FirstLaunchFiles.read(publicFile,256);byte[] data=ControlHttp73.fetch(java.net.URI.create(cfg.get("endpoint")+"control73/"+cfg.get("round")+".ready82"),null,null);return verify(data,pub,cfg,inventory,minecraft,neoforge,System.currentTimeMillis());
 }
 public static final class Signer {
  private final Path root;private final byte[] localKey,pub;private final PrivateKey key;private final String run;private Binding binding;private long serial;private byte[] lastLocal,lastProof;private Path file;
  private static final class Binding {
   final Map<String,String> fence;final byte[] prepared,sealed,journal;final ControlRoom73 captured;
   Binding(Path root,Map<String,String> d)throws Exception{UUID round=UUID.fromString(d.get("round"));fence=CommitFence81.checkAdmitted(root,round,d.get("label"),Math.toIntExact(number(d.get("source"))),Math.toIntExact(number(d.get("target"))));Path dir=root.resolve("commits81").resolve(round.toString());prepared=FirstLaunchFiles.read(root.resolve("prepared74"),16384);sealed=FirstLaunchFiles.read(dir.resolve("fence"),16384);journal=FirstLaunchFiles.read(dir.resolve(round+".journal"),131072);captured=ControlRoom73.restore(dir,root.resolve("publisher73"));}
   ControlRoom73 current(Path root,Map<String,String> d)throws Exception{
    for(String k:List.of("round","source","target","label","release"))if(!fence.get(k).equals(d.get(k)))throw bad();if(!fence.get("client").equals(d.get("archive"))||!fence.get("newInventory").equals(d.get("serverInventory")))throw bad();
    Path dir=root.resolve("commits81").resolve(d.get("round"));if(!Arrays.equals(prepared,FirstLaunchFiles.read(root.resolve("prepared74"),16384))||!Arrays.equals(prepared,FirstLaunchFiles.read(dir.resolve("prepared74"),16384))||!Arrays.equals(sealed,FirstLaunchFiles.read(dir.resolve("fence"),16384))||!Arrays.equals(journal,FirstLaunchFiles.read(dir.resolve(d.get("round")+".journal"),131072))||!new String(FirstLaunchFiles.read(root.resolve("control73/active73"),64),StandardCharsets.US_ASCII).equals(d.get("round")))throw bad();
    var current=ControlRoom73.restore(root.resolve("control73"),root.resolve("publisher73"));if(!current.phase().equals("COMMITTED")||!current.admission()||current.installed()!=current.denominator()||current.serial()<number(d.get("controlSequence"))||!Arrays.equals(current.offer.encode(),captured.offer.encode())||!CommitFence81.cohort(current).equals(CommitFence81.cohort(captured)))throw bad();return current;
   }
  }
  public Signer(Path root)throws Exception{this.root=FirstLaunchFiles.safe(root);localKey=FirstLaunchFiles.read(root.resolve("owner74.key"),32);var marker=LocalApply67.verify(localKey,FirstLaunchFiles.read(root.resolve("owner82"),16384));if(!marker.keySet().equals(Set.of("schema","run"))||!"82".equals(marker.get("schema")))throw bad();run=uuid(marker.get("run"));if(!LocalApply67.verify(localKey,FirstLaunchFiles.read(root.resolve("owner75"),16384)).equals(Map.of("schema","75","run",run)))throw bad();pub=FirstLaunchFiles.read(root.resolve("publisher73/public.der"),256);key=KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(FirstLaunchFiles.read(root.resolve("publisher73/private.der"),256)));}
  public synchronized byte[] publish(long now)throws Exception{try{return issue(now);}catch(Exception failure){clear();throw failure;}}
  private byte[] issue(long now)throws Exception{
   byte[] raw=FirstLaunchFiles.read(root.resolve("admission82.local"),16384);var d=LocalApply67.verify(localKey,raw);if(!d.keySet().equals(LOCAL)||!"82".equals(d.get("schema"))||!run.equals(d.get("run")))throw bad();uuid(d.get("round"));fresh(number(d.get("at")),now,5000);long next=number(d.get("serial"));if(next<1||next<serial||next==serial&&!Arrays.equals(raw,lastLocal))throw bad();
   long source=number(d.get("source")),targetGeneration=number(d.get("target"));if(targetGeneration!=source+1||targetGeneration>999999999||!d.get("label").matches("[A-Za-z0-9_-]{1,40}")||!d.get("serverInventory").matches("[a-f0-9]{64}")||!d.get("archive").matches("[a-f0-9]{64}")||number(d.get("controlSequence"))<1)throw bad();uuid(d.get("release"));
   var view=DedicatedAdmin75.open(root).view();fresh(number(view.get("updated")),now,5000);if(!"true".equals(view.get("ready"))||!d.get("target").equals(view.get("generation"))||!Set.of("ACTIVE_UPDATED","WAITING_THRESHOLD").contains(view.get("phase")))throw bad();
   var session=LocalApply67.verify(localKey,FirstLaunchFiles.read(FolderRuntime118.channel(root,d.get("target")).resolve("server-"+d.get("target")+".session"),16384));var receipt=LocalApply67.verify(localKey,FirstLaunchFiles.read(FolderRuntime118.channel(root,d.get("target")).resolve("server-"+d.get("target")+".status"),16384));for(String k:List.of("run","generation","inventory"))if(!Objects.equals(session.get(k),receipt.get(k)))throw bad();if(!run.equals(session.get("run"))||!d.get("target").equals(session.get("generation"))||!d.get("serverInventory").equals(session.get("inventory"))||!Set.of("READY","COMMAND_ACCEPTED").contains(receipt.get("phase")))throw bad();
   if(binding==null||!binding.fence.get("round").equals(d.get("round"))){clear();binding=new Binding(root,d);lastProof=null;}
   var room=binding.current(root,d);var offer=room.offer;
   var published=ControlWire73.verify(RemoteOffer69.publicKey(pub),FirstLaunchFiles.read(root.resolve("control73/public").resolve(room.round+".state"),131072));
   if(!"COMMITTED".equals(published.get("phase"))||!"true".equals(published.get("admission"))||!room.round.toString().equals(published.get("round"))||!room.instance.toString().equals(published.get("instance"))||!offer.release().toString().equals(published.get("release"))||!offer.archiveSha().equals(published.get("archive"))||number(published.get("installed"))!=room.denominator()||number(published.get("expires"))<=now||number(published.get("expires"))>now+30000)throw bad();
   long publicSequence=number(published.get("sequence"));if(publicSequence<number(d.get("controlSequence"))-1||publicSequence>room.serial())throw bad();
   if(next==serial&&lastProof!=null){var old=ControlWire73.verify(RemoteOffer69.publicKey(pub),lastProof);if(number(old.get("expires"))<=now)throw bad();FirstLaunchFiles.write(file,lastProof,true);return lastProof;}
   var values=new TreeMap<String,String>();values.putAll(Map.of("schema","82","purpose","JOIN_ONLY","owner",run,"instance",room.instance.toString(),"round",room.round.toString(),"source",d.get("source"),"target",d.get("target"),"label",d.get("label"),"release",offer.release().toString(),"archive",offer.archiveSha()));values.putAll(Map.of("distribution",offer.distribution().toString(),"game",offer.game(),"endpoint",offer.endpoint(),"signer",offer.signer(),"minecraft",offer.minecraft(),"neoforge",offer.neoforge(),"serverInventory",d.get("serverInventory"),"total",Integer.toString(room.denominator())));values.putAll(Map.of("serial",Long.toString(next),"controlSequence",Long.toString(publicSequence),"issued",Long.toString(now),"expires",Long.toString(now+8000)));
   values.put("clients",String.join(";",room.members().stream().filter(m->m.counted).map(m->m.id+","+m.inventory+","+accountHash(m.account.toString())).sorted().toList()));byte[] data=ControlWire73.sign(key,values);Path target=FirstLaunchFiles.safe(root.resolve("control73/public").resolve(room.round+".ready82"));FirstLaunchFiles.write(target,data,true);file=target;serial=next;lastLocal=raw;lastProof=data;return data;
  }
  public synchronized void clear()throws IOException{if(file!=null)Files.deleteIfExists(file);}
 }
 public static void main(String[] a){try{if(a.length!=6||!a[0].equals("poll"))throw bad();var d=poll(Path.of(a[1]),Path.of(a[2]),a[3],a[4],a[5]);System.out.println(Base64.getEncoder().encodeToString(ControlWire73.encode(d)));}catch(Exception invalid){System.err.println("ADMISSION82_UNAVAILABLE");System.exit(2);}}
}
