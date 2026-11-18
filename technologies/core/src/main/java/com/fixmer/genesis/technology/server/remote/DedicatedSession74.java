package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;import java.nio.file.*;import java.util.*;
/** Local owner-created dedicated session. HMAC is not remote-player authentication or PID adoption. */
public final class DedicatedSession74 {
 public final Path root,profile;public final int generation;public final boolean repeatable;public final Map<String,String> config;private final byte[] key;private final Path channel;
 private DedicatedSession74(Path root,int gen,Map<String,String> c,byte[] key,boolean repeats,Path channel,Path actualProfile)throws Exception{this.root=root;generation=gen;repeatable=repeats;config=c;this.key=key;this.channel=channel;profile=actualProfile==null?FirstLaunchFiles.safe(root.resolve("generations/server-"+gen)):actualProfile;}
 public static DedicatedSession74 open(Map<String,String> env)throws Exception{
  if(env.get("GENESIS_DEDICATED74_ROOT")==null)return null;Path root=FirstLaunchFiles.safe(Path.of(env.get("GENESIS_DEDICATED74_ROOT")));String gen=env.get("GENESIS_DEDICATED74_GENERATION");boolean inplace="104".equals(env.get("GENESIS_INPLACE_VERSION"));boolean repeats="75".equals(env.get("GENESIS_DEDICATED75_VERSION"));if(gen==null||!gen.matches(inplace?"0|[1-9][0-9]{0,8}":"0|[1-9][0-9]?")||!inplace&&Integer.parseInt(gen)>32||!repeats&&!Set.of("0","1").contains(gen))throw new IOException("DEDICATED74_GENERATION");byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);if(key.length!=32)throw new IOException("DEDICATED74_KEY");Path channel=root,actualProfile=null;
  if(inplace){
   var owner=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner104"),16384));String launch=env.getOrDefault("GENESIS_INPLACE_LAUNCH","");if(!UUID.fromString(launch).toString().equals(launch))throw new IOException("SERVER104_LAUNCH");
   if(!owner.keySet().equals(Set.of("schema","run","profile"))||!owner.get("schema").equals("104")||!owner.get("run").equals(env.get("GENESIS_DEDICATED74_RUN")))throw new IOException("SERVER104_OWNER");
   actualProfile=FirstLaunchFiles.safe(Path.of(owner.get("profile")));if(!root.equals(actualProfile.resolve(".genesis/server104")))throw new IOException("SERVER104_PROFILE");
   channel=FirstLaunchFiles.safe(root.resolve("runs").resolve(launch));var binding=LocalApply67.verify(key,FirstLaunchFiles.read(channel.resolve("launch104"),16384));
   if(!binding.equals(Map.of("schema","104","run",owner.get("run"),"launch",launch,"generation",gen,"profile",actualProfile.toString())))throw new IOException("SERVER104_LAUNCH_BINDING");
  }
  var c=LocalApply67.verify(key,FirstLaunchFiles.read(channel.resolve("server-"+gen+".session"),16384));
  if(!c.keySet().equals(Set.of("schema","run","generation","inventory","minecraft","neoforge","auth","bind","port"))||!c.get("schema").equals("74")||!c.get("run").equals(env.get("GENESIS_DEDICATED74_RUN"))||!c.get("generation").equals(gen)||!c.get("inventory").matches("[a-f0-9]{64}")||!Set.of("true","false").contains(c.get("auth"))||!c.get("port").matches("[0-9]{1,5}")||Integer.parseInt(c.get("port"))<1||Integer.parseInt(c.get("port"))>65535)throw new IOException("DEDICATED74_SESSION");UUID.fromString(c.get("run"));if(repeats&&!LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner75"),16384)).equals(Map.of("schema","75","run",c.get("run"))))throw new IOException("DEDICATED75_OWNER_REQUIRED");return new DedicatedSession74(root,Integer.parseInt(gen),c,key,repeats,channel,actualProfile);
 }
 public void verifyRuntime(Path actual,Collection<Path> loaded,String mc,String neo,boolean auth,String bind,int port)throws Exception{
  if(!FirstLaunchFiles.safe(actual).equals(profile)||!config.get("minecraft").equals(mc)||!config.get("neoforge").equals(neo)||!config.get("auth").equals(Boolean.toString(auth))||!config.get("bind").equals(bind)||!config.get("port").equals(Integer.toString(port))||!FirstLaunchFiles.inventory(profile).equals(config.get("inventory")))throw new IOException("DEDICATED74_RUNTIME");
  try(var paths=Files.list(profile.resolve("mods"))){for(Path jar:paths.filter(p->p.getFileName().toString().endsWith(".jar")).toList()){boolean found=false;for(Path p:loaded){try{if(Files.isSameFile(jar,p)){found=true;break;}}catch(IOException|RuntimeException unrelated){}}if(!found)throw new IOException("DEDICATED74_NOT_LOADED");}}
 }
 public Map<String,String> command()throws Exception{
  Path p=channel.resolve("server-"+generation+".command");if(!Files.exists(p))return Map.of();var d=LocalApply67.verify(key,FirstLaunchFiles.read(p,16384));
  if(!d.keySet().equals(Set.of("schema","run","generation","inventory","sequence","request","action","round","label"))||!d.get("schema").equals("74")||!d.get("run").equals(config.get("run"))||!d.get("generation").equals(config.get("generation"))||!d.get("inventory").equals(config.get("inventory"))||!d.get("sequence").matches("[1-9][0-9]{0,16}")||!Set.of("LOCAL_TEST_ON","LOCAL_TEST_OFF","PREPARE_OFFER118","PREPARE","OFFER","STATUS","COMMIT","CANCEL","CLOSE","QUIESCE","SAVE_STOP","RESTART125","SHUTDOWN").contains(d.get("action"))||!d.get("label").matches("[A-Za-z0-9_-]{1,40}"))throw new IOException("DEDICATED74_COMMAND");if(UUID.fromString(d.get("request")).equals(new UUID(0,0)))throw new IOException("DEDICATED74_REQUEST");if(!d.get("round").equals("none"))UUID.fromString(d.get("round"));return d;
 }
 public void prepared(RemoteOffer69 offer)throws Exception{
  if(!channel.equals(root)){FolderRuntime118.prepared(root,config,offer);return;}
  if(generation!=0&&!repeatable)throw new IOException("DEDICATED74_ONE_TRANSITION");var d=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("prepared74"),16384));
  if(!d.keySet().equals(Set.of("schema","run","generation","oldInventory","newInventory","release","client","server","label","codeSeal"))||!d.get("schema").equals("74")||!d.get("run").equals(config.get("run"))||!d.get("generation").equals(config.get("generation"))||!d.get("oldInventory").equals(config.get("inventory"))||!d.get("release").equals(offer.release().toString())||!d.get("client").equals(offer.archiveSha())||!d.get("newInventory").matches("[a-f0-9]{64}")||!d.get("server").matches("[a-f0-9]{64}")||!d.get("codeSeal").matches("[a-f0-9]{64}"))throw new IOException("DEDICATED74_NOT_PREPARED");
 }
 public synchronized void status(String phase,long seq,UUID request,String round)throws Exception{
  if(!Set.of("READY","QUIESCED","SAVED","STOPPED_SAVED","STOPPED","ERROR","COMMAND_ACCEPTED").contains(phase))throw new IOException("DEDICATED74_PHASE");FirstLaunchFiles.write(channel.resolve("server-"+generation+".status"),LocalApply67.sign(key,Map.of("schema","74","run",config.get("run"),"generation",Integer.toString(generation),"inventory",config.get("inventory"),"sequence",Long.toString(seq),"request",request.toString(),"phase",phase,"round",round,"at",Long.toString(System.currentTimeMillis()))),true);
 }
}
