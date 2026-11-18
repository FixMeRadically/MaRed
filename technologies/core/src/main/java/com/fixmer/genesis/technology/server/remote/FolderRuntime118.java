package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;import java.nio.file.*;import java.util.*;
/** A signed generation-to-launch mapping, never a search for or adoption of a PID. */
public final class FolderRuntime118 {
 private FolderRuntime118(){}
 public static Path channel(Path root,String generation)throws Exception {
  root=FirstLaunchFiles.safe(root);
  if(!generation.matches("0|[1-9][0-9]{0,8}"))throw new IOException("FOLDER118_GENERATION");
  if(!Files.exists(root.resolve("owner104"),LinkOption.NOFOLLOW_LINKS))return root;
  byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);
  var owner=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner104"),16384));
  if(!owner.keySet().equals(Set.of("schema","run","profile"))||!"104".equals(owner.get("schema"))||!FirstLaunchFiles.safe(Path.of(owner.get("profile"))).resolve(".genesis/server104").equals(root))throw new IOException("FOLDER118_OWNER");
  var d=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("channels118").resolve(generation),16384));
  if(!d.keySet().equals(Set.of("schema","run","generation","launch","profile"))||!"118".equals(d.get("schema"))||!owner.get("run").equals(d.get("run"))||!generation.equals(d.get("generation"))||!owner.get("profile").equals(d.get("profile"))||!UUID.fromString(d.get("launch")).toString().equals(d.get("launch")))throw new IOException("FOLDER118_CHANNEL");
  Path channel=FirstLaunchFiles.safe(root.resolve("runs").resolve(d.get("launch")));
  if(!LocalApply67.verify(key,FirstLaunchFiles.read(channel.resolve("launch104"),16384)).equals(Map.of("schema","104","run",d.get("run"),"generation",generation,"launch",d.get("launch"),"profile",d.get("profile"))))throw new IOException("FOLDER118_CHANNEL");
  return channel;
 }
 public static boolean supported(Path root)throws Exception {
  var admin=DedicatedAdmin75.open(root);
  if(!admin.isInPlace104())return false;
  byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);
  return Files.isRegularFile(root.resolve("owner118"),LinkOption.NOFOLLOW_LINKS)&&LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner118"),16384)).equals(Map.of("schema","118","run",admin.run.toString()));
 }
 public static void prepared(Path root,Map<String,String> session,RemoteOffer69 offer)throws Exception {
  if(!supported(root))throw new IOException("SERVER104_UPDATE_COORDINATOR_REQUIRED");
  byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);
  var actualOwner=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner104"),16384));
  if(!FirstLaunchFiles.inventory(Path.of(actualOwner.get("profile"))).equals(session.get("inventory")))throw new IOException("FOLDER118_SOURCE_CHANGED");
  var d=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("prepared74"),16384));
  if(!d.keySet().equals(Set.of("schema","run","generation","oldInventory","newInventory","release","client","server","label","codeSeal"))||!"74".equals(d.get("schema"))||!session.get("run").equals(d.get("run"))||!session.get("generation").equals(d.get("generation"))||!session.get("inventory").equals(d.get("oldInventory")))throw new IOException("FOLDER118_PREPARED");
  if(!d.get("label").matches("[A-Za-z0-9_-]{1,40}"))throw new IOException("FOLDER118_LABEL");
  var pair=DedicatedRelease74.verify(root.resolve("releases").resolve(d.get("label")),FirstLaunchFiles.read(root.resolve("publisher73/public.der"),256));
  if(!Arrays.equals(pair.offer().encode(),offer.encode())||!offer.release().toString().equals(d.get("release"))||!offer.archiveSha().equals(d.get("client"))||!pair.archive().archiveSha256().equals(d.get("server")))throw new IOException("FOLDER118_PAIR");
  if(d.get("newInventory").equals(d.get("oldInventory"))&&!Files.exists(root.resolve("change104"))){
   var proof=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("clientonly118"),16384));
   if(!proof.equals(Map.of("schema","118","run",d.get("run"),"generation",d.get("generation"),"inventory",d.get("oldInventory"),"codeSeal",d.get("codeSeal"),"release",d.get("release"),"client",d.get("client"),"server",d.get("server"))))throw new IOException("FOLDER118_CLIENT_ONLY");
   var owner=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner104"),16384));
   if(!FirstLaunchFiles.inventory(Path.of(owner.get("profile"))).equals(d.get("oldInventory")))throw new IOException("FOLDER118_CLIENT_ONLY");return;
  }
  var change=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("change104"),16384));
  if(!change.keySet().equals(Set.of("schema","id","plan","phase"))||!UUID.fromString(change.get("id")).toString().equals(change.get("id"))||!"104".equals(change.get("schema"))||!"PREPARED".equals(change.get("phase"))||!d.get("codeSeal").equals(change.get("plan"))||!FirstLaunchFiles.hash(root.resolve("changes").resolve(change.get("id")).resolve("plan.json"),262144).equals(d.get("codeSeal")))throw new IOException("FOLDER118_ARTIFACT_CHANGED");
 }
}
